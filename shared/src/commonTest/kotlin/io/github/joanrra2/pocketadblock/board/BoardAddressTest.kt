package io.github.joanrra2.pocketadblock.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardAddressTest {
    @Test
    fun parsesWhatPeopleType() {
        assertEquals(BoardAddress("192.168.1.50"), BoardAddress.parse("192.168.1.50"))
        assertEquals(BoardAddress("192.168.1.50"), BoardAddress.parse("  http://192.168.1.50/  "))
        assertEquals(BoardAddress("192.168.1.50", 8080), BoardAddress.parse("192.168.1.50:8080"))
        assertEquals(BoardAddress("sinkhole.local"), BoardAddress.parse("Sinkhole.local"))
        assertEquals(BoardAddress("192.168.1.50"), BoardAddress.parse("http://192.168.1.50/#settings"))
    }

    @Test
    fun rejectsNonAddresses() {
        for (s in listOf("", "   ", "https://192.168.1.50", "192.168.1.500", "192.168.1", "host name", "a:0", "a:70000", "under_score")) {
            assertNull(BoardAddress.parse(s), s)
        }
    }

    @Test
    fun onlyLocalAddressesAreLocal() {
        assertTrue(BoardAddress("192.168.18.23").isLocal)
        assertTrue(BoardAddress("sinkhole.local").isLocal)
        assertTrue(BoardAddress("127.0.0.1", 8080).isLocal)
        assertFalse(BoardAddress("8.8.8.8").isLocal)
        assertFalse(BoardAddress("example.com").isLocal)
    }

    @Test
    fun homeNetworkNamesAreLocal() {
        // The names the firmware accepts in the Host header while the factory password is set.
        for (h in listOf("sinkhole", "sinkhole.lan", "sinkhole.home", "sinkhole.home.arpa", "sinkhole.internal", "sinkhole.localdomain", "SINKHOLE.LAN."))
            assertTrue(BoardAddress(h).isLocal, h)
        for (h in listOf("sinkhole.example.com", "192.168.1", "local.example.org", ".lan", "home.arpa.example.com"))
            assertFalse(BoardAddress.isLocalName(h), h)
    }

    @Test
    fun urls() {
        assertEquals("http://192.168.18.23/api/status", BoardAddress("192.168.18.23").url("/api/status"))
        assertEquals("http://127.0.0.1:8080/", BoardAddress("127.0.0.1", 8080).url())
    }
}
