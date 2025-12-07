package io.github.mishkun.ataman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class LeaderBindingExtensionsTest {

    @Test
    fun `toKeyStrokeMap returns empty map for empty list`() {
        val bindings = emptyList<LeaderBinding>()
        assertTrue(bindings.toKeyStrokeMap().isEmpty())
    }

    @Test
    fun `toKeyStrokeMap creates map keyed by keystroke`() {
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeB = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true)

        val bindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA"),
            LeaderBinding.SingleBinding(keyStrokeB, "b", "Action B", "ActionB")
        )

        val map = bindings.toKeyStrokeMap()

        assertEquals(2, map.size)
        assertEquals(bindings[0], map[keyStrokeA])
        assertEquals(bindings[1], map[keyStrokeB])
    }

    @Test
    fun `toKeyStrokeMap works with group bindings`() {
        val keyStrokeG = KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false)
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)

        val nestedBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Nested Action", "NestedAction")
        )
        val groupBinding = LeaderBinding.GroupBinding(keyStrokeG, "g", "Group", nestedBindings)

        val map = listOf(groupBinding).toKeyStrokeMap()

        assertEquals(1, map.size)
        assertEquals(groupBinding, map[keyStrokeG])
    }

    @Test
    fun `toKeyStrokeMap works for nested bindings list`() {
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeB = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true)

        val nestedBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA"),
            LeaderBinding.SingleBinding(keyStrokeB, "b", "Action B", "ActionB")
        )

        val nestedMap = nestedBindings.toKeyStrokeMap()

        assertEquals(2, nestedMap.size)
        assertEquals(nestedBindings[0], nestedMap[keyStrokeA])
        assertEquals(nestedBindings[1], nestedMap[keyStrokeB])
    }
}
