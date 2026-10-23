package dev.fitiavana.accounting.features.alerts

import dev.fitiavana.accounting.features.accounts.AccountRepository
import dev.fitiavana.accounting.features.balances.AccountGainLoss
import dev.fitiavana.accounting.features.balances.BalanceRepository
import dev.fitiavana.accounting.features.exchangerates.ExchangeRateRepository
import dev.fitiavana.accounting.features.exchangerates.RateRefresher
import dev.fitiavana.accounting.features.instruments.InstrumentRepository

/** Arms (and disarms) the single alarm that triggers the next alert check. */
interface AlertScheduler {
    fun scheduleAt(triggerAtMillis: Long)
    fun cancel()
}

/** Tells the user an alert fired. [accountName] is the account the alert watches. */
interface AlertNotifier {
    fun notifyFired(fired: FiredAlert, accountName: String)
}

/**
 * Checks the gain/loss alerts against the cached rates. It is driven from three places: the hourly
 * alarm ([onAlarm], which refreshes the rates first), the Home rate refresh ([check], once the rates
 * are fresh) and app start / boot ([ensureScheduled]). Everything is synchronous: call it off the
 * main thread. Failures never throw out of [onAlarm]; the next alarm is armed first.
 */
class AlertCheckManager(
    private val alertRepository: AlertRepository,
    private val balanceRepository: BalanceRepository,
    private val accountRepository: AccountRepository,
    private val instrumentRepository: InstrumentRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val rateRefresher: RateRefresher,
    private val notifier: AlertNotifier,
    private val scheduler: AlertScheduler,
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Evaluates every alert against the rates already cached and notifies the ones that crossed. */
    @Synchronized
    fun check() {
        val alerts = alertRepository.getAllSync()
        if (alerts.isEmpty()) return
        val accounts = accountRepository.getAllSync()
        val percents = AccountGainLoss.percentByAccount(
            balanceRepository.getAllSync(),
            accounts,
            instrumentRepository.getAllSync().associateBy { it.code },
            exchangeRateRepository.getAllCachedSync().associateBy { it.pairKey }
        )
        val states = alertRepository.getStatesSync().associateBy { it.alertId }
        val result = AlertEvaluator.evaluate(alerts, percents, states)
        // Saved before notifying, so a crash in between cannot notify the same crossing twice.
        if (result.states.isNotEmpty()) alertRepository.saveStates(result.states)
        val names = accounts.associate { it.id to it.name }
        result.fired.forEach { notifier.notifyFired(it, names[it.alert.accountId].orEmpty()) }
    }

    /** The alarm fired: arm the next one, then refresh the rates and check (skipped with no alerts). */
    fun onAlarm() {
        scheduler.scheduleAt(clock() + INTERVAL_MILLIS)
        try {
            if (alertRepository.getAllSync().isEmpty()) return
            rateRefresher.refresh()
            check()
        } catch (e: Exception) {
            // Offline or a fetch failed: the next alarm tries again.
        }
    }

    /** App start and boot: (re)arms the alarm, which a reboot or a force stop clears. */
    fun ensureScheduled() {
        scheduler.scheduleAt(clock() + INTERVAL_MILLIS)
    }

    companion object {
        const val INTERVAL_MILLIS = 60L * 60 * 1000
    }
}
