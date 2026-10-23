package dev.fitiavana.accounting.features.alerts

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.fitiavana.accounting.features.accounts.Account

/** Which way the gain/loss % must cross the threshold for an alert to fire. Stored as text. */
object AlertTypes {
    const val CROSSING_ABOVE = "crossing_above"
    const val CROSSING_BELOW = "crossing_below"
}

/**
 * Notifies when [accountId]'s gain/loss % (the one shown on Home) crosses [thresholdPercent]:
 * positive for a gain, negative for a loss. Deleting the account deletes its alerts.
 */
@Entity(
    tableName = "gain_loss_alerts",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class GainLossAlert(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val thresholdPercent: Double,
    val createdAt: Long
) {
    /** Changes whenever the alert is edited in a way that restarts its crossing detection. */
    val signature: String get() = "$accountId|$type|$thresholdPercent"
}

/**
 * Device-local detection state of an alert; never synced or backed up. [signature] is the alert's
 * [GainLossAlert.signature] when the state was written, so a state that no longer matches (the alert was
 * edited here or by a sync) is discarded and re-seeded silently.
 */
@Entity(
    tableName = "alert_states",
    foreignKeys = [
        ForeignKey(
            entity = GainLossAlert::class,
            parentColumns = ["id"],
            childColumns = ["alertId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AlertState(
    @PrimaryKey val alertId: String,
    val armed: Boolean,
    val signature: String
)
