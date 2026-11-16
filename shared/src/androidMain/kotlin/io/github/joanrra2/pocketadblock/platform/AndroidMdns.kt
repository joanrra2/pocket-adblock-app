package io.github.joanrra2.pocketadblock.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * mDNS through the system's NsdManager: no multicast lock and no location permission.
 * Boards announce `_http._tcp` with the instance name "DNS Sinkhole" (a second board on
 * the same network may get "DNS Sinkhole (2)"), so only those are resolved.
 */
class AndroidMdns(context: Context) : MdnsBrowser {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override fun browse(): Flow<String> = callbackFlow {
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                Log.d(TAG, "found '${info.serviceName}'")
                if (info.serviceName.startsWith(INSTANCE, ignoreCase = true)) toResolve.trySend(info)
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed: $errorCode")
                toResolve.close()
            }
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        // NsdManager resolves one service at a time on older Android versions.
        launch {
            for (info in toResolve) {
                val host = withTimeoutOrNull(4000) { resolve(info) }
                Log.d(TAG, "resolved '${info.serviceName}' -> $host")
                if (host != null) send(host)
            }
        }
        awaitClose { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    @Suppress("DEPRECATION") // resolveService: deprecated on API 34 but works on every version we support
    private suspend fun resolve(info: NsdServiceInfo): String? = suspendCancellableCoroutine { cont ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val addr = resolved.host as? Inet4Address
                if (cont.isActive) cont.resume(addr?.hostAddress)
            }
            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }

    private companion object {
        const val TAG = "PocketMdns"
        const val SERVICE_TYPE = "_http._tcp"
        const val INSTANCE = "DNS Sinkhole"
    }
}
