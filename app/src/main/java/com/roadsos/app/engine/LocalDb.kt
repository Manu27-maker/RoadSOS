package com.roadsos.app.engine

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.roadsos.app.data.Incident
import com.roadsos.app.data.VisionResult
import java.io.File
import java.util.UUID

private const val TAG = "RoadSOS"

/** A responder unit kept on the device (no dispatch server involved). */
data class Responder(
    val id: Int,
    val name: String,
    val type: String,
    val phone: String,
    val base: String,
    val lat: Double,
    val lng: Double,
    val status: String,
)

/** Incident plus the few extra facts the local simulation needs. */
data class StoredIncident(
    val incident: Incident,
    val roadBlocked: Boolean = false,
    val clientId: String? = null,
    val deviceId: String? = null,
    val imagePath: String? = null,
)

class DbState(
    var incidents: MutableList<StoredIncident> = mutableListOf(),
    var responders: MutableList<Responder> = mutableListOf(),
)

@Immutable
data class DbStats(
    val incidents: Int,
    val activeIncidents: Int,
    val photos: Int,
    val storageKb: Long,
)

/**
 * Tiny on-device database: one JSON document in app-private storage plus a
 * folder of re-encoded photos. No server, no account, no sync.
 */
class LocalDb(context: Context) {

    private val gson = Gson()
    private val root = File(context.filesDir, "roadsos")
    private val dbFile = File(root, "roadsos.json")
    private val tmpFile = File(root, "roadsos.json.tmp")
    private val photoDir = File(root, "photos")

    private var cached: DbState? = null

    @Synchronized
    fun state(): DbState {
        cached?.let { return it }
        // Only write when there was nothing to read: a plain read must not
        // rewrite the database on every cold start.
        val loaded = read() ?: seed().also(::write)
        cached = loaded
        return loaded
    }

    @Synchronized
    fun persist() {
        cached?.let { write(it) }
    }

    @Synchronized
    fun resetToSeed(): DbState {
        cached = null
        photoDir.listFiles()?.forEach { it.delete() }
        tmpFile.delete()
        val seeded = seed()
        cached = seeded
        write(seeded)
        return seeded
    }

    fun photoFile(imageId: String): File = File(photoDir, "$imageId.jpg")

    fun stats(): DbStats {
        val state = state()
        val photos = photoDir.listFiles().orEmpty().filter { it.isFile }
        val bytes = (dbFile.takeIf { it.exists() }?.length() ?: 0L) + photos.sumOf { it.length() }
        return DbStats(
            incidents = state.incidents.size,
            activeIncidents = state.incidents.count { it.incident.status != "Resolved" },
            photos = photos.size,
            storageKb = (bytes / 1024).coerceAtLeast(0),
        )
    }

