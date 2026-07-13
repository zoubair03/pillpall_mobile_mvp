package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MorningSunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 4.5f
        val cx = size.width / 2f
        val cy = size.height / 2f + r * 0.4f

        // Draw Sun circle
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy - r * 0.6f)
        )

        // Draw top rays
        val rayLength = r * 0.9f
        val strokeWidth = 2.5.dp.toPx()
        val angles = listOf(-150f, -120f, -90f, -60f, -30f)
        for (angleDeg in angles) {
            val angle = angleDeg * (Math.PI / 180f)
            val startX = cx + (r * 1.3f) * Math.cos(angle).toFloat()
            val startY = (cy - r * 0.6f) + (r * 1.3f) * Math.sin(angle).toFloat()
            val endX = cx + (r * 1.3f + rayLength) * Math.cos(angle).toFloat()
            val endY = (cy - r * 0.6f) + (r * 1.3f + rayLength) * Math.sin(angle).toFloat()

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Horizontal horizon/ground line
        drawLine(
            color = color,
            start = Offset(cx - r * 2.2f, cy),
            end = Offset(cx + r * 2.2f, cy),
            strokeWidth = strokeWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun MiddaySunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 3.2f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Draw Bright Center Sun
        drawCircle(
            color = color,
            radius = r,
            center = Offset(cx, cy)
        )

        // Draw rays all around
        val rayLength = r * 0.6f
        val strokeWidth = 2.5.dp.toPx()
        for (i in 0 until 8) {
            val angle = (i * 45) * (Math.PI / 180f)
            val startX = cx + (r * 1.3f) * Math.cos(angle).toFloat()
            val startY = cy + (r * 1.3f) * Math.sin(angle).toFloat()
            val endX = cx + (r * 1.3f + rayLength) * Math.cos(angle).toFloat()
            val endY = cy + (r * 1.3f + rayLength) * Math.sin(angle).toFloat()

            drawLine(
                color = color,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun NightMoonIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2.5f
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Draw crescent moon path
        val path = androidx.compose.ui.graphics.Path().apply {
            addArc(
                oval = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -90f,
                sweepAngleDegrees = 180f
            )
            quadraticTo(
                x1 = cx + r * 0.15f,
                y1 = cy,
                x2 = cx,
                y2 = cy - r
            )
            close()
        }

        drawPath(
            path = path,
            color = color
        )

        // Mini star
        drawCircle(
            color = color,
            radius = 2.5.dp.toPx(),
            center = Offset(cx - r * 0.5f, cy - r * 0.5f)
        )
    }
}
