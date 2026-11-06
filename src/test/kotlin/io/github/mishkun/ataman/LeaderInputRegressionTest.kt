package io.github.mishkun.ataman

import com.intellij.testFramework.LightPlatform4TestCase
import io.github.mishkun.ataman.LeaderStroke.*
import org.junit.Test

/** Ten behavior cases through real LeaderAction, current dispatchers and real popup APIs. */
class LeaderInputRegressionTest : LightPlatform4TestCase() {
    @Test
    fun `01 non-overlap executes child once`() = scenario(listOf("child")) {
        input(C_DOWN, C_UP, R_DOWN, R_UP)
    }

    @Test
    fun `02 overlap releasing c first executes child once`() = scenario(listOf("child")) {
        input(C_DOWN, R_DOWN, C_UP, R_UP)
    }

    @Test
    fun `03 queued rollover releasing r first executes child once`() = scenario(listOf("child")) {
        queuedInput(C_DOWN, R_DOWN, R_UP, C_UP)
    }

    @Test
    fun `04 rollover draining deferred work between events executes child once`() = scenario(listOf("child")) {
        input(C_DOWN, R_DOWN, R_UP, C_UP, drainBetween = true)
    }

    @Test
    fun `05 function key group then a executes child once`() = scenario(listOf("child")) {
        input(F12_DOWN, F12_UP, A_DOWN, A_UP)
    }

    @Test
    fun `06 mouse group then keyboard r executes child not root`() = scenario(listOf("child")) {
        mouseSelect("c")
        input(R_DOWN, R_UP)
    }

    @Test
    fun `07 nested Escape returns to root sibling`() = scenario(listOf("sibling")) {
        input(C_DOWN, C_UP, ESC_DOWN, ESC_UP, S_DOWN, S_UP)
    }

    @Test
    fun `08 function key leaf executes child once`() = scenario(listOf("child"), functionKeyLeaf = true) {
        input(F12_DOWN, F12_UP)
    }

    @Test
    fun `09 mouse group then mouse r executes child once`() = scenario(listOf("child")) {
        mouseSelect("c")
        mouseSelect("r")
    }

    @Test
    fun `10 ShiftEscape closes every popup and subsequent r does nothing`() = scenario(emptyList()) {
        input(C_DOWN, C_UP, SHIFT_ESC_DOWN, SHIFT_ESC_UP, R_DOWN, R_UP)
    }

    private fun scenario(
        expected: List<String>,
        functionKeyLeaf: Boolean = false,
        exercise: LeaderInputHarness.() -> Unit
    ) {
        LeaderInputHarness(project, functionKeyLeaf).use { h ->
            h.exercise()
            h.drainEdt()
            println("INPUT_RESULT expected=$expected actual=${h.actions} popupOpen=${h.hasOpenPopup()}")
            assertEquals("Complete action log after trailing releases and deferred work", expected, h.actions)
            assertFalse("Completed input must close every popup before cleanup", h.hasOpenPopup())
        }
    }
}
