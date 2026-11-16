package io.github.joanrra2.pocketadblock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The board web UI's own tokens (www/index.html), dark theme. */
object Tokens {
    val bg = Color(0xFF0B0F15)
    val s1 = Color(0xFF121822)
    val s2 = Color(0xFF19212D)
    val line = Color(0xFF232D3B)
    val text = Color(0xFFE7EDF5)
    val muted = Color(0xFF8A97A8)
    val accent = Color(0xFF7C8CFF)
    val ok = Color(0xFF34D399)
    val bad = Color(0xFFFB7185)
    val warn = Color(0xFFFBBF24)
}

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tokens.accent,
            onPrimary = Color.White,
            background = Tokens.bg,
            onBackground = Tokens.text,
            surface = Tokens.s1,
            onSurface = Tokens.text,
            surfaceVariant = Tokens.s2,
            onSurfaceVariant = Tokens.muted,
            surfaceContainer = Tokens.s1,
            surfaceContainerHigh = Tokens.s2,
            surfaceContainerHighest = Tokens.s2,
            outline = Tokens.line,
            outlineVariant = Tokens.line,
            error = Tokens.bad,
        ),
        content = content,
    )
}
