package io.github.mishkun.ataman

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.LightPlatform4TestCase
import io.github.mishkun.ataman.core.setupStubHomeDir
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtamanConfigParsingNotificationTest : LightPlatform4TestCase() {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `displays a notification if config is malformed`() {
        updateConfig(project, tmpFolder.setupStubHomeDir(text = "}{"))
        val notifications =
            NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
        MatcherAssert.assertThat(
            notifications.toList(), Matchers.allOf(Matchers.not(Matchers.empty()), Matchers.hasSize(1))
        )
        MatcherAssert.assertThat(
            notifications.first().content, Matchers.containsString("Config is malformed. Aborting...")
        )
    }

    @After
    fun teardownProject() {
        parsedBindings = emptyList()
        setProject(null)
    }
}
