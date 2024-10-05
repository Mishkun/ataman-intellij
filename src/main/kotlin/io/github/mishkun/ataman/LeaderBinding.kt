package io.github.mishkun.ataman

import javax.swing.KeyStroke

sealed class LeaderBinding {
    abstract val key: KeyStroke
    abstract val char: String
    abstract val description: String

    data class SingleBinding(
        override val key: KeyStroke,
        override val char: String,
        override val description: String,
        val action: List<String>
    ) : LeaderBinding() {
        constructor(
            key: KeyStroke,
            char: String,
            description: String,
            action: String
        ) : this(key, char, description, listOf(action))
    }

    data class GroupBinding(
        override val key: KeyStroke,
        override val char: String,
        override val description: String,
        val bindings: List<LeaderBinding>
    ) : LeaderBinding()
}
