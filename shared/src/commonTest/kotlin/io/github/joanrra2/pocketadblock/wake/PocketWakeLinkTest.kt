package io.github.joanrra2.pocketadblock.wake

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PocketWakeLinkTest {
    @Test
    fun sameLinkAsTheBoardsQrCode() {
        // The firmware builds: WAKE_APP + '#setup?' + URLSearchParams({v,host,port,tls,wsport,wspath,prefix[,user]})
        assertEquals(
            "https://joanrra2.github.io/pocket-wake/#setup?v=1&host=x1.emqxsl.com&port=8883&tls=1&wsport=8084&wspath=%2Fmqtt&prefix=sinkhole&user=phone",
            PocketWakeLink.build("mqtts://x1.emqxsl.com:8883", "sinkhole", phoneUser = "phone"),
        )
    }

    @Test
    fun brokerUris() {
        assertEquals(PocketWakeLink.Broker("h", 8883, true, null), PocketWakeLink.parseBroker("mqtts://h"))
        assertEquals(PocketWakeLink.Broker("h", 1883, false, null), PocketWakeLink.parseBroker("mqtt://h"))
        assertEquals(PocketWakeLink.Broker("h", 9000, true, null), PocketWakeLink.parseBroker("ssl://h:9000"))
        assertEquals(PocketWakeLink.Broker("h", 8883, true, 8084), PocketWakeLink.parseBroker("wss://h:8084/mqtt"))
        assertEquals(PocketWakeLink.Broker("::1", 1883, false, null), PocketWakeLink.parseBroker("tcp://[::1]"))
        assertNull(PocketWakeLink.parseBroker(""))
        assertNull(PocketWakeLink.parseBroker("http://h"))
    }

    @Test
    fun noBrokerNoLink() {
        assertNull(PocketWakeLink.build("", "sinkhole"))
    }

    @Test
    fun webSocketPortFromTheUriAndDefaults() {
        val l = PocketWakeLink.build("wss://h:443/mqtt", "", webSocketPath = "")!!
        assertTrue("wsport=443" in l)
        assertTrue("wspath=%2Fmqtt" in l)
        assertTrue("prefix=sinkhole" in l)
        assertFalse("user=" in l)
    }

    @Test
    fun valuesAreEncoded() {
        val l = PocketWakeLink.build("mqtts://h", "home/board 1", phoneUser = "a&b")!!
        assertTrue("prefix=home%2Fboard%201" in l || "prefix=home%2Fboard+1" in l, l)
        assertTrue("user=a%26b" in l, l)
    }

    @Test
    fun recognisesSetupLinks() {
        assertTrue(PocketWakeLink.isSetupLink("https://joanrra2.github.io/pocket-wake/#setup?v=1&host=h"))
        assertFalse(PocketWakeLink.isSetupLink("https://joanrra2.github.io/pocket-wake/"))
        assertFalse(PocketWakeLink.isSetupLink("https://example.com/#setup?v=1"))
    }
}
