package com.roadsos.app.engine

import com.roadsos.app.data.ReverseGeo
import kotlin.math.roundToInt

/**
 * Reverse geocoding without a network.
 *
 * A curated NH-44 / Outer Ring Road landmark table gives responders a human
 * reference ("near Silk Board") instead of raw coordinates. Outside the table
 * the app degrades to a coordinate label, which is still perfectly usable.
 */
object OfflineGeocoder {

    private data class Landmark(
        val name: String,
        val lat: Double,
        val lng: Double,
        val highway: String,
        val city: String,
    )

    private val LANDMARKS = listOf(
        Landmark("Silk Board Junction", 12.9172, 77.6234, "NH-44 / Outer Ring Road", "Bengaluru"),
        Landmark("Electronic City Flyover", 12.8452, 77.6602, "NH-44", "Bengaluru"),
        Landmark("Bommanahalli Junction", 12.8980, 77.6250, "NH-44", "Bengaluru"),
        Landmark("HSR Layout Sector 2", 12.9121, 77.6446, "27th Main Road", "Bengaluru"),
        Landmark("Marathahalli Bridge", 12.9569, 77.7011, "Outer Ring Road", "Bengaluru"),
        Landmark("Bellandur Junction", 12.9250, 77.6760, "Outer Ring Road", "Bengaluru"),
        Landmark("Hebbal Flyover", 13.0358, 77.5971, "NH-44", "Bengaluru"),
        Landmark("Attibele Toll Plaza", 12.7784, 77.7705, "NH-44", "Bengaluru"),
        Landmark("BTM Layout 2nd Stage", 12.9166, 77.6101, "16th Main Road", "Bengaluru"),
        Landmark("Madivala (Madiwala) Lake", 12.9226, 77.6186, "Hosur Road", "Bengaluru"),
        Landmark("KR Puram Bridge", 13.0075, 77.6960, "Old Madras Road", "Bengaluru"),
        Landmark("Nayandahalli Junction", 12.9420, 77.5220, "Mysuru Road", "Bengaluru"),
    )

    private const val MAX_KM = 6.0

    fun reverse(lat: Double, lng: Double): ReverseGeo {
        var best: Landmark? = null
        var bestKm = Double.MAX_VALUE
        for (landmark in LANDMARKS) {
            val km = GeoMath.haversineKm(lat, lng, landmark.lat, landmark.lng)
            if (km < bestKm) {
                bestKm = km
                best = landmark
            }
        }

        val nearest = best
        if (nearest != null && bestKm <= MAX_KM) {
            val metres = (bestKm * 1000).roundToInt()
            return ReverseGeo(
                address = "Near ${nearest.name}, ${nearest.highway}, ${nearest.city}",
                highway = nearest.highway,
                landmark = nearest.name,
                source = "landmark-table ($metres m)",
            )
        }

        return ReverseGeo(
            address = String.format(java.util.Locale.US, "Unmapped location (%.5f, %.5f)", lat, lng),
            highway = "Unmapped corridor",
            landmark = null,
            source = "coordinate-fallback",
        )
    }
}
