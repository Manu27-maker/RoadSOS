package com.roadsos.app.engine

import android.content.Context
import com.roadsos.app.data.Board
import com.roadsos.app.data.ContactPayload
import com.roadsos.app.data.CreateIncidentRequest
import com.roadsos.app.data.CreateIncidentResponse
import com.roadsos.app.data.Incident
import com.roadsos.app.data.IncidentDetailResponse
import com.roadsos.app.data.IncidentListResponse
import com.roadsos.app.data.NearbyDriversResponse
import com.roadsos.app.data.NotifyDelivery
import com.roadsos.app.data.NotifyResponse
import com.roadsos.app.data.NotifySummary
import com.roadsos.app.data.PushInfo
import com.roadsos.app.data.ReverseGeo
import com.roadsos.app.data.RoutePlan
import com.roadsos.app.data.SafeResponse
import com.roadsos.app.data.SuggestedUnit
import com.roadsos.app.data.TextResult
import com.roadsos.app.data.VisionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The whole RoadSOS pipeline, on the phone.
 *
 * Triage, fusion, storage, contact alerts, responder ranking, geofencing and
 * the highway board all run here. The only optional network call is
 * [RoutingEngine], reached only when the user turns on online routing; with
 * the toggle off (the default) the app never opens a socket.
 */
object LocalEngine {

    private const val AVG_SPEED_KMH = 38.0
    private const val CREATE_COOLDOWN_MS = 10_000L
    private const val ENGINE_VERSION = "1.0"
    private const val MAX_PENDING_VISION = 4
    private const val DEMO_STATUS_STAGE_SECONDS = 45L
    private const val MOCK_NOTE = "Simulated on this phone - no SMS, WhatsApp, email or push is sent."

    private val DEMO_STATUS_STAGES = listOf("Reported", "Dispatched", "On Scene", "Resolved")

