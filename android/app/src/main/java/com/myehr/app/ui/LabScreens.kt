package com.myehr.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myehr.app.data.LabDetail
import com.myehr.app.data.LabSeries
import com.myehr.app.data.LabVisit
import com.myehr.app.data.fmtDate
import com.myehr.app.data.ltr
import com.myehr.app.data.range
import com.myehr.app.data.valueUnit
import java.time.LocalDate

val OutOfRange = Color(0xFFC62828)

@Composable
fun LabVisitList(visits: List<LabVisit>, onOpen: (LabVisit) -> Unit) {
    if (visits.isEmpty()) return Empty()
    LazyColumn {
        items(visits, key = { it.stickerId }) { v ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(v) }.padding(16.dp)) {
                Text(fmtDate(v.date), fontWeight = FontWeight.Bold)
                Text(listOfNotNull(v.institute, v.doctor).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun LabSeriesList(series: List<LabSeries>, onOpen: (LabSeries) -> Unit) {
    if (series.isEmpty()) return Empty()
    LazyColumn {
        items(series, key = { it.code }) { s ->
            val last = s.latest
            Row(Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Text("${s.points.size} תוצאות · אחרונה ${fmtDate(last?.date)}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    valueUnit(last?.resultText, s.unit),
                    color = if (last?.outOfRange == true) OutOfRange else Color.Unspecified,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun LabDetailScreen(detail: LabDetail?) {
    if (detail == null || detail.records.isEmpty()) return Empty("אין פירוט לבדיקה זו.")
    LazyColumn {
        item { Text("${detail.labName ?: ""} ${fmtDate(detail.date)}", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
        items(detail.records) { r ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(r.title)
                    if (r.min != null || r.max != null) Text("טווח תקין: ${range(r.min, r.max)}", style = MaterialTheme.typography.bodySmall)
                    r.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Text(valueUnit(r.value, r.unit), color = if (r.outOfRange) OutOfRange else Color.Unspecified, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun LabSeriesScreen(series: LabSeries?) {
    if (series == null) return Empty()
    LazyColumn {
        item {
            Text(series.name, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            LabChart(series, Modifier.fillMaxWidth().padding(16.dp))
            val norm = series.points.lastOrNull { it.normLow != null || it.normHigh != null }
            if (norm != null) Text("טווח תקין: ${valueUnit(range(norm.normLow, norm.normHigh), series.unit)}", Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
        }
        items(series.points.reversed()) { p ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(fmtDate(p.date), Modifier.weight(1f))
                Text(valueUnit(p.resultText, p.unit), color = if (p.outOfRange) OutOfRange else Color.Unspecified)
            }
            HorizontalDivider()
        }
    }
}

private fun epochDay(s: String?): Long? = runCatching { LocalDate.parse(s!!.take(10)).toEpochDay() }.getOrNull()

/** Value over time. Green band = normal range, red dots = out of range. */
@Composable
fun LabChart(series: LabSeries, modifier: Modifier = Modifier) {
    val pts = series.points.mapNotNull { p -> val x = epochDay(p.date); if (x == null || p.value == null) null else Triple(x, p.value, p) }
    if (pts.isEmpty()) return
    val band = series.points.lastOrNull { it.normLow != null || it.normHigh != null }
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = Color(0x332E7D32)
    val minV = pts.minOf { it.second }; val maxV = pts.maxOf { it.second }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(modifier) {
    Text("${trim(maxV)} ${series.unit ?: ""}", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val xs = pts.map { it.first }
        val ys = pts.map { it.second } + listOfNotNull(band?.normLow, band?.normHigh)
        val minX = xs.min(); val maxX = maxOf(xs.max(), minX + 1)
        var minY = ys.min(); var maxY = ys.max()
        val pad = maxOf((maxY - minY) * 0.1, 0.5); minY -= pad; maxY += pad
        // Graph reads left→right in time even in RTL.
        fun px(x: Long) = ((x - minX).toFloat() / (maxX - minX)) * size.width
        fun py(y: Double) = size.height - ((y - minY) / (maxY - minY)).toFloat() * size.height
        if (band != null) {
            val top = py(band.normHigh ?: maxY); val bottom = py(band.normLow ?: minY)
            drawRect(bandColor, Offset(0f, top), Size(size.width, bottom - top))
        }
        val path = Path()
        pts.forEachIndexed { i, (x, y, _) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
        drawPath(path, lineColor, style = Stroke(width = 4f))
        pts.forEach { (x, y, p) -> drawCircle(if (p.outOfRange) OutOfRange else lineColor, radius = 8f, center = Offset(px(x), py(y))) }
    }
    Text("${trim(minV)} ${series.unit ?: ""}", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(fmtDate(pts.first().third.date), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(fmtDate(pts.last().third.date), style = MaterialTheme.typography.bodySmall)
    }
    }
    }
}

private fun trim(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

@Composable
fun Empty(text: String = "אין נתונים. לחצו על \"סנכרון\" והתחברו למאוחדת.") {
    Text(text, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
}
