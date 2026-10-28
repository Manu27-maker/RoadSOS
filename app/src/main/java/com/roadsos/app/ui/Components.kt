package com.roadsos.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadsos.app.ui.theme.Amber
import com.roadsos.app.ui.theme.Bg
import com.roadsos.app.ui.theme.Cyan
import com.roadsos.app.ui.theme.Green
import com.roadsos.app.ui.theme.Orange
import com.roadsos.app.ui.theme.Panel
import com.roadsos.app.ui.theme.PanelRaised
import com.roadsos.app.ui.theme.PanelTop
import com.roadsos.app.ui.theme.Red
import com.roadsos.app.ui.theme.Stroke
import com.roadsos.app.ui.theme.TextDim
import com.roadsos.app.ui.theme.TextMain
import com.roadsos.app.ui.theme.TextMuted

fun severityColor(severity: String?): Color = when (severity?.trim()?.lowercase()) {
    "critical" -> Red
    "high" -> Orange
    "medium" -> Amber
    "low" -> Green
    else -> TextDim
}

fun statusColor(status: String?): Color = when (status?.trim()?.lowercase()) {
    "dispatched" -> Amber
    "on scene" -> Orange
    "resolved" -> Green
    else -> Cyan
}

fun statusRank(status: String?): Int = when (status?.trim()?.lowercase()) {
    "dispatched" -> 1
    "on scene" -> 2
    "resolved" -> 3
    else -> 0
}

/** Hairline border reused by every card, allocated once instead of per card. */
private val CARD_BORDER = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.03f)),
)

/**
 * Surface card with a soft top-light gradient, hairline border and an optional
 * coloured accent strip along the top edge.
 */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, CARD_BORDER),
    ) {
        Column(
            Modifier.background(
                Brush.verticalGradient(listOf(PanelTop.copy(alpha = 0.55f), Panel)),
            ),
        ) {
            if (accent != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.05f))),
                        ),
                )
            }
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, accent: Color = TextDim) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Text(
            text = text.uppercase(),
            modifier = Modifier.padding(start = 7.dp),
            color = TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun Dot(color: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

/**
 * Status dot. Only severity chips for critical/high incidents get a breathing
 * dot, and only they pay for an animation - a static dot runs no frame clock.
 */
@Composable
private fun PulseDot(color: Color, pulsing: Boolean, size: Dp = 6.dp) {
    if (!pulsing) {
        Dot(color, size)
        return
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "dotAlpha",
    )
    Dot(color.copy(alpha = alpha), size)
}

@Composable
fun Chip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    score: Int? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.33f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pulsing) PulseDot(color = color, pulsing = true) else Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
        if (score != null) {
            Text(text = "$score", color = color.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

@Composable
fun SeverityChip(severity: String?, modifier: Modifier = Modifier, score: Int? = null) {
    val color = severityColor(severity)
    val pulsing = severity?.trim()?.lowercase() in setOf("critical", "high")
    Chip(label = severity ?: "Pending", color = color, modifier = modifier, pulsing = pulsing, score = score)
}

@Composable
fun StatusChip(status: String?, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Chip(label = status ?: "Unknown", color = color, modifier = modifier, pulsing = false)
}

@Composable
fun ScoreRing(
    score: Int,
    severity: String?,
    size: Dp = 88.dp,
    strokeWidth: Dp = 8.dp,
) {
    val color = severityColor(severity)
    val target = score.coerceIn(0, 100) / 100f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "scoreSweep",
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

            drawArc(
                color = color.copy(alpha = 0.14f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                // Soft outer glow, then the crisp progress arc.
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                color = TextMain,
                fontSize = if (size > 80.dp) 26.sp else 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = (severity ?: "AI").uppercase(),
                color = color,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
            )
        }
    }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Red,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.975f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "buttonPress",
    )
    val active = enabled && !loading
    val shape = RoundedCornerShape(16.dp)
    // Light accents (cyan, green, orange) need dark ink to stay legible.
    val ink = if (color.luminance() > 0.30f) Bg else Color.White
    val sheen = if (ink == Bg) 0.10f else 0.18f
    // Loading keeps the accent visible; a disabled button fades into the panel.
    val container = when {
        loading -> color.copy(alpha = 0.82f)
        enabled -> color
        else -> color.copy(alpha = 0.20f)
    }
    val content = when {
        loading || enabled -> ink
        else -> Color.White.copy(alpha = 0.62f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (active) 16.dp else 0.dp,
                shape = shape,
                ambientColor = color.copy(alpha = 0.55f),
                spotColor = color.copy(alpha = 0.75f),
            )
            .clip(shape)
            .background(container),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = sheen), Color.Transparent),
                    ),
                ),
        )
        Button(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
            enabled = active,
            interactionSource = interaction,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = content,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = content,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
                Spacer(Modifier.width(9.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = text,
                color = content,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.sp,
            )
        }
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = TextMain,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Stroke),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            containerColor = PanelRaised.copy(alpha = 0.55f),
            disabledContentColor = contentColor.copy(alpha = 0.4f),
        ),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Red.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Red.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Red.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("!", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
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
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Cyan,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.10f))
                    .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        Text(text = title, color = TextMain, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp),
        )
    }
}
