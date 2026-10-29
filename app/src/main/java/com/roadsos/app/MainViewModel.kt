package com.roadsos.app

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadsos.app.data.Contact
import com.roadsos.app.data.CreateIncidentRequest
import com.roadsos.app.data.Fusion
import com.roadsos.app.data.Incident
import com.roadsos.app.data.NotifySummary
import com.roadsos.app.data.RoutePlan
import com.roadsos.app.data.Store
import com.roadsos.app.data.SuggestedUnit
import com.roadsos.app.data.TextResult
import com.roadsos.app.data.VisionResult
import com.roadsos.app.engine.DbStats
import com.roadsos.app.engine.LocalEngine
import com.roadsos.app.util.currentLocation
import com.roadsos.app.util.hasInternet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Wizard state. Every field is a snapshot state object, so Compose only
 * recomposes the reads that actually changed.
 */
@Stable
class ReportDraft {
    var step by mutableStateOf(0)
    var lat by mutableStateOf<Double?>(null)
    var lng by mutableStateOf<Double?>(null)
    var accuracy by mutableStateOf<Double?>(null)
    var address by mutableStateOf<String?>(null)
    var landmark by mutableStateOf<String?>(null)
    var highway by mutableStateOf<String?>(null)
    var locationSource by mutableStateOf("gps")

    var description by mutableStateOf("")
    var tags by mutableStateOf<Map<String, Boolean>>(emptyMap())
    var textResult by mutableStateOf<TextResult?>(null)

    var photoPath by mutableStateOf<String?>(null)
    var imageId by mutableStateOf<String?>(null)
    var vision by mutableStateOf<VisionResult?>(null)

    var consent by mutableStateOf(false)
    var clientId by mutableStateOf(UUID.randomUUID().toString())

    var submitted by mutableStateOf<Incident?>(null)
    var notifications by mutableStateOf<NotifySummary?>(null)
    var fusion by mutableStateOf<Fusion?>(null)
    var units by mutableStateOf<List<SuggestedUnit>>(emptyList())
    var route by mutableStateOf<RoutePlan?>(null)

    fun hasSignal(): Boolean = description.isNotBlank() || tags.values.any { it }

    fun toggleTag(key: String) {
        val next = tags.toMutableMap()
        next[key] = !(next[key] ?: false)
        tags = next
    }

