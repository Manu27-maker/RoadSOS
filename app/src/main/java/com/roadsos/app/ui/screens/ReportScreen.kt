package com.roadsos.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.Locale
import com.roadsos.app.engine.VisionEngine
import com.roadsos.app.MainViewModel
import com.roadsos.app.ReportDraft
import com.roadsos.app.data.Incident
import com.roadsos.app.data.TextResult
import com.roadsos.app.data.VisionResult
import com.roadsos.app.data.errorMessage
import com.roadsos.app.dialEmergency
import com.roadsos.app.ui.PanelCard
import com.roadsos.app.ui.PrimaryAction
import com.roadsos.app.ui.ScoreRing
import com.roadsos.app.ui.SecondaryAction
import com.roadsos.app.ui.SectionLabel
import com.roadsos.app.ui.SeverityChip
import com.roadsos.app.ui.StatusChip
import com.roadsos.app.ui.severityColor
import com.roadsos.app.ui.statusColor
import com.roadsos.app.ui.statusRank
import com.roadsos.app.ui.theme.Amber
import com.roadsos.app.ui.theme.Cyan
import com.roadsos.app.ui.theme.Green
import com.roadsos.app.ui.theme.Panel
import com.roadsos.app.ui.theme.Red
import com.roadsos.app.ui.theme.Stroke
import com.roadsos.app.ui.theme.TextDim
import com.roadsos.app.ui.theme.TextMain
import com.roadsos.app.ui.theme.TextMuted
import com.roadsos.app.util.formatCoord
import com.roadsos.app.util.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SCENE_TAGS = listOf(
    "fire_or_smoke" to "Fire / smoke",
    "overturned_vehicle" to "Overturned",
    "road_blocked" to "Road blocked",
    "visible_injuries" to "Injuries",
    "multiple_vehicles" to "Multiple vehicles",
)

private val STEP_LABELS = listOf("Location", "Describe", "Photo", "Confirm")

@Composable
fun ReportScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val draft = vm.draft
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var locating by remember { mutableStateOf(false) }
    var analysingText by remember { mutableStateOf(false) }
    var analysingPhoto by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(draft.step) { scroll.scrollTo(0) }

    fun notify(message: String) {
        // Replace the current message instead of queueing a backlog of them.
        snackbar.currentSnackbarData?.dismiss()
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun analysePhoto(file: File) {
        // Drop a previous cache original when the user retakes or re-picks.
        draft.photoPath?.let(::File)
            ?.takeIf { it != file && it.parentFile == context.cacheDir }
            ?.delete()

        draft.photoPath = file.absolutePath
        draft.imageId = null
        draft.vision = null
        error = null
        scope.launch {
            analysingPhoto = true
            vm.analyzePhoto(file)
                .onSuccess {
                    // The draft now points at the re-encoded copy in filesDir;
                    // the cache original still carries EXIF/GPS, so delete it.
                    if (file.parentFile == context.cacheDir && file.absolutePath != draft.photoPath) {
                        file.delete()
                    }
                }
                .onFailure { error = errorMessage(it) }
            analysingPhoto = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingPhoto
        if (saved && file != null) analysePhoto(file)
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "scene-${System.currentTimeMillis()}.jpg")
        pendingPhoto = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        runCatching { takePicture.launch(uri) }
            .onFailure { notify("No camera app available - use the gallery instead.") }
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else notify("Camera permission denied - you can still add a photo from the gallery.")
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val file = copyToCache(context, uri)
        if (file != null) analysePhoto(file) else notify("Could not read that image.")
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                draft.description = (draft.description + " " + spoken).trim().take(2000)
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            scope.launch {
                locating = true
                error = null
                vm.locateWithGps().onFailure { error = errorMessage(it) }
                locating = false
            }
        } else {
            notify("Location permission denied - use the demo location or type details manually.")
        }
    }

    fun requestGps() {
        if (!hasLocationPermission(context)) {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            return
        }
        scope.launch {
            locating = true
            error = null
            vm.locateWithGps().onFailure { error = errorMessage(it) }
            locating = false
        }
    }

    fun useDemo() {
        scope.launch {
            locating = true
            error = null
            vm.useDemoLocation()
            locating = false
        }
    }

    // Deliberately no auto-fix here: the screen promises "GPS is captured only
    // when you tap", so the first fix always comes from an explicit tap.

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (draft.step in 0..3) {
            ProgressHeader(step = draft.step, onBack = {
                if (draft.step > 0) {
                    draft.step -= 1
                    error = null
                }
            })
            Spacer(Modifier.height(14.dp))
        }

        when (draft.step) {
            0 -> LocationStep(
                draft = draft,
                locating = locating,
                error = error,
                onRequestGps = ::requestGps,
                onDemo = ::useDemo,
                onContinue = { draft.step = 1 },
            )

            1 -> DescribeStep(
                draft = draft,
                busy = analysingText,
                error = error,
                onVoice = {
                    val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Describe the accident")
                    }
                    runCatching { speech.launch(intent) }
                        .onFailure { notify("No speech recogniser available - type instead.") }
                },
                onAnalyse = {
                    scope.launch {
                        analysingText = true
                        error = null
                        vm.analyzeText()
                            .onSuccess { draft.step = 2 }
                            .onFailure { error = errorMessage(it) }
                        analysingText = false
                    }
                },
            )

            2 -> PhotoStep(
                draft = draft,
                busy = analysingPhoto,
                error = error,
                onCamera = {
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        launchCamera()
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
                onGallery = { gallery.launch("image/*") },
                onRetry = { draft.photoPath?.let { path -> File(path).takeIf { it.exists() }?.let(::analysePhoto) } },
                onContinue = { draft.step = 3 },
            )

            3 -> ReviewStep(
                vm = vm,
                draft = draft,
                busy = submitting,
                error = error,
                onBack = { draft.step = 2 },
                onSubmit = { name, phone ->
                    scope.launch {
                        submitting = true
                        error = null
                        vm.submit(name, phone).onFailure { error = errorMessage(it) }
                        submitting = false
                    }
                },
            )

            else -> SuccessStep(
                vm = vm,
                draft = draft,
                snackbar = snackbar,
                onNewReport = {
                    vm.draft.clear()
                    error = null
                },
            )
        }
    }
}

