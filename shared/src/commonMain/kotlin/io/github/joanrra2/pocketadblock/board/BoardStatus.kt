package io.github.joanrra2.pocketadblock.board

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parts of the board's GET /api/status that the app shows. Unknown fields are ignored. */
@Serializable
data class BoardStatus(
    val version: String = "",
    val edition: String = "",
    val hostname: String = "",
    val uptime: Long = 0,
    val defaultPassword: Boolean = false,
    val wifi: Wifi = Wifi(),
    val dns: Dns = Dns(),
    val blocklist: Blocklist = Blocklist(),
) {
    @Serializable
    data class Wifi(val ssid: String = "", val ip: String = "")

    @Serializable
    data class Dns(
        val blocking: Boolean = true,
        val resumeIn: Long = 0,
        val blocked: Long = 0,
        val allowed: Long = 0,
    )

    @Serializable
    data class Blocklist(val count: Long = 0)

    val queries: Long get() = dns.blocked + dns.allowed

    /** Share of queries blocked since boot, 0..100. */
    val blockedPercent: Double get() = if (queries == 0L) 0.0 else dns.blocked * 100.0 / queries

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): BoardStatus = json.decodeFromString(serializer(), text)
    }
}
