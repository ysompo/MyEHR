package com.myehr.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.myehr.app.ui.App

class MainActivity : FragmentActivity() {
    private val unlocked = mutableStateOf(false)
    private val lockMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, and hidden in the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(Modifier.fillMaxSize()) {
                        if (unlocked.value) App() else LockScreen()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!unlocked.value) authenticate()
    }

    override fun onStop() {
        super.onStop()
        // Lock every time the app leaves the screen, unless it's just rotating.
        if (!isChangingConfigurations) unlocked.value = false
    }

    private fun authenticate() {
        val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            lockMessage.value = "יש להגדיר נעילת מסך (טביעת אצבע או קוד) בטלפון כדי להשתמש באפליקציה."
            return
        }
        lockMessage.value = null
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked.value = true }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { lockMessage.value = errString.toString() }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("התיק הרפואי שלי")
                .setSubtitle("אימות כדי לפתוח")
                .setAllowedAuthenticators(allowed)
                .build()
        )
    }

    @androidx.compose.runtime.Composable
    private fun LockScreen() {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("האפליקציה נעולה", style = MaterialTheme.typography.headlineSmall)
            lockMessage.value?.let { Text(it, Modifier.padding(top = 12.dp)) }
            Button(onClick = { authenticate() }, modifier = Modifier.padding(top = 24.dp)) { Text("פתיחה") }
        }
    }
}
