package dev.fitiavana.accounting.ui.common

import dev.fitiavana.accounting.features.instruments.Instrument

object TransactionDisplay {

    fun formatInstrumentAmount(amount: Long, instrument: Instrument): String =
        "${formatInstrumentValue(amount, instrument)} ${instrument.code}"

    /** Like [formatInstrumentAmount] but without the instrument code, e.g. for report columns. */
    fun formatInstrumentValue(amount: Long, instrument: Instrument): String {
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        return if (instrument.decimalPlaces > 0) {
            formatDecimalValue(amount / factor, instrument.decimalPlaces)
        } else {
            formatAmount(amount)
        }
    }

    private fun formatDecimalValue(value: Double, decimalPlaces: Int): String {
        val raw = String.format("%.${decimalPlaces}f", value)
        val stripped = raw.trimEnd('0')
        val dotPos = stripped.indexOf('.')
        val intPart = String.format("%,d", stripped.substring(0, dotPos).toLong())
        val decPart = stripped.substring(dotPos + 1).ifEmpty { "0" }
        return "$intPart.$decPart"
    }

    fun formatAccountList(names: List<String>): String {
        if (names.isEmpty()) return "?"
        return when {
            names.size <= 2 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + ", ..."
        }
    }

    fun formatNotePreview(note: String): String {
        if (note.isBlank()) return ""
        val lines = note.lines()
        val firstLine = lines.first()
        return if (lines.size > 1) {
            if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine + "..."
        } else {
            if (firstLine.length > 60) firstLine.take(60) + "..." else firstLine
        }
    }

    fun formatAmount(amount: Long): String = String.format("%,d", amount)

    fun formatExchangeRate(
        baseAmount: Long,
        instrumentAmount: Long,
        instrument: Instrument
    ): String? {
        if (instrumentAmount == 0L) return null
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        val rate = Math.round(baseAmount * factor / instrumentAmount)
        return "1 ${instrument.code} = Ar ${formatAmount(rate)}"
    }

    fun formatInstrumentExchangeRate(
        fromAmount: Long,
        fromInstrument: Instrument,
        toAmount: Long,
        toInstrument: Instrument
    ): String? {
        if (fromAmount == 0L) return null
        val fromFactor = Math.pow(10.0, fromInstrument.decimalPlaces.toDouble())
        val toFactor = Math.pow(10.0, toInstrument.decimalPlaces.toDouble())
        val rate = (toAmount / toFactor) / (fromAmount / fromFactor)
        return formatInstrumentRate(fromInstrument, rate, toInstrument)
    }

    /** An APR as a plain decimal without trailing zeros ("5.5", "12"), e.g. for an edit field. */
    fun formatApr(percent: Double): String =
        java.math.BigDecimal.valueOf(percent).stripTrailingZeros().toPlainString()

    /** An APR with its sign, e.g. "5.5%". */
    fun formatAprPercent(percent: Double): String = "${formatApr(percent)}%"

    /** Formats an already-computed rate (e.g. a live fetched price), not derived from balances. */
    fun formatInstrumentRate(fromInstrument: Instrument, rate: Double, toInstrument: Instrument): String {
        val rateText = if (toInstrument.decimalPlaces > 0) {
            formatDecimalValue(rate, toInstrument.decimalPlaces)
        } else {
            formatAmount(Math.round(rate))
        }
        return "1 ${fromInstrument.code} = $rateText ${toInstrument.code}"
    }
}
