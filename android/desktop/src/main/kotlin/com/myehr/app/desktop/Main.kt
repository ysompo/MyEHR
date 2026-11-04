package com.myehr.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.myehr.app.ui.App
import com.myehr.app.ui.LocalPlatform
import com.myehr.app.ui.Look
import com.myehr.app.ui.Looks
import com.myehr.app.ui.MyEhrTheme
import com.myehr.app.ui.PineLook
import com.myehr.app.ui.PlatformFonts
import com.myehr.app.ui.TextSize
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val prefs = Preferences.userRoot().node("myehr")

/** App password: stored only as a salted PBKDF2 hash in the OS keychain. */
private object AppPassword {
    private fun hash(pw: String, salt: ByteArray): String =
        Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pw.toCharArray(), salt, 210_000, 256)).encoded)
    val isSet: Boolean get() = Keychain.get("app-password") != null
    fun set(pw: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        Keychain.set("app-password", Base64.getEncoder().encodeToString(salt) + ":" + hash(pw, salt))
    }
    fun check(pw: String): Boolean {
        val (s, h) = (Keychain.get("app-password") ?: return false).split(":")
        return MessageDigest.isEqual(hash(pw, Base64.getDecoder().decode(s)).toByteArray(), h.toByteArray())
    }
}

@Composable
private fun PasswordGate(onUnlock: () -> Unit) {
    val firstTime = remember { !AppPassword.isSet }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MyEHR", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(if (firstTime) "בחרו סיסמה לפתיחת האפליקציה במחשב" else "הזינו סיסמה לפתיחה", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        OutlinedTextField(pw, { pw = it }, label = { Text("סיסמה") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.padding(top = 12.dp))
        if (firstTime) OutlinedTextField(pw2, { pw2 = it }, label = { Text("שוב") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(modifier = Modifier.padding(top = 16.dp), onClick = {
            when {
                firstTime && pw.length < 6 -> error = "לפחות 6 תווים"
                firstTime && pw != pw2 -> error = "הסיסמאות לא תואמות"
                firstTime -> { AppPassword.set(pw); onUnlock() }
                AppPassword.check(pw) -> onUnlock()
                else -> error = "סיסמה שגויה"
            }
        }) { Text(if (firstTime) "שמירה" else "פתיחה") }
    }
}

fun main() = application {
    PlatformFonts.family = DesktopPlatform.rubik
    val platform = remember { DesktopPlatform() }
    Window(onCloseRequest = ::exitApplication, title = "MyEHR", state = rememberWindowState(width = 560.dp, height = 900.dp)) {
        var textSize by remember { mutableStateOf(runCatching { TextSize.valueOf(prefs.get("text_size", "NORMAL")) }.getOrDefault(TextSize.NORMAL)) }
        var look by remember { mutableStateOf<Look>(Looks.firstOrNull { it.name == prefs.get("look", "") } ?: PineLook) }
        var unlocked by remember { mutableStateOf(false) }
        CompositionLocalProvider(LocalPlatform provides platform) {
            MyEhrTheme(textSize, look = look) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Phone-shaped column, centered: the screens are designed for this width.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                            if (!unlocked) PasswordGate { unlocked = true }
                            else App(textSize, { textSize = it; prefs.put("text_size", it.name) }, look, { look = it; prefs.put("look", it.name) })
                        }
                    }
                }
            }
        }
    }
}
