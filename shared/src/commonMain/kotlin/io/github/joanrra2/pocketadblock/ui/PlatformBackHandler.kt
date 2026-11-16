package io.github.joanrra2.pocketadblock.ui

import androidx.compose.runtime.Composable

/** The system back gesture / button (Android). Does nothing where there is none. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
