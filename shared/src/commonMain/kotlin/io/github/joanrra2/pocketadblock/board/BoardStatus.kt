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
    val firmware: Firmware = Firmware(),
    /** The blocklist's automatic download. */
    val update: ListUpdate = ListUpdate(),
    /** The board's clock, unix seconds; 0 until NTP set it or before firmware 0.6. */
    val time: Long = 0,
    /** Why the board last started ("power on", "crash", ...); empty before firmware 0.6. */
    val lastRestart: String = "",
) {
    /** The last restart was a crash, a watchdog or a power dip (not something a person did). */
    val unexpectedRestart: Boolean get() = isUnexpected(lastRestart)

    @Serializable
    data class Firmware(
        val latest: String = "",
        val available: Boolean = false,
        /** Downloading or writing an update right now; the board restarts when done. */
        val installing: Boolean = false,
        /** Installs updates by itself (signed builds only). */
        val auto: Boolean = false,
        val message: String = "",
    )

    @Serializable
    data class Wifi(
        val ssid: String = "",
        val ip: String = "",
        /** Signal in dBm; 0 when unknown. */
        val rssi: Int = 0,
    ) {
        val weakSignal: Boolean get() = rssi in -127..-76
    }

    @Serializable
    data class ListUpdate(
        val running: Boolean = false,
        val lastOk: Boolean = true,
        /** Seconds since the last try; -1 when it never ran. */
        val lastAgo: Long = -1,
        val message: String = "",
    ) {
        /** The last automatic download failed (and none is running now). */
        val failed: Boolean get() = !running && !lastOk && lastAgo >= 0
    }

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

        /**
         * The firmware's unexpected restart reasons (crash, watchdog, brownout). GET
         * /api/restarts says it per item; /api/status only has the text.
         */
        fun isUnexpected(reason: String): Boolean {
            val r = reason.lowercase()
            return "crash" in r || "watchdog" in r || "brownout" in r
        }
    }
}
