package com.myehr.app.sync

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Shares cookies with the login WebView, so the session the user opened there is used here. */
private object WebViewCookies : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return raw.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = CookieManager.getInstance()
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
        cm.flush()
    }
}

/**
 * Calls the site directly (no page), with the same cookies and headers the site's own pages send.
 * Runs fine with the screen off, inside [SyncService].
 */
class NativeCaller(private val userAgent: String) : Caller {
    private val client = OkHttpClient.Builder()
        .cookieJar(WebViewCookies)
        .followRedirects(true).followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun call(method: String, path: String, body: String?): Resp = withContext(Dispatchers.IO) {
        val sep = if ('?' in path) '&' else '?'
        val url = "https://online.meuhedet.co.il$path${sep}uniq_param=${System.currentTimeMillis()}"
        val reqBody = when {
            method == "GET" -> null
            body == null -> FormBody.Builder().build()
            else -> body.toRequestBody("application/json".toMediaType())
        }
        val req = Request.Builder().url(url).method(method, reqBody)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("locale", "he-IL")
            .header("x-platform", "1")
            .header("User-Agent", userAgent)
            .header("Referer", "https://online.meuhedet.co.il/")
            .build()
        runCatching {
            client.newCall(req).execute().use { r ->
                Resp(r.code, r.header("content-type").orEmpty(), r.body?.string().orEmpty(), r.request.url.toString())
            }
        }.getOrElse { Resp(0, "", "", "") }
    }

    override suspend fun file(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://online.meuhedet.co.il$path").get()
            .header("Accept", "application/pdf, application/json, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("locale", "he-IL")
            .header("x-platform", "1")
            .header("User-Agent", userAgent)
            .header("Referer", "https://online.meuhedet.co.il/")
            .build()
        runCatching {
            client.newCall(req).execute().use { r ->
                if (r.code != 200 || r.header("content-type").orEmpty().contains("html")) null else r.body?.bytes()
            }
        }.getOrNull()
    }
}
