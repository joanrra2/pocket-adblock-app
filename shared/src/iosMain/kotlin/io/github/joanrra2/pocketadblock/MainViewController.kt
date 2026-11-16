package io.github.joanrra2.pocketadblock

import androidx.compose.ui.window.ComposeUIViewController
import io.github.joanrra2.pocketadblock.platform.IosPlatform
import io.github.joanrra2.pocketadblock.ui.App

/** Entry point for the iPhone app (the Xcode project arrives in milestone M2). */
@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController { App(IosPlatform()) }
