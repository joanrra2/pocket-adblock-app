package io.github.joanrra2.pocketadblock

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import io.github.joanrra2.pocketadblock.platform.AndroidPlatform
import io.github.joanrra2.pocketadblock.ui.AndroidApp

// FragmentActivity (not just ComponentActivity): BiometricPrompt needs it.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val platform = AndroidPlatform(this)
        setContent { AndroidApp(platform) }
    }
}
