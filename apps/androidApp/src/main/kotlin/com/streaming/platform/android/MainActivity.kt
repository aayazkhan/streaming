package com.streaming.platform.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.streaming.platform.android.ui.navigation.StreamingNavHost
import com.streaming.platform.android.ui.theme.StreamingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamingTheme {
                StreamingNavHost()
            }
        }
    }
}
