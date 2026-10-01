package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.network.p2p.P2pAd
import org.junit.Assert.assertEquals
import org.junit.Test

class P2pAdFormatterTest {

    @Test
    fun `short advertiser names are kept whole`() {
        assertEquals("Rakoto", P2pAdFormatter.advertiser(P2pAd(1.0, advertiser = "Rakoto")))
    }

    @Test
    fun `long advertiser names are truncated with dots`() {
        assertEquals("Wicken...", P2pAdFormatter.advertiser(P2pAd(1.0, advertiser = "Wickendo man")))
    }

    @Test
    fun `missing advertiser is empty`() {
        assertEquals("", P2pAdFormatter.advertiser(P2pAd(1.0)))
    }

    @Test
    fun `limits are a compact range`() {
        assertEquals("20.0K–500K", P2pAdFormatter.limits(P2pAd(4600.0, minLimit = 20_000, maxLimit = 500_000)))
    }

    @Test
    fun `only one limit is shown with its direction`() {
        assertEquals("min 20.0K", P2pAdFormatter.limits(P2pAd(1.0, minLimit = 20_000)))
        assertEquals("max 500K", P2pAdFormatter.limits(P2pAd(1.0, maxLimit = 500_000)))
    }

    @Test
    fun `no limits is empty`() {
        assertEquals("", P2pAdFormatter.limits(P2pAd(1.0)))
    }
}
