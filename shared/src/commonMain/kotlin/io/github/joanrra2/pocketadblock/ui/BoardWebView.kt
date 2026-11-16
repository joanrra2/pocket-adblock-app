package io.github.joanrra2.pocketadblock.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.joanrra2.pocketadblock.board.BoardAddress

/**
 * The board's own web page, logged in with the saved user and password.
 * Links that leave the board open in the system browser.
 *
 * @param reloadKey change it to reload the page.
 * @param onAuthRejected called when the board refuses the credentials.
 * @param onExit called when the user goes back past the page's first view.
 */
@Composable
expect fun BoardWebView(
    address: BoardAddress,
    user: String,
    password: String,
    reloadKey: Int,
    onAuthRejected: () -> Unit,
    onOpenExternal: (String) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
)
