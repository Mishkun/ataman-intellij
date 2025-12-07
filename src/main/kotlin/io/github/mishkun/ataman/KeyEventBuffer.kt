package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.KeyStroke

/**
 * Service that intercepts and buffers key events during popup initialization.
 *
 * This solves the problem where keystrokes are lost when the user types quickly
 * after triggering the leader action, before the popup UI has finished rendering.
 */
@Service
class KeyEventBuffer {
    private val eventQueue = ConcurrentLinkedQueue<KeyEvent>()
    private var isCapturing = false
    private var keyEventDispatcher: KeyEventDispatcher? = null

    /**
     * Start capturing key events. Call this immediately when LeaderAction is invoked,
     * before the popup is created.
     */
    fun startCapturing() {
        if (isCapturing) return

        isCapturing = true
        eventQueue.clear()

        keyEventDispatcher = KeyEventDispatcher { event ->
            if (!isCapturing) return@KeyEventDispatcher false

            // Only capture KEY_TYPED for character input and KEY_PRESSED for navigation
            when (event.id) {
                KeyEvent.KEY_TYPED -> {
                    // Skip modifier-only events (char is CHAR_UNDEFINED for these)
                    if (event.keyChar != KeyEvent.CHAR_UNDEFINED) {
                        // Copy the event since AWT may reuse the object
                        eventQueue.offer(copyKeyEvent(event))
                        return@KeyEventDispatcher true // consume the event
                    }
                }
                KeyEvent.KEY_PRESSED -> {
                    // Capture escape key
                    when (event.keyCode) {
                        KeyEvent.VK_ESCAPE -> {
                            eventQueue.offer(copyKeyEvent(event))
                            return@KeyEventDispatcher true // consume the event
                        }
                        // Skip modifier-only key presses
                        KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META -> {
                            return@KeyEventDispatcher false
                        }
                    }
                }
            }
            false
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(keyEventDispatcher)
    }

    /**
     * Stop capturing key events. Call this when the popup is disposed.
     */
    fun stopCapturing() {
        isCapturing = false
        keyEventDispatcher?.let {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(it)
        }
        keyEventDispatcher = null
        eventQueue.clear()
    }

    /**
     * Drain all captured events from the buffer.
     * Call this after the popup is visible to replay buffered keystrokes.
     *
     * @return List of captured KeyEvents in order they were received
     */
    fun drainCapturedEvents(): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        while (true) {
            val event = eventQueue.poll() ?: break
            events.add(event)
        }
        return events
    }

    /**
     * Check if there are any buffered events.
     */
    fun hasBufferedEvents(): Boolean = eventQueue.isNotEmpty()

    /**
     * Copy a KeyEvent since AWT may reuse event objects.
     */
    private fun copyKeyEvent(event: KeyEvent): KeyEvent {
        return KeyEvent(
            event.source as? java.awt.Component ?: event.component,
            event.id,
            event.`when`,
            event.modifiersEx,
            event.keyCode,
            event.keyChar,
            event.keyLocation
        )
    }
}

/**
 * Utility to convert a KeyEvent to a KeyStroke for matching against bindings.
 */
fun KeyEvent.toKeyStroke(): KeyStroke? {
    return when (id) {
        KeyEvent.KEY_TYPED -> {
            if (keyChar != KeyEvent.CHAR_UNDEFINED) {
                KeyStroke.getKeyStroke(keyChar)
            } else null
        }
        KeyEvent.KEY_PRESSED -> {
            KeyStroke.getKeyStroke(keyCode, modifiersEx)
        }
        else -> null
    }
}
