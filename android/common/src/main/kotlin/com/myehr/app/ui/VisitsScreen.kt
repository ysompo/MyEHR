package com.myehr.app.ui

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
import com.myehr.app.data.Visit
import com.myehr.app.data.fmtDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val Israel = ZoneId.of("Asia/Jerusalem")

/** Site times look like "2026-11-20T09:30:00" or "…+02:00". → epoch ms. */
fun epochMs(s: String?): Long? = s?.let {
    runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(it.take(19)).atZone(Israel).toInstant().toEpochMilli() }.getOrNull()
}

/** "09:30", or "" for date-only values. */
private fun hhmm(s: String?): String = s?.takeIf { it.length >= 16 && it[10] == 'T' }?.substring(11, 16)?.takeIf { it != "00:00" } ?: ""

private fun LazyListScope.visits(title: String, list: List<Visit>, empty: String, withCalendar: Boolean, platform: Platform) {
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
                        leading = { AppIcon(Ic.CALENDAR, null, tint = MaterialTheme.colorScheme.primary) },
                        divider = !withCalendar && i < list.lastIndex,
                    )
                    if (withCalendar) {
                        Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = Space.m), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { platform.addToCalendar(v) }) { Text("הוספה ליומן") }
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
    val platform = LocalPlatform.current
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        visits("תורים קרובים", upcoming, "אין תורים קרובים.", withCalendar = true, platform = platform)
        visits("ביקורים קודמים", past, "אין ביקורים קודמים.", withCalendar = false, platform = platform)
        item {
            Text("״הוספה ליומן״ פותחת את אפליקציית היומן עם פרטי התור. האירוע נשמר רק אחרי שתלחצו שמירה.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Space.xl))
        }
    }
}

