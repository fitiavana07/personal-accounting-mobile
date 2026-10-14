package dev.fitiavana.accounting

import androidx.test.core.app.ApplicationProvider
import dev.fitiavana.accounting.ui.earn.EarnActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sync runs only while one of the app's screens is on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class AccountingApplicationTest {
    private val application = ApplicationProvider.getApplicationContext<AccountingApplication>()
    private val coordinator get() = AppContainer.getInstance(application).syncCoordinator

    @Test
    fun `sync starts when a screen starts and stops when the last one stops`() {
        val controller = Robolectric.buildActivity(EarnActivity::class.java, EarnActivity.intent(application))

        controller.create().start()
        assertTrue(coordinator.state.value.running)

        controller.stop()
        assertFalse(coordinator.state.value.running)
    }

    @Test
    fun `moving from one screen to another keeps sync running`() {
        val first = Robolectric.buildActivity(EarnActivity::class.java, EarnActivity.intent(application)).create().start()
        val second = Robolectric.buildActivity(EarnActivity::class.java, EarnActivity.intent(application)).create().start()

        first.stop()
        assertTrue(coordinator.state.value.running)

        second.stop()
        assertFalse(coordinator.state.value.running)
        assertEquals(false, coordinator.state.value.peers.isNotEmpty())
    }
}
