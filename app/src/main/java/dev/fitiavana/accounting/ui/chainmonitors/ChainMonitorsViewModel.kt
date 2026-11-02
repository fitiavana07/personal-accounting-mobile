package dev.fitiavana.accounting.ui.chainmonitors

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitor
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitorCheckManager
import dev.fitiavana.accounting.features.chainmonitors.ChainMonitorRepository
import dev.fitiavana.accounting.network.evm.Erc20BalanceFetcher
import dev.fitiavana.accounting.network.evm.Erc20Codec
import dev.fitiavana.accounting.network.evm.RpcException
import dev.fitiavana.accounting.network.evm.TokenInfo
import java.math.BigDecimal

/** What the user typed in the monitor dialog. */
data class MonitorForm(
    val label: String,
    val holder: String,
    val token: String,
    val threshold: String,
    val intervalMinutes: Int
)

enum class MonitorSaveError { LABEL, HOLDER, TOKEN, THRESHOLD, NOT_A_TOKEN, NO_NETWORK, FAILED }

/** The configured chain monitors, and creating, editing and deleting them. */
class ChainMonitorsViewModel(
    private val repository: ChainMonitorRepository,
    private val fetcher: Erc20BalanceFetcher,
    private val manager: ChainMonitorCheckManager
) : ViewModel() {

    val items: LiveData<List<ChainMonitor>> = repository.getAll()

    /** What is wrong with the typed values themselves (no network involved), or null. */
    fun validate(form: MonitorForm): MonitorSaveError? = when {
        form.label.isBlank() -> MonitorSaveError.LABEL
        !Erc20Codec.isValidAddress(form.holder.trim()) -> MonitorSaveError.HOLDER
        !Erc20Codec.isValidAddress(form.token.trim()) -> MonitorSaveError.TOKEN
        parseThreshold(form.threshold) == null -> MonitorSaveError.THRESHOLD
        else -> null
    }

    /**
     * Saves in the background (a new monitor when [existing] is null, otherwise [existing] edited): the token's
     * symbol and decimals are read from the chain first. [onDone] runs on the main thread with what went wrong,
     * or null once saved.
     */
    fun save(existing: ChainMonitor?, form: MonitorForm, onDone: (MonitorSaveError?) -> Unit) {
        validate(form)?.let { return onDone(it) }
        Thread {
            val result = saveSync(existing, form)
            Handler(Looper.getMainLooper()).post { onDone(result) }
        }.start()
    }

    internal fun saveSync(existing: ChainMonitor?, form: MonitorForm): MonitorSaveError? {
        validate(form)?.let { return it }
        val label = form.label.trim()
        val holder = form.holder.trim()
        val threshold = parseThreshold(form.threshold)!!
        val token = existing?.tokenAddress?.takeIf { it.equals(form.token.trim(), ignoreCase = true) } ?: form.token.trim()
        val info = if (existing != null && existing.tokenAddress == token) {
            TokenInfo(existing.tokenSymbol, existing.tokenDecimals)
        } else {
            try {
                fetcher.fetchTokenInfo(token)
            } catch (e: RpcException.NoNetwork) {
                return MonitorSaveError.NO_NETWORK
            } catch (e: RpcException.Malformed) {
                return MonitorSaveError.NOT_A_TOKEN
            } catch (e: RpcException.Rpc) {
                return MonitorSaveError.NOT_A_TOKEN
            } catch (e: Exception) {
                return MonitorSaveError.FAILED
            }
        }
        if (existing == null) {
            repository.create(label, holder, token, info.symbol, info.decimals, threshold, form.intervalMinutes)
        } else {
            repository.update(
                existing.copy(
                    label = label, holderAddress = holder, tokenAddress = token, tokenSymbol = info.symbol,
                    tokenDecimals = info.decimals, threshold = threshold, intervalMinutes = form.intervalMinutes
                )
            )
        }
        manager.refreshSchedule()
        try {
            // Reads the balance now, so a balance that is already above the threshold notifies without waiting for the alarm.
            manager.check()
        } catch (e: Exception) {
            // The monitor is saved; the next alarm checks it.
        }
        return null
    }

    fun delete(id: String) {
        Thread { deleteSync(id) }.start()
    }

    internal fun deleteSync(id: String) {
        repository.delete(id)
        manager.refreshSchedule()
    }

    /**
     * App start: re-arms the alarm, which a force stop clears. [onHasMonitors] runs on the main thread when
     * monitors exist, so the caller can make sure notifications are allowed.
     */
    fun ensureScheduled(onHasMonitors: () -> Unit = {}) {
        Thread {
            if (ensureScheduledSync()) Handler(Looper.getMainLooper()).post(onHasMonitors)
        }.start()
    }

    /** Re-arms the alarm; returns whether any monitor exists. */
    internal fun ensureScheduledSync(): Boolean {
        manager.refreshSchedule()
        return repository.getAllSync().isNotEmpty()
    }

    companion object {
        /** A number of zero or more such as "0", "2,5" or "1000.25", as plain text without trailing zeros; null otherwise. */
        fun parseThreshold(text: String): String? {
            val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
            return value.takeIf { it.signum() >= 0 }?.stripTrailingZeros()?.toPlainString()
        }
    }
}

class ChainMonitorsViewModelFactory(
    private val repository: ChainMonitorRepository,
    private val fetcher: Erc20BalanceFetcher,
    private val manager: ChainMonitorCheckManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChainMonitorsViewModel(repository, fetcher, manager) as T
    }
}