    fun clear() {
        step = 0
        lat = null
        lng = null
        accuracy = null
        address = null
        landmark = null
        highway = null
        locationSource = "gps"
        description = ""
        tags = emptyMap()
        textResult = null
        photoPath = null
        imageId = null
        vision = null
        consent = false
        clientId = UUID.randomUUID().toString()
        submitted = null
        notifications = null
        fusion = null
        units = emptyList()
        route = null
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    val draft = ReportDraft()

    var stats by mutableStateOf<DbStats?>(null)
        private set

    var contacts by mutableStateOf(store.loadContacts())
        private set
    var reporterName by mutableStateOf(store.reporterName)
        private set
    var reporterPhone by mutableStateOf(store.reporterPhone)
        private set

    /** Off by default; the only switch that can allow a socket. */
    var onlineRouting by mutableStateOf(store.onlineRouting)
        private set

    val directionsKeyConfigured: Boolean get() = BuildConfig.DIRECTIONS_API_KEY.isNotBlank()

    val deviceId: String get() = store.deviceId
    val engineVersion: String get() = LocalEngine.engineVersion()

    init {
        LocalEngine.init(app)
        // The first read seeds and writes the JSON DB: warm it off the main
        // thread, and clear camera/gallery originals left by a previous run.
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                clearStaleSceneFiles()
                LocalEngine.stats()
            }
            stats = loaded
        }
    }

    private fun clearStaleSceneFiles() {
        getApplication<Application>().cacheDir
            .listFiles { file -> file.name.startsWith("scene-") && file.name.endsWith(".jpg") }
            ?.forEach { it.delete() }
    }

    // ------------------------------------------------------------------- engine
    fun refreshStatus() {
        stats = LocalEngine.stats()
    }

    fun resetDemoData() {
        draft.clear()
        stats = LocalEngine.resetDemoData()
    }

    /**
     * Persist the explicit opt-in. Turning it on after a report is filed
     * re-requests the route; turning it off immediately drops back to the
     * offline estimate.
     */
    fun setOnlineMode(enabled: Boolean) {
        onlineRouting = enabled
        store.onlineRouting = enabled
        if (draft.submitted != null) {
            viewModelScope.launch { refreshRoute() }
        }
    }

    suspend fun refreshRoute() {
        val id = draft.submitted?.id ?: return
        draft.route = runCatching {
            LocalEngine.routePlan(
                incidentId = id,
                online = onlineRouting,
                onlineAvailable = hasInternet(getApplication()),
                apiKey = BuildConfig.DIRECTIONS_API_KEY,
            )
        }.getOrNull()
    }

    // ---------------------------------------------------------------- contacts
    fun addContact(contact: Contact): String? {
        if (contacts.size >= Store.MAX_CONTACTS) return "You can save up to ${Store.MAX_CONTACTS} contacts."
        val updated = contacts + contact.copy(id = "LOCAL-" + UUID.randomUUID().toString().take(6).uppercase())
        contacts = updated
        store.saveContacts(updated)
        return null
    }

    fun updateContact(contact: Contact) {
        val updated = contacts.map { if (it.id == contact.id) contact else it }
        contacts = updated
        store.saveContacts(updated)
    }

    fun deleteContact(id: String) {
        val updated = contacts.filterNot { it.id == id }
        contacts = updated
        store.saveContacts(updated)
    }

    fun resetContacts() {
        contacts = store.resetContacts()
    }

    fun saveReporter(name: String, phone: String) {
        reporterName = name.trim()
        reporterPhone = phone.trim()
        store.reporterName = reporterName
        store.reporterPhone = reporterPhone
    }

    // ---------------------------------------------------------------- location
    suspend fun locateWithGps(): Result<Unit> {
        val location = currentLocation(getApplication())
            ?: return Result.failure(IllegalStateException("No GPS fix yet. Move near a window or use the demo location."))
        draft.lat = location.latitude
        draft.lng = location.longitude
        draft.accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else null
        draft.locationSource = "gps"
        draft.address = null
        draft.landmark = null
        draft.highway = null
        enrichAddress(location.latitude, location.longitude)
        return Result.success(Unit)
    }

    suspend fun useDemoLocation(): Result<Unit> {
        val lat = 12.9172
        val lng = 77.6234
        draft.lat = lat
        draft.lng = lng
        draft.accuracy = 20.0
        draft.locationSource = "pin"
        draft.address = "Silk Board Junction, NH-44 / ORR, Bengaluru"
        draft.landmark = "Silk Board Junction"
        draft.highway = "NH-44 / Outer Ring Road"
        enrichAddress(lat, lng)
        return Result.success(Unit)
    }

    private fun enrichAddress(lat: Double, lng: Double) {
        val geo = LocalEngine.reverseGeocode(lat, lng)
        if (geo.address.isNullOrBlank()) return
        draft.address = geo.address
        draft.landmark = geo.landmark
        draft.highway = geo.highway
    }

    // ------------------------------------------------------------------- triage
    suspend fun analyzeText(): Result<TextResult> {
        val description = draft.description.trim()
        if (description.isEmpty() && draft.tags.values.none { it }) {
            return Result.failure(IllegalStateException("Speak, type, or tap at least one scene tag."))
        }
        val activeTags = draft.tags.filterValues { it }
        val result = runCatching {
            LocalEngine.analyzeText(description, activeTags)
        }
        val text = result.getOrNull()
            ?: return Result.failure(result.exceptionOrNull() ?: IllegalStateException("No triage result."))
        draft.textResult = text
        return Result.success(text)
    }

    suspend fun analyzePhoto(file: File): Result<VisionResult> {
        if (!file.exists()) return Result.failure(IllegalStateException("The photo file is missing."))
        val activeTags = draft.tags.filterValues { it }
        val description = draft.description.trim()
        val result = runCatching { LocalEngine.analyzePhoto(file, description, activeTags) }
        val payload = result.getOrNull()
            ?: return Result.failure(result.exceptionOrNull() ?: IllegalStateException("The photo could not be analysed."))
        draft.imageId = payload.first
        draft.vision = payload.second
        // Show the sanitised copy from app storage, not the cache original.
        LocalEngine.photoPath(payload.first)?.let { draft.photoPath = it }
        return Result.success(payload.second)
    }

    // ------------------------------------------------------------------- submit
    suspend fun submit(name: String, phone: String): Result<Incident> {
        val lat = draft.lat ?: return Result.failure(IllegalStateException("Capture a location first."))
        val lng = draft.lng ?: return Result.failure(IllegalStateException("Capture a location first."))
        if (!draft.consent) return Result.failure(IllegalStateException("Consent is required to file a report."))
        if (!draft.hasSignal() && draft.imageId == null) {
            return Result.failure(IllegalStateException("Add a description, a tag, or a photo."))
        }
        saveReporter(name, phone)

        val payload = CreateIncidentRequest(
            lat = lat,
            lng = lng,
            description = draft.description.trim().takeIf { it.isNotEmpty() },
            sceneTags = draft.tags.filterValues { it }.takeIf { it.isNotEmpty() },
            imageId = draft.imageId,
            contacts = contacts.map { it.payload() }.takeIf { it.isNotEmpty() },
            consent = true,
            clientId = draft.clientId,
            deviceId = deviceId,
            accuracy = draft.accuracy,
            address = draft.address,
            landmark = draft.landmark,
            highway = draft.highway,
            reporterName = name.trim().takeIf { it.isNotEmpty() },
            reporterPhone = phone.trim().takeIf { it.isNotEmpty() },
            shareLiveLocation = false,
            locationSource = draft.locationSource,
        )

        val result = runCatching { LocalEngine.createIncident(payload) }
        val response = result.getOrNull()
        val incident = response?.incident
        if (incident == null) {
            return Result.failure(result.exceptionOrNull() ?: IllegalStateException("The report was not accepted."))
        }
        draft.submitted = incident
        draft.notifications = response.notifications
        draft.fusion = response.fusion
        draft.units = response.units.orEmpty()
        draft.route = null
        draft.step = 4
        refreshStatus()
        // Online routing can take a few seconds; never block the success screen.
        viewModelScope.launch { refreshRoute() }
        return Result.success(incident)
    }

    suspend fun refreshSubmitted() {
        val id = draft.submitted?.id ?: return
        val detail = runCatching { LocalEngine.incident(id) }.getOrNull() ?: return
        detail.incident?.let { draft.submitted = it }
        detail.units?.takeIf { it.isNotEmpty() }?.let { draft.units = it }
    }

    suspend fun sendSafe(): Result<String> {
        val id = draft.submitted?.id
            ?: return Result.failure(IllegalStateException("File a report first."))
        if (contacts.isEmpty()) {
            return Result.failure(IllegalStateException("Add at least one emergency contact first."))
        }
        val result = runCatching {
            LocalEngine.notifySafe(
                contacts = contacts.map { it.payload() },
                incidentId = id,
                note = "Reporter marked safe from the RoadSOS Android app.",
            )
        }
        val response = result.getOrNull()
            ?: return Result.failure(result.exceptionOrNull() ?: IllegalStateException("Could not send."))
        return Result.success(response.message ?: "Your contacts have been told you are safe.")
    }

    suspend fun sendTestAlert(): Result<NotifySummary> {
        if (contacts.isEmpty()) {
            return Result.failure(IllegalStateException("Add at least one emergency contact first."))
        }
        val result = runCatching {
            LocalEngine.notifyContacts(
                contacts = contacts.map { it.payload() },
                kind = "alert",
                lat = draft.lat ?: 12.9172,
                lng = draft.lng ?: 77.6234,
                severity = "Medium",
                landmark = draft.landmark ?: "Silk Board Junction",
                address = draft.address ?: "NH-44, Bengaluru",
                note = "Test alert from the RoadSOS Android app.",
            )
        }
        val summary = result.getOrNull()?.summary
            ?: return Result.failure(result.exceptionOrNull() ?: IllegalStateException("No delivery summary."))
        return Result.success(summary)
    }
}
