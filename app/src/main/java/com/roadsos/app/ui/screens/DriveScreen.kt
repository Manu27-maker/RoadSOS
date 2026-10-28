package com.roadsos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SnackbarHostState
import com.roadsos.app.MainViewModel
import com.roadsos.app.data.Incident
import com.roadsos.app.data.NearbyDriversResponse
import com.roadsos.app.data.errorMessage
import com.roadsos.app.engine.LocalEngine
import com.roadsos.app.dialEmergency
import com.roadsos.app.ui.ErrorBanner
import com.roadsos.app.ui.PanelCard
import com.roadsos.app.ui.PrimaryAction
import com.roadsos.app.ui.SecondaryAction
import com.roadsos.app.ui.SectionLabel
import com.roadsos.app.ui.SeverityChip
import com.roadsos.app.ui.StatusChip
import com.roadsos.app.ui.severityColor
import com.roadsos.app.ui.theme.Amber
import com.roadsos.app.ui.theme.Cyan
import com.roadsos.app.ui.theme.Panel
import com.roadsos.app.ui.theme.Red
import com.roadsos.app.ui.theme.Stroke
import com.roadsos.app.ui.theme.TextDim
import com.roadsos.app.ui.theme.TextMain
import com.roadsos.app.ui.theme.TextMuted
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun DriveScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var incidents by remember { mutableStateOf<List<Incident>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var radius by remember { mutableFloatStateOf(3f) }
    var nearby by remember { mutableStateOf<NearbyDriversResponse?>(null) }
    var loadingIncidents by remember { mutableStateOf(false) }
    var loadingNearby by remember { mutableStateOf(false) }
    var broadcasting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        loadingIncidents = true
        error = null
        runCatching { LocalEngine.incidents(activeOnly = true) }
            .onSuccess { response ->
                incidents = response.incidents.orEmpty()
                if (selectedId == null || incidents.none { it.id == selectedId }) {
                    selectedId = incidents.firstOrNull()?.id
                }
            }
            .onFailure { error = errorMessage(it) }
        loadingIncidents = false
    }

    LaunchedEffect(selectedId, radius, reloadKey) {
        val id = selectedId ?: return@LaunchedEffect
        delay(250)
        loadingNearby = true
        error = null
        runCatching { LocalEngine.nearbyDrivers(id, radius.toDouble(), notify = false) }
            .onSuccess { nearby = it }
            .onFailure { error = errorMessage(it) }
        loadingNearby = false
    }

    val selected = incidents.firstOrNull { it.id == selectedId }
    val board = nearby?.board
    val boardColor = severityColor(board?.severity ?: selected?.finalSeverity)
    val inGeofence = nearby?.inGeofence ?: nearby?.drivers?.count { it.insideGeofence == true } ?: 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Driver alerts", accent = Amber)
                Spacer(Modifier.height(6.dp))
                Text("Approaching driver", color = TextMain, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Simulated in-car unit · geofence computed on this phone",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            IconButton(onClick = { reloadKey += 1 }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextMuted)
            }
        }

        if (loadingIncidents && incidents.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 30.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = Cyan, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }

        if (incidents.isEmpty() && !loadingIncidents) {
            PanelCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = TextDim, modifier = Modifier.size(34.dp))
                    Text(
                        "No active incidents",
                        color = TextMain,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "File a report from the Report tab, then come back to see the driver alert.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryAction("Refresh", { reloadKey += 1 }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (incidents.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                incidents.take(6).forEach { incident ->
                    val active = incident.id == selectedId
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedId = incident.id },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) Panel else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (active) severityColor(incident.finalSeverity).copy(alpha = 0.5f) else Stroke,
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            SeverityChip(severity = incident.finalSeverity, score = incident.finalScore)
                            Text(
                                incident.landmark ?: incident.id ?: "Incident",
                                color = if (active) TextMain else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (loadingNearby && nearby == null) {
                Box(
                    Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Cyan, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            if (board != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = boardColor.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, boardColor.copy(alpha = 0.4f)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = boardColor.copy(alpha = 0.16f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, boardColor.copy(alpha = 0.4f)),
                            ) {
                                Text(
                                    if (board.blocked == true) "ROAD BLOCKED" else "COLLISION AHEAD",
                                    color = boardColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                board.distanceKm?.let { String.format(Locale.US, "%.1f km ahead", it) } ?: "",
                                color = boardColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            board.line1 ?: "ACCIDENT AHEAD",
                            color = TextMain,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            board.line2 ?: "Slow down and move left",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (!board.line3.isNullOrBlank()) {
                            Text(
                                board.line3,
                                color = boardColor.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }

                        if (board.blocked == true) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Red.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    "Prepare to stop or take the nearest exit. Do not cross the carriageway.",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stat("Distance", board.distanceKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "-")
                            Stat("In geofence", "$inGeofence")
                            Stat("Severity", board.severity ?: "-")
                        }

                        Spacer(Modifier.height(14.dp))
                        PrimaryAction(
                            text = if (broadcasting) "Broadcasting..." else "Broadcast to $inGeofence drivers",
                            onClick = {
                                val id = selectedId ?: return@PrimaryAction
                                scope.launch {
                                    broadcasting = true
                                    runCatching {
                                        LocalEngine.nearbyDrivers(id, radius.toDouble(), notify = true)
                                    }.onSuccess {
                                        nearby = it
                                        val targeted = it.push?.targeted ?: it.inGeofence ?: 0
                                        snackbar.showSnackbar("Mock alert queued to $targeted drivers on this device.")
                                    }.onFailure {
                                        snackbar.showSnackbar(errorMessage(it))
                                    }
                                    broadcasting = false
                                }
                            },
                            enabled = !broadcasting && !loadingNearby,
                            color = if (board.blocked == true) Red else Amber,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                PanelCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                            Text(
                                "Alert radius  ${String.format(Locale.US, "%.1f", radius)} km",
                                color = TextMain,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        Slider(
                            value = radius,
                            onValueChange = { radius = it },
                            valueRange = 2f..5f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                thumbColor = Cyan,
                                activeTrackColor = Cyan,
                                inactiveTrackColor = Cyan.copy(alpha = 0.15f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("2 km", color = TextDim, fontSize = 9.sp)
                            Text("5 km", color = TextDim, fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryAction(
                                "Call 112",
                                { dialEmergency(context) },
                                contentColor = Red,
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryAction(
                                "Refresh alerts",
                                { selectedId?.let { reloadKey += 1 } },
                                enabled = !loadingNearby,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                PanelCard {
                    Column {
                        SectionLabel("Highway VMS board")
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0A0F0A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber.copy(alpha = 0.25f)),
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    board.line1 ?: "ACCIDENT AHEAD",
                                    color = Amber,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    board.line2 ?: "",
                                    color = Amber.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    board.line3 ?: "",
                                    color = Amber.copy(alpha = 0.55f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        if (!board.source.isNullOrBlank()) {
                            Text(
                                board.source,
                                color = TextDim,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Driver positions are generated deterministically on this phone. A driver is alerted only when " +
                        "inside the radius and heading toward the incident (haversine + bearing).",
                    color = TextDim,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }

            if (error != null) ErrorBanner(error!!)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label.uppercase(), color = TextDim, fontSize = 8.sp, letterSpacing = 0.6.sp)
        Text(value, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
