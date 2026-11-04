package com.myehr.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.myehr.app.data.Repo
import com.myehr.app.data.Secrets
import com.myehr.app.data.Store
import com.myehr.app.data.Visit
import com.myehr.app.sync.SyncScreen
import com.myehr.app.ui.Ic
import com.myehr.app.ui.Look
import com.myehr.app.ui.Looks
import com.myehr.app.ui.PineLook
import com.myehr.app.ui.Platform
import com.myehr.app.ui.SecretStore
import com.myehr.app.ui.TextSize
import com.myehr.app.ui.epochMs
import com.myehr.app.update.UpdateInfo
import com.myehr.app.update.Updater

/** Android implementation of the shared screens' needs. Storage opens lazily (unlock first). */
class AndroidPlatform(private val context: Context) : Platform {
    override val deviceWord = "בטלפון"
    private val store by lazy { Store.get(context) }
    override val repo by lazy { Repo(store) }
    override val secrets: SecretStore by lazy { Secrets(context) }
    override fun logAi(person: String, question: String, sent: String, answer: String) = store.logAi(person, question, sent, answer)

    /** Opens the phone's calendar app (e.g. Google Calendar) with the appointment filled in; the user taps Save. */
    override fun addToCalendar(v: Visit) {
        val begin = epochMs(v.start) ?: return
        val end = epochMs(v.end)?.takeIf { it > begin } ?: (begin + 30 * 60_000)
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.Events.TITLE, listOfNotNull("תור", v.specialty, v.doctor).joinToString(" · "))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, v.place ?: "")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) }
        catch (e: ActivityNotFoundException) { Toast.makeText(context, "לא נמצאה אפליקציית יומן", Toast.LENGTH_LONG).show() }
    }

    @Composable override fun icon(ic: Ic): Painter {
        val ctx = LocalContext.current
        val id = remember(ic) { ctx.resources.getIdentifier(ic.file, "drawable", ctx.packageName) }
        return painterResource(id)
    }

    @Composable override fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
        androidx.activity.compose.BackHandler(enabled, onBack)

    @Composable override fun SyncContent(onDone: () -> Unit) = SyncScreen(onDone)

    @Composable override fun UpdateBanner() {
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        LaunchedEffect(Unit) { update = Updater.dailyCheck(context) }
        update?.let { u -> com.myehr.app.update.UpdateBanner(u) { update = null } }
    }

    @Composable override fun UpdateSection() = com.myehr.app.update.UpdateSection()

    companion object {
        @OptIn(ExperimentalTextApi::class)
        private fun rubik(w: Int) = Font(R.font.rubik_var, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
        val rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700))
    }
}

/** Text size and colors, remembered on this phone. */
object UiPrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("myehr_ui", Context.MODE_PRIVATE)
    fun textSize(c: Context) = runCatching { TextSize.valueOf(prefs(c).getString("text_size", null)!!) }.getOrDefault(TextSize.NORMAL)
    fun setTextSize(c: Context, t: TextSize) = prefs(c).edit().putString("text_size", t.name).apply()
    fun look(c: Context): Look = Looks.firstOrNull { it.name == prefs(c).getString("look", null) } ?: PineLook
    fun setLook(c: Context, l: Look) = prefs(c).edit().putString("look", l.name).apply()
}
