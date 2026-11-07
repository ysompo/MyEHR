package com.myehr.app.ai

import com.myehr.app.data.LabSeries
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A medication as Claude may see it: name and dates only (no doctor, pharmacy, prescription number). */
data class MedSummary(
    val name: String,
    val firstPurchase: String?,
    val lastPurchase: String?,
    val purchases: Int,
    val active: Boolean,
    val permanent: Boolean,
    /** null = Meuhedet; otherwise the uploaded document's source. Never sent to Claude (may name a provider). */
    val source: String? = null,
)

/** What the tools can read for ONE person. Implemented over Repo on the phone, faked in tests. */
interface AiData {
    fun series(): List<LabSeries>
    fun medications(): List<MedSummary>
    fun age(): Int?
    /** Diagnoses, allergies and the user's own notes. */
    fun conditions(): List<com.myehr.app.data.Condition> = emptyList()
}

/** Kinds of data, used for the first-time "what will be sent" consent. */
enum class DataKind(val label: String) {
    TEST_NAMES("רשימת שמות הבדיקות"),
    LAB_VALUES("ערכי בדיקות ותאריכים"),
    MEDICATIONS("שמות תרופות ותאריכי רכישה"),
    AGE("גיל"),
    CONDITIONS("אבחנות, רגישויות והערות שהוספתם"),
}

/** Result of running a tool: the JSON sent to Claude, plus a human summary for the preview/log. */
data class ToolOutput(val kind: DataKind, val json: String, val summary: String, val isError: Boolean = false)

data class ToolSpec(val name: String, val description: String, val properties: Map<String, Map<String, Any>>, val required: List<String>)

/**
 * The only way data reaches Claude. Every output is built field by field from an allow-list:
 * test/medication names, values, units, normal ranges, dates, counts, age. Nothing else.
 */
class AiTools(private val data: AiData) {

    val specs = listOf(
        ToolSpec(
            "list_lab_tests",
            "List the lab tests this person has results for: code, name, unit, number of results, first and last date. Call this first to find the right test code. Names may be Hebrew or English.",
            emptyMap(), emptyList(),
        ),
        ToolSpec(
            "get_lab_history",
            "Get every result of one lab test over time: date, numeric value, result text, unit, normal range low/high, and whether it was out of range.",
            mapOf("code" to mapOf("type" to "string", "description" to "Test code from list_lab_tests")),
            listOf("code"),
        ),
        ToolSpec(
            "list_medications",
            "List this person's medications: name, first purchase date (use this as the start date), last purchase date, number of purchases, whether currently active, whether marked permanent, and source (Meuhedet or an uploaded document, where first_purchase is the document's start date).",
            emptyMap(), emptyList(),
        ),
        ToolSpec(
            "list_conditions",
            "List this person's diagnoses, allergies/sensitivities and their own notes, with dates. Notes are the person's own words, not medical records.",
            emptyMap(), emptyList(),
        ),
        ToolSpec(
            "get_age",
            "Get this person's age in years. Only call if age matters for the answer.",
            emptyMap(), emptyList(),
        ),
    )

    fun kindOf(tool: String): DataKind = when (tool) {
        "list_lab_tests" -> DataKind.TEST_NAMES
        "get_lab_history" -> DataKind.LAB_VALUES
        "list_medications" -> DataKind.MEDICATIONS
        "get_age" -> DataKind.AGE
        "list_conditions" -> DataKind.CONDITIONS
        else -> DataKind.TEST_NAMES
    }

    fun run(tool: String, input: JsonObject): ToolOutput = when (tool) {
        "list_lab_tests" -> listTests()
        "get_lab_history" -> history((input["code"] as? JsonPrimitive)?.content.orEmpty())
        "list_medications" -> meds()
        "get_age" -> age()
        "list_conditions" -> conditions()
        else -> ToolOutput(DataKind.TEST_NAMES, """{"error":"unknown tool"}""", "כלי לא מוכר", isError = true)
    }

    private fun listTests(): ToolOutput {
        val all = data.series()
        val json = buildJsonArray {
            all.forEach { s ->
                add(buildJsonObject {
                    put("code", s.code)
                    put("name", s.name)
                    put("unit", s.unit)
                    put("results", s.points.size)
                    put("first_date", s.points.firstOrNull()?.date?.take(10))
                    put("last_date", s.points.lastOrNull()?.date?.take(10))
                })
            }
        }
        return ToolOutput(DataKind.TEST_NAMES, json.toString(), "רשימת ${all.size} שמות בדיקות")
    }

