package com.myehr.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.myehr.app.data.Hit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Search across everything stored for one person. Runs on the device only; nothing is sent. */
@Composable
fun SearchScreen(person: String, onOpen: (Hit) -> Unit) {
    val repo = LocalPlatform.current.repo
    var q by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q, person) {
        delay(200)
        hits = withContext(Dispatchers.IO) { repo.search(person, q) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
        item {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("בדיקה, תרופה, מסמך, רופא…") },
                leadingIcon = { AppIcon(Ic.SEARCH, null) },
                modifier = Modifier.fillMaxWidth().padding(Space.l).focusRequester(focus))
            Text("החיפוש נעשה במכשיר בלבד. שום דבר לא נשלח.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.xl))
            if (q.isNotBlank() && hits.isEmpty()) Text("לא נמצא.", modifier = Modifier.padding(Space.xl))
        }
        hits.groupBy { it.group }.forEach { (group, list) ->
            item { SectionTitle("$group (${list.size})") }
            item {
                SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
                    list.take(50).forEachIndexed { i, h ->
                        CardRow(h.title, h.subtitle.ifBlank { null }, onClick = if (h.target == Hit.Target.None) null else ({ onOpen(h) }),
                            divider = i < minOf(list.size, 50) - 1)
                    }
                }
            }
        }
    }
}
