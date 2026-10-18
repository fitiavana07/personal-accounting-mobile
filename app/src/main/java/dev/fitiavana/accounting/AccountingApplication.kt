package dev.fitiavana.accounting

import androidx.multidex.MultiDexApplication
import dev.fitiavana.accounting.features.sync.ForegroundTracker

open class AccountingApplication: MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        // Sync runs only while one of the app's screens is on screen. The container is looked up lazily,
        // so it is still built by the first screen that needs it, not here.
        registerActivityLifecycleCallbacks(
            ForegroundTracker(
                onForeground = { AppContainer.getInstance(this).syncCoordinator.start() },
                onBackground = { AppContainer.getInstance(this).syncCoordinator.stop() }
            )
        )
    }
}
