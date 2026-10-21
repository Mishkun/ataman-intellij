package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.UiInterceptors
import com.intellij.ui.awt.RelativePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.rules.TemporaryFolder
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.SwingUtilities

internal enum class LeaderStroke(val code: Int, val char: Char, val release: Boolean = false, val modifiers: Int = 0) {
    C_DOWN(KeyEvent.VK_C, 'c'), C_UP(KeyEvent.VK_C, 'c', true),
    R_DOWN(KeyEvent.VK_R, 'r'), R_UP(KeyEvent.VK_R, 'r', true),
    S_DOWN(KeyEvent.VK_S, 's'), S_UP(KeyEvent.VK_S, 's', true),
    A_DOWN(KeyEvent.VK_A, 'a'), A_UP(KeyEvent.VK_A, 'a', true),
    F12_DOWN(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED), F12_UP(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED, true),
    ESC_DOWN(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED), ESC_UP(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, true),
    SHIFT_ESC_DOWN(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, modifiers = KeyEvent.SHIFT_DOWN_MASK),
    SHIFT_ESC_UP(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, true, KeyEvent.SHIFT_DOWN_MASK)
}

/** Real LeaderAction and popup navigation; only native window showing is intercepted. */
internal class LeaderInputHarness(project: Project, functionKeyLeaf: Boolean = false) : AutoCloseable {
    val actions = mutableListOf<String>()
    private val config = service<ConfigService>()
    private val dispatcher = service<LeaderKeyDispatcher>()
    private val actionManager = ActionManager.getInstance()
    private val previousBindings = config.parsedBindings
    private val previousCommand = config.latestCommand
    private val originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val focusManager = TestFocusManager()
    private val interceptorLifetime = Disposer.newDisposable("Ataman input regression")
    private val folder = TemporaryFolder()
    private val registeredActions = mutableListOf<String>()
    private val popups = mutableListOf<LeaderPopupUI>()
    private var lastList: JList<*>? = null
    private var closed = false

