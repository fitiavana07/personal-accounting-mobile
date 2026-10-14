package dev.fitiavana.accounting.features.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StampClockTest {
    private var now = 1_000L
    private fun clock(deviceId: String = "a", highest: Long = 0) = StampClock(deviceId, { now }, highest)

    @Test
    fun stampsCarryTheDeviceIdAndTheCurrentTime() {
        val stamp = clock("dev-1").next()

        assertEquals(Stamp(1_000, "dev-1"), stamp)
    }

    @Test
    fun stampsStayStrictlyIncreasingWhileTheWallClockStandsStillOrGoesBack() {
        val clock = clock()

        val first = clock.next()
        val second = clock.next()
        now = 500
        val third = clock.next()

        assertTrue(first < second)
        assertTrue(second < third)
    }

    @Test
    fun observingAFutureStampPushesLaterStampsAfterIt() {
        val clock = clock("a")

        clock.observe(Stamp(9_000, "b"))

        assertEquals(Stamp(9_001, "a"), clock.next())
    }

    @Test
    fun observingAnOlderStampChangesNothing() {
        val clock = clock("a")

        clock.observe(Stamp(10, "b"))

        assertEquals(Stamp(1_000, "a"), clock.next())
    }

    @Test
    fun startsAfterTheHighestTimeAlreadyStored() {
        val clock = clock("a", highest = 5_000)

        assertEquals(Stamp(5_001, "a"), clock.next())
    }

    @Test
    fun stampsOrderByTimeThenDeviceId() {
        assertTrue(Stamp(1, "z") < Stamp(2, "a"))
        assertTrue(Stamp(5, "a") < Stamp(5, "b"))
        assertEquals(0, Stamp(5, "a").compareTo(Stamp(5, "a")))
    }
}
