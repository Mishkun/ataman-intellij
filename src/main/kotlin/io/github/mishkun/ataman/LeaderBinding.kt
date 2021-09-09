package io.github.mishkun.ataman

import javax.swing.KeyStroke

sealed class LeaderBinding {
    abstract val key: KeyStroke
    abstract val char: Char
    abstract val description: String

    data class SingleBinding(
        override val key: KeyStroke,
        override val char: Char,
        override val description: String,
        val action: String
    ) : LeaderBinding()

    data class GroupBinding(
        override val key: KeyStroke,
        override val char: Char,
        override val description: String,
        val bindings: List<LeaderBinding>
    ) : LeaderBinding()
}
