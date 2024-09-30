package io.github.mishkun.ataman

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.LightPlatform4TestCase
import io.github.mishkun.ataman.core.setupStubHomeDir
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtamanConfigParsingNotificationTest : LightPlatform4TestCase() {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Before
    fun setup() {
        parsedBindings = emptyList()
    }

    @Test
    fun `displays a notification if config is malformed`() {
        updateConfig(project, tmpFolder.setupStubHomeDir(text = "}{"))
        val notifications =
            NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
        assertThat(notifications.toList(), allOf(not(Matchers.empty()), Matchers.hasSize(1)))
        assertThat(notifications.first().content, Matchers.containsString("Config is malformed. Aborting..."))
    }

    @After
    fun teardown() {
        parsedBindings = emptyList()
    }
}

class AtamanConfigParsingTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Before
    fun setup() {
        parsedBindings = emptyList()
    }

    @Test
    fun `skips bindings that are not set up properly`() {
        updateConfig(homeDir = tmpFolder.setupStubHomeDir(text = "bindings { q { description: Session } }"))
        assertThat(parsedBindings, Matchers.empty())
    }

    @Test
    fun `parses config to the list of bindings`() {
        updateConfig(homeDir = tmpFolder.setupStubHomeDir())
        assertThat(parsedBindings, not(Matchers.empty()))
    }

    @After
    fun teardown() {
        parsedBindings = emptyList()
    }
}
