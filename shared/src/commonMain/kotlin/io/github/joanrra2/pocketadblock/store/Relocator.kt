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
 */
class Relocator(private val client: BoardClient) {

    data class Missing(val board: SavedBoard, val password: String)

    suspend fun match(
        candidates: List<BoardAddress>,
        missing: List<Missing>,
        knownAddresses: Set<BoardAddress>,
    ): Map<String, BoardAddress> {
        val result = mutableMapOf<String, BoardAddress>()
        for (address in candidates) {
            if (address in knownAddresses || address in result.values) continue
            for (m in missing) {
                if (m.board.id in result) continue
                val login = client.login(address, m.board.user, m.password)
                if (login is BoardClient.Login.Ok && login.status.hostname.equals(m.board.name, ignoreCase = true)) {
                    result[m.board.id] = address
                    break
                }
            }
        }
        return result
    }
}
