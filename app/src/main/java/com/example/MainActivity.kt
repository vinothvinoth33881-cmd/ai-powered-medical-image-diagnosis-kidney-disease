package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.audio.AlarmSoundManager
import com.example.model.DriverIncident
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PythonProjectScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DriverCyan
import com.example.ui.theme.DriverNavyDark
import com.example.ui.theme.DriverSurfaceDark
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var soundManager: AlarmSoundManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = AlarmSoundManager(this)
        soundManager = manager

        setContent {
            MyApplicationTheme(darkTheme = true) {
                MainAppContent(soundManager = manager)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        soundManager?.stopAlarm()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager?.release()
    }
}

@Composable
fun MainAppContent(soundManager: AlarmSoundManager) {
    var selectedScreenIndex by remember { mutableIntStateOf(0) }
    val incidentHistory = remember { mutableStateListOf<DriverIncident>() }

    DisposableEffect(Unit) {
        onDispose {
            soundManager.stopAlarm()
        }
    }

    val navItems = listOf(
        Triple("Monitor HUD", Icons.Default.RemoveRedEye, "nav_monitor"),
        Triple("Python Project", Icons.Default.Code, "nav_python"),
        Triple("Analytics", Icons.Default.Analytics, "nav_analytics"),
        Triple("Settings", Icons.Default.Settings, "nav_settings")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DriverNavyDark,
        bottomBar = {
            NavigationBar(
                containerColor = DriverSurfaceDark,
                contentColor = DriverCyan
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedScreenIndex == index,
                        onClick = { selectedScreenIndex = index },
                        icon = { Icon(item.second, contentDescription = item.first) },
                        label = { Text(item.first) },
                        modifier = Modifier.testTag(item.third),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = DriverCyan,
                            indicatorColor = DriverCyan,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedScreenIndex) {
            0 -> MonitorScreen(
                soundManager = soundManager,
                onIncidentRecorded = { incidentHistory.add(0, it) },
                modifier = modifier
            )
            1 -> PythonProjectScreen(modifier = modifier)
            2 -> AnalyticsScreen(incidentHistory = incidentHistory, modifier = modifier)
            3 -> SettingsScreen(modifier = modifier)
        }
    }
}
