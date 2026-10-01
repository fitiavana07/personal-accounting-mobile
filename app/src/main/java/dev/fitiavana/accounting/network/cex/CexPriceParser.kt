package dev.fitiavana.accounting.network.cex

import org.json.JSONObject
import java.io.IOException

/** The exchange answered, but has no price for the requested pair. */
class PairNotListedException(message: String) : IOException(message)

/** Extracts the last spot price from each exchange's ticker response body. */
object CexPriceParser {

    /** Binance and MEXC: `{"symbol":"BTCUSDT","price":"65000.5"}`. */
    fun parseTopLevelPrice(json: String): Double =
        toPrice(JSONObject(json).optString("price", ""))

    /** Bybit: `{"result":{"list":[{"lastPrice":"..."}]}}`. */
    fun parseBybit(json: String): Double =
        toPrice(firstOf(JSONObject(json).optJSONObject("result"), "list")?.optString("lastPrice", ""))

    /** Bitget: `{"data":[{"lastPr":"..."}]}`. */
    fun parseBitget(json: String): Double =
        toPrice(firstOf(JSONObject(json), "data")?.optString("lastPr", ""))

    /** OKX: `{"data":[{"last":"..."}]}`. */
    fun parseOkx(json: String): Double =
        toPrice(firstOf(JSONObject(json), "data")?.optString("last", ""))

    /** KuCoin level1 order book: `{"data":{"price":"..."}}`; `data` is null when unlisted. */
    fun parseKucoin(json: String): Double =
        toPrice(JSONObject(json).optJSONObject("data")?.optString("price", ""))

    /** Kraken: `{"error":[],"result":{"<pairKey>":{"c":["lastPrice","lotVolume"]}}}`. */
    fun parseKraken(json: String): Double {
        val root = JSONObject(json)
        val errors = root.optJSONArray("error")
        if (errors != null && errors.length() > 0) throw PairNotListedException(errors.optString(0))
        val result = root.optJSONObject("result")
        val key = result?.keys()?.takeIf { it.hasNext() }?.next()
        return toPrice(result?.optJSONObject(key)?.optJSONArray("c")?.optString(0, ""))
    }

    private fun firstOf(parent: JSONObject?, arrayName: String): JSONObject? =
        parent?.optJSONArray(arrayName)?.optJSONObject(0)

    private fun toPrice(raw: String?): Double =
        raw?.toDoubleOrNull() ?: throw PairNotListedException("No price in response")
}
