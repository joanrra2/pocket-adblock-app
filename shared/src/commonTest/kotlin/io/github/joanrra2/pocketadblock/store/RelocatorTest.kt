package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.TEST_PASSWORD
import io.github.joanrra2.pocketadblock.board.TEST_USER
import io.github.joanrra2.pocketadblock.board.fakeLan
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelocatorTest {
    // The fake boards all report hostname "sinkhole" (SAMPLE_STATUS).
    private val saved = SavedBoard("sinkhole", "sinkhole", "192.168.18.23", user = TEST_USER)

    @Test
    fun findsTheBoardAtItsNewAddress() = runTest {
        val r = Relocator(BoardClient(fakeLan(boards = setOf("192.168.18.40"))))
        val moved = r.match(
            candidates = listOf(BoardAddress("192.168.18.40")),
            missing = listOf(Relocator.Missing(saved, TEST_PASSWORD)),
            knownAddresses = setOf(saved.address),
        )
        assertEquals(mapOf("sinkhole" to BoardAddress("192.168.18.40")), moved)
    }

    @Test
    fun aDifferentHostnameIsNotAMatch() = runTest {
        val other = saved.copy(id = "garage", name = "sinkhole-garage")
        val r = Relocator(BoardClient(fakeLan(boards = setOf("192.168.18.40"))))
        val moved = r.match(listOf(BoardAddress("192.168.18.40")), listOf(Relocator.Missing(other, TEST_PASSWORD)), emptySet())
        assertTrue(moved.isEmpty())
    }

    @Test
    fun wrongPasswordIsNotAMatch() = runTest {
        val r = Relocator(BoardClient(fakeLan(boards = setOf("192.168.18.40"))))
        val moved = r.match(listOf(BoardAddress("192.168.18.40")), listOf(Relocator.Missing(saved, "old")), emptySet())
        assertTrue(moved.isEmpty())
    }

    @Test
    fun skipsAddressesOfOtherSavedBoards() = runTest {
        var requests = 0
        val client = BoardClient(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine {
            requests++
            error("must not be asked")
        }))
        val moved = Relocator(client).match(
            candidates = listOf(BoardAddress("192.168.18.57")),
            missing = listOf(Relocator.Missing(saved, TEST_PASSWORD)),
            knownAddresses = setOf(BoardAddress("192.168.18.57")),
        )
        assertTrue(moved.isEmpty())
        assertEquals(0, requests, "a saved board's password was sent to another saved board")
    }
}
