package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginGuardTest {
    private var now = 0L
    private val guard = LoginGuard { now }
    private val board = BoardAddress("192.168.18.23")
    private val other = BoardAddress("192.168.18.24")

    @Test
    fun aRefusedPasswordIsNeverSentAgain() {
        assertTrue(guard.maySend(board, "admin", "old"))
        guard.refused(board, "admin", "old")
        assertFalse(guard.maySend(board, "admin", "old"))
        now += 3_600_000
        assertFalse(guard.maySend(board, "admin", "old"), "time alone must not lift it")
        assertTrue(guard.maySend(board, "admin", "new"), "a newly typed password may be tried")
        assertTrue(guard.maySend(other, "admin", "old"), "other boards are separate")
    }

    @Test
    fun waitsAsLongAsTheBoardAsks() {
        guard.wait(board, 30)
        assertEquals(30, guard.secondsLeft(board))
        assertFalse(guard.maySend(board, "admin", "any"))
        now += 29_500
        assertEquals(1, guard.secondsLeft(board))
        now += 500
        assertEquals(0, guard.secondsLeft(board))
        assertTrue(guard.maySend(board, "admin", "any"))
    }

    @Test
    fun aGoodLoginClearsWhatTheBoardRefused() {
        guard.refused(board, "admin", "old")
        guard.accepted(board, "admin", "good")
        assertTrue(guard.maySend(board, "admin", "old"))
    }

    @Test
    fun duringAWaitOnlyTheAcceptedLoginIsSent() {
        // The firmware keeps taking the right password while it makes others wait.
        guard.accepted(board, "admin", "good")
        guard.wait(board, 30)
        assertTrue(guard.maySend(board, "admin", "good"))
        assertFalse(guard.maySend(board, "admin", "guess"))
        guard.wait(other, 30)
        assertFalse(guard.maySend(other, "admin", "good"), "accepted on one board only")
        assertEquals(30, guard.secondsLeft(board))
    }
}
