package io.github.joanrra2.pocketadblock.board

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
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
    suspend fun login(address: BoardAddress, user: String, password: String, timeoutMs: Long = 4000): Login {
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
                else -> Login.NotBoard
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Login.Unreachable(e.message ?: e::class.simpleName ?: "error")
        }
    }

    /** The board's MQTT settings from GET /api/settings (the board never returns the password). */
    @kotlinx.serialization.Serializable
    data class MqttSettings(val mqttUri: String = "", val mqttUser: String = "", val mqttPrefix: String = "")

    /** Reads the MQTT settings, or null when the board can't be read. */
    suspend fun mqttSettings(address: BoardAddress, user: String, password: String): MqttSettings? {
        if (!address.isLocal) return null
        return try {
            val r = http.get(address.url("/api/settings")) {
                header(HttpHeaders.Authorization, basicAuth(user, password))
                timeout { requestTimeoutMillis = 5000; connectTimeoutMillis = 4000 }
            }
            if (r.status.value != 200) null
            else settingsJson.decodeFromString(MqttSettings.serializer(), r.bodyAsText())
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

    /** Changes blocking. Returns null on success, or the reason it failed. */
    suspend fun setBlocking(address: BoardAddress, user: String, password: String, change: Blocking): String? {
        if (!address.isLocal) return "not a local address"
        return try {
            val r = http.submitForm(
                url = address.url("/api/blocking"),
                formParameters = parameters {
                    when (change) {
                        Blocking.On -> append("mode", "on")
                        Blocking.Off -> append("mode", "off")
                        is Blocking.Pause -> {
                            append("mode", "pause")
                            append("minutes", change.minutes.toString())
                        }
                    }
                },
            ) {
                header(HttpHeaders.Authorization, basicAuth(user, password))
                // The firmware refuses changes without it (CSRF guard, web_server.cpp).
                header("X-Requested-With", "sinkhole")
                timeout { requestTimeoutMillis = 5000; connectTimeoutMillis = 4000 }
            }
            if (r.status.isSuccess()) null else "HTTP ${r.status.value}: ${r.bodyAsText().take(120)}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e::class.simpleName ?: "error"
        }
    }

    companion object {
        private val settingsJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        fun isSinkholeRealm(header: String?): Boolean {
            if (header == null || !header.trim().startsWith("Basic", ignoreCase = true)) return false
            return Regex("""realm\s*=\s*"?sinkhole"?""", RegexOption.IGNORE_CASE).containsMatchIn(header)
        }

        fun basicAuth(user: String, password: String): String =
            "Basic " + Base64.encode("$user:$password".encodeToByteArray())
    }
}
