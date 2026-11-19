package io.github.joanrra2.pocketadblock.notify

/**
 * Links to a board's ntfy topic, so the phone can subscribe without typing it.
 * The board stores the server as a full URL ("https://ntfy.sh", no trailing slash).
 */
object NtfyLink {
    private val TOPIC = Regex("[A-Za-z0-9_-]{1,64}")

    /** Host (and port/path) of [server] and whether it uses HTTPS; null when it isn't an http(s) URL. */
    private fun split(server: String): Pair<String, Boolean>? {
        val s = server.trim().trimEnd('/')
        val secure = when {
            s.startsWith("https://", ignoreCase = true) -> true
            s.startsWith("http://", ignoreCase = true) -> false
            else -> return null
        }
        val rest = s.substringAfter("://")
        if (rest.isEmpty() || rest.any { it.isWhitespace() || it == '?' || it == '#' || it == '@' }) return null
        return rest to secure
    }

    /**
     * `ntfy://host/topic`, which the ntfy Android app opens on its subscribe screen
     * (`?secure=false` for a plain-HTTP server). Null when server or topic aren't usable.
     */
    fun subscribe(server: String, topic: String): String? {
        if (!TOPIC.matches(topic)) return null
        val (rest, secure) = split(server) ?: return null
        return "ntfy://$rest/$topic" + if (secure) "" else "?secure=false"
    }

    /** The topic's web page (the ntfy web app, which can also subscribe). */
    fun web(server: String, topic: String): String? {
        if (!TOPIC.matches(topic)) return null
        val (rest, secure) = split(server) ?: return null
        return (if (secure) "https://" else "http://") + "$rest/$topic"
    }

    /** "ntfy.sh/pocket-x1y2" for showing. */
    fun label(server: String, topic: String): String =
        (split(server)?.first ?: server.trim()) + "/" + topic
}
