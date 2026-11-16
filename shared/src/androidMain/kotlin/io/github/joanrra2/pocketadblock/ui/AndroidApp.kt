package io.github.joanrra2.pocketadblock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import io.github.joanrra2.pocketadblock.platform.Platform

/** [App] with test tags exposed as resource ids, so UI tests and adb tools can find the fields. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AndroidApp(platform: Platform) {
    App(platform, Modifier.semantics { testTagsAsResourceId = true })
}
