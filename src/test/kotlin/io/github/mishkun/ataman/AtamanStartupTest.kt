package io.github.mishkun.ataman

import com.intellij.openapi.components.service
import io.github.mishkun.ataman.core.BaseTestWithConfigService
import io.github.mishkun.ataman.core.MockConfigService
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test

class AtamanStartupTest : BaseTestWithConfigService() {

    override val mockConfigService: MockConfigService = MockConfigService()

    @Test
    fun `executes startup`() {
        MatcherAssert.assertThat(service<ConfigService>().parsedBindings, Matchers.not(Matchers.empty()))
    }
}
