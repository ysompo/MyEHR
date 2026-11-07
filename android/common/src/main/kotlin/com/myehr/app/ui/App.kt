package com.myehr.app.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.myehr.app.ai.AskScreen
import com.myehr.app.data.Person
import com.myehr.app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val icon: Ic) {
    HOME("בית", Ic.HOME),
    LABS("בדיקות", Ic.LAB),
    ASK("שאלה", Ic.ASK),
    MEDS("תרופות", Ic.PILL),
    VISITS("ביקורים", Ic.CALENDAR),
    DOCS("מסמכים", Ic.DOC),
}

private sealed interface Sub {
    data object Sync : Sub
    data object Settings : Sub
    data class LabVisitDetail(val stickerId: String) : Sub
    data class LabHistory(val code: String) : Sub
    data object AddDoc : Sub
    data class ViewFile(val fileId: String, val title: String) : Sub
    data class ViewUpload(val id: String) : Sub
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
                label = { Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

@Composable
fun App(textSize: TextSize, onTextSize: (TextSize) -> Unit, look: Look = PineLook, onLook: (Look) -> Unit = {}) {
    val platform = LocalPlatform.current
    val repo = platform.repo
    val secrets = platform.secrets
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sub by remember { mutableStateOf<Sub?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var askPrefill by remember { mutableStateOf<String?>(null) }
    var byTest by rememberSaveable { mutableStateOf(true) }
    var hasKey by remember { mutableStateOf(secrets.apiKey != null) }
    val people = load(emptyList<Person>(), refresh) { repo.people() }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    val person = people.firstOrNull { it.id == personId } ?: people.firstOrNull()

    val syncStatus by com.myehr.app.sync.SyncState.status.collectAsState()
    androidx.compose.runtime.LaunchedEffect(syncStatus.finishedAt) { if (syncStatus.finishedAt > 0) refresh++ }

    platform.BackHandler(enabled = sub != null || tab != Tab.HOME) { if (sub != null) sub = null else tab = Tab.HOME }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { if (sub == null) BottomBar(tab) { tab = it } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = sub) {
                Sub.Sync -> { SubHeader("סנכרון ממאוחדת") { sub = null }; platform.SyncContent(onDone = { refresh++; sub = null }) }
                Sub.Settings -> { SubHeader("הגדרות") { sub = null }; SettingsScreen(textSize, onTextSize, look, onLook, hasKey) { secrets.apiKey = null; hasKey = false } }
                is Sub.LabVisitDetail -> { SubHeader("תוצאות בדיקה") { sub = null }
                    if (person != null) {
                        val fid = com.myehr.app.sync.meuhedetFileId(person.id, "lab", s.stickerId)
                        if (remember(fid) { platform.files.has(fid) }) androidx.compose.material3.TextButton(onClick = { sub = Sub.ViewFile(fid, "תוצאות בדיקה (PDF)") },
                            modifier = Modifier.padding(horizontal = Space.s)) { Text("פתיחת קובץ ה-PDF של מאוחדת") }
                    }
                    LabDetailScreen(load(null, s, person?.id) { person?.let { repo.labDetail(it.id, s.stickerId) } }) }
                is Sub.ViewFile -> { SubHeader(s.title) { sub = null }
                    val bytes = load<ByteArray?>(null, s) { platform.files.get(s.fileId) }
                    if (bytes != null) androidx.compose.foundation.lazy.LazyColumn { item { platform.PageView(com.myehr.app.data.Page(bytes, "application/pdf"), Modifier.padding(Space.s)) } } }
                Sub.AddDoc -> { SubHeader("הוספת מסמך") { sub = null }; if (person != null) AddDocScreen(people, person) { refresh++; sub = null } }
                is Sub.ViewUpload -> { SubHeader("מסמך שהעליתם") { sub = null }
                    if (person != null) UploadView(person.id, load(null, s, person.id) { repo.upload(person.id, s.id) }) { refresh++; sub = null } }
                is Sub.LabHistory -> { SubHeader("מגמה לאורך זמן") { sub = null }; LabSeriesScreen(load(null, s, person?.id, refresh) { person?.let { repo.series(it.id, s.code) } }, load(emptyList(), person?.id) { person?.let { repo.aiData(it.id).medications() } ?: emptyList() }) }
                null -> {
                    if (person == null) {
                        if (tab == Tab.HOME) Welcome { sub = Sub.Sync } else Empty()
                        return@Column
                    }
                    val id = person.id
                    when (tab) {
                        Tab.HOME -> {
                            platform.UpdateBanner()
                            val d = load(null, id, refresh) { repo.dashboard(people, person) }
                            if (d != null) Dashboard(d, onPerson = { personId = it }, onAsk = { askPrefill = it; tab = Tab.ASK },
                                onSeries = { sub = Sub.LabHistory(it) }, onSync = { sub = Sub.Sync }, onSettings = { sub = Sub.Settings }, onConditionsChanged = { refresh++ })
                        }
                        Tab.LABS -> LabsScreen(load(emptyList(), id, refresh) { repo.labSeries(id) }, load(emptyList(), id) { repo.labVisits(id) },
                            byTest, { byTest = it }, { sub = Sub.LabHistory(it.code) }, { sub = Sub.LabVisitDetail(it.stickerId) })
                        Tab.ASK -> AskScreen(person, askPrefill)
                        Tab.MEDS -> MedsScreen(load(emptyList(), id) { repo.activeMeds(id) }, load(emptyList(), id) { repo.prescriptions(id) }, load(emptyList(), id) { repo.purchases(id) }, load(emptyList(), id, refresh) { repo.uploadedMeds(id) })
                        Tab.VISITS -> VisitsScreen(load(emptyList(), id, refresh) { repo.upcomingVisits(id) }, load(emptyList(), id, refresh) { repo.pastVisits(id) })
                        Tab.DOCS -> DocsScreen(load(emptyList(), id) { repo.documents(id) }, load(emptyList(), id) { repo.vaccines(id) }, load(emptyList(), id) { repo.examinations(id) },
                            load(emptyList(), id, refresh) { repo.uploads(id) }, onAdd = { sub = Sub.AddDoc }, onOpen = { sub = Sub.ViewUpload(it.id) },
                            fileOf = { d -> d.guid?.let { com.myehr.app.sync.meuhedetFileId(id, "doc", it + d.documentKey) }?.takeIf { platform.files.has(it) } },
                            onOpenFile = { fid, title -> sub = Sub.ViewFile(fid, title) })
                    }
                }
            }
        }
    }
}

@Composable
fun Welcome(onSync: () -> Unit) {
    val platform = LocalPlatform.current
    Column(Modifier.padding(Space.xl)) {
        Text("MyEHR", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Gap(Space.m)
        Text("התיק הרפואי של המשפחה, ${platform.deviceWord} שלכם בלבד.", style = MaterialTheme.typography.titleMedium)
        Gap(Space.s)
        Text("התחברו לאתר מאוחדת פעם אחת, והבדיקות, התרופות והמסמכים יישמרו כאן מוצפנים.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(Space.xl)
        androidx.compose.material3.Button(onClick = onSync) { Text("חיבור למאוחדת") }
    }
}