    private fun history(code: String): ToolOutput {
        val s = data.series().firstOrNull { it.code == code }
            ?: return ToolOutput(DataKind.LAB_VALUES, """{"error":"no test with this code"}""", "בדיקה לא נמצאה", isError = true)
        val json = buildJsonObject {
            put("name", s.name)
            put("unit", s.unit)
            put("data_cautions", buildJsonArray { s.cautions().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            put("results", buildJsonArray {
                s.points.forEach { p ->
                    add(buildJsonObject {
                        put("date", p.date?.take(10))
                        put("value", p.value)
                        put("result_text", p.resultText)
                        put("norm_low", p.normLow)
                        put("norm_high", p.normHigh)
                        put("out_of_range", p.outOfRange)
                        put("source", if (p.source == null) "Meuhedet" else "uploaded document")
                    })
                }
            })
        }
        val years = listOfNotNull(s.points.firstOrNull()?.date?.take(4), s.points.lastOrNull()?.date?.take(4)).distinct().joinToString("–")
        return ToolOutput(DataKind.LAB_VALUES, json.toString(), "${s.name}: ${s.points.size} תוצאות ($years)")
    }

    private fun meds(): ToolOutput {
        val m = data.medications()
        val json = buildJsonArray {
            m.forEach { x ->
                add(buildJsonObject {
                    put("name", x.name)
                    put("first_purchase", x.firstPurchase?.take(10))
                    put("last_purchase", x.lastPurchase?.take(10))
                    put("purchases", x.purchases)
                    put("active", x.active)
                    put("permanent", x.permanent)
                    put("source", if (x.source == null) "Meuhedet" else "uploaded document")
                })
            }
        }
        return ToolOutput(DataKind.MEDICATIONS, json.toString(), "${m.size} תרופות")
    }

    private fun conditions(): ToolOutput {
        val c = data.conditions()
        val json = buildJsonArray {
            c.forEach { x ->
                add(buildJsonObject {
                    put("type", x.type)
                    put("text", x.text)
                    put("date", x.date?.take(10))
                    put("source", when { x.type == com.myehr.app.data.Condition.NOTE -> "the person's own note"; x.source == null -> "added by the person"; else -> "uploaded document" })
                })
            }
        }
        return ToolOutput(DataKind.CONDITIONS, json.toString(), "${c.size} אבחנות, רגישויות והערות")
    }

    private fun age(): ToolOutput {
        val a = data.age()
        return ToolOutput(DataKind.AGE, buildJsonObject { put("age", a) }.toString(), "גיל")
    }
}

/**
 * Last line of defence: removes any personal string (names, ID, doctors, clinics, pharmacies)
 * that might have slipped into text going to Claude, e.g. inside a test name.
 */
class Guard(forbidden: Collection<String>) {
    private val words = forbidden.map { it.trim() }.filter { it.length >= 3 }.distinct().sortedByDescending { it.length }

    fun scrub(text: String): String {
        var out = text
        for (w in words) out = out.replace(w, "[הוסר]", ignoreCase = true)
        // Israeli ID / phone-like digit runs (8+ digits) never belong in lab or medication data.
        out = Regex("(?<![\\d.])\\d{8,}(?![\\d.])").replace(out, "[הוסר]")
        return out
    }
}

/** Builds the list of personal strings from the person's member record and the stored records. */
fun personalStrings(memberJson: JsonElement?, extra: Collection<String?>): List<String> {
    val out = mutableListOf<String>()
    fun walk(e: JsonElement?, key: String = "") {
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> walk(v, k) }
            is JsonArray -> e.forEach { walk(it, key) }
            is JsonPrimitive -> if (e.isString && key.matches(Regex("(?i).*(name|id|phone|mail|guid|mask).*"))) {
                out += e.content
                out += e.content.split(' ').filter { it.length >= 3 }
            }
            JsonNull, null -> {}
        }
    }
    walk(memberJson)
    extra.filterNotNull().forEach { out += it; out += it.split(' ').filter { p -> p.length >= 3 && !p.startsWith("ד\"ר") } }
    return out
}
