package com.boomstream.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.boomstream.example.ui.MainScreen
import com.boomstream.example.ui.PlayerDemoScreen

// AppCompatActivity required so MediaRouteButton (Cast demo) picks up the correct theme attrs.
class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                val isLandscape =
                    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

                if (isLandscape) {
                    // In landscape, hide the tab bar: MainScreen goes fullscreen immersive,
                    // PlayerDemoScreen shows its fullscreen player.
                    when (selectedTab) {
                        0 -> MainScreen(vm = vm)
                        else -> PlayerDemoScreen(vm = vm)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TabRow(selectedTabIndex = selectedTab) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Медиа") },
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Player API") },
                            )
                        }
                        when (selectedTab) {
                            0 -> MainScreen(vm = vm)
                            else -> PlayerDemoScreen(vm = vm)
                        }
                    }
                }
            }
        }
    }
}
