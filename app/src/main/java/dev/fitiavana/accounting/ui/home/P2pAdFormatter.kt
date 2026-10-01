package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.network.p2p.P2pAd

/** Texts shown beside an ad's price: a short advertiser name and the compact MGA order limit range. */
object P2pAdFormatter {
    private const val MAX_NAME_LENGTH = 6

    fun advertiser(ad: P2pAd): String {
        val name = ad.advertiser ?: return ""
        return if (name.length > MAX_NAME_LENGTH) name.take(MAX_NAME_LENGTH).trimEnd() + "..." else name
    }

    fun limits(ad: P2pAd): String {
        val min = ad.minLimit?.let { CompactNumberFormatter.format(it) }
        val max = ad.maxLimit?.let { CompactNumberFormatter.format(it) }
        return when {
            min != null && max != null -> "$min–$max"
            min != null -> "min $min"
            max != null -> "max $max"
            else -> ""
        }
    }
}
