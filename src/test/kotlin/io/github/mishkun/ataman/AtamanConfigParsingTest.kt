package io.github.mishkun.ataman

import com.intellij.testFramework.LightPlatform4TestCase
import io.github.mishkun.ataman.core.setupStubHomeDir
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtamanConfigParsingTest : LightPlatform4TestCase() {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Before
    fun setup() {
        parsedBindings = emptyList()
    }

    @Test
    fun `skips bindings that are not set up properly`() {
        updateConfig(tmpFolder.setupStubHomeDir(text = "bindings { q { description: Session } }"))
        assertThat(parsedBindings, Matchers.empty())
    }

    @Test
    fun `parses config to the list of bindings`() {
        updateConfig(tmpFolder.setupStubHomeDir())
        assertThat(parsedBindings, not(Matchers.empty()))
    }

    @After
    fun teardown() {
        parsedBindings = emptyList()
    }
}
