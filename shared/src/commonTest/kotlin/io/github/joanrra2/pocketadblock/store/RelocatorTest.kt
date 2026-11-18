package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.TEST_PASSWORD
import io.github.joanrra2.pocketadblock.board.TEST_USER
import io.github.joanrra2.pocketadblock.board.fakeLan
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
    fun sendsAtMostTwoPasswordsToAnUnknownBoard() = runTest {
        var logins = 0
        val client = BoardClient(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine {
            logins++
            respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"sinkhole\""))
        }))
        val missing = (1..4).map { Relocator.Missing(saved.copy(id = "b$it", name = "b$it"), "password-$it") }
        val guard = LoginGuard()
        val moved = Relocator(client, guard).match(listOf(BoardAddress("192.168.18.40")), missing, emptySet())
        assertTrue(moved.isEmpty())
        assertEquals(2, logins)
        // A second search never resends a refused password.
        Relocator(client, guard).match(listOf(BoardAddress("192.168.18.40")), missing.take(2), emptySet())
        assertEquals(2, logins)
    }

    @Test
    fun stopsWhenTheBoardAsksToWait() = runTest {
        var logins = 0
        val client = BoardClient(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine {
            logins++
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "120"))
        }))
        val guard = LoginGuard()
        val address = BoardAddress("192.168.18.40")
        val missing = (1..2).map { Relocator.Missing(saved.copy(id = "b$it"), "password-$it") }
        Relocator(client, guard).match(listOf(address), missing, emptySet())
        assertEquals(1, logins)
        assertEquals(120, guard.secondsLeft(address))
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
