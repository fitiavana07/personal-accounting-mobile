package dev.fitiavana.accounting.features.reports

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import kotlin.math.abs

/**
 * Canonical asset ordering used to assign color-dot indices consistently
 * across the Home "Assets" pie chart (AssetSliceBuilder) and the Instant
 * Balance Sheet's per-account color dots (BalanceSheetBuilder): accounts
 * sorted by name then balance descending, with accounts whose |balance| is
 * below [OTHER_ASSET_THRESHOLD] collapsed into one trailing "Other".
 */
object AssetColorIndex {
    const val OTHER_ASSET_THRESHOLD = 10_000L

    data class RankedAsset(val accountId: String, val name: String, val balance: Long)

    data class Result(val main: List<RankedAsset>, val other: List<RankedAsset>) {
        /** Color index per account id; accounts collapsed into "Other" all share the trailing index. */
        fun colorIndexByAccountId(): Map<String, Int> {
            val map = mutableMapOf<String, Int>()
            main.forEachIndexed { index, ranked -> map[ranked.accountId] = index }
            other.forEach { map[it.accountId] = main.size }
            return map
        }
    }

    fun compute(accounts: List<Account>, balances: List<AccountBalance>): Result {
        val accountMap = accounts.associateBy { it.id }
        val ranked = balances
            .filter { accountMap.containsKey(it.accountId) }
            .filter { accountMap.getValue(it.accountId).type == "asset" }
            .filter { it.balance != 0L }
            .map { RankedAsset(it.accountId, accountMap.getValue(it.accountId).name, it.balance) }
            .sortedBy { it.name }
            .sortedByDescending { it.balance }

        val (main, other) = ranked.partition { abs(it.balance) >= OTHER_ASSET_THRESHOLD }
        return Result(main, other)
    }
}
