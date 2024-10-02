package io.github.mishkun.ataman

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import io.github.mishkun.ataman.core.BaseTestWithConfigService
import io.github.mishkun.ataman.core.MockConfigService
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class AtamanConfigParsingNotificationTest : BaseTestWithConfigService() {

    override val mockConfigService: MockConfigService = MockConfigService("}malformed{")

    @Test
    fun `displays a notification if config is malformed`() {
        val notifications = NotificationsManager.getNotificationsManager()
            .getNotificationsOfType(Notification::class.java, project)
        MatcherAssert.assertThat(
            notifications.toList(), Matchers.allOf(
                Matchers.not(Matchers.empty()),
                Matchers.hasSize(1)
            )
        )
        MatcherAssert.assertThat(
            notifications.first().content,
            Matchers.containsString("Config is malformed. Aborting...")
        )
    }
}
