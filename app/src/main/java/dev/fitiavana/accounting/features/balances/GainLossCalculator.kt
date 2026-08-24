package dev.fitiavana.accounting.features.balances

object GainLossCalculator {

    fun computeCurrentValue(instrumentBalance: Long, instrumentDecimalPlaces: Int, rate: Double): Double {
        val factor = Math.pow(10.0, instrumentDecimalPlaces.toDouble())
        return (instrumentBalance / factor) * rate
    }

    fun computeGainLoss(currentValue: Double, bookValue: Double): Double = currentValue - bookValue

    fun computeGainLossPercent(gainLoss: Double, bookValue: Double): Double? {
        if (bookValue == 0.0) return null
        return (gainLoss / bookValue) * 100.0
    }
}
