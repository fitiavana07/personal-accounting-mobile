package dev.fitiavana.accounting.network.p2p

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21])
class BinanceP2pApiTest {

    @Test
    fun `request body targets fiat asset side and row count`() {
        val body = JSONObject(BinanceP2pApi.requestBody("MGA", "USDT", P2pSide.BUY, 3))

        assertEquals("MGA", body.getString("fiat"))
        assertEquals("USDT", body.getString("asset"))
        assertEquals("BUY", body.getString("tradeType"))
        assertEquals(3, body.getInt("rows"))
        assertEquals(1, body.getInt("page"))
    }

    @Test
    fun `request body uses SELL trade type for the sell side`() {
        val body = JSONObject(BinanceP2pApi.requestBody("MGA", "USDT", P2pSide.SELL, 3))

        assertEquals("SELL", body.getString("tradeType"))
    }

    @Test
    fun `request body has no payment filter by default`() {
        val body = JSONObject(BinanceP2pApi.requestBody("MGA", "USDT", P2pSide.BUY, 3))

        assertEquals(0, body.getJSONArray("payTypes").length())
    }

    @Test
    fun `request body filters on the given payment method`() {
        val body = JSONObject(BinanceP2pApi.requestBody("MGA", "USDT", P2pSide.BUY, 3, "Mvola"))

        assertEquals(listOf("Mvola"), (0 until 1).map { body.getJSONArray("payTypes").getString(it) })
        assertEquals(1, body.getJSONArray("payTypes").length())
    }

    @Test
    fun `filter conditions body targets the fiat`() {
        assertEquals("MGA", JSONObject(BinanceP2pApi.filterConditionsBody("MGA")).getString("fiat"))
    }

    @Test
    fun `parses payment methods with identifier and name`() {
        val json = """{"code":"000000","data":{"countries":[],"tradeMethods":[
            {"identifier":"Mvola","tradeMethodName":"Mvola","iconUrlColor":"/x.png"},
            {"identifier":"OrangeMoney","tradeMethodName":"Orange Money - OM"},
            {"identifier":"BANK","tradeMethodName":null}]}}"""

        assertEquals(
            listOf(
                P2pPaymentMethod("Mvola", "Mvola"),
                P2pPaymentMethod("OrangeMoney", "Orange Money - OM"),
                P2pPaymentMethod("BANK", "BANK")
            ),
            BinanceP2pApi.parsePaymentMethods(json)
        )
    }

    @Test
    fun `payment methods error code is reported as IOException`() {
        try {
            BinanceP2pApi.parsePaymentMethods("""{"code":"000002","message":"nope","data":null}""")
            fail("Expected IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    private val sample = """{"code":"000000","message":null,"data":[
        {"adv":{"price":"4650.00","asset":"USDT","minSingleTransAmount":"20000","maxSingleTransAmount":"500000",
                "remark":null,"tradeMethods":[{"tradeMethodName":"MVola"},{"tradeMethodName":"Orange Money"}]},
         "advertiser":{"nickName":"a","monthOrderCount":42,"monthFinishRate":0.987}},
        {"adv":{"price":"4651.50"},"advertiser":{"nickName":"b"}},
        {"adv":{"price":"4660"},"advertiser":{"nickName":"c"}}],
        "total":120,"success":true}"""

    @Test
    fun `parses ad prices in response order`() {
        assertEquals(listOf(4650.0, 4651.5, 4660.0), BinanceP2pApi.parseAds(sample).map { it.price })
    }

    @Test
    fun `reads order limits and advertiser name`() {
        val ad = BinanceP2pApi.parseAds(sample).first()

        assertEquals(20_000L, ad.minLimit)
        assertEquals(500_000L, ad.maxLimit)
        assertEquals("a", ad.advertiser)
    }

    @Test
    fun `max limit is the dynamic one, capped by what is still available in the ad`() {
        val json = """{"code":"000000","data":[{"adv":{"price":"4420.05","minSingleTransAmount":"500000",
            "maxSingleTransAmount":"210000000","dynamicMaxSingleTransAmount":"8822455"}}]}"""

        assertEquals(8_822_455L, BinanceP2pApi.parseAds(json).single().maxLimit)
    }

    @Test
    fun `max limit falls back to the configured one without a dynamic value`() {
        val json = """{"code":"000000","data":[{"adv":{"price":"1","maxSingleTransAmount":"170543"}}]}"""

        assertEquals(170_543L, BinanceP2pApi.parseAds(json).single().maxLimit)
    }

    @Test
    fun `missing optional fields are null`() {
        val ad = BinanceP2pApi.parseAds(sample)[1]

        assertNull(ad.minLimit)
        assertNull(ad.maxLimit)
        assertEquals("b", ad.advertiser)
    }

    @Test
    fun `no ads gives an empty list`() {
        assertTrue(BinanceP2pApi.parseAds("""{"code":"000000","data":[],"total":0,"success":true}""").isEmpty())
    }

    @Test
    fun `ads without a numeric price are skipped`() {
        val json = """{"code":"000000","data":[{"adv":{"price":"abc"}},{"adv":{"price":"4600"}}]}"""

        assertEquals(listOf(4600.0), BinanceP2pApi.parseAds(json).map { it.price })
    }

    @Test
    fun `error code is reported as IOException`() {
        try {
            BinanceP2pApi.parseAds("""{"code":"000002","message":"illegal parameter","data":null}""")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertFalse(e.message.isNullOrEmpty())
        }
    }
}
