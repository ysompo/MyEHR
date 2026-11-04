package com.myehr.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.myehr.app.ui.App
import com.myehr.app.ui.MyEhrTheme
import com.myehr.app.ui.UiPrefs
import androidx.compose.runtime.remember
import com.myehr.app.ui.LockScreen

class MainActivity : FragmentActivity() {
    private val unlocked = mutableStateOf(false)
    private val lockMessage = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, and hidden in the recent-apps preview.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val textSize = remember { mutableStateOf(UiPrefs.textSize(this)) }
            MyEhrTheme(textSize.value) {
                run {
                    // The app stays composed under the lock screen, so a login in progress
                    // (e.g. while reading the SMS code in another app) isn't lost.
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize()) { App(textSize.value) { textSize.value = it; UiPrefs.setTextSize(this@MainActivity, it) } }
                        if (!unlocked.value) Surface(Modifier.fillMaxSize()) { LockScreen(lockMessage.value) { authenticate() } } // M3 Surface blocks touches
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
}
