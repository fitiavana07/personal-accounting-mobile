package dev.fitiavana.accounting.ui.transactions

import dev.fitiavana.accounting.data.model.Instrument

object TransactionDisplay {

    fun formatInstrumentAmount(amount: Long, instrument: Instrument): String {
        val factor = Math.pow(10.0, instrument.decimalPlaces.toDouble())
        return if (instrument.decimalPlaces > 0) {
            val raw = String.format("%.${instrument.decimalPlaces}f", amount / factor)
            val stripped = raw.trimEnd('0')
            val dotPos = stripped.indexOf('.')
            val intPart = String.format("%,d", stripped.substring(0, dotPos).toLong())
            val decPart = stripped.substring(dotPos + 1).ifEmpty { "0" }
            "$intPart.$decPart ${instrument.code}"
        } else {
            "${String.format("%,d", amount)} ${instrument.code}"
        }
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
}
