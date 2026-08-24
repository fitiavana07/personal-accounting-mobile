package dev.fitiavana.accounting.features.balances

import kotlin.math.pow

object GainLossCalculator {

    /**
     * Converts a raw integer instrument balance into its current market value.
     *
     * Instrument balances are stored as integers scaled by
     * [instrumentDecimalPlaces] (e.g. shares held to 4 decimal places), so
     * the balance must be divided back down before applying the market rate.
     *
     * Example: 0.5 BTC (50000000 satoshis, 8 decimal places) at a market
     * price of $65000 per BTC:
     * `computeCurrentValue(50000000, 8, 65000.0)` -> `(50000000 / 10^8) * 65000.0`
     * = `0.5 * 65000.0` = `32500.0`
     *
     * @param instrumentBalance raw integer quantity held, scaled by
     *   `10^instrumentDecimalPlaces`
     * @param instrumentDecimalPlaces number of decimal places the
     *   instrument's quantity is scaled by
     * @param rate current market price per whole unit of the instrument
     * @return current market value of the held quantity
     */
    fun computeCurrentValue(
        instrumentBalance: Long,
        instrumentDecimalPlaces: Int,
        rate: Double
    ): Double {
        val factor = 10.0.pow(instrumentDecimalPlaces.toDouble())
        return (instrumentBalance / factor) * rate
    }

    fun computeGainLoss(currentValue: Double, bookValue: Double): Double =
        currentValue - bookValue

    // Percent is undefined when there is no cost basis to compare against.
    fun computeGainLossPercent(gainLoss: Double, bookValue: Double): Double? {
        if (bookValue == 0.0) return null
        return (gainLoss / bookValue) * 100.0
    }
}
