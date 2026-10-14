package dev.fitiavana.accounting.features.sync

/** Version of a record: a total order over every write made by every device. */
data class Stamp(val time: Long, val deviceId: String) : Comparable<Stamp> {
    override fun compareTo(other: Stamp): Int =
        compareValuesBy(this, other, Stamp::time, Stamp::deviceId)
}

/**
 * Issues strictly increasing [Stamp]s for one device. A stamp is never lower than any stamp
 * seen so far, so a device with a slow wall clock still orders its writes after what it synced.
 */
class StampClock(
    private val deviceId: String,
    private val now: () -> Long,
    highestTime: Long = 0,
) {
    private var highest = highestTime

    @Synchronized
    fun next(): Stamp {
        highest = maxOf(now(), highest + 1)
        return Stamp(highest, deviceId)
    }

    @Synchronized
    fun observe(stamp: Stamp) {
        highest = maxOf(highest, stamp.time)
    }
}
