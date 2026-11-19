package io.github.joanrra2.pocketadblock.board

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

/**
 * Talks to a board's REST API, the same one its web page uses
 * (components/web/web_server.cpp in the firmware).
 */
class BoardClient(engineClient: HttpClient, private val logFailures: Boolean = false) {

    private val http = engineClient.config {
        install(HttpTimeout)
        expectSuccess = false
        followRedirects = false
    }

    sealed interface Probe {
        /** Answered like a board: 401 with `WWW-Authenticate: Basic realm="sinkhole"`. */
        data object Board : Probe
        data object NotBoard : Probe
        data object Unreachable : Probe
    }

    sealed interface Login {
        data class Ok(val status: BoardStatus) : Login
        data object WrongCredentials : Login
        data object NotBoard : Login
        data object NotLocal : Login
        data class Unreachable(val reason: String) : Login
        /**
         * The board answered 429 after too many wrong passwords: nothing may be sent to it
         * with a password for [retryAfterSeconds] (its Retry-After header).
         */
        data class TooManyAttempts(val retryAfterSeconds: Int) : Login
        /** The board refused the request with its own message (for example a 403). */
        data class Refused(val message: String) : Login
    }

    /** What a change (POST) gave. */
    sealed interface Result {
        data class Ok(val message: String) : Result
        data class Failed(val reason: String) : Result
        data class TooManyAttempts(val retryAfterSeconds: Int) : Result
    }

    /**
     * Asks GET /api/status without credentials. A board refuses with 401 and its
     * realm, which identifies it without sending any password.
     */
    suspend fun probe(address: BoardAddress, timeoutMs: Long = 1500): Probe {
        if (!address.isLocal) return Probe.NotBoard
        return try {
            val r = http.get(address.url("/api/status")) {
                timeout {
                    connectTimeoutMillis = timeoutMs
                    requestTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }
            }
            if (r.status.value == 401 && isSinkholeRealm(r.headers[HttpHeaders.WWWAuthenticate])) Probe.Board
            else Probe.NotBoard
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (logFailures) println("PocketProbe ${address.label}: ${e::class.simpleName}: ${e.message}")
            Probe.Unreachable
        }
    }

