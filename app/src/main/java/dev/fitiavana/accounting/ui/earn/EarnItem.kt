package dev.fitiavana.accounting.ui.earn

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountTypes
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.YieldAmounts
import dev.fitiavana.accounting.features.balances.YieldCalculator
import dev.fitiavana.accounting.features.balances.YieldPeriod
import dev.fitiavana.accounting.features.instruments.Instrument

/** One Earn account with its balances and the interest it is projected to pay per period. */
data class EarnItem(
    val account: Account,
    val aprPercent: Double,
    val balance: Long,
    val instrumentBalance: Long,
    val instrument: Instrument?,
    val intermediaryBalance: Long,
    val intermediaryInstrument: Instrument?,
    val daily: YieldAmounts,
    val monthly: YieldAmounts,
    val yearly: YieldAmounts,
    /** This account's share of the total yearly base interest, 0 to 100. */
    val yearlySharePercent: Int = 0
)

/** Base-currency interest summed over every Earn account, per period. */
data class EarnTotals(val daily: Long, val monthly: Long, val yearly: Long)

data class EarnState(val items: List<EarnItem>, val totals: EarnTotals)

object EarnItemBuilder {

    /** Earn accounts are the asset accounts that have a positive APR. */
    fun build(
        accounts: List<Account>,
        balances: List<AccountBalance>,
        instruments: Map<String, Instrument>
    ): EarnState {
        val balanceByAccountId = balances.associateBy { it.accountId }
        val items = accounts
            .filter { it.type == AccountTypes.ASSET && (it.aprPercent ?: 0.0) > 0.0 }
            .map { account -> toItem(account, balanceByAccountId[account.id], instruments) }
            .sortedWith(compareByDescending<EarnItem> { it.yearly.base }.thenBy { it.account.name })
        val totals = EarnTotals(
            daily = items.sumOf { it.daily.base },
            monthly = items.sumOf { it.monthly.base },
            yearly = items.sumOf { it.yearly.base }
        )
        return EarnState(
            items = items.map { it.copy(yearlySharePercent = sharePercent(it.yearly.base, totals.yearly)) },
            totals = totals
        )
    }

    private fun sharePercent(part: Long, total: Long): Int =
        if (total <= 0L) 0 else Math.round(part * 100.0 / total).toInt()

    private fun toItem(account: Account, balance: AccountBalance?, instruments: Map<String, Instrument>): EarnItem {
        val apr = account.aprPercent ?: 0.0
        val instrument = account.instrumentCode?.let { instruments[it] }
        // The intermediary only exists next to an instrument, as everywhere else in the app.
        val intermediaryInstrument = account.intermediaryInstrumentCode?.let { instruments[it] }
            .takeIf { instrument != null }
        val base = balance?.balance ?: 0L
        val instrumentBalance = balance?.instrumentBalance ?: 0L
        val intermediaryBalance = balance?.intermediaryBalance ?: 0L

        fun project(period: YieldPeriod): YieldAmounts = YieldCalculator.project(
            aprPercent = apr,
            balance = base,
            instrumentBalance = instrumentBalance.takeIf { instrument != null },
            intermediaryBalance = intermediaryBalance.takeIf { intermediaryInstrument != null },
            period = period
        ) ?: YieldAmounts(0L, null, null)

        return EarnItem(
            account = account,
            aprPercent = apr,
            balance = base,
            instrumentBalance = instrumentBalance,
            instrument = instrument,
            intermediaryBalance = intermediaryBalance,
            intermediaryInstrument = intermediaryInstrument,
            daily = project(YieldPeriod.DAILY),
            monthly = project(YieldPeriod.MONTHLY),
            yearly = project(YieldPeriod.YEARLY)
        )
    }
}
