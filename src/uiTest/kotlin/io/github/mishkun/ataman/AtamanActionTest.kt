package io.github.mishkun.ataman

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.JLabelUiComponent
import com.intellij.driver.sdk.ui.components.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.vcsToolWindow
import com.intellij.driver.sdk.ui.components.welcomeScreen
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.Starter
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.waitForDumbMode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import java.awt.event.KeyEvent

class AtamanActionTest {

    @Test
    fun `activates ide specific binding`() {
        val testContext = Starter
            .newContext("test_plugin_action", UltimateCase.simpleProject)
            .prepareProjectCleanImport()
            .disableAutoImport(disabled = true)
        testContext.pluginConfigurator.installPluginFromPath(System.getenv("ATAMAN_PLUGIN_PATH").toNioPathOrNull()!!)
        testContext.runIdeWithConfiguredDriver().useDriverAndCloseIde {
            this.invokeAction("LeaderAction")
            ideFrame {
                assertThat(atamanPopup().isVisible(), equalTo(true))
            }
            this.ui.robot.pressAndReleaseKey(KeyEvent.VK_Q)
            ideFrame {
                vcsToolWindow {
                    assertThat(isVisible(), equalTo(true))
                }
            }
        }
    }

    @Test
    fun `should not activate transparent leader action if speed search is active`() {
        val testContext = Starter
            .newContext("transparent_action", MyTestCase.simpleProject)
            .prepareProjectCleanImport()
            .disableAutoImport(disabled = true)
        testContext.pluginConfigurator.installPluginFromPath(System.getenv("ATAMAN_PLUGIN_PATH").toNioPathOrNull()!!)
        testContext.runIdeWithConfiguredDriver().useDriverAndCloseIde {
            // trigger speed search in project view
            ideFrame {
                ui.robot.focus(this.projectViewTree.component)
                ui.robot.type('p')
            }
            // trigger action. This time it should not display the popup because speed search is active
            this.invokeAction("TransparentLeaderAction")
            ideFrame {
                val popup = kotlin.runCatching { atamanPopup().component }
                assertThat(popup.isFailure, equalTo(true))
            }
            // close speed search
            ui.robot.pressKey(KeyEvent.VK_ESCAPE)

            // trigger action again. This time it should display the popup
            this.invokeAction("TransparentLeaderAction")
            ideFrame {
                assertThat(atamanPopup().isVisible(), equalTo(true))
            }
        }
    }

    @Test
    fun test_plugin_main_action() {
        val testContext = Starter
            .newContext("test_plugin_action", MyTestCase.simpleProject)
            .prepareProjectCleanImport()
            .disableAutoImport(disabled = true)
        testContext.pluginConfigurator.installPluginFromPath(System.getenv("ATAMAN_PLUGIN_PATH").toNioPathOrNull()!!)
        CommandChain().waitForDumbMode(10)
        testContext.runIdeWithConfiguredDriver().useDriverAndCloseIde {
            this.invokeAction("LeaderAction")
            ideFrame {
                assertThat(atamanPopup().isVisible(), equalTo(true))
            }
            exitProjectViaAtamanAndCheckSuccess()
        }
    }

    private fun IDETestContext.runIdeWithConfiguredDriver(): BackgroundRun {
        val commands = CommandChain().waitForDumbMode(10)

        return this.runIdeWithDriver(
            commands = commands,
            commandLine = {
                IDECommandLine.OpenTestCaseProject(
                    this,
                    listOf("-Dataman.configFolder=${System.getProperty("ataman.configFolder")}")
                )
            }
        )
    }

    private fun Finder.atamanPopup(): JLabelUiComponent = xx(
        "//div[@text='Ataman']",
        JLabelUiComponent::class.java
    ).list().first()

    private fun Driver.exitProjectViaAtamanAndCheckSuccess(): WelcomeScreenUI {
        this.ui.robot.waitForIdle()
        this.ui.robot.pressAndReleaseKey(KeyEvent.VK_Q)
        this.ui.robot.waitForIdle()
        takeScreenshot("test_plugin_main_action")
        this.ui.robot.pressAndReleaseKey(KeyEvent.VK_F1)
        this.ui.robot.waitForIdle()
        return this.welcomeScreen {
            this.isVisible()
        }
    }
}
