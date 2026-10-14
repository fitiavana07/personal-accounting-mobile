package dev.fitiavana.accounting.features.sync

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ForegroundTrackerTest {
    private val events = mutableListOf<String>()
    private val tracker = ForegroundTracker(onForeground = { events += "foreground" }, onBackground = { events += "background" })
    private val first: Activity = mock()
    private val second: Activity = mock()

    @Test
    fun `the first started activity brings the app to the foreground`() {
        tracker.onActivityStarted(first)

        assertEquals(listOf("foreground"), events)
    }

    @Test
    fun `the last stopped activity sends the app to the background`() {
        tracker.onActivityStarted(first)

        tracker.onActivityStopped(first)

        assertEquals(listOf("foreground", "background"), events)
    }

    @Test
    fun `moving between two activities never leaves the foreground`() {
        tracker.onActivityStarted(first)

        // The new screen starts before the old one stops.
        tracker.onActivityStarted(second)
        tracker.onActivityStopped(first)

        assertEquals(listOf("foreground"), events)

        tracker.onActivityStopped(second)
        assertEquals(listOf("foreground", "background"), events)
    }

    @Test
    fun `a stop without a start is ignored`() {
        tracker.onActivityStopped(first)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `coming back to the foreground is announced again`() {
        tracker.onActivityStarted(first)
        tracker.onActivityStopped(first)

        tracker.onActivityStarted(first)

        assertEquals(listOf("foreground", "background", "foreground"), events)
    }
}
