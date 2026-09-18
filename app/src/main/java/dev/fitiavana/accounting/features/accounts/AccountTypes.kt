package dev.fitiavana.accounting.features.accounts

object AccountTypes {
    const val ASSET = "asset"
    const val LIABILITY = "liability"
    const val EQUITY = "equity"
    const val REVENUE = "revenue"
    const val EXPENSE = "expense"
    const val DRAWING = "drawing"
    const val GAIN = "gain"
    const val LOSS = "loss"

    val VALUES = listOf(ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE, DRAWING, GAIN, LOSS)
}