    // ------------------------------------------------------------------ storage
    private fun read(): DbState? {
        // Prefer the newest copy: a failed rename can leave the newest data in
        // the temp file, and a half-written temp file must not lose the DB.
        val candidates = listOf(dbFile, tmpFile)
            .filter { it.exists() && it.length() > 0 }
            .sortedByDescending { it.lastModified() }

        for (file in candidates) {
            val parsed = runCatching {
                gson.fromJson(file.readText(), DbState::class.java)
            }.onFailure {
                Log.w(TAG, "Unreadable local DB (${file.name}); trying the next copy", it)
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun write(state: DbState) {
        runCatching {
            root.mkdirs()
            // Write-then-rename: a process kill mid-write must not leave a
            // truncated JSON document behind.
            tmpFile.writeText(gson.toJson(state))
            if (!tmpFile.renameTo(dbFile)) {
                dbFile.delete()
                check(tmpFile.renameTo(dbFile)) { "Could not replace ${dbFile.name}" }
            }
        }.onFailure {
            Log.e(TAG, "Could not persist the local DB", it)
        }
    }

    // --------------------------------------------------------------------- seed
    private fun seed(): DbState {
        val state = DbState()

        state.responders.addAll(
            listOf(
                Responder(1, "A-108 Bengaluru South", "ambulance", "+91 80 1080 1080", "Silk Board sub-station", 12.9080, 77.6100, "busy"),
                Responder(2, "PCR-44 HSR Layout", "police", "+91 80 1000 0044", "HSR Layout police outpost", 12.9121, 77.6446, "available"),
                Responder(3, "Bommanahalli Fire Station", "fire", "+91 80 1010 1010", "Bommanahalli fire station", 12.8980, 77.6250, "available"),
            ),
        )

        val crashDescription =
            "Truck has overturned near Electronic City flyover. Fuel is leaking and there is " +
                "smoke coming from the cab. Two people are trapped, one is bleeding. Road is blocked " +
                "and traffic is at a full stop. It is night and visibility is poor."
        val crashTags = mapOf(
            "fire_or_smoke" to true,
            "overturned_vehicle" to true,
            "road_blocked" to true,
        )
        val crashText = NlpEngine.ruleScore(crashDescription, crashTags)
        val crashVision = VisionResult(
            score = 85,
            severity = "Critical",
            confidence = 0.71,
            engine = "demo-sample",
            summary = "Truck on its side with fire and smoke; second vehicle impacted; carriageway partly blocked.",
            fireOrSmoke = true,
            roadBlocked = true,
            overturned = true,
            visibleInjuries = false,
            lowLight = true,
            vehiclesCount = 0,
        )
        val crashFusion = SeverityFusion.fuse(crashText, crashVision)

        state.incidents.add(
            StoredIncident(
                incident = Incident(
                    id = "INC-DEMO01",
                    lat = 12.8452,
                    lng = 77.6602,
                    address = "Near Electronic City Flyover, NH-44, Bengaluru",
                    highway = "NH-44",
                    landmark = "Electronic City Flyover",
                    description = crashDescription,
                    finalScore = crashFusion.score,
                    finalSeverity = crashFusion.severity,
                    nlpScore = crashText.score,
                    visionScore = crashVision.score,
                    fusionExplanation = crashFusion.explanation,
                    injuries = maxOf(crashText.injuries ?: 0, 3),
                    hazards = (crashText.hazards.orEmpty() + "Fire / smoke visible in photo").distinct(),
                    status = "Dispatched",
                    createdAt = IsoTime.minutesAgo(12),
                    ageMinutes = 12,
                    shareLiveLocation = 0,
                ),
                roadBlocked = true,
                clientId = "seed-client-1",
                deviceId = "seed-device-1",
            ),
        )

        val pileupDescription =
            "Four car pile-up at Silk Board signal. There is smoke from one engine and one " +
                "person is unconscious. The road is blocked, traffic is not moving."
        val pileupTags = mapOf(
            "fire_or_smoke" to true,
            "road_blocked" to true,
            "multiple_vehicles" to true,
        )
        val pileupText = NlpEngine.ruleScore(pileupDescription, pileupTags)
        val pileupVision = VisionResult(
            score = 65,
            severity = "High",
            confidence = 0.66,
            engine = "demo-sample",
            summary = "Four vehicles clustered across two lanes; smoke from one engine bay.",
            fireOrSmoke = true,
            roadBlocked = true,
            overturned = false,
            visibleInjuries = false,
            lowLight = false,
            vehiclesCount = 0,
        )
        val pileupFusion = SeverityFusion.fuse(pileupText, pileupVision)

        state.incidents.add(
            StoredIncident(
                incident = Incident(
                    id = "INC-DEMO02",
                    lat = 12.9172,
                    lng = 77.6234,
                    address = "Near Silk Board Junction, NH-44 / Outer Ring Road, Bengaluru",
                    highway = "NH-44 / Outer Ring Road",
                    landmark = "Silk Board Junction",
                    description = pileupDescription,
                    finalScore = pileupFusion.score,
                    finalSeverity = pileupFusion.severity,
                    nlpScore = pileupText.score,
                    visionScore = pileupVision.score,
                    fusionExplanation = pileupFusion.explanation,
                    injuries = maxOf(pileupText.injuries ?: 0, 4),
                    hazards = (pileupText.hazards.orEmpty() + "Multi-vehicle scene").distinct(),
                    status = "Reported",
                    createdAt = IsoTime.minutesAgo(26),
                    ageMinutes = 26,
                    shareLiveLocation = 0,
                ),
                roadBlocked = true,
                clientId = "seed-client-2",
                deviceId = "seed-device-2",
            ),
        )

        val skidDescription =
            "Two-wheeler skidded near Bommanahalli. The rider has minor scratches and the bike " +
                "is on the shoulder. Traffic is moving slowly."
        val skidText = NlpEngine.ruleScore(skidDescription)
        val skidFusion = SeverityFusion.fuse(skidText, null)

        state.incidents.add(
            StoredIncident(
                incident = Incident(
                    id = "INC-DEMO03",
                    lat = 12.8980,
                    lng = 77.6250,
                    address = "Near Bommanahalli Junction, NH-44, Bengaluru",
                    highway = "NH-44",
                    landmark = "Bommanahalli Junction",
                    description = skidDescription,
                    finalScore = skidFusion.score,
                    finalSeverity = skidFusion.severity,
                    nlpScore = skidText.score,
                    visionScore = 0,
                    fusionExplanation = skidFusion.explanation,
                    injuries = skidText.injuries ?: 0,
                    hazards = skidText.hazards.orEmpty(),
                    status = "Resolved",
                    createdAt = IsoTime.minutesAgo(47),
                    ageMinutes = 47,
                    shareLiveLocation = 0,
                ),
                roadBlocked = false,
                clientId = "seed-client-3",
                deviceId = "seed-device-3",
            ),
        )

        return state
    }

    fun newIncidentId(): String = "INC-" + UUID.randomUUID().toString().take(6).uppercase()
}

/** ISO-8601 UTC timestamps without pulling in java.time formatters everywhere. */
object IsoTime {

    private const val PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    // SimpleDateFormat is not thread-safe, so each thread keeps its own instance.
    private val FORMATTER = object : ThreadLocal<java.text.SimpleDateFormat>() {
        override fun initialValue() = java.text.SimpleDateFormat(PATTERN, java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
    }

    fun now(): String = format(System.currentTimeMillis())

    fun minutesAgo(minutes: Int): String = format(System.currentTimeMillis() - minutes * 60_000L)

    private fun format(millis: Long): String = FORMATTER.get()!!.format(java.util.Date(millis))

    fun ageMinutes(createdAt: String?): Int {
        if (createdAt.isNullOrBlank()) return 0
        return runCatching {
            val then = FORMATTER.get()!!.parse(createdAt)?.time ?: return 0
            ((System.currentTimeMillis() - then) / 60_000L).toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    }

    fun ageSeconds(createdAt: String?): Long {
        if (createdAt.isNullOrBlank()) return 0L
        return runCatching {
            val then = FORMATTER.get()!!.parse(createdAt)?.time ?: return 0L
            ((System.currentTimeMillis() - then) / 1_000L).coerceAtLeast(0L)
        }.getOrDefault(0L)
    }
}
