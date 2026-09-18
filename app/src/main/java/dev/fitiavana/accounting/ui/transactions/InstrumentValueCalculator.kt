package dev.fitiavana.accounting.ui.transactions

import kotlin.math.roundToLong

/**
 * Base-currency/instrument rate math shared by the instrument-aware
 * transaction modes (Instrument Transfer, Instrument Income): converting an
 * instrument amount to base currency using an account's own implicit
 * exchange rate.
 */
object InstrumentValueCalculator {

    /**
     * The base-currency amount implied by applying [instrumentAmount] units
     * at the implicit rate of an account whose prior state was [balance] Ar
     * for [instrumentBalance] instrument units. Null when the account has no
     * prior instrument balance to establish that rate from.
     */
    fun computeBaseAmount(
        instrumentAmount: Long,
        balance: Long,
        instrumentBalance: Long
    ): Long? {
        if (instrumentBalance <= 0L) return null
        return (instrumentAmount.toDouble() * balance.toDouble() / instrumentBalance.toDouble())
            .roundToLong()
    }
}
