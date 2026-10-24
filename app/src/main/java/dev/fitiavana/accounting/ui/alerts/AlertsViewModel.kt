package dev.fitiavana.accounting.ui.alerts

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.accounts.Account
import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.alerts.AlertCheckManager
import dev.fitiavana.accounting.features.alerts.AlertRepository
import dev.fitiavana.accounting.features.alerts.GainLossAlert
import dev.fitiavana.accounting.features.balances.AccountBalance
import dev.fitiavana.accounting.features.balances.AccountGainLoss
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.instruments.Instrument
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

/** An alert with the name of the account it watches. */
data class AlertItem(val alert: GainLossAlert, val accountName: String)

/** An account an alert can watch: one that has a gain/loss on Home. */
data class AccountChoice(val id: String, val name: String)

enum class AlertSaveError { NO_ACCOUNT, INVALID_THRESHOLD }

/** The configured gain/loss alerts, and creating, editing and deleting them. */
class AlertsViewModel(
    private val alertRepository: AlertRepository,
    accountRepository: AccountRepository,
    balanceRepository: BalanceRepository,
    instrumentRepository: InstrumentRepository,
    private val alertCheckManager: AlertCheckManager
) : ViewModel() {

    private val accounts = accountRepository.getAll()

    val items: LiveData<List<AlertItem>> = MediatorLiveData<List<AlertItem>>().apply {
        var latestAlerts: List<GainLossAlert> = emptyList()
        var latestAccounts: Map<String, String> = emptyMap()

        fun update() {
            value = latestAlerts.mapNotNull { alert ->
                latestAccounts[alert.accountId]?.let { AlertItem(alert, it) }
            }
        }
        addSource(alertRepository.getAll()) {
            latestAlerts = it ?: emptyList()
            update()
        }
        addSource(accounts) {
            latestAccounts = (it ?: emptyList()).associate { account -> account.id to account.name }
            update()
        }
    }

    val accountChoices: LiveData<List<AccountChoice>> = MediatorLiveData<List<AccountChoice>>().apply {
        var latestAccounts: List<Account> = emptyList()
        var latestBalances: List<AccountBalance> = emptyList()
        var latestInstruments: Map<String, Instrument> = emptyMap()

        fun update() {
            value = AccountGainLoss.positions(latestBalances, latestAccounts, latestInstruments, emptyMap())
                .map { AccountChoice(it.account.id, it.account.name) }
        }
        addSource(accounts) {
            latestAccounts = it ?: emptyList()
            update()
        }
        addSource(balanceRepository.getAll()) {
            latestBalances = it ?: emptyList()
            update()
        }
        addSource(instrumentRepository.getAll()) {
            latestInstruments = (it ?: emptyList()).associateBy { instrument -> instrument.code }
            update()
        }
    }

    /**
     * Validates the form and, when it is valid, saves it in the background (a new alert when [existing] is
     * null, otherwise [existing] edited). Returns what is wrong, or null when saving started.
     */
    fun save(existing: GainLossAlert?, accountId: String?, type: String, thresholdText: String): AlertSaveError? {
        validate(accountId, thresholdText)?.let { return it }
        Thread { saveSync(existing, accountId, type, thresholdText) }.start()
        return null
    }

    internal fun saveSync(existing: GainLossAlert?, accountId: String?, type: String, thresholdText: String): AlertSaveError? {
        validate(accountId, thresholdText)?.let { return it }
        val threshold = parseThreshold(thresholdText)!!
        if (existing == null) {
            alertRepository.create(accountId!!, type, threshold)
        } else {
            alertRepository.update(existing.copy(accountId = accountId!!, type = type, thresholdPercent = threshold))
        }
        alertCheckManager.ensureScheduled()
        // Seeds the new alert from the cached rates now, so a crossing before the next hourly check still fires later.
        alertCheckManager.check()
        return null
    }

    fun delete(id: String) {
        Thread { deleteSync(id) }.start()
    }

    internal fun deleteSync(id: String) = alertRepository.delete(id)

    /**
     * App start: arms the hourly alarm, which a reboot or a force stop clears. [onHasAlerts] runs on the
     * main thread when alerts exist, so the caller can make sure notifications are allowed.
     */
    fun ensureScheduled(onHasAlerts: () -> Unit = {}) {
        Thread {
            if (ensureScheduledSync()) Handler(Looper.getMainLooper()).post(onHasAlerts)
        }.start()
    }

    /** Arms the alarm; returns whether any alert exists. */
    internal fun ensureScheduledSync(): Boolean {
        alertCheckManager.ensureScheduled()
        return alertRepository.getAllSync().isNotEmpty()
    }

    private fun validate(accountId: String?, thresholdText: String): AlertSaveError? = when {
        accountId == null -> AlertSaveError.NO_ACCOUNT
        parseThreshold(thresholdText) == null -> AlertSaveError.INVALID_THRESHOLD
        else -> null
    }

    companion object {
        /** A signed percentage such as "12.5", "+3" or "-4,5"; null when unreadable, infinite or zero (nothing to cross). */
        fun parseThreshold(text: String): Double? {
            val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
            return value.takeIf { it.isFinite() && it != 0.0 }
        }
    }
}

class AlertsViewModelFactory(
    private val alertRepository: AlertRepository,
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository,
    private val instrumentRepository: InstrumentRepository,
    private val alertCheckManager: AlertCheckManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AlertsViewModel(
            alertRepository, accountRepository, balanceRepository, instrumentRepository, alertCheckManager
        ) as T
    }
}
