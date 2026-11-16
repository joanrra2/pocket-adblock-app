package io.github.joanrra2.pocketadblock.net

/** IPv4 addresses as Ints (most significant byte first). */
object Ipv4 {
    fun parse(text: String): Int? {
        val parts = text.trim().split('.')
        if (parts.size != 4) return null
        var value = 0
        for (p in parts) {
            if (p.isEmpty() || p.length > 3 || (p.length > 1 && p[0] == '0')) return null
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            value = (value shl 8) or n
        }
        return value
    }

    fun format(v: Int): String = "${v ushr 24 and 255}.${v ushr 16 and 255}.${v ushr 8 and 255}.${v and 255}"

    /**
     * Addresses the app may talk plain HTTP to: private ranges (RFC 1918), link-local
     * and loopback. Everything else is refused, so a typo can't send a password to
     * the internet unencrypted.
     */
    fun isLocal(v: Int): Boolean {
        val a = v ushr 24 and 255
        val b = v ushr 16 and 255
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
            (a == 169 && b == 254) || a == 127
    }

    /**
     * The addresses to try when mDNS finds nothing: the phone's own subnet, capped at
     * the /24 that contains it (254 addresses), without the network and broadcast
     * addresses and without the phone itself. Closest addresses first is not useful
     * here (DHCP pools sit anywhere), so the order is simply ascending.
     */
    fun sweepHosts(address: Int, prefixLength: Int): List<Int> {
        val prefix = prefixLength.coerceIn(24, 30)
        val mask = -1 shl (32 - prefix)
        val network = address and mask
        val broadcast = network or mask.inv()
        return ((network + 1) until broadcast).filter { it != address }
    }
}

/** The phone's address on the current Wi-Fi. */
data class LocalNetwork(val address: Int, val prefixLength: Int) {
    override fun toString() = "${Ipv4.format(address)}/$prefixLength"
}
