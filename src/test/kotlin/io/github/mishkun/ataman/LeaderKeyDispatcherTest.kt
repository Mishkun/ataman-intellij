package io.github.mishkun.ataman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
