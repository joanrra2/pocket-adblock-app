package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.discovery.FoundBoard
import kotlinx.coroutines.launch

@Composable
fun LoginDialog(
    board: FoundBoard,
    strings: Strings,
    client: BoardClient,
    onDismiss: () -> Unit,
    onLoggedIn: (Session) -> Unit,
) {
    // "admin" is the documented default user name; the password is never pre-filled.
    var user by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || user.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            val result = client.login(board.address, user.trim(), password)
            busy = false
            when (result) {
                is BoardClient.Login.Ok -> onLoggedIn(Session(board, user.trim(), password, result.status))
                BoardClient.Login.WrongCredentials -> error = strings.wrongCredentials
                BoardClient.Login.NotBoard -> error = strings.notABoard
                BoardClient.Login.NotLocal -> error = strings.notLocal
                is BoardClient.Login.Unreachable -> error = strings.unreachable.fill(result.reason)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Tokens.s1,
        title = { Text(strings.loginTitle.fill(board.address.label), fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it; error = null },
                    label = { Text(strings.user) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("login-user"),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(strings.password) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    modifier = Modifier.fillMaxWidth().testTag("login-password"),
                )
                if (busy) Text(strings.checking, color = Tokens.muted, fontSize = 13.sp)
                error?.let { Text(it, color = Tokens.bad, fontSize = 13.sp, modifier = Modifier.testTag("login-error")) }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.testTag("login-connect")) {
                Text(strings.connect)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) }
        },
    )
}
