package io.github.joanrra2.pocketadblock.discovery

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.fakeLan
import io.github.joanrra2.pocketadblock.net.Ipv4
import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.github.joanrra2.pocketadblock.platform.MdnsBrowser
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryTest {
    private val home = LocalNetwork(Ipv4.parse("192.168.18.12")!!, 24)

    @Test
    fun sweepFindsBoardsAndSkipsOtherDevices() = runTest {
        val client = BoardClient(fakeLan(boards = setOf("192.168.18.23", "192.168.18.57"), others = setOf("192.168.18.1")))
        val d = Discovery(client, { emptyFlow() }, { home }, mdnsGraceMs = 10)
        val states = d.search().toList()
        val end = states.last()
        assertTrue(states.first().running)
        assertFalse(end.running)
        assertEquals(253, end.sweepTotal)
        assertEquals(253, end.swept)
        assertEquals(setOf("192.168.18.23", "192.168.18.57"), end.boards.map { it.address.host }.toSet())
        assertTrue(end.boards.all { it.via == FoundVia.Sweep })
    }

    @Test
    fun mdnsResultsAreCheckedAndNotListedTwice() = runTest {
        val client = BoardClient(fakeLan(boards = setOf("192.168.18.23")))
        // mDNS names the board, a printer that isn't a board, and the board again.
        val mdns = MdnsBrowser { flow { emit("192.168.18.23"); emit("192.168.18.40"); emit("192.168.18.23") } }
        val end = Discovery(client, mdns, { home }, mdnsGraceMs = 10).search().last()
        assertEquals(listOf(BoardAddress("192.168.18.23")), end.boards.map { it.address })
    }

    @Test
    fun withoutWifiOnlyMdnsCanFindBoards() = runTest {
        val client = BoardClient(fakeLan(boards = setOf("10.0.0.5")))
        val end = Discovery(client, { flow { emit("10.0.0.5") } }, { null }, mdnsGraceMs = 10).search().last()
        assertEquals(0, end.sweepTotal)
        assertEquals(listOf(FoundBoard(BoardAddress("10.0.0.5"), FoundVia.Mdns)), end.boards)
    }

    @Test
    fun setupAccessPointIsMarked() = runTest {
        val onSetupWifi = LocalNetwork(Ipv4.parse("192.168.4.2")!!, 24)
        val client = BoardClient(fakeLan(boards = setOf("192.168.4.1")))
        val end = Discovery(client, { emptyFlow() }, { onSetupWifi }, mdnsGraceMs = 10).search().last()
        assertEquals(listOf(FoundBoard(BoardAddress("192.168.4.1"), FoundVia.SetupAccessPoint)), end.boards)
    }

    @Test
    fun aBoardAt192_168_4_1OnAnotherNetworkIsNotASetupAccessPoint() {
        val elsewhere = LocalNetwork(Ipv4.parse("192.168.18.12")!!, 24)
        assertFalse(Discovery.isSetupAccessPoint(elsewhere, BoardAddress("192.168.4.1")))
        assertFalse(Discovery.isSetupAccessPoint(null, BoardAddress("192.168.4.1")))
    }
}
