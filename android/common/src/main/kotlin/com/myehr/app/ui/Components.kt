package com.myehr.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myehr.app.data.LabPoint
import com.myehr.app.data.LabSeries

// ---------- status ----------
enum class Status { HIGH, LOW, OK, NONE }

fun LabPoint.status(): Status = when {
    value == null || (normLow == null && normHigh == null) -> Status.NONE
    normHigh != null && value > normHigh -> Status.HIGH
    normLow != null && value < normLow -> Status.LOW
    else -> Status.OK
}

/** Arrow + word, so status never depends on color alone. */
@Composable
fun StatusPill(status: Status, modifier: Modifier = Modifier) {
    if (status == Status.NONE) return
    val s = LocalStatus.current
    val (fg, bg, word, icon) = when (status) {
        Status.HIGH -> Quad(s.high, s.highBg, "גבוה", Ic.ARROW_UP)
        Status.LOW -> Quad(s.high, s.highBg, "נמוך", Ic.ARROW_DOWN)
        else -> Quad(s.ok, s.okBg, "תקין", Ic.CHECK)
    }
    Row(
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(LocalPlatform.current.icon(icon), null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(word, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

private data class Quad(val fg: Color, val bg: Color, val word: String, val icon: Ic)

// ---------- sparkline ----------
/** Tiny trend line. Time runs left→right; the last point is emphasized. */
@Composable
fun Sparkline(series: LabSeries, modifier: Modifier = Modifier, width: Dp = 72.dp, height: Dp = 28.dp) {
    val pts = series.points.mapNotNull { it.value?.let { v -> v to it } }
    if (pts.size < 2) { Spacer(modifier.size(width, height)); return }
    val line = MaterialTheme.colorScheme.primary
    val alert = LocalStatus.current.high
    val band = MaterialTheme.colorScheme.primaryContainer
    val norm = series.points.lastOrNull { it.normLow != null || it.normHigh != null }
    Canvas(modifier.size(width, height)) {
        val vals = pts.map { it.first } + listOfNotNull(norm?.normLow, norm?.normHigh)
        val lo = vals.min(); val hi = vals.max().let { if (it == lo) lo + 1 else it }
        fun x(i: Int) = i * size.width / (pts.size - 1)
        fun y(v: Double) = (size.height - 3f) - ((v - lo) / (hi - lo)).toFloat() * (size.height - 6f)
        if (norm != null) {
            val top = y(norm.normHigh ?: hi); val bottom = y(norm.normLow ?: lo)
            drawRect(band.copy(alpha = 0.6f), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, bottom - top))
        }
        val path = Path()
        pts.forEachIndexed { i, (v, _) -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (lv, lp) = pts.last()
        drawCircle(if (lp.status() == Status.HIGH || lp.status() == Status.LOW) alert else line, 3.5.dp.toPx(), Offset(x(pts.size - 1), y(lv)))
    }
}

// ---------- cards & sections ----------
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Rounded card on warm paper; thin outline instead of heavy shadows. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(content = content) }
}

/** One row inside a grouped card. */
@Composable
fun CardRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(Space.m)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.let { Spacer(Modifier.width(Space.m)); it() }
    }
    if (divider) HorizontalDivider(Modifier.padding(horizontal = Space.l), color = MaterialTheme.colorScheme.outline)
}

/** Each family member gets their own color, so two names starting with the same letter still differ. */
@Composable
fun personColors(index: Int): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme; val st = LocalStatus.current
    return listOf(c.primary to c.primaryContainer, st.accent to st.accentBg, st.high to st.highBg, st.ok to st.okBg)[index % 4]
}

/** Round initial for a family member. */
@Composable
fun Avatar(name: String, selected: Boolean, index: Int = 0, size: Dp = 40.dp) {
    val (strong, soft) = personColors(index)
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).clip(CircleShape).background(if (selected) strong else soft)
            .then(if (selected) Modifier.border(3.dp, c.background, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), color = if (selected) c.background else strong, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AppIcon(icon: Ic, description: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) =
    Icon(LocalPlatform.current.icon(icon), description, modifier = modifier.size(24.dp), tint = tint)

@Composable
fun ValueText(value: String, unit: String?, status: Status = Status.NONE, big: Boolean = false) {
    val color = if (status == Status.HIGH || status == Status.LOW) LocalStatus.current.high else MaterialTheme.colorScheme.onSurface
    val main = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium
    val small = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // One left-to-right run, so "13.4 g/dL" never flips inside Hebrew layout.
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append("\u2066")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.SemiBold, fontSize = main.fontSize)); append(value); pop()
        if (unit != null) { pushStyle(androidx.compose.ui.text.SpanStyle(color = muted, fontSize = small.fontSize)); append(" $unit"); pop() }
        append("\u2069")
    }
    Text(text, style = main, modifier = Modifier.semantics { contentDescription = "$value ${unit ?: ""}" })
}

@Composable
fun Gap(h: Dp = Space.l) = Spacer(Modifier.height(h))

/** A tappable suggestion: full width, wraps nicely at any text size, 48dp+ tall. */
@Composable
fun Suggestion(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick).padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
        Text("\u2190", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
    }
}
