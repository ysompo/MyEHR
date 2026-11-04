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
import com.myehr.app.data.Examination
import com.myehr.app.data.MedDocument
import com.myehr.app.data.Medication
import com.myehr.app.data.Prescription
import com.myehr.app.data.Purchase
import com.myehr.app.data.Vaccine
import com.myehr.app.data.fmtDate

private class Line(val title: String, val subtitle: String)

private fun LazyListScope.group(title: String, icon: Ic, lines: List<Line>) {
    if (lines.isEmpty()) return
    item { SectionTitle(title) }
    item {
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            lines.forEachIndexed { i, l ->
                CardRow(l.title, l.subtitle.ifBlank { null }, divider = i < lines.lastIndex,
                    leading = { AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary) })
            }
        }
    }
}

@Composable
fun MedsScreen(active: List<Medication>, prescriptions: List<Prescription>, purchases: List<Purchase>) {
    if (active.isEmpty() && prescriptions.isEmpty() && purchases.isEmpty()) return Empty()
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        group("תרופות פעילות", Ic.PILL, active.map { m ->
            Line(m.name, listOfNotNull(if (m.permanent) "קבועה" else null, m.end?.let { "מרשם עד ${fmtDate(it)}" }, m.doctor).joinToString(" · "))
        })
        group("מרשמים", Ic.DOC, prescriptions.map { p ->
            Line(p.medicines.joinToString(", ").ifEmpty { "מרשם ${p.number ?: ""}" }, listOfNotNull(fmtDate(p.createdAt), p.doctor, p.specialty).joinToString(" · "))
        })
        group("רכישות", Ic.PILL, purchases.map { p -> Line(p.name, listOfNotNull(fmtDate(p.date), p.pharmacy).joinToString(" · ")) })
    }
}

@Composable
fun DocsScreen(docs: List<MedDocument>, vaccines: List<Vaccine>, exams: List<Examination>) {
    if (docs.isEmpty() && vaccines.isEmpty() && exams.isEmpty()) return Empty()
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        group("מסמכים והפניות", Ic.DOC, docs.map { d -> Line(d.title, listOfNotNull(fmtDate(d.date), d.doctor, d.specialty).joinToString(" · ")) })
        group("א.ק.ג ומכונים", Ic.LAB, exams.map { e -> Line(e.description, listOfNotNull(fmtDate(e.date), e.institute).joinToString(" · ")) })
        group("חיסונים", Ic.CHECK, vaccines.map { v -> Line(v.name, fmtDate(v.date)) })
        item { Text("פתיחת קובצי PDF תתווסף בגרסה הבאה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.xl)) }
    }
}
