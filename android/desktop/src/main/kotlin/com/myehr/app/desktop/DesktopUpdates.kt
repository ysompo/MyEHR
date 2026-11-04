package com.myehr.app.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.prefs.Preferences

/** Checks the "desktop-latest" GitHub release; installing is a normal download the user opens. */
object DesktopUpdates {
    private const val BASE = "https://github.com/ysompo/MyEHR/releases"
    private val prefs = Preferences.userRoot().node("myehr")

    private fun build(v: String) = v.substringAfterLast('.').toIntOrNull() ?: 0

    /** Newer version string, or null. Checks at most daily unless [force]. Never throws. */
    suspend fun newerVersion(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (DESKTOP_VERSION == "dev") return@withContext null
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("update_check", 0) < 24 * 3600_000L) return@withContext null
        prefs.putLong("update_check", now)
        runCatching {
            val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build()
            val body = client.send(HttpRequest.newBuilder(URI("$BASE/download/desktop-latest/desktop-version.json")).build(), HttpResponse.BodyHandlers.ofString()).body()
            val latest = Json.parseToJsonElement(body).jsonObject["version"]!!.jsonPrimitive.content
            latest.takeIf { build(it) > build(DESKTOP_VERSION) }
        }.getOrNull()
    }

    fun openDownload() { runCatching { Desktop.getDesktop().browse(URI("$BASE/tag/desktop-latest")) } }
}
