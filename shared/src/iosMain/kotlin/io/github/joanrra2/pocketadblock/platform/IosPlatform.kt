package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.flow.emptyFlow
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.UIKit.UIApplication

/**
 * iPhone. Milestone M2 fills in mDNS (NWBrowser), the phone's address (getifaddrs), the
 * Keychain, Face ID and the web view. Until then passwords are kept in memory only.
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

    override val settings: KeyValueStore = object : KeyValueStore {
        private val defaults = NSUserDefaults.standardUserDefaults
        override fun get(key: String): String? = defaults.stringForKey(key)
        override fun put(key: String, value: String?) {
            if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
        }
    }

    // TODO(M2): Keychain. In memory until then, so nothing secret is written unencrypted.
    override val secrets: KeyValueStore = MemoryStore()

    override fun canAuthenticate(): Boolean = false

    override suspend fun authenticate(title: String, subtitle: String, cancel: String): Boolean = true
}
