package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.LiquidityLevels
import dev.fitiavana.accounting.features.balances.AccountBalance

/** Sum of balances for asset accounts marked as Cash & Cash Equivalents — the emergency fund's basis. */
object LiquidAssetsBuilder {
    fun totalLiquidAssets(accounts: List<Account>, balances: List<AccountBalance>): Long {
        val accountMap = accounts.associateBy { it.id }
        return balances
            .filter { accountMap[it.accountId]?.type == "asset" }
            .filter { accountMap[it.accountId]?.liquidityLevel == LiquidityLevels.CASH_AND_EQUIVALENTS }
            .sumOf { it.balance }
    }
}
