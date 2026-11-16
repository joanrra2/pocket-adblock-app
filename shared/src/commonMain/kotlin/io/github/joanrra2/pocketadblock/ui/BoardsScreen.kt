package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.discovery.Discovery
import io.github.joanrra2.pocketadblock.discovery.DiscoveryState
import io.github.joanrra2.pocketadblock.discovery.FoundBoard
import io.github.joanrra2.pocketadblock.discovery.FoundVia
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

@Composable
fun BoardsScreen(
    strings: Strings,
    client: BoardClient,
    discovery: Discovery,
    notice: String?,
    savedAddresses: Set<BoardAddress>,
    onBack: (() -> Unit)?,
    onConnect: (FoundBoard) -> Unit,
) {
    PlatformBackHandler(enabled = onBack != null) { onBack?.invoke() }
    var state by remember { mutableStateOf(DiscoveryState(running = true)) }
    var run by remember { mutableIntStateOf(0) }
    val typed = remember { mutableStateListOf<FoundBoard>() }
    LaunchedEffect(run) { discovery.search().conflate().collect { state = it } }

    var address by remember { mutableStateOf("") }
    var addressError by remember { mutableStateOf<String?>(null) }
    var trying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun tryTyped() {
        val a = BoardAddress.parse(address)
        addressError = when {
            a == null -> strings.notAnAddress
            !a.isLocal -> strings.notLocal
            else -> null
        }
        if (a == null || addressError != null) return
        trying = true
        scope.launch {
            val probe = client.probe(a, 3000)
            trying = false
            if (probe == BoardClient.Probe.Board) {
                val board = FoundBoard(a, FoundVia.Typed)
                if (typed.none { it.address == a }) typed += board
                onConnect(board)
            } else {
                addressError = strings.notABoard
            }
        }
    }

    val boards = state.boards + typed.filter { t -> state.boards.none { it.address == t.address } }

    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("search-back")) { Text("‹", fontSize = 24.sp) }
            }
            Text(
                if (onBack != null) strings.searchTitle else strings.appName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.text,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val net = state.network
            Text(
                text = when {
                    state.running && net != null -> strings.searchingOn.fill(net)
                    state.running -> strings.searching
                    else -> strings.searchDone
                },
                color = Tokens.muted,
                fontSize = 13.sp,
                modifier = Modifier.testTag("search-status"),
            )
            if (state.running && state.sweepTotal > 0) {
                LinearProgressIndicator(
                    progress = { state.swept.toFloat() / state.sweepTotal },
                    modifier = Modifier.fillMaxWidth(),
                    color = Tokens.accent,
                    trackColor = Tokens.s2,
                )
            }
            if (!state.running && net == null) Text(strings.noNetwork, color = Tokens.warn, fontSize = 13.sp)
            notice?.let { Text(it, color = Tokens.warn, fontSize = 13.sp) }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(boards, key = { it.address.label }) { BoardCard(it, it.address in savedAddresses, strings, onConnect) }
            if (!state.running && boards.isEmpty()) {
                item { Text(strings.foundNone, color = Tokens.muted, fontSize = 13.sp) }
            }
        }

        Text(strings.typeAddress, color = Tokens.muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; addressError = null },
                placeholder = { Text(strings.addressHint) },
                singleLine = true,
                isError = addressError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { tryTyped() }),
                modifier = Modifier.weight(1f).testTag("address-field"),
            )
            Button(onClick = { tryTyped() }, enabled = !trying, modifier = Modifier.testTag("address-try")) {
                Text(if (trying) "…" else strings.tryAddress)
            }
        }
        addressError?.let { Text(it, color = Tokens.bad, fontSize = 12.sp) }
        OutlinedButton(
            onClick = { run++ },
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth().testTag("search-again"),
        ) { Text(strings.searchAgain) }
    }
}

@Composable
private fun BoardCard(board: FoundBoard, saved: Boolean, strings: Strings, onConnect: (FoundBoard) -> Unit) {
    val setup = board.via == FoundVia.SetupAccessPoint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.s1, RoundedCornerShape(14.dp))
            .border(1.dp, if (setup) Tokens.warn else Tokens.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("board-${board.address.label}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Dot(if (setup) Tokens.warn else Tokens.ok)
        Column(Modifier.weight(1f)) {
            Text(board.address.label, color = Tokens.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                when (board.via) {
                    FoundVia.Mdns -> strings.viaMdns
                    FoundVia.Sweep -> strings.viaSweep
                    FoundVia.SetupAccessPoint -> strings.viaSetup
                    FoundVia.Typed -> strings.viaTyped
                } + if (saved) " · ${strings.savedTag}" else "",
                color = Tokens.muted,
                fontSize = 12.sp,
            )
        }
        Button(onClick = { onConnect(board) }, modifier = Modifier.testTag("connect-${board.address.label}")) {
            Text(strings.connect)
        }
    }
}

@Composable
fun Dot(color: Color) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
}
