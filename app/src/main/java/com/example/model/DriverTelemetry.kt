package com.example.model

enum class EyeState(val label: String) {
    OPEN("Open"),
    CLOSED("Closed"),
    DROWSY("Drowsy (Heavy)"),
    UNDETECTED("No Eyes Detected")
}

enum class DriverAlertLevel {
    SAFE,
    WARNING,
    CRITICAL_ALARM
}

data class DriverIncident(
    val id: String,
    val timestamp: String,
    val durationSeconds: Float,
    val maxScore: Int,
    val severity: DriverAlertLevel
)

data class TripStatistics(
    val tripDurationSeconds: Long = 0,
    val totalBlinks: Int = 0,
    val drowsinessIncidentsCount: Int = 0,
    val peakFatigueScore: Int = 0,
    val incidentHistory: List<DriverIncident> = emptyList()
)
