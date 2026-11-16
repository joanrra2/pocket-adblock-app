package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.flow.emptyFlow
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIApplication

/**
 * iPhone. Milestone M2 fills in mDNS (NWBrowser), the phone's address (getifaddrs) and
 * the web view; until then discovery works with typed addresses only.
 */
class IosPlatform : Platform {
    override val mdns: MdnsBrowser = MdnsBrowser { emptyFlow() }

    override fun localNetwork(): LocalNetwork? = null

    override fun httpClient(): HttpClient = HttpClient(Darwin)

    override val languageCode: String get() = NSLocale.currentLocale.languageCode

    override fun openInBrowser(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any>(), null)
    }
}
