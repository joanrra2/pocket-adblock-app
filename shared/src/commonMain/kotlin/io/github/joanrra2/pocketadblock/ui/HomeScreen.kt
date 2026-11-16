package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.BoardStatus
import io.github.joanrra2.pocketadblock.discovery.Discovery
import io.github.joanrra2.pocketadblock.store.BoardRepository
import io.github.joanrra2.pocketadblock.store.Relocator
import io.github.joanrra2.pocketadblock.store.SavedBoard
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch

/** What a board card shows. */
sealed interface CardState {
    data object Checking : CardState
    data class Online(val status: BoardStatus) : CardState
    /** Answers, but there is no saved password to read its state. */
    data object Locked : CardState
    data object NeedsLogin : CardState
    data object Locating : CardState
    data object Offline : CardState
}

private const val REFRESH_MS = 10_000L

@Composable
fun HomeScreen(
    strings: Strings,
    client: BoardClient,
    discovery: Discovery,
    repo: BoardRepository,
    notice: String?,
    unlock: suspend (SavedBoard) -> Boolean,
    onOpen: (SavedBoard) -> Unit,
    onAdd: () -> Unit,
    openPocketWake: (String) -> Boolean,
) {
    val boards by repo.boards.collectAsState()
    val states = remember { mutableStateMapOf<String, CardState>() }
    var relocationTried by remember { mutableStateOf(false) }
    var pauseFor by remember { mutableStateOf<SavedBoard?>(null) }
    var forgetFor by remember { mutableStateOf<SavedBoard?>(null) }
    var menuFor by remember { mutableStateOf<SavedBoard?>(null) }
    var wakeFor by remember { mutableStateOf<SavedBoard?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh(b: SavedBoard) {
        val password = repo.password(b.id)
        states[b.id] = if (password == null) {
            if (client.probe(b.address) == BoardClient.Probe.Board) CardState.Locked else CardState.Offline
        } else {
            when (val r = client.login(b.address, b.user, password)) {
                is BoardClient.Login.Ok -> CardState.Online(r.status)
                BoardClient.Login.WrongCredentials -> CardState.NeedsLogin
                else -> CardState.Offline
            }
        }
    }

    // Poll every board while this screen is shown. A board that stopped answering may
    // have a new address from DHCP: search once and match it by its hostname.
    LaunchedEffect(boards) {
        while (true) {
            coroutineScope { boards.forEach { b -> launch { refresh(b) } } }
            val missing = boards.filter { states[it.id] == CardState.Offline }
                .mapNotNull { b -> repo.password(b.id)?.let { Relocator.Missing(b, it) } }
            if (missing.isNotEmpty() && !relocationTried) {
                relocationTried = true
                missing.forEach { states[it.board.id] = CardState.Locating }
                val found = discovery.search().last().boards.map { it.address }
                val moved = Relocator(client).match(found, missing, boards.map { it.address }.toSet())
                missing.filter { it.board.id !in moved }.forEach { states[it.board.id] = CardState.Offline }
                // Saving a new address changes `boards`, which restarts this loop.
                moved.forEach { (id, address) -> repo.updateAddress(id, address) }
                if (moved.isNotEmpty()) return@LaunchedEffect
            }
            delay(REFRESH_MS)
        }
    }

    fun act(b: SavedBoard, change: BoardClient.Blocking) {
        scope.launch {
            if (!unlock(b)) return@launch
            val password = repo.password(b.id) ?: return@launch
            client.setBlocking(b.address, b.user, password, change)?.let { message = strings.actionFailed.fill(it) }
            refresh(b)
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                strings.boardsTitle,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.text,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            (message ?: notice)?.let { Text(it, color = Tokens.warn, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            if (boards.isEmpty()) Text(strings.noBoards, color = Tokens.muted, fontSize = 14.sp)
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(boards, key = { it.id }) { b ->
                    BoardCard(
                        board = b,
                        state = states[b.id] ?: CardState.Checking,
                        strings = strings,
                        onOpen = { onOpen(b) },
                        onPause = { pauseFor = b },
                        onResume = { act(b, BoardClient.Blocking.On) },
                        onMenu = { menuFor = b },
                    )
                }
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).testTag("add-board"),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) { Text(strings.add, fontWeight = FontWeight.Bold) }
    }

    pauseFor?.let { b ->
        PauseDialog(strings, onDismiss = { pauseFor = null }) { change ->
            pauseFor = null
            act(b, change)
        }
    }
    menuFor?.let { b ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            containerColor = Tokens.s1,
            title = { Text(b.name, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { menuFor = null; wakeFor = b }, modifier = Modifier.testTag("menu-pocketwake")) {
                        Text(strings.pwMenu, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { menuFor = null; forgetFor = b }, modifier = Modifier.testTag("menu-forget")) {
                        Text(strings.forget, color = Tokens.bad, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { menuFor = null }) { Text(strings.cancel) } },
        )
    }
    wakeFor?.let { b ->
        PocketWakeDialog(
            board = b,
            password = repo.password(b.id),
            strings = strings,
            client = client,
            openPocketWake = openPocketWake,
            onDone = { done -> wakeFor = null; done?.let { message = it } },
        )
    }
    forgetFor?.let { b ->
        AlertDialog(
            onDismissRequest = { forgetFor = null },
            containerColor = Tokens.s1,
            title = { Text(b.name, fontSize = 17.sp) },
            text = { Text(strings.forgetText, fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    repo.remove(b.id)
                    states.remove(b.id)
                    forgetFor = null
                }) { Text(strings.forget, color = Tokens.bad) }
            },
            dismissButton = { TextButton(onClick = { forgetFor = null }) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun BoardCard(
    board: SavedBoard,
    state: CardState,
    strings: Strings,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onMenu: () -> Unit,
) {
    val es = strings === Strings.ES
    val online = state as? CardState.Online
    val st = online?.status
    val (dot, tag, tagColor) = when (state) {
        is CardState.Online -> Triple(
            if (st!!.dns.blocking) Tokens.ok else Tokens.warn,
            if (st.firmware.available && st.firmware.latest.isNotEmpty()) strings.updateTag.fill(st.firmware.latest) else strings.online,
            if (st.firmware.available) Tokens.warn else Tokens.ok,
        )
        CardState.Locked -> Triple(Tokens.ok, strings.online, Tokens.ok)
        CardState.NeedsLogin -> Triple(Tokens.warn, strings.online, Tokens.warn)
        CardState.Locating -> Triple(Tokens.line, strings.lookingForIt, Tokens.muted)
        CardState.Offline -> Triple(Tokens.line, strings.offline, Tokens.muted)
        CardState.Checking -> Triple(Tokens.line, strings.checking, Tokens.muted)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.s1, RoundedCornerShape(16.dp))
            .border(1.dp, Tokens.line, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)
            .testTag("saved-${board.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(dot)
            Text(board.name, color = Tokens.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Tag(tag, tagColor)
            Box(Modifier.weight(1f))
            TextButton(onClick = onMenu, modifier = Modifier.testTag("menu-${board.id}")) { Text("⋯", color = Tokens.muted, fontSize = 18.sp) }
        }
        Text(
            listOfNotNull(board.address.label, st?.edition?.ifEmpty { null }, st?.version?.ifEmpty { null }?.let { "v$it" })
                .joinToString(" · "),
            color = Tokens.muted,
            fontSize = 12.sp,
        )
        if (st != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 2.dp)) {
                Stat(strings.blocked.replaceFirstChar { it.uppercase() }, "${formatDecimal(st.blockedPercent, es)} %")
                Stat(strings.queries.replaceFirstChar { it.uppercase() }, formatInt(st.queries, es))
                Stat(strings.listSize.replaceFirstChar { it.uppercase() }, formatInt(st.blocklist.count, es))
            }
            if (!st.dns.blocking) {
                Text(
                    if (st.dns.resumeIn > 0) strings.pausedFor.fill((st.dns.resumeIn + 59) / 60) else strings.pausedOff,
                    color = Tokens.warn,
                    fontSize = 12.sp,
                )
            }
        }
        if (state == CardState.NeedsLogin) Text(strings.needsLogin, color = Tokens.warn, fontSize = 12.sp)
        if (state != CardState.Offline && state != CardState.Locating && state != CardState.Checking) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 10.dp, top = 4.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f).testTag("open-${board.id}")) { Text(strings.open) }
                if (st != null) {
                    if (st.dns.blocking) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).testTag("pause-${board.id}")) { Text(strings.pause) }
                    } else {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f).testTag("resume-${board.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = Tokens.s2, contentColor = Tokens.ok),
                        ) { Text(strings.resume) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, color = Tokens.muted, fontSize = 11.sp)
        Text(value, color = Tokens.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.background(Tokens.s2, RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun PauseDialog(strings: Strings, onDismiss: () -> Unit, onChoose: (BoardClient.Blocking) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tokens.s1,
        title = { Text(strings.pauseTitle, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(strings.pauseText, color = Tokens.muted, fontSize = 13.sp)
                for ((label, change) in listOf(
                    strings.pause5 to BoardClient.Blocking.Pause(5),
                    strings.pause30 to BoardClient.Blocking.Pause(30),
                    strings.pause60 to BoardClient.Blocking.Pause(60),
                    strings.pauseOff to BoardClient.Blocking.Off,
                )) {
                    OutlinedButton(onClick = { onChoose(change) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
