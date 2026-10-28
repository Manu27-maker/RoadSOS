package com.roadsos.app.engine

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Spherical helpers shared by the geocoder, geofence and dispatch ranking. */
object GeoMath {

    private const val EARTH_RADIUS_KM = 6371.0088

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
    }

    /** Initial bearing from point 1 to point 2, degrees clockwise from north. */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Smallest absolute difference between two bearings, 0-180 degrees. */
    fun bearingDelta(a: Double, b: Double): Double = abs((a - b + 540.0) % 360.0 - 180.0)

    /** Moves [distanceKm] from a point along [bearingDeg]. */
    fun destination(lat: Double, lng: Double, bearingDeg: Double, distanceKm: Double): Pair<Double, Double> {
        val d = distanceKm / EARTH_RADIUS_KM
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lng)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(
            sin(b) * sin(d) * cos(p1),
            cos(d) - sin(p1) * sin(p2),
        )
        val outLat = Math.toDegrees(p2)
        val outLng = (Math.toDegrees(l2) + 540.0) % 360.0 - 180.0
        return round6(outLat) to round6(outLng)
    }

    private fun round6(value: Double): Double = Math.round(value * 1_000_000.0) / 1_000_000.0
}
