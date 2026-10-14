package dev.fitiavana.accounting.ui.sync

import android.content.Context
import dev.fitiavana.accounting.R
import dev.fitiavana.accounting.features.sync.PeerSync
import dev.fitiavana.accounting.features.sync.SessionFailure
import java.text.DateFormat
import java.util.Date

/** Turns the state of a sync into the plain words the sync screen shows. */
object SyncStatusFormatter {

    fun peer(context: Context, status: PeerSync): String = when (status) {
        PeerSync.Idle -> context.getString(R.string.sync_status_idle)
        PeerSync.Syncing -> context.getString(R.string.sync_status_syncing)
        is PeerSync.Synced -> context.getString(
            if (status.changed) R.string.sync_status_synced_changed else R.string.sync_status_synced,
            time(status.atMillis)
        )
        is PeerSync.Failed -> failure(context, status.failure)
    }

    fun lastSynced(context: Context, atMillis: Long?): String =
        context.getString(
            R.string.sync_last_synced,
            atMillis?.let(::time) ?: context.getString(R.string.sync_never)
        )

    private fun failure(context: Context, failure: SessionFailure): String = when (failure) {
        SessionFailure.NoAnswer -> context.getString(R.string.sync_failure_no_answer)
        SessionFailure.WrongPassphrase -> context.getString(R.string.sync_failure_wrong_passphrase)
        is SessionFailure.Incompatible -> context.getString(R.string.sync_failure_incompatible, failure.message)
        is SessionFailure.Refused -> context.getString(R.string.sync_failure_refused, failure.message)
        is SessionFailure.Invalid -> context.getString(R.string.sync_failure_invalid, failure.message)
        is SessionFailure.Network -> context.getString(R.string.sync_failure_network, failure.message)
    }

    private fun time(atMillis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(atMillis))
}
