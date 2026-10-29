package com.roadsos.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * True only when the active network reports both INTERNET and VALIDATED.
 * Used to choose the online route engine immediately and to fall back to the
 * offline estimate without waiting for a timeout. Requires ACCESS_NETWORK_STATE
 * (a normal, non-runtime permission).
 */
fun hasInternet(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
