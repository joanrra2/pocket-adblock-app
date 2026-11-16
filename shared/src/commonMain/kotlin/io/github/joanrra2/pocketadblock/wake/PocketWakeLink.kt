package io.github.joanrra2.pocketadblock.wake

import io.ktor.http.encodeURLParameter

/**
 * The setup link for the Pocket Wake app (remote Wake-on-LAN over MQTT): the same
 * "contract v1" as the QR code on the board's Wake-on-LAN page, built here from the
 * board's own MQTT settings. It never contains a password.
 *
 *   https://joanrra2.github.io/pocket-wake/#setup?v=1&host=H&port=8883&tls=1&wsport=8084&wspath=%2Fmqtt&prefix=P[&user=U]
 */
object PocketWakeLink {
    const val WEB_APP = "https://joanrra2.github.io/pocket-wake/"
    const val ANDROID_PACKAGE = "io.github.joanrra2.pocketwake"

    data class Broker(val host: String, val port: Int, val tls: Boolean, val webSocketPort: Int?)

    /** True for links meant for Pocket Wake, e.g. from the board page's "Open the web app". */
    fun isSetupLink(url: String): Boolean = url.startsWith(WEB_APP) && "#setup?" in url

    /**
     * Reads the board's Broker URI like the firmware's web page does:
     * mqtt(s)://, ssl://, tcp:// or ws(s)://, optional port. WebSocket URIs give the
     * web app its port; the Android app then uses the usual 8883 / 1883.
     */
    fun parseBroker(uri: String): Broker? {
        val m = Regex("""^(mqtts?|wss?|ssl|tcp)://(\[[^\]]+]|[^/:?#]+)(?::(\d+))?""", RegexOption.IGNORE_CASE)
            .find(uri.trim()) ?: return null
        val scheme = m.groupValues[1].lowercase()
        val tls = scheme == "mqtts" || scheme == "ssl" || scheme == "wss"
        val ws = scheme.startsWith("ws")
        val port = m.groupValues[3].toIntOrNull()
        val defaultPort = if (tls) 8883 else 1883
        return Broker(
            host = m.groupValues[2].removePrefix("[").removeSuffix("]"),
            port = if (ws) defaultPort else port ?: defaultPort,
            tls = tls,
            webSocketPort = if (ws) port else null,
        )
    }

    /** The link, or null when the board has no broker set up. */
    fun build(
        mqttUri: String,
        prefix: String,
        phoneUser: String = "",
        webSocketPort: Int? = null,
        webSocketPath: String = "/mqtt",
    ): String? {
        val b = parseBroker(mqttUri) ?: return null
        val params = linkedMapOf(
            "v" to "1",
            "host" to b.host,
            "port" to b.port.toString(),
            "tls" to if (b.tls) "1" else "0",
            "wsport" to (webSocketPort ?: b.webSocketPort ?: 8084).toString(),
            "wspath" to webSocketPath.trim().ifEmpty { "/mqtt" },
            "prefix" to prefix.trim().ifEmpty { "sinkhole" },
        )
        phoneUser.trim().takeIf { it.isNotEmpty() }?.let { params["user"] = it }
        return WEB_APP + "#setup?" + params.entries.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
    }
}
