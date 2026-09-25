package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DriverAlertLevel
import com.example.model.EyeState
import com.example.ui.theme.DriverCyan
import com.example.ui.theme.DriverGreenAwake
import com.example.ui.theme.DriverNavyDark
import com.example.ui.theme.DriverRedAlert

@Composable
fun DriverFaceVisualizer(
    eyeState: EyeState,
    alertLevel: DriverAlertLevel,
    fatigueScore: Int,
    threshold: Int,
    earValue: Float, // Eye Aspect Ratio (0.0 to 0.40)
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline"
    )

    val borderColor = when (alertLevel) {
        DriverAlertLevel.CRITICAL_ALARM -> DriverRedAlert.copy(alpha = pulseAlpha)
        DriverAlertLevel.WARNING -> Color(0xFFF59E0B)
        DriverAlertLevel.SAFE -> DriverCyan.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DriverNavyDark)
            .border(
                width = if (alertLevel == DriverAlertLevel.CRITICAL_ALARM) 4.dp else 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val centerX = w / 2f
            val centerY = h / 2f

            // Biometric subtle grid
            val gridStep = 40f
            var x = 0f
            while (x < w) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < h) {
                drawLine(
                    color = Color.White.copy(alpha = 0.04f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // Head & Face Haar Bounding Box
            val faceWidth = w * 0.52f
            val faceHeight = h * 0.68f
            val faceLeft = centerX - (faceWidth / 2f)
            val faceTop = centerY - (faceHeight / 2f) + 10f

            // Corner brackets for face detection (OpenCV style)
            val bracketLen = 28f
            val bracketColor = Color(0xFF38BDF8).copy(alpha = 0.8f)

            // Top-Left corner
            drawLine(bracketColor, Offset(faceLeft, faceTop), Offset(faceLeft + bracketLen, faceTop), 3f)
            drawLine(bracketColor, Offset(faceLeft, faceTop), Offset(faceLeft, faceTop + bracketLen), 3f)

            // Top-Right corner
            drawLine(bracketColor, Offset(faceLeft + faceWidth, faceTop), Offset(faceLeft + faceWidth - bracketLen, faceTop), 3f)
            drawLine(bracketColor, Offset(faceLeft + faceWidth, faceTop), Offset(faceLeft + faceWidth, faceTop + bracketLen), 3f)

            // Bottom-Left corner
            drawLine(bracketColor, Offset(faceLeft, faceTop + faceHeight), Offset(faceLeft + bracketLen, faceTop + faceHeight), 3f)
            drawLine(bracketColor, Offset(faceLeft, faceTop + faceHeight), Offset(faceLeft, faceTop + faceHeight - bracketLen), 3f)

            // Bottom-Right corner
            drawLine(bracketColor, Offset(faceLeft + faceWidth, faceTop + faceHeight), Offset(faceLeft + faceWidth - bracketLen, faceTop + faceHeight), 3f)
            drawLine(bracketColor, Offset(faceLeft + faceWidth, faceTop + faceHeight), Offset(faceLeft + faceWidth, faceTop + faceHeight - bracketLen), 3f)

            // Head silhouette outline
            val headPath = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = faceLeft + 18f,
                        top = faceTop + 14f,
                        right = faceLeft + faceWidth - 18f,
                        bottom = faceTop + faceHeight - 12f,
                        radiusX = faceWidth * 0.35f,
                        radiusY = faceHeight * 0.45f
                    )
                )
            }
            drawPath(
                path = headPath,
                color = Color.White.copy(alpha = 0.08f),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f))
            )

            // Eye positions
            val eyeSpacing = faceWidth * 0.28f
            val eyeY = faceTop + (faceHeight * 0.38f)
            val leftEyeX = centerX - eyeSpacing
            val rightEyeX = centerX + eyeSpacing

            val eyeBoxWidth = faceWidth * 0.26f
            val eyeBoxHeight = faceHeight * 0.22f

            val isClosed = eyeState == EyeState.CLOSED || earValue < 0.18f
            val eyeColor = if (isClosed) DriverRedAlert else DriverGreenAwake

            // Draw Left Eye Haar Box
            val leftBoxLeft = leftEyeX - (eyeBoxWidth / 2f)
            val leftBoxTop = eyeY - (eyeBoxHeight / 2f)
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(leftBoxLeft, leftBoxTop),
                size = Size(eyeBoxWidth, eyeBoxHeight),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2.5f)
            )

            // Draw Right Eye Haar Box
            val rightBoxLeft = rightEyeX - (eyeBoxWidth / 2f)
            val rightBoxTop = eyeY - (eyeBoxHeight / 2f)
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(rightBoxLeft, rightBoxTop),
                size = Size(eyeBoxWidth, eyeBoxHeight),
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(width = 2.5f)
            )

            // Eye Features (Pupil and Eyelids)
            listOf(leftEyeX, rightEyeX).forEach { ex ->
                if (isClosed) {
                    // Closed eye: horizontal slit
                    drawLine(
                        color = eyeColor,
                        start = Offset(ex - (eyeBoxWidth * 0.35f), eyeY),
                        end = Offset(ex + (eyeBoxWidth * 0.35f), eyeY),
                        strokeWidth = 3f
                    )
                    // Downward eyelash marks
                    drawLine(
                        color = eyeColor.copy(alpha = 0.7f),
                        start = Offset(ex - 12f, eyeY),
                        end = Offset(ex - 16f, eyeY + 8f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = eyeColor.copy(alpha = 0.7f),
                        start = Offset(ex, eyeY),
                        end = Offset(ex, eyeY + 9f),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = eyeColor.copy(alpha = 0.7f),
                        start = Offset(ex + 12f, eyeY),
                        end = Offset(ex + 16f, eyeY + 8f),
                        strokeWidth = 2f
                    )
                } else {
                    // Open eye: sclera and pupil
                    val pupilRadius = 7f + (earValue * 10f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.9f),
                        radius = 16f,
                        center = Offset(ex, eyeY)
                    )
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = pupilRadius,
                        center = Offset(ex, eyeY)
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = pupilRadius * 0.6f,
                        center = Offset(ex, eyeY)
                    )
                    // Specular light reflection
                    drawCircle(
                        color = Color.White,
                        radius = 2.5f,
                        center = Offset(ex - 3f, eyeY - 3f)
                    )
                }
            }

            // Biometric scanning laser line
            val laserY = h * scanLineY
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        DriverCyan.copy(alpha = 0.8f),
                        Color.White,
                        DriverCyan.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                ),
                start = Offset(0f, laserY),
                end = Offset(w, laserY),
                strokeWidth = 2f
            )

            // Critical alert red vignette flashing
            if (alertLevel == DriverAlertLevel.CRITICAL_ALARM) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            DriverRedAlert.copy(alpha = 0.35f * pulseAlpha)
                        ),
                        center = Offset(centerX, centerY),
                        radius = w * 0.8f
                    ),
                    size = size
                )
            }
        }

        // Live Overlays & Labels
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            val statusColor = if (eyeState == EyeState.CLOSED) DriverRedAlert else DriverGreenAwake
            Text(
                text = "EYE STATE: ${eyeState.label.uppercase()}",
                color = statusColor,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "EAR: ${String.format("%.2f", earValue)} | FATIGUE: $fatigueScore/$threshold",
                color = Color.White,
                fontSize = 12.sp
            )
        }

        if (alertLevel == DriverAlertLevel.CRITICAL_ALARM) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(DriverRedAlert, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚠️ WAKE UP! DROWSINESS DETECTED!",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}
