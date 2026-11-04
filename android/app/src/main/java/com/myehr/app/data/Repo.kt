package com.myehr.app.data

import com.myehr.app.sync.Kind

/** Reads raw answers from the store and parses them for the screens. */
class Repo(private val store: Store) {
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
}

/** "2024-05-01T08:30:00" → "01/05/2024". */
fun fmtDate(s: String?): String {
    if (s == null || s.length < 10 || s[4] != '-') return s ?: ""
    return "${s.substring(8, 10)}/${s.substring(5, 7)}/${s.substring(0, 4)}"
}
