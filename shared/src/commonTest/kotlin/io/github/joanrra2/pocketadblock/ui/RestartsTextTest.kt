package io.github.joanrra2.pocketadblock.ui

import io.github.joanrra2.pocketadblock.board.BoardClient
import kotlin.test.Test
import kotlin.test.assertEquals

class RestartsTextTest {
    @Test
    fun durations() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("12 min", formatDuration(12 * 60 + 5))
        assertEquals("5 h", formatDuration(5 * 3600))
        assertEquals("5 h 20 min", formatDuration(5 * 3600 + 20 * 60))
        assertEquals("3 d 4 h", formatDuration(3 * 86_400 + 4 * 3600 + 59))
        assertEquals("0 s", formatDuration(-5))
    }

    @Test
    fun restartLines() {
        val now = 1_795_040_000L
        val en = Strings.EN
        assertEquals("now, running for 5 min · v0.6.0",
            restartLine(0, BoardClient.Restarts.Restart(at = now - 300, ranFor = 300, why = "crash", version = "0.6.0"), now, en))
        assertEquals("2 d ago · ran 1 d · v0.5.2",
            restartLine(1, BoardClient.Restarts.Restart(at = now - 2 * 86_400, ranFor = 86_400, version = "0.5.2"), now, en))
        assertEquals("time unknown · ran 3 h",
            restartLine(2, BoardClient.Restarts.Restart(at = 0, ranFor = 3 * 3600), now, en))
    }
}
