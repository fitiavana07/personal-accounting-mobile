package dev.fitiavana.accounting.features.sync

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tells when the app as a whole is on screen: sync does not run in the background. It counts the
 * started activities, because moving from one screen to the next starts the new one before it stops
 * the old one, so the app never seems to leave the foreground (and sync is not restarted) in between.
 * Register it with [Application.registerActivityLifecycleCallbacks].
 */
class ForegroundTracker(
    private val onForeground: () -> Unit,
    private val onBackground: () -> Unit,
) : Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        if (++started == 1) onForeground()
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0 && --started == 0) onBackground()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
