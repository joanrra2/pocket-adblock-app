package io.github.joanrra2.pocketadblock.board

import io.github.joanrra2.pocketadblock.net.Ipv4

/** Where a board answers: an IPv4 address or a .local name, and the HTTP port. */
data class BoardAddress(val host: String, val port: Int = 80) {

    /** True when the app may use plain HTTP with this address (see [Ipv4.isLocal]). */
    val isLocal: Boolean
        get() = Ipv4.parse(host)?.let(Ipv4::isLocal)
            ?: (host.endsWith(".local", ignoreCase = true) || host.equals("localhost", ignoreCase = true))

    fun url(path: String = "/"): String = "http://$label$path"

    /** What people see and type: "192.168.1.50", or "192.168.1.50:8080" off port 80. */
    val label: String get() = if (port == 80) host else "$host:$port"

    override fun toString() = label

    companion object {
        /**
         * Reads what someone typed: "192.168.1.50", "sinkhole.local",
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
