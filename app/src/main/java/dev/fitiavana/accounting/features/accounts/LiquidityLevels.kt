package dev.fitiavana.accounting.features.accounts

object LiquidityLevels {
    const val CASH_AND_EQUIVALENTS = "cash_and_equivalents"
    const val ACCOUNTS_RECEIVABLE = "accounts_receivable"
    const val STOCKS = "stocks"
    const val CRYPTO = "crypto"
    const val OTHER_LONG_TERM_ASSETS = "other_long_term_assets"

    val VALUES = listOf(
        CASH_AND_EQUIVALENTS,
        ACCOUNTS_RECEIVABLE,
        STOCKS,
        CRYPTO,
        OTHER_LONG_TERM_ASSETS
    )

    const val UNCLASSIFIED_DISPLAY_NAME = "Unclassified"

    /** Display label for a liquidity level value, or [UNCLASSIFIED_DISPLAY_NAME] for `null`/unknown values. */
    fun displayName(value: String?): String = when (value) {
        CASH_AND_EQUIVALENTS -> "Cash & Cash Equivalents"
        ACCOUNTS_RECEIVABLE -> "Accounts Receivable"
        STOCKS -> "Stocks"
        CRYPTO -> "Crypto (non-stablecoin)"
        OTHER_LONG_TERM_ASSETS -> "Other Long-Term Assets"
        else -> UNCLASSIFIED_DISPLAY_NAME
    }
}
