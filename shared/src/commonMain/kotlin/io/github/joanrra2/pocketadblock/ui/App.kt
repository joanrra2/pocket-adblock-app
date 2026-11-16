package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.BoardStatus
import io.github.joanrra2.pocketadblock.discovery.Discovery
import io.github.joanrra2.pocketadblock.discovery.FoundBoard
import io.github.joanrra2.pocketadblock.discovery.FoundVia
import io.github.joanrra2.pocketadblock.platform.Platform
import io.github.joanrra2.pocketadblock.store.BoardRepository
import io.github.joanrra2.pocketadblock.store.SavedBoard
import io.github.joanrra2.pocketadblock.wake.PocketWakeLink
import kotlinx.coroutines.launch

/** A board page that is open, logged in. */
data class Session(
    val address: BoardAddress,
    val user: String,
    val password: String,
    val status: BoardStatus,
    /** A new board on its setup Wi-Fi (192.168.4.1): shown with the setup hint, never saved. */
    val setup: Boolean = false,
    val notice: String? = null,
)

/** Who the login dialog is for. */
data class LoginTarget(val address: BoardAddress, val setup: Boolean, val saved: SavedBoard?)

private enum class Screen { Home, Search }

@Composable
fun App(platform: Platform, modifier: Modifier = Modifier) {
    val strings = remember { Strings.forLanguage(platform.languageCode) }
    val client = remember { BoardClient(platform.httpClient(), logFailures = true) }
    val discovery = remember { Discovery(client, platform.mdns, platform::localNetwork) }
    val repo = remember { BoardRepository(platform.settings, platform.secrets) }
    val boards by repo.boards.collectAsState()
    var screen by remember { mutableStateOf(if (repo.boards.value.isEmpty()) Screen.Search else Screen.Home) }
    var session by remember { mutableStateOf<Session?>(null) }
    var loginFor by remember { mutableStateOf<LoginTarget?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun unlock(board: SavedBoard): Boolean =
        !board.requireUnlock || !platform.canAuthenticate() ||
            platform.authenticate(strings.unlockTitle.fill(board.name), strings.unlockSubtitle, strings.cancel)

    fun openSaved(board: SavedBoard) {
        notice = null
        scope.launch {
            if (!unlock(board)) return@launch
            val password = repo.password(board.id)
            if (password == null) {
                loginFor = LoginTarget(board.address, setup = false, saved = board)
                return@launch
            }
            when (val r = client.login(board.address, board.user, password)) {
                is BoardClient.Login.Ok -> session = Session(board.address, board.user, password, r.status)
                BoardClient.Login.WrongCredentials -> {
                    notice = strings.loginAgain
                    loginFor = LoginTarget(board.address, setup = false, saved = board)
                }
                is BoardClient.Login.Unreachable -> notice = strings.unreachable.fill(r.reason)
                BoardClient.Login.NotBoard, BoardClient.Login.NotLocal -> notice = strings.notABoard
            }
        }
    }

    fun connectFound(found: FoundBoard) {
        notice = null
        val saved = boards.firstOrNull { it.address == found.address }
        if (saved != null) openSaved(saved)
        else loginFor = LoginTarget(found.address, setup = found.via == FoundVia.SetupAccessPoint, saved = null)
    }

    fun loggedIn(target: LoginTarget, result: LoginResult) {
        loginFor = null
        val st = result.status
        var pageNotice: String? = null
        if (!target.setup) {
            val name = st.hostname.ifEmpty { target.address.host }
            val existing = target.saved ?: repo.find(target.address, st.hostname)
            // A factory password is never stored: the board's own banner asks to change it.
            val keep = result.remember && !st.defaultPassword
            if (result.remember && st.defaultPassword) pageNotice = strings.defaultPasswordNotice
            repo.save(
                SavedBoard(
                    id = existing?.id ?: repo.newId(name),
                    name = name,
                    host = target.address.host,
                    port = target.address.port,
                    user = result.user,
                    requireUnlock = keep && result.requireUnlock,
                ),
                password = if (keep) result.password else null,
            )
            screen = Screen.Home
        }
        session = Session(target.address, result.user, result.password, st, setup = target.setup, notice = pageNotice)
    }

    PocketTheme {
        Surface(modifier = modifier.fillMaxSize(), color = Tokens.bg) {
            val current = session
            when {
                current != null -> BoardScreen(
                    session = current,
                    strings = strings,
                    onBack = { session = null },
                    onAuthRejected = {
                        session = null
                        notice = strings.loginAgain
                        val saved = repo.find(current.address, current.status.hostname)
                        loginFor = LoginTarget(current.address, current.setup, saved)
                    },
                    onOpenExternal = { url ->
                        if (PocketWakeLink.isSetupLink(url)) platform.openPocketWake(url) else platform.openInBrowser(url)
                    },
                )
                screen == Screen.Search -> BoardsScreen(
                    strings = strings,
                    client = client,
                    discovery = discovery,
                    notice = notice,
                    savedAddresses = boards.map { it.address }.toSet(),
                    onBack = if (boards.isNotEmpty()) ({ screen = Screen.Home }) else null,
                    onConnect = ::connectFound,
                )
                else -> HomeScreen(
                    strings = strings,
                    client = client,
                    discovery = discovery,
                    repo = repo,
                    notice = notice,
                    unlock = ::unlock,
                    onOpen = ::openSaved,
                    onAdd = { notice = null; screen = Screen.Search },
                    openPocketWake = platform::openPocketWake,
                )
            }
            loginFor?.let { target ->
                LoginDialog(
                    target = target,
                    strings = strings,
                    client = client,
                    canAuthenticate = platform.canAuthenticate(),
                    onDismiss = { loginFor = null },
                    onLoggedIn = { loggedIn(target, it) },
                )
            }
        }
    }
}
