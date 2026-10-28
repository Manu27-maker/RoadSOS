package com.roadsos.app.engine

import com.roadsos.app.data.Board
import com.roadsos.app.data.Incident
import com.roadsos.app.data.SimDriver
import java.util.Locale
import java.util.Random
import kotlin.math.roundToInt

/**
 * Geofencing for the "approaching driver" simulation.
 *
 * A driver is alerted when:
 *   1. haversine(driver, incident) <= radius, and
 *   2. the driver is actually heading toward the incident (bearing check +-65 deg).
 *
 * Drivers are generated deterministically from the incident id, so the same
 * incident always produces the same scene across reloads - important for a demo.
 */
object DriverSim {

    /** NH-44 northbound (Hosur -> Bengaluru), the direction traffic approaches from. */
    private const val CORRIDOR_BEARING = 35.0

    private val VEHICLE_KINDS = listOf(
        "car" to "KA-05-MJ-4821",
        "truck" to "KA-51-AB-2290",
        "bike" to "KA-03-HX-7712",
        "car" to "KA-01-AC-9087",
        "bus" to "KA-57-F-1104",
        "car" to "KA-53-MN-6633",
        "auto" to "KA-02-AD-3355",
        "truck" to "TN-22-CZ-8810",
    )

    private val SPEEDS = intArrayOf(48, 55, 62, 68, 74, 82, 90)

    fun simulatedDrivers(
        incident: Incident,
        blocked: Boolean,
        radiusKm: Double,
        count: Int = 7,
    ): List<SimDriver> {
        val lat = incident.lat ?: return emptyList()
        val lng = incident.lng ?: return emptyList()
        val severity = incident.finalSeverity ?: "High"
        val rng = Random((incident.id ?: "demo").hashCode().toLong())

        val drivers = mutableListOf<SimDriver>()
        for (i in 0 until count) {
            val distance = round(0.6 + rng.nextDouble() * (5.2 - 0.6), 2)
            val upstreamBearing = (CORRIDOR_BEARING + 180 + (rng.nextDouble() * 24 - 12) + 360) % 360
            var (driverLat, driverLng) = GeoMath.destination(lat, lng, upstreamBearing, distance)
            // small lateral offset so pins do not overlap on a map
            val (offsetLat, offsetLng) = GeoMath.destination(
                driverLat,
                driverLng,
                (upstreamBearing + 90) % 360,
                rng.nextDouble() * 0.12,
            )
            driverLat = offsetLat
            driverLng = offsetLng

            val heading = (CORRIDOR_BEARING + (rng.nextDouble() * 16 - 8) + 360) % 360
            val (kind, plate) = VEHICLE_KINDS[i % VEHICLE_KINDS.size]
            val speed = SPEEDS[rng.nextInt(SPEEDS.size)]
            val approaching = isApproaching(driverLat, driverLng, heading, lat, lng)
            val (title, body) = message(distance, severity, blocked)

            drivers.add(
                SimDriver(
                    id = "DRV-${i + 1}",
                    label = plate,
                    vehicleType = kind,
                    distanceKm = distance,
                    etaMin = round(distance / speed * 60, 1),
                    insideGeofence = distance <= radiusKm && approaching,
                    alertTitle = title,
                    alertBody = body,
                    speedKmh = speed,
                ),
            )
        }

        return drivers.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }

    fun inGeofence(drivers: List<SimDriver>): Int = drivers.count { it.insideGeofence == true }

    fun boardMessage(incident: Incident, blocked: Boolean, distanceKm: Double = 1.5): Board {
        val severity = incident.finalSeverity ?: "High"
        val distance = String.format(Locale.US, "%.1f", distanceKm)

        val (line1, line2, line3) = if (blocked) {
            Triple(
                "ROAD BLOCKED AHEAD",
                "ACCIDENT $distance KM - TAKE ALTERNATE ROUTE",
                "VIA ELECTRONIC CITY / HOSUR ROAD",
            )
        } else {
            Triple(
                "ACCIDENT $distance KM AHEAD",
                "SEVERITY: ${severity.uppercase(Locale.US)} - SLOW DOWN",
                "EMERGENCY VEHICLES MAY ENTER",
            )
        }

        return Board(
            line1 = line1,
            line2 = line2,
            line3 = line3,
            blocked = blocked,
            severity = severity,
            distanceKm = distanceKm,
            source = "VMS board at ${incident.highway ?: "NH-44"} - simulated on this phone",
        )
    }

    private fun isApproaching(
        driverLat: Double,
        driverLng: Double,
        heading: Double,
        incidentLat: Double,
        incidentLng: Double,
    ): Boolean {
        val toward = GeoMath.bearing(driverLat, driverLng, incidentLat, incidentLng)
        return GeoMath.bearingDelta(toward, heading) <= 65
    }

    private fun message(distanceKm: Double, severity: String, blocked: Boolean): Pair<String, String> {
        val distance = String.format(Locale.US, "%.1f", distanceKm)
        return when {
            distanceKm <= 1.5 && blocked -> (
                "ACCIDENT $distance KM AHEAD - ROAD BLOCKED" to
                    "Slow down now. Prepare to stop or take the nearest exit."
                )
            distanceKm <= 1.5 -> (
                "ACCIDENT $distance KM AHEAD - ${severity.uppercase(Locale.US)}" to
                    "Slow down now and move to the left lane."
                )
            blocked -> (
                "ACCIDENT $distance KM AHEAD - LANE BLOCKED" to
                    "Consider an alternate route to avoid standing traffic."
                )
            else -> (
                "ACCIDENT AHEAD IN $distance KM - ${severity.uppercase(Locale.US)}" to
                    "Reduce speed and stay alert. Emergency vehicles may enter the carriageway."
                )
        }
    }

    private fun round(value: Double, places: Int): Double {
        val factor = Math.pow(10.0, places.toDouble())
        return (value * factor).roundToInt() / factor
    }
}
