package io.github.mishkun.ataman

import com.intellij.openapi.components.service
import io.github.mishkun.ataman.core.BaseTestWithConfig
import io.github.mishkun.ataman.core.MockConfig
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class AtamanStartupTest : BaseTestWithConfig() {

    override val mockConfig: MockConfig = MockConfig()

    @Test
    fun `executes startup`() {
        MatcherAssert.assertThat(service<ConfigService>().parsedBindings, Matchers.not(Matchers.empty()))
    }
}
