package dev.fitiavana.accounting.features.settings

import androidx.lifecycle.LiveData

class AppSettingsRepository(private val dao: AppSettingsDao) {
    fun observe(): LiveData<AppSettings?> = dao.observe()

    fun setMonthlyLivingExpenses(amount: Long) =
        dao.upsert(AppSettings(monthlyLivingExpenses = amount))
}
