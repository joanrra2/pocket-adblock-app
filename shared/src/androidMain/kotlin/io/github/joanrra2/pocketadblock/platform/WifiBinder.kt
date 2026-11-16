package io.github.joanrra2.pocketadblock.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * Sends all of the app's traffic over Wi-Fi.
 *
 * A board's setup Wi-Fi ("Sinkhole-XXXX") has no internet, so Android keeps mobile data
 * as the default network and 192.168.4.1 would be unreachable. The app only ever talks
 * to boards on the local network, so binding the whole process (OkHttp and the WebView)
 * to the current Wi-Fi is always right. The binding follows the Wi-Fi as it comes and
 * goes, and is dropped when there is none.
 */
object WifiBinder {
    private const val TAG = "PocketWifi"
    private var started = false

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // Also a Wi-Fi that Android says has no internet (the setup access point).
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (cm.boundNetworkForProcess != network) {
                    Log.d(TAG, "binding to Wi-Fi $network")
                    cm.bindProcessToNetwork(network)
                }
            }

            override fun onLost(network: Network) {
                if (cm.boundNetworkForProcess == network) {
                    Log.d(TAG, "Wi-Fi $network lost, unbinding")
                    cm.bindProcessToNetwork(null)
                }
            }
        })
    }
}
