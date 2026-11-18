package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.board.BoardClient

/**
 * The board's restart history (GET /api/restarts, firmware 0.6): why it started each
 * time, how long it ran, and whether its clock is set. Crashes, watchdog resets and
 * power dips are shown in red.
 */
@Composable
fun RestartsDialog(
    title: String,
    strings: Strings,
    load: suspend () -> BoardClient.Restarts?,
    onDismiss: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var restarts by remember { mutableStateOf<BoardClient.Restarts?>(null) }
    LaunchedEffect(Unit) {
        restarts = load()
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tokens.s1,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            val r = restarts
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                when {
                    loading -> Text(strings.checking, color = Tokens.muted, fontSize = 13.sp)
                    r == null -> Text(strings.restartsUnavailable, color = Tokens.muted, fontSize = 13.sp)
                    else -> {
                        Text(
                            if (r.time > 0) strings.clockSet else strings.clockNotSet,
                            color = if (r.time > 0) Tokens.muted else Tokens.warn,
                            fontSize = 12.sp,
                        )
                        r.items.forEachIndexed { i, item ->
                            Column {
                                Text(
                                    item.why.ifEmpty { "?" },
                                    color = if (item.unexpected) Tokens.bad else Tokens.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                                Text(restartLine(i, item, r.time, strings), color = Tokens.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.close) } },
    )
}

/** "now · running for 3 h · v0.6.0" or "2 d ago · ran 5 h · v0.5.2". */
internal fun restartLine(index: Int, item: BoardClient.Restarts.Restart, now: Long, strings: Strings): String {
    val parts = mutableListOf<String>()
    if (index == 0) {
        parts += strings.runningFor.fill(formatDuration(item.ranFor))
    } else {
        parts += if (item.at > 0 && now >= item.at) strings.ago.fill(formatDuration(now - item.at)) else strings.timeUnknown
        parts += strings.ranFor.fill(formatDuration(item.ranFor))
    }
    if (item.version.isNotEmpty()) parts += "v${item.version}"
    return parts.joinToString(" · ")
}

/** 45 s, 12 min, 5 h 20 min, 3 d 4 h. */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "$s s"
        s < 3600 -> "${s / 60} min"
        s < 86_400 -> if (s % 3600 / 60 == 0L) "${s / 3600} h" else "${s / 3600} h ${s % 3600 / 60} min"
        else -> if (s % 86_400 / 3600 == 0L) "${s / 86_400} d" else "${s / 86_400} d ${s % 86_400 / 3600} h"
    }
}
