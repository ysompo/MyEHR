package com.myehr.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.myehr.app.data.ltr
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

val OutOfRange = Color(0xFFB8432F)

@Composable
fun LabsScreen(series: List<LabSeries>, visits: List<LabVisit>, byTest: Boolean, onMode: (Boolean) -> Unit, onSeries: (LabSeries) -> Unit, onVisit: (LabVisit) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = Space.l, vertical = Space.s)) {
            Segment("לפי בדיקה", byTest) { onMode(true) }
            Spacer(Modifier.width(Space.s))
            Segment("לפי תאריך", !byTest) { onMode(false) }
        }
        if (byTest) LabSeriesList(series, onSeries) else LabVisitList(visits, onVisit)
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) c.onPrimary else c.onSurfaceVariant,
        modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(if (selected) c.primary else c.surfaceVariant)
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = Space.l, vertical = Space.m))
}

@Composable
fun LabVisitList(visits: List<LabVisit>, onOpen: (LabVisit) -> Unit) {
    if (visits.isEmpty()) return Empty()
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        items(visits, key = { it.stickerId }) { v ->
            SoftCard(Modifier.fillMaxWidth(), onClick = { onOpen(v) }) {
                CardRow(fmtDate(v.date), listOfNotNull(v.institute, v.doctor).joinToString(" · "), divider = false,
                    leading = { AppIcon(Ic.LAB, null, tint = MaterialTheme.colorScheme.primary) })
            }
        }
    }
}

@Composable
fun LabSeriesList(series: List<LabSeries>, onOpen: (LabSeries) -> Unit) {
    if (series.isEmpty()) return Empty()
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.l)) {
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                series.forEachIndexed { i, s ->
                    val last = s.latest
                    CardRow(s.name, "${s.points.size} תוצאות · ${fmtDate(last?.date)}", onClick = { onOpen(s) }, divider = i < series.lastIndex,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Sparkline(s); Spacer(Modifier.width(Space.m))
                                ValueText(last?.resultText ?: "", s.unit, last?.status() ?: Status.NONE)
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun LabDetailScreen(detail: LabDetail?) {
    if (detail == null || detail.records.isEmpty()) return Empty("אין פירוט לבדיקה זו.")
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.l)) {
        item {
            Text(detail.labName ?: "", style = MaterialTheme.typography.headlineSmall)
            Text(fmtDate(detail.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap()
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                detail.records.forEachIndexed { i, r ->
                    val st = when { r.numeric == null || (r.min == null && r.max == null) -> Status.NONE; r.max != null && r.numeric!! > r.max -> Status.HIGH; r.min != null && r.numeric!! < r.min -> Status.LOW; else -> Status.OK }
                    CardRow(r.title, listOfNotNull(if (r.min != null || r.max != null) "טווח ${range(r.min, r.max)}" else null, r.note).joinToString(" · ").ifEmpty { null },
                        divider = i < detail.records.lastIndex,
                        trailing = {
                            Column(horizontalAlignment = Alignment.End) {
                                ValueText(r.value ?: "", r.unit, st)
                                if (st == Status.HIGH || st == Status.LOW) StatusPill(st, Modifier.padding(top = Space.xs))
                            }
                        })
                }
            }
        }
    }
}

@Composable
fun LabSeriesScreen(series: LabSeries?, meds: List<com.myehr.app.ai.MedSummary> = emptyList()) {
    if (series == null) return Empty()
    val last = series.latest
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.l)) {
        item {
            Text(series.name, style = MaterialTheme.typography.headlineSmall)
            Gap(Space.s)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueText(last?.resultText ?: "", series.unit, last?.status() ?: Status.NONE, big = true)
                Spacer(Modifier.width(Space.m))
                StatusPill(last?.status() ?: Status.NONE)
            }
            Text("אחרונה ${fmtDate(last?.date)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap()
            SoftCard(Modifier.fillMaxWidth()) {
                val starts = meds.mapNotNull { m -> m.firstPurchase?.let { it.take(10) to m.name } }
                LabChart(series, Modifier.fillMaxWidth().padding(Space.l), starts)
                starts.filter { (d, _) -> d >= (series.points.firstOrNull()?.date ?: "") }.forEach { (d, name) ->
                    Row(Modifier.padding(horizontal = Space.l, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(14.dp).height(3.dp).background(LocalStatus.current.accent))
                        Spacer(Modifier.width(Space.s))
                        Text("התחלת $name · ${fmtDate(d)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val norm = series.points.lastOrNull { it.normLow != null || it.normHigh != null }
                if (norm != null) Text("הרצועה הירוקה: טווח תקין ${valueUnit(range(norm.normLow, norm.normHigh), series.unit)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.l, end = Space.l, bottom = Space.l))
            }
            series.cautions().forEach { c ->
                Gap(Space.s)
                Text("⚠ $c", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Gap()
            SectionTitle("כל התוצאות", Modifier.padding(horizontal = 0.dp))
        }
        item {
            SoftCard(Modifier.fillMaxWidth()) {
                val pts = series.points.reversed()
                pts.forEachIndexed { i, p ->
                    CardRow(fmtDate(p.date), p.source?.let { "ממסמך: $it" }, divider = i < pts.lastIndex, trailing = { ValueText(p.resultText ?: "", p.unit, p.status()) })
                }
            }
        }
    }
}

private fun epochDay(s: String?): Long? = runCatching { LocalDate.parse(s!!.take(10)).toEpochDay() }.getOrNull()

/** Value over time. Green band = normal range, red dots = out of range. */
@Composable
fun LabChart(series: LabSeries, modifier: Modifier = Modifier, markers: List<Pair<String, String>> = emptyList()) {
    val pts = series.points.mapNotNull { p -> val x = epochDay(p.date); if (x == null || p.value == null) null else Triple(x, p.value, p) }
    if (pts.isEmpty()) return
    val band = series.points.lastOrNull { it.normLow != null || it.normHigh != null }
    val lineColor = MaterialTheme.colorScheme.primary
    val bandColor = LocalStatus.current.okBg
    val alertColor = LocalStatus.current.high
    val accent = LocalStatus.current.accent
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
        // Medication starts: dashed saffron verticals.
        markers.forEach { (d, _) ->
            val x = epochDay(d) ?: return@forEach
            if (x in minX..maxX) drawLine(accent, Offset(px(x), 0f), Offset(px(x), size.height), strokeWidth = 4f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        val path = Path()
        pts.forEachIndexed { i, (x, y, _) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
        drawPath(path, lineColor, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        pts.forEach { (x, y, p) -> drawCircle(if (p.outOfRange) alertColor else lineColor, radius = 9f, center = Offset(px(x), py(y))) }
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
fun Empty(text: String = "עדיין אין נתונים. לחצו על סמל הסנכרון במסך הבית והתחברו למאוחדת.") {
    Text(text, Modifier.padding(Space.xl), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
