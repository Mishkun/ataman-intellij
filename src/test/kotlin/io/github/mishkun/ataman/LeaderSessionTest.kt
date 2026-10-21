package io.github.mishkun.ataman

import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import io.github.mishkun.ataman.LeaderStroke.*
import org.junit.Assert.assertThrows as expectThrows
import org.junit.Test
import java.awt.event.KeyEvent

class LeaderSessionTest : LightPlatform4TestCase() {
    @Test
    fun `configured Enter group overrides native selection`() {
        LeaderInputHarness(project, extraBindings = """
            "\n" {
                description: "Configured Enter group"
                bindings { a { description: "Child a", actionId: "Ataman.InputRegression.child" } }
            }
        """, onShow = { popup ->
            if (popup.parent == null) {
                val values = (popup.step as LeaderListStep).values
                val group = values.single { it.char == "\n" } as LeaderBinding.GroupBinding
                assertEquals(KeyEvent.VK_ENTER, group.key.keyCode)
                assertFalse(group.key.isOnKeyRelease)
                popup.list.setSelectedValue(values.single { it.char == "r" }, true)
            }
        }).use { h ->
            h.popupEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n')
            h.popupEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, '\n')
            h.popupInput(A_DOWN, A_UP)
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `configured Escape group overrides native back`() {
        LeaderInputHarness(project, extraBindings = """
            "\u001b" {
                description: "Configured Escape group"
                bindings { a { description: "Child a", actionId: "Ataman.InputRegression.child" } }
            }
        """, onShow = { popup ->
            if (popup.parent == null) {
                val group = (popup.step as LeaderListStep).values.single { it.char == "\u001b" }
                    as LeaderBinding.GroupBinding
                assertEquals(KeyEvent.VK_ESCAPE, group.key.keyCode)
                assertFalse(group.key.isOnKeyRelease)
            }
        }).use { h ->
            h.popupInput(ESC_DOWN, ESC_UP, A_DOWN, A_UP)
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `registered Escape during root show follows preceding group input`() {
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent == null) popupInput(C_DOWN, ESC_DOWN)
        }).use { h ->
            h.input(S_DOWN, S_UP)
            h.drainEdt()
            assertEquals(listOf("sibling"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `registered Enter during root show selects child after preceding group input`() {
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent == null) {
                val rootLeaf = (popup.step as LeaderListStep).values.single { it.char == "r" }
                popup.list.setSelectedValue(rootLeaf, true)
                popupInput(C_DOWN)
                popupEvent(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, '\n')
            }
        }).use { h ->
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `platform popup-first dispatch uses the same binding handler`() {
        LeaderInputHarness(project).use { h ->
            h.event(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED, KeyEvent.CTRL_DOWN_MASK)
            h.popupInput(C_DOWN, R_DOWN)
            assertTrue(h.popupEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'r'))
            assertTrue(h.actions.isEmpty())
            h.popupInput(R_UP, C_UP)
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `nested group back restores parent bindings`() {
        LeaderInputHarness(project, nestedGroup = true).use { h ->
            h.input(C_DOWN, C_UP)
            h.event(KeyEvent.KEY_PRESSED, KeyEvent.VK_D, 'd')
            h.event(KeyEvent.KEY_RELEASED, KeyEvent.VK_D, 'd')
            h.input(ESC_DOWN, ESC_UP, R_DOWN, R_UP)
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `nested event loop before show buffers FIFO and consumes typed input without early action`() {
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent == null) {
                input(C_DOWN)
                assertTrue(event(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, 'c'))
                queuedInput(R_DOWN, R_UP, C_UP)
                assertTrue("No action before root show returns", actions.isEmpty())
            }
        }).use { h ->
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `cancel before ready discards buffered input`() {
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent == null) {
                queuedInput(R_DOWN, R_UP)
                popup.cancel()
            }
        }).use { h ->
            h.drainEdt()
            assertTrue(h.actions.isEmpty())
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `replacement invalidates old buffer and old close cannot stop new session`() {
        var replaced = false
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent == null && !replaced) {
                replaced = true
                queuedInput(R_DOWN, R_UP)
                popup.addListener(object : JBPopupListener {
                    override fun onClosed(event: LightweightWindowEvent) { openLeader() }
                })
                openLeader()
                popup.cancel()
            }
        }).use { h ->
            assertTrue(h.actions.isEmpty())
            h.input(S_DOWN, S_UP)
            h.drainEdt()
            assertEquals(listOf("sibling"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `input pumped while mouse child is showing waits for child navigation`() {
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent != null) {
                queuedInput(R_DOWN, R_UP)
                assertTrue("Child show must finish before buffered action", actions.isEmpty())
            }
        }).use { h ->
            h.mouseSelect("c")
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `final action runs after close with original project and may open a new leader`() {
        var reopened = false
        LeaderInputHarness(project, onAction = { name, event ->
            if (name == "child") {
                assertFalse("Old chain must close before action", hasOpenPopup())
                assertSame(project, event.project)
                openLeader()
                reopened = true
            }
        }).use { h ->
            h.queuedInput(C_DOWN, R_DOWN, R_UP, C_UP)
            h.drainEdt()
            assertTrue(reopened)
            assertTrue(h.hasOpenPopup())
            h.input(S_DOWN, S_UP)
            h.drainEdt()
            assertEquals(listOf("child", "sibling"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }

    @Test
    fun `show exception closes its session and drops buffered input`() {
        var failShow = false
        val original = IllegalStateException("show failed")
        LeaderInputHarness(project, onShow = { popup ->
            if (popup.parent == null && failShow) {
                queuedInput(R_DOWN, R_UP)
                throw original
            }
        }).use { h ->
            failShow = true
            assertSame(original, expectThrows(IllegalStateException::class.java) { h.openLeader() })
            assertFalse(h.hasOpenPopup())
            h.input(S_DOWN, S_UP)
            h.drainEdt()
            assertTrue(h.actions.isEmpty())
        }
    }

    @Test
    fun `layout character fallback honors phase and modifiers`() {
        LeaderInputHarness(project).use { h ->
            h.input(C_DOWN, C_UP)
            h.event(KeyEvent.KEY_PRESSED, KeyEvent.VK_R, 'r', KeyEvent.CTRL_DOWN_MASK)
            h.event(KeyEvent.KEY_RELEASED, KeyEvent.VK_R, 'r', KeyEvent.CTRL_DOWN_MASK)
            assertTrue(h.actions.isEmpty())
            // A layout can deliver the configured character with a different physical code.
            h.event(KeyEvent.KEY_PRESSED, KeyEvent.VK_Z, 'r')
            assertTrue(h.actions.isEmpty())
            h.event(KeyEvent.KEY_RELEASED, KeyEvent.VK_Z, 'r')
            h.drainEdt()
            assertEquals(listOf("child"), h.actions)
            assertFalse(h.hasOpenPopup())
            h.openLeader()
            // Prefer the delivered character even if its physical code is another binding.
            h.event(KeyEvent.KEY_PRESSED, KeyEvent.VK_S, 'r')
            h.event(KeyEvent.KEY_RELEASED, KeyEvent.VK_S, 'r')
            h.drainEdt()
            assertEquals(listOf("child", "root"), h.actions)
            assertFalse(h.hasOpenPopup())
        }
    }
}
