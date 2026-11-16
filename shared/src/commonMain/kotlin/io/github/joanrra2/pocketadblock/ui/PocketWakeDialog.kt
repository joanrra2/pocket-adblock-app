package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.store.SavedBoard
import io.github.joanrra2.pocketadblock.wake.PocketWakeLink

/**
 * Hands this board's broker settings to the Pocket Wake app, with the same setup
 * link as the board's QR code. Works on any firmware: it only reads /api/settings.
 */
@Composable
fun PocketWakeDialog(
    board: SavedBoard,
    password: String?,
    strings: Strings,
    client: BoardClient,
    openPocketWake: (String) -> Boolean,
    onDone: (String?) -> Unit,
) {
    var settings by remember { mutableStateOf<BoardClient.MqttSettings?>(null) }
    var loading by remember { mutableStateOf(password != null) }
    var phoneUser by remember { mutableStateOf("") }

    LaunchedEffect(board.id) {
        if (password != null) settings = client.mqttSettings(board.address, board.user, password)
        loading = false
    }
    val broker = settings?.let { PocketWakeLink.parseBroker(it.mqttUri) }

    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Tokens.s1,
        title = { Text(strings.pwTitle, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.pwIntro, color = Tokens.muted, fontSize = 13.sp)
                when {
                    password == null -> Text(strings.pwNeedsLogin, color = Tokens.warn, fontSize = 13.sp)
                    loading -> Text(strings.pwLoading, color = Tokens.muted, fontSize = 13.sp)
                    settings == null -> Text(strings.unreachable.fill(board.address.label), color = Tokens.bad, fontSize = 13.sp)
                    broker == null -> Text(strings.pwNoBroker, color = Tokens.warn, fontSize = 13.sp)
                    else -> {
                        Column {
                            Text(strings.pwBroker, color = Tokens.muted, fontSize = 11.sp)
                            Text("${broker.host} · ${broker.port}${if (broker.tls) " · TLS" else ""}", color = Tokens.text, fontSize = 14.sp)
                        }
                        Column {
                            Text(strings.pwPrefix, color = Tokens.muted, fontSize = 11.sp)
                            Text(settings!!.mqttPrefix.ifEmpty { "sinkhole" }, color = Tokens.text, fontSize = 14.sp)
                        }
                        OutlinedTextField(
                            value = phoneUser,
                            onValueChange = { phoneUser = it },
                            label = { Text(strings.pwUser) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().testTag("pw-user"),
                        )
                        Text(strings.pwNote, color = Tokens.muted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            val s = settings
            if (broker != null && s != null) {
                Button(
                    onClick = {
                        val link = PocketWakeLink.build(s.mqttUri, s.mqttPrefix, phoneUser) ?: return@Button
                        openPocketWake(link)
                        onDone(strings.pwOpened)
                    },
                    modifier = Modifier.testTag("pw-open"),
                ) { Text(strings.pwOpen) }
            }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text(strings.cancel) } },
    )
}
