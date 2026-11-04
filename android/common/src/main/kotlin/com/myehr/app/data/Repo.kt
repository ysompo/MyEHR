package com.myehr.app.data

import com.myehr.app.ai.AiData
import com.myehr.app.ai.MedSummary
import com.myehr.app.sync.Kind

/** Reads raw answers from the store and parses them for the screens. */
/** Read access to the stored raw answers (Android: SQLCipher; desktop: encrypted SQLite). */
interface RecordStore : com.myehr.app.sync.RawSink {
    fun people(): List<Person>
    fun all(person: String, kind: String): Map<String, String>
}

private fun RecordStore.get(person: String, kind: String): String? = get(person, kind, "")

class Repo(private val store: RecordStore) {
    fun people() = store.people()
    fun labVisits(p: String) = Parsers.labVisits(store.get(p, Kind.LAB_LIST))
    fun labDetail(p: String, stickerId: String) = Parsers.labDetail(store.get(p, Kind.LAB_DETAIL, stickerId))
    fun labSeries(p: String): List<LabSeries> =
        store.all(p, Kind.LAB_HISTORY).mapNotNull { (code, json) -> Parsers.labSeries(code, json) }
            .sortedBy { it.name }
    fun series(p: String, code: String) = Parsers.labSeries(code, store.get(p, Kind.LAB_HISTORY, code))
    fun activeMeds(p: String) = Parsers.activeMeds(store.get(p, Kind.MEDS_ACTIVE))
    fun prescriptions(p: String) = Parsers.prescriptions(store.get(p, Kind.MEDS_PRESCRIPTIONS))
    fun purchases(p: String) = Parsers.purchases(store.get(p, Kind.MEDS_PURCHASED))
    fun documents(p: String) = Parsers.documents(store.get(p, Kind.DOCUMENTS))
    fun vaccines(p: String) = Parsers.vaccines(store.get(p, Kind.VACCINES))
    fun examinations(p: String) = Parsers.examinations(store.get(p, Kind.EXAMS))
    fun upcomingVisits(p: String) = Parsers.upcomingVisits(store.get(p, Kind.APPOINTMENTS))
        .filter { (it.start ?: "") >= java.time.LocalDate.now().toString() }
    fun pastVisits(p: String) = Parsers.pastVisits(store.get(p, Kind.PASSPORT))

    fun memberJson(p: String) = Parsers.parse(store.get(p, Kind.MEMBER))

    /** Doctors, labs, clinics and pharmacies in this person's records: never sent to the AI. */
    fun providerNames(p: String): List<String?> =
        labVisits(p).flatMap { listOf(it.doctor, it.institute) } +
            activeMeds(p).map { it.doctor } +
            purchases(p).flatMap { listOf(it.doctor, it.pharmacy) } +
            prescriptions(p).map { it.doctor } +
            documents(p).map { it.doctor } +
            examinations(p).map { it.institute }

    fun dashboard(people: List<Person>, person: Person): com.myehr.app.ui.DashboardData {
        val all = labSeries(person.id)
        val byRecent = all.sortedByDescending { it.latest?.date ?: "" }
        return com.myehr.app.ui.DashboardData(
            people = people, selected = person,
            flagged = byRecent.filter { it.latest?.outOfRange == true }.take(6),
            latest = byRecent.take(6),
            meds = aiData(person.id).medications().filter { it.active },
        )
    }

    /** The AI's view of one person: names, values, dates. Built only from allow-listed fields. */
    fun aiData(p: String): AiData = object : AiData {
        override fun series() = labSeries(p)
        override fun medications(): List<MedSummary> {
            val bought = purchases(p).groupBy { it.name }
            val active = activeMeds(p)
            val names = (bought.keys + active.map { it.name } + prescriptions(p).flatMap { it.medicines }).distinct()
            return names.map { n ->
                val dates = bought[n].orEmpty().mapNotNull { it.date }.sorted()
                val a = active.firstOrNull { it.name == n }
                MedSummary(n, dates.firstOrNull(), dates.lastOrNull(), dates.size, a != null, a?.permanent ?: false)
            }.sortedBy { it.firstPurchase ?: "9999" }
        }
        override fun age(): Int? = (memberJson(p) as? kotlinx.serialization.json.JsonObject)
            ?.get("Age")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
    }
}

/** "2024-05-01T08:30:00" → "01/05/2024". */
fun fmtDate(s: String?): String {
    if (s == null || s.length < 10 || s[4] != '-') return s ?: ""
    return "${s.substring(8, 10)}/${s.substring(5, 7)}/${s.substring(0, 4)}"
}

/** Keeps numbers, units and ranges left-to-right inside Hebrew text ("13.4 g/dL", "12–16"). */
fun ltr(s: String): String = "\u2066$s\u2069"

/** "13.4 g/dL" as one left-to-right piece, or "" if both are missing. */
fun valueUnit(value: String?, unit: String?): String =
    listOfNotNull(value, unit).joinToString(" ").let { if (it.isEmpty()) it else ltr(it) }

/** "12–16" (either end may be missing). */
fun range(low: Double?, high: Double?): String = ltr("${low?.let(::num) ?: ""}–${high?.let(::num) ?: ""}")

private fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
