package com.roadsos.app.data

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * Payloads exchanged with the on-device engine.
 *
 * They are marked [Immutable] so Compose can skip composables that receive them:
 * the engine always hands back fresh copies instead of mutating in place.
 */

@Immutable
data class ReverseGeo(
    val address: String? = null,
    val highway: String? = null,
    val landmark: String? = null,
    val source: String? = null,
)

@Immutable
data class TextResult(
    val score: Int? = null,
    val severity: String? = null,
    val confidence: Double? = null,
    val engine: String? = null,
    val hazards: List<String>? = null,
    @SerializedName("injuries_estimated") val injuries: Int? = null,
    @SerializedName("detected_keywords") val keywords: List<String>? = null,
    @SerializedName("ai_reason") val aiReason: String? = null,
    val rationale: String? = null,
)

@Immutable
data class VisionResult(
    val score: Int? = null,
    val severity: String? = null,
    val confidence: Double? = null,
    val engine: String? = null,
    @SerializedName("short_summary") val summary: String? = null,
    @SerializedName("fire_or_smoke") val fireOrSmoke: Boolean? = null,
    @SerializedName("road_blocked") val roadBlocked: Boolean? = null,
    @SerializedName("overturned_vehicle") val overturned: Boolean? = null,
    @SerializedName("visible_injuries") val visibleInjuries: Boolean? = null,
    @SerializedName("low_light") val lowLight: Boolean? = null,
    @SerializedName("vehicles_count") val vehiclesCount: Int? = null,
)

@Immutable
data class Fusion(
    val score: Int? = null,
    val severity: String? = null,
    val explanation: String? = null,
)

@Immutable
data class Incident(
    val id: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String? = null,
    val highway: String? = null,
    val landmark: String? = null,
    val description: String? = null,
    @SerializedName("final_score") val finalScore: Int? = null,
    @SerializedName("final_severity") val finalSeverity: String? = null,
    @SerializedName("nlp_score") val nlpScore: Int? = null,
    @SerializedName("vision_score") val visionScore: Int? = null,
    @SerializedName("fusion_explanation") val fusionExplanation: String? = null,
    @SerializedName("injuries_estimated") val injuries: Int? = null,
    val hazards: List<String>? = null,
    val status: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("age_minutes") val ageMinutes: Int? = null,
    @SerializedName("share_live_location") val shareLiveLocation: Int? = null,
)

@Immutable
data class NotifyDelivery(
    val name: String? = null,
    val target: String? = null,
    val channel: String? = null,
    val message: String? = null,
    val status: String? = null,
)

@Immutable
data class NotifySummary(
    val body: String? = null,
    val sent: Int? = null,
    val total: Int? = null,
    val mocked: Boolean? = null,
    val deliveries: List<NotifyDelivery>? = null,
    @SerializedName("provider_note") val providerNote: String? = null,
)

@Immutable
data class SuggestedUnit(
    val id: Int? = null,
    val name: String? = null,
    val type: String? = null,
    val status: String? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("eta_min") val etaMin: Double? = null,
)

@Immutable
data class IncidentListResponse(
    val incidents: List<Incident>? = null,
    val count: Int? = null,
)

@Immutable
data class IncidentDetailResponse(
    val incident: Incident? = null,
    @SerializedName("suggested_units") val units: List<SuggestedUnit>? = null,
)

@Immutable
data class Board(
    val line1: String? = null,
    val line2: String? = null,
    val line3: String? = null,
    val blocked: Boolean? = null,
    val severity: String? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    val source: String? = null,
)

@Immutable
data class SimDriver(
    val id: String? = null,
    val label: String? = null,
    @SerializedName("vehicle_type") val vehicleType: String? = null,
    @SerializedName("distance_km") val distanceKm: Double? = null,
    @SerializedName("eta_to_incident_min") val etaMin: Double? = null,
    @SerializedName("inside_geofence") val insideGeofence: Boolean? = null,
    @SerializedName("alert_title") val alertTitle: String? = null,
    @SerializedName("alert_body") val alertBody: String? = null,
    @SerializedName("speed_kmh") val speedKmh: Int? = null,
)

@Immutable
data class PushInfo(
    val targeted: Int? = null,
    @SerializedName("device_count") val deviceCount: Int? = null,
    val provider: String? = null,
    val topic: String? = null,
)

@Immutable
data class NearbyDriversResponse(
    @SerializedName("incident_id") val incidentId: String? = null,
    @SerializedName("radius_km") val radiusKm: Double? = null,
    val drivers: List<SimDriver>? = null,
    @SerializedName("in_geofence") val inGeofence: Int? = null,
    val board: Board? = null,
    val push: PushInfo? = null,
    val engine: String? = null,
)

@Immutable
data class CreateIncidentResponse(
    val incident: Incident? = null,
    val notifications: NotifySummary? = null,
    val fusion: Fusion? = null,
    @SerializedName("suggested_units") val units: List<SuggestedUnit>? = null,
    val duplicate: Boolean? = null,
    @SerializedName("duplicate_warning") val duplicateWarning: Boolean? = null,
)

@Immutable
data class NotifyResponse(
    val summary: NotifySummary? = null,
    val kind: String? = null,
    @SerializedName("incident_id") val incidentId: String? = null,
)

@Immutable
data class SafeResponse(
    val ok: Boolean? = null,
    val message: String? = null,
    val summary: NotifySummary? = null,
)

@Immutable
data class ContactPayload(
    val name: String,
    val phone: String,
    val channel: String,
    val relationship: String? = null,
)

@Immutable
data class CreateIncidentRequest(
    val lat: Double,
    val lng: Double,
    val description: String? = null,
    @SerializedName("scene_tags") val sceneTags: Map<String, Boolean>? = null,
    @SerializedName("image_id") val imageId: String? = null,
    val contacts: List<ContactPayload>? = null,
    val consent: Boolean = true,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("device_id") val deviceId: String,
    val accuracy: Double? = null,
    val address: String? = null,
    val landmark: String? = null,
    val highway: String? = null,
    @SerializedName("reporter_name") val reporterName: String? = null,
    @SerializedName("reporter_phone") val reporterPhone: String? = null,
    @SerializedName("share_live_location") val shareLiveLocation: Boolean = false,
    @SerializedName("location_source") val locationSource: String = "gps",
)
