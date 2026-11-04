package com.myehr.app.ai

import com.myehr.app.data.LabPoint
import com.myehr.app.data.LabSeries
import com.myehr.app.data.Parsers
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Invented data only. "Person Test", "000000001", "Dr Alpha", "Lab Beta" stand in for personal strings.
class AiTest {
    private val data = object : AiData {
        override fun series() = listOf(LabSeries("100", "HEMOGLOBIN", listOf(
            LabPoint("2020-01-01T08:00:00", 13.0, "13.0", "g/dL", 12.0, 16.0, "s1"),
            LabPoint("2023-01-01T08:00:00", 11.5, "11.5", "g/dL", 12.0, 16.0, "s2"),
        )))
        override fun medications() = listOf(MedSummary("MED A 10MG", "2022-05-01T00:00:00", "2024-01-01T00:00:00", 5, true, true))
        override fun age() = 41
    }
    private val member = Parsers.parse("""{"Id":"000000001","FullName":"Person Test","FirstName":"Person","LastName":"Test","MobilePhoneNumber":"0500000000","Age":41}""")
    private val guard = Guard(personalStrings(member, listOf("Dr Alpha", "Lab Beta")))

    @Test fun toolsSendOnlyAllowedFields() {
        val t = AiTools(data)
        val h = t.run("get_lab_history", kotlinx.serialization.json.buildJsonObject { put("code", kotlinx.serialization.json.JsonPrimitive("100")) })
        assertTrue(h.json.contains("\"out_of_range\":true"))
        assertFalse(h.json.contains("s1")) // internal sticker ids are not sent
        assertTrue(t.run("list_medications", kotlinx.serialization.json.JsonObject(emptyMap())).json.contains("2022-05-01"))
        assertTrue(t.run("nope", kotlinx.serialization.json.JsonObject(emptyMap())).isError)
    }

    @Test fun guardRemovesPersonalStrings() {
        val out = guard.scrub("Person Test (000000001) saw Dr Alpha at Lab Beta; phone 0500000000; HbA1c 5.6")
        listOf("Person", "Test", "000000001", "Alpha", "Beta", "0500000000").forEach { assertFalse(it, out.contains(it)) }
        assertTrue(out.contains("5.6"))
    }

    /** Runs the real SDK against a fake API: one tool call, then an answer. */
    @Test fun agentLoopAgainstFakeApi() = runBlocking {
        val server = MockWebServer()
        val r1 = """{"id":"m1","type":"message","role":"assistant","model":"$MODEL","stop_reason":"tool_use","stop_sequence":null,
            "content":[{"type":"tool_use","id":"tu1","name":"get_lab_history","input":{"code":"100"}}],"usage":{"input_tokens":10,"output_tokens":5}}"""
        val r2 = """{"id":"m2","type":"message","role":"assistant","model":"$MODEL","stop_reason":"end_turn","stop_sequence":null,
            "content":[{"type":"text","text":"Last hemoglobin: 11.5 g/dL on 2023-01-01."}],"usage":{"input_tokens":10,"output_tokens":5}}"""
        listOf(r1, r2).forEach { server.enqueue(MockResponse().setHeader("content-type", "application/json").setBody(it)) }
        server.start()
        try {
            val consents = mutableListOf<DataKind>()
            val agent = AiAgent("sk-test", AiTools(data), guard, beforeSend = { consents += it.kind; true },
                baseUrl = server.url("/").toString().trimEnd('/'))
            val answer = agent.ask("What is my last hemoglobin? I am Person Test")
            assertEquals("Last hemoglobin: 11.5 g/dL on 2023-01-01.", answer)
            assertEquals(listOf(DataKind.LAB_VALUES), consents)
            assertEquals(2, server.requestCount)
            val reqs = listOf(server.takeRequest(), server.takeRequest())
            val bodies = reqs.map { it.body.readUtf8() }
            val headers = reqs.map { it.getHeader("anthropic-beta").orEmpty() }
            assertTrue(bodies[0].contains("\"model\":\"$MODEL\""))
            assertTrue(bodies[0].contains("\"fallbacks\":\"default\""))
            assertTrue(headers[0].contains("server-side-fallback-2026-07-01"))
            assertTrue(bodies[1].contains("tool_result") && bodies[1].contains("11.5"))
            bodies.forEach { b -> listOf("Person", "000000001", "Alpha", "Beta").forEach { assertFalse("leaked $it", b.contains(it)) } }
        } finally { server.shutdown() }
    }
}
