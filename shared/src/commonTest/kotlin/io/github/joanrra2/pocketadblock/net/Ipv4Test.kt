package io.github.joanrra2.pocketadblock.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Ipv4Test {
    private fun ip(s: String) = Ipv4.parse(s)!!

    @Test
    fun parsesAndFormats() {
        assertEquals("192.168.18.23", Ipv4.format(ip("192.168.18.23")))
        assertEquals("255.255.255.255", Ipv4.format(ip("255.255.255.255")))
        assertEquals("0.0.0.0", Ipv4.format(ip("0.0.0.0")))
    }

    @Test
    fun rejectsInvalid() {
        for (s in listOf("", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.-4", "01.2.3.4", "a.b.c.d", "1..2.3", "1.2.3.4 x")) {
            assertNull(Ipv4.parse(s), s)
        }
    }

    @Test
    fun localRanges() {
        for (s in listOf("10.0.0.1", "172.16.0.1", "172.31.255.254", "192.168.18.23", "169.254.1.1", "127.0.0.1")) {
            assertTrue(Ipv4.isLocal(ip(s)), s)
        }
        for (s in listOf("8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.0.1", "1.1.1.1", "100.64.0.1")) {
            assertFalse(Ipv4.isLocal(ip(s)), s)
        }
    }

    @Test
    fun sweepCoversTheSlash24WithoutSelf() {
        val hosts = Ipv4.sweepHosts(ip("192.168.18.12"), 24)
        assertEquals(253, hosts.size)
        assertEquals(ip("192.168.18.1"), hosts.first())
        assertEquals(ip("192.168.18.254"), hosts.last())
        assertFalse(ip("192.168.18.12") in hosts)
        assertTrue(ip("192.168.18.23") in hosts)
    }

    @Test
    fun widerSubnetsAreCappedToTheSlash24() {
        val hosts = Ipv4.sweepHosts(ip("10.1.2.3"), 16)
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { Ipv4.format(it).startsWith("10.1.2.") })
    }

    @Test
    fun smallSubnets() {
        assertEquals(listOf(ip("192.168.4.1")), Ipv4.sweepHosts(ip("192.168.4.2"), 30))
    }
}
