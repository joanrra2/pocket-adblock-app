package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.BoardStatus
import io.github.joanrra2.pocketadblock.discovery.Discovery
import io.github.joanrra2.pocketadblock.discovery.FoundBoard
import io.github.joanrra2.pocketadblock.platform.Platform

/** A board the user is logged in to. Credentials live only in memory in this milestone. */
data class Session(
    val board: FoundBoard,
    val user: String,
    val password: String,
    val status: BoardStatus,
) {
    val address: BoardAddress get() = board.address
}

@Composable
fun App(platform: Platform, modifier: Modifier = Modifier) {
    val strings = remember { Strings.forLanguage(platform.languageCode) }
    val client = remember { BoardClient(platform.httpClient()) }
    val discovery = remember { Discovery(client, platform.mdns, platform::localNetwork) }
    var loggingIn by remember { mutableStateOf<FoundBoard?>(null) }
    var session by remember { mutableStateOf<Session?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    PocketTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = Tokens.bg,
        ) {
            val current = session
            if (current == null) {
                BoardsScreen(
                    strings = strings,
                    client = client,
                    discovery = discovery,
                    notice = notice,
                    onConnect = { notice = null; loggingIn = it },
                )
            } else {
                BoardScreen(
                    session = current,
                    strings = strings,
                    onBack = { session = null },
                    onAuthRejected = {
                        session = null
                        notice = strings.loginAgain
                        loggingIn = current.board
                    },
                    onOpenExternal = platform::openInBrowser,
                )
            }
            loggingIn?.let { board ->
                LoginDialog(
                    board = board,
                    strings = strings,
                    client = client,
                    onDismiss = { loggingIn = null },
                    onLoggedIn = { s ->
                        loggingIn = null
                        session = s
                    },
                )
            }
        }
    }
}
