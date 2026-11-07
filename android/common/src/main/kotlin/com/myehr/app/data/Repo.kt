package com.myehr.app.data

import com.myehr.app.ai.AiData
import com.myehr.app.ai.MedSummary
import com.myehr.app.sync.Kind
import kotlinx.serialization.decodeFromString

/** Reads raw answers from the store and parses them for the screens. */
/** Read access to the stored raw answers (Android: SQLCipher; desktop: encrypted SQLite). */
interface RecordStore : com.myehr.app.sync.RawSink {
    fun people(): List<Person>
    fun all(person: String, kind: String): Map<String, String>
    fun delete(person: String, kind: String, key: String)
}

private fun RecordStore.get(person: String, kind: String): String? = get(person, kind, "")

class Repo(private val store: RecordStore) {
    fun people() = store.people()
    fun labVisits(p: String) = Parsers.labVisits(store.get(p, Kind.LAB_LIST))
    fun labDetail(p: String, stickerId: String) = Parsers.labDetail(store.get(p, Kind.LAB_DETAIL, stickerId))
    /** Meuhedet's lab series with values from uploaded documents merged in (Meuhedet wins on the same day). */
    fun labSeries(p: String): List<LabSeries> =
        demographics(memberJson(p)).let { who ->
            mergeUploads(store.all(p, Kind.LAB_HISTORY).mapNotNull { (code, json) -> Parsers.labSeries(code, json)?.let { GeneralRanges.apply(fillRanges(p, it), who) } }, uploads(p))
                .map { GeneralRanges.apply(it, who) }
                .sortedBy { it.name }
        }

    /** History answers sometimes lack the normal range; the result's own page usually has it. */
    private fun fillRanges(p: String, s: LabSeries): LabSeries {
        if (s.points.all { it.normLow != null || it.normHigh != null }) return s
        return s.copy(points = s.points.map { pt ->
            if (pt.normLow != null || pt.normHigh != null || pt.stickerId == null || pt.value == null) return@map pt
            val recs = labDetail(p, pt.stickerId)?.records.orEmpty().filter { it.numeric == pt.value && (it.min != null || it.max != null) }
            val r = recs.firstOrNull { sameTestName(it.title, s.name) } ?: recs.singleOrNull() ?: return@map pt
            pt.copy(normLow = r.min, normHigh = r.max)
        })
    }
    fun series(p: String, code: String) = labSeries(p).firstOrNull { it.code == code }

    // ---- Uploaded documents, diagnoses, allergies and notes ----
    fun uploads(p: String): List<Upload> = store.all(p, UpKind.UPLOAD).values
        .mapNotNull { runCatching { upJson.decodeFromString<Upload>(it) }.getOrNull() }
        .sortedByDescending { it.date ?: "" }
    fun upload(p: String, id: String): Upload? = uploads(p).firstOrNull { it.id == id }
    fun saveUpload(p: String, u: Upload) = store.put(p, UpKind.UPLOAD, u.id, u.toJson())
    fun deleteUpload(p: String, u: Upload, vault: FileVault) {
        u.mimes.indices.forEach { vault.delete(u.pageId(it)) }
        conditions(p).filter { it.uploadId == u.id }.forEach { deleteCondition(p, it.id) }
        store.delete(p, UpKind.UPLOAD, u.id)
    }
    fun conditions(p: String): List<Condition> = store.all(p, UpKind.CONDITION).values
        .mapNotNull { runCatching { upJson.decodeFromString<Condition>(it) }.getOrNull() }
        .sortedWith(compareBy<Condition> { it.type }.thenByDescending { it.date ?: "" })
    fun saveCondition(p: String, c: Condition) = store.put(p, UpKind.CONDITION, c.id, c.toJson())
    fun deleteCondition(p: String, id: String) = store.delete(p, UpKind.CONDITION, id)
    fun uploadedMeds(p: String): List<Pair<UpMed, Upload>> = uploads(p).flatMap { u -> u.meds.map { it to u } }

