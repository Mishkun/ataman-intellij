package io.github.mishkun.ataman

import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.util.ui.UIUtil
import org.junit.Test
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Explicit coverage, not a claim to prove every possible timing. All calls stay on EDT.
 * Timestamp metadata and actual invokeLater/queue-boundary schedules are separate tests.
 * No sleeps, random timing, private state access, root r binding, or manual updateBindings.
 */
class LeaderKeyDispatcherTimingRegressionTest : LightPlatform4TestCase() {
    private enum class Stroke(val id: Int, val code: Int, val char: Char) {
        C_DOWN(KeyEvent.KEY_PRESSED, KeyEvent.VK_C, 'c'),
        C_UP(KeyEvent.KEY_RELEASED, KeyEvent.VK_C, 'c'),
        R_DOWN(KeyEvent.KEY_PRESSED, KeyEvent.VK_R, 'r'),
        R_UP(KeyEvent.KEY_RELEASED, KeyEvent.VK_R, 'r')
    }

    private enum class Order(val strokes: List<Stroke>, val heldTimes: List<Long>) {
        NON_OVERLAP(listOf(Stroke.C_DOWN, Stroke.C_UP, Stroke.R_DOWN, Stroke.R_UP), listOf(0, 5_000, 5_001, 5_002)),
        OVERLAP_C_RELEASE_FIRST(listOf(Stroke.C_DOWN, Stroke.R_DOWN, Stroke.C_UP, Stroke.R_UP), listOf(0, 4_999, 5_000, 5_001)),
        ROLLOVER_R_RELEASE_FIRST(listOf(Stroke.C_DOWN, Stroke.R_DOWN, Stroke.R_UP, Stroke.C_UP), listOf(0, 1, 2, 5_000))
    }

    private enum class Delivery { ONE_QUEUED_TURN, SEPARATE_QUEUED_TURNS, BARRIER_BETWEEN_EVENTS }

    @Test
    fun `timestamp metadata matrix non-overlap executes child exactly once`() = timestampMatrix(Order.NON_OVERLAP)

    @Test
    fun `timestamp metadata matrix overlap releasing c first executes child exactly once`() =
        timestampMatrix(Order.OVERLAP_C_RELEASE_FIRST)

    @Test
    fun `timestamp metadata matrix rollover releasing r first executes child exactly once`() =
        timestampMatrix(Order.ROLLOVER_R_RELEASE_FIRST)

    private fun timestampMatrix(order: Order) {
        val outcomes = mutableListOf<Pair<String, Boolean>>()
        val profiles = listOf(0L, 1L, 5L, 16L, 100L, 500L).map { gap ->
            "gap=${gap}ms" to List(4) { it * gap }
        } + ("c-held-5000ms" to order.heldTimes)
        profiles.forEach { (name, times) ->
            LeaderPopupRegressionHarness(project, includeRootR = false).use { h ->
                h.startDispatcherOnly()
                order.strokes.zip(times).forEach { (stroke, time) ->
                    // Trailing releases may pass through after the action stops capture.
                    h.send(stroke.id, stroke.code, stroke.char, time)
                }
                record(outcomes, "metadata/$order/$name/when=$times", h)
            }
        }
        assertAllCases(outcomes)
    }

    @Test
    fun `queued delivery after actual LeaderAction popup startup preserves every press order`() {
        val outcomes = mutableListOf<Pair<String, Boolean>>()
        Order.entries.forEach { order ->
            Delivery.entries.forEach { delivery ->
                LeaderPopupRegressionHarness(project, includeRootR = false).use { h ->
                    // Actual production action owns start, constructor, listener and close wiring.
                    enqueueAndDrain(listOf({ h.startWithProductionAction() }))
                    deliver(order.strokes.map { stroke -> { h.send(stroke.id, stroke.code, stroke.char); Unit } }, delivery)
                    record(outcomes, "actual-LeaderAction/$order/$delivery", h)
                }
            }
        }
        assertAllCases(outcomes)
    }

    @Test
    fun `split startup queued events before and after popup listeners preserve every press order`() {
        val outcomes = mutableListOf<Pair<String, Boolean>>()
        Order.entries.forEach { order ->
            // Construct the real popup after 0..4 input events. This is an explicit model
            // of the public start/UI boundary, NOT preemption of LeaderAction on EDT.
            (0..4).forEach { popupAfterEvents ->
                Delivery.entries.forEach { delivery ->
                    LeaderPopupRegressionHarness(project, includeRootR = false).use { h ->
                        enqueueAndDrain(listOf({ h.startDispatcherOnly() }))
                        val tasks = mutableListOf<() -> Unit>()
                        (0..4).forEach { index ->
                            if (index == popupAfterEvents) tasks.add { h.attachPopupAfterStart(); Unit }
                            if (index < 4) {
                                val stroke = order.strokes[index]
                                tasks.add { h.send(stroke.id, stroke.code, stroke.char); Unit }
                            }
                        }
                        deliver(tasks, delivery)
                        record(outcomes, "split-start/$order/popup-after-$popupAfterEvents-events/$delivery", h)
                    }
                }
            }
        }
        assertAllCases(outcomes)
    }

    private fun deliver(tasks: List<() -> Unit>, delivery: Delivery) {
        when (delivery) {
            Delivery.ONE_QUEUED_TURN -> enqueueAndDrain(listOf({ tasks.forEach { it() } }))
            Delivery.SEPARATE_QUEUED_TURNS -> enqueueAndDrain(tasks)
            Delivery.BARRIER_BETWEEN_EVENTS -> tasks.forEach { enqueueAndDrain(listOf(it)) }
        }
    }

    private fun enqueueAndDrain(tasks: List<() -> Unit>) {
        check(SwingUtilities.isEventDispatchThread())
        val errors = mutableListOf<Throwable>()
        var barrierReached = false
        tasks.forEach { task ->
            SwingUtilities.invokeLater {
                try { task() } catch (error: Throwable) { errors.add(error) }
            }
        }
        SwingUtilities.invokeLater { barrierReached = true }
        UIUtil.dispatchAllInvocationEvents()
        assertTrue("Queued EDT barrier must execute", barrierReached)
        if (errors.isNotEmpty()) throw errors.first()
    }

    private fun record(outcomes: MutableList<Pair<String, Boolean>>, label: String, h: LeaderPopupRegressionHarness) {
        val passed = h.executions == listOf("child")
        val detail = "$label expected=[child] actual=${h.executions}; ${h.state()}"
        println("P1_CASE ${if (passed) "PASS" else "FAIL"} $detail")
        outcomes.add(detail to passed)
    }

    private fun assertAllCases(outcomes: List<Pair<String, Boolean>>) {
        val failures = outcomes.filterNot { it.second }
        println("P1_MATRIX cases=${outcomes.size}, passed=${outcomes.size - failures.size}, failed=${failures.size}")
        assertTrue("${failures.size}/${outcomes.size} cases failed:\n${failures.joinToString("\n") { it.first }}", failures.isEmpty())
    }
}
