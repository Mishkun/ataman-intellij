package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JPanel

/**
 * Exercises the callback actually registered by public start(), not private matching methods.
 * The focus manager exposes its protected dispatcher list; no reflection or mocked outcomes.
 * OS focus routing and popup rendering are intentionally outside these state-machine tests.
 */
class LeaderKeyDispatcherRegressionTest : LightPlatform4TestCase() {
    @get:Rule
    val configFolder = TemporaryFolder()

    private val actionId = "Ataman.DispatcherRegression.Count"
    private val eventSource = JPanel()
    private val enteredGroups = mutableListOf<String>()
    private val selectedBindings = mutableListOf<String>()
    private var executions = 0
    private var dismissals = 0
    private lateinit var originalFocusManager: KeyboardFocusManager
    private lateinit var focusManager: InspectableFocusManager
    private lateinit var dispatcher: LeaderKeyDispatcher
    private lateinit var registeredDispatcher: KeyEventDispatcher
    private lateinit var previousBindings: List<LeaderBinding>
    private var previousCommand: String? = null

    private val listener = object : LeaderStateListener {
        override fun onBindingSelected(binding: LeaderBinding.SingleBinding) {
            selectedBindings.add(binding.char)
        }

        override fun onGroupEntered(group: LeaderBinding.GroupBinding) {
            enteredGroups.add(group.char)
        }

        override fun onDismiss() {
            dismissals++
        }
    }

    override fun setUp() {
        super.setUp()
        val config = service<ConfigService>()
        previousBindings = config.parsedBindings
        previousCommand = config.latestCommand
        dispatcher = service<LeaderKeyDispatcher>()
        dispatcher.stop()
        dispatcher.addListener(listener)
        ActionManager.getInstance().registerAction(actionId, object : DumbAwareAction() {
            override fun getActionUpdateThread() = ActionUpdateThread.EDT

            override fun actionPerformed(e: AnActionEvent) {
                executions++
            }
        })
        originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager = InspectableFocusManager()
        KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)
    }

    override fun tearDown() {
        try {
            dispatcher.stop()
            dispatcher.removeListener(listener)
            KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager)
            ActionManager.getInstance().unregisterAction(actionId)
            service<ConfigService>().apply {
                parsedBindings = previousBindings
                latestCommand = previousCommand
            }
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `overlapping group and leaf keys execute the child once`() {
        startWithGroup("c", "r")

        // Baseline 7be6bb9 registers group KEY_PRESSED and leaf KEY_RELEASED.
        // In particular, there is no root-level r that could mask a missed group.
        press(KeyEvent.VK_C, 'c')
        press(KeyEvent.VK_R, 'r')
        release(KeyEvent.VK_R, 'r')
        release(KeyEvent.VK_C, 'c')

        assertEquals("Child must execute exactly once after overlapping c/r; ${state()}", 1, executions)
        assertEquals(listOf("c"), enteredGroups)
        assertEquals(listOf("r"), selectedBindings)
        assertEquals(1, dismissals)
    }

    @Test
    fun `function key group opens and executes its child`() {
        val group = startWithGroup("F12", "a")
        assertEquals(KeyEvent.VK_F12, group.key.keyCode)
        assertFalse("Production parser must keep group keys on press", group.key.isOnKeyRelease)

        // Non-overlapping input isolates this from the rollover regression.
        press(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED)
        release(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED)
        press(KeyEvent.VK_A, 'a')
        release(KeyEvent.VK_A, 'a')

        assertEquals("F12 must enter its parsed group; ${state()}", listOf("F12"), enteredGroups)
        assertEquals("F12/a child must execute exactly once; ${state()}", 1, executions)
        assertEquals(listOf("a"), selectedBindings)
        assertEquals(1, dismissals)
    }

    @Test
    fun `control non-overlapping character group executes its child`() {
        startWithGroup("c", "r")

        press(KeyEvent.VK_C, 'c')
        release(KeyEvent.VK_C, 'c')
        press(KeyEvent.VK_R, 'r')
        release(KeyEvent.VK_R, 'r')

        assertEquals("Control proves real action invocation works; ${state()}", 1, executions)
        assertEquals(listOf("c"), enteredGroups)
        assertEquals(listOf("r"), selectedBindings)
        assertEquals(1, dismissals)
        assertEquals(actionId, service<ConfigService>().latestCommand)
    }

    @Test
    fun `control function key leaf executes on release`() {
        startWithConfig("""
            bindings {
                F12 { description: "Function leaf", actionId: "$actionId" }
            }
        """)

        press(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED)
        assertEquals(0, executions)
        release(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED)

        assertEquals("Control proves function-key event delivery works; ${state()}", 1, executions)
        assertEquals(listOf("F12"), selectedBindings)
        assertEquals(1, dismissals)
    }

    private fun startWithGroup(groupKey: String, childKey: String): LeaderBinding.GroupBinding {
        startWithConfig("""
            bindings {
                $groupKey {
                    description: "Regression group"
                    bindings {
                        $childKey { description: "Count executions", actionId: "$actionId" }
                    }
                }
            }
        """)
        val group = service<ConfigService>().parsedBindings.single() as LeaderBinding.GroupBinding
        assertFalse(group.key.isOnKeyRelease)
        assertTrue(group.bindings.single().key.isOnKeyRelease)
        return group
    }

    private fun startWithConfig(hocon: String) {
        configFolder.root.resolve(ATAMAN_RC_FILENAME).writeText(hocon.trimIndent())
        // Validate with the real parser, then use the same ConfigService update as production.
        val parsed = parseConfig(configFolder.root, "IU").getOrThrow()
        updateConfig(project, configFolder.root, "IU")
        assertEquals(parsed, service<ConfigService>().parsedBindings)
        dispatcher.start(service<ConfigService>().parsedBindings, SimpleDataContext.getProjectContext(project))
        registeredDispatcher = focusManager.registeredDispatchers().single()
    }

    private fun press(code: Int, char: Char) = dispatch(KeyEvent.KEY_PRESSED, code, char)

    private fun release(code: Int, char: Char) = dispatch(KeyEvent.KEY_RELEASED, code, char)

    private fun dispatch(id: Int, code: Int, char: Char) {
        // A completed action may stop capture before the trailing key release.
        // The contract here is exactly-once execution, not consuming every event.
        registeredDispatcher.dispatchKeyEvent(
            KeyEvent(eventSource, id, System.currentTimeMillis(), 0, code, char)
        )
    }

    private fun state(): String =
        "executions=$executions, enteredGroups=$enteredGroups, selectedBindings=$selectedBindings, dismissals=$dismissals"
            .also { println(it) }

    private class InspectableFocusManager : DefaultKeyboardFocusManager() {
        fun registeredDispatchers(): List<KeyEventDispatcher> = keyEventDispatchers.orEmpty()
    }
}
