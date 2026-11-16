@file:OptIn(ExperimentalForeignApi::class)

package io.github.joanrra2.pocketadblock.platform

import io.github.joanrra2.pocketadblock.net.Ipv4
import io.github.joanrra2.pocketadblock.net.LocalNetwork
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.sockaddr
import platform.posix.sockaddr_in

/** in_addr.s_addr is stored in network byte order; iPhones are little-endian. */
internal fun networkToHost(v: UInt): Int =
    (((v and 0xFFu) shl 24) or (((v shr 8) and 0xFFu) shl 16) or (((v shr 16) and 0xFFu) shl 8) or (v shr 24)).toInt()

/** The IPv4 address in a sockaddr, or null when it isn't IPv4. */
internal fun ipv4Of(addr: CPointer<sockaddr>?): Int? {
    if (addr == null || addr.pointed.sa_family.toInt() != AF_INET) return null
    return networkToHost(addr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr)
}

/**
 * The phone's Wi-Fi address (interface en0) and prefix length, from getifaddrs.
 * Needs no permission; the local-network prompt only appears when the app talks to the LAN.
 */
internal fun wifiIpv4(): LocalNetwork? = memScoped {
    val list = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(list.ptr) != 0) return null
    try {
        var p = list.value
        while (p != null) {
            val ifa = p.pointed
            val ip = ipv4Of(ifa.ifa_addr)
            if (ifa.ifa_name?.toKString() == "en0" && ip != null && Ipv4.isLocal(ip)) {
                val mask = ipv4Of(ifa.ifa_netmask) ?: -256
                return LocalNetwork(ip, mask.countOneBits())
            }
            p = ifa.ifa_next
        }
        null
    } finally {
        freeifaddrs(list.value)
    }
}
