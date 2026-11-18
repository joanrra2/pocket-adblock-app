package io.github.joanrra2.pocketadblock.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardStatusTest {
    @Test
    fun parsesARealStatus() {
        val s = BoardStatus.parse(SAMPLE_STATUS)
        assertEquals("pro", s.edition)
        assertEquals("192.168.18.23", s.wifi.ip)
        assertTrue(s.dns.blocking)
        assertEquals(18342, s.queries)
        assertEquals(23.39, s.blockedPercent, 0.01)
    }

    @Test
    fun missingFieldsGetDefaults() {
        val s = BoardStatus.parse("""{"version":"0.3.0"}""")
        assertEquals("0.3.0", s.version)
        assertEquals(0.0, s.blockedPercent)
        kotlin.test.assertFalse(s.update.failed)
        kotlin.test.assertFalse(s.wifi.weakSignal)
    }

    @Test
    fun firmware052Fields() {
        // Shapes from firmware 0.5.2 GET /api/status (web_server.cpp getStatus).
        val s = BoardStatus.parse(
            """{"wifi":{"rssi":-81},"update":{"running":false,"lastOk":false,"lastAgo":120,"message":"HTTP 404"},
               "firmware":{"latest":"0.5.3","available":true,"installing":true,"auto":true}}""",
        )
        kotlin.test.assertTrue(s.wifi.weakSignal)
        kotlin.test.assertTrue(s.update.failed)
        assertEquals("HTTP 404", s.update.message)
        kotlin.test.assertTrue(s.firmware.installing)
        kotlin.test.assertTrue(s.firmware.auto)
        kotlin.test.assertFalse(BoardStatus.parse("""{"update":{"running":true,"lastOk":false,"lastAgo":5}}""").update.failed)
    }

    @Test
    fun restartFieldsFromFirmware06() {
        val s = BoardStatus.parse("""{"uptime":300,"time":1795040000,"lastRestart":"power dip (brownout)"}""")
        assertEquals(1795040000, s.time)
        kotlin.test.assertTrue(s.unexpectedRestart)
        for (why in listOf("crash", "watchdog")) kotlin.test.assertTrue(BoardStatus.isUnexpected(why), why)
        for (why in listOf("power on", "firmware update", "restarted from the web UI", "factory reset", "settings restored", ""))
            kotlin.test.assertFalse(BoardStatus.isUnexpected(why), why)
    }
}
