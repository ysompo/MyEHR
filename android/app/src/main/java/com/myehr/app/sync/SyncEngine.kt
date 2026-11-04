package com.myehr.app.sync

import com.myehr.app.data.Parsers
import com.myehr.app.data.Person
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One HTTP answer, as seen from inside the logged-in Meuhedet page. */
data class Resp(val status: Int, val contentType: String, val text: String, val url: String)

/** Makes a same-origin request from the logged-in page (cookies included). */
fun interface Caller {
    suspend fun call(method: String, path: String, body: String?): Resp
}

/** Where raw answers go. */
interface RawSink {
    fun putPerson(p: Person)
    fun put(person: String, kind: String, key: String, json: String)
    fun get(person: String, kind: String, key: String): String?
    /** When this answer was stored (epoch ms), or null if never. */
    fun fetchedAt(person: String, kind: String, key: String): Long?
}

class SessionExpired : Exception("session expired")

/** Selects the active family member on the site (null = back to the logged-in person). */
fun interface MemberSwitcher {
    suspend fun select(guid: String?)
}

/** Endpoints from docs/SITE_MAP.md. */
object Api {
    const val MEMBER = "/umbraco/api/WeakPersonalInfoApi/GetCurrentMemberinfo"
    const val PERSONAL_USER = "/api/Personal/User?currentNodeId=2327"
    /** Cookie the site uses to pick the active family member (value = member GUID). */
    const val MEMBER_COOKIE = "meuhedet.current.member2"
    const val LAB_LIST = "/umbraco/api/LabApi/Stickers"
    fun labDetail(labCode: String, stickerId: String, yyyymmdd: String) = "/api/labs/$labCode/stickers/$stickerId?date=$yyyymmdd"
    const val LAB_CATALOG = "/umbraco/api/LabApi/HistoryTestsSearchBarData"
    const val LAB_HISTORY = "/umbraco/api/LabApi/HistoryTests"
    const val MEDS_ACTIVE = "/api/medications/active/all"
    const val MEDS_PURCHASED = "/api/medications/meds/purchased"
    const val MEDS_APPROVALS = "/api/medications/meds/approvals"
    const val MEDS_PRESCRIPTIONS = "/api/medications/prescriptions"
    const val DOCUMENTS = "/umbraco/api/OnlineCommunicationApi/GetVisitsRefs"
    const val VISIT_SUMMARIES = "/api/visits/products/summaries"
    const val VACCINES = "/umbraco/api/NursesApi/VaccinesData"
    const val EXAMS = "/api/ExaminationsApi/Search"
    const val PASSPORT = "/umbraco/api/PersonalInfoApi/medicalPassport"
    const val APPOINTMENTS = "/umbraco/api/PersonalDashboardApi/Apponitments"

    // Files (PDF)
    fun labPdf(labCode: String, stickerId: String, yyyymmdd: String) = "/api/labs/$labCode/stickers/$stickerId/pdf/$yyyymmdd"
    fun approvalPdf(key: String) = "/api/medications/meds/approval?key=$key"
    fun documentPdf(guid: String, documentKey: String, formCode: String, formSourceSystem: String) =
        "/umbraco/api/OnlineCommunicationApi/ReferenceFile?guid=$guid&documentKey=$documentKey&formCode=$formCode&formSourceSystem=$formSourceSystem"
}

/** Kinds used as keys in the local store. */
object Kind {
    const val MEMBER = "member"
    const val LAB_LIST = "lab_list"
    const val LAB_DETAIL = "lab_detail"
    const val LAB_CATALOG = "lab_catalog"
    const val LAB_HISTORY = "lab_history"
    const val MEDS_ACTIVE = "meds_active"
    const val MEDS_PURCHASED = "meds_purchased"
    const val MEDS_APPROVALS = "meds_approvals"
    const val MEDS_PRESCRIPTIONS = "meds_prescriptions"
    const val DOCUMENTS = "documents"
    const val VISIT_SUMMARIES = "visit_summaries"
    const val VACCINES = "vaccines"
    const val EXAMS = "exams"
    const val PASSPORT = "passport"
    const val APPOINTMENTS = "appointments"
    /** Bookkeeping: "1" while a lab-history refresh is still owed for this person. */
    const val STATE = "sync_state"
}

/**
 * Pulls everything for the person currently active on the site.
 * Family switching is added once the switch request is recorded.
 */
