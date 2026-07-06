package dev.fitiavana.accounting.data.network

import org.json.JSONObject

data class TickerQuote(
    val target: String,
    val exchangeIdentifier: String,
    val last: Double
)

object CoinGeckoResponseParser {
    /**
     * Parses a `/coins/{id}/tickers` response body into the list of quoted pairs, e.g.
     * `{"tickers":[{"target":"USDT","last":65000.0,"market":{"identifier":"binance"}}, ...]}`.
     * Entries with a missing target or non-numeric last price are skipped.
     */
    fun parseTickers(json: String): List<TickerQuote> {
        val root = JSONObject(json)
        val tickersArray = root.optJSONArray("tickers") ?: return emptyList()
        val result = mutableListOf<TickerQuote>()
        for (i in 0 until tickersArray.length()) {
            val ticker = tickersArray.getJSONObject(i)
            val target = ticker.optString("target", "")
            val last = ticker.optDouble("last", Double.NaN)
            if (target.isEmpty() || last.isNaN()) continue
            val exchangeIdentifier = ticker.optJSONObject("market")?.optString("identifier", "") ?: ""
            result.add(TickerQuote(target, exchangeIdentifier, last))
        }
        return result
    }
}
