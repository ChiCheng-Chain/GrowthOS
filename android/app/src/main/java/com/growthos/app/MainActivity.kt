package com.growthos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.growthos.app.ui.navigation.GrowthOSApp
import com.growthos.app.ui.theme.GrowthOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrowthOSRoot()
        }
    }
}

@Composable
private fun GrowthOSRoot() {
    GrowthOSTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GrowthOSApp()
        }
    }
}
