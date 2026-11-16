package io.github.joanrra2.pocketadblock.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.joanrra2.pocketadblock.board.BoardAddress

/** Placeholder until milestone M2 adds WKWebView with the saved credentials. */
@Composable
actual fun BoardWebView(
    address: BoardAddress,
    user: String,
    password: String,
    reloadKey: Int,
    onAuthRejected: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    Text(address.url(), modifier = modifier)
}
