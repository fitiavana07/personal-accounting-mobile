package dev.fitiavana.accounting.features.reports

import android.content.SharedPreferences

/** Remembers whether the Reports trend charts are shown (device-local, not synced). */
interface TrendVisibilityStore {
    fun isVisible(): Boolean
    fun setVisible(visible: Boolean)
}

class SharedPreferencesTrendVisibilityStore(private val prefs: SharedPreferences) : TrendVisibilityStore {

    override fun isVisible(): Boolean = prefs.getBoolean(KEY_VISIBLE, true)

    override fun setVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_VISIBLE, visible).apply()
    }

    companion object {
        const val PREFS_NAME = "reports_trend"
        private const val KEY_VISIBLE = "visible"
    }
}

/** Non-persistent store, the default when nothing is injected (e.g. in tests). */
class InMemoryTrendVisibilityStore(private var visible: Boolean = true) : TrendVisibilityStore {
    override fun isVisible(): Boolean = visible
    override fun setVisible(visible: Boolean) {
        this.visible = visible
    }
}
