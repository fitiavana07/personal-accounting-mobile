package dev.fitiavana.accounting.features

import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for strict JSON decoding: `org.json` coerces types (7 becomes "7"), so every field is
 * type-checked and a hand-edited or foreign document is rejected, not guessed at.
 */
class FormatException(message: String) : Exception(message)

fun <T> array(items: Collection<T>, row: JSONObject.(T) -> JSONObject) =
    JSONArray().apply { items.forEach { put(JSONObject().row(it)) } }

fun <T> JSONObject.rows(key: String, row: JSONObject.() -> T): List<T> {
    val array = value(key) as? JSONArray ?: throw FormatException("'$key' must be a list")
    return List(array.length()) { i ->
        val item = array.opt(i) as? JSONObject ?: throw FormatException("'$key' has a row that is not an object")
        item.row()
    }
}

fun JSONObject.value(key: String): Any =
    if (has(key)) get(key) else throw FormatException("Missing '$key'")

fun JSONObject.obj(key: String): JSONObject =
    value(key) as? JSONObject ?: throw FormatException("'$key' must be an object")

fun JSONObject.string(key: String): String =
    value(key) as? String ?: throw FormatException("'$key' must be a string")

fun JSONObject.int(key: String): Int =
    value(key) as? Int ?: throw FormatException("'$key' must be a whole number")

/** A whole number that may be too large for an Int (org.json reads it as an Int when it fits). */
fun JSONObject.long(key: String): Long =
    when (val value = value(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw FormatException("'$key' must be a whole number")
    }

fun JSONObject.boolean(key: String): Boolean =
    value(key) as? Boolean ?: throw FormatException("'$key' must be true or false")
