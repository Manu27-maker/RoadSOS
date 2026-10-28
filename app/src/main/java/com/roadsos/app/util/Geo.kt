package com.roadsos.app.util

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_KM = 6371.0

fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return EARTH_RADIUS_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun formatKm(km: Double?): String = when {
    km == null -> "-"
    km < 1.0 -> "${(km * 1000).toInt()} m"
    else -> String.format(Locale.US, "%.1f km", km)
}

fun formatCoord(value: Double?): String = value?.let { String.format(Locale.US, "%.5f", it) } ?: "-"
