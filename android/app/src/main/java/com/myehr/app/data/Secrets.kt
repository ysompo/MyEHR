package com.myehr.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The Anthropic API key, encrypted with a hardware-backed Keystore key. Plus first-time consent flags. */
class Secrets(context: Context) {
    private val prefs = context.getSharedPreferences("myehr_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()
            )
        }.generateKey()
    }

    var apiKey: String?
        get() = prefs.getString(PREF_KEY, null)?.let { stored ->
            runCatching {
                val raw = Base64.decode(stored, Base64.NO_WRAP)
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
                    String(doFinal(raw.copyOfRange(12, raw.size)))
                }
            }.getOrNull()
        }
        set(value) {
            if (value.isNullOrBlank()) { prefs.edit().remove(PREF_KEY).apply(); return }
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val blob = c.iv + c.doFinal(value.trim().toByteArray())
            prefs.edit().putString(PREF_KEY, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        }

    fun consented(kind: String) = prefs.getBoolean("consent_$kind", false)
    fun setConsented(kind: String) = prefs.edit().putBoolean("consent_$kind", true).apply()
    fun resetConsents() = prefs.edit().apply { prefs.all.keys.filter { it.startsWith("consent_") }.forEach { remove(it) } }.apply()

    private companion object {
        const val ALIAS = "myehr_api_key"
        const val PREF_KEY = "anthropic_api_key"
    }
}
