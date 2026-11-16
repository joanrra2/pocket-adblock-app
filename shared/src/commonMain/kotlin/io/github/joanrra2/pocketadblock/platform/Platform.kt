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
}

fun interface MdnsBrowser {
    /** Emits the address of each board found, for as long as it is collected. */
    fun browse(): Flow<String>
}
