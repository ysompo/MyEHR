package com.myehr.app.sync

import com.myehr.app.data.Person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Fake site with invented data.
class SyncEngineTest {
    private class Sink : RawSink {
        val raw = mutableMapOf<Triple<String, String, String>, String>()
        val people = mutableListOf<Person>()
        val at = mutableMapOf<Triple<String, String, String>, Long>()
        var now = 1_000_000_000L
        override fun putPerson(p: Person) { people += p }
        override fun put(person: String, kind: String, key: String, json: String) { raw[Triple(person, kind, key)] = json; at[Triple(person, kind, key)] = now }
        override fun get(person: String, kind: String, key: String) = raw[Triple(person, kind, key)]
        override fun fetchedAt(person: String, kind: String, key: String) = at[Triple(person, kind, key)]
    }

    private fun ok(text: String) = Resp(200, "application/json; charset=utf-8", text, "https://online.meuhedet.co.il/x")

    @Test fun pullsEverythingForCurrentPerson() = runTest {
        val calls = mutableListOf<String>()
        val caller = Caller { method, path, body ->
            calls += "$method ${path.substringBefore('?')}"
            when {
                path.startsWith(Api.MEMBER) -> ok("""{"Id":"000000001","FullName":"Test Person"}""")
                path.startsWith(Api.LAB_LIST) -> { assertTrue(body!!.contains("FromDate")); ok("""{"Items":[{"StickerId":"11","LabCode":"2","TestDate":"2023-06-01T00:00:00"}]}""") }
                path.startsWith("/api/labs/2/stickers/11") -> { assertTrue(path.contains("date=20230601")); ok("""{"testSections":[]}""") }
                path.startsWith(Api.LAB_CATALOG) -> ok("""{"Tests":[{"Key":"100","Value":"A"},{"Key":"101","Value":"B"}]}""")
                path.startsWith(Api.LAB_HISTORY) -> ok("""{"Items":[]}""")
                path.startsWith("/api/medications/info") -> Resp(409, "application/problem+json", "{}", "")
                else -> ok("{}")
            }
        }
        val sink = Sink()
        val p = SyncEngine(caller, sink, today = LocalDate.of(2025, 1, 1), pauseMs = 0).syncCurrent()
        assertEquals("000000001", p.id)
        assertTrue(sink.raw.containsKey(Triple("000000001", Kind.LAB_DETAIL, "11")))
        assertTrue(sink.raw.containsKey(Triple("000000001", Kind.LAB_HISTORY, "101")))
        assertTrue(sink.raw.containsKey(Triple("000000001", Kind.MEDS_PRESCRIPTIONS, "")))
        assertEquals(2, calls.count { it == "POST ${Api.LAB_HISTORY}" })
        assertEquals(1, sink.people.size)
    }

    @Test fun oneFailedSectionIsReportedAndTheRestIsKept() = runTest {
        val caller = Caller { _, path, _ ->
            when {
                path.startsWith(Api.MEMBER) -> ok("""{"Id":"000000001","FullName":"Test Person"}""")
                path.startsWith(Api.MEDS_PURCHASED) -> Resp(500, "application/json", "{}", "")
                path.startsWith(Api.VACCINES) -> Resp(0, "", "", "") // network error
                else -> ok("{}")
            }
        }
        val sink = Sink()
        val engine = SyncEngine(caller, sink, today = LocalDate.of(2025, 1, 1), pauseMs = 0)
        engine.syncCurrent()
        assertEquals(listOf("תרופות (Test)", "חיסונים (Test)"), engine.failures.toList())
        assertTrue(sink.raw.containsKey(Triple("000000001", Kind.MEDS_ACTIVE, "")))
        assertTrue(sink.raw.containsKey(Triple("000000001", Kind.APPOINTMENTS, "")))
    }

    @Test fun savesNewPdfsOnce() = runTest {
        val vault = object : com.myehr.app.data.FileVault {
            val m = mutableMapOf<String, ByteArray>()
            override fun put(id: String, bytes: ByteArray) { m[id] = bytes }
            override fun get(id: String) = m[id]
            override fun delete(id: String) { m.remove(id) }
        }
        val fileCalls = mutableListOf<String>()
        val pdfB64 = java.util.Base64.getEncoder().encodeToString("%PDF-1.4 fake".toByteArray())
        val caller = object : Caller {
            override suspend fun call(method: String, path: String, body: String?) = when {
                path.startsWith(Api.MEMBER) -> ok("""{"Id":"000000001","FullName":"Test Person"}""")
                path.startsWith(Api.LAB_LIST) -> ok("""{"Items":[{"StickerId":"11","LabCode":"2","TestDate":"2023-06-01T00:00:00"}]}""")
                path.startsWith(Api.DOCUMENTS) -> ok("""{"Items":[{"DocumentDescription":"D","DocumentGuid":"g1","DocumentKey":"k1","Available":true}]}""")
                else -> ok("{}")
            }
            override suspend fun file(path: String): ByteArray? {
                fileCalls += path
                return if (path.contains("/pdf/")) "%PDF-1.4 fake".toByteArray() else """{"File":"$pdfB64"}""".toByteArray()
            }
        }
        val e = SyncEngine(caller, Sink(), today = LocalDate.of(2025, 1, 1), pauseMs = 0, files = vault)
        e.syncCurrent()
        assertEquals(2, vault.m.size)
        assertTrue(vault.m.values.all { String(it).startsWith("%PDF-") })
        assertTrue(vault.has(meuhedetFileId("000000001", "lab", "11")))
        SyncEngine(caller, Sink(), today = LocalDate.of(2025, 1, 1), pauseMs = 0, files = vault).syncCurrent()
        assertEquals(2, fileCalls.size) // already saved: not downloaded again
    }

