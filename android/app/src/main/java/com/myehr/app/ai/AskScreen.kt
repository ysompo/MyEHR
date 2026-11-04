package com.myehr.app.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myehr.app.data.Person
import com.myehr.app.data.Repo
import com.myehr.app.data.Secrets
import com.myehr.app.data.Store
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pending first-time consent: what will be sent, and the answer. */
private class ConsentRequest(val kind: DataKind, val summary: String, val reply: CompletableDeferred<Boolean>)

@Composable
fun AskScreen(person: Person, prefill: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secrets = remember { Secrets(context) }
    val storeLazy = remember { lazy { Store.get(context) } }  // opened only when a question is sent
    val repoLazy = remember { lazy { Repo(storeLazy.value) } }

    var hasKey by remember { mutableStateOf(secrets.apiKey != null) }
    var keyInput by remember { mutableStateOf("") }
    var question by remember(prefill) { mutableStateOf(prefill ?: "") }
    var answer by remember { mutableStateOf<String?>(null) }
    var sentText by remember { mutableStateOf<String?>(null) }
    var showSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<ConsentRequest?>(null) }

    if (!hasKey) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("שאלות חופשיות נענות על ידי Claude (Anthropic). נשלחים רק מספרים, תאריכים ושמות בדיקות ותרופות, ללא שם, ת\"ז או שמות רופאים.")
            Text("הזינו מפתח API מ-console.anthropic.com. הוא נשמר מוצפן בטלפון.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(keyInput, { keyInput = it }, label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(onClick = { secrets.apiKey = keyInput; keyInput = ""; hasKey = secrets.apiKey != null }, enabled = keyInput.startsWith("sk-")) { Text("שמירה") }
        }
        return
    }

    fun send() {
        val q = question.trim()
        if (q.isEmpty() || busy) return
        busy = true; answer = null; sentText = null; showSent = false
        scope.launch {
            try {
                val (tools, guard) = withContext(Dispatchers.IO) {
                    repoLazy.value.let { repo -> AiTools(repo.aiData(person.id)) to Guard(personalStrings(repo.memberJson(person.id), repo.providerNames(person.id) + person.name)) }
                }
                val agent = AiAgent(secrets.apiKey!!, tools, guard, beforeSend = { out ->
                    if (out.isError || secrets.consented(out.kind.name)) true
                    else {
                        val d = CompletableDeferred<Boolean>()
                        withContext(Dispatchers.Main) { consent = ConsentRequest(out.kind, out.summary, d) }
                        d.await().also { if (it) secrets.setConsented(out.kind.name) }
                    }
                })
                val a = agent.ask(q)
                answer = a
                sentText = agent.sent.joinToString("\n\n")
                withContext(Dispatchers.IO) { storeLazy.value.logAi(person.id, q, sentText!!, a) }
            } catch (e: Exception) {
                answer = "שגיאה: ${e.javaClass.simpleName}. בדקו חיבור לאינטרנט ואת מפתח ה-API."
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("שאלה על ${person.name}", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(question, { question = it }, label = { Text("למשל: מה ההמוגלובין האחרון שלי?") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = ::send, enabled = !busy && question.isNotBlank()) { Text("שאל") }
            TextButton(onClick = { secrets.apiKey = null; hasKey = false }) { Text("החלפת מפתח") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        answer?.let { Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(16.dp)) } }
        sentText?.let {
            TextButton(onClick = { showSent = !showSent }) { Text(if (showSent) "הסתר מה נשלח" else "מה נשלח ל-Claude?") }
            if (showSent) Text(it, style = MaterialTheme.typography.bodySmall)
        }
        Text("התשובות אינן ייעוץ רפואי. שינוי לאחר תחילת תרופה אינו מוכיח שהתרופה גרמה לו.", style = MaterialTheme.typography.bodySmall)
    }

    consent?.let { c ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("לשלוח ל-Claude?") },
            text = { Text("בפעם הראשונה: יישלחו ${c.kind.label}.\nלדוגמה עכשיו: ${c.summary}.\nלא יישלחו שם, ת\"ז, טלפון, או שמות רופאים ומרפאות.") },
            confirmButton = { TextButton(onClick = { consent = null; c.reply.complete(true) }) { Text("אישור") } },
            dismissButton = { TextButton(onClick = { consent = null; c.reply.complete(false) }) { Text("לא") } },
        )
    }
}
