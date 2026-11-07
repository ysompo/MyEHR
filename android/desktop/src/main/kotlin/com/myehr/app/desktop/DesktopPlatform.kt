package com.myehr.app.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.myehr.app.data.Repo
import com.myehr.app.data.Visit
import com.myehr.app.ui.CardRow
import com.myehr.app.ui.Ic
import com.myehr.app.ui.Platform
import com.myehr.app.ui.SecretStore
import com.myehr.app.ui.SoftCard
import com.myehr.app.ui.Space
import com.myehr.app.ui.epochMs
import java.awt.Desktop
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

/** Set by the installer's launcher (jpackage); "dev" when run from Gradle. */
val DESKTOP_VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

/** API key in the OS keychain; consent flags in the user's preferences. */
object DesktopSecrets : SecretStore {
    private val prefs = Preferences.userRoot().node("myehr")
    override var apiKey: String?
        get() = Keychain.get("anthropic-api-key")
        set(v) = Keychain.set("anthropic-api-key", v?.trim()?.ifEmpty { null })
    override fun consented(kind: String) = prefs.getBoolean("consent_$kind", false)
    override fun setConsented(kind: String) = prefs.putBoolean("consent_$kind", true)
}

class DesktopPlatform(makeStore: () -> DesktopStore = { DesktopStore() }, makeVault: () -> com.myehr.app.data.FileVault = { DesktopVault() }) : Platform {
    override val deviceWord = "במחשב"
    val store by lazy(makeStore)
    override val repo by lazy { Repo(store) }
    override val secrets: SecretStore = DesktopSecrets
    override val files by lazy(makeVault)

    @Composable override fun rememberPicker(onPages: (List<com.myehr.app.data.Page>) -> Unit) = rememberDesktopPicker(onPages)

    @Composable override fun PageView(page: com.myehr.app.data.Page, modifier: Modifier) = DesktopPageView(page, modifier)
    override fun logAi(person: String, question: String, sent: String, answer: String) = store.logAi(person, question, sent, answer)

    /** Opens Google Calendar in the browser with the appointment filled in; the user clicks Save. */
    override fun addToCalendar(v: Visit) {
        val begin = epochMs(v.start) ?: return
        val end = epochMs(v.end)?.takeIf { it > begin } ?: (begin + 30 * 60_000)
        val f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
        fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        val url = "https://calendar.google.com/calendar/render?action=TEMPLATE" +
            "&text=" + enc(listOfNotNull("תור", v.specialty, v.doctor).joinToString(" · ")) +
            "&dates=" + f.format(Instant.ofEpochMilli(begin)) + "/" + f.format(Instant.ofEpochMilli(end)) +
            "&location=" + enc(v.place ?: "")
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    @Suppress("DEPRECATION")
    @Composable override fun icon(ic: Ic): Painter = painterResource("icons/${ic.file}.xml")

    @Composable override fun BackHandler(enabled: Boolean, onBack: () -> Unit) { /* back is the on-screen arrow */ }

    @Composable override fun SyncContent(onDone: () -> Unit) {
        val sync by com.myehr.app.sync.SyncState.status.collectAsState()
        androidx.compose.runtime.LaunchedEffect(Unit) { if (!sync.running) DesktopSync.start { store } }
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().padding(Space.l)) {
            Text(sync.text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            if (sync.running) {
                Text("אפשר להמשיך לעבוד במחשב בזמן הסנכרון.", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Space.s))
            }
            if (!sync.running) Button(onClick = { DesktopSync.start { store } }, modifier = Modifier.padding(top = Space.s)) { Text("פתיחת חלון ההתחברות") }
            Button(onClick = onDone, modifier = Modifier.padding(top = Space.s)) { Text("חזרה למסך הבית") }
        }
    }

    @Composable override fun UpdateBanner() {
        var newer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        androidx.compose.runtime.LaunchedEffect(Unit) { newer = DesktopUpdates.newerVersion() }
        newer?.let { v ->
            androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                shape = androidx.compose.material3.MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                androidx.compose.foundation.layout.Column(Modifier.padding(Space.l)) {
                    Text("גרסה חדשה למחשב: $v", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Button(onClick = { DesktopUpdates.openDownload() }, modifier = Modifier.padding(top = Space.s)) { Text("הורדה והתקנה") }
                }
            }
        }
    }

    @Composable override fun UpdateSection() {
        var status by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            CardRow("גרסה $DESKTOP_VERSION", status, divider = false, trailing = {
                androidx.compose.material3.TextButton(onClick = {
                    status = "בודק…"
                    scope.launch { status = DesktopUpdates.newerVersion(force = true)?.let { "גרסה $it זמינה" } ?: "זו הגרסה העדכנית" }
                }) { Text("בדיקת עדכונים") }
            })
            if (status?.contains("זמינה") == true) Button(onClick = { DesktopUpdates.openDownload() }, modifier = Modifier.padding(start = Space.l, bottom = Space.l)) { Text("הורדה והתקנה") }
        }
    }

    companion object {
        @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
        private fun rubik(w: Int) = Font("font/rubik_var.ttf", FontWeight(w), androidx.compose.ui.text.font.FontStyle.Normal,
            androidx.compose.ui.text.font.FontVariation.Settings(androidx.compose.ui.text.font.FontVariation.weight(w)))
        val rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700))
    }
}

