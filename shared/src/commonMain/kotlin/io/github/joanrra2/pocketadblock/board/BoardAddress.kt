package io.github.joanrra2.pocketadblock.board

import io.github.joanrra2.pocketadblock.net.Ipv4

/** Where a board answers: an IPv4 address or a home-network name, and the HTTP port. */
data class BoardAddress(val host: String, val port: Int = 80) {

    /**
     * True when the app may use plain HTTP with this address: a private IPv4 address
     * (see [Ipv4.isLocal]) or a home-network name. These are the names the firmware
     * accepts in the Host header: a single label ("sinkhole") or one ending in .local,
     * .lan, .home, .home.arpa, .internal or .localdomain.
     */
    val isLocal: Boolean
        get() = Ipv4.parse(host)?.let(Ipv4::isLocal) ?: isLocalName(host)

    fun url(path: String = "/"): String = "http://$label$path"

    /** What people see and type: "192.168.1.50", or "192.168.1.50:8080" off port 80. */
    val label: String get() = if (port == 80) host else "$host:$port"

    override fun toString() = label

    companion object {
        private val localSuffixes = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain")

        fun isLocalName(host: String): Boolean {
            val h = host.lowercase().trimEnd('.')
            if (h.isEmpty() || h.all { it.isDigit() || it == '.' }) return false
            return '.' !in h || localSuffixes.any { h.endsWith(it) && h.length > it.length }
        }

        /**
         * Reads what someone typed: "192.168.1.50", "sinkhole.local", "sinkhole.lan",
         * "http://192.168.1.50:8080/" and so on. Returns null when it isn't an address.
         */
        fun parse(input: String): BoardAddress? {
            var s = input.trim()
            s = s.removePrefix("http://").removePrefix("HTTP://")
            s = s.substringBefore('/').substringBefore('?').substringBefore('#')
            if (s.isEmpty() || s.startsWith("https://", ignoreCase = true)) return null
            val colon = s.lastIndexOf(':')
            val host: String
            val port: Int
            if (colon > 0) {
                host = s.substring(0, colon)
                port = s.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            } else {
                host = s
                port = 80
            }
            val validName = host.isNotEmpty() && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
            if (!validName) return null
            // Something that looks like an IPv4 address must be a valid one.
            if (host.all { it.isDigit() || it == '.' } && Ipv4.parse(host) == null) return null
            return BoardAddress(host.lowercase(), port)
        }
    }
}
