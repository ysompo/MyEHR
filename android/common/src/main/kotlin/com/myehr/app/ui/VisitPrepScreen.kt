package com.myehr.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myehr.app.data.Condition
import com.myehr.app.data.VisitPrep
import com.myehr.app.data.fmtDate
import com.myehr.app.data.range

private fun LazyListScope.block(title: String, empty: String?, rows: List<Pair<String, String?>>, status: (Int) -> Status = { Status.NONE }) {
    if (rows.isEmpty() && empty == null) return
    item { SectionTitle(title) }
    item {
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            if (rows.isEmpty()) Text(empty!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.l))
            rows.forEachIndexed { i, (t, sub) ->
                val st = status(i)
                CardRow(t, sub, divider = i < rows.lastIndex, trailing = if (st == Status.HIGH || st == Status.LOW) ({ StatusPill(st) }) else null)
            }
        }
    }
}

/** One page to look at before (or show at) an appointment: what changed since the last visit. */
@Composable
fun VisitPrepScreen(p: VisitPrep?) {
    if (p == null) return Empty()
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        item {
            Text(listOfNotNull(p.visit.specialty, p.visit.doctor).joinToString(" · ").ifEmpty { "ביקור" },
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l))
            Text("${fmtDate(p.visit.start)} · שינויים מאז ${fmtDate(p.since)}" +
                when { p.sameSpecialty -> " (הביקור הקודם באותו תחום)"; p.sinceVisit != null -> " (הביקור הקודם)"; else -> " (שנה אחורה)" },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.xl))
        }
        val nr = p.newResults
        block("תוצאות חדשות מאז", "אין תוצאות חדשות.", nr.map { (s, pt) ->
            "${s.name}  ${valueOf(pt.resultText, pt.unit)}" to listOfNotNull(fmtDate(pt.date),
                if (pt.normLow != null || pt.normHigh != null) "${pt.rangeLabel} ${range(pt.normLow, pt.normHigh)}" else null, pt.source).joinToString(" · ")
        }) { i -> nr[i].second.status() }
        block("עדיין מחוץ לטווח (מבדיקה קודמת)", null, p.stillOut.map { s ->
            "${s.name}  ${valueOf(s.latest?.resultText, s.unit)}" to fmtDate(s.latest?.date)
        }) { i -> p.stillOut[i].latest?.status() ?: Status.NONE }
        block("תרופות שהתחילו מאז", "לא התחילו תרופות חדשות.", p.medsStarted.map { it.name to fmtDate(it.firstPurchase) })
        block("תרופות פעילות", null, p.activeMeds.map { it.name to it.firstPurchase?.let { d -> "מאז ${fmtDate(d)}" } })
        block("אבחנות ורגישויות", null, p.conditions.filter { it.type != Condition.NOTE }.map { it.text to Condition.labels[it.type] })
        block("ההערות שלי", null, p.conditions.filter { it.type == Condition.NOTE }.map { it.text to it.date?.let(::fmtDate) })
        block("מסמכים שהעליתם מאז", null, p.newUploads.map { listOfNotNull(it.title, it.source).joinToString(" · ").ifEmpty { "מסמך" } to fmtDate(it.date) })
        item {
            Text("תקציר מתוך הנתונים באפליקציה בלבד. מה שלא מופיע כאן לא בהכרח לא קרה.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.xl))
        }
    }
}

private fun valueOf(v: String?, unit: String?) = com.myehr.app.data.valueUnit(v, unit)
