package dev.fitiavana.accounting.network.cex

/** How to query one exchange: the ticker URL for a pair and how to read its response. */
class CexEndpoint(val url: (base: String, quote: String) -> String, val parse: (String) -> Double)

object CexEndpoints {
    private fun concatenated(base: String, quote: String) = base.uppercase() + quote.uppercase()
    private fun dashed(base: String, quote: String) = "${base.uppercase()}-${quote.uppercase()}"
    private fun krakenAsset(code: String) = code.uppercase().let { if (it == "BTC") "XBT" else it }

    val all: Map<CexId, CexEndpoint> = mapOf(
        CexId.BINANCE to CexEndpoint(
            { b, q -> "https://api.binance.com/api/v3/ticker/price?symbol=${concatenated(b, q)}" },
            CexPriceParser::parseTopLevelPrice
        ),
        CexId.BYBIT to CexEndpoint(
            { b, q -> "https://api.bybit.com/v5/market/tickers?category=spot&symbol=${concatenated(b, q)}" },
            CexPriceParser::parseBybit
        ),
        CexId.BITGET to CexEndpoint(
            { b, q -> "https://api.bitget.com/api/v2/spot/market/tickers?symbol=${concatenated(b, q)}" },
            CexPriceParser::parseBitget
        ),
        CexId.OKX to CexEndpoint(
            { b, q -> "https://www.okx.com/api/v5/market/ticker?instId=${dashed(b, q)}" },
            CexPriceParser::parseOkx
        ),
        CexId.KUCOIN to CexEndpoint(
            { b, q -> "https://api.kucoin.com/api/v1/market/orderbook/level1?symbol=${dashed(b, q)}" },
            CexPriceParser::parseKucoin
        ),
        CexId.MEXC to CexEndpoint(
            { b, q -> "https://api.mexc.com/api/v3/ticker/price?symbol=${concatenated(b, q)}" },
            CexPriceParser::parseTopLevelPrice
        ),
        CexId.KRAKEN to CexEndpoint(
            { b, q -> "https://api.kraken.com/0/public/Ticker?pair=${krakenAsset(b)}${krakenAsset(q)}" },
            CexPriceParser::parseKraken
        )
    )
}
