package dev.fitiavana.accounting.features.p2pprices

import android.content.SharedPreferences
import dev.fitiavana.accounting.network.p2p.P2pPaymentMethod
import org.json.JSONArray
import org.json.JSONObject

/** Remembers the selected payment method and the last fetched list of methods. */
interface P2pFilterStore {
    fun loadSelected(): String?
    fun saveSelected(identifier: String?)
    fun loadMethods(): List<P2pPaymentMethod>?
    fun saveMethods(methods: List<P2pPaymentMethod>)
}

class SharedPreferencesP2pFilterStore(private val prefs: SharedPreferences) : P2pFilterStore {

    override fun loadSelected(): String? = prefs.getString(KEY_SELECTED, null)

    override fun saveSelected(identifier: String?) {
        prefs.edit().apply { if (identifier == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, identifier) }.apply()
    }

    override fun loadMethods(): List<P2pPaymentMethod>? {
        val json = prefs.getString(KEY_METHODS, null) ?: return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                array.getJSONObject(i).let { P2pPaymentMethod(it.getString("id"), it.getString("name")) }
            }
        } catch (e: org.json.JSONException) {
            null
        }
    }

    override fun saveMethods(methods: List<P2pPaymentMethod>) {
        val array = JSONArray()
        methods.forEach { array.put(JSONObject().put("id", it.identifier).put("name", it.name)) }
        prefs.edit().putString(KEY_METHODS, array.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = "p2p_prices"
        private const val KEY_SELECTED = "selected_method"
        private const val KEY_METHODS = "methods"
    }
}
