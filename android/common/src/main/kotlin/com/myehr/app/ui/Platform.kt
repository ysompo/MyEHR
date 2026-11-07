package com.myehr.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import com.myehr.app.data.Repo
import com.myehr.app.data.Visit

/** Line icons drawn for MyEHR (same files on Android and desktop). */
enum class Ic(val file: String) {
    HOME("ic_home"), LAB("ic_lab"), ASK("ic_ask"), PILL("ic_pill"), DOC("ic_doc"), SYNC("ic_sync"),
    SETTINGS("ic_settings"), BACK("ic_back"), ARROW_UP("ic_arrow_up"), ARROW_DOWN("ic_arrow_down"),
    CHECK("ic_check"), CALENDAR("ic_calendar"),
}

/** Secrets kept by the platform (Android Keystore / OS keychain). */
interface SecretStore {
    var apiKey: String?
    fun consented(kind: String): Boolean
    fun setConsented(kind: String)
}

/** Everything the shared screens need from the phone or the computer. */
interface Platform {
    /** "בטלפון" / "במחשב", for texts like "saved encrypted on the phone". */
    val deviceWord: String
    val repo: Repo
    val secrets: SecretStore
    /** Encrypted storage for uploaded documents and saved PDFs. */
    val files: com.myehr.app.data.FileVault
    fun logAi(person: String, question: String, sent: String, answer: String)
    fun addToCalendar(v: Visit)

    @Composable fun icon(ic: Ic): Painter
    @Composable fun BackHandler(enabled: Boolean, onBack: () -> Unit)
    /** Login + sync screen. */
    @Composable fun SyncContent(onDone: () -> Unit)
    /** Home-screen banner when an update is available (or nothing). */
    @Composable fun UpdateBanner()
    /** Settings section: version + check for updates. */
    @Composable fun UpdateSection()
    /** Camera (phone) and file picker; pages arrive already shrunk for sending. */
    @Composable fun rememberPicker(onPages: (List<com.myehr.app.data.Page>) -> Unit): DocPicker
    /** Settings row for platform-only privacy options (phone: allow screenshots). */
    @Composable fun PrivacySettings() {}
    /** Lets the user choose where to save a file; returns the function that starts it. */
    @Composable fun rememberSaver(onResult: (Boolean) -> Unit): (name: String, bytes: ByteArray) -> Unit
    /** Shows one image, or every page of a PDF, inside the app. */
    @Composable fun PageView(page: com.myehr.app.data.Page, modifier: androidx.compose.ui.Modifier)
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("No Platform provided") }

/** Set once by the platform at startup (Rubik on both). */
object PlatformFonts { var family: FontFamily = FontFamily.Default }
