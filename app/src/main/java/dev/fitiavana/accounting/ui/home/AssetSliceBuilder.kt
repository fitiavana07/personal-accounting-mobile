package dev.fitiavana.accounting.ui.home

import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.balances.AccountBalance

data class AssetSlice(val name: String, val amount: Long)

/** Asset allocation slices for the Home pie chart — a Home-screen-only concern, not report domain logic. */
object AssetSliceBuilder {

    private const val OTHER_ASSET_THRESHOLD = 10_000L

    private data class NamedBalance(val name: String, val balance: Long)

    /**
     * Asset lines grouped the same way as the "Assets" section of the balance sheet:
     * accounts with |balance| below [OTHER_ASSET_THRESHOLD] are collapsed into "Other".
     */
    fun assetSlices(accounts: List<Account>, balances: List<AccountBalance>): List<AssetSlice> {
        val accountMap = accounts.associateBy { it.id }
        val assetLines = balances.associate { it.accountId to it.balance }.entries
            .filter { accountMap.containsKey(it.key) }
            .filter { accountMap.getValue(it.key).type == "asset" }
            .filter { it.value != 0L }
            .map { NamedBalance(accountMap.getValue(it.key).name, it.value) }
            .sortedBy { it.name }
            .sortedByDescending { it.balance }
        if (assetLines.isEmpty()) return emptyList()

        val (mainAssetLines, otherAssetLines) = assetLines.partition {
            Math.abs(it.balance) >= OTHER_ASSET_THRESHOLD
        }

        val slices = mainAssetLines.map { AssetSlice(it.name, it.balance) }.toMutableList()
        if (otherAssetLines.isNotEmpty()) {
            slices += AssetSlice("Other", otherAssetLines.sumOf { it.balance })
        }
        return slices
    }
}
