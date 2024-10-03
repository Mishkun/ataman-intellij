package io.github.mishkun.ataman

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import io.github.mishkun.ataman.core.BaseTestWithConfig
import io.github.mishkun.ataman.core.MockConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Test

class AtamanConfigReloadingTest : BaseTestWithConfig() {

    override val mockConfig: MockConfig = MockConfig()

    @Test
    fun `reloads config successfully`() {
        mockConfig.stubConfig("bindings { q { actionId: A, description: Session }, w { actionId: B, description: Session } }")
        reloadWithAction()
        assertThat(service<ConfigService>().parsedBindings.size, Matchers.equalTo(2))
    }

    @Test
    fun `displays a notification if bindings schema is invalid`() {
        mockConfig.stubConfig("bindings { q { description: Session } }")
        reloadWithAction()
        project.checkNotification("Bindings schema is invalid. Aborting...")
    }

    @Test
    fun `displays a notification if config is malformed`() {
        mockConfig.stubConfig("}malformed{")
        reloadWithAction()
        project.checkNotification("Config is malformed. Aborting...")
    }
}

private fun reloadWithAction() {
    PlatformTestUtil.invokeNamedAction("ReloadAtamanConfigAction")
}

private fun Project.checkNotification(notification: String) {
    val notifications = NotificationsManager.getNotificationsManager()
        .getNotificationsOfType(Notification::class.java, this)
    assertThat(
        notifications.toList(), Matchers.allOf(
            Matchers.not(Matchers.empty()),
            Matchers.hasSize(1)
        )
    )
    assertThat(
        notifications.first().content,
        Matchers.containsString(notification)
    )
}
