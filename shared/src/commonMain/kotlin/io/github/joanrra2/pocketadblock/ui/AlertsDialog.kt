package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import io.github.joanrra2.pocketadblock.notify.NtfyLink

/**
 * Phone alerts (GET /api/notify, firmware 0.6 Pro builds): subscribes the ntfy app to the
 * board's topic in one tap, shows whether the last alert went out, and sends a test.
 * Setting alerts up stays in the board's own web page.
 */
@Composable
fun AlertsDialog(
    title: String,
    strings: Strings,
    load: suspend () -> BoardClient.Notify?,
    openNtfy: (appLink: String, webLink: String) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var notify by remember { mutableStateOf<BoardClient.Notify?>(null) }
    LaunchedEffect(Unit) {
        notify = load()
        loading = false
    }
    val n = notify

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tokens.s1,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.alertsIntro, color = Tokens.muted, fontSize = 13.sp)
                when {
                    loading -> Text(strings.checking, color = Tokens.muted, fontSize = 13.sp)
                    n == null -> Text(strings.alertsUnavailable, color = Tokens.bad, fontSize = 13.sp)
                    !n.anyConfigured -> Text(strings.alertsNotSet, color = Tokens.warn, fontSize = 13.sp)
                    else -> {
                        if (n.ntfy.configured) {
                            Column {
                                Text(strings.alertsNtfy, color = Tokens.muted, fontSize = 11.sp)
                                Text(NtfyLink.label(n.ntfyServer, n.ntfyTopic), color = Tokens.text, fontSize = 14.sp)
                                Text(channelLine(n.ntfy, strings), color = channelColor(n.ntfy), fontSize = 12.sp)
                            }
                            if (n.ntfyTokenSet) Text(strings.alertsTokenNote, color = Tokens.muted, fontSize = 12.sp)
                            val app = NtfyLink.subscribe(n.ntfyServer, n.ntfyTopic)
                            val web = NtfyLink.web(n.ntfyServer, n.ntfyTopic)
                            if (app != null && web != null) {
                                Button(onClick = { openNtfy(app, web) }, modifier = Modifier.testTag("alerts-subscribe")) {
                                    Text(strings.alertsSubscribe)
                                }
                            }
                        }
                        if (n.telegram.configured) {
                            Column {
                                Text(strings.alertsTelegram, color = Tokens.muted, fontSize = 11.sp)
                                Text(strings.alertsTelegramHere, color = Tokens.text, fontSize = 14.sp)
                                Text(channelLine(n.telegram, strings), color = channelColor(n.telegram), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (n != null && n.anyConfigured) {
                TextButton(onClick = onTest, modifier = Modifier.testTag("alerts-test")) { Text(strings.alertsTest) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

/** "Last alert sent 5 min ago", "Last alert failed 2 min ago: HTTP 401" or "No alert sent…". */
internal fun channelLine(c: BoardClient.Notify.Channel, strings: Strings): String = when {
    c.ago < 0 -> strings.alertsNoneYet
    c.ok -> strings.alertsLastOk.fill(formatDuration(c.ago))
    else -> strings.alertsLastFailed.fill(formatDuration(c.ago), c.error.ifEmpty { "?" })
}

private fun channelColor(c: BoardClient.Notify.Channel) = if (c.ago >= 0 && !c.ok) Tokens.bad else Tokens.muted
