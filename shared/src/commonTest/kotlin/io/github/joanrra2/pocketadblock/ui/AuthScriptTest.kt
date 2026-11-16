package io.github.joanrra2.pocketadblock.ui

import io.github.joanrra2.pocketadblock.board.BoardAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthScriptTest {
    @Test
    fun onlyActiveOnTheBoardsOrigin() {
        val s = fetchAuthScript(BoardAddress("192.168.18.23"), "admin", "pw")
        assertTrue("location.host !== \"192.168.18.23\"" in s)
        assertTrue("\"Basic YWRtaW46cHc=\"" in s)
    }

    @Test
    fun passwordCantBreakOutOfTheString() {
        // The header is base64, so quotes in a password never reach the script as quotes.
        val s = fetchAuthScript(BoardAddress("192.168.18.23", 8080), "admin", "a\"b'c\\</script>")
        assertFalse("</script>" in s)
        assertFalse("a\"b" in s)
        assertTrue("location.host !== \"192.168.18.23:8080\"" in s)
    }
}
