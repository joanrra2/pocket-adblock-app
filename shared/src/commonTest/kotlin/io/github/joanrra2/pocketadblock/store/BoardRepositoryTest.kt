package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.platform.MemoryStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardRepositoryTest {
    private val settings = MemoryStore()
    private val secrets = MemoryStore()
    private fun board(id: String = "sinkhole", host: String = "192.168.18.23") =
        SavedBoard(id = id, name = "sinkhole", host = host, user = "admin")

    @Test
    fun passwordsNeverGoToThePlainSettings() {
        val repo = BoardRepository(settings, secrets)
        repo.save(board(), "s3cret-pass")
        assertEquals("s3cret-pass", repo.password("sinkhole"))
        assertFalse(settings.get("boards.v1")!!.contains("s3cret-pass"))
        assertEquals("s3cret-pass", secrets.get("pw.sinkhole"))
    }

    @Test
    fun survivesARestart() {
        BoardRepository(settings, secrets).save(board(), "pw")
        val again = BoardRepository(settings, secrets)
        assertEquals(listOf(board()), again.boards.value)
        assertEquals("pw", again.password("sinkhole"))
    }

    @Test
    fun savingWithoutPasswordForgetsTheOldOne() {
        val repo = BoardRepository(settings, secrets)
        repo.save(board(), "pw")
        repo.save(board(), null)
        assertNull(repo.password("sinkhole"))
        assertEquals(1, repo.boards.value.size)
    }

    @Test
    fun removeDeletesThePasswordToo() {
        val repo = BoardRepository(settings, secrets)
        repo.save(board(), "pw")
        repo.remove("sinkhole")
        assertTrue(repo.boards.value.isEmpty())
        assertNull(secrets.get("pw.sinkhole"))
    }

    @Test
    fun newAddress() {
        val repo = BoardRepository(settings, secrets)
        repo.save(board(), "pw")
        repo.updateAddress("sinkhole", BoardAddress("192.168.18.40"))
        assertEquals("192.168.18.40", repo.boards.value.single().host)
        assertEquals("pw", repo.password("sinkhole"))
    }

    @Test
    fun findsByAddressThenByHostname() {
        val repo = BoardRepository(settings, secrets)
        repo.save(board(), null)
        assertEquals("sinkhole", repo.find(BoardAddress("192.168.18.23"), "")?.id)
        assertEquals("sinkhole", repo.find(BoardAddress("192.168.18.99"), "SINKHOLE")?.id)
        assertNull(repo.find(BoardAddress("192.168.18.99"), "other"))
        assertNull(repo.find(BoardAddress("192.168.18.99"), ""))
    }

    @Test
    fun uniqueIds() {
        val repo = BoardRepository(settings, secrets)
        assertEquals("sinkhole", repo.newId("Sinkhole"))
        repo.save(board("sinkhole"), null)
        assertEquals("sinkhole-2", repo.newId("sinkhole"))
        repo.save(board("sinkhole-2", "192.168.18.57"), null)
        assertEquals("sinkhole-3", repo.newId("sinkhole"))
        assertEquals("board", repo.newId("!!"))
    }

    @Test
    fun damagedSettingsStartEmpty() {
        settings.put("boards.v1", "{not json")
        assertTrue(BoardRepository(settings, secrets).boards.value.isEmpty())
    }
}
