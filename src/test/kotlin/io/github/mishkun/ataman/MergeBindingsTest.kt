package io.github.mishkun.ataman

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test

class MergeBindingsTest {

    @Test
    fun `replaces with override if type mismatches`() {
        val singleBindingOriginal = LeaderBinding.GroupBinding(
            getKeyStroke('c'),
            "c",
            "Comment...",
            bindings = listOf(
                LeaderBinding.SingleBinding(
                    getKeyStroke('c'),
                    "c",
                    "CommentAction",
                    "CommentByLineComment"
                )
            )
        )
        val singleBindingOverriden = LeaderBinding.SingleBinding(
            getKeyStroke('c'),
            "c",
            "DeleteAction",
            "DeleteLine"
        )
        val bindings = mergeBindings(
            bindingConfig = listOf(
                singleBindingOriginal
            ),
            overrideConfig = listOf(
                singleBindingOverriden
            )
        )
        assertThat(
            bindings, Matchers.containsInAnyOrder(
                singleBindingOverriden
            )
        )
    }

    @Test
    fun `merges nested binding`() {
        val singleBindingOriginal = LeaderBinding.SingleBinding(
            getKeyStroke('c'),
            "c",
            "CommentAction",
            "CommentByLineComment"
        )
        val singleBindingOverriden = LeaderBinding.SingleBinding(
            getKeyStroke('d'),
            "d",
            "DeleteAction",
            "DeleteLine"
        )
        val bindings = mergeBindings(
            bindingConfig = listOf(
                LeaderBinding.GroupBinding(
                    getKeyStroke('c'),
                    "c",
                    description = "Comment...",
                    listOf(
                        singleBindingOriginal
                    )
                )
            ),
            overrideConfig = listOf(
                LeaderBinding.GroupBinding(
                    getKeyStroke('c'),
                    "c",
                    description = "Deletion...",
                    listOf(
                        singleBindingOverriden
                    )
                )
            )
        )
        assertThat(
            bindings, Matchers.containsInAnyOrder(
                LeaderBinding.GroupBinding(
                    getKeyStroke('c'),
                    "c",
                    description = "Deletion...",
                    listOf(
                        singleBindingOriginal,
                        singleBindingOverriden
                    )
                )
            )
        )
    }

    @Test
    fun `overrides top level binding`() {
        val singleBindingOriginal = LeaderBinding.SingleBinding(
            getKeyStroke('c'),
            "c",
            "CommentAction",
            "CommentByLineComment"
        )
        val singleBindingOverriden = LeaderBinding.SingleBinding(
            getKeyStroke('c'),
            "c",
            "DeleteAction",
            "DeleteLine"
        )
        val bindings = mergeBindings(
            bindingConfig = listOf(
                singleBindingOriginal
            ),
            overrideConfig = listOf(
                singleBindingOverriden
            )
        )
        assertThat(
            bindings, Matchers.containsInAnyOrder(
                singleBindingOverriden
            )
        )
    }

    @Test
    fun `merges top level bindings`() {
        val singleBindingOriginal = LeaderBinding.SingleBinding(
            getKeyStroke('c'),
            "c",
            "CommentAction",
            "CommentByLineComment"
        )
        val singleBindingOverriden = LeaderBinding.SingleBinding(
            getKeyStroke('d'),
            "d",
            "DeleteAction",
            "DeleteLine"
        )
        val bindings = mergeBindings(
            bindingConfig = listOf(
                singleBindingOriginal
            ),
            overrideConfig = listOf(
                singleBindingOverriden
            )
        )
        assertThat(
            bindings, Matchers.containsInAnyOrder(
                singleBindingOriginal,
                singleBindingOverriden
            )
        )
    }
}
