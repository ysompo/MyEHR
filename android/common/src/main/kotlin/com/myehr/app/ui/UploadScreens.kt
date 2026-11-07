package com.myehr.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.myehr.app.ai.DocReader
import com.myehr.app.ai.Findings
import com.myehr.app.data.Condition
import com.myehr.app.data.Page
import com.myehr.app.data.Person
import com.myehr.app.data.Upload
import com.myehr.app.data.fmtDate
import com.myehr.app.data.newId
import com.myehr.app.data.valueUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Camera / file picker provided by the platform. */
interface DocPicker {
    val hasCamera: Boolean
    fun camera()
    fun file()
}

private enum class Phase { PICK, READING, REVIEW }

/** Add a document: pick pages → OK to send → Claude reads it → tick what to keep → saved encrypted. */
@Composable
fun AddDocScreen(people: List<Person>, initial: Person, onDone: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<Page>() }
    var target by remember { mutableStateOf(initial) }
    var phase by remember { mutableStateOf(Phase.PICK) }
    var error by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Findings?>(null) }
    val keep = remember { mutableStateListOf<Boolean>() }
    val picker = platform.rememberPicker { pages += it }
    val apiKey = platform.secrets.apiKey

    when (phase) {
        Phase.PICK -> LazyColumn(contentPadding = PaddingValues(Space.l)) {
            item {
                Text("צלמו את המסמך או בחרו קובץ (PDF או תמונה). אפשר כמה עמודים.", style = MaterialTheme.typography.bodyLarge)
                Gap(Space.m)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (picker.hasCamera) Button(onClick = picker::camera) { Text("צילום עמוד") }
                    OutlinedButton(onClick = picker::file) { Text("בחירת קובץ") }
                }
                Gap(Space.m)
                if (pages.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${pages.size} ${if (pages.size == 1) "עמוד / קובץ" else "עמודים / קבצים"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { pages.removeAt(pages.lastIndex) }) { Text("הסרת האחרון") }
                }
                if (people.size > 1) {
                    Gap(Space.m)
                    Text("של מי המסמך?", style = MaterialTheme.typography.titleSmall)
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        people.forEach { p -> FilterChip(selected = p.id == target.id, onClick = { target = p }, label = { Text(p.name) }) }
                    }
                }
                Gap(Space.l)
                SoftCard(Modifier.fillMaxWidth()) {
                    Text("כדי לקרוא את המסמך, הוא יישלח כולו ל-Claude (Anthropic), כולל שם, ת\"ז ופרטים אחרים שמופיעים בו. " +
                        "רק מה שתסמנו בשלב הבא יישמר, ${platform.deviceWord} בלבד ומוצפן.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Space.l))
                }
                Gap(Space.l)
                if (apiKey == null) Text("צריך להוסיף מפתח API בהגדרות.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = pages.isNotEmpty() && apiKey != null,
                    onClick = {
                        phase = Phase.READING; error = null
                        scope.launch {
                            runCatching { DocReader(apiKey!!).read(pages.toList(), withContext(Dispatchers.IO) { platform.repo.knownTests(target.id) }) }
                                .onSuccess { f -> found = f; keep.clear(); repeat(f.labs.size + f.meds.size + f.diagnoses.size + f.allergies.size) { keep += true }; phase = Phase.REVIEW }
                                .onFailure { error = "הקריאה נכשלה. בדקו את החיבור ונסו שוב."; phase = Phase.PICK }
                        }
                    },
                ) { Text("אישור ושליחה לקריאה") }
            }
        }
        Phase.READING -> Column(Modifier.fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Gap()
            Text("Claude קורא את המסמך…")
        }
        Phase.REVIEW -> {
            val f = found ?: return
            val rows = f.labs.map { "${it.name}  ${valueUnit(it.value, it.unit)}" + (it.date?.let { d -> " · ${fmtDate(d)}" } ?: "") } +
                f.meds.map { "תרופה: ${it.name}" + (it.dose?.let { d -> " $d" } ?: "") } +
                f.diagnoses.map { "אבחנה: $it" } + f.allergies.map { "רגישות: $it" }
            LazyColumn(contentPadding = PaddingValues(Space.l)) {
                item {
                    Text(listOfNotNull(f.title, f.source, f.date?.let(::fmtDate)).joinToString(" · ").ifEmpty { "מסמך" }, style = MaterialTheme.typography.titleMedium)
                    Text(if (rows.isEmpty()) "לא נמצאו בדיקות, תרופות או אבחנות. המסמך יישמר כקובץ." else "סמנו מה להוסיף לתיק של ${target.name}:",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(Space.s)
                }
                itemsIndexed(rows) { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keep[i], onCheckedChange = { keep[i] = it })
                        Text(r, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item {
                    Gap()
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Button(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { saveUpload(platform, target.id, pages, f, keep.toList()) }
                                onDone()
                            }
                        }) { Text("שמירה") }
                        OutlinedButton(onClick = onDone) { Text("ביטול") }
                    }
                }
            }
        }
    }
}

