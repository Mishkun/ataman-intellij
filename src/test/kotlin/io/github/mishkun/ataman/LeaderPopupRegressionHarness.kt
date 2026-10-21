package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.ui.UiInterceptors
import com.intellij.ui.awt.RelativePoint
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Headless integration seam: the official persistent UI interceptor records real popups
 * and suppresses only native window showing. It does not replace navigation or callbacks.
 * The test focus manager exposes its protected callback list without reflection.
 */
internal class LeaderPopupRegressionHarness(
    private val project: Project,
    includeRootR: Boolean = true
) : AutoCloseable {
    val executions = mutableListOf<String>()
    val popups = mutableListOf<LeaderPopupUI>()
    val dispatcher = service<LeaderKeyDispatcher>()
    val context = SimpleDataContext.getProjectContext(project)
    private val config = service<ConfigService>()
    private val previousBindings = config.parsedBindings
    private val previousCommand = config.latestCommand
    private val folder = TemporaryFolder()
    private val interceptorLifetime = Disposer.newDisposable("Ataman popup regression interceptor")
    private val originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val focusManager = InspectableFocusManager()
    private val actionIds = listOf("root", "child", "sibling").associateWith { "Ataman.NavigationRegression.$it" }
    private lateinit var callback: KeyEventDispatcher

    init {
        check(SwingUtilities.isEventDispatchThread())
        dispatcher.stop()
        folder.create()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
        actionIds.forEach { (name, id) ->
            ActionManager.getInstance().registerAction(id, object : DumbAwareAction() {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) { executions.add(name) }
            })
        }
        UiInterceptors.registerPersistent(interceptorLifetime,
            object : UiInterceptors.PersistentUiInterceptor<LeaderPopupUI>(LeaderPopupUI::class.java) {
                override fun shouldIntercept(component: LeaderPopupUI) = true
                override fun doIntercept(component: LeaderPopupUI, point: RelativePoint?) {
                    popups.add(component)
                    component.list.fixedCellHeight = 24
                    component.list.fixedCellWidth = 360
                    component.list.setSize(360, 180)
                    component.list.doLayout()
                }
            })
        folder.root.resolve(ATAMAN_RC_FILENAME).writeText("""
            bindings {
                c {
                    description: "Child group"
                    bindings {
                        r { description: "Child r", actionId: "${actionIds.getValue("child")}" }
                    }
                }
                ${if (includeRootR) "r { description: \"Root r\", actionId: \"${actionIds.getValue("root")}\" }" else ""}
                s { description: "Root sibling", actionId: "${actionIds.getValue("sibling")}" }
            }
        """.trimIndent())
        val parsed = parseConfig(folder.root, "IU").getOrThrow()
        updateConfig(project, folder.root, "IU")
        assertEquals(parsed, config.parsedBindings)
    }

    fun startWithProductionAction(): LeaderPopupUI {
        val action = LeaderAction()
        action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context))
        callback = focusManager.registeredDispatchers().single()
        return popups.single().also {
            assertNull(it.parent)
            assertFalse(it.isDisposed)
        }
    }

    fun startDispatcherOnly() {
        dispatcher.start(config.parsedBindings, context)
        callback = focusManager.registeredDispatchers().single()
    }

    /** Models a split start/UI boundary, not an interleaving inside atomic LeaderAction. */
    fun attachPopupAfterStart(): LeaderPopupUI {
        val popup = LeaderPopupUI(project, LeaderListStep("Ataman", context, config.parsedBindings))
        // Same close -> stop wiring as LeaderAction; no binding synchronization is added.
        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) { dispatcher.stop() }
        })
        popup.showCenteredInCurrentWindow(project)
        return popup
    }

    fun selectViaMousePath(popup: LeaderPopupUI, key: String) {
        val binding = (0 until popup.list.model.size).map { popup.list.model.getElementAt(it) }
            .filterIsInstance<LeaderBinding>().single { it.char == key }
        popup.list.setSelectedValue(binding, true)
        val bounds = popup.list.getCellBounds(popup.list.selectedIndex, popup.list.selectedIndex)
        assertNotNull("Real list selection must have a cell", bounds)
        // ListPopupImpl.MyMouseListener.mouseReleased calls this exact public entry point.
        // Synthetic mouse metadata exercises the library's mouse-specific branch too.
        popup.handleSelect(true, MouseEvent(popup.list, MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(), 0, bounds.x + 4, bounds.y + bounds.height / 2,
            1, false, MouseEvent.BUTTON1))
    }

    fun childOf(root: LeaderPopupUI): LeaderPopupUI =
        popups.single { it.parent === root && !it.isDisposed }.also {
            assertTrue("Child must be created by the real popup factory", it.step is LeaderListStep)
        }

    fun send(id: Int, code: Int, char: Char, whenMillis: Long = 10_000, modifiers: Int = 0): Boolean {
        check(SwingUtilities.isEventDispatchThread())
        return callback.dispatchKeyEvent(KeyEvent(JPanel(), id, whenMillis, modifiers, code, char))
    }

    fun type(code: Int, char: Char) {
        send(KeyEvent.KEY_PRESSED, code, char)
        send(KeyEvent.KEY_RELEASED, code, char)
    }

    fun captureCount() = focusManager.registeredDispatchers().count { ::callback.isInitialized && it === callback }

    fun state() = "executions=$executions, popups=${popups.map { "parent=${it.parent != null}/disposed=${it.isDisposed}" }}, captureCount=${captureCount()}"

    override fun close() {
        try {
            dispatcher.stop()
            popups.asReversed().forEach { if (!it.isDisposed) it.cancel() }
            assertTrue("Every real popup must be disposed", popups.all { it.isDisposed })
            assertEquals("Dispatcher must unregister on every case", 0, captureCount())
        } finally {
            Disposer.dispose(interceptorLifetime)
            KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager)
            actionIds.values.forEach { ActionManager.getInstance().unregisterAction(it) }
            config.parsedBindings = previousBindings
            config.latestCommand = previousCommand
            folder.delete()
        }
    }

    private class InspectableFocusManager : DefaultKeyboardFocusManager() {
        fun registeredDispatchers(): List<KeyEventDispatcher> = keyEventDispatchers.orEmpty()
    }
}
