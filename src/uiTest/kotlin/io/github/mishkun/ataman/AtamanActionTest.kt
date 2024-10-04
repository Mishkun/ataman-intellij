package io.github.mishkun.ataman

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.JListUiComponent
import com.intellij.driver.sdk.ui.components.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.ideFrame
import com.intellij.driver.sdk.ui.components.welcomeScreen
import com.intellij.driver.sdk.ui.ui
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.runner.IDECommandLine
import com.intellij.ide.starter.runner.Starter
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.waitForDumbMode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test
import java.awt.event.KeyEvent

class AtamanActionTest {

    @Test
    fun `should not activate transparent leader action if speed search is active`() {
        val testContext = Starter
            .newContext("transparent_action", MyTestCase.simpleProject)
            .prepareProjectCleanImport()
            .disableAutoImport(disabled = true)
        testContext.pluginConfigurator.installPluginFromPath(System.getenv("ATAMAN_PLUGIN_PATH").toNioPathOrNull()!!)
        val commands = CommandChain().waitForDumbMode(10)
        testContext.runIdeWithDriver(commands = commands).useDriverAndCloseIde {
            // trigger speed search
            ideFrame {
                ui.robot.focus(this.projectViewTree.component)
                ui.robot.type('p')
            }
            this.invokeAction("TransparentLeaderAction")
            ideFrame {
                val popup = kotlin.runCatching { atamanPopup().component }
                assertThat(popup.isFailure, Matchers.equalTo(true))
            }
            // close speed search
            ui.robot.pressKey(KeyEvent.VK_ESCAPE)
            this.invokeAction("TransparentLeaderAction")
            ideFrame {
                assertThat(atamanPopup().isVisible(), Matchers.equalTo(true))
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
        val commands = CommandChain().waitForDumbMode(10)
        testContext.runIdeWithDriver(
            commands = commands,
            commandLine = {
                IDECommandLine.OpenTestCaseProject(
                    testContext,
                    listOf("-Dataman.configFolder=${System.getProperty("ataman.configFolder")}")
                )
            })
            .useDriverAndCloseIde {
            this.invokeAction("LeaderAction")
            ideFrame {
                assertThat(atamanPopup().isVisible(), Matchers.equalTo(true))
            }
            exitProjectViaAtamanAndCheckSuccess()
        }
    }

    private fun Finder.atamanPopup(): JListUiComponent = xx(
        "//div[@text='Ataman']",
        JListUiComponent::class.java
    ).list().first()

    private fun Driver.exitProjectViaAtamanAndCheckSuccess(): WelcomeScreenUI {
        this.ui.robot.waitForIdle()
        this.ui.robot.pressAndReleaseKey(KeyEvent.VK_Q)
        this.ui.robot.waitForIdle()
        this.ui.robot.pressAndReleaseKey(KeyEvent.VK_E)
        this.ui.robot.waitForIdle()
        return this.welcomeScreen {
            this.isVisible()
        }
    }
}
