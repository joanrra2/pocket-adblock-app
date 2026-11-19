package io.github.joanrra2.pocketadblock.notify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NtfyLinkTest {
    @Test
    fun linksForTheDefaultServer() {
        assertEquals("ntfy://ntfy.sh/pocket-x1y2", NtfyLink.subscribe("https://ntfy.sh", "pocket-x1y2"))
        assertEquals("https://ntfy.sh/pocket-x1y2", NtfyLink.web("https://ntfy.sh/", "pocket-x1y2"))
        assertEquals("ntfy.sh/pocket-x1y2", NtfyLink.label("https://ntfy.sh", "pocket-x1y2"))
    }

    @Test
    fun ownServerWithPortAndPlainHttp() {
        assertEquals("ntfy://ntfy.home.lan:8080/alerts?secure=false", NtfyLink.subscribe("http://ntfy.home.lan:8080", "alerts"))
        assertEquals("http://ntfy.home.lan:8080/alerts", NtfyLink.web("http://ntfy.home.lan:8080", "alerts"))
    }

    @Test
    fun refusesWhatTheNtfyAppCouldNotUse() {
        assertNull(NtfyLink.subscribe("", "alerts"))
        assertNull(NtfyLink.subscribe("ftp://ntfy.sh", "alerts"))
        assertNull(NtfyLink.subscribe("https://user@evil.example", "alerts"))
        assertNull(NtfyLink.subscribe("https://ntfy.sh", ""))
        assertNull(NtfyLink.subscribe("https://ntfy.sh", "a/b"))
        assertNull(NtfyLink.web("https://ntfy.sh", "has space"))
    }
}
