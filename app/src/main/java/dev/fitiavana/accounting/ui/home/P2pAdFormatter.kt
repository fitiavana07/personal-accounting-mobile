package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.network.p2p.P2pAd

/** Short summary of an ad shown beside its price: advertiser · order limits (MGA, compact). */
object P2pAdFormatter {
    private const val MAX_NAME_LENGTH = 8

    fun details(ad: P2pAd): String =
        listOfNotNull(ad.advertiser?.let(::shortName), limits(ad)).joinToString(" · ")

    private fun shortName(name: String) =
        if (name.length > MAX_NAME_LENGTH) name.take(MAX_NAME_LENGTH).trimEnd() + "..." else name

    private fun limits(ad: P2pAd): String? {
        val min = ad.minLimit?.let { CompactNumberFormatter.format(it) }
        val max = ad.maxLimit?.let { CompactNumberFormatter.format(it) }
        return when {
            min != null && max != null -> "$min–$max"
            min != null -> "min $min"
            max != null -> "max $max"
            else -> null
        }
    }
}
