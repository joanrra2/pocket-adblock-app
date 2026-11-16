package io.github.joanrra2.pocketadblock.ui

import androidx.compose.runtime.Composable

/** iPhone uses the on-screen back buttons; edge-swipe back can come in M2. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
