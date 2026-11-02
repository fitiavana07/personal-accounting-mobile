package dev.fitiavana.accounting.features.chainmonitors

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Notifies when [holderAddress]'s balance of the ERC-20 [tokenAddress] on Base is greater than [threshold]
 * (a decimal in token units, kept as text so it is exact; 0 is valid). [tokenSymbol] and [tokenDecimals] are
 * read from the token when the monitor is saved. The balance is fetched about every [intervalMinutes].
 */
@Entity(tableName = "chain_monitors")
data class ChainMonitor(
    @PrimaryKey val id: String,
    val label: String,
    val holderAddress: String,
    val tokenAddress: String,
    val tokenSymbol: String,
    val tokenDecimals: Int,
    val threshold: String,
    val intervalMinutes: Int,
    val createdAt: Long
) {
    val thresholdValue: BigDecimal get() = BigDecimal(threshold)

    /** Changes whenever the monitor is edited in a way that restarts its crossing detection. */
    val signature: String get() = "$holderAddress|$tokenAddress|$threshold"

    companion object {
        val INTERVALS_MINUTES = listOf(15, 30)
    }
}

/**
 * Device-local detection state of a monitor; never synced or backed up. [signature] is the monitor's
 * [ChainMonitor.signature] when the state was written, so a stale state is discarded and re-seeded.
 * [lastBalance] is the last balance read, in token units, for display when a fetch fails.
 */
@Entity(
    tableName = "chain_monitor_states",
    foreignKeys = [
        ForeignKey(
            entity = ChainMonitor::class,
            parentColumns = ["id"],
            childColumns = ["monitorId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MonitorState(
    @PrimaryKey val monitorId: String,
    val armed: Boolean,
    val signature: String,
    val lastCheckedAt: Long,
    val lastBalance: String?
)
