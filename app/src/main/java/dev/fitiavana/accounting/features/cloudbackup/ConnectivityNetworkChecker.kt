package dev.fitiavana.accounting.features.cloudbackup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Whether the active network reaches the internet. Only a quick pre-check to skip a run while offline;
 * a connection that turns out unusable still fails the upload, which is then counted and retried.
 */
class ConnectivityNetworkChecker(context: Context) : NetworkChecker {

    private val connectivity = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
