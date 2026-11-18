package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient

/**
 * Finds saved boards that answer at a new address (DHCP gave them another one).
 *
 * Without a firmware change a board says nothing about itself before login, so each
 * candidate address (already confirmed to be a board by its realm) is checked with the
 * missing board's saved login, and accepted when the hostname matches. Addresses that
 * already belong to another saved board are skipped, so a password is only ever sent
 * to boards that are not otherwise known.
 *
 * Different wrong passwords are what the firmware's login limit counts, so a candidate
 * gets at most [maxPasswordsPerBoard] of them, none it refused before, and nothing at
 * all while it asks to wait (429).
 */
class Relocator(
    private val client: BoardClient,
    private val guard: LoginGuard = LoginGuard(),
    private val maxPasswordsPerBoard: Int = 2,
) {

    data class Missing(val board: SavedBoard, val password: String)

    suspend fun match(
        candidates: List<BoardAddress>,
        missing: List<Missing>,
        knownAddresses: Set<BoardAddress>,
    ): Map<String, BoardAddress> {
        val result = mutableMapOf<String, BoardAddress>()
        for (address in candidates) {
            if (address in knownAddresses || address in result.values) continue
            var tried = 0
            for (m in missing) {
                if (m.board.id in result || tried >= maxPasswordsPerBoard) continue
                if (!guard.maySend(address, m.board.user, m.password)) continue
                tried++
                when (val login = client.login(address, m.board.user, m.password)) {
                    is BoardClient.Login.Ok -> {
                        guard.accepted(address)
                        if (login.status.hostname.equals(m.board.name, ignoreCase = true)) {
                            result[m.board.id] = address
                            break
                        }
                    }
                    BoardClient.Login.WrongCredentials -> guard.refused(address, m.board.user, m.password)
                    is BoardClient.Login.TooManyAttempts -> {
                        guard.wait(address, login.retryAfterSeconds)
                        break
                    }
                    else -> break
                }
            }
        }
        return result
    }
}
