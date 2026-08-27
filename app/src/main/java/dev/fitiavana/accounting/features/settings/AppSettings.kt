package dev.fitiavana.accounting.features.settings

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table for app-wide settings. */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val monthlyLivingExpenses: Long
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
