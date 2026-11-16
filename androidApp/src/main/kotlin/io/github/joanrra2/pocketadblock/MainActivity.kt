package io.github.joanrra2.pocketadblock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.joanrra2.pocketadblock.platform.AndroidPlatform
import io.github.joanrra2.pocketadblock.ui.AndroidApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val platform = AndroidPlatform(applicationContext)
        setContent { AndroidApp(platform) }
    }
}
