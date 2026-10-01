package dev.fitiavana.accounting.network.p2p

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Which side of the order book, from the user's point of view: BUY = ads to buy crypto from. */
enum class P2pSide { BUY, SELL }

/** A payment method ads can be filtered on; [identifier] is what the search endpoint expects in `payTypes`. */
data class P2pPaymentMethod(val identifier: String, val name: String)

/**
 * One P2P ad. Limits are per-order bounds in the fiat currency (MGA).
 * Anything Binance omits is null.
 */
data class P2pAd(
    val price: Double,
    val minLimit: Long? = null,
    val maxLimit: Long? = null,
    val advertiser: String? = null
)

/** Request building and response parsing for Binance P2P's public endpoints. */
object BinanceP2pApi {
    const val URL = "https://p2p.binance.com/bapi/c2c/v2/friendly/c2c/adv/search"
    const val FILTER_CONDITIONS_URL = "https://p2p.binance.com/bapi/c2c/v2/public/c2c/adv/filter-conditions"
    private const val SUCCESS_CODE = "000000"

    /** [payType] restricts the search to ads accepting that payment method identifier; null means any. */
    fun requestBody(fiat: String, asset: String, side: P2pSide, rows: Int, payType: String? = null): String =
        JSONObject()
            .put("fiat", fiat)
            .put("asset", asset)
            .put("tradeType", side.name)
            .put("page", 1)
            .put("rows", rows)
            .put("countries", JSONArray())
            .put("payTypes", JSONArray().apply { payType?.let { put(it) } })
            .put("publisherType", JSONObject.NULL)
            .put("proMerchantAds", false)
            .put("shieldMerchantAds", false)
            .toString()

    fun filterConditionsBody(fiat: String): String = JSONObject().put("fiat", fiat).toString()

    /** Ads in the order Binance ranks them (best first). Ads without a numeric price are skipped. */
    @Throws(IOException::class)
    fun parseAds(json: String): List<P2pAd> {
        val ads = dataOf(json).optJSONArray("data") ?: return emptyList()
        return (0 until ads.length()).mapNotNull { i ->
            val ad = ads.optJSONObject(i) ?: return@mapNotNull null
            val adv = ad.optJSONObject("adv") ?: return@mapNotNull null
            val price = adv.optString("price", "").toDoubleOrNull() ?: return@mapNotNull null
            P2pAd(
                price = price,
                minLimit = amount(adv, "minSingleTransAmount"),
                // The dynamic max is what Binance's app shows: the configured max capped by the ad's remaining stock.
                maxLimit = amount(adv, "dynamicMaxSingleTransAmount") ?: amount(adv, "maxSingleTransAmount"),
                advertiser = ad.optJSONObject("advertiser")?.optString("nickName", "")?.takeIf { it.isNotEmpty() }
            )
        }
    }

    /** Payment methods offered for the fiat of a filter-conditions response. */
    @Throws(IOException::class)
    fun parsePaymentMethods(json: String): List<P2pPaymentMethod> {
        val methods = dataOf(json).optJSONObject("data")?.optJSONArray("tradeMethods") ?: return emptyList()
        return (0 until methods.length()).mapNotNull { i ->
            val method = methods.optJSONObject(i) ?: return@mapNotNull null
            val identifier = method.optString("identifier", "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = method.optString("tradeMethodName", "").takeIf { it.isNotEmpty() && it != "null" } ?: identifier
            P2pPaymentMethod(identifier, name)
        }
    }

    /** The parsed response root, after checking Binance's own status code. */
    private fun dataOf(json: String): JSONObject {
        val root = JSONObject(json)
        val code = root.optString("code", "")
        if (code != SUCCESS_CODE) throw IOException("Binance P2P error ${root.optString("message", code)}")
        return root
    }

    private fun amount(adv: JSONObject, key: String): Long? = adv.optString(key, "").toDoubleOrNull()?.toLong()
}