    /** Checks a user and password with GET /api/status and returns the board's state. */
    // From firmware 0.6 the first check after a restart or password change takes ~1 s
    // (PBKDF2); the default timeout leaves room for it on a slow Wi-Fi.
    suspend fun login(address: BoardAddress, user: String, password: String, timeoutMs: Long = 5000): Login {
        if (!address.isLocal) return Login.NotLocal
        return try {
            val r = http.get(address.url("/api/status")) {
                header(HttpHeaders.Authorization, basicAuth(user, password))
                timeout {
                    connectTimeoutMillis = timeoutMs
                    requestTimeoutMillis = timeoutMs
                    socketTimeoutMillis = timeoutMs
                }
            }
            when (r.status.value) {
                200 -> runCatching { Login.Ok(BoardStatus.parse(r.bodyAsText())) }.getOrElse { Login.NotBoard }
                401 -> if (isSinkholeRealm(r.headers[HttpHeaders.WWWAuthenticate])) Login.WrongCredentials else Login.NotBoard
                429 -> Login.TooManyAttempts(retryAfter(r))
                else -> boardMessage(r)?.let { Login.Refused(it) } ?: Login.NotBoard
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Login.Unreachable(e.message ?: e::class.simpleName ?: "error")
        }
    }

    /** GET /api/restarts (firmware 0.6): the board's clock and its restarts, newest first. */
    @kotlinx.serialization.Serializable
    data class Restarts(val time: Long = 0, val items: List<Restart> = emptyList()) {
        @kotlinx.serialization.Serializable
        data class Restart(
            /** Unix seconds; 0 when the clock wasn't set yet. */
            val at: Long = 0,
            /** Seconds it ran after this start (the first item: the current uptime). */
            val ranFor: Long = 0,
            val why: String = "",
            /** Crash, watchdog or power dip. */
            val unexpected: Boolean = false,
            val version: String = "",
        )
    }

    /** Reads the restart history, or null when the board can't be read or is older than 0.6. */
    suspend fun restarts(address: BoardAddress, user: String, password: String): Restarts? =
        getJson(address, user, password, "/api/restarts", Restarts.serializer())

    /**
     * What the app uses from GET /api/settings (the board never returns passwords).
     * [notifyAvailable]: the build can send alerts (Pro builds of firmware 0.6 and later).
     */
    @kotlinx.serialization.Serializable
    data class MqttSettings(
        val mqttUri: String = "",
        val mqttUser: String = "",
        val mqttPrefix: String = "",
        val notifyAvailable: Boolean = false,
    )

    /** Reads the settings, or null when the board can't be read. */
    suspend fun mqttSettings(address: BoardAddress, user: String, password: String): MqttSettings? =
        getJson(address, user, password, "/api/settings", MqttSettings.serializer())

    /** GET /api/notify (firmware 0.6 Pro): where alerts go and how sending went. Tokens never come back. */
    @kotlinx.serialization.Serializable
    data class Notify(
        val ntfyServer: String = "",
        val ntfyTopic: String = "",
        val ntfyTokenSet: Boolean = false,
        val tgTokenSet: Boolean = false,
        val tgChat: String = "",
        val events: Int = 0,
        val ntfy: Channel = Channel(),
        val telegram: Channel = Channel(),
    ) {
        /** [ago]: seconds since the last attempt, -1 = none since the board started. */
        @kotlinx.serialization.Serializable
        data class Channel(val configured: Boolean = false, val ok: Boolean = false, val ago: Long = -1, val error: String = "")

        val anyConfigured: Boolean get() = ntfy.configured || telegram.configured
    }

    /** Reads the alert settings, or null when the board can't be read or has no alerts. */
    suspend fun notify(address: BoardAddress, user: String, password: String): Notify? =
        getJson(address, user, password, "/api/notify", Notify.serializer())

    /** Sends a test alert to every channel set up on the board (POST /api/notify action=test). */
    suspend fun testNotify(address: BoardAddress, user: String, password: String): Result =
        post(address, user, password, "/api/notify") { append("action", "test") }

    private suspend fun <T> getJson(
        address: BoardAddress,
        user: String,
        password: String,
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T? {
        if (!address.isLocal) return null
        return try {
            val r = http.get(address.url(path)) {
                header(HttpHeaders.Authorization, basicAuth(user, password))
                timeout { requestTimeoutMillis = 5000; connectTimeoutMillis = 4000 }
            }
            if (r.status.value != 200) null
            else settingsJson.decodeFromString(serializer, r.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** How to change blocking with POST /api/blocking. */
    sealed interface Blocking {
        data object On : Blocking
        /** Off until turned back on (the board remembers it across restarts). */
        data object Off : Blocking
        /** Paused for 1..1440 minutes, then back on by itself. */
        data class Pause(val minutes: Int) : Blocking
    }

    /** Changes blocking. */
    suspend fun setBlocking(address: BoardAddress, user: String, password: String, change: Blocking): Result =
        post(address, user, password, "/api/blocking") {
            when (change) {
                Blocking.On -> append("mode", "on")
                Blocking.Off -> append("mode", "off")
                is Blocking.Pause -> {
                    append("mode", "pause")
                    append("minutes", change.minutes.toString())
                }
            }
        }

    /** Asks the board to look for a firmware update now (POST /api/firmware/check). */
    suspend fun checkFirmware(address: BoardAddress, user: String, password: String): Result =
        post(address, user, password, "/api/firmware/check")

    /**
     * Installs the update the board found (POST /api/firmware/install). The board
     * downloads it, checks it and restarts by itself.
     */
    suspend fun installFirmware(address: BoardAddress, user: String, password: String): Result =
        post(address, user, password, "/api/firmware/install")

    /** Restarts the board (POST /api/reboot). */
    suspend fun reboot(address: BoardAddress, user: String, password: String): Result =
        post(address, user, password, "/api/reboot")

    private suspend fun post(
        address: BoardAddress,
        user: String,
        password: String,
        path: String,
        form: io.ktor.http.ParametersBuilder.() -> Unit = {},
    ): Result {
        if (!address.isLocal) return Result.Failed("not a local address")
        return try {
            val r = http.submitForm(url = address.url(path), formParameters = parameters(form)) {
                header(HttpHeaders.Authorization, basicAuth(user, password))
                // The firmware refuses changes without it (CSRF guard, web_server.cpp).
                header("X-Requested-With", "sinkhole")
                timeout { requestTimeoutMillis = 8000; connectTimeoutMillis = 4000 }
            }
            when {
                r.status.isSuccess() -> Result.Ok(boardMessage(r) ?: "")
                r.status.value == 429 -> Result.TooManyAttempts(retryAfter(r))
                else -> Result.Failed(boardMessage(r) ?: "HTTP ${r.status.value}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failed(e.message ?: e::class.simpleName ?: "error")
        }
    }

    companion object {
        private val settingsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        /** Seconds to wait from a Retry-After header; 60 when it is missing or a date. */
        fun retryAfterSeconds(header: String?): Int =
            header?.trim()?.toIntOrNull()?.coerceIn(1, 24 * 3600) ?: 60

        private fun retryAfter(r: HttpResponse) = retryAfterSeconds(r.headers[HttpHeaders.RetryAfter])

        /** The `message` of the board's JSON answer (`{"ok":false,"message":"..."}`), if any. */
        private suspend fun boardMessage(r: HttpResponse): String? = runCatching {
            Json.parseToJsonElement(r.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }

        fun isSinkholeRealm(header: String?): Boolean {
            if (header == null || !header.trim().startsWith("Basic", ignoreCase = true)) return false
            return Regex("""realm\s*=\s*"?sinkhole"?""", RegexOption.IGNORE_CASE).containsMatchIn(header)
        }

        fun basicAuth(user: String, password: String): String =
            "Basic " + Base64.encode("$user:$password".encodeToByteArray())
    }
}
