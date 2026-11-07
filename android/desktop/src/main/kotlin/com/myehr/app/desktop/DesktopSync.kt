package com.myehr.app.desktop

import com.myehr.app.data.Parsers
import com.myehr.app.sync.Api
import com.myehr.app.sync.Caller
import com.myehr.app.sync.MemberSwitcher
import com.myehr.app.sync.Resp
import com.myehr.app.sync.SessionExpired
import com.myehr.app.sync.SyncEngine
import com.myehr.app.sync.SyncState
import javafx.application.Platform as Fx
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.web.WebView
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val ORIGIN = "https://online.meuhedet.co.il"

/** One cookie jar for the login window (JavaFX WebView) and the downloader, in memory only. */
object Session {
    val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL).also { CookieHandler.setDefault(it) }
    var userAgent: String = "Mozilla/5.0"

    fun clear() = cookies.cookieStore.removeAll()

    fun selectMember(guid: String?) {
        val uri = URI(ORIGIN)
        cookies.cookieStore.cookies.filter { it.name == Api.MEMBER_COOKIE }.forEach { cookies.cookieStore.remove(null, it) }
        if (guid != null) cookies.cookieStore.add(uri, HttpCookie(Api.MEMBER_COOKIE, guid).apply { domain = ".meuhedet.co.il"; path = "/"; version = 0 })
    }
}

/** Calls the site directly with the login window's cookies and the site's own headers. */
class DesktopCaller : Caller {
    private val client = HttpClient.newBuilder()
        .cookieHandler(Session.cookies)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    override suspend fun call(method: String, path: String, body: String?): Resp = withContext(Dispatchers.IO) {
        val sep = if ('?' in path) '&' else '?'
        val b = HttpRequest.newBuilder(URI("$ORIGIN$path${sep}uniq_param=${System.currentTimeMillis()}"))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("locale", "he-IL")
            .header("x-platform", "1")
            .header("User-Agent", Session.userAgent)
            .header("Referer", "$ORIGIN/")
        when {
            method == "GET" -> b.GET()
            body == null -> b.header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.noBody())
            else -> b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        }
        runCatching {
            val r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            Resp(r.statusCode(), r.headers().firstValue("content-type").orElse(""), r.body(), r.uri().toString())
        }.getOrElse { Resp(0, "", "", "") }
    }

    override suspend fun file(path: String): ByteArray? = withContext(Dispatchers.IO) {
        val req = HttpRequest.newBuilder(URI("$ORIGIN$path")).timeout(Duration.ofSeconds(90)).GET()
            .header("Accept", "application/pdf, application/json, */*")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("locale", "he-IL")
            .header("x-platform", "1")
            .header("User-Agent", Session.userAgent)
            .header("Referer", "$ORIGIN/")
            .build()
        runCatching {
            val r = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (r.statusCode() != 200 || r.headers().firstValue("content-type").orElse("").contains("html")) null else r.body()
        }.getOrNull()
    }
}

/** Login window + background download. Runs while the app is open; the computer may lock meanwhile. */
object DesktopSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var fxStarted = false
    private var stage: Stage? = null

    private fun fx(block: () -> Unit) {
        if (!fxStarted) { Fx.setImplicitExit(false); runCatching { Fx.startup(block) }.onFailure { Fx.runLater(block) }; fxStarted = true }
        else Fx.runLater(block)
    }

    /** Opens the real Meuhedet login page. When the session works, closes it and syncs. */
    fun start(storeProvider: () -> DesktopStore) {
        if (SyncState.status.value.running) return
        Session.clear()
        SyncState.set(SyncState.Status(text = "התחברו לאתר מאוחדת בחלון שנפתח (ת\"ז, טלפון וקוד SMS)."))
        fx {
            val web = WebView()
            Session.userAgent = web.engine.userAgent
            val st = Stage().apply { title = "התחברות למאוחדת"; scene = Scene(web, 900.0, 760.0) }
            stage = st
            var handedOff = false
            // Stay on Meuhedet: any other site (a link, an ad) is sent back to the personal area.
            web.engine.locationProperty().addListener { _, _, loc ->
                val h = runCatching { URI(loc).host }.getOrNull() ?: return@addListener
                if (h != "meuhedet.co.il" && !h.endsWith(".meuhedet.co.il")) Fx.runLater { web.engine.load("$ORIGIN/") }
            }
            web.engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (state != Worker.State.SUCCEEDED || handedOff) return@addListener
                val host = runCatching { URI(web.engine.location).host }.getOrNull()
                if (host == "online.meuhedet.co.il") scope.launch {
                    val r = DesktopCaller().call("GET", Api.MEMBER, null)
                    if (r.status == 200 && !r.contentType.contains("html") && Parsers.person(r.text) != null && !handedOff) {
                        handedOff = true
                        fx { web.engine.load("about:blank"); st.close() }
                        runSync(storeProvider())
                    }
                }
            }
            web.engine.load("$ORIGIN/")
            st.show(); st.toFront()
        }
    }

    private suspend fun runSync(store: DesktopStore) {
        SyncState.set(SyncState.Status(running = true, text = "מתחיל סנכרון…"))
        try {
            val engine = SyncEngine(DesktopCaller(), store, onProgress = { SyncState.set(SyncState.Status(running = true, text = it)) }, files = DesktopVault())
            val people = engine.syncFamily(MemberSwitcher { Session.selectMember(it) })
            val news = SyncState.newResultsText(engine.newResults)
            SyncState.set(SyncState.Status(text = "הסנכרון הסתיים: ${people.joinToString(", ") { it.name }}" + (if (news.isNotEmpty()) "\n$news" else "") + SyncState.missing(engine.failures), finishedAt = System.currentTimeMillis()))
        } catch (e: SessionExpired) {
            SyncState.set(SyncState.Status(text = "החיבור למאוחדת נותק. התחברו שוב.", needsLogin = true))
        } catch (e: Exception) {
            SyncState.set(SyncState.Status(text = "הסנכרון נכשל (${e.javaClass.simpleName}). נסו שוב."))
        } finally {
            Session.clear() // sign out: nothing stays logged in on the computer
        }
    }
}