    /**
     * What changed since the last visit, for an upcoming one: since the last past visit in the same specialty
     * (else the last visit of any kind, else one year back).
     */
    fun visitPrep(p: String, v: Visit): VisitPrep {
        val past = pastVisits(p).filter { (it.start ?: "") < (v.start ?: "9999") }.sortedByDescending { it.start ?: "" }
        val same = past.firstOrNull { v.specialty != null && it.specialty == v.specialty }
        val sinceVisit = same ?: past.firstOrNull()
        val since = sinceVisit?.start?.take(10) ?: java.time.LocalDate.now().minusYears(1).toString()
        val series = labSeries(p)
        val newResults = series.flatMap { s -> s.points.filter { (it.date?.take(10) ?: "") > since }.map { s to it } }
            .sortedByDescending { it.second.date ?: "" }
        val meds = aiData(p).medications()
        return VisitPrep(
            visit = v, since = since, sinceVisit = sinceVisit, sameSpecialty = same != null,
            newResults = newResults,
            stillOut = series.filter { s -> s.latest?.outOfRange == true && (s.latest?.date?.take(10) ?: "") <= since },
            medsStarted = meds.filter { (it.firstPurchase?.take(10) ?: "") > since },
            activeMeds = meds.filter { it.active },
            conditions = conditions(p),
            newUploads = uploads(p).filter { (it.date ?: "") > since },
        )
    }

    /** Searches everything stored for this person, on the device only. Every word must appear. */
    fun search(p: String, query: String): List<Hit> {
        val words = query.lowercase().split(Regex("\\s+")).map { it.filter { c -> c.isLetterOrDigit() || c == '.' } }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        fun ok(vararg parts: String?) = parts.filterNotNull().joinToString(" ").lowercase().let { t -> words.all { it in t } }
        val hits = mutableListOf<Hit>()
        labSeries(p).filter { ok(it.name, it.unit) }.forEach { s ->
            val l = s.latest
            hits += Hit("בדיקות", s.name, listOfNotNull(l?.resultText?.let { v -> listOfNotNull(v, s.unit).joinToString(" ") }, fmtDate(l?.date)).joinToString(" · "), Hit.Target.Series(s.code))
        }
        aiData(p).medications().filter { ok(it.name) }.forEach { m ->
            hits += Hit("תרופות", m.name, listOfNotNull(m.firstPurchase?.let { "מ-${fmtDate(it)}" }, if (m.active) "פעילה" else null, m.source).joinToString(" · "), Hit.Target.Meds)
        }
        conditions(p).filter { ok(it.text, Condition.labels[it.type]) }.forEach { c ->
            hits += Hit("אבחנות והערות", c.text, listOfNotNull(Condition.labels[c.type], c.date?.let(::fmtDate)).joinToString(" · "), Hit.Target.Home)
        }
        uploads(p).filter { u -> ok(u.title, u.source, u.type, *u.labs.map { it.name }.toTypedArray(), *u.meds.map { it.name }.toTypedArray()) }.forEach { u ->
            hits += Hit("מסמכים", listOfNotNull(u.title, u.source).joinToString(" · ").ifEmpty { "מסמך" }, fmtDate(u.date), Hit.Target.Upload(u.id))
        }
        documents(p).filter { ok(it.title, it.type, it.doctor, it.specialty) }.forEach { d ->
            hits += Hit("מסמכים", d.title, listOfNotNull(fmtDate(d.date), d.doctor, d.specialty).joinToString(" · "), Hit.Target.Doc(d))
        }
        (upcomingVisits(p) + pastVisits(p)).filter { ok(it.specialty, it.doctor, it.place) }.forEach { v ->
            hits += Hit("ביקורים", listOfNotNull(v.specialty, v.doctor).joinToString(" · ").ifEmpty { "ביקור" }, listOfNotNull(fmtDate(v.start), v.place).joinToString(" · "), Hit.Target.Visits)
        }
        examinations(p).filter { ok(it.description, it.institute) }.forEach { e -> hits += Hit("מסמכים", e.description, listOfNotNull(fmtDate(e.date), e.institute).joinToString(" · "), Hit.Target.None) }
        vaccines(p).filter { ok(it.name, "חיסון") }.forEach { v -> hits += Hit("חיסונים", v.name, fmtDate(v.date), Hit.Target.None) }
        return hits
    }

    /** Test codes and names this person already has, so a document's tests can be linked to them. */
    fun knownTests(p: String): List<Pair<String, String>> =
        store.all(p, Kind.LAB_HISTORY).mapNotNull { (code, json) -> Parsers.labSeries(code, json)?.let { code to it.name } }
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
            examinations(p).map { it.institute } +
            uploads(p).map { it.source }

    fun dashboard(people: List<Person>, person: Person): com.myehr.app.ui.DashboardData {
        val all = labSeries(person.id)
        val byRecent = all.sortedByDescending { it.latest?.date ?: "" }
        return com.myehr.app.ui.DashboardData(
            people = people, selected = person,
            flagged = byRecent.filter { it.latest?.outOfRange == true }.take(6),
            latest = byRecent.take(6),
            meds = aiData(person.id).medications().filter { it.active },
            conditions = conditions(person.id),
        )
    }

