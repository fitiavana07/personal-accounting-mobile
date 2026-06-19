package dev.fitiavana.accounting.ui.transactions

object TransactionDisplay {

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

    fun formatAmount(amount: Int): String = String.format("%,d", amount)
}
