package dev.fitiavana.accounting.features.cexprices

import android.content.SharedPreferences

/** Remembers the last pair the user looked at. */
interface CexPairStore {
    fun load(): Pair<String, String>?
    fun save(base: String, quote: String)
}

class SharedPreferencesCexPairStore(private val prefs: SharedPreferences) : CexPairStore {

    override fun load(): Pair<String, String>? {
        val base = prefs.getString(KEY_BASE, null)
        val quote = prefs.getString(KEY_QUOTE, null)
        return if (base != null && quote != null) base to quote else null
    }

    override fun save(base: String, quote: String) {
        prefs.edit().putString(KEY_BASE, base).putString(KEY_QUOTE, quote).apply()
    }

    companion object {
        const val PREFS_NAME = "cex_prices"
        private const val KEY_BASE = "last_base"
        private const val KEY_QUOTE = "last_quote"
    }
}
