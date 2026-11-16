package io.github.joanrra2.pocketadblock.store

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A board the user added. The password is not here: it lives in the encrypted store. */
@Serializable
data class SavedBoard(
    val id: String,
    /** The board's hostname when it was saved (Settings → Device name); used to find it again. */
    val name: String,
    val host: String,
    val port: Int = 80,
    val user: String,
    /** Ask for the fingerprint / screen lock before opening it. */
    val requireUnlock: Boolean = false,
) {
    val address: BoardAddress get() = BoardAddress(host, port)
}

/**
 * The saved boards. The list is kept in [settings] as JSON; each password is kept
 * separately in [secrets] (encrypted by the platform) under "pw.<id>".
 */
class BoardRepository(private val settings: KeyValueStore, private val secrets: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SavedBoard.serializer())
    private val state = MutableStateFlow(load())

    val boards: StateFlow<List<SavedBoard>> get() = state

    fun password(id: String): String? = secrets.get(secretKey(id))

    /** Adds or replaces [board]. A null [password] forgets any saved password. */
    fun save(board: SavedBoard, password: String?) {
        secrets.put(secretKey(board.id), password)
        val list = state.value
        val i = list.indexOfFirst { it.id == board.id }
        persist(if (i >= 0) list.toMutableList().also { it[i] = board } else list + board)
    }

    fun updateAddress(id: String, address: BoardAddress) {
        persist(state.value.map { if (it.id == id) it.copy(host = address.host, port = address.port) else it })
    }

    fun remove(id: String) {
        secrets.put(secretKey(id), null)
        persist(state.value.filter { it.id != id })
    }

    /** The saved board at [address], or else the one with this hostname. */
    fun find(address: BoardAddress, hostname: String): SavedBoard? =
        state.value.firstOrNull { it.address == address }
            ?: state.value.firstOrNull { hostname.isNotEmpty() && it.name.equals(hostname, ignoreCase = true) }

    /** An id for a new board: its hostname, made unique ("sinkhole", "sinkhole-2", …). */
    fun newId(hostname: String): String {
        val base = hostname.lowercase().filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "board" }
        val taken = state.value.map { it.id }.toSet()
        if (base !in taken) return base
        var n = 2
        while ("$base-$n" in taken) n++
        return "$base-$n"
    }

    private fun load(): List<SavedBoard> =
        settings.get(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun persist(list: List<SavedBoard>) {
        settings.put(KEY, json.encodeToString(serializer, list))
        state.value = list
    }

    private fun secretKey(id: String) = "pw.$id"

    private companion object {
        const val KEY = "boards.v1"
    }
}
