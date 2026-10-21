package io.github.mishkun.ataman

import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test
import java.awt.event.KeyEvent

/** Deterministic headless popup integration, not native mouse/keyboard UI automation. */
class LeaderPopupNavigationRegressionTest : LightPlatform4TestCase() {
    @Test
    fun `mouse-selected group routes subsequent key to child not root`() {
        LeaderPopupRegressionHarness(project).use { h ->
            val root = h.startWithProductionAction()
            h.selectViaMousePath(root, "c")
            val child = h.childOf(root)
            assertEquals(listOf("r"), (child.step as LeaderListStep).values.map { it.char })

            h.type(KeyEvent.VK_R, 'r')

            println("mouse group -> r: ${h.state()}")
            assertEquals("Mouse-entered child must execute instead of distinct root r; ${h.state()}",
                listOf("child"), h.executions)
        }
    }

    @Test
    fun `nested Escape returns one level and keeps root sibling available`() {
        LeaderPopupRegressionHarness(project).use { h ->
            val root = h.startWithProductionAction()
            h.type(KeyEvent.VK_C, 'c')
            val child = h.childOf(root)

            h.send(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
            val rootDisposedAfterEscape = root.isDisposed
            val captureCountAfterEscape = h.captureCount()
            h.type(KeyEvent.VK_S, 's')

            println("nested Escape -> sibling: rootDisposedAfterEscape=$rootDisposedAfterEscape, captureCountAfterEscape=$captureCountAfterEscape, ${h.state()}")
            assertFalse("Escape must close only the child and leave the root available; rootDisposedAfterEscape=$rootDisposedAfterEscape, captureCountAfterEscape=$captureCountAfterEscape, ${h.state()}",
                rootDisposedAfterEscape)
            assertTrue(child.isDisposed)
            assertEquals(1, captureCountAfterEscape)
            assertEquals(listOf("sibling"), h.executions)
        }
    }

    @Test
    fun `control real popup mouse selection executes the child action`() {
        LeaderPopupRegressionHarness(project).use { h ->
            val root = h.startWithProductionAction()
            h.selectViaMousePath(root, "c")
            val child = h.childOf(root)
            h.selectViaMousePath(child, "r")
            UIUtil.dispatchAllInvocationEvents()

            assertEquals("Real onChosen/finalRunnable must execute the registered action", listOf("child"), h.executions)
            assertTrue(root.isDisposed)
            assertTrue(child.isDisposed)
        }
    }

    @Test
    fun `control library Escape goes back one popup level`() {
        LeaderPopupRegressionHarness(project).use { h ->
            val root = h.startWithProductionAction()
            h.selectViaMousePath(root, "c")
            val child = h.childOf(root)

            // Bypass only the plugin dispatcher to verify the platform's existing contract.
            assertTrue(child.dispatch(KeyEvent(child.list, KeyEvent.KEY_PRESSED, 10_000, 0,
                KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)))

            assertTrue(child.isDisposed)
            assertFalse(root.isDisposed)
            assertEquals(1, h.captureCount())
            assertTrue(h.executions.isEmpty())
        }
    }

    @Test
    fun `control ShiftEscape dismisses the whole popup stack`() {
        LeaderPopupRegressionHarness(project).use { h ->
            val root = h.startWithProductionAction()
            h.type(KeyEvent.VK_C, 'c')
            val child = h.childOf(root)

            h.send(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED,
                modifiers = KeyEvent.SHIFT_DOWN_MASK)

            assertTrue(root.isDisposed)
            assertTrue(child.isDisposed)
            assertEquals(0, h.captureCount())
            h.type(KeyEvent.VK_R, 'r')
            assertTrue("No action after full dismissal", h.executions.isEmpty())
        }
    }
}