private fun saveUpload(platform: Platform, person: String, pages: List<Page>, f: Findings, keep: List<Boolean>) {
    val id = newId()
    var i = 0
    fun <T> pick(list: List<T>) = list.filter { keep.getOrElse(i++) { true } }
    val labs = pick(f.labs); val meds = pick(f.meds)
    val diagnoses = pick(f.diagnoses); val allergies = pick(f.allergies)
    pages.forEachIndexed { n, p -> platform.files.put("${id}_$n", p.bytes) }
    val u = Upload(id, System.currentTimeMillis(), pages.map { it.mime }, f.date, f.source, f.title, f.type, labs, meds)
    platform.repo.saveUpload(person, u)
    f.copy(diagnoses = diagnoses, allergies = allergies).conditions(id).forEach { platform.repo.saveCondition(person, it) }
}

/** An uploaded document: its pages, what was taken from it, and delete. */
@Composable
fun UploadView(person: String, upload: Upload?, onDeleted: () -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    if (upload == null) return Empty()
    LazyColumn(contentPadding = PaddingValues(Space.l)) {
        item {
            Text(listOfNotNull(upload.title, upload.source).joinToString(" · ").ifEmpty { "מסמך" }, style = MaterialTheme.typography.titleMedium)
            Text(fmtDate(upload.date), color = MaterialTheme.colorScheme.onSurfaceVariant)
            val taken = listOfNotNull(
                upload.labs.size.takeIf { it > 0 }?.let { "$it ערכי בדיקה" },
                upload.meds.size.takeIf { it > 0 }?.let { "$it תרופות" },
            ).joinToString(", ")
            if (taken.isNotEmpty()) Text("נוספו לתיק: $taken", style = MaterialTheme.typography.bodyMedium)
            Gap(Space.s)
            TextButton(onClick = { confirm = true }) { Text("מחיקת המסמך", color = MaterialTheme.colorScheme.error) }
        }
        items(upload.mimes.size) { n ->
            val bytes = remember(upload.id, n) { platform.files.get(upload.pageId(n)) }
            if (bytes != null) platform.PageView(Page(bytes, upload.mimes[n]), Modifier.fillMaxWidth().padding(vertical = Space.s))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("למחוק את המסמך?") },
        text = { Text("הקובץ וכל מה שנוסף ממנו (בדיקות, תרופות, אבחנות) יימחקו.") },
        confirmButton = { TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { platform.repo.deleteUpload(person, upload, platform.files) }; onDeleted() } }) { Text("מחיקה") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("ביטול") } },
    )
}

/** Home-screen card: diagnoses, allergies and the user's own notes. Tap to edit, + to add. */
@Composable
fun ConditionsCard(person: String, conditions: List<Condition>, onChanged: () -> Unit) {
    var editing by remember { mutableStateOf<Condition?>(null) }
    SectionTitle("אבחנות, רגישויות והערות", trailing = {
        TextButton(onClick = { editing = Condition("", Condition.DIAGNOSIS, "") }) { Text("+ הוספה") }
    })
    SoftCard(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        if (conditions.isEmpty()) Text("אין עדיין. אפשר להוסיף ידנית או מתוך מסמך שמעלים.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.l))
        conditions.forEachIndexed { i, c ->
            CardRow(c.text, listOfNotNull(Condition.labels[c.type], c.date?.let(::fmtDate), c.source).joinToString(" · "),
                onClick = { editing = c }, divider = i < conditions.lastIndex)
        }
    }
    editing?.let { c -> ConditionDialog(person, c, onClose = { changed -> editing = null; if (changed) onChanged() }) }
}

@Composable
private fun ConditionDialog(person: String, c: Condition, onClose: (Boolean) -> Unit) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(c.type) }
    var text by remember { mutableStateOf(c.text) }
    var date by remember { mutableStateOf(fmtDate(c.date)) }
    fun save(block: () -> Unit) = scope.launch { withContext(Dispatchers.IO) { block() }; onClose(true) }
    AlertDialog(
        onDismissRequest = { onClose(false) },
        title = { Text(if (c.id.isEmpty()) "הוספה" else "עריכה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Condition.labels.forEach { (k, label) -> FilterChip(selected = type == k, onClick = { type = k }, label = { Text(label) }) }
                }
                OutlinedTextField(text, { text = it }, label = { Text("תיאור") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("תאריך (לא חובה, dd/mm/yyyy)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                save { platform.repo.saveCondition(person, c.copy(id = c.id.ifEmpty { newId() }, type = type, text = text.trim(), date = parseDate(date))) }
            }) { Text("שמירה") }
        },
        dismissButton = {
            Row {
                if (c.id.isNotEmpty()) TextButton(onClick = { save { platform.repo.deleteCondition(person, c.id) } }) { Text("מחיקה", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.width(Space.xs))
                TextButton(onClick = { onClose(false) }) { Text("ביטול") }
            }
        },
    )
}

/** "05/03/2024" or "2024-03-05" → "2024-03-05"; anything else → null. */
fun parseDate(s: String): String? {
    val t = s.trim()
    Regex("""(\d{1,2})[/.](\d{1,2})[/.](\d{4})""").matchEntire(t)?.let { m ->
        val (d, mo, y) = m.destructured
        return "%s-%02d-%02d".format(y, mo.toInt(), d.toInt())
    }
    return Regex("""\d{4}-\d{2}-\d{2}""").find(t)?.value
}
