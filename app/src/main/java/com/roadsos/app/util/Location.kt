package com.roadsos.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

/**
 * One-shot fix with a 9 s budget, using the platform [LocationManager] only.
 *
 * No Play Services, no network calls - the app stays installable on de-Googled
 * phones. Falls back to the newest cached fix when the user is indoors.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    val provider = bestProvider(manager) ?: return lastKnownLocation(context)

    val fresh = withTimeoutOrNull(FIX_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                @Deprecated("Required by the platform below API 30")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) {
                    manager.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }
            }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } else {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { manager.removeUpdates(listener) }
                }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    return fresh ?: lastKnownLocation(context)
}

@SuppressLint("MissingPermission")
fun lastKnownLocation(context: Context): Location? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
    return providers
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
        .maxByOrNull { it.time }
}

@SuppressLint("MissingPermission")
private fun bestProvider(manager: LocationManager): String? {
    val enabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    if (enabled) return LocationManager.GPS_PROVIDER
    val network = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    if (network) return LocationManager.NETWORK_PROVIDER
    return manager.getProviders(true).firstOrNull()
}

private const val FIX_TIMEOUT_MS = 9_000L
