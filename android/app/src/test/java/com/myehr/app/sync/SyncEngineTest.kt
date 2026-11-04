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
        override fun putPerson(p: Person) { people += p }
        override fun put(person: String, kind: String, key: String, json: String) { raw[Triple(person, kind, key)] = json }
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

    @Test(expected = SessionExpired::class)
    fun htmlAnswerMeansLoggedOut() = runTest {
        val caller = Caller { _, _, _ -> Resp(200, "text/html", "<html>", "https://login.meuhedet.co.il/Account/Login") }
        SyncEngine(caller, Sink(), pauseMs = 0).syncCurrent()
    }
}
