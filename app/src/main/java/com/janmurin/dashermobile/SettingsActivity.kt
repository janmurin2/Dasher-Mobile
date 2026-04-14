package com.janmurin.dashermobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.janmurin.dashermobile.ui.SettingsScreen
import com.janmurin.dashermobile.ui.theme.DasherMobileTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DasherMobileTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

