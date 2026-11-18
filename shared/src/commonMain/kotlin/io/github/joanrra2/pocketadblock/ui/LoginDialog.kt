package io.github.joanrra2.pocketadblock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.joanrra2.pocketadblock.board.BoardClient
import io.github.joanrra2.pocketadblock.board.BoardStatus
import io.github.joanrra2.pocketadblock.store.LoginGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LoginResult(
    val user: String,
    val password: String,
    val status: BoardStatus,
    val remember: Boolean,
    val requireUnlock: Boolean,
)

@Composable
fun LoginDialog(
    target: LoginTarget,
    strings: Strings,
    client: BoardClient,
    guard: LoginGuard,
    canAuthenticate: Boolean,
    onDismiss: () -> Unit,
    onLoggedIn: (LoginResult) -> Unit,
) {
    // "admin" is the documented default user name; the password is never pre-filled.
    var user by remember { mutableStateOf(target.saved?.user ?: "admin") }
    var password by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var requireUnlock by remember { mutableStateOf(target.saved?.requireUnlock ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // Seconds the board asked to wait (429); Connect stays off until then.
    var waitLeft by remember { mutableStateOf(guard.secondsLeft(target.address)) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(waitLeft > 0) {
        while (waitLeft > 0) {
            delay(1000)
            waitLeft = guard.secondsLeft(target.address)
        }
    }

    fun submit() {
        if (busy || waitLeft > 0 || user.isBlank() || password.isEmpty()) return
        val u = user.trim()
        // The same wrong password is never sent twice: the board already said no.
        if (guard.wasRefused(target.address, u, password)) {
            error = strings.wrongCredentials
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = client.login(target.address, u, password)
            busy = false
            when (result) {
                is BoardClient.Login.Ok -> {
                    guard.accepted(target.address)
                    onLoggedIn(LoginResult(u, password, result.status, remember, requireUnlock))
                }
                BoardClient.Login.WrongCredentials -> {
                    guard.refused(target.address, u, password)
                    error = strings.wrongCredentials
                }
                is BoardClient.Login.TooManyAttempts -> {
                    guard.wait(target.address, result.retryAfterSeconds)
                    waitLeft = guard.secondsLeft(target.address)
                }
                is BoardClient.Login.Refused -> error = strings.refused.fill(result.message)
                BoardClient.Login.NotBoard -> error = strings.notABoard
                BoardClient.Login.NotLocal -> error = strings.notLocal
                is BoardClient.Login.Unreachable -> error = strings.unreachable.fill(result.reason)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Tokens.s1,
        title = { Text(strings.loginTitle.fill(target.saved?.name ?: target.address.label), fontSize = 17.sp) },
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
                if (!target.setup) {
                    Toggle(strings.remember, remember) { remember = it }
                    if (remember && canAuthenticate) Toggle(strings.requireUnlock, requireUnlock) { requireUnlock = it }
                    if (remember) Text(strings.rememberNote, color = Tokens.muted, fontSize = 12.sp)
                }
                if (busy) Text(strings.checking, color = Tokens.muted, fontSize = 13.sp)
                val shown = if (waitLeft > 0) strings.tooManyAttempts.fill(waitLeft) else error
                shown?.let { Text(it, color = Tokens.bad, fontSize = 13.sp, modifier = Modifier.testTag("login-error")) }
            }
        },
        confirmButton = {
            Button(onClick = { submit() }, enabled = !busy && waitLeft == 0, modifier = Modifier.testTag("login-connect")) {
                Text(strings.connect)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(strings.cancel) }
        },
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Tokens.accent, uncheckedTrackColor = Tokens.s2),
        )
        Text(label, color = Tokens.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
