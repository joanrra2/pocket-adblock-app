package io.github.joanrra2.pocketadblock

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.discovery.Discovery
import io.github.joanrra2.pocketadblock.platform.JvmPlatform
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in check against a real board on this PC's network. Sends no password: it only
 * looks for boards the way the app does (sweep + realm check).
 *
 *   POCKETADBLOCK_IT=1 [POCKETADBLOCK_BOARD=192.168.18.23] ./gradlew :shared:jvmTest
 */
class RealBoardCheck {
    private val enabled = System.getenv("POCKETADBLOCK_IT") == "1"
    private val expected = System.getenv("POCKETADBLOCK_BOARD")?.let(BoardAddress::parse)

    @Test
    fun findsTheBoardOnThisNetwork(): Unit = runBlocking {
        if (!enabled) return@runBlocking
        val platform = JvmPlatform()
        val client = BoardClient(platform.httpClient())
        val started = System.currentTimeMillis()
        val end = Discovery(client, platform.mdns, platform::localNetwork).search().last()
        val ms = System.currentTimeMillis() - started
        println("network ${end.network}: swept ${end.swept}/${end.sweepTotal} in $ms ms, boards: ${end.boards}")
        assertTrue(end.boards.isNotEmpty(), "no board found on ${end.network}")
        expected?.let { e -> assertTrue(end.boards.any { it.address == e }, "$e not found") }
    }

    @Test
    fun probeAnswersWithoutPassword(): Unit = runBlocking {
        if (!enabled || expected == null) return@runBlocking
        val client = BoardClient(JvmPlatform().httpClient())
        assertEquals(BoardClient.Probe.Board, client.probe(expected))
        assertEquals(BoardClient.Login.WrongCredentials, client.login(expected, "pocket-adblock-test", "definitely-not-the-password"))
    }
}
