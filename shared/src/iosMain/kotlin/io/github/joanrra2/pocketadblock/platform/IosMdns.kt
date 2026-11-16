@file:OptIn(ExperimentalForeignApi::class)

package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.Ipv4
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject

/**
 * mDNS with Bonjour (NSNetServiceBrowser). Needs NSBonjourServices = _http._tcp and
 * NSLocalNetworkUsageDescription in Info.plist; iOS asks the user once.
 * Boards announce `_http._tcp` with the instance name "DNS Sinkhole".
 */
class IosMdns : MdnsBrowser {
    override fun browse(): Flow<String> = callbackFlow {
        val delegate = BrowserDelegate { host -> trySend(host) }
        val browser = NSNetServiceBrowser()
        browser.delegate = delegate
        browser.searchForServicesOfType("_http._tcp.", inDomain = "local.")
        awaitClose {
            browser.stop()
            delegate.stop()
        }
    }.flowOn(Dispatchers.Main) // Bonjour callbacks need a run loop: the main one.
}

private class BrowserDelegate(private val onHost: (String) -> Unit) :
    NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {

    // Services being resolved must be kept alive until they answer.
    private val resolving = mutableListOf<NSNetService>()

    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        if (!didFindService.name.startsWith("DNS Sinkhole", ignoreCase = true)) return
        didFindService.delegate = this
        resolving += didFindService
        didFindService.resolveWithTimeout(4.0)
    }

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        sender.addresses?.forEach { item ->
            val data = item as? NSData ?: return@forEach
            ipv4Of(data.bytes?.reinterpret())?.let { onHost(Ipv4.format(it)) }
        }
        resolving.remove(sender)
    }

    fun stop() {
        resolving.forEach { it.stop() }
        resolving.clear()
    }
}
