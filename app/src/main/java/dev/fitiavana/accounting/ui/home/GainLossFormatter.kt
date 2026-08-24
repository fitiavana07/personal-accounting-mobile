package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.ui.common.TransactionDisplay

object GainLossFormatter {

    fun formatSignedAmount(value: Double, instrument: Instrument): String {
        val sign = if (value >= 0) "+" else "-"
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        val scaledAmount = Math.round(Math.abs(value) * factor)
        return "$sign${TransactionDisplay.formatInstrumentAmount(scaledAmount, instrument)}"
    }

    fun formatSignedAmountAr(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return "${sign}Ar ${TransactionDisplay.formatAmount(Math.round(Math.abs(value)))}"
    }

    fun formatSignedPercent(value: Double): String {
        val sign = if (value >= 0) "+" else "-"
        return String.format("%s%.1f%%", sign, Math.abs(value))
    }
}
