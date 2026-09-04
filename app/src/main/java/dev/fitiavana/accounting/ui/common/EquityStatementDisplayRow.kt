package dev.fitiavana.accounting.ui.common

/** Display-ready form of [dev.fitiavana.accounting.features.reports.EquityStatement]. */
data class EquityStatementDisplay(
    val columnTitles: List<String>,
    val rows: List<EquityStatementDisplayRow>
)

/** Display-ready form of [dev.fitiavana.accounting.features.reports.EquityStatementRow]. */
data class EquityStatementDisplayRow(
    val label: String,
    val cellTexts: List<String>,
    val emphasized: Boolean = false
)
