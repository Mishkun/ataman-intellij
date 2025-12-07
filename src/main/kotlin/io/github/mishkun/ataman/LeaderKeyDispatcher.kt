package io.github.mishkun.ataman

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

private val LOG = Logger.getInstance(LeaderKeyDispatcher::class.java)

/**
 * Listener interface for LeaderKeyDispatcher state changes.
 */
interface LeaderStateListener {
    /** Called when a SingleBinding is matched and should be executed. */
    fun onBindingSelected(binding: LeaderBinding.SingleBinding)

    /** Called when entering a group (for nested popup creation). */
    fun onGroupEntered(group: LeaderBinding.GroupBinding)

    /** Called when the dispatcher is dismissed (escape or action complete). */
    fun onDismiss()
}

/**
 * State machine that captures all key events and matches against bindings.
 * This is the single source of truth for key handling in the leader popup flow.
 */
@Service(Service.Level.APP)
class LeaderKeyDispatcher {
    private var isCapturing = false
    private var keyEventDispatcher: KeyEventDispatcher? = null
    private val listeners = mutableListOf<LeaderStateListener>()

    private var currentBindings: List<LeaderBinding> = emptyList()
    private var currentBindingsMap: Map<KeyStroke, LeaderBinding> = emptyMap()
    private var currentCharMap: Map<Char, LeaderBinding> = emptyMap()
    private var dataContext: DataContext? = null

    /**
     * Start capturing key events and matching against the provided bindings.
     * Call this immediately when LeaderAction is invoked, before creating the popup.
     */
    fun start(bindings: List<LeaderBinding>, dataContext: DataContext) {
        if (isCapturing) {
            // Already capturing - stop first
            stop()
        }

        isCapturing = true
        currentBindings = bindings
        currentBindingsMap = bindings.toKeyStrokeMap()
        currentCharMap = bindings.toCharMap()
        this.dataContext = dataContext

        LOG.warn("LeaderKeyDispatcher started with ${bindings.size} bindings, charMap keys: ${currentCharMap.keys}")

        keyEventDispatcher = KeyEventDispatcher { event ->
            if (!isCapturing) return@KeyEventDispatcher false
            handleKeyEvent(event)
        }

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
            .addKeyEventDispatcher(keyEventDispatcher)

        LOG.warn("KeyEventDispatcher registered")
    }

    /**
     * Stop capturing key events and clean up.
     */
    fun stop() {
        isCapturing = false
        keyEventDispatcher?.let {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(it)
        }
        keyEventDispatcher = null
        currentBindings = emptyList()
        currentBindingsMap = emptyMap()
        currentCharMap = emptyMap()
        dataContext = null
    }

    /**
     * Update bindings for nested group navigation.
     * Called when entering a sub-group to update what keys we're matching against.
     */
    fun updateBindings(bindings: List<LeaderBinding>) {
        currentBindings = bindings
        currentBindingsMap = bindings.toKeyStrokeMap()
        currentCharMap = bindings.toCharMap()
    }

    fun addListener(listener: LeaderStateListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: LeaderStateListener) {
        listeners.remove(listener)
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        LOG.warn("LeaderKeyDispatcher received event: id=${event.id}, keyCode=${event.keyCode}, keyChar='${event.keyChar}', isCapturing=$isCapturing")

        when (event.id) {
            KeyEvent.KEY_PRESSED -> {
                LOG.warn("KEY_PRESSED: keyCode=${event.keyCode}")
                when (event.keyCode) {
                    KeyEvent.VK_ESCAPE -> {
                        LOG.warn("Escape pressed, dismissing")
                        notifyDismiss()
                        return true
                    }
                    // Skip modifier-only key presses
                    KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META -> {
                        return false
                    }
                }
                // Consume KEY_PRESSED to prevent other handlers, but wait for KEY_RELEASED to act
                return true
            }
            KeyEvent.KEY_RELEASED -> {
                LOG.warn("KEY_RELEASED: keyCode=${event.keyCode}, keyChar='${event.keyChar}'")
                // Skip modifier-only key releases
                when (event.keyCode) {
                    KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META, KeyEvent.VK_ESCAPE -> {
                        return false
                    }
                }
                val binding = findBindingForEvent(event)
                LOG.warn("Found binding: $binding for char '${event.keyChar}', charMap keys: ${currentCharMap.keys}")
                if (binding != null) {
                    processBinding(binding)
                    return true
                }
                // No match - consume anyway to prevent other handlers
                return true
            }
        }
        return false
    }

    private fun findBindingForEvent(event: KeyEvent): LeaderBinding? {
        // Match by character directly
        currentCharMap[event.keyChar]?.let { return it }

        // Also try matching by KeyStroke
        val keyStroke = event.toKeyStroke()
        if (keyStroke != null) {
            currentBindingsMap[keyStroke]?.let { return it }
        }

        return null
    }

    private fun processBinding(binding: LeaderBinding) {
        when (binding) {
            is LeaderBinding.SingleBinding -> {
                val ctx = dataContext
                if (ctx != null) {
                    binding.action.forEach { actionId ->
                        executeAction(actionId, ctx)
                    }
                }
                listeners.toList().forEach { it.onBindingSelected(binding) }
                notifyDismiss()
            }
            is LeaderBinding.GroupBinding -> {
                updateBindings(binding.bindings)
                listeners.toList().forEach { it.onGroupEntered(binding) }
            }
        }
    }

    private fun notifyDismiss() {
        listeners.toList().forEach { it.onDismiss() }
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
            KeyStroke.getKeyStroke(keyCode, modifiersEx, false)
        }
        KeyEvent.KEY_RELEASED -> {
            KeyStroke.getKeyStroke(keyCode, modifiersEx, true)
        }
        else -> null
    }
}
