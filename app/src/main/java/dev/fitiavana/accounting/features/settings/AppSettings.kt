package dev.fitiavana.accounting.features.settings

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table for app-wide settings. */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val monthlyLivingExpenses: Long,
    /** The asset account shown first in Home's gains/losses; at most one, so it lives on this single row. */
    val pinnedAccountId: String? = null
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
