package dev.fitiavana.accounting.features.settings

import androidx.lifecycle.LiveData
import dev.fitiavana.accounting.features.sync.ChangeTracker
import dev.fitiavana.accounting.features.sync.SyncKind

class AppSettingsRepository(private val dao: AppSettingsDao, private val tracker: ChangeTracker) {
    fun observe(): LiveData<AppSettings?> = dao.observe()

    fun setMonthlyLivingExpenses(amount: Long) = tracker.inTransaction {
        dao.upsert(AppSettings(monthlyLivingExpenses = amount))
        tracker.touch(SyncKind.SETTINGS, AppSettings.SINGLETON_ID.toString())
    }
}
