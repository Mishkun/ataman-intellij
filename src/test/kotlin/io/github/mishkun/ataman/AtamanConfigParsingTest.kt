package io.github.mishkun.ataman

import io.github.mishkun.ataman.core.setupStubConfigDir
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtamanConfigParsingTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `returns error on malformed config`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(text = "}malformed{")
        )
        assertThat(parsedBindings.isFailure, Matchers.equalTo(true))
    }

    @Test
    fun `throws if bindings are not set up properly`() {
        val parsedBindings = parseConfig(
            configDir = tmpFolder.setupStubConfigDir(text = "bindings { q { description: Session } }")
        )
        assertThat(parsedBindings.isFailure, Matchers.equalTo(true))
        println(parsedBindings.exceptionOrNull())
    }

    @Test
    fun `parses config to the list of bindings`() {
        val parsedBindings = parseConfig(configDir = tmpFolder.setupStubConfigDir())
        assertThat(parsedBindings.getOrNull()!!, not(Matchers.empty()))
    }
}