class SyncEngine(
    private val caller: Caller,
    private val sink: RawSink,
    private val today: LocalDate = LocalDate.now(),
    private val pauseMs: Long = 250,
    private val onProgress: (String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object { const val RESUME_WINDOW_MS = 6 * 3600_000L }

    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private fun isoDay(d: LocalDate) = d.atStartOfDay().atOffset(ZoneOffset.UTC).format(iso)

    private suspend fun json(method: String, path: String, body: String? = null, okStatuses: Set<Int> = setOf(200)): String? {
        val r = caller.call(method, path, body)
        // Signed out: an error code, or the request ended on the login site / sign-out page.
        if (r.status == 401 || r.status == 403 || r.url.contains("login.meuhedet", ignoreCase = true) || r.url.contains("AuthSurface/Signout", ignoreCase = true)) throw SessionExpired()
        if (r.contentType.contains("html", ignoreCase = true)) throw SessionExpired()
        if (r.status !in okStatuses) return null
        if (pauseMs > 0) delay(pauseMs)
        return r.text.takeIf { Parsers.parse(it) != null }
    }

    /**
     * The logged-in person, then every family member reachable through the family link.
     * Always switches back to the logged-in person at the end.
     */
    suspend fun syncFamily(switcher: MemberSwitcher): List<Person> {
        switcher.select(null) // start from the logged-in person, even after an interrupted run
        val done = mutableListOf(syncCurrent())
        val family = Parsers.familyMembers(json("GET", Api.PERSONAL_USER))
        try {
            family.forEachIndexed { i, (guid, _, _) ->
                onProgress("בן משפחה ${i + 1}/${family.size}")
                switcher.select(guid)
                val p = syncCurrent()
                // Guard: if the site ignored the switch we'd be re-reading the same person.
                if (done.none { it.id == p.id }) done += p
            }
        } finally {
            switcher.select(null)
        }
        return done
    }

    suspend fun syncCurrent(): Person {
        onProgress("פרטי המשתמש")
        val memberJson = json("GET", Api.MEMBER) ?: throw SessionExpired()
        val person = Parsers.person(memberJson) ?: throw SessionExpired()
        val id = person.id
        sink.put(id, Kind.MEMBER, "", memberJson)

        onProgress("בדיקות מעבדה")
        val range = """{"FromDate":"${isoDay(today.minusYears(30))}","ToDate":"${isoDay(today.plusDays(1))}"}"""
        val labList = json("POST", Api.LAB_LIST, range)
            ?: json("POST", Api.LAB_LIST, """{"FromDate":"${isoDay(today.minusYears(3))}","ToDate":"${isoDay(today.plusDays(1))}"}""")
        var newVisits = 0
        if (labList != null) {
            sink.put(id, Kind.LAB_LIST, "", labList)
            // Only results not stored yet (lab results don't change once issued).
            val visits = Parsers.labVisits(labList).filter { sink.fetchedAt(id, Kind.LAB_DETAIL, it.stickerId) == null }
            newVisits = visits.size
            if (newVisits > 0) sink.put(id, Kind.STATE, "history_pending", "1")
            visits.forEachIndexed { i, v ->
                onProgress("תוצאות חדשות ${i + 1}/${visits.size}")
                val day = v.date?.take(10)?.replace("-", "") ?: return@forEachIndexed
                json("GET", Api.labDetail(v.labCode, v.stickerId, day))?.let { sink.put(id, Kind.LAB_DETAIL, v.stickerId, it) }
            }
        }

        // History (graphs) only when there are new results, or never fetched. Resumable:
        // tests refreshed in the last few hours are skipped after an interruption.
        val pending = sink.get(id, Kind.STATE, "history_pending") == "1"
        json("GET", Api.LAB_CATALOG)?.let { catalog ->
            sink.put(id, Kind.LAB_CATALOG, "", catalog)
            val fresh = clock() - RESUME_WINDOW_MS
            val tests = Parsers.labTestCatalog(catalog).filter { (code, _) ->
                val at = sink.fetchedAt(id, Kind.LAB_HISTORY, code)
                at == null || (pending && at < fresh)
            }
            tests.forEachIndexed { i, (code, _) ->
                onProgress("היסטוריית בדיקות ${i + 1}/${tests.size}")
                json("POST", Api.LAB_HISTORY, """{"Tests":["$code"]}""")?.let { sink.put(id, Kind.LAB_HISTORY, code, it) }
            }
            sink.put(id, Kind.STATE, "history_pending", "0")
        }

        onProgress("תרופות")
        json("GET", Api.MEDS_ACTIVE)?.let { sink.put(id, Kind.MEDS_ACTIVE, "", it) }
        json("GET", Api.MEDS_PURCHASED)?.let { sink.put(id, Kind.MEDS_PURCHASED, "", it) }
        json("GET", Api.MEDS_APPROVALS)?.let { sink.put(id, Kind.MEDS_APPROVALS, "", it) }
        json("GET", Api.MEDS_PRESCRIPTIONS)?.let { sink.put(id, Kind.MEDS_PRESCRIPTIONS, "", it) }

        onProgress("מסמכים")
        json("GET", Api.DOCUMENTS)?.let { sink.put(id, Kind.DOCUMENTS, "", it) }
        json("GET", Api.VISIT_SUMMARIES)?.let { sink.put(id, Kind.VISIT_SUMMARIES, "", it) }
        json("GET", Api.VACCINES)?.let { sink.put(id, Kind.VACCINES, "", it) }
        json("POST", Api.EXAMS, range)?.let { sink.put(id, Kind.EXAMS, "", it) }
        json("GET", Api.PASSPORT)?.let { sink.put(id, Kind.PASSPORT, "", it) }
        onProgress("תורים")
        json("POST", Api.APPOINTMENTS)?.let { sink.put(id, Kind.APPOINTMENTS, "", it) }

        val done = person.copy(lastSync = System.currentTimeMillis())
        sink.putPerson(done)
        return done
    }
}
