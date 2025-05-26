package io.github.mishkun.ataman

import javax.swing.KeyStroke as SwingKeyStroke

/**
 * Represents a leader binding configuration.
 * Each binding has a key, character representation, and description.
 */
sealed class LeaderBinding {
    abstract val key: SwingKeyStroke
    abstract val char: String
    abstract val description: String

    /**
     * A binding that executes one or more actions when selected.
     */
    data class SingleBinding(
        override val key: SwingKeyStroke,
        override val char: String,
        override val description: String,
        val action: List<String>
    ) : LeaderBinding() {
        constructor(key: SwingKeyStroke, char: String, description: String, action: String) : this(
            key, char, description, listOf(action)
        )
    }

    /**
     * A binding that shows a submenu of other bindings when selected.
     */
    data class GroupBinding(
        override val key: SwingKeyStroke,
        override val char: String,
        override val description: String,
        val bindings: List<LeaderBinding>
    ) : LeaderBinding()
}