    init {
        try {
            check(SwingUtilities.isEventDispatchThread())
            dispatcher.stop()
            folder.create()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
            listOf("root", "child", "sibling").forEach { name ->
                val id = "Ataman.InputRegression.$name"
                check(actionManager.getAction(id) == null) { "Action already registered: $id" }
                registeredActions.add(id)
                actionManager.registerAction(id, object : DumbAwareAction() {
                    override fun getActionUpdateThread() = ActionUpdateThread.EDT
                    override fun actionPerformed(e: AnActionEvent) { actions.add(name) }
                })
            }
            UiInterceptors.registerPersistent(interceptorLifetime,
                object : UiInterceptors.PersistentUiInterceptor<LeaderPopupUI>(LeaderPopupUI::class.java) {
                    override fun shouldIntercept(component: LeaderPopupUI) = true
                    override fun doIntercept(component: LeaderPopupUI, point: RelativePoint?) {
                        popups.add(component)
                        lastList = component.list
                        component.list.fixedCellHeight = 24
                        component.list.fixedCellWidth = 360
                        component.list.setSize(360, 180)
                    }
                })
            val f12 = if (functionKeyLeaf) """
                F12 { description: "Function leaf", actionId: "Ataman.InputRegression.child" }
            """ else """
                F12 {
                    description: "Function group"
                    bindings { a { description: "Child a", actionId: "Ataman.InputRegression.child" } }
                }
            """
            folder.root.resolve(ATAMAN_RC_FILENAME).writeText("""
                bindings {
                    c {
                        description: "Child group"
                        bindings { r { description: "Child r", actionId: "Ataman.InputRegression.child" } }
                    }
                    r { description: "Root r", actionId: "Ataman.InputRegression.root" }
                    s { description: "Root sibling", actionId: "Ataman.InputRegression.sibling" }
                    $f12
                }
            """.trimIndent())
            val parsed = parseConfig(folder.root, "IU").getOrThrow()
            updateConfig(project, folder.root, "IU")
            assertEquals(parsed, config.parsedBindings)
            val action = LeaderAction()
            action.actionPerformed(AnActionEvent.createEvent(action, SimpleDataContext.getProjectContext(project),
                null, ActionPlaces.KEYBOARD_SHORTCUT, ActionUiKind.NONE, null))
            check(hasOpenPopup()) { "LeaderAction did not create a popup" }
        } catch (error: Throwable) {
            try { close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    fun input(vararg strokes: LeaderStroke, drainBetween: Boolean = false) {
        strokes.forEach {
            send(it)
            if (drainBetween) drainEdt()
        }
    }

    fun queuedInput(vararg strokes: LeaderStroke) {
        val errors = mutableListOf<Throwable>()
        // Enqueue the complete backlog BEFORE pumping; deferred work follows pending input.
        strokes.forEach { stroke ->
            SwingUtilities.invokeLater {
                try { send(stroke) } catch (error: Throwable) { errors.add(error) }
            }
        }
        drainEdt()
        if (errors.isNotEmpty()) {
            errors.drop(1).forEach { errors.first().addSuppressed(it) }
            throw errors.first()
        }
    }

    private fun send(stroke: LeaderStroke) {
        check(SwingUtilities.isEventDispatchThread())
        val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager() as TestFocusManager
        val callbacks = manager.registeredDispatchers()
        val popup = activePopup()
        if (callbacks.isEmpty() && popup == null) return
        val source = popup?.list ?: lastList ?: return
        val event = KeyEvent(source, if (stroke.release) KeyEvent.KEY_RELEASED else KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(), stroke.modifiers, stroke.code, stroke.char)
        for (callback in callbacks) {
            // A prior callback may have unregistered another callback in this snapshot.
            if (callback !in manager.registeredDispatchers()) continue
            if (callback.dispatchKeyEvent(event) || event.isConsumed) return
        }
        activePopup()?.dispatch(event)
    }

    fun mouseSelect(key: String) {
        val popup = checkNotNull(activePopup()) { "No active popup for mouse selection" }
        val binding = (0 until popup.list.model.size).map { popup.list.model.getElementAt(it) }
            .filterIsInstance<LeaderBinding>().single { it.char == key }
        popup.list.setSelectedValue(binding, true)
        val cell = checkNotNull(popup.list.getCellBounds(popup.list.selectedIndex, popup.list.selectedIndex))
        // Public entry point used by ListPopupImpl.MyMouseListener.mouseReleased.
        popup.handleSelect(true, MouseEvent(popup.list, MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(), 0, cell.x + 4, cell.y + cell.height / 2,
            1, false, MouseEvent.BUTTON1))
    }

    fun hasOpenPopup() = activePopup() != null
    private fun activePopup() = popups.lastOrNull { !it.isDisposed }

    fun drainEdt() {
        // Platform pump runs until the queue is empty, including work posted by dispatched
        // events. Unlike a sentinel, this drains transitive pending work. No timer sleeps.
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        fun attempt(step: () -> Unit) {
            try { step() } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        attempt { dispatcher.stop() }
        // Pending real navigation may create children before or during cancellation.
        attempt { drainEdt() }
        for (pass in 1..4) {
            popups.filter { !it.isDisposed }.asReversed().forEach { popup ->
                attempt { if (!popup.isDisposed) popup.cancel() }
                attempt { if (!popup.isDisposed) Disposer.dispose(popup) }
                attempt { dispatcher.removeListener(popup) }
            }
            attempt { drainEdt() }
            if (!hasOpenPopup()) break
        }
        attempt { dispatcher.stop() }
        // Forced teardown of this test-owned manager is cleanup, NOT lifecycle evidence.
        attempt {
            focusManager.registeredDispatchers().forEach { callback ->
                attempt { focusManager.removeKeyEventDispatcher(callback) }
            }
        }
        attempt { assertTrue("Cleanup must dispose every test popup", popups.all { it.isDisposed }) }
        attempt { assertTrue("Cleanup must remove test-manager registrations", focusManager.registeredDispatchers().isEmpty()) }
        attempt { Disposer.dispose(interceptorLifetime) }
        attempt { KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager) }
        registeredActions.forEach { id -> attempt { actionManager.unregisterAction(id) } }
        attempt { config.parsedBindings = previousBindings }
        attempt { config.latestCommand = previousCommand }
        attempt { folder.delete() }
        failure?.let { throw it }
    }

    private class TestFocusManager : DefaultKeyboardFocusManager() {
        fun registeredDispatchers(): List<KeyEventDispatcher> = keyEventDispatchers.orEmpty()
    }
}
