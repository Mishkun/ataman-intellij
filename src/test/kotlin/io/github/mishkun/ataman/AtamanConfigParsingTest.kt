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
    fun `supports special characters`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(
                text = """
                |bindings { 
                |  "." { actionId: CommentByLineComment, description: Comment }
                |  ">" { actionId: CommentByLineComment, description: Comment }
                |}""".trimMargin()
            ),
            ideProductKey = "IC"
        )
        assertThat(parsedBindings.exceptionOrNull(), Matchers.nullValue())
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0),
                        ".",
                        "Comment",
                        "CommentByLineComment",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, KeyEvent.SHIFT_DOWN_MASK),
                        ">",
                        "Comment",
                        "CommentByLineComment",
                    )
                )
            )
        )
    }

    @Test
    fun `supports multibindings`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(
                text = """
                |bindings { 
                |  w { actionId: [SplitVertically, Unsplit], description: Split vertically and unsplit }
                |}""".trimMargin()
            ),
            ideProductKey = "IC"
        )
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_W, 0),
                        "w",
                        "Split vertically and unsplit",
                        listOf("SplitVertically", "Unsplit")
                    )
                )
            )
        )
    }

    @Test
    fun `merges ide specific config`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(
                text = """
                |bindings { 
                |  q { actionId: CommentByLineComment, description: Comment }
                |}
                |IU {
                | q { actionId: OpenAtamanConfigAction, description: Open ~/.atamanrc.config }
                |}""".trimMargin()
            ), ideProductKey = "IU"
        )
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0),
                        "q",
                        "Open ~/.atamanrc.config",
                        "OpenAtamanConfigAction",
                    )
                )
            )
        )
    }

    @Test
    fun `supports f keys and does not mistake them with capitalized F`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(
                text = """
                |bindings { 
                |  F { actionId: Unsplit, description: Unsplit }
                |  F1 { actionId: CommentByLineComment, description: Comment }
                |  F12 { actionId: OpenAtamanConfigAction, description: Open ~/.atamanrc.config } 
                |}""".trimMargin()
            ),
            ideProductKey = "IC"
        )
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.SHIFT_DOWN_MASK),
                        "F",
                        "Unsplit",
                        "Unsplit",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                        "F1",
                        "Comment",
                        "CommentByLineComment",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0),
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
            configDir = tmpFolder.setupStubConfigDir(text = "}malformed{"),
            ideProductKey = "IC"
        )
        assertThat(parsedBindings.isFailure, Matchers.equalTo(true))
    }

    @Test
    fun `throws if bindings are not set up properly`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(text = "bindings { q { description: Session } }"),
            ideProductKey = "IC"
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
            ),
            ideProductKey = "IC"
        )
        parsedBindings.exceptionOrNull()?.printStackTrace()
        assertThat(parsedBindings.exceptionOrNull(), Matchers.nullValue())
        assertThat(
            parsedBindings.getOrNull()!!, Matchers.equalTo(
                listOf(
                    LeaderBinding.GroupBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0),
                        "q",
                        "Session...",
                        listOf(
                            LeaderBinding.SingleBinding(
                                KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.SHIFT_DOWN_MASK),
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
