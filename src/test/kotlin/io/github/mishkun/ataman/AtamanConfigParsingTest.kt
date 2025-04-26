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
        
        // Get the parsed bindings
        val bindings = parsedBindings.getOrNull()!!
        
        assertThat(
            bindings, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0, true), // Use key release for leaf binding
                        ".",
                        "Comment",
                        "CommentByLineComment",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, KeyEvent.SHIFT_DOWN_MASK, true), // Use key release for leaf binding
                        ">",
                        "Comment",
                        "CommentByLineComment",
                    )
                )
            )
        )
        
        // Verify that single bindings use key release
        bindings.forEach {
            assertThat("Leaf binding should use key release event", (it as LeaderBinding.SingleBinding).key.isOnKeyRelease, Matchers.equalTo(true))
        }
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
        
        // Get the parsed bindings
        val bindings = parsedBindings.getOrNull()!!
        
        assertThat(
            bindings, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_W, 0, true), // Use key release for leaf binding
                        "w",
                        "Split vertically and unsplit",
                        listOf("SplitVertically", "Unsplit")
                    )
                )
            )
        )
        
        // Verify that single bindings use key release
        assertThat("Leaf binding should use key release event", (bindings[0] as LeaderBinding.SingleBinding).key.isOnKeyRelease, Matchers.equalTo(true))
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
        
        // Get the parsed bindings
        val bindings = parsedBindings.getOrNull()!!
        
        assertThat(
            bindings, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, true), // Use key release for leaf binding
                        "q",
                        "Open ~/.atamanrc.config",
                        "OpenAtamanConfigAction",
                    )
                )
            )
        )
        
        // Verify that single bindings use key release
        assertThat("Leaf binding should use key release event", (bindings[0] as LeaderBinding.SingleBinding).key.isOnKeyRelease, Matchers.equalTo(true))
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
        
        // Get the parsed bindings
        val bindings = parsedBindings.getOrNull()!!
        
        assertThat(
            bindings, Matchers.equalTo(
                listOf(
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.SHIFT_DOWN_MASK, true), // Use key release
                        "F",
                        "Unsplit",
                        "Unsplit",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0, true), // Use key release
                        "F1",
                        "Comment",
                        "CommentByLineComment",
                    ),
                    LeaderBinding.SingleBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0, true), // Use key release
                        "F12",
                        "Open ~/.atamanrc.config",
                        "OpenAtamanConfigAction",
                    )
                )
            )
        )
        
        // Verify that all bindings use key release
        bindings.forEach { binding ->
            assertThat("Leaf binding should use key release event", 
                (binding as LeaderBinding.SingleBinding).key.isOnKeyRelease, 
                Matchers.equalTo(true))
        }
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
        
        // Get the parsed bindings
        val bindings = parsedBindings.getOrNull()!!
        
        // Check the structure of the bindings
        assertThat(
            bindings, Matchers.equalTo(
                listOf(
                    LeaderBinding.GroupBinding(
                        KeyStroke.getKeyStroke(KeyEvent.VK_Q, 0, false), // Group binding should use key press (released=false)
                        "q",
                        "Session...",
                        listOf(
                            LeaderBinding.SingleBinding(
                                KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.SHIFT_DOWN_MASK, true), // Leaf binding should use key release (released=true)
                                "F",
                                "Open ~/.atamanrc.config",
                                "OpenAtamanConfigAction",
                            )
                        )
                    )
                )
            )
        )
        
        // Verify explicitly that group bindings use key press and leaf bindings use key release
        val groupBinding = bindings[0] as LeaderBinding.GroupBinding
        assertThat("Group binding should use key press event", groupBinding.key.isOnKeyRelease, Matchers.equalTo(false))
        
        val leafBinding = groupBinding.bindings[0] as LeaderBinding.SingleBinding
        assertThat("Leaf binding should use key release event", leafBinding.key.isOnKeyRelease, Matchers.equalTo(true))
    }
}
