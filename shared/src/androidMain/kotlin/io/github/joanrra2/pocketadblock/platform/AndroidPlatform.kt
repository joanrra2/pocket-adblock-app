package io.github.joanrra2.pocketadblock.platform

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.github.joanrra2.pocketadblock.net.LocalNetwork
import io.github.joanrra2.pocketadblock.wake.PocketWakeLink
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.util.Locale
import kotlin.coroutines.resume

class AndroidPlatform(private val activity: FragmentActivity) : Platform {
    private val context = activity.applicationContext

    override val mdns: MdnsBrowser = AndroidMdns(context)
    override val settings: KeyValueStore = PrefsStore(context, "app")
    override val secrets: KeyValueStore = KeystoreStore(context)

    init {
        WifiBinder.start(context)
    }

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

    override fun openPocketWake(link: String): Boolean {
        // Release first, then a debug build of Pocket Wake (package suffix .debug).
        for (pkg in listOf(PocketWakeLink.ANDROID_PACKAGE, PocketWakeLink.ANDROID_PACKAGE + ".debug")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return true
        }
        openInBrowser(link)
        return false
    }

    // Fingerprint/face, or the screen lock PIN/pattern where Android allows combining them.
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    override fun canAuthenticate(): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    override suspend fun authenticate(title: String, subtitle: String, cancel: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                    // onAuthenticationFailed: a finger that didn't match; the prompt lets the user retry.
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .apply { if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText(cancel) }
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
