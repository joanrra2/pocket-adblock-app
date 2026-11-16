package io.github.joanrra2.pocketadblock.board

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoardClientTest {
    private val board = BoardAddress("192.168.18.23")
    private val router = BoardAddress("192.168.18.1")
    private val nobody = BoardAddress("192.168.18.99")
    private val client = BoardClient(fakeLan(boards = setOf(board.host), others = setOf(router.host)))

    @Test
    fun recognisesBoardsByTheirRealm() = runTest {
        assertEquals(BoardClient.Probe.Board, client.probe(board))
        assertEquals(BoardClient.Probe.NotBoard, client.probe(router))
        assertEquals(BoardClient.Probe.Unreachable, client.probe(nobody))
    }

    @Test
    fun neverTalksToNonLocalAddresses() = runTest {
        var requests = 0
        val c = BoardClient(HttpClient(MockEngine { requests++; respond("", HttpStatusCode.OK) }))
        assertEquals(BoardClient.Probe.NotBoard, c.probe(BoardAddress("8.8.8.8")))
        assertEquals(BoardClient.Login.NotLocal, c.login(BoardAddress("example.com"), TEST_USER, TEST_PASSWORD))
        assertEquals(0, requests)
    }

    @Test
    fun loginReturnsTheBoardState() = runTest {
        val r = client.login(board, TEST_USER, TEST_PASSWORD)
        assertIs<BoardClient.Login.Ok>(r)
        assertEquals("sinkhole", r.status.hostname)
        assertEquals("0.4.2", r.status.version)
        assertEquals(140212, r.status.blocklist.count)
    }

    @Test
    fun wrongPassword() = runTest {
        assertEquals(BoardClient.Login.WrongCredentials, client.login(board, TEST_USER, "nope"))
    }

    @Test
    fun loginElsewhere() = runTest {
        assertEquals(BoardClient.Login.NotBoard, client.login(router, TEST_USER, TEST_PASSWORD))
        assertIs<BoardClient.Login.Unreachable>(client.login(nobody, TEST_USER, TEST_PASSWORD))
    }

    @Test
    fun pauseSendsTheFormTheFirmwareExpects() = runTest {
        var seen: String? = null
        val c = BoardClient(HttpClient(MockEngine { req ->
            val body = (req.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            seen = "${req.method.value} ${req.url.encodedPath} csrf=${req.headers["X-Requested-With"]} " +
                "auth=${req.headers[io.ktor.http.HttpHeaders.Authorization] != null} $body"
            respond("""{"ok":true,"message":"ok"}""", HttpStatusCode.OK)
        }))
        assertEquals(null, c.setBlocking(board, TEST_USER, TEST_PASSWORD, BoardClient.Blocking.Pause(30)))
        assertEquals("POST /api/blocking csrf=sinkhole auth=true mode=pause&minutes=30", seen)
        c.setBlocking(board, TEST_USER, TEST_PASSWORD, BoardClient.Blocking.On)
        assertTrue(seen!!.endsWith("mode=on"))
        c.setBlocking(board, TEST_USER, TEST_PASSWORD, BoardClient.Blocking.Off)
        assertTrue(seen!!.endsWith("mode=off"))
    }

    @Test
    fun pauseReportsTheBoardsError() = runTest {
        val c = BoardClient(HttpClient(MockEngine {
            respond("""{"ok":false,"message":"missing X-Requested-With header"}""", HttpStatusCode.Forbidden)
        }))
        val err = c.setBlocking(board, TEST_USER, TEST_PASSWORD, BoardClient.Blocking.On)
        assertTrue(err!!.startsWith("HTTP 403"), err)
    }

    @Test
    fun realmHeader() {
        assertTrue(BoardClient.isSinkholeRealm("Basic realm=\"sinkhole\", charset=\"UTF-8\""))
        assertTrue(BoardClient.isSinkholeRealm("basic realm=sinkhole"))
        assertFalse(BoardClient.isSinkholeRealm("Basic realm=\"Router\""))
        assertFalse(BoardClient.isSinkholeRealm("Digest realm=\"sinkhole\""))
        assertFalse(BoardClient.isSinkholeRealm(null))
    }

    @Test
    fun basicAuthMatchesTheFirmware() {
        // The firmware base64-encodes "user:pass" (web_server.cpp, authorized()).
        assertEquals("Basic YWRtaW46c2lua2hvbGU=", BoardClient.basicAuth("admin", "sinkhole"))
    }
}
