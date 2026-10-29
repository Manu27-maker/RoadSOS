package com.roadsos.app.engine

import com.google.gson.JsonParser
import com.roadsos.app.data.RoutePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Optional online routing.
 *
 * Offline (default): the route is a straight-line distance at 38 km/h plus
 * 2 min mobilisation. The app never opens a socket.
 *
 * Online (explicit toggle): asks Google Directions for a driving route with
 * `departure_time=now` and `traffic_model=best_guess`. Google returns
 * `duration_in_traffic` when it has traffic data; that value is used for the
 * ETA and the free-flow duration is kept separately. Every failure (no key,
 * no network, quota, non-OK status) falls back to the offline estimate and
 * says so — no route, ETA or delay is ever invented.
 *
 * The key is injected at build time from the untracked `local.properties`
 * (`GOOGLE_DIRECTIONS_API_KEY`) or the environment, never committed.
 */
object RoutingEngine {

    const val ENGINE_GOOGLE = "google-directions"
    const val ENGINE_OSRM = "osrm"
    const val ENGINE_OFFLINE = "offline-estimate"

    const val STATUS_LIVE = "live"
    const val STATUS_OSRM = "osrm"
    const val STATUS_FALLBACK = "fallback"
    const val STATUS_OFF = "off"
    const val STATUS_NO_KEY = "no-key"
    const val STATUS_NO_NETWORK = "no-network"
    const val STATUS_UNREACHABLE = "unreachable"

    private const val AVG_SPEED_KMH = 38.0
    private const val MOBILISATION_MIN = 2.0
    private const val TIMEOUT_MS = 5_000

    /** Pure offline estimate, also used by the JVM tests. */
    fun offline(distanceKm: Double, status: String, note: String): RoutePlan {
        val driveMin = distanceKm / AVG_SPEED_KMH * 60.0
        return RoutePlan(
            engine = ENGINE_OFFLINE,
            status = status,
            distanceKm = round(distanceKm, 2),
            durationMin = round(driveMin, 1),
            trafficDurationMin = null,
            etaMin = round(driveMin + MOBILISATION_MIN, 1),
            trafficDelayMin = null,
            summary = "Straight-line estimate",
            note = note,
        )
    }

    /**
     * Decide which engine to use and never throw: the caller always gets a
     * usable plan, with `status` explaining any fallback.
     */
    suspend fun route(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        onlineEnabled: Boolean,
        onlineAvailable: Boolean = true,
        apiKey: String,
    ): RoutePlan = withContext(Dispatchers.IO) {
        val km = GeoMath.haversineKm(originLat, originLng, destLat, destLng)

        if (!onlineEnabled) {
            return@withContext offline(
                km,
                STATUS_OFF,
                "Online routing is forced off in Setup. Straight-line distance at 38 km/h + 2 min mobilisation.",
            )
        }
        if (!onlineAvailable) {
            return@withContext offline(
                km,
                STATUS_NO_NETWORK,
                "No internet connection right now. Using the straight-line estimate; " +
                    "live routing resumes automatically when the phone is back online.",
            )
        }

        // Google Directions is preferred because it can return a traffic-aware
        // duration. Without a key (or if Google fails) try OSRM's public demo
        // server: real road geometry and driving time, but no traffic data.
        if (apiKey.isNotBlank()) {
            return@withContext runCatching {
                fetchGoogleDirections(originLat, originLng, destLat, destLng, apiKey)
            }.getOrElse { googleError ->
                runCatching {
                    fetchOsrm(originLat, originLng, destLat, destLng)
                }.getOrElse { osrmError ->
                    offline(
                        km,
                        STATUS_UNREACHABLE,
                        "Google Directions failed (${googleError.message ?: googleError.javaClass.simpleName}) " +
                            "and OSRM failed (${osrmError.message ?: osrmError.javaClass.simpleName}). " +
                            "Falling back to the straight-line estimate.",
                    )
                }
            }
        }

        runCatching {
            fetchOsrm(originLat, originLng, destLat, destLng)
        }.getOrElse { error ->
            offline(
                km,
                STATUS_NO_KEY,
                "No Directions API key and OSRM is unreachable " +
                    "(${error.message ?: error.javaClass.simpleName}). " +
                    "Falling back to the straight-line estimate.",
            )
        }
    }

