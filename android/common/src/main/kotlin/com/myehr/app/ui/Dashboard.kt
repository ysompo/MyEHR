package com.myehr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myehr.app.ai.MedSummary
import com.myehr.app.data.LabSeries
import com.myehr.app.data.Person
import com.myehr.app.data.fmtDate

data class DashboardData(
    val people: List<Person>,
    val selected: Person,
    val flagged: List<LabSeries>,
    val latest: List<LabSeries>,
    val meds: List<MedSummary>,
    val conditions: List<com.myehr.app.data.Condition> = emptyList(),
)

val AskSuggestions = listOf("מה ההמוגלובין האחרון שלי?", "איך השתנה הכולסטרול לאורך השנים?", "מה השתנה מאז שהתחלתי תרופה?")

private fun monthYear(s: String?) = s?.takeIf { it.length >= 7 }?.let { "${it.substring(5, 7)}/${it.substring(0, 4)}" } ?: ""

@Composable
fun Dashboard(
    d: DashboardData,
    onPerson: (String) -> Unit,
    onAsk: (String?) -> Unit,
    onSeries: (String) -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onConditionsChanged: () -> Unit = {},
) {
    val bigText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        // Header: wordmark + actions
        item {
            Row(Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.s, top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("MyEHR", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = onSync) { AppIcon(Ic.SYNC, "סנכרון") }
                IconButton(onClick = onSettings) { AppIcon(Ic.SETTINGS, "הגדרות") }
            }
        }
        // Greeting
        item {
            Column(Modifier.padding(horizontal = Space.xl, vertical = Space.s)) {
                Text("שלום, ${d.selected.name}", style = MaterialTheme.typography.headlineSmall)
                d.selected.lastSync?.let {
                    Text("עודכן ממאוחדת ב-${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ROOT).format(java.util.Date(it))}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Family
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.xl), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                itemsIndexed(d.people, key = { _, p -> p.id }) { i, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onPerson(p.id) }.padding(vertical = Space.s)) {
                        Avatar(p.name, p.id == d.selected.id, index = i, size = 52.dp)
                        Text(p.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Space.xs),
                            color = if (p.id == d.selected.id) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Ask
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
            ) {
                Column(Modifier.padding(Space.l)) {
                    Text("מה תרצו לדעת?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Gap(Space.m)
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth().clickable { onAsk(null) },
                    ) {
                        Row(Modifier.padding(horizontal = Space.l, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(Ic.ASK, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(Space.m))
                            Text("שאלה על ${d.selected.name}…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Gap(Space.m)
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        AskSuggestions.forEach { Suggestion(it) { onAsk(it) } }
                    }
                }
            }
        }
        // Needs attention
        item { SectionTitle("דורש תשומת לב") }
        if (d.flagged.isEmpty()) item {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
                Row(Modifier.padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(Status.OK)
                    Spacer(Modifier.width(Space.m))
                    Text("כל התוצאות האחרונות בטווח התקין.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else item {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                items(d.flagged, key = { it.code }) { s ->
                    val last = s.latest
                    SoftCard(Modifier.fillParentMaxWidth(0.78f), onClick = { onSeries(s.code) }) {
                        Column(Modifier.padding(Space.l)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                StatusPill(last?.status() ?: Status.NONE)
                            }
                            Gap(Space.s)
                            ValueText(last?.resultText ?: "", s.unit, last?.status() ?: Status.NONE, big = true)
                            if (last?.normLow != null || last?.normHigh != null) Text("טווח תקין ${com.myehr.app.data.range(last.normLow, last.normHigh)}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(fmtDate(last?.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Gap(Space.m)
                            Sparkline(s, width = 220.dp, height = 44.dp)
                        }
                    }
                }
            }
        }
        item { ConditionsCard(d.selected.id, d.conditions, onConditionsChanged) }
        // Latest results
        item { SectionTitle("תוצאות אחרונות") }
        item {
            SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
                d.latest.forEachIndexed { i, s ->
                    val last = s.latest
                    CardRow(
                        title = s.name,
                        subtitle = listOfNotNull(fmtDate(last?.date),
                            if (last?.normLow != null || last?.normHigh != null) "טווח תקין ${com.myehr.app.data.range(last.normLow, last.normHigh)}" else null,
                        ).joinToString(" · "),
                        onClick = { onSeries(s.code) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!bigText) { Sparkline(s); Spacer(Modifier.width(Space.m)) }
                                Column(horizontalAlignment = Alignment.End) {
                                    ValueText(last?.resultText ?: "", s.unit, last?.status() ?: Status.NONE)
                                }
                            }
                        },
                        divider = i < d.latest.lastIndex,
                    )
                }
            }
        }
        // Active medications
        if (d.meds.isNotEmpty()) {
            item { SectionTitle("תרופות פעילות") }
            item {
                SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
                    d.meds.forEachIndexed { i, m ->
                        CardRow(
                            title = m.name,
                            subtitle = listOfNotNull(m.firstPurchase?.let { "מאז ${monthYear(it)}" }, if (m.permanent) "קבועה" else null).joinToString(" · "),
                            leading = { AppIcon(Ic.PILL, null, tint = MaterialTheme.colorScheme.primary) },
                            divider = i < d.meds.lastIndex,
                        )
                    }
                }
            }
        }
    }
}
