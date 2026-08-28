package com.growthos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.growthos.app.ui.navigation.GrowthOSApp
import com.growthos.app.ui.theme.GrowthOSTheme
import com.growthos.app.ui.theme.GrowthThemePreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 主题冷读(DataStore 无同步读):毫秒级一次性阻塞,换非默认主题首帧即正确,
        // 不闪默认色(设计 D3)。读不到(异常/null)回退默认。
        val initialTheme = runBlocking {
            try {
                (application as GrowthOSApp).container.themeStore.flow.first()
            } catch (t: Throwable) {
                null
            }
        } ?: GrowthThemePreset.DEFAULT
        setContent {
            GrowthOSRoot(initialTheme)
        }
    }
}

@Composable
private fun GrowthOSRoot(initialTheme: GrowthThemePreset) {
    val container = (LocalContext.current.applicationContext as GrowthOSApp).container
    val preset: GrowthThemePreset by container.themeStore.flow
        .map { it ?: initialTheme }
        .collectAsStateWithLifecycle(initialValue = initialTheme)
    GrowthOSTheme(preset = preset) {
        Surface(modifier = Modifier.fillMaxSize()) {
            GrowthOSApp()
        }
    }
}
