package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AlarmSoundManager
import com.example.model.DriverAlertLevel
import com.example.model.DriverIncident
import com.example.model.EyeState
import com.example.ui.components.DriverFaceVisualizer
import com.example.ui.theme.DriverCyan
import com.example.ui.theme.DriverGreenAwake
import com.example.ui.theme.DriverRedAlert
import com.example.ui.theme.DriverSurfaceDark
import com.example.ui.theme.DriverSurfaceVariantDark
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DriverScenario(val title: String) {
    AWAKE("Awake (Normal)"),
    BLINKING("Natural Blinks"),
    DROWSY("Heavy Eyelids"),
    MICRO_SLEEP("Micro-Sleep (Alarm!)"),
    DISTRACTED("Looking Away")
}

@Composable
fun MonitorScreen(
    soundManager: AlarmSoundManager,
    onIncidentRecorded: (DriverIncident) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMonitoringActive by remember { mutableStateOf(true) }
    var selectedScenario by remember { mutableStateOf(DriverScenario.AWAKE) }
    var threshold by remember { mutableIntStateOf(15) }
    var isAudioMuted by remember { mutableStateOf(false) }

    // Telemetry state
    var fatigueScore by remember { mutableIntStateOf(0) }
    var eyeState by remember { mutableStateOf(EyeState.OPEN) }
    var earValue by remember { mutableFloatStateOf(0.32f) }
    var alertLevel by remember { mutableStateOf(DriverAlertLevel.SAFE) }
    var tripElapsedSeconds by remember { mutableLongStateOf(0L) }

    // Driving Simulation Loop
    LaunchedEffect(isMonitoringActive, selectedScenario, threshold, isAudioMuted) {
        if (!isMonitoringActive) return@LaunchedEffect

        var blinkCounter = 0
        var closureFrames = 0

        while (true) {
            delay(100) // ~10 updates per second for smooth UI response
            blinkCounter++
            tripElapsedSeconds++

            when (selectedScenario) {
                DriverScenario.AWAKE -> {
                    eyeState = EyeState.OPEN
                    earValue = 0.32f + (Math.sin(blinkCounter * 0.1).toFloat() * 0.03f)
                    if (fatigueScore > 0) fatigueScore -= 1
                }
                DriverScenario.BLINKING -> {
                    // Blink every 30 frames (~3 seconds)
                    if (blinkCounter % 28 == 0 || blinkCounter % 28 == 1) {
                        eyeState = EyeState.CLOSED
                        earValue = 0.08f
                    } else {
                        eyeState = EyeState.OPEN
                        earValue = 0.30f
                        if (fatigueScore > 0) fatigueScore -= 1
                    }
                }
                DriverScenario.DROWSY -> {
                    // Frequent heavy eye droops
                    if ((blinkCounter % 20) in 0..7) {
                        eyeState = EyeState.DROWSY
                        earValue = 0.14f
                        fatigueScore = (fatigueScore + 1).coerceAtMost(30)
                    } else {
                        eyeState = EyeState.OPEN
                        earValue = 0.26f
                        if (fatigueScore > 0) fatigueScore -= 1
                    }
                }
                DriverScenario.MICRO_SLEEP -> {
                    // Continuous eye closure: eyes shut for sustained duration
                    eyeState = EyeState.CLOSED
                    earValue = 0.05f
                    closureFrames++
                    fatigueScore = (fatigueScore + 1).coerceAtMost(35)
                }
                DriverScenario.DISTRACTED -> {
                    eyeState = EyeState.UNDETECTED
                    earValue = 0.0f
                    if (fatigueScore > 0) fatigueScore -= 1
                }
            }

            // Update Alert Level
            val newAlertLevel = when {
                fatigueScore >= threshold -> DriverAlertLevel.CRITICAL_ALARM
                fatigueScore >= (threshold * 0.6f) -> DriverAlertLevel.WARNING
                else -> DriverAlertLevel.SAFE
            }

            if (newAlertLevel != alertLevel) {
                alertLevel = newAlertLevel
                if (newAlertLevel == DriverAlertLevel.CRITICAL_ALARM) {
                    if (!isAudioMuted) {
                        soundManager.startEmergencyAlarm()
                    }
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    onIncidentRecorded(
                        DriverIncident(
                            id = System.currentTimeMillis().toString(),
                            timestamp = timeStr,
                            durationSeconds = (closureFrames * 0.1f),
                            maxScore = fatigueScore,
                            severity = DriverAlertLevel.CRITICAL_ALARM
                        )
                    )
                } else {
                    soundManager.stopAlarm()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header & Status Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isMonitoringActive) DriverGreenAwake else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMonitoringActive) "SYSTEM ACTIVE" else "MONITOR PAUSED",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMonitoringActive) DriverGreenAwake else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Driver Drowsiness HUD",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(
                    onClick = {
                        isAudioMuted = !isAudioMuted
                        if (isAudioMuted) soundManager.stopAlarm()
                    },
                    modifier = Modifier.testTag("audio_mute_toggle")
                ) {
                    Icon(
                        imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute/Unmute",
                        tint = if (isAudioMuted) DriverRedAlert else DriverCyan
                    )
                }

                IconButton(
                    onClick = {
                        fatigueScore = 0
                        selectedScenario = DriverScenario.AWAKE
                        soundManager.stopAlarm()
                    },
                    modifier = Modifier.testTag("reset_score_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Score",
                        tint = Color.White
                    )
                }
            }
        }

        // Live Biometric Face Visualizer (OpenCV Haar + CNN Simulation)
        DriverFaceVisualizer(
            eyeState = eyeState,
            alertLevel = alertLevel,
            fatigueScore = fatigueScore,
            threshold = threshold,
            earValue = earValue
        )

        // Drowsiness Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (alertLevel) {
                    DriverAlertLevel.CRITICAL_ALARM -> DriverRedAlert.copy(alpha = 0.2f)
                    DriverAlertLevel.WARNING -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                    DriverAlertLevel.SAFE -> DriverSurfaceDark
                }
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (alertLevel) {
                                DriverAlertLevel.CRITICAL_ALARM -> DriverRedAlert
                                DriverAlertLevel.WARNING -> Color(0xFFF59E0B)
                                DriverAlertLevel.SAFE -> DriverCyan
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (alertLevel) {
                            DriverAlertLevel.CRITICAL_ALARM -> Icons.Default.NotificationsActive
                            DriverAlertLevel.WARNING -> Icons.Default.Warning
                            DriverAlertLevel.SAFE -> Icons.Default.Security
                        },
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (alertLevel) {
                            DriverAlertLevel.CRITICAL_ALARM -> "CRITICAL FATIGUE ALARM"
                            DriverAlertLevel.WARNING -> "DROWSINESS DETECTED"
                            DriverAlertLevel.SAFE -> "DRIVER AWAKE & ATTENTIVE"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (alertLevel) {
                            DriverAlertLevel.CRITICAL_ALARM -> DriverRedAlert
                            DriverAlertLevel.WARNING -> Color(0xFFF59E0B)
                            DriverAlertLevel.SAFE -> DriverGreenAwake
                        }
                    )
                    Text(
                        text = when (alertLevel) {
                            DriverAlertLevel.CRITICAL_ALARM -> "Eyes closed over threshold! Audible alarm sounding."
                            DriverAlertLevel.WARNING -> "Fatigue score rising. Prepare to take a rest."
                            DriverAlertLevel.SAFE -> "Haar cascade tracking face & eye aspect ratio normally."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Fatigue Score Meter
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DriverSurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Real-Time Fatigue Counter",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$fatigueScore / $threshold pts",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (fatigueScore >= threshold) DriverRedAlert else DriverCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { (fatigueScore.toFloat() / (threshold * 1.5f)).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = if (fatigueScore >= threshold) DriverRedAlert else DriverCyan,
                    trackColor = DriverSurfaceVariantDark
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Score Threshold (Alarm Trigger): $threshold",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Score increments if closed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { threshold = it.toInt() },
                    valueRange = 8f..30f,
                    steps = 11,
                    modifier = Modifier.testTag("threshold_slider")
                )
            }
        }

        // Scenario Testing Simulation Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DriverSurfaceDark),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Test Driver States (Simulation)",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Simulate different driver conditions to test the Haar eye classifier and score logic:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedScenario == DriverScenario.AWAKE,
                            onClick = { selectedScenario = DriverScenario.AWAKE },
                            label = { Text("Awake") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scenario_awake"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriverCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = selectedScenario == DriverScenario.BLINKING,
                            onClick = { selectedScenario = DriverScenario.BLINKING },
                            label = { Text("Normal Blinks") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scenario_blinking"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriverCyan,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedScenario == DriverScenario.DROWSY,
                            onClick = { selectedScenario = DriverScenario.DROWSY },
                            label = { Text("Heavy Eyelids") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scenario_drowsy"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B),
                                selectedLabelColor = Color.Black
                            )
                        )
                        FilterChip(
                            selected = selectedScenario == DriverScenario.MICRO_SLEEP,
                            onClick = { selectedScenario = DriverScenario.MICRO_SLEEP },
                            label = { Text("Micro-Sleep (Alarm)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scenario_microsleep"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriverRedAlert,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Live Audio Test Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    soundManager.triggerWarningBeep()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("test_beep_button"),
                colors = ButtonDefaults.buttonColors(containerColor = DriverSurfaceVariantDark)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Test Warning Beep")
            }

            Button(
                onClick = {
                    isMonitoringActive = !isMonitoringActive
                    if (!isMonitoringActive) soundManager.stopAlarm()
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("toggle_monitoring_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoringActive) DriverRedAlert else DriverCyan
                )
            ) {
                Icon(
                    if (isMonitoringActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isMonitoringActive) "Pause HUD" else "Start HUD")
            }
        }
    }
}
