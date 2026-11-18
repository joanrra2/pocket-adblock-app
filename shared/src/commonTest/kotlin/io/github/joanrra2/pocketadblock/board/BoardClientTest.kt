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
        assertEquals(BoardClient.Result.Ok("ok"), c.setBlocking(board, TEST_USER, TEST_PASSWORD, BoardClient.Blocking.Pause(30)))
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
        val r = c.setBlocking(board, TEST_USER, TEST_PASSWORD, BoardClient.Blocking.On)
        assertEquals(BoardClient.Result.Failed("missing X-Requested-With header"), r)
    }

    @Test
    fun tooManyAttemptsCarriesRetryAfter() = runTest {
        val c = BoardClient(HttpClient(MockEngine {
            respond("too many attempts", HttpStatusCode.TooManyRequests, io.ktor.http.headersOf(io.ktor.http.HttpHeaders.RetryAfter, "45"))
        }))
        assertEquals(BoardClient.Login.TooManyAttempts(45), c.login(board, TEST_USER, "nope"))
        assertEquals(BoardClient.Result.TooManyAttempts(45), c.reboot(board, TEST_USER, TEST_PASSWORD))
    }

    @Test
    fun retryAfterHeader() {
        assertEquals(30, BoardClient.retryAfterSeconds("30"))
        assertEquals(60, BoardClient.retryAfterSeconds(null))
        assertEquals(60, BoardClient.retryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(1, BoardClient.retryAfterSeconds("0"))
    }

    @Test
    fun loginShowsTheBoardsRefusal() = runTest {
        val c = BoardClient(HttpClient(MockEngine {
            respond("""{"ok":false,"message":"use the board's IP address"}""", HttpStatusCode.Forbidden)
        }))
        assertEquals(BoardClient.Login.Refused("use the board's IP address"), c.login(board, TEST_USER, TEST_PASSWORD))
    }

    @Test
    fun firmwareActionsUseTheirEndpoints() = runTest {
        val seen = mutableListOf<String>()
        val c = BoardClient(HttpClient(MockEngine { req ->
            seen += "${req.method.value} ${req.url.encodedPath} csrf=${req.headers["X-Requested-With"]}"
            if (req.url.encodedPath == "/api/firmware/install")
                respond("""{"ok":false,"message":"no update available; check first"}""", HttpStatusCode.Conflict)
            else respond("""{"ok":true,"message":"rebooting"}""", HttpStatusCode.OK)
        }))
        assertEquals(BoardClient.Result.Ok("rebooting"), c.reboot(board, TEST_USER, TEST_PASSWORD))
        assertEquals(BoardClient.Result.Failed("no update available; check first"), c.installFirmware(board, TEST_USER, TEST_PASSWORD))
        c.checkFirmware(board, TEST_USER, TEST_PASSWORD)
        assertEquals(
            listOf("POST /api/reboot csrf=sinkhole", "POST /api/firmware/install csrf=sinkhole", "POST /api/firmware/check csrf=sinkhole"),
            seen,
        )
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

    @Test
    fun readsTheRestartHistory() = runTest {
        val c = BoardClient(HttpClient(MockEngine { req ->
            if (req.url.encodedPath != "/api/restarts") respond("", HttpStatusCode.NotFound)
            else respond(
                """{"time":1795040000,"items":[{"at":1795039700,"ranFor":300,"why":"crash","unexpected":true,"version":"0.6.0"},
                   {"at":0,"ranFor":86400,"why":"power on","unexpected":false,"version":"0.6.0"}]}""",
                HttpStatusCode.OK,
            )
        }))
        val r = c.restarts(board, TEST_USER, TEST_PASSWORD)!!
        assertEquals(1795040000, r.time)
        assertEquals(2, r.items.size)
        assertTrue(r.items[0].unexpected)
        assertEquals("power on", r.items[1].why)
        // Boards before 0.6 have no such page.
        assertEquals(null, BoardClient(HttpClient(MockEngine { respond("not found", HttpStatusCode.NotFound) }))
            .restarts(board, TEST_USER, TEST_PASSWORD))
    }
}
