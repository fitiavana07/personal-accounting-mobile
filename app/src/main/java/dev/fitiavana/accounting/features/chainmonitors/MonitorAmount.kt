package dev.fitiavana.accounting.features.chainmonitors

import java.math.BigDecimal
import java.text.DecimalFormat

/** Shows a token amount with thousands separators and no trailing zeros, e.g. `1,234.5`. */
object MonitorAmount {
    private const val MAX_FRACTION_DIGITS = 18

    fun format(value: BigDecimal): String =
        DecimalFormat("#,##0." + "#".repeat(MAX_FRACTION_DIGITS)).format(value)

    /** [lastBalance] is a plain decimal string as kept in [MonitorState]; null means no reading yet. */
    fun format(lastBalance: String?): String? = lastBalance?.let { format(BigDecimal(it)) }
}
