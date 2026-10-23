package dev.fitiavana.accounting.features.settings

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.SyncKind

class AppSettingsRepository(private val dao: AppSettingsDao, private val tracker: ChangeTracker) {
    fun observe(): LiveData<AppSettings?> = dao.observe()

    fun setMonthlyLivingExpenses(amount: Long) = update { it.copy(monthlyLivingExpenses = amount) }

    /** Pins [accountId] first on Home, replacing any other pin; pinning the pinned account unpins it. */
    fun togglePin(accountId: String) = update {
        it.copy(pinnedAccountId = accountId.takeUnless { id -> id == it.pinnedAccountId })
    }

    private fun update(change: (AppSettings) -> AppSettings) = tracker.inTransaction {
        dao.upsert(change(dao.getSync() ?: AppSettings(monthlyLivingExpenses = 0)))
        tracker.touch(SyncKind.SETTINGS, AppSettings.SINGLETON_ID.toString())
    }
}
