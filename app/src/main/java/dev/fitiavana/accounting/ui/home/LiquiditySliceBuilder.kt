package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance

/** Home pie chart grouping asset balances by liquidity level, in [LiquidityLevels.VALUES] order (unclassified last). */
object LiquiditySliceBuilder {
    fun liquiditySlices(accounts: List<Account>, balances: List<AccountBalance>): List<AssetSlice> {
        val accountMap = accounts.associateBy { it.id }
        val assetBalances = balances.filter { accountMap[it.accountId]?.type == "asset" }

        val groupOrder = LiquidityLevels.VALUES + listOf<String?>(null)
        return groupOrder.mapNotNull { liquidityLevel ->
            val total = assetBalances
                .filter { accountMap.getValue(it.accountId).liquidityLevel == liquidityLevel }
                .sumOf { it.balance }
            if (total == 0L) null else AssetSlice(LiquidityLevels.displayName(liquidityLevel), total)
        }
    }
}
