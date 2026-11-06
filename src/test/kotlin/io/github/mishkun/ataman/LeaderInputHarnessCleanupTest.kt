package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Assert.assertThrows as expectThrows
import org.junit.Test
import java.awt.KeyboardFocusManager
import javax.swing.SwingUtilities

/** Infrastructure checks, separate from the ten advertised input behavior cases. */
class LeaderInputHarnessCleanupTest : LightPlatform4TestCase() {
    @Test
    fun `constructor failure rolls back partial setup and preserves existing action`() {
        val manager = ActionManager.getInstance()
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val config = service<ConfigService>()
        val bindings = config.parsedBindings
        val command = config.latestCommand
        val conflictId = "Ataman.InputRegression.sibling"
        val existing = object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {}
        }
        manager.registerAction(conflictId, existing)
        try {
            val error = expectThrows(IllegalStateException::class.java) { LeaderInputHarness(project) }
            assertTrue(error.message!!.contains("Action already registered"))
            assertSame(existing, manager.getAction(conflictId))
            assertNull(manager.getAction("Ataman.InputRegression.root"))
            assertNull(manager.getAction("Ataman.InputRegression.child"))
            assertSame(focus, KeyboardFocusManager.getCurrentKeyboardFocusManager())
            assertEquals(bindings, config.parsedBindings)
            assertEquals(command, config.latestCommand)
        } finally {
            manager.unregisterAction(conflictId)
        }
    }

    @Test
    fun `body failure closes popups restores shared state and preserves original error`() {
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val config = service<ConfigService>()
        val bindings = config.parsedBindings
        val command = config.latestCommand
        val harness = LeaderInputHarness(project)
        val original = IllegalStateException("test body failure")
        var navigationCompleted = false
        var navigationError: Throwable? = null

        val actual = expectThrows(IllegalStateException::class.java) {
            harness.use { h ->
                SwingUtilities.invokeLater {
                    try {
                        h.mouseSelect("c")
                        navigationCompleted = true
                    } catch (error: Throwable) {
                        navigationError = error
                    }
                }
                throw original
            }
        }

        assertSame(original, actual)
        assertNull("Deferred real popup navigation must complete before cancellation", navigationError)
        assertTrue("Cleanup must process navigation queued before the body failure", navigationCompleted)
        assertFalse(harness.hasOpenPopup())
        assertSame(focus, KeyboardFocusManager.getCurrentKeyboardFocusManager())
        assertEquals(bindings, config.parsedBindings)
        assertEquals(command, config.latestCommand)
        listOf("root", "child", "sibling", "nested").forEach {
            assertNull(ActionManager.getInstance().getAction("Ataman.InputRegression.$it"))
        }
    }
}
