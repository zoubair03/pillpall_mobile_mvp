package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.statusBarsPadding
import com.example.ui.theme.*

@Composable
fun WifiIcon(isConnected: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.85f // Dot center coordinates
        val strokeWidth = 2.dp.toPx()

        // Bottom signal dot
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = Offset(cx, cy)
        )

        if (isConnected) {
            // Three concentric wave rings
            for (i in 1..3) {
                val r = (i * 4.5).dp.toPx()
                drawArc(
                    color = color,
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        } else {
            // Offline wave outline (faint) with strike-through slash line
            for (i in 1..3) {
                val r = (i * 4.5).dp.toPx()
                drawArc(
                    color = color.copy(alpha = 0.35f),
                    startAngle = -135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
            // Crisp strike-through slash lines
            drawLine(
                color = color,
                start = Offset(cx - 7.dp.toPx(), cy - 13.dp.toPx()),
                end = Offset(cx + 7.dp.toPx(), cy + 2.dp.toPx()),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun BatteryIcon(batteryLevel: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val rectW = w * 0.82f
        val rectH = h * 0.55f

        val rx = (w - rectW) / 2f
        val ry = (h - rectH) / 2f

        val strokeWidth = 1.5.dp.toPx()
        val cornerRadius = 2.2.dp.toPx()

        // Outer case frame drawing
        drawRoundRect(
            color = color,
            topLeft = Offset(rx, ry),
            size = androidx.compose.ui.geometry.Size(rectW - 2.5.dp.toPx(), rectH),
            style = Stroke(width = strokeWidth),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
        )

        // Terminal connection nib drawing
        val nibW = 2.2.dp.toPx()
        val nibH = rectH * 0.4f
        drawRoundRect(
            color = color,
            topLeft = Offset(rx + rectW - 2.5.dp.toPx(), ry + (rectH - nibH) / 2f),
            size = androidx.compose.ui.geometry.Size(nibW, nibH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(0.8.dp.toPx(), 0.8.dp.toPx())
        )

        // Fill gauge level
        val levelPercent = batteryLevel.coerceIn(0, 100) / 100f
        val padding = 2.dp.toPx()
        val fillW = (rectW - 2.5.dp.toPx() - padding * 2) * levelPercent
        val fillH = rectH - padding * 2
        drawRoundRect(
            color = color,
            topLeft = Offset(rx + padding, ry + padding),
            size = androidx.compose.ui.geometry.Size(fillW, fillH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )
    }
}

@Composable
fun PillPalTopStatusBar(
    isSyncing: Boolean,
    isOnline: Boolean,
    batteryLevel: Int,
    onSyncClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PillPal Logo Name
            Text(
                text = "PillPal",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PillPalPrimary,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("app_logo_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // WiFi Status Chip
                Row(
                    modifier = Modifier
                        .background(
                            if (isOnline) CustomPaletteMint.copy(alpha = 0.22f) else PillPalAlertBg.copy(alpha = 0.22f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val wifiColor = if (isOnline) PillPalPrimary else PillPalAlert
                    WifiIcon(
                        isConnected = isOnline,
                        color = wifiColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isOnline) "WiFi" else "Hors-ligne",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) PillPalPrimary else PillPalAlertText
                    )
                }

                // Battery Status Chip
                Row(
                    modifier = Modifier
                        .background(PillPalWarningBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BatteryIcon(
                        batteryLevel = batteryLevel,
                        color = PillPalPrimary,
                        modifier = Modifier.size(24.dp, 16.dp)
                    )
                    Text(
                        text = "$batteryLevel%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PillPalTextSecondary
                    )
                }

                // Rotating Refresh/Sync Sync button
                val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                val rotationAngle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "sync_rotation"
                )

                IconButton(
                    onClick = onSyncClick,
                    modifier = Modifier
                        .testTag("sync_hardware_button")
                        .size(36.dp)
                        .background(
                            if (isSyncing) PillPalPrimary.copy(alpha = 0.1f) else Color.Transparent,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Synchroniser l'appareil",
                        tint = PillPalPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (isSyncing) rotationAngle else 0f)
                    )
                }
            }
        }
    }
}
