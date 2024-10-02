package io.github.mishkun.ataman

import com.intellij.testFramework.LightPlatform4TestCase
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Test

class AtamanStartupTest : LightPlatform4TestCase() {

    @Test
    fun `executes startup`() {
        MatcherAssert.assertThat(parsedBindings, Matchers.not(Matchers.empty()))
    }

    @After
    fun teardownProject() {
        parsedBindings = emptyList()
        setProject(null)
    }
}
