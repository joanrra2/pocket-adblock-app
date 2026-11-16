package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow

/** What each operating system provides to the shared code. */
interface Platform {
    /** Boards announced by mDNS (`_http._tcp`, instance "DNS Sinkhole"): their IPv4 addresses. */
    val mdns: MdnsBrowser

    /** The phone's IPv4 address and prefix on the current Wi-Fi, or null when it has none. */
    fun localNetwork(): LocalNetwork?

    /** An HTTP client on this platform's engine. */
    fun httpClient(): HttpClient

    /** Two-letter language of the device ("es", "en", ...). */
    val languageCode: String

    /** Opens a link in the system browser. */
    fun openInBrowser(url: String)

    /** Plain app settings (the list of saved boards). Not for passwords. */
    val settings: KeyValueStore

    /** Encrypted storage for passwords (Android Keystore, iOS Keychain). */
    val secrets: KeyValueStore

    /** True when the phone has a fingerprint, face or screen lock the app can ask for. */
    fun canAuthenticate(): Boolean

    /** Asks for the fingerprint, face or screen lock. True when the user passed it. */
    suspend fun authenticate(title: String, subtitle: String, cancel: String): Boolean
}

fun interface MdnsBrowser {
    /** Emits the address of each board found, for as long as it is collected. */
    fun browse(): Flow<String>
}

interface KeyValueStore {
    fun get(key: String): String?

    /** Stores [value], or removes the key when it is null. */
    fun put(key: String, value: String?)
}

/** In-memory store, for tests and for platforms that don't persist yet. */
class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}
