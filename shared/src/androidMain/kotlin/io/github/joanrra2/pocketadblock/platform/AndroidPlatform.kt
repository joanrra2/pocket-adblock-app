package io.github.joanrra2.pocketadblock.platform

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.Inet4Address
import java.util.Locale

class AndroidPlatform(private val context: Context) : Platform {

    override val mdns: MdnsBrowser = AndroidMdns(context)

    /** The IPv4 address on Wi-Fi (or Ethernet), even when mobile data is the default network. */
    @Suppress("DEPRECATION") // allNetworks: still the simplest way to look at every network
    override fun localNetwork(): LocalNetwork? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val candidates = listOfNotNull(cm.activeNetwork) + cm.allNetworks.toList()
        for (network in candidates.distinct()) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val lan = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!lan) continue
            val link = cm.getLinkProperties(network)?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address } ?: continue
            val b = link.address.address
            val ip = (b[0].toInt() and 255 shl 24) or (b[1].toInt() and 255 shl 16) or
                (b[2].toInt() and 255 shl 8) or (b[3].toInt() and 255)
            return LocalNetwork(ip, link.prefixLength)
        }
        return null
    }

    override fun httpClient(): HttpClient = HttpClient(OkHttp)

    override val languageCode: String get() = Locale.getDefault().language

    override fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
