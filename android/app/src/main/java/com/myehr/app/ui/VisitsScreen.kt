package com.myehr.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.myehr.app.R
import com.myehr.app.data.Visit
import com.myehr.app.data.fmtDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val Israel = ZoneId.of("Asia/Jerusalem")

/** Site times look like "2026-11-20T09:30:00" or "…+02:00". → epoch ms. */
internal fun epochMs(s: String?): Long? = s?.let {
    runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(it.take(19)).atZone(Israel).toInstant().toEpochMilli() }.getOrNull()
}

/** "09:30", or "" for date-only values. */
private fun hhmm(s: String?): String = s?.takeIf { it.length >= 16 && it[10] == 'T' }?.substring(11, 16)?.takeIf { it != "00:00" } ?: ""

/** Opens the phone's calendar app (e.g. Google Calendar) with the appointment filled in; the user taps Save. */
fun addToCalendar(context: Context, v: Visit) {
    val begin = epochMs(v.start) ?: return
    val end = epochMs(v.end)?.takeIf { it > begin } ?: (begin + 30 * 60_000)
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.Events.TITLE, listOfNotNull("תור", v.specialty, v.doctor).joinToString(" · "))
        .putExtra(CalendarContract.Events.EVENT_LOCATION, v.place ?: "")
    try { context.startActivity(intent) }
    catch (e: ActivityNotFoundException) { Toast.makeText(context, "לא נמצאה אפליקציית יומן", Toast.LENGTH_LONG).show() }
}

private fun LazyListScope.visits(title: String, list: List<Visit>, empty: String, withCalendar: Boolean, context: Context) {
    item { SectionTitle(title) }
    if (list.isEmpty()) item {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xl))
    } else item {
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            list.forEachIndexed { i, v ->
                Column {
                    CardRow(
                        title = listOfNotNull(v.specialty, v.doctor).joinToString(" · ").ifEmpty { "ביקור" },
                        subtitle = listOfNotNull(com.myehr.app.data.ltr("${fmtDate(v.start)} ${hhmm(v.start)}".trim()), v.place).joinToString("\n"),
                        leading = { AppIcon(R.drawable.ic_calendar, null, tint = MaterialTheme.colorScheme.primary) },
                        divider = !withCalendar && i < list.lastIndex,
                    )
                    if (withCalendar) {
                        Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { addToCalendar(context, v) }) { Text("הוספה ליומן") }
                        }
                        if (i < list.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = Space.l), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun VisitsScreen(upcoming: List<Visit>, past: List<Visit>) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        visits("תורים קרובים", upcoming, "אין תורים קרובים.", withCalendar = true, context = context)
        visits("ביקורים קודמים", past, "אין ביקורים קודמים.", withCalendar = false, context = context)
        item {
            Text("״הוספה ליומן״ פותחת את אפליקציית היומן עם פרטי התור. האירוע נשמר רק אחרי שתלחצו שמירה.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Space.xl))
        }
    }
}

