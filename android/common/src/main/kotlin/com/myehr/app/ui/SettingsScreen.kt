package com.myehr.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable

@Composable
fun SettingsScreen(textSize: TextSize, onTextSize: (TextSize) -> Unit, look: Look, onLook: (Look) -> Unit, hasApiKey: Boolean, onClearApiKey: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Space.xl)) {
        SectionTitle("גודל טקסט")
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            TextSize.entries.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = t == textSize, role = Role.RadioButton) { onTextSize(t) }
                        .padding(horizontal = Space.l, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = t == textSize, onClick = null)
                    Column(Modifier.padding(start = Space.m)) {
                        Text(t.label, style = MaterialTheme.typography.titleSmall)
                        Text("המוגלובין ${com.myehr.app.data.ltr("13.4 g/dL")}", style = MaterialTheme.typography.bodyMedium.let { it.copy(fontSize = it.fontSize * t.scale) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text("נוסף על הגדרת גודל הגופן של הטלפון.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s))

        SectionTitle("צבעים")
        // 2 per row, so names stay readable at large text sizes.
        Looks.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.s)) {
                pair.forEach { l -> LookOption(l, selected = l.name == look.name, modifier = Modifier.weight(1f)) { onLook(l) } }
            }
        }
        Text("בהיר או כהה לפי הגדרת הטלפון.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.s))

        SectionTitle("שאלות ל-Claude")
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            CardRow("מפתח API", if (hasApiKey) "שמור ומוצפן בטלפון" else "לא הוגדר", divider = false,
                trailing = { if (hasApiKey) TextButton(onClick = onClearApiKey) { Text("מחיקה") } })
        }

        SectionTitle("עדכונים")
        LocalPlatform.current.UpdateSection()

        SectionTitle("פרטיות")
        SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            CardRow("המידע נשמר רק בטלפון", "מוצפן, ללא גיבוי לענן וללא שרת", divider = false)
        }
    }
}

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) { AppIcon(Ic.BACK, "חזרה") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}


/** A small preview of a look: its background, card, main color and accent. */
@Composable
private fun LookOption(look: Look, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val c = if (dark) look.dark else look.light
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(look.cardRadius.dp)
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .background(c.background).padding(Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(shape).background(c.surfaceContainer).border(1.dp, c.outline, shape).padding(Space.s)) {
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.primary))
                Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.secondary))
            }
        }
        Text(look.name, style = MaterialTheme.typography.titleSmall, color = c.onBackground, modifier = Modifier.padding(top = Space.s))
    }
}