    /** The AI's view of one person: names, values, dates. Built only from allow-listed fields. */
    fun aiData(p: String): AiData = object : AiData {
        override fun series() = labSeries(p)
        override fun medications(): List<MedSummary> {
            val bought = purchases(p).groupBy { it.name }
            val active = activeMeds(p)
            val names = (bought.keys + active.map { it.name } + prescriptions(p).flatMap { it.medicines }).distinct()
            val fromMeuhedet = names.map { n ->
                val dates = bought[n].orEmpty().mapNotNull { it.date }.sorted()
                val a = active.firstOrNull { it.name == n }
                MedSummary(n, dates.firstOrNull(), dates.lastOrNull(), dates.size, a != null, a?.permanent ?: false)
            }
            // From uploaded documents: the document's start date (or its date) is the start.
            val fromDocs = uploadedMeds(p).filter { (m, _) -> fromMeuhedet.none { sameTestName(it.name, m.name) } }
                .groupBy { it.first.name }.map { (n, list) ->
                    val starts = list.mapNotNull { (m, u) -> m.start ?: u.date }.sorted()
                    MedSummary(n, starts.firstOrNull(), list.mapNotNull { (m, u) -> m.end ?: m.start ?: u.date }.maxOrNull(), 0,
                        active = false, permanent = false, source = list.first().second.source ?: "מסמך שהועלה")
                }
            return (fromMeuhedet + fromDocs).sortedBy { it.firstPurchase ?: "9999" }
        }
        override fun conditions() = this@Repo.conditions(p)
        override fun age(): Int? = (memberJson(p) as? kotlinx.serialization.json.JsonObject)
            ?.get("Age")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
    }
}

/**
 * Adds uploaded lab values to the series: to the Meuhedet test with the same code or name, or to a new series.
 * A value on a day Meuhedet already has for that test is the same result: Meuhedet's is kept.
 */
fun mergeUploads(series: List<LabSeries>, uploads: List<Upload>): List<LabSeries> {
    val out = series.associateBy { it.code }.toMutableMap()
    uploads.sortedBy { it.addedAt }.forEach { u ->
        u.labs.forEach { l ->
            val date = (l.date ?: u.date)?.take(10) ?: return@forEach
            val target = out[l.code] ?: out.values.firstOrNull { sameTestName(it.name, l.name) }
            val code = target?.code ?: "up:" + l.name.lowercase().filter { it.isLetterOrDigit() }
            val existing = target ?: out[code]
            if (existing?.points?.any { it.date?.take(10) == date } == true) return@forEach
            val v = l.value?.replace(",", ".")?.trim()?.toDoubleOrNull()
            val point = LabPoint("${date}T00:00:00", v, l.value, l.unit ?: existing?.unit, l.low, l.high, null, u.source ?: "מסמך שהועלה")
            out[code] = LabSeries(code, existing?.name ?: l.name, ((existing?.points ?: emptyList()) + point).sortedBy { it.date ?: "" })
        }
    }
    return out.values.toList()
}

/** One search result and where tapping it goes. */
data class Hit(val group: String, val title: String, val subtitle: String, val target: Target) {
    sealed interface Target {
        data class Series(val code: String) : Target
        data class Upload(val id: String) : Target
        data class Doc(val doc: MedDocument) : Target
        data object Meds : Target
        data object Visits : Target
        data object Home : Target
        data object None : Target
    }
}

data class VisitPrep(
    val visit: Visit,
    val since: String,
    val sinceVisit: Visit?,
    val sameSpecialty: Boolean,
    val newResults: List<Pair<LabSeries, LabPoint>>,
    /** Out of range at their last result, from before [since]: still worth mentioning. */
    val stillOut: List<LabSeries>,
    val medsStarted: List<com.myehr.app.ai.MedSummary>,
    val activeMeds: List<com.myehr.app.ai.MedSummary>,
    val conditions: List<Condition>,
    val newUploads: List<Upload>,
)

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
fun range(low: Double?, high: Double?): String = when {
    low != null && high == null -> ltr("≥ ${num(low)}")
    low == null && high != null -> ltr("≤ ${num(high)}")
    else -> ltr("${low?.let(::num) ?: ""}–${high?.let(::num) ?: ""}")
}

private fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
