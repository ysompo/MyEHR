package com.myehr.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.myehr.app.R
import com.myehr.app.ai.AskScreen
import com.myehr.app.data.Person
import com.myehr.app.data.Repo
import com.myehr.app.data.Secrets
import com.myehr.app.data.Store
import com.myehr.app.sync.SyncScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val icon: Int) {
    HOME("בית", R.drawable.ic_home),
    LABS("בדיקות", R.drawable.ic_lab),
    ASK("שאלה", R.drawable.ic_ask),
    MEDS("תרופות", R.drawable.ic_pill),
    DOCS("מסמכים", R.drawable.ic_doc),
}

private sealed interface Sub {
    data object Sync : Sub
    data object Settings : Sub
    data class LabVisitDetail(val stickerId: String) : Sub
    data class LabHistory(val code: String) : Sub
}

@Composable
private fun <T> load(initial: T, vararg keys: Any?, block: suspend () -> T): T {
    val v by produceState(initial, *keys) { value = withContext(Dispatchers.IO) { block() } }
    return v
}

@Composable
fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { t ->
            NavigationBarItem(
                selected = t == tab, onClick = { onTab(t) },
                icon = { AppIcon(t.icon, null, tint = if (t == tab) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) },
                label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
fun App(textSize: TextSize, onTextSize: (TextSize) -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repo(Store.get(context)) }
    val secrets = remember { Secrets(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var askPrefill by remember { mutableStateOf<String?>(null) }
    var byTest by rememberSaveable { mutableStateOf(true) }
    var hasKey by remember { mutableStateOf(secrets.apiKey != null) }
    val people = load(emptyList<Person>(), refresh) { repo.people() }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    val person = people.firstOrNull { it.id == personId } ?: people.firstOrNull()

    BackHandler(enabled = sub != null || tab != Tab.HOME) { if (sub != null) sub = null else tab = Tab.HOME }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (sub == null) BottomBar(tab) { tab = it } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = sub) {
                Sub.Sync -> { SubHeader("סנכרון ממאוחדת") { sub = null }; SyncScreen(onDone = { refresh++; sub = null }) }
                Sub.Settings -> { SubHeader("הגדרות") { sub = null }; SettingsScreen(textSize, onTextSize, hasKey) { secrets.apiKey = null; hasKey = false } }
                is Sub.LabVisitDetail -> { SubHeader("תוצאות בדיקה") { sub = null }; LabDetailScreen(load(null, s, person?.id) { person?.let { repo.labDetail(it.id, s.stickerId) } }) }
                is Sub.LabHistory -> { SubHeader("מגמה לאורך זמן") { sub = null }; LabSeriesScreen(load(null, s, person?.id) { person?.let { repo.series(it.id, s.code) } }, load(emptyList(), person?.id) { person?.let { repo.aiData(it.id).medications() } ?: emptyList() }) }
                null -> {
                    if (person == null) {
                        if (tab == Tab.HOME) Welcome { sub = Sub.Sync } else Empty()
                        return@Column
                    }
                    val id = person.id
                    when (tab) {
                        Tab.HOME -> {
                            val d = load(null, id, refresh) { repo.dashboard(people, person) }
                            if (d != null) Dashboard(d, onPerson = { personId = it }, onAsk = { askPrefill = it; tab = Tab.ASK },
                                onSeries = { sub = Sub.LabHistory(it) }, onSync = { sub = Sub.Sync }, onSettings = { sub = Sub.Settings })
                        }
                        Tab.LABS -> LabsScreen(load(emptyList(), id) { repo.labSeries(id) }, load(emptyList(), id) { repo.labVisits(id) },
                            byTest, { byTest = it }, { sub = Sub.LabHistory(it.code) }, { sub = Sub.LabVisitDetail(it.stickerId) })
                        Tab.ASK -> AskScreen(person, askPrefill)
                        Tab.MEDS -> MedsScreen(load(emptyList(), id) { repo.activeMeds(id) }, load(emptyList(), id) { repo.prescriptions(id) }, load(emptyList(), id) { repo.purchases(id) })
                        Tab.DOCS -> DocsScreen(load(emptyList(), id) { repo.documents(id) }, load(emptyList(), id) { repo.vaccines(id) }, load(emptyList(), id) { repo.examinations(id) })
                    }
                }
            }
        }
    }
}

@Composable
fun Welcome(onSync: () -> Unit) {
    Column(Modifier.padding(Space.xl)) {
        Text("MyEHR", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Gap(Space.m)
        Text("התיק הרפואי של המשפחה, בטלפון שלכם בלבד.", style = MaterialTheme.typography.titleMedium)
        Gap(Space.s)
        Text("התחברו לאתר מאוחדת פעם אחת, והבדיקות, התרופות והמסמכים יישמרו כאן מוצפנים.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(Space.xl)
        androidx.compose.material3.Button(onClick = onSync) { Text("חיבור למאוחדת") }
    }
}
