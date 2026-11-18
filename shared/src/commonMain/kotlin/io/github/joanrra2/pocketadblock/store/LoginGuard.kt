package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import kotlin.time.TimeSource

/**
 * Keeps the app from hammering a board with passwords it refused.
 *
 * The firmware slows logins down after repeated wrong passwords (429 with Retry-After),
 * so the app never sends a password again once a board refused it, and sends nothing
 * with a password to a board while it asks to wait. Only the person typing a new
 * password (or a different user) lifts the first rule; time lifts the second.
 *
 * Kept in memory only, for as long as the app runs.
 */
class LoginGuard(private val nowMs: () -> Long = defaultClock()) {

    private val refused = mutableMapOf<BoardAddress, MutableSet<String>>()
    private val waitUntilMs = mutableMapOf<BoardAddress, Long>()

    /** True when this login may be sent to the board now. */
    fun maySend(address: BoardAddress, user: String, password: String): Boolean =
        secondsLeft(address) == 0 && !wasRefused(address, user, password)

    /** The board said this user and password are wrong. */
    fun refused(address: BoardAddress, user: String, password: String) {
        refused.getOrPut(address) { mutableSetOf() } += key(user, password)
    }

    fun wasRefused(address: BoardAddress, user: String, password: String): Boolean =
        refused[address]?.contains(key(user, password)) == true

    /** The board answered 429: nothing with a password for [seconds]. */
    fun wait(address: BoardAddress, seconds: Int) {
        waitUntilMs[address] = nowMs() + seconds * 1000L
    }

    /** Seconds before anything may be sent to this board with a password; 0 when it may. */
    fun secondsLeft(address: BoardAddress): Int {
        val until = waitUntilMs[address] ?: return 0
        val left = until - nowMs()
        if (left <= 0) {
            waitUntilMs.remove(address)
            return 0
        }
        return ((left + 999) / 1000).toInt()
    }

    /** A login worked: forget what this board refused before. */
    fun accepted(address: BoardAddress) {
        refused.remove(address)
        waitUntilMs.remove(address)
    }

    private fun key(user: String, password: String) = "$user\u0000$password"

    private companion object {
        fun defaultClock(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}
