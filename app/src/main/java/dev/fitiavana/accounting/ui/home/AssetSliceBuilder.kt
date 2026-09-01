package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.reports.AssetColorIndex

data class AssetSlice(val name: String, val amount: Long)

/** Asset allocation slices for the Home pie chart — a Home-screen-only concern, not report domain logic. */
object AssetSliceBuilder {

    /**
     * Asset lines grouped the same way as the "Assets" section of the balance sheet
     * (via [AssetColorIndex]), so slice colors stay in sync with the balance sheet's
     * per-account color dots: accounts with |balance| below
     * [AssetColorIndex.OTHER_ASSET_THRESHOLD] are collapsed into "Other".
     */
    fun assetSlices(accounts: List<Account>, balances: List<AccountBalance>): List<AssetSlice> {
        val result = AssetColorIndex.compute(accounts, balances)
        val slices = result.main.map { AssetSlice(it.name, it.balance) }.toMutableList()
        if (result.other.isNotEmpty()) {
            slices += AssetSlice("Other", result.other.sumOf { it.balance })
        }
        return slices
    }
}