    private fun fetchGoogleDirections(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String,
    ): RoutePlan {
        val origin = coords(originLat, originLng)
        val destination = coords(destLat, destLng)
        val url = URL(
            "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=$origin&destination=$destination" +
                "&mode=driving&departure_time=now&traffic_model=best_guess" +
                "&language=en&key=$apiKey",
        )

        return parseDirections(readBody(url))
            ?: throw IOException("Directions returned no usable route")
    }

    private fun fetchOsrm(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): RoutePlan {
        // OSRM takes longitude,latitude; the public demo server needs no key.
        val origin = lngLat(originLng, originLat)
        val destination = lngLat(destLng, destLat)
        val url = URL(
            "https://router.project-osrm.org/route/v1/driving/$origin;$destination" +
                "?overview=false&alternatives=false",
        )

        return parseOsrm(readBody(url))
            ?: throw IOException("OSRM returned no usable route")
    }

    private fun readBody(url: URL): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code")
            return body
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parse a Directions JSON response. Kept pure so it can be tested without
     * a network or an API key. Returns null for non-OK statuses and malformed
     * bodies so the caller falls back instead of showing made-up numbers.
     */
    fun parseDirections(json: String, mobilisationMin: Double = MOBILISATION_MIN): RoutePlan? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (root.get("status")?.asString != "OK") return null

        val route = root.getAsJsonArray("routes")?.firstOrNull()?.asJsonObject ?: return null
        val leg = route.getAsJsonArray("legs")?.firstOrNull()?.asJsonObject ?: return null

        val distanceM = leg.getAsJsonObject("distance")?.get("value")?.asDouble ?: return null
        val durationS = leg.getAsJsonObject("duration")?.get("value")?.asDouble ?: return null
        val trafficS = leg.getAsJsonObject("duration_in_traffic")?.get("value")?.asDouble

        val durationMin = durationS / 60.0
        val trafficMin = trafficS?.div(60.0)
        val usedMin = trafficMin ?: durationMin

        return RoutePlan(
            engine = ENGINE_GOOGLE,
            status = STATUS_LIVE,
            distanceKm = round(distanceM / 1000.0, 2),
            durationMin = round(durationMin, 1),
            trafficDurationMin = trafficMin?.let { round(it, 1) },
            etaMin = round(usedMin + mobilisationMin, 1),
            trafficDelayMin = trafficMin
                ?.let { round((it - durationMin).coerceAtLeast(0.0), 1) }
                ?.takeIf { it > 0.0 },
            summary = route.get("summary")?.takeIf { !it.isJsonNull }?.asString,
            note = if (trafficMin != null) {
                "Google Directions · departure_time=now · traffic-aware ETA (+${format(mobilisationMin)} min " +
                    "mobilisation)."
            } else {
                "Google Directions · departure_time=now · no traffic duration returned, so free-flow " +
                    "duration is shown (+${format(mobilisationMin)} min mobilisation)."
            },
        )
    }

    /**
     * Parse an OSRM route response. OSRM is the keyless online fallback: it
     * returns real road geometry and driving time but **no traffic data**, so
     * the card must not claim a traffic-aware ETA for this engine.
     */
    fun parseOsrm(json: String, mobilisationMin: Double = MOBILISATION_MIN): RoutePlan? {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        if (root.get("code")?.asString != "Ok") return null

        val route = root.getAsJsonArray("routes")?.firstOrNull()?.asJsonObject ?: return null
        val distanceM = route.get("distance")?.asDouble ?: return null
        val durationS = route.get("duration")?.asDouble ?: return null
        val durationMin = durationS / 60.0

        return RoutePlan(
            engine = ENGINE_OSRM,
            status = STATUS_OSRM,
            distanceKm = round(distanceM / 1000.0, 2),
            durationMin = round(durationMin, 1),
            trafficDurationMin = null,
            etaMin = round(durationMin + mobilisationMin, 1),
            trafficDelayMin = null,
            summary = "OSRM road route",
            note = "OSRM public demo server · real road geometry and driving time, but no live " +
                "traffic data (+${format(mobilisationMin)} min mobilisation).",
        )
    }

    private fun coords(lat: Double, lng: Double): String =
        String.format(Locale.US, "%.6f,%.6f", lat, lng)

    private fun lngLat(lng: Double, lat: Double): String =
        String.format(Locale.US, "%.6f,%.6f", lng, lat)

    private fun round(value: Double, places: Int): Double {
        val factor = Math.pow(10.0, places.toDouble())
        return (value * factor).roundToInt() / factor
    }

    private fun format(value: Double): String =
        if (value == value.toInt().toDouble()) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
}