    @Test fun countsNewResultsAfterFirstSync() = runTest {
        var stickers = """[{"StickerId":"11","LabCode":"2","TestDate":"2023-06-01T00:00:00"}]"""
        val detail = """{"testSections":[{"tests":[{"records":[
            {"title":"A","scale":{"minRange":1,"maxRange":5,"value":"9"}},{"title":"B","scale":{"minRange":1,"maxRange":5,"value":"3"}}]}]}]}"""
        val caller = Caller { _, path, _ ->
            when {
                path.startsWith(Api.MEMBER) -> ok("""{"Id":"000000001","FullName":"Test Person"}""")
                path.startsWith(Api.LAB_LIST) -> ok("""{"Items":$stickers}""")
                path.startsWith("/api/labs/") -> ok(detail)
                else -> ok("{}")
            }
        }
        val sink = Sink()
        val first = SyncEngine(caller, sink, today = LocalDate.of(2025, 1, 1), pauseMs = 0).also { it.syncCurrent() }
        assertTrue(first.newResults.isEmpty()) // first sync: everything is "new", no alert
        stickers = """[{"StickerId":"11","LabCode":"2","TestDate":"2023-06-01T00:00:00"},{"StickerId":"12","LabCode":"2","TestDate":"2024-06-01T00:00:00"}]"""
        val second = SyncEngine(caller, sink, today = LocalDate.of(2025, 1, 1), pauseMs = 0).also { it.syncCurrent() }
        assertEquals(mapOf("Test" to (2 to 1)), second.newResults)
        assertEquals("2 תוצאות חדשות (1 מחוץ לטווח): Test 2", SyncState.newResultsText(second.newResults))
    }

    @Test fun secondSyncFetchesOnlyNewResults() = runTest {
        var stickers = """{"Items":[{"StickerId":"11","LabCode":"2","TestDate":"2023-06-01T00:00:00"}]}"""
        val calls = mutableListOf<String>()
        val caller = Caller { method, path, _ ->
            calls += "$method ${path.substringBefore('?')}"
            when {
                path.startsWith(Api.MEMBER) -> ok("""{"Id":"000000001","FullName":"P"}""")
                path.startsWith(Api.LAB_LIST) -> ok(stickers)
                path.startsWith(Api.LAB_CATALOG) -> ok("""{"Tests":[{"Key":"100"},{"Key":"101"}]}""")
                else -> ok("{}")
            }
        }
        val sink = Sink()
        val engine = SyncEngine(caller, sink, pauseMs = 0, clock = { sink.now })
        engine.syncCurrent()
        assertEquals(1, calls.count { it.startsWith("GET /api/labs/") }); assertEquals(2, calls.count { it == "POST ${Api.LAB_HISTORY}" })

        // Nothing new: no details, no history.
        calls.clear(); sink.now += 24 * 3600_000L
        engine.syncCurrent()
        assertEquals(0, calls.count { it.startsWith("GET /api/labs/") }); assertEquals(0, calls.count { it == "POST ${Api.LAB_HISTORY}" })

        // One new result: its details + a history refresh.
        calls.clear(); sink.now += 24 * 3600_000L
        stickers = """{"Items":[{"StickerId":"11","LabCode":"2","TestDate":"2023-06-01T00:00:00"},{"StickerId":"12","LabCode":"2","TestDate":"2024-06-01T00:00:00"}]}"""
        engine.syncCurrent()
        assertEquals(listOf("GET /api/labs/2/stickers/12"), calls.filter { it.startsWith("GET /api/labs/") })
        assertEquals(2, calls.count { it == "POST ${Api.LAB_HISTORY}" })
    }

    @Test fun syncsEachFamilyMemberAndSwitchesBack() = runTest {
        var selected: String? = null
        val switches = mutableListOf<String?>()
        val caller = Caller { _, path, _ ->
            when {
                path.startsWith(Api.MEMBER) -> ok(when (selected) {
                    null -> """{"Id":"000000001","FullName":"Parent"}"""
                    "g-2" -> """{"Id":"000000002","FullName":"Spouse"}"""
                    else -> """{"Id":"000000003","FullName":"Child","IsChild":true}"""
                })
                path.startsWith("/api/Personal/User") -> ok("""{"LoginUser":{"Id":"000000001"},
                    "GeneticFamilyMembers":[{"Id":"000000002","Guid":"g-2","FullName":"Spouse"},{"Id":"000000003","Guid":"g-3","FullName":"Child","IsChild":true},{"Id":"000000001","Guid":"g-1"}],
                    "FamilyMembers":[{"Id":"000000002","Guid":""}]}""")
                else -> ok("{}")
            }
        }
        val sink = Sink()
        val people = SyncEngine(caller, sink, pauseMs = 0).syncFamily { g -> selected = g; switches += g }
        assertEquals(listOf("000000001", "000000002", "000000003"), people.map { it.id })
        assertEquals(listOf(null, "g-2", "g-3", null), switches)
        assertTrue(sink.raw.containsKey(Triple("000000003", Kind.MEMBER, "")))
    }

    @Test(expected = SessionExpired::class)
    fun htmlAnswerMeansLoggedOut() = runTest {
        val caller = Caller { _, _, _ -> Resp(200, "text/html", "<html>", "https://login.meuhedet.co.il/Account/Login") }
        SyncEngine(caller, Sink(), pauseMs = 0).syncCurrent()
    }
}
