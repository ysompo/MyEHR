package com.myehr.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myehr.app.data.Examination
import com.myehr.app.data.MedDocument
import com.myehr.app.data.Medication
import com.myehr.app.data.Prescription
import com.myehr.app.data.Purchase
import com.myehr.app.data.Vaccine
import com.myehr.app.data.fmtDate

private fun LazyListScope.header(text: String) = item {
    Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
}

private fun LazyListScope.row(title: String, subtitle: String) = item {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
}

@Composable
fun MedsScreen(active: List<Medication>, prescriptions: List<Prescription>, purchases: List<Purchase>) {
    if (active.isEmpty() && prescriptions.isEmpty() && purchases.isEmpty()) return Empty()
    LazyColumn {
        if (active.isNotEmpty()) header("תרופות פעילות")
        active.forEach { m ->
            row(m.name, listOfNotNull(if (m.permanent) "קבועה" else null, m.doctor, m.end?.let { "מרשם עד ${fmtDate(it)}" }).joinToString(" · "))
        }
        if (prescriptions.isNotEmpty()) header("מרשמים")
        prescriptions.forEach { p ->
            row(p.medicines.joinToString(", ").ifEmpty { "מרשם ${p.number ?: ""}" },
                listOfNotNull(fmtDate(p.createdAt), p.doctor, p.specialty).joinToString(" · "))
        }
        if (purchases.isNotEmpty()) header("רכישות")
        purchases.forEach { p -> row(p.name, listOfNotNull(fmtDate(p.date), p.pharmacy, p.doctor).joinToString(" · ")) }
    }
}

@Composable
fun DocsScreen(docs: List<MedDocument>, vaccines: List<Vaccine>, exams: List<Examination>) {
    if (docs.isEmpty() && vaccines.isEmpty() && exams.isEmpty()) return Empty()
    LazyColumn {
        if (docs.isNotEmpty()) header("מסמכים והפניות")
        docs.forEach { d -> row(d.title, listOfNotNull(fmtDate(d.date), d.doctor, d.specialty).joinToString(" · ")) }
        if (exams.isNotEmpty()) header("א.ק.ג ומכונים")
        exams.forEach { e -> row(e.description, listOfNotNull(fmtDate(e.date), e.institute).joinToString(" · ")) }
        if (vaccines.isNotEmpty()) header("חיסונים")
        vaccines.forEach { v -> row(v.name, fmtDate(v.date)) }
        item { Text("פתיחת קובצי PDF תתווסף בגרסה הבאה.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
    }
}
