package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import java.awt.AWTEvent
import java.awt.Component
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

/**
 * Service that aggressively blocks and buffers key events while the popup is being initialized.
 * This class uses multiple layers of event capture to ensure no key events escape processing.
 */
@Service
class KeyEventBuffer : AWTEventListener {
    private val keyEventQueue = ConcurrentLinkedQueue<KeyEvent>()
    private val isCapturing = AtomicBoolean(false)

    // Layer 1: Pre-dispatch listener at the KeyboardFocusManager level
    private val keyEventPreDispatchListener = KeyEventDispatcher { e ->
        if (isCapturing.get() && (e.id == KeyEvent.KEY_PRESSED || e.id == KeyEvent.KEY_TYPED)) {
            // Skip modifier keys by themselves
            if (e.id == KeyEvent.KEY_TYPED || !isModifierKey(e.keyCode)) {
                captureKeyEvent(e)
            }

            // Mark as consumed to prevent further processing
            e.consume()
            return@KeyEventDispatcher true
        }
        false
    }

    // Layer 2: Custom keyboard focus manager to override default dispatch behavior
    private val originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    private val customFocusManager = object : DefaultKeyboardFocusManager() {
        override fun dispatchKeyEvent(e: KeyEvent): Boolean {
            if (isCapturing.get()) {
                if (e.id == KeyEvent.KEY_PRESSED || e.id == KeyEvent.KEY_TYPED) {
                    // Skip modifier keys by themselves
                    if (e.id == KeyEvent.KEY_TYPED || !isModifierKey(e.keyCode)) {
                        captureKeyEvent(e)
                    }

                    // Mark as consumed to prevent further processing
                    e.consume()
                    return true
                }
            }
            return super.dispatchKeyEvent(e)
        }
    }

    // Layer 4: Direct component key listener for the focused component
    private val captureKeyListener = object : KeyListener {
        override fun keyPressed(e: KeyEvent) {
            if (isCapturing.get()) {
                // Skip modifier keys by themselves
                if (!isModifierKey(e.keyCode)) {
                    captureKeyEvent(e)
                    e.consume()
                }
            }
        }

        override fun keyReleased(e: KeyEvent) {
            if (isCapturing.get()) {
                e.consume()
            }
        }

        override fun keyTyped(e: KeyEvent) {
            if (isCapturing.get()) {
                e.consume()
            }
        }
    }

    private var currentFocusOwner: Component? = null
    private var rootPanes = mutableListOf<JRootPane>()

    /**
     * Start capturing key events at multiple levels to ensure complete blocking
     */
    fun startCapturing() {
        if (!isCapturing.getAndSet(true)) {
            try {
                // Layer 1: Add pre-dispatch interceptor
                originalFocusManager.addKeyEventDispatcher(keyEventPreDispatchListener)

                // Layer 2: Replace the keyboard focus manager (extreme approach)
                KeyboardFocusManager.setCurrentKeyboardFocusManager(customFocusManager)

                // Layer 3: Capture at AWT event dispatch level (catches system-level events)
                Toolkit.getDefaultToolkit().addAWTEventListener(
                    this,
                    AWTEvent.KEY_EVENT_MASK or AWTEvent.FOCUS_EVENT_MASK
                )

                // Layer 4: Add direct key listeners to currently focused component
                SwingUtilities.invokeLater {
                    try {
                        currentFocusOwner = originalFocusManager.focusOwner
                        currentFocusOwner?.addKeyListener(captureKeyListener)

                        // Add key listeners to all root panes to catch events that might be missed
                        Window.getWindows().forEach { window ->
                            val rootPane = SwingUtilities.getRootPane(window)
                            if (rootPane != null) {
                                rootPanes.add(rootPane)
                                rootPane.addKeyListener(captureKeyListener)

                                // Also disable all key bindings temporarily
                                if (rootPane is JComponent) {
                                    rootPane.inputMap.keys()?.toList()?.forEach { key ->
                                        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                                            .put(key as KeyStroke?, "none")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Error setting up component-level key listeners
                    }
                }

            } catch (e: Exception) {
                stopCapturing() // Clean up in case of partial initialization
                throw e
            }
        }
    }

    /**
     * Stop capturing key events and restore normal event processing
     */
    fun stopCapturing() {
        if (isCapturing.getAndSet(false)) {
            try {
                // Remove Layer 1: Pre-dispatch interceptor
                originalFocusManager.removeKeyEventDispatcher(keyEventPreDispatchListener)

                // Remove Layer 2: Restore original keyboard focus manager
                KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager)

                // Remove Layer 3: AWT event listeners
                Toolkit.getDefaultToolkit().removeAWTEventListener(this)

                // Remove Layer 4: Component-level key listeners
                SwingUtilities.invokeLater {
                    try {
                        currentFocusOwner?.removeKeyListener(captureKeyListener)
                        currentFocusOwner = null

                        // Remove key listeners from all root panes
                        rootPanes.forEach { rootPane ->
                            rootPane.removeKeyListener(captureKeyListener)
                        }
                        rootPanes.clear()
                    } catch (e: Exception) {
                        // Error cleaning up component-level key listeners
                    }
                }
            } catch (e: Exception) {
                // Error stopping key event capture
            }
        }
    }

    /**
     * Get and clear all captured key events
     */
    fun drainCapturedEvents(): List<KeyStroke> {
        val events = mutableListOf<KeyStroke>()
        while (keyEventQueue.isNotEmpty()) {
            val event = keyEventQueue.poll()
            val keyStroke = KeyStroke.getKeyStrokeForEvent(event)
            events.add(keyStroke)
        }
        return events
    }

    /**
     * Clear all captured events without processing them
     */
    fun clearCapturedEvents() {
        keyEventQueue.clear()
    }

    /**
     * Safely capture a key event and add it to the queue
     */
    private fun captureKeyEvent(e: KeyEvent) {
        try {
            // Skip modifier keys by themselves
            if (e.id == KeyEvent.KEY_TYPED || !isModifierKey(e.keyCode)) {
                // Create a copy of the event to avoid issues with event reuse
                val eventCopy = KeyEvent(
                    e.component,
                    e.id,
                    e.`when`,
                    e.modifiers,
                    e.keyCode,
                    e.keyChar,
                    e.keyLocation
                )
                keyEventQueue.add(eventCopy)
            }
        } catch (ex: Exception) {
            // Error capturing key event
        }
    }

    /**
     * AWTEventListener implementation - our third layer of defense
     */
    override fun eventDispatched(event: AWTEvent) {
        if (isCapturing.get()) {
            when (event) {
                is KeyEvent -> {
                    if (event.id == KeyEvent.KEY_PRESSED || event.id == KeyEvent.KEY_TYPED) {
                        // Skip modifier keys by themselves for KEY_PRESSED
                        if (event.id == KeyEvent.KEY_TYPED || !isModifierKey(event.keyCode)) {
                            captureKeyEvent(event)
                        }

                        // Always consume the event to prevent further processing
                        event.consume()
                    }
                }
            }
        }
    }

    /**
     * Check if a key is a modifier key (Shift, Ctrl, Alt, Meta)
     */
    private fun isModifierKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.VK_SHIFT ||
                keyCode == KeyEvent.VK_CONTROL ||
                keyCode == KeyEvent.VK_ALT ||
                keyCode == KeyEvent.VK_META
    }
}
