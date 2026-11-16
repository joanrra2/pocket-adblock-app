package io.github.joanrra2.pocketadblock.ui

import io.github.joanrra2.pocketadblock.board.BoardAddress
import io.github.joanrra2.pocketadblock.board.BoardClient
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A page script that adds the board's Basic-auth header to every fetch() the board's
 * own page makes, active only on that board's origin.
 *
 * Used on iOS next to WKWebView's authentication challenge, as a safety net in case a
 * WebKit version doesn't pass fetch() challenges to the app. Values are JSON-encoded,
 * so quotes or backslashes in a password can't break out of the string.
 */
fun fetchAuthScript(address: BoardAddress, user: String, password: String): String {
    val host = Json.encodeToString(String.serializer(), address.label)
    val auth = Json.encodeToString(String.serializer(), BoardClient.basicAuth(user, password))
    return """
        (function () {
          if (location.protocol !== 'http:' || location.host !== $host) return;
          var auth = $auth, original = window.fetch;
          window.fetch = function (input, init) {
            init = init || {};
            var headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
            if (!headers.has('Authorization')) headers.set('Authorization', auth);
            init.headers = headers;
            return original.call(this, input, init);
          };
        })();
    """.trimIndent()
}
