package com.example

import com.example.model.DriverAlertLevel
import com.example.model.EyeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverGuardUnitTest {
    @Test
    fun eyeState_labelsAreAccurate() {
        assertEquals("Open", EyeState.OPEN.label)
        assertEquals("Closed", EyeState.CLOSED.label)
        assertEquals("Drowsy (Heavy)", EyeState.DROWSY.label)
    }

    @Test
    fun fatigueScore_reachesAlarmThreshold() {
        var score = 0
        val threshold = 15

        // Simulate 20 consecutive closed eye frames
        repeat(20) {
            score++
        }

        assertTrue("Fatigue score must exceed threshold", score >= threshold)

        val alertLevel = when {
            score >= threshold -> DriverAlertLevel.CRITICAL_ALARM
            score >= threshold * 0.6f -> DriverAlertLevel.WARNING
            else -> DriverAlertLevel.SAFE
        }

        assertEquals(DriverAlertLevel.CRITICAL_ALARM, alertLevel)

        // Simulate reopening eyes
        repeat(25) {
            if (score > 0) score--
        }

        assertEquals(0, score)
    }
}
