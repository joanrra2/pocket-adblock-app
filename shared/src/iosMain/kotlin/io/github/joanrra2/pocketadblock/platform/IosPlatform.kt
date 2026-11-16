@file:OptIn(ExperimentalForeignApi::class)

package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSLocale
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

class IosPlatform : Platform {
    override val mdns: MdnsBrowser = IosMdns()

    override fun localNetwork(): LocalNetwork? = wifiIpv4()

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

    override val secrets: KeyValueStore = KeychainStore()

    // Face ID / Touch ID, falling back to the passcode (LAPolicyDeviceOwnerAuthentication).
    override fun canAuthenticate(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override suspend fun authenticate(title: String, subtitle: String, cancel: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val context = LAContext()
            context.localizedCancelTitle = cancel
            context.evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = "$title. $subtitle") { ok, _ ->
                if (cont.isActive) cont.resume(ok)
            }
            cont.invokeOnCancellation { context.invalidate() }
        }
}
