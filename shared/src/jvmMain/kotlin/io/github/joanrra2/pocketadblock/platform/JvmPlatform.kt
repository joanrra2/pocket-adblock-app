package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.emptyFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Desktop JVM: used only to run the shared code on a PC (tests, and the opt-in check
 * against a real board). No mDNS here; discovery relies on the address sweep.
 */
class JvmPlatform(private val network: LocalNetwork? = null) : Platform {
    override val mdns: MdnsBrowser = MdnsBrowser { emptyFlow() }

    override fun localNetwork(): LocalNetwork? = network ?: firstPrivateIpv4()

    override fun httpClient(): HttpClient = HttpClient(OkHttp)

    override val languageCode: String get() = Locale.getDefault().language

    override fun openInBrowser(url: String) = Unit

    override fun openPocketWake(link: String): Boolean = false

    override fun openNtfy(appLink: String, webLink: String): Boolean = false

    override val settings: KeyValueStore = MemoryStore()
    override val secrets: KeyValueStore = MemoryStore()

    override fun canAuthenticate(): Boolean = false

    override suspend fun authenticate(title: String, subtitle: String, cancel: String): Boolean = true

    private fun firstPrivateIpv4(): LocalNetwork? {
        for (nif in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
            for (ia in nif.interfaceAddresses) {
                val a = ia.address as? Inet4Address ?: continue
                if (!a.isSiteLocalAddress) continue
                val b = a.address
                val ip = (b[0].toInt() and 255 shl 24) or (b[1].toInt() and 255 shl 16) or
                    (b[2].toInt() and 255 shl 8) or (b[3].toInt() and 255)
                return LocalNetwork(ip, ia.networkPrefixLength.toInt())
            }
        }
        return null
    }
}
