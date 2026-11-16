package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.discovery.FoundVia
import kotlin.math.roundToLong

@Composable
fun BoardScreen(
    session: Session,
    strings: Strings,
    onBack: () -> Unit,
    onAuthRejected: () -> Unit,
    onOpenExternal: (String) -> Unit,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    val st = session.status
    val es = strings === Strings.ES

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Tokens.s1).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("board-back")) { Text("‹ " + strings.back) }
            Column(Modifier.weight(1f)) {
                Text(
                    st.hostname.ifEmpty { session.address.label },
                    color = Tokens.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("board-title"),
                )
                Text(
                    listOf(session.address.label, st.version.takeIf { it.isNotEmpty() }?.let { "v$it" }, st.edition.ifEmpty { null })
                        .filterNotNull().joinToString(" · "),
                    color = Tokens.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            TextButton(onClick = { reloadKey++ }) { Text("⟳") }
            TextButton(onClick = { onOpenExternal(session.address.url()) }) { Text("↗") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().background(Tokens.s1).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!st.dns.blocking) {
                Text(strings.paused, color = Tokens.warn, fontSize = 12.sp)
            } else {
                Text("${formatDecimal(st.blockedPercent, es)} % ${strings.blocked}", color = Tokens.text, fontSize = 12.sp)
            }
            Text("${formatInt(st.queries, es)} ${strings.queries}", color = Tokens.muted, fontSize = 12.sp)
            Text("${formatInt(st.blocklist.count, es)} ${strings.listSize}", color = Tokens.muted, fontSize = 12.sp)
        }
        if (session.board.via == FoundVia.SetupAccessPoint) {
            Text(
                strings.setupHint,
                color = Tokens.warn,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(Tokens.s2).padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        BoardWebView(
            address = session.address,
            user = session.user,
            password = session.password,
            reloadKey = reloadKey,
            onAuthRejected = onAuthRejected,
            onOpenExternal = onOpenExternal,
            onExit = onBack,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

internal fun formatInt(n: Long, spanish: Boolean): String {
    val sep = if (spanish) '.' else ','
    val digits = n.toString()
    val out = StringBuilder()
    digits.forEachIndexed { i, c ->
        if (i > 0 && (digits.length - i) % 3 == 0) out.append(sep)
        out.append(c)
    }
    return out.toString()
}

internal fun formatDecimal(v: Double, spanish: Boolean): String {
    val tenths = (v * 10).roundToLong()
    return "${tenths / 10}${if (spanish) ',' else '.'}${tenths % 10}"
}
