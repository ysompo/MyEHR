package com.myehr.app.sync

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.myehr.app.data.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val START_URL = "https://online.meuhedet.co.il/"
private const val ONLINE_ORIGIN = "https://online.meuhedet.co.il"
private const val BRIDGE = "myehrBridge"

private fun isMeuhedet(host: String?) = host != null && (host == "meuhedet.co.il" || host.endsWith(".meuhedet.co.il"))

/** Trackers loaded by the site. Blocked inside the app; not needed for login or data. */
private val BLOCKED = Regex("google-analytics|googletagmanager|doubleclick|facebook|taboola|outbrain|tiktok|clarity\\.ms|hotjar|gbt\\.meuhedet|algolia")

private const val HELPER = """
if (!window.__myehrCall) {
  window.__myehrCall = async function (id, method, path, body) {
    try {
      const sep = path.indexOf('?') >= 0 ? '&' : '?';
      const headers = { 'Accept': 'application/json, text/plain, */*', 'X-Requested-With': 'XMLHttpRequest', 'locale': 'he-IL', 'x-platform': '1' };
      if (body) headers['Content-Type'] = 'application/json';
      const r = await fetch(path + sep + 'uniq_param=' + Date.now(), { method, credentials: 'include', headers, body: body || undefined });
      const text = await r.text();
      $BRIDGE.postMessage(JSON.stringify({ id, status: r.status, ct: r.headers.get('content-type') || '', url: r.url, text }));
    } catch (e) {
      $BRIDGE.postMessage(JSON.stringify({ id, status: 0, ct: '', url: '', text: String(e) }));
    }
  };
}
"""

/** Runs fetch() inside the logged-in page, so the site's own cookies are used. */
private class PageCaller(private val web: WebView) : Caller {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Resp>>()
    private val ids = AtomicInteger()

    fun onMessage(raw: String) {
        val o = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val id = o["id"]?.jsonPrimitive?.int ?: return
        pending.remove(id)?.complete(
            Resp(o.s("status").toIntOrNull() ?: 0, o.s("ct"), o.s("text"), o.s("url"))
        )
    }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content ?: ""

    override suspend fun call(method: String, path: String, body: String?): Resp {
        val id = ids.incrementAndGet()
        val d = CompletableDeferred<Resp>()
        pending[id] = d
        val js = HELPER + "window.__myehrCall($id, ${q(method)}, ${q(path)}, ${if (body == null) "null" else q(body)});"
        withContext(Dispatchers.Main) { web.evaluateJavascript(js, null) }
        return withTimeoutOrNull(60_000) { d.await() } ?: Resp(0, "", "", "").also { pending.remove(id) }
    }

    private fun q(s: String) = JsonPrimitive(s).toString()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SyncScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync by SyncState.status.collectAsState()
    var handedOff by remember { mutableStateOf(sync.running) }
    var checking by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("התחברו לאתר מאוחדת (ת\"ז, טלפון וקוד SMS).") }

    // Android 13+: allow the progress notification (sync works without it too).
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    DisposableEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        onDispose { }
    }

    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
        }
    }
    val caller = remember { PageCaller(web) }

    /** Logged in? Then hand the session to the background service. */
    fun handOffIfLoggedIn() {
        if (handedOff || checking) return
        checking = true
        scope.launch {
            try {
                val r = caller.call("GET", Api.MEMBER, null)
                val loggedIn = r.status == 200 && !r.contentType.contains("html") && com.myehr.app.data.Parsers.person(r.text) != null
                if (loggedIn) {
                    CookieManager.getInstance().flush()
                    SyncService.start(context)
                    handedOff = true
                }
            } finally { checking = false }
        }
    }

    DisposableEffect(Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, BRIDGE, setOf(ONLINE_ORIGIN)) { _, message, _, _, _ ->
                message.data?.let(caller::onMessage)
            }
        } else {
            hint = "גרסת WebView ישנה מדי. עדכנו את Android System WebView."
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isMeuhedet(request.url.host) // stay on Meuhedet only

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val u: Uri = request.url
                return if (BLOCKED.containsMatchIn(u.host ?: "")) WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == "online.meuhedet.co.il") handOffIfLoggedIn()
            }
        }
        if (!handedOff) web.loadUrl(START_URL)
        onDispose { web.destroy() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (!handedOff) {
                Text(hint, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(if (sync.running) "הסנכרון רץ ברקע. אפשר לנעול את הטלפון או לצאת מהאפליקציה." else sync.text,
                    style = MaterialTheme.typography.titleMedium)
                if (sync.running) {
                    Text(sync.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (sync.needsLogin) Button(onClick = { handedOff = false; web.loadUrl(START_URL) }, modifier = Modifier.padding(top = 8.dp)) { Text("התחברות מחדש") }
                Button(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) { Text("חזרה למסך הבית") }
            }
        }
        if (!handedOff) AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
    }
}
