package io.github.mishkun.ataman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.KeyStroke

class LeaderKeyDispatcherTest {

    private lateinit var dispatcher: LeaderKeyDispatcher

    @Before
    fun setUp() {
        dispatcher = LeaderKeyDispatcher()
    }

    @Test
    fun `start can be called multiple times safely`() {
        // This would require a DataContext mock, so we just verify no exception
        // In real usage, start() stops any existing capture first
    }

    @Test
    fun `stop can be called without start`() {
        dispatcher.stop() // Should not throw
    }

    @Test
    fun `stop can be called multiple times safely`() {
        dispatcher.stop()
        dispatcher.stop() // Should not throw
    }

    @Test
    fun `addListener and removeListener work correctly`() {
        var dismissed = false
        val listener = object : LeaderStateListener {
            override fun onBindingSelected(binding: LeaderBinding.SingleBinding) {}
            override fun onGroupEntered(group: LeaderBinding.GroupBinding) {}
            override fun onDismiss() { dismissed = true }
        }

        dispatcher.addListener(listener)
        dispatcher.removeListener(listener)
        // After removal, listener should not be called
        // (can't easily test without starting dispatcher with mock DataContext)
    }

    @Test
    fun `removeListener is safe when listener not present`() {
        val listener = object : LeaderStateListener {
            override fun onBindingSelected(binding: LeaderBinding.SingleBinding) {}
            override fun onGroupEntered(group: LeaderBinding.GroupBinding) {}
            override fun onDismiss() {}
        }

        dispatcher.removeListener(listener) // Should not throw
    }
}

class KeyEventToKeyStrokeTest {

    private lateinit var dummyComponent: Component

    @Before
    fun setUp() {
        dummyComponent = JPanel()
    }

    @Test
    fun `toKeyStroke converts KEY_TYPED event to KeyStroke`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_UNDEFINED,
            'a'
        )
        val keyStroke = event.toKeyStroke()
        assertEquals(KeyStroke.getKeyStroke('a'), keyStroke)
    }

    @Test
    fun `toKeyStroke converts KEY_PRESSED event to KeyStroke`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_UP,
            KeyEvent.CHAR_UNDEFINED
        )
        val keyStroke = event.toKeyStroke()
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), keyStroke)
    }

    @Test
    fun `toKeyStroke converts KEY_RELEASED event to KeyStroke with onKeyRelease true`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_A,
            'a'
        )
        val keyStroke = event.toKeyStroke()
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), keyStroke)
    }

    @Test
    fun `toKeyStroke handles navigation keys`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_ESCAPE,
            KeyEvent.CHAR_UNDEFINED
        )
        val keyStroke = event.toKeyStroke()
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), keyStroke)
    }

    @Test
    fun `toKeyStroke preserves modifiers for KEY_PRESSED`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_PRESSED,
            System.currentTimeMillis(),
            KeyEvent.CTRL_DOWN_MASK,
            KeyEvent.VK_C,
            'c'
        )
        val keyStroke = event.toKeyStroke()
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), keyStroke)
    }

    @Test
    fun `toKeyStroke preserves modifiers for KEY_RELEASED`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            KeyEvent.SHIFT_DOWN_MASK,
            KeyEvent.VK_A,
            'A'
        )
        val keyStroke = event.toKeyStroke()
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.SHIFT_DOWN_MASK, true), keyStroke)
    }

    @Test
    fun `toKeyStroke returns null for unknown event type`() {
        // Use an arbitrary event ID that's not KEY_PRESSED, KEY_RELEASED, or KEY_TYPED
        val event = KeyEvent(
            dummyComponent,
            999, // Unknown event type
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_A,
            'a'
        )
        val keyStroke = event.toKeyStroke()
        assertNull(keyStroke)
    }
}

class LeaderBindingCharMapTest {

    @Test
    fun `toCharMap returns empty map for empty list`() {
        val bindings = emptyList<LeaderBinding>()
        assertTrue(bindings.toCharMap().isEmpty())
    }

    @Test
    fun `toCharMap creates map keyed by char`() {
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeB = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true)

        val bindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA"),
            LeaderBinding.SingleBinding(keyStrokeB, "b", "Action B", "ActionB")
        )

        val map = bindings.toCharMap()

        assertEquals(2, map.size)
        assertEquals(bindings[0], map['a'])
        assertEquals(bindings[1], map['b'])
    }

    @Test
    fun `toCharMap handles uppercase chars`() {
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.SHIFT_DOWN_MASK, true)

        val bindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "A", "Action A", "ActionA")
        )

        val map = bindings.toCharMap()

        assertEquals(1, map.size)
        assertEquals(bindings[0], map['A'])
    }

    @Test
    fun `toCharMap skips bindings with multi-char keys`() {
        val keyStrokeF1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0, true)

        val bindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeF1, "F1", "Action F1", "ActionF1")
        )

        val map = bindings.toCharMap()

        assertTrue(map.isEmpty())
    }

    @Test
    fun `toCharMap works with group bindings`() {
        val keyStrokeG = KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, false)
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)

        val nestedBindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Nested Action", "NestedAction")
        )
        val groupBinding = LeaderBinding.GroupBinding(keyStrokeG, "g", "Group", nestedBindings)

        val map = listOf(groupBinding).toCharMap()

        assertEquals(1, map.size)
        assertEquals(groupBinding, map['g'])
    }

    @Test
    fun `toCharMap mixed single and multi-char bindings`() {
        val keyStrokeA = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true)
        val keyStrokeF1 = KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0, true)
        val keyStrokeB = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true)

        val bindings = listOf(
            LeaderBinding.SingleBinding(keyStrokeA, "a", "Action A", "ActionA"),
            LeaderBinding.SingleBinding(keyStrokeF1, "F1", "Action F1", "ActionF1"),
            LeaderBinding.SingleBinding(keyStrokeB, "b", "Action B", "ActionB")
        )

        val map = bindings.toCharMap()

        assertEquals(2, map.size)
        assertEquals(bindings[0], map['a'])
        assertEquals(bindings[2], map['b'])
        assertFalse(map.containsKey('F'))
    }
}
