package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.network.p2p.P2pAd
import org.junit.Assert.assertEquals
import org.junit.Test

class P2pAdFormatterTest {

    @Test
    fun `shows advertiser and compact limit range`() {
        val ad = P2pAd(4600.0, minLimit = 20_000, maxLimit = 500_000, advertiser = "Rakoto")

        assertEquals("Rakoto · 20.0K–500K", P2pAdFormatter.details(ad))
    }

    @Test
    fun `long advertiser names are truncated with dots`() {
        assertEquals("Wickendo... · 1.00K–2.00K", P2pAdFormatter.details(P2pAd(1.0, 1_000, 2_000, "Wickendo man")))
    }

    @Test
    fun `name at the limit is kept whole`() {
        assertEquals("Wickendo", P2pAdFormatter.details(P2pAd(1.0, advertiser = "Wickendo")))
    }

    @Test
    fun `missing parts are left out`() {
        assertEquals("20.0K–500K", P2pAdFormatter.details(P2pAd(1.0, 20_000, 500_000)))
        assertEquals("Rakoto", P2pAdFormatter.details(P2pAd(1.0, advertiser = "Rakoto")))
        assertEquals("", P2pAdFormatter.details(P2pAd(1.0)))
    }

    @Test
    fun `only one limit is shown with its direction`() {
        assertEquals("min 20.0K", P2pAdFormatter.details(P2pAd(1.0, minLimit = 20_000)))
        assertEquals("max 500K", P2pAdFormatter.details(P2pAd(1.0, maxLimit = 500_000)))
    }
}
