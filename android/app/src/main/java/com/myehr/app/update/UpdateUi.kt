package com.myehr.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.myehr.app.ui.CardRow
import com.myehr.app.ui.SoftCard
import com.myehr.app.ui.Space
import kotlinx.coroutines.launch

/** Download + install button with progress and errors. */
@Composable
private fun InstallButton(info: UpdateInfo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        if (progress != null) LinearProgressIndicator(progress = { (progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = Space.s))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(enabled = progress == null, onClick = {
            error = null; progress = 0
            scope.launch {
                runCatching { Updater.install(context, info) { progress = it } }
                    .onFailure { error = "ההורדה נכשלה. נסו שוב." }
                progress = null
            }
        }) { Text("התקנת גרסה ${info.versionName}") }
    }
}

/** Shown at the top of the home screen when the daily check finds a newer version. */
@Composable
fun UpdateBanner(info: UpdateInfo, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Column(Modifier.padding(Space.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("עדכון זמין", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("אחר כך") }
            }
            InstallButton(info)
        }
    }
}

/** Settings section: current version + manual check. */
@Composable
fun UpdateSection() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(false) }
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        CardRow("גרסה ${Updater.currentName}", status, divider = false, trailing = {
            TextButton(enabled = !checking, onClick = {
                checking = true; status = "בודק…"
                scope.launch {
                    runCatching { Updater.check() }
                        .onSuccess { found = it; status = if (it == null) "זו הגרסה העדכנית" else "גרסה ${it.versionName} זמינה" }
                        .onFailure { status = "אין חיבור. נסו שוב." }
                    checking = false
                }
            }) { Text("בדיקת עדכונים") }
        })
        found?.let { Column(Modifier.padding(start = Space.l, end = Space.l, bottom = Space.l)) { InstallButton(it) } }
    }
}
