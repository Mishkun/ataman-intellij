package io.github.mishkun.ataman

import org.junit.After
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

class KeyEventBufferTest {

    private lateinit var buffer: KeyEventBuffer
    private lateinit var dummyComponent: Component

    @Before
    fun setUp() {
        buffer = KeyEventBuffer()
        dummyComponent = JPanel()
    }

    @After
    fun tearDown() {
        buffer.stopCapturing()
    }

    @Test
    fun `drainCapturedEvents returns empty list when not capturing`() {
        val events = buffer.drainCapturedEvents()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `drainCapturedEvents returns empty list after stopCapturing`() {
        buffer.startCapturing()
        buffer.stopCapturing()
        val events = buffer.drainCapturedEvents()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `hasBufferedEvents returns false when buffer is empty`() {
        assertFalse(buffer.hasBufferedEvents())
    }

    @Test
    fun `startCapturing can be called multiple times safely`() {
        buffer.startCapturing()
        buffer.startCapturing() // Should not throw
        buffer.stopCapturing()
    }

    @Test
    fun `stopCapturing can be called without startCapturing`() {
        buffer.stopCapturing() // Should not throw
    }

    @Test
    fun `stopCapturing clears the buffer`() {
        buffer.startCapturing()
        buffer.stopCapturing()
        assertFalse(buffer.hasBufferedEvents())
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
    fun `toKeyStroke returns null for KEY_RELEASED event`() {
        val event = KeyEvent(
            dummyComponent,
            KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(),
            0,
            KeyEvent.VK_A,
            'a'
        )
        val keyStroke = event.toKeyStroke()
        assertNull(keyStroke)
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
