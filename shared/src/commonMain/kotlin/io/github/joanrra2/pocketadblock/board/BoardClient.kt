package io.github.joanrra2.pocketadblock.board

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
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
            val auth = r.headers[HttpHeaders.WWWAuthenticate]
            if (logFailures) println("PocketProbe ${address.label}: HTTP ${r.status.value} auth=$auth")
            if (r.status.value == 401 && isSinkholeRealm(auth)) Probe.Board
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

    companion object {
        fun isSinkholeRealm(header: String?): Boolean {
            if (header == null || !header.trim().startsWith("Basic", ignoreCase = true)) return false
            return Regex("""realm\s*=\s*"?sinkhole"?""", RegexOption.IGNORE_CASE).containsMatchIn(header)
        }

        fun basicAuth(user: String, password: String): String =
            "Basic " + Base64.encode("$user:$password".encodeToByteArray())
    }
}
