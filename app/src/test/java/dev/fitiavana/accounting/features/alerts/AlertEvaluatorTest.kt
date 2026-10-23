package dev.fitiavana.accounting.features.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {

    private fun above(threshold: Double, id: String = "a") =
        GainLossAlert(id, "acc", AlertTypes.CROSSING_ABOVE, threshold, 1L)

    private fun below(threshold: Double, id: String = "a") =
        GainLossAlert(id, "acc", AlertTypes.CROSSING_BELOW, threshold, 1L)

    private fun state(alert: GainLossAlert, armed: Boolean) =
        mapOf(alert.id to AlertState(alert.id, armed, alert.signature))

    private fun evaluate(
        alert: GainLossAlert,
        percent: Double?,
        states: Map<String, AlertState> = emptyMap()
    ) = AlertEvaluator.evaluate(listOf(alert), mapOf(alert.accountId to percent), states)

    @Test
    fun `an alert with no state is seeded armed when below the threshold, without firing`() {
        val alert = above(10.0)

        val result = evaluate(alert, 5.0)

        assertTrue(result.fired.isEmpty())
        assertEquals(listOf(AlertState("a", true, alert.signature)), result.states)
    }

    @Test
    fun `an alert with no state is seeded disarmed when already past the threshold, without firing`() {
        val alert = above(10.0)

        val result = evaluate(alert, 15.0)

        assertTrue(result.fired.isEmpty())
        assertEquals(listOf(AlertState("a", false, alert.signature)), result.states)
    }

    @Test
    fun `an armed crossing-above alert fires at the threshold and disarms`() {
        val alert = above(10.0)

        val result = evaluate(alert, 10.0, state(alert, armed = true))

        assertEquals(listOf(FiredAlert(alert, 10.0)), result.fired)
        assertEquals(listOf(AlertState("a", false, alert.signature)), result.states)
    }

    @Test
    fun `an armed crossing-above alert stays quiet below the threshold`() {
        val alert = above(10.0)

        val result = evaluate(alert, 9.99, state(alert, armed = true))

        assertTrue(result.fired.isEmpty())
        assertTrue(result.states.isEmpty())
    }

    @Test
    fun `a disarmed alert does not fire again while still above`() {
        val alert = above(10.0)

        val result = evaluate(alert, 20.0, state(alert, armed = false))

        assertTrue(result.fired.isEmpty())
        assertTrue(result.states.isEmpty())
    }

    @Test
    fun `a disarmed crossing-above alert stays disarmed inside the hysteresis band`() {
        val alert = above(10.0)

        val result = evaluate(alert, 9.5, state(alert, armed = false))

        assertTrue(result.fired.isEmpty())
        assertTrue(result.states.isEmpty())
    }

    @Test
    fun `a disarmed crossing-above alert re-arms one point below the threshold`() {
        val alert = above(10.0)

        val result = evaluate(alert, 9.0, state(alert, armed = false))

        assertTrue(result.fired.isEmpty())
        assertEquals(listOf(AlertState("a", true, alert.signature)), result.states)
    }

    @Test
    fun `an armed crossing-below alert fires at a negative threshold and disarms`() {
        val alert = below(-10.0)

        val result = evaluate(alert, -12.0, state(alert, armed = true))

        assertEquals(listOf(FiredAlert(alert, -12.0)), result.fired)
        assertEquals(listOf(AlertState("a", false, alert.signature)), result.states)
    }

    @Test
    fun `a disarmed crossing-below alert re-arms one point above the threshold`() {
        val alert = below(-10.0)

        val result = evaluate(alert, -9.0, state(alert, armed = false))

        assertTrue(result.fired.isEmpty())
        assertEquals(listOf(AlertState("a", true, alert.signature)), result.states)
    }

    @Test
    fun `a crossing-below alert on a positive threshold warns when a gain shrinks`() {
        val alert = below(10.0)

        val result = evaluate(alert, 9.0, state(alert, armed = true))

        assertEquals(listOf(FiredAlert(alert, 9.0)), result.fired)
    }

    @Test
    fun `an unknown gain loss changes nothing`() {
        val alert = above(10.0)

        val result = evaluate(alert, null, state(alert, armed = true))

        assertTrue(result.fired.isEmpty())
        assertTrue(result.states.isEmpty())
    }

    @Test
    fun `an account missing from the percentages changes nothing`() {
        val alert = above(10.0)

        val result = AlertEvaluator.evaluate(listOf(alert), emptyMap(), state(alert, armed = true))

        assertTrue(result.fired.isEmpty())
        assertTrue(result.states.isEmpty())
    }

    @Test
    fun `a state whose signature no longer matches is re-seeded without firing`() {
        val edited = above(10.0)
        val stale = mapOf("a" to AlertState("a", true, "other|crossing_above|5.0"))

        val result = evaluate(edited, 12.0, stale)

        assertTrue(result.fired.isEmpty())
        assertEquals(listOf(AlertState("a", false, edited.signature)), result.states)
    }

    @Test
    fun `several alerts fire independently in one run`() {
        val first = above(10.0, id = "a")
        val second = above(5.0, id = "b")
        val states = state(first, true) + state(second, true)

        val result = AlertEvaluator.evaluate(listOf(first, second), mapOf("acc" to 12.0), states)

        assertEquals(listOf("a", "b"), result.fired.map { it.alert.id })
        assertEquals(listOf(false, false), result.states.map { it.armed })
    }
}
