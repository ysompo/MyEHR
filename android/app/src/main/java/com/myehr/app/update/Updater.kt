package com.myehr.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.myehr.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(val versionCode: Int, val versionName: String, val sha256: String)

/**
 * Checks the fixed "android-latest" GitHub release and installs newer signed builds.
 * Android itself refuses any APK not signed with the same key as the installed app,
 * and the download must also match the SHA-256 published next to it.
 */
object Updater {
    private const val BASE = "https://github.com/ysompo/MyEHR/releases/download/android-latest"
    private const val PREFS = "myehr_update"

    val currentVersion: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    private fun get(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000; readTimeout = 60_000
        }

    /** Newer version on GitHub, or null. Throws on network errors. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val text = get("$BASE/version.json").run { try { inputStream.bufferedReader().readText() } finally { disconnect() } }
        val o = Json.parseToJsonElement(text).jsonObject
        val info = UpdateInfo(
            o["versionCode"]!!.jsonPrimitive.int,
            o["versionName"]?.jsonPrimitive?.content ?: "",
            o["sha256"]!!.jsonPrimitive.content.lowercase(),
        )
        info.takeIf { it.versionCode > currentVersion }
    }

    /** Checks at most once a day; returns an update if one is available. Never throws. */
    suspend fun dailyCheck(context: Context): UpdateInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0) < 24 * 3600_000L) return null
        return runCatching { check() }.getOrNull().also { prefs.edit().putLong("last_check", now).apply() }
    }

    /** Downloads, verifies the SHA-256, and hands the APK to the system installer (which asks the user). */
    suspend fun install(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        val conn = get("$BASE/MyEHR.apk")
        val total = conn.contentLengthLong
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); digest.update(buf, 0, n); done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        } finally { conn.disconnect() }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha != info.sha256) { file.delete(); error("checksum mismatch") }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("MyEHR.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
        file.delete()
    }
}

/** Shows the system "install update?" screen when the installer asks for the user's confirmation. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
