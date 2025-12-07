package io.github.mishkun.ataman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class ConfigServiceTest {

    @Test
    fun `bindingsMap is empty when parsedBindings is empty`() {
        val service = ConfigService()
        assertTrue(service.bindingsMap.isEmpty())
    }

    @Test
    fun `bindingsMap is built when parsedBindings is set`() {
        val service = ConfigService()
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeB = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true)

        val bindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA"),
            LeaderBinding.SingleBinding(keyStrokeB, "b", "Action B", "ActionB")
        )

        service.parsedBindings = bindings

        assertEquals(2, service.bindingsMap.size)
        assertEquals(bindings[0], service.bindingsMap[keyStrokeA])
        assertEquals(bindings[1], service.bindingsMap[keyStrokeB])
    }

    @Test
    fun `bindingsMap is updated when parsedBindings changes`() {
        val service = ConfigService()
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeC = KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, true)

        val initialBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA")
        )
        service.parsedBindings = initialBindings

        val newBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeC, "c", "Action C", "ActionC")
        )
        service.parsedBindings = newBindings

        assertEquals(1, service.bindingsMap.size)
        assertTrue(service.bindingsMap.containsKey(keyStrokeC))
        assertTrue(!service.bindingsMap.containsKey(keyStrokeA))
    }

    @Test
    fun `buildBindingsMap creates correct map for group bindings`() {
        val service = ConfigService()
        val keyStrokeG = KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false)
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)

        val nestedBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Nested Action", "NestedAction")
        )
        val groupBinding = LeaderBinding.GroupBinding(keyStrokeG, "g", "Group", nestedBindings)

        val map = service.buildBindingsMap(listOf(groupBinding))

        assertEquals(1, map.size)
        assertEquals(groupBinding, map[keyStrokeG])
    }

    @Test
    fun `buildBindingsMap can build map for nested bindings`() {
        val service = ConfigService()
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeB = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true)

        val nestedBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA"),
            LeaderBinding.SingleBinding(keyStrokeB, "b", "Action B", "ActionB")
        )

        val nestedMap = service.buildBindingsMap(nestedBindings)

        assertEquals(2, nestedMap.size)
        assertEquals(nestedBindings[0], nestedMap[keyStrokeA])
        assertEquals(nestedBindings[1], nestedMap[keyStrokeB])
    }
}
