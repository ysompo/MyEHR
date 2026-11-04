package com.myehr.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.myehr.app.data.Person
import com.myehr.app.data.Repo
import com.myehr.app.data.Store
import com.myehr.app.sync.SyncScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface Screen {
    data object Home : Screen
    data object Sync : Screen
    data class LabVisitDetail(val stickerId: String) : Screen
    data class LabHistory(val code: String) : Screen
}

/** Loads data off the main thread; reloads when [keys] change. */
@Composable
private fun <T> load(initial: T, vararg keys: Any?, block: suspend () -> T): T {
    val v by produceState(initial, *keys) { value = withContext(Dispatchers.IO) { block() } }
    return v
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val repo = remember { Repo(Store.get(context)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var refresh by remember { mutableIntStateOf(0) }
    val people = load(emptyList<Person>(), refresh) { repo.people() }
    var personId by rememberSaveable { mutableStateOf<String?>(null) }
    val person = people.firstOrNull { it.id == personId } ?: people.firstOrNull()

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (screen == Screen.Sync) "סנכרון ממאוחדת" else "התיק הרפואי שלי",
                onBack = if (screen != Screen.Home) ({ screen = Screen.Home }) else null,
                onSync = if (screen == Screen.Home) ({ screen = Screen.Sync }) else null,
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val s = screen) {
                Screen.Sync -> SyncScreen(onDone = { refresh++; screen = Screen.Home })
                is Screen.LabVisitDetail -> {
                    val d = load(null, s, person?.id) { person?.let { repo.labDetail(it.id, s.stickerId) } }
                    LabDetailScreen(d)
                }
                is Screen.LabHistory -> {
                    val series = load(null, s, person?.id) { person?.let { repo.series(it.id, s.code) } }
                    LabSeriesScreen(series)
                }
                Screen.Home -> {
                    if (person == null) { Empty(); return@Column }
                    PersonChips(people, person.id) { personId = it }
                    Home(repo, person, onVisit = { screen = Screen.LabVisitDetail(it) }, onSeries = { screen = Screen.LabHistory(it) })
                }
            }
        }
    }
}

@Composable
private fun Home(repo: Repo, person: Person, onVisit: (String) -> Unit, onSeries: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    HomeTabs(tab) { tab = it }
    val id = person.id
    when (tab) {
        0 -> com.myehr.app.ai.AskScreen(person)
        1 -> LabSeriesList(load(emptyList(), id) { repo.labSeries(id) }) { onSeries(it.code) }
        2 -> LabVisitList(load(emptyList(), id) { repo.labVisits(id) }) { onVisit(it.stickerId) }
        3 -> MedsScreen(
            load(emptyList(), id) { repo.activeMeds(id) },
            load(emptyList(), id) { repo.prescriptions(id) },
            load(emptyList(), id) { repo.purchases(id) },
        )
        else -> DocsScreen(
            load(emptyList(), id) { repo.documents(id) },
            load(emptyList(), id) { repo.vaccines(id) },
            load(emptyList(), id) { repo.examinations(id) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?, onSync: (() -> Unit)?) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
            }
        },
        actions = { if (onSync != null) TextButton(onClick = onSync) { Text("סנכרון") } },
    )
}

@Composable
fun PersonChips(people: List<Person>, selectedId: String, onSelect: (String) -> Unit) {
    if (people.size < 2) return
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
        people.forEach { p ->
            FilterChip(selected = p.id == selectedId, onClick = { onSelect(p.id) }, label = { Text(p.name) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

val HomeTabTitles = listOf("שאלה", "לפי בדיקה", "לפי תאריך", "תרופות", "מסמכים")

@Composable
fun HomeTabs(tab: Int, onTab: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
        HomeTabTitles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { onTab(i) }, text = { Text(t, style = MaterialTheme.typography.labelLarge) }) }
    }
}
