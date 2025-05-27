package io.github.mishkun.ataman

import com.intellij.openapi.components.Service
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.KeyStroke

/**
 * Service that manages key actions for leader bindings.
 * Creates and stores actions ahead of time to optimize popup performance.
 */
@Service
class KeyActionManager {
    // Map of action key to pre-created action
    private val actionMap = mutableMapOf<String, AbstractAction>()

    /**
     * Create and store actions for all bindings ahead of time
     */
    fun prepareActionsForBindings(bindings: List<LeaderBinding>) {
        actionMap.clear()
        bindings.forEach { binding ->
            prepareActionForBinding(binding)
        }
    }

    /**
     * Recursively prepare actions for binding and all its children
     */
    private fun prepareActionForBinding(binding: LeaderBinding) {
        val actionKey = getActionKey(binding.key)
        actionMap[actionKey] = createActionTemplate(binding)

        // If this is a group binding, prepare actions for all child bindings
        if (binding is LeaderBinding.GroupBinding) {
            binding.bindings.forEach { childBinding ->
                prepareActionForBinding(childBinding)
            }
        }
    }

    /**
     * Get a pre-created action for a binding by its key
     */
    fun getActionForBinding(bindingKey: KeyStroke): AbstractAction? {
        val actionKey = getActionKey(bindingKey)
        return actionMap[actionKey]
    }

    /**
     * Create a template action for a binding
     * This action will be configured with a callback when used in a popup
     */
    private fun createActionTemplate(binding: LeaderBinding): AbstractAction {
        return object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                // This will be replaced with a callback when used in a popup
            }
        }
    }

    /**
     * Generate a unique action key for a KeyStroke
     */
    private fun getActionKey(keyStroke: KeyStroke): String {
        return "handle${keyStroke.keyCode}_${keyStroke.modifiers}_${keyStroke.isOnKeyRelease}"
    }
}
