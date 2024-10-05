package io.github.mishkun.ataman

import io.github.mishkun.ataman.core.setupStubConfigDir
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class AtamanConfigParsingTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `supports f keys`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(
                text = """
                |bindings { 
                |  F1 { actionId: CommentByLineComment, description: Comment }
                |  F12 { actionId: OpenAtamanConfigAction, description: Open ~/.atamanrc.config } 
                |}""".trimMargin()
            )
        )
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0, true),
                        "F1",
                        "Comment",
                        "CommentByLineComment",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0, true),
                        "F12",
                        "Open ~/.atamanrc.config",
                        "OpenAtamanConfigAction",
                    )
                )
            )
        )
    }

    @Test
    fun `returns error on malformed config`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(text = "}malformed{")
        )
        assertThat(parsedBindings.isFailure, Matchers.equalTo(true))
    }

    @Test
    fun `throws if bindings are not set up properly`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(text = "bindings { q { description: Session } }")
        )
        assertThat(parsedBindings.isFailure, Matchers.equalTo(true))
        println(parsedBindings.exceptionOrNull())
    }

    @Test
    fun `parses config to the list of bindings`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(
                text = """
                |bindings {
                |  q { 
                |    description: Session...
                |    bindings {
                |      F { actionId: OpenAtamanConfigAction, description: Open ~/.atamanrc.config }
                |    }
                |  }
                |}""".trimMargin()
            )
        )
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.GroupBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, true),
                        "q",
                        "Session...",
                        listOf(
                            LeaderBinding.SingleBinding(
                                KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.SHIFT_DOWN_MASK, true),
                                "F",
                                "Open ~/.atamanrc.config",
                                "OpenAtamanConfigAction",
                            )
                        )
                    )
                )
            )
        )
    }
}