private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
    val file = File(context.cacheDir, "scene-${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    if (file.length() > 0) file else null
}.getOrNull()

private fun openMaps(context: Context, lat: Double?, lng: Double?) {
    if (lat == null || lng == null) return
    val intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"),
    )
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ProgressHeader(step: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (step > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Panel,
                border = BorderStroke(1.dp, Stroke),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "STEP ${step + 1} OF 4",
                    color = Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    " · ${STEP_LABELS[step].uppercase()}",
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("${(step + 1) * 25}%", color = TextDim, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { index ->
                    val color by animateColorAsState(
                        targetValue = if (index <= step) Cyan else Cyan.copy(alpha = 0.12f),
                        animationSpec = tween(durationMillis = 300),
                        label = "stepSegment",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, body: String) {
    Text(title, color = TextMain, style = MaterialTheme.typography.headlineMedium)
    Text(body, color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun ErrorNote(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = Red.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Red.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = Red, modifier = Modifier.size(16.dp))
            Text(
                message,
                color = Color(0xFFFCA5A5),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LocationStep(
    draft: ReportDraft,
    locating: Boolean,
    error: String?,
    onRequestGps: () -> Unit,
    onDemo: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    StepHeading(
        "Where are you?",
        "GPS is captured only when you tap. If the fix fails, use the demo location instead.",
    )

    PanelCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (draft.lat != null) Cyan.copy(alpha = 0.12f) else Panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = if (draft.lat != null) Cyan else TextDim,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        if (draft.lat != null) "Location captured" else "No location yet",
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (draft.lat != null) "Ready to attach to the report" else "Waiting for a GPS fix",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                }
                if (draft.lat != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Cyan.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, Cyan.copy(alpha = 0.3f)),
                    ) {
                        Text(
                            if (draft.locationSource == "pin") "PIN" else "GPS",
                            color = Cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (draft.lat != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    draft.address ?: "Address lookup unavailable",
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (!draft.highway.isNullOrBlank() || !draft.landmark.isNullOrBlank()) {
                    Text(
                        listOfNotNull(draft.landmark, draft.highway).joinToString(" · "),
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${formatCoord(draft.lat)}, ${formatCoord(draft.lng)}",
                        color = TextDim,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    Text(
                        "±${draft.accuracy?.toInt() ?: "?"} m",
                        color = TextDim,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryAction(
                        text = if (locating) "Locating..." else "Refresh GPS",
                        onClick = onRequestGps,
                        enabled = !locating,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryAction(
                        text = "Demo location",
                        onClick = onDemo,
                        enabled = !locating,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    "We ask for location only when you tap “Use my GPS”. Nothing leaves this phone until you send the report.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryAction(
                    text = if (locating) "Getting GPS fix..." else "Use my GPS",
                    onClick = onRequestGps,
                    enabled = !locating,
                    color = Cyan,
                    loading = locating,
                    icon = Icons.Filled.Place,
                )
                Spacer(Modifier.height(8.dp))
                SecondaryAction(
                    text = "Use demo location (Silk Board)",
                    onClick = onDemo,
                    enabled = !locating,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (error != null) ErrorNote(error)

    Spacer(Modifier.height(14.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
        Text(
            "Location, photo and description are used only for this incident and shared with emergency responders.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 8.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    PrimaryAction(
        text = "Continue",
        onClick = onContinue,
        enabled = draft.lat != null && !locating,
        icon = Icons.AutoMirrored.Filled.ArrowForward,
    )
    if (draft.lat != null) {
        TextButton(
            onClick = {
                draft.lat?.let { lat -> draft.lng?.let { lng -> openMaps(context, lat, lng) } }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open this point in Maps", color = Cyan, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescribeStep(
    draft: ReportDraft,
    busy: Boolean,
    error: String?,
    onVoice: () -> Unit,
    onAnalyse: () -> Unit,
) {
    StepHeading(
        "What happened?",
        "Speak or type in any language. Mention fire, injuries, trapped people and vehicles.",
    )

    OutlinedTextField(
        value = draft.description,
        onValueChange = { draft.description = it.take(2000) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("e.g. Truck overturned near the flyover, smoke and one person trapped", color = TextDim) },
        minLines = 4,
        maxLines = 8,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Stroke,
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            cursorColor = Cyan,
        ),
    )
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${draft.description.length}/2000", color = TextDim, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        if (draft.description.isNotEmpty()) {
            TextButton(onClick = { draft.description = "" }) {
                Text("Clear", color = TextDim, fontSize = 11.sp)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryAction(
            text = "Tap to speak",
            onClick = onVoice,
            contentColor = Cyan,
            modifier = Modifier.weight(1f),
        )
        SecondaryAction(
            text = "Clear text",
            onClick = { draft.description = "" },
            enabled = draft.description.isNotEmpty(),
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(18.dp))
    SectionLabel("Tap what you can see")
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SCENE_TAGS.forEach { (key, label) ->
            val on = draft.tags[key] == true
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { draft.toggleTag(key) },
                shape = RoundedCornerShape(12.dp),
                color = if (on) Cyan.copy(alpha = 0.14f) else Panel,
                border = BorderStroke(1.dp, if (on) Cyan.copy(alpha = 0.5f) else Stroke),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) Cyan else TextDim),
                    )
                    Text(
                        label,
                        color = if (on) Cyan else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
            }
        }
    }
    Text(
        "These tags are trusted over the AI when you confirm them.",
        color = TextDim,
        fontSize = 10.sp,
        modifier = Modifier.padding(top = 6.dp),
    )

    draft.textResult?.let { result ->
        Spacer(Modifier.height(16.dp))
        SeverityPreview(result)
    }

    if (error != null) ErrorNote(error)

    Spacer(Modifier.height(18.dp))
    PrimaryAction(
        text = when {
            busy -> "Analysing severity..."
            draft.description.isBlank() && draft.tags.values.none { it } -> "Add a few words or a tag"
            else -> "Analyse & continue"
        },
        onClick = onAnalyse,
        enabled = !busy && (draft.description.isNotBlank() || draft.tags.values.any { it }),
        loading = busy,
        icon = Icons.Filled.Bolt,
    )
}

@Composable
private fun SeverityPreview(result: TextResult) {
    PanelCard(accent = severityColor(result.severity)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score = result.score ?: 0, severity = result.severity, size = 78.dp, strokeWidth = 7.dp)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("Live severity estimate", color = TextMain, style = MaterialTheme.typography.titleMedium)
                Text(
                    result.engine ?: "rules",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (result.injuries != null && result.injuries > 0) {
                    Text("~${result.injuries} injured", color = Amber, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (!result.hazards.isNullOrEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                result.hazards.take(4).joinToString(" · "),
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun PhotoStep(
    draft: ReportDraft,
    busy: Boolean,
    error: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    StepHeading(
        "Show us the scene",
        "A photo gives responders fire, smoke and blockage hints. Vehicle type, colour and exact count are not detected. You can skip this.",
    )

    val photo = draft.photoPath?.let { File(it) }?.takeIf { it.exists() }
    // Decode off the main thread: a 1600 px JPEG takes long enough to drop frames.
    val preview by produceState<android.graphics.Bitmap?>(initialValue = null, photo?.absolutePath) {
        value = photo?.let { file ->
            withContext(Dispatchers.IO) { VisionEngine.decode(file) }
        }
    }

    if (photo != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Panel),
        ) {
            preview?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Crash scene photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (draft.vision != null) {
                // A scrim behind the chip keeps it legible over a busy photo.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xE6070B12))
                        .padding(horizontal = 3.dp, vertical = 3.dp),
                ) {
                    SeverityChip(
                        severity = draft.vision?.severity,
                        score = draft.vision?.score,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Retake", onCamera, modifier = Modifier.weight(1f))
            SecondaryAction("Change photo", onGallery, modifier = Modifier.weight(1f))
        }

        if (busy) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                Text(
                    "Analysing photo on this device...",
                    color = Cyan,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        draft.vision?.let { vision -> VisionPreview(vision) }

        if (error != null) {
            ErrorNote(error)
            SecondaryAction(
                "Retry photo analysis",
                onRetry,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    } else {
        PanelCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TextDim, modifier = Modifier.size(34.dp))
                Text(
                    "No photo attached",
                    color = TextMain,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Night shots are fine. Do not step into traffic to take one.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryAction("Take photo", onCamera, color = Cyan, icon = Icons.Filled.PhotoCamera)
                Spacer(Modifier.height(8.dp))
                SecondaryAction("Choose from gallery", onGallery, modifier = Modifier.fillMaxWidth())
            }
        }
        if (error != null) ErrorNote(error)
    }

    Spacer(Modifier.height(16.dp))
    PrimaryAction(
        text = if (draft.imageId != null) "Continue" else "Continue without photo",
        onClick = onContinue,
        enabled = !busy,
        icon = Icons.AutoMirrored.Filled.ArrowForward,
    )
    Text(
        "Photos are re-encoded on this phone to strip EXIF/GPS metadata.",
        color = TextDim,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun VisionPreview(vision: VisionResult) {
    PanelCard(modifier = Modifier.padding(top = 12.dp), accent = severityColor(vision.severity)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(severity = vision.severity, score = vision.score)
                Spacer(Modifier.weight(1f))
                Text(
                    "confidence ${((vision.confidence ?: 0.0) * 100).toInt()}%",
                    color = TextDim,
                    fontSize = 10.sp,
                )
            }
            vision.summary?.let {
                Text(
                    it,
                    color = TextMain,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val flags = buildList {
                if (vision.fireOrSmoke == true) add("Fire / smoke")
                if (vision.roadBlocked == true) add("Road blocked")
                if (vision.overturned == true) add("Overturned")
                if (vision.visibleInjuries == true) add("Injuries")
                if (vision.lowLight == true) add("Low light")
            }
            if (flags.isNotEmpty()) {
                Text(
                    flags.joinToString(" · "),
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "Heuristic CV, not object detection: vehicle type, colour and exact count are not identified.",
                color = TextDim,
                fontSize = 9.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewStep(
    vm: MainViewModel,
    draft: ReportDraft,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(vm.reporterName) }
    var phone by remember { mutableStateOf(vm.reporterPhone) }

    val textScore = draft.textResult?.score ?: 0
    val visionScore = draft.vision?.score ?: 0
    val estimateScore = maxOf(textScore, visionScore)
    val estimateSeverity = if (visionScore > textScore) draft.vision?.severity else draft.textResult?.severity

    StepHeading(
        "Confirm & send",
        "Check the details. Responders see this instantly and your emergency contacts get a mock alert.",
    )

    PanelCard(accent = severityColor(estimateSeverity)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (estimateScore > 0) {
                ScoreRing(estimateScore, estimateSeverity)
            } else {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(Panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AI", color = TextDim, fontSize = 14.sp)
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text("On-device estimate", color = TextDim, fontSize = 10.sp)
                Text(
                    estimateSeverity ?: "Pending",
                    color = severityColor(estimateSeverity),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Text $textScore · Photo $visionScore — final fused score is computed on this phone.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow("Location", draft.address ?: "${formatCoord(draft.lat)}, ${formatCoord(draft.lng)}")
            SummaryRow("Description", draft.description.ifBlank { "No description" }, maxLines = 3)
            SummaryRow(
                "Scene tags",
                draft.tags.filterValues { it }.keys
                    .mapNotNull { key -> SCENE_TAGS.firstOrNull { it.first == key }?.second }
                    .joinToString(", ")
                    .ifBlank { "None" },
            )
            SummaryRow("Photo", if (draft.imageId != null) "Attached and analysed" else "No photo")
            SummaryRow(
                "Emergency contacts",
                if (vm.contacts.isEmpty()) "None - add one in Contacts"
                else "${vm.contacts.size} will be alerted (${vm.contacts.joinToString(", ") { it.name }})",
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Your details (optional)")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Callback phone", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { draft.consent = !draft.consent }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = draft.consent,
            onCheckedChange = { draft.consent = it },
            colors = CheckboxDefaults.colors(checkedColor = Red, uncheckedColor = TextDim),
        )
        Text(
            "I consent to sharing this report, my location and photo with emergency responders and my saved contacts.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 10.dp, end = 4.dp),
        )
    }

    if (error != null) ErrorNote(error)

    Spacer(Modifier.height(14.dp))
    PrimaryAction(
        text = if (busy) "Sending report..." else "Send report & alert contacts",
        onClick = { onSubmit(name, phone) },
        enabled = draft.consent && !busy,
        loading = busy,
        icon = Icons.AutoMirrored.Filled.Send,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryAction("Back", onBack, enabled = !busy, modifier = Modifier.weight(1f))
        SecondaryAction(
            "View on map",
            { openMaps(context, draft.lat, draft.lng) },
            enabled = draft.lat != null,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        "Delivery is mocked in this demo: messages are logged on this phone, not sent by SMS.",
        color = TextDim,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun SummaryRow(label: String, value: String, maxLines: Int = 2) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, color = TextDim, fontSize = 11.sp, modifier = Modifier.weight(0.36f))
        Text(
            value,
            color = TextMain,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = maxLines,
            modifier = Modifier.weight(0.64f),
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Stroke,
    focusedContainerColor = Panel,
    unfocusedContainerColor = Panel,
    focusedLabelColor = Cyan,
    cursorColor = Cyan,
)

private val LIFECYCLE = listOf("Reported", "Dispatched", "On Scene", "Resolved")

@Composable
private fun SuccessStep(
    vm: MainViewModel,
    draft: ReportDraft,
    snackbar: SnackbarHostState,
    onNewReport: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val incident: Incident? = draft.submitted
    var sendingSafe by remember { mutableStateOf(false) }

    LaunchedEffect(incident?.id) {
        while (incident?.id != null) {
            delay(10_000)
            vm.refreshSubmitted()
        }
    }

    if (incident == null) {
        Text("Report submitted.", color = TextMain)
        return
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Green.copy(alpha = 0.16f), Panel)))
            .border(1.dp, Green.copy(alpha = 0.26f), RoundedCornerShape(24.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Green.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text("Report filed", color = TextMain, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(7.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Stroke),
            ) {
                Text(
                    incident.id ?: "",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SeverityChip(severity = incident.finalSeverity, score = incident.finalScore)
                StatusChip(incident.status)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    PanelCard(accent = severityColor(incident.finalSeverity)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(incident.finalScore ?: 0, incident.finalSeverity, size = 78.dp, strokeWidth = 7.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    SectionLabel("Fused severity")
                    Text(
                        incident.landmark ?: incident.address ?: "Incident location",
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(
                        "Refreshes every 10 s · demo status steps every 45 s",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Lifecycle(status = incident.status)
            if (!incident.fusionExplanation.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    incident.fusionExplanation,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }

    if (!incident.hazards.isNullOrEmpty()) {
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Column {
                SectionLabel("Hazards at the scene")
                Text(
                    incident.hazards.joinToString(" · "),
                    color = TextMain,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    val notifications = draft.notifications
    if (notifications != null) {
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("Emergency contacts", color = TextMain, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${notifications.sent ?: 0}/${notifications.total ?: 0} alerted" +
                                if (notifications.mocked == true) " · mock delivery" else "",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
                notifications.deliveries.orEmpty().forEach { delivery ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            delivery.name ?: "Contact",
                            color = TextMain,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${delivery.channel?.uppercase(Locale.US) ?: "SMS"} · ${delivery.status ?: "Queued"}",
                            color = TextDim,
                            fontSize = 9.sp,
                        )
                    }
                }
                if (notifications.mocked == true) {
                    Text(
                        "No SMS, WhatsApp, email or push leaves this phone.",
                        color = TextDim,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }

    if (draft.units.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        PanelCard {
            Column {
                SectionLabel("Suggested units")
                draft.units.take(3).forEach { unit ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(unit.name ?: "Unit", color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${unit.distanceKm?.let { String.format(Locale.US, "%.1f km", it) } ?: ""} · ETA ${unit.etaMin?.toInt() ?: "?"} min",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
                Text(
                    "Ranked by straight-line distance; these ETAs use a flat 38 km/h + 2 min mobilisation.",
                    color = TextDim,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    draft.route?.let { route ->
        val trafficAware = route.engine == "google-directions" && route.trafficDurationMin != null
        val online = route.status == "live" || route.status == "osrm"
        val accent = when {
            trafficAware -> Cyan
            online -> Green
            else -> Amber
        }
        val badge = when {
            trafficAware -> "LIVE TRAFFIC"
            online -> "ROAD ROUTE"
            else -> "OFFLINE ESTIMATE"
        }
        Spacer(Modifier.height(12.dp))
        PanelCard(accent = accent) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Route to scene")
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = accent.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            badge,
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Text(
                    buildString {
                        append(route.distanceKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "-")
                        append(" · ETA ")
                        append(route.etaMin?.let { String.format(Locale.US, "%.0f", it) } ?: "?")
                        append(" min")
                    },
                    color = TextMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (route.trafficDurationMin != null) {
                    Text(
                        buildString {
                            append("Traffic-aware driving time ")
                            append(String.format(Locale.US, "%.0f", route.trafficDurationMin))
                            append(" min")
                            route.trafficDelayMin?.let {
                                append(" · traffic adds ")
                                append(String.format(Locale.US, "%.0f", it))
                                append(" min")
                            }
                        },
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                route.summary?.let {
                    Text("via $it", color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                }

                route.note?.let {
                    Text(
                        it,
                        color = TextDim,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                if (online) {
                    Text(
                        if (trafficAware) {
                            "Source: Google Directions, departure_time=now. Only coordinates were sent."
                        } else {
                            "Source: OSRM public demo server. Only coordinates were sent; no traffic data."
                        },
                        color = TextDim,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    PrimaryAction(
        text = if (sendingSafe) "Alerting contacts..." else "I'm safe — alert my contacts",
        onClick = {
            scope.launch {
                sendingSafe = true
                vm.sendSafe()
                    .onSuccess { snackbar.showSnackbar(it) }
                    .onFailure { snackbar.showSnackbar(errorMessage(it)) }
                sendingSafe = false
            }
        },
        enabled = !sendingSafe,
        color = Green,
        loading = sendingSafe,
        icon = Icons.Filled.Shield,
    )
    Spacer(Modifier.height(8.dp))
    SecondaryAction(
        "Call 112 — emergency",
        { dialEmergency(context) },
        contentColor = Red,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryAction(
            "Open in Maps",
            { openMaps(context, incident.lat, incident.lng) },
            modifier = Modifier.weight(1f),
        )
        SecondaryAction("New report", onNewReport, modifier = Modifier.weight(1f))
    }

    Text(
        "Keep this screen open for live status updates. Call 112 directly for life-threatening emergencies.",
        color = TextDim,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
}

@Composable
private fun Lifecycle(status: String?) {
    val rank = statusRank(status)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LIFECYCLE.forEachIndexed { index, label ->
            val reached = index <= rank
            val color = if (index == rank) statusColor(status) else if (reached) Green else Stroke
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
                Text(
                    label,
                    color = if (index == rank) TextMain else TextDim,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}
