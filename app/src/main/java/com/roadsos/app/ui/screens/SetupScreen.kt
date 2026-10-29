package com.roadsos.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.roadsos.app.MainViewModel
import com.roadsos.app.data.Store
import com.roadsos.app.ui.PanelCard
import com.roadsos.app.ui.SecondaryAction
import com.roadsos.app.ui.SectionLabel

import com.roadsos.app.ui.theme.Amber
import com.roadsos.app.ui.theme.Cyan
import com.roadsos.app.ui.theme.Green
import com.roadsos.app.ui.theme.Panel
import com.roadsos.app.ui.theme.PanelRaised
import com.roadsos.app.ui.theme.Red
import com.roadsos.app.ui.theme.Stroke
import com.roadsos.app.ui.theme.TextDim
import com.roadsos.app.ui.theme.TextMain
import com.roadsos.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PermissionInfo(
    val label: String,
    val rationale: String,
    val permissions: Array<String>,
    val key: String,
)

private val PERMISSIONS = listOf(
    PermissionInfo(
        label = "Location",
        rationale = "Attaches GPS coordinates to a report. Only read when you tap.",
        permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        key = "location",
    ),
    PermissionInfo(
        label = "Camera",
        rationale = "Lets responders see the scene before they arrive.",
        permissions = arrayOf(Manifest.permission.CAMERA),
        key = "camera",
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var name by remember { mutableStateOf(vm.reporterName) }
    var phone by remember { mutableStateOf(vm.reporterPhone) }
    var permissionTick by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmWipeData by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionTick += 1
        if (grants.values.none { it }) {
            scope.launch {
                snackbar.showSnackbar("Permission denied. You can still use the demo location and gallery.")
            }
        }
    }

    fun request(info: PermissionInfo) {
        permissionLauncher.launch(info.permissions)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SectionLabel("Device", accent = Green)
        Spacer(Modifier.height(6.dp))
        Text("Setup", color = TextMain, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Everything runs on this phone - triage, storage and alerts. No server. " +
                "Live routing is used automatically when online; switch it off to force offline.",
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(14.dp))

        // ------------------------------------------------------------- engine
        SectionLabel("On-device engine")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Green),
                    )
                    Text(
                        "Engine ready",
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    IconButton(onClick = { vm.refreshStatus() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh engine status",
                            tint = TextMuted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }

                HealthDetail(label = "Triage", value = "Rules + heuristic CV (v${vm.engineVersion})")
                HealthDetail(
                    label = "Network",
                    value = if (vm.onlineRouting) {
                        "Auto - live route when internet is available"
                    } else {
                        "Forced off - no network calls"
                    },
                )
                HealthDetail(
                    label = "Dispatch",
                    value = if (vm.onlineRouting) {
                        "Live route when available · else distance + ETA"
                    } else {
                        "Distance + ETA only · not route-aware"
                    },
                )
                HealthDetail(label = "Geocoder", value = "12 NH-44 landmarks · 6 km radius (demo)")
                HealthDetail(label = "Alerts", value = "Simulated on-device · no SMS/WhatsApp/email")
                HealthDetail(label = "Incidents", value = "${vm.stats?.incidents ?: 0} stored on this phone")
                HealthDetail(label = "Active", value = "${vm.stats?.activeIncidents ?: 0} open right now")
                HealthDetail(label = "Photos", value = "${vm.stats?.photos ?: 0} re-encoded, EXIF stripped")
                HealthDetail(label = "Storage", value = "${vm.stats?.storageKb ?: 0} KB in app-private storage")

                Spacer(Modifier.height(12.dp))
                SecondaryAction(
                    text = "Reset demo data",
                    onClick = { confirmWipeData = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Restores the three sample incidents and responder units. Reports you filed are removed.",
                    color = TextDim,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // -------------------------------------------------------- online mode
        SectionLabel("Online mode (optional)")
        Spacer(Modifier.height(8.dp))
        PanelCard(accent = if (vm.onlineRouting) Cyan else null) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Online mode: live routing",
                            color = TextMain,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (vm.onlineRouting) "AUTO - live when online" else "FORCED OFF - fully offline",
                            color = if (vm.onlineRouting) Cyan else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Switch(
                        checked = vm.onlineRouting,
                        onCheckedChange = { enabled ->
                            vm.setOnlineMode(enabled)
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (enabled) {
                                        "Online routing auto: live when internet is available."
                                    } else {
                                        "Online routing forced off. The app is fully offline."
                                    },
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Cyan,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = PanelRaised,
                            uncheckedBorderColor = Stroke,
                        ),
                    )
                }

                Text(
                    "Online routing is automatic. When the phone has a validated internet connection, " +
                        "RoadSOS sends the crash coordinates and the nearest unit's base coordinates " +
                        "to Google Directions when a key is configured (traffic-aware ETA), or to the " +
                        "OSRM public demo server otherwise (real road route, no traffic data). " +
                        "Names, contacts, photos and report text are never sent. " +
                        "Offline, or with this switch off, the ETA stays a straight-line estimate.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (vm.directionsKeyConfigured) Green else Amber),
                    )
                    Text(
                        if (vm.directionsKeyConfigured) {
                            "Directions key configured - traffic-aware route when Google returns it"
                        } else {
                            "No Directions key - using OSRM (road route, no traffic) when online"
                        },
                        color = if (vm.directionsKeyConfigured) TextMuted else Amber,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                Text(
                    "The key is injected at build time from the untracked local.properties and is " +
                        "extractable from the APK. Restrict it by package name and SHA-1 in Google Cloud.",
                    color = TextDim,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ------------------------------------------------------------ profile
        SectionLabel("Reporter profile")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Optional. Pre-fills the callback fields on the review step and is stored on this device.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name", color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = setupFieldColors(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.take(20) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Callback phone", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    colors = setupFieldColors(),
                )
                SecondaryAction(
                    text = "Save profile",
                    onClick = {
                        vm.saveReporter(name, phone)
                        scope.launch { snackbar.showSnackbar("Reporter profile saved.") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // -------------------------------------------------------- permissions
        SectionLabel("Permissions")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PERMISSIONS.forEach { info ->
                val granted = remember(info.key, permissionTick) {
                    info.permissions.any {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                }
                val icon = when (info.key) {
                    "location" -> Icons.Filled.LocationOn
                    else -> Icons.Filled.CameraAlt
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Panel,
                    border = BorderStroke(1.dp, Stroke),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (granted) Green.copy(alpha = 0.12f) else PanelRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = if (granted) Green else TextDim, modifier = Modifier.size(17.dp))
                        }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(info.label, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (granted) "  GRANTED" else "  NOT GRANTED",
                                    color = if (granted) Green else TextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            Text(
                                info.rationale,
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        TextButton(
                            onClick = { if (granted) permissionTick += 1 else request(info) },
                        ) {
                            Text(if (granted) "Re-check" else "Allow", color = Cyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryAction(
            text = "Open app settings",
            onClick = { openAppSettings(context) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        // --------------------------------------------------------------- demo
        SectionLabel("Device & demo")
        Spacer(Modifier.height(8.dp))
        PanelCard {
            Column {
                HealthDetail(label = "Device ID", value = vm.deviceId)
                Spacer(Modifier.height(4.dp))
                SecondaryAction(
                    text = "Copy device ID",
                    onClick = {
                        clipboard.setText(AnnotatedString(vm.deviceId))
                        scope.launch { snackbar.showSnackbar("Device ID copied.") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = TextDim, modifier = Modifier.size(16.dp))
                    Text(
                        "Demo contacts",
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    TextButton(onClick = { confirmReset = true }) {
                        Text("Reset", color = TextMuted, fontSize = 11.sp)
                    }
                }
                Text(
                    "Restores the three seeded contacts (${Store.MAX_CONTACTS} max). Local contacts are replaced.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        PanelCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Red, modifier = Modifier.size(17.dp))
                    Text(
                        " RoadSOS demo build",
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "Version 1.0 - standalone Android app. Triage, incidents, contacts and driver alerts " +
                        "live on this device. Contact alerts and driver pushes are simulated, and no SMS is sent.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Not connected to the React/Flask dashboard - the two products share only the demo scenario.",
                    color = TextDim,
                    fontSize = 9.sp,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "In a real emergency, call 112.",
                    color = Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo contacts?", color = TextMain) },
            text = {
                Text(
                    "Your saved contacts will be replaced with Ananya, Vikram and Meera.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetContacts()
                    confirmReset = false
                    scope.launch { snackbar.showSnackbar("Demo contacts restored.") }
                }) {
                    Text("Reset", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelRaised,
            shape = RoundedCornerShape(18.dp),
        )
    }

    if (confirmWipeData) {
        AlertDialog(
            onDismissRequest = { confirmWipeData = false },
            title = { Text("Reset demo data?", color = TextMain) },
            text = {
                Text(
                    "Sample incidents, responder units and stored photos are restored. " +
                        "Reports you filed on this phone are deleted.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipeData = false
                    scope.launch {
                        // Deletes every photo and rewrites the DB: keep it off the main thread.
                        withContext(Dispatchers.IO) { vm.resetDemoData() }
                        snackbar.showSnackbar("Demo data restored.")
                    }
                }) {
                    Text("Reset", color = TextMuted, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipeData = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
            containerColor = PanelRaised,
            shape = RoundedCornerShape(18.dp),
        )
    }
}

@Composable
private fun HealthDetail(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextDim, fontSize = 10.sp, modifier = Modifier.weight(0.32f))
        Text(
            value,
            color = TextMain,
            fontSize = 11.sp,
            fontFamily = if (label.contains("URL", ignoreCase = true) || label.contains("Device", ignoreCase = true)) {
                FontFamily.Monospace
            } else {
                FontFamily.Default
            },
            modifier = Modifier.weight(0.68f),
        )
    }
}

@Composable
private fun setupFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Stroke,
    focusedContainerColor = Panel,
    unfocusedContainerColor = Panel,
    focusedLabelColor = Cyan,
    cursorColor = Cyan,
)

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }
}