    private val CLOCK = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() =
            java.text.SimpleDateFormat("dd MMM HH:mm", Locale.US)
    }

    private lateinit var db: LocalDb
    private var lastCreateAt = 0L

    /**
     * Vision result of the most recent photo, reused when the report is filed.
     * Bounded so abandoned wizard drafts cannot grow it forever.
     */
    private val pendingVision = object : LinkedHashMap<String, VisionResult>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VisionResult>?) =
            size > MAX_PENDING_VISION
    }

    fun init(context: Context) {
        // Construct only: the first db.state() (and the seed write) happens on
        // the IO dispatcher from the ViewModel, not on the first frame.
        if (!::db.isInitialized) {
            db = LocalDb(context.applicationContext)
        }
    }

    // ------------------------------------------------------------------- status
    fun stats(): DbStats = db.stats()

    fun engineVersion(): String = ENGINE_VERSION

    fun resetDemoData(): DbStats {
        pendingVision.clear()
        lastCreateAt = 0L
        db.resetToSeed()
        return db.stats()
    }

    // -------------------------------------------------------------------- geo
    fun reverseGeocode(lat: Double, lng: Double): ReverseGeo = OfflineGeocoder.reverse(lat, lng)

    /** Path of the re-encoded (EXIF-free) copy kept for a draft or a filed report. */
    fun photoPath(imageId: String): String? =
        db.photoFile(imageId).takeIf { it.exists() }?.absolutePath

    // ----------------------------------------------------------------- triage
    suspend fun analyzeText(description: String, sceneTags: Map<String, Boolean>): TextResult =
        withContext(Dispatchers.Default) {
            delay(180)
            NlpEngine.ruleScore(description, sceneTags)
        }

    suspend fun analyzePhoto(
        file: File,
        description: String,
        sceneTags: Map<String, Boolean>,
    ): Pair<String, VisionResult> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) throw IllegalArgumentException("The photo file is missing.")

            val bitmap = VisionEngine.decode(file)
                ?: throw IllegalArgumentException("Could not read that image. Try another photo.")

            val vision: VisionResult
            val imageId = UUID.randomUUID().toString().replace("-", "").take(16)
            try {
                vision = VisionEngine.analyze(bitmap, sceneTags)
                // Re-encoding into app storage drops EXIF/GPS before the photo is kept.
                VisionEngine.writeCleanJpeg(bitmap, db.photoFile(imageId))
            } finally {
                bitmap.recycle()
            }

            pendingVision[imageId] = vision
            imageId to vision
        }

    // ------------------------------------------------------------------ report
    suspend fun createIncident(request: CreateIncidentRequest): CreateIncidentResponse =
        withContext(Dispatchers.IO) {
            val state = db.state()

            // Idempotency: tapping "send" twice must not create two incidents.
            state.incidents.firstOrNull { it.clientId == request.clientId }?.let { existing ->
                return@withContext CreateIncidentResponse(
                    incident = withAge(existing.incident),
                    notifications = null,
                    fusion = fusionOf(existing.incident),
                    units = rankResponders(existing.incident),
                    duplicate = true,
                    duplicateWarning = true,
                )
            }

            val now = System.currentTimeMillis()
            val waitMs = CREATE_COOLDOWN_MS - (now - lastCreateAt)
            if (lastCreateAt > 0 && waitMs > 0) {
                throw IllegalStateException(
                    "You just filed a report. Please wait ${(waitMs / 1000) + 1}s before filing another.",
                )
            }
            lastCreateAt = now

            delay(260) // keeps the "Sending report..." state honest

            val tags = request.sceneTags.orEmpty()
            val description = request.description.orEmpty()
            val text = NlpEngine.ruleScore(description, tags)
            val vision = request.imageId?.let { pendingVision[it] }
            val fusion = SeverityFusion.fuse(text, vision)

            val imagePath = request.imageId?.let { id ->
                db.photoFile(id).takeIf { it.exists() }?.absolutePath
            }

            val incident = Incident(
                id = db.newIncidentId(),
                lat = request.lat,
                lng = request.lng,
                address = request.address,
                highway = request.highway,
                landmark = request.landmark,
                description = description.takeIf { it.isNotBlank() },
                finalScore = fusion.score,
                finalSeverity = fusion.severity,
                nlpScore = text.score,
                visionScore = vision?.score,
                fusionExplanation = fusion.explanation,
                injuries = maxOf(text.injuries ?: 0, if (vision != null) 1 else 0),
                hazards = (text.hazards.orEmpty() + visionHazards(vision)).distinct(),
                status = "Reported",
                createdAt = IsoTime.now(),
                ageMinutes = 0,
                shareLiveLocation = if (request.shareLiveLocation) 1 else 0,
            )

            state.incidents.add(
                0,
                StoredIncident(
                    incident = incident,
                    roadBlocked = vision?.roadBlocked == true || tags["road_blocked"] == true,
                    clientId = request.clientId,
                    deviceId = request.deviceId,
                    imagePath = imagePath,
                ),
            )
            db.persist()

            CreateIncidentResponse(
                incident = incident,
                notifications = alertContacts(request.contacts.orEmpty(), incident),
                fusion = fusion,
                units = rankResponders(incident),
                duplicate = false,
                duplicateWarning = false,
            )
        }

    suspend fun incident(id: String): IncidentDetailResponse = withContext(Dispatchers.IO) {
        advanceDemoStatuses()
        val stored = db.state().incidents.firstOrNull { it.incident.id == id }
            ?: throw IllegalStateException("That report is no longer on this device.")
        IncidentDetailResponse(
            incident = withAge(stored.incident),
            units = rankResponders(stored.incident),
        )
    }

    suspend fun incidents(activeOnly: Boolean = true): IncidentListResponse = withContext(Dispatchers.IO) {
        advanceDemoStatuses()
        val all = db.state().incidents
            .filter { !activeOnly || it.incident.status != "Resolved" }
            .map { withAge(it.incident) }
            .sortedByDescending { it.createdAt ?: "" }
        IncidentListResponse(incidents = all, count = all.size)
    }

    // --------------------------------------------------------------- lifecycle
    /**
     * Reports filed on this phone advance on a demo clock (45 s per stage) so
     * the lifecycle strip visibly progresses during a short pitch. The seeded
     * demo incidents keep their curated statuses.
     */
    private fun advanceDemoStatuses() {
        val state = db.state()
        var changed = false
        for (index in state.incidents.indices) {
            val stored = state.incidents[index]
            val clientId = stored.clientId ?: continue
            if (clientId.startsWith("seed-")) continue

            val stage = (IsoTime.ageSeconds(stored.incident.createdAt) / DEMO_STATUS_STAGE_SECONDS)
                .coerceIn(0L, DEMO_STATUS_STAGES.lastIndex.toLong())
                .toInt()
            val next = DEMO_STATUS_STAGES[stage]
            if (stored.incident.status != next) {
                state.incidents[index] = stored.copy(incident = stored.incident.copy(status = next))
                changed = true
            }
        }
        if (changed) db.persist()
    }

    // --------------------------------------------------------------- geofence
    suspend fun nearbyDrivers(
        incidentId: String,
        radiusKm: Double,
        notify: Boolean,
    ): NearbyDriversResponse = withContext(Dispatchers.Default) {
        val stored = db.state().incidents.firstOrNull { it.incident.id == incidentId }
            ?: throw IllegalStateException("That report is no longer on this device.")
        val incident = stored.incident
        val drivers = DriverSim.simulatedDrivers(incident, stored.roadBlocked, radiusKm)
        val inGeofence = DriverSim.inGeofence(drivers)
        val board: Board = DriverSim.boardMessage(incident, stored.roadBlocked)

        NearbyDriversResponse(
            incidentId = incidentId,
            radiusKm = radiusKm,
            drivers = drivers,
            inGeofence = inGeofence,
            board = board,
            push = if (notify) {
                PushInfo(
                    targeted = inGeofence,
                    deviceCount = inGeofence,
                    provider = "local-simulation",
                    topic = "roadsos_drivers",
                )
            } else {
                null
            },
            engine = "on-device-simulation",
        )
    }

    // ----------------------------------------------------------------- notify
    suspend fun notifySafe(
        contacts: List<ContactPayload>,
        incidentId: String,
        note: String?,
    ): SafeResponse = withContext(Dispatchers.IO) {
        val stored = db.state().incidents.firstOrNull { it.incident.id == incidentId }
            ?: throw IllegalStateException("File a report first.")
        if (contacts.isEmpty()) throw IllegalStateException("Add at least one emergency contact first.")

        val body = "RoadSOS: I'M SAFE. I reported a road incident near " +
            "${where(stored.incident)} at ${clock()}." +
            (note?.let { " $it" } ?: "") +
            " My location: ${mapsLink(stored.incident)}"

        val deliveries = deliveriesFor(contacts, body)
        SafeResponse(
            ok = true,
            message = "${deliveries.size}/${contacts.size} contacts told you are safe (mock delivery).",
            summary = NotifySummary(
                body = deliveries.firstOrNull()?.message,
                sent = deliveries.size,
                total = contacts.size,
                mocked = true,
                deliveries = deliveries,
                providerNote = MOCK_NOTE,
            ),
        )
    }

    suspend fun notifyContacts(
        contacts: List<ContactPayload>,
        kind: String?,
        lat: Double?,
        lng: Double?,
        severity: String?,
        landmark: String?,
        address: String?,
        note: String?,
    ): NotifyResponse = withContext(Dispatchers.IO) {
        if (contacts.isEmpty()) throw IllegalStateException("Add at least one emergency contact first.")

        val label = landmark ?: address ?: "the highway"
        val body = buildString {
            append("ROADSOS ${(severity ?: "High").uppercase(Locale.US)} ALERT: road accident near $label")
            append(" at ${clock()}.")
            note?.let { append(" $it") }
            if (lat != null && lng != null) append(" Location: ${mapsLink(lat, lng)}")
        }

        val deliveries = deliveriesFor(contacts, body)
        NotifyResponse(
            summary = NotifySummary(
                body = deliveries.firstOrNull()?.message,
                sent = deliveries.size,
                total = contacts.size,
                mocked = true,
                deliveries = deliveries,
                providerNote = MOCK_NOTE,
            ),
            kind = kind ?: "alert",
            incidentId = null,
        )
    }

    // ------------------------------------------------------------------ internals
    private fun alertContacts(
        contacts: List<ContactPayload>,
        incident: Incident,
    ): NotifySummary {
        val hazards = incident.hazards.orEmpty().take(3).joinToString(", ")
            .ifBlank { "scene being assessed" }
        val body = "ROADSOS ${(incident.finalSeverity ?: "High").uppercase(Locale.US)} ALERT: road accident " +
            "near ${where(incident)} at ${clock()}. Est. ${incident.injuries ?: 0} injured. " +
            "Hazards: $hazards. Live location: ${mapsLink(incident)}"

        val deliveries = deliveriesFor(contacts, body)
        return NotifySummary(
            body = deliveries.firstOrNull()?.message,
            sent = deliveries.size,
            total = contacts.size,
            mocked = true,
            deliveries = deliveries,
            providerNote = MOCK_NOTE,
        )
    }

    /**
     * One rendered message per contact, addressed to that person and labelled
     * with their chosen channel. This is a demo renderer: the delivery is
     * queued on the phone and nothing is actually sent.
     */
    private fun deliveriesFor(
        contacts: List<ContactPayload>,
        body: String,
    ): List<NotifyDelivery> = contacts.map { contact ->
        val channel = contact.channel.uppercase(Locale.US)
        NotifyDelivery(
            name = contact.name,
            target = contact.phone,
            channel = contact.channel,
            message = "To ${contact.name} via $channel: ${body.take(440)}",
            status = "Queued (mock)",
        )
    }

    // ------------------------------------------------------------------ routing
    /**
     * Route from the nearest available unit to the incident. Returns null when
     * the incident or its coordinates are missing; otherwise always returns a
     * plan, with [RoutePlan.status] explaining whether it is live or offline.
     */
    suspend fun routePlan(
        incidentId: String,
        online: Boolean,
        onlineAvailable: Boolean,
        apiKey: String,
    ): RoutePlan? =
        withContext(Dispatchers.IO) {
            val state = db.state()
            val incident = state.incidents
                .firstOrNull { it.incident.id == incidentId }
                ?.incident ?: return@withContext null
            val lat = incident.lat ?: return@withContext null
            val lng = incident.lng ?: return@withContext null
            val unit = state.responders
                .filter { it.status == "available" }
                .minByOrNull { GeoMath.haversineKm(it.lat, it.lng, lat, lng) }
                ?: return@withContext null
            RoutingEngine.route(unit.lat, unit.lng, lat, lng, online, onlineAvailable, apiKey)
        }

    private fun rankResponders(incident: Incident, limit: Int = 3): List<SuggestedUnit> {
        val lat = incident.lat ?: return emptyList()
        val lng = incident.lng ?: return emptyList()
        return db.state().responders
            .map { unit ->
                val km = GeoMath.haversineKm(lat, lng, unit.lat, unit.lng)
                SuggestedUnit(
                    id = unit.id,
                    name = unit.name,
                    type = unit.type,
                    status = unit.status,
                    distanceKm = round(km, 2),
                    etaMin = round(km / AVG_SPEED_KMH * 60 + 2.0, 1),
                )
            }
            .sortedWith(compareBy({ it.status != "available" }, { it.etaMin ?: Double.MAX_VALUE }))
            .take(limit)
    }

    private fun fusionOf(incident: Incident) = com.roadsos.app.data.Fusion(
        score = incident.finalScore,
        severity = incident.finalSeverity,
        explanation = incident.fusionExplanation,
    )

    private fun withAge(incident: Incident): Incident =
        incident.copy(ageMinutes = IsoTime.ageMinutes(incident.createdAt))

    private fun visionHazards(vision: VisionResult?): List<String> {
        if (vision == null) return emptyList()
        return buildList {
            if (vision.fireOrSmoke == true) add("Fire / smoke visible in photo")
            if (vision.overturned == true) add("Vehicle overturned")
            if (vision.roadBlocked == true) add("Carriageway blocked")
            if (vision.visibleInjuries == true) add("Casualties visible")
        }
    }

    private fun where(incident: Incident): String =
        incident.landmark ?: incident.address ?: "the highway"

    private fun mapsLink(incident: Incident): String =
        mapsLink(incident.lat ?: 0.0, incident.lng ?: 0.0)

    private fun mapsLink(lat: Double, lng: Double): String =
        String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", lat, lng)

    private fun clock(): String = CLOCK.get()!!.format(java.util.Date())

    private fun round(value: Double, places: Int): Double {
        val factor = Math.pow(10.0, places.toDouble())
        return (value * factor).roundToInt() / factor
    }
}
