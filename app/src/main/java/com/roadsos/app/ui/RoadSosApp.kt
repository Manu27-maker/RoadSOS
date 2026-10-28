package com.roadsos.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadsos.app.MainViewModel
import com.roadsos.app.dialEmergency
import com.roadsos.app.ui.screens.ContactsScreen
import com.roadsos.app.ui.screens.DriveScreen
import com.roadsos.app.ui.screens.ReportScreen
import com.roadsos.app.ui.screens.SetupScreen
import com.roadsos.app.ui.theme.Bg
import com.roadsos.app.ui.theme.BgBottom
import com.roadsos.app.ui.theme.BgTop
import com.roadsos.app.ui.theme.Cyan
import com.roadsos.app.ui.theme.Panel
import com.roadsos.app.ui.theme.Red
import com.roadsos.app.ui.theme.RedDark
import com.roadsos.app.ui.theme.Stroke
import com.roadsos.app.ui.theme.TextDim
import com.roadsos.app.ui.theme.TextMain
import com.roadsos.app.ui.theme.TextMuted

private data class NavTab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    NavTab("Report", Icons.Filled.Warning),
    NavTab("Drive", Icons.Filled.DirectionsCar),
    NavTab("Contacts", Icons.Filled.Contacts),
    NavTab("Setup", Icons.Filled.Settings),
)

@Composable
fun RoadSosApp(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                TopBar(onCall = { dialEmergency(context) })
            }
        },
        bottomBar = {
            BottomNav(selected = tab, onSelect = { tab = it })
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState > initialState
                    (
                        fadeIn(tween(durationMillis = 220)) +
                            slideInHorizontally(tween(durationMillis = 260)) { width ->
                                if (forward) width / 16 else -width / 16
                            }
                        ).togetherWith(fadeOut(tween(durationMillis = 120)))
                },
                label = "tabTransition",
            ) { index ->
                when (index) {
                    0 -> ReportScreen(vm, snackbar)
                    1 -> DriveScreen(vm, snackbar)
                    2 -> ContactsScreen(vm, snackbar)
                    else -> SetupScreen(vm, snackbar)
                }
            }
        }
    }
}

@Composable
private fun TopBar(onCall: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(BgTop, Bg))),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .shadow(10.dp, RoundedCornerShape(12.dp), ambientColor = Red, spotColor = Red)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Red, RedDark))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column {
                Text(
                    "RoadSOS",
                    color = TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
                Text(
                    "HIGHWAY RESPONSE",
                    color = TextMuted,
                    fontSize = 8.sp,
                    letterSpacing = 1.9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.weight(1f))
            OnDeviceBadge()
            CallButton(onClick = onCall)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Stroke),
        )
    }
}

@Composable
private fun OnDeviceBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Cyan.copy(alpha = 0.08f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Static marker: the top bar is always on screen, so it must not hold a
        // frame clock open just to breathe.
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Cyan),
        )
        Text(
            "ON-DEVICE AI",
            color = Cyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "call")
    val pulse by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300), RepeatMode.Reverse),
        label = "callPulse",
    )
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Red.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Red.copy(alpha = pulse)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                tint = Red,
                modifier = Modifier.size(13.dp),
            )
            Text("112", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Panel, BgBottom)))
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Stroke),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEachIndexed { index, item ->
                NavItem(
                    label = item.label,
                    icon = item.icon,
                    selected = selected == index,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Cyan else TextDim,
        animationSpec = tween(durationMillis = 220),
        label = "navTint",
    )
    val indicator by animateColorAsState(
        targetValue = if (selected) Cyan.copy(alpha = 0.13f) else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "navIndicator",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "navScale",
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(indicator)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.1.sp,
        )
        Spacer(Modifier.height(2.dp))
    }
}
