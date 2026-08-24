package dev.fitiavana.accounting.network

import org.json.JSONObject

data class StockQuote(
    val symbol: String,
    val currency: String,
    val price: Double
)

object YahooFinanceResponseParser {
    /**
     * Parses a `/v8/finance/chart/{symbol}` response body, e.g.
     * `{"chart":{"result":[{"meta":{"currency":"USD","symbol":"NVDA","regularMarketPrice":123.45}}],"error":null}}`.
     * Returns null if the quote is missing a currency or price.
     */
    fun parseQuote(json: String): StockQuote? {
        val chart = JSONObject(json).optJSONObject("chart") ?: return null
        val result = chart.optJSONArray("result") ?: return null
        if (result.length() == 0) return null
        val meta = result.getJSONObject(0).optJSONObject("meta") ?: return null

        val currency = meta.optString("currency", "")
        val price = meta.optDouble("regularMarketPrice", Double.NaN)
        val symbol = meta.optString("symbol", "")
        if (currency.isEmpty() || price.isNaN()) return null

        return StockQuote(symbol, currency, price)
    }
}
