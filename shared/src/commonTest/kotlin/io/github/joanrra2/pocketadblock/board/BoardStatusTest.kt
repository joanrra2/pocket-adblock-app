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
    }
}
