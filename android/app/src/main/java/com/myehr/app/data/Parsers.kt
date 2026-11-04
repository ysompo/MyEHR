package com.myehr.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Turns raw Meuhedet JSON into app models. Field names come from docs/SITE_MAP.md.
 * Parsing never throws: missing or odd fields become null / empty lists.
 */
object Parsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String?): JsonElement? =
        if (text.isNullOrBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull()

    // ---------- tiny JSON helpers ----------
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()
    private fun JsonObject?.el(key: String): JsonElement? = this?.get(key)?.takeUnless { it is JsonNull }
    private fun JsonObject?.str(key: String): String? =
        (el(key) as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject?.num(key: String): Double? =
        (el(key) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.replace(",", ".").trim().toDoubleOrNull() }
    private fun JsonObject?.bool(key: String): Boolean? = (el(key) as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject?.list(key: String): List<JsonElement> = el(key).arr()
    private fun joinName(vararg parts: String?) = parts.filterNotNull().joinToString(" ").trim().ifEmpty { null }

    // ---------- identity ----------
    fun person(text: String?): Person? {
        val o = parse(text).obj() ?: return null
        val id = o.str("Id") ?: o.str("Guid") ?: return null
        val name = o.str("FullName") ?: joinName(o.str("FirstName"), o.str("LastName")) ?: id
        return Person(id = id, name = name, isChild = o.bool("IsChild") ?: false)
    }

    /** Family members from /api/Personal/User: (guid, name, isChild). Members without a GUID can't be selected. */
    fun familyMembers(text: String?): List<Triple<String, String, Boolean>> {
        val o = parse(text).obj() ?: return emptyList()
        val loginId = o.el("LoginUser").obj().str("Id")
        val seen = mutableSetOf<String>()
        return (o.list("GeneticFamilyMembers") + o.list("FamilyMembers")).mapNotNull { e ->
            val m = e.obj()
            val guid = m.str("Guid") ?: return@mapNotNull null
            if (m.str("Id") != null && m.str("Id") == loginId) return@mapNotNull null
            if (!seen.add(guid)) return@mapNotNull null
            Triple(guid, m.str("FullName") ?: joinName(m.str("FirstName"), m.str("LastName")) ?: guid, m.bool("IsChild") ?: false)
        }
    }

    // ---------- labs ----------
    fun labVisits(text: String?): List<LabVisit> =
        parse(text).obj().list("Items").mapNotNull { e ->
            val o = e.obj()
            val id = o.str("StickerId") ?: return@mapNotNull null
            LabVisit(
                stickerId = id,
                labCode = o.str("LabCode") ?: "2",
                date = o.str("TestDate"),
                doctor = joinName(o.str("DoctorFirstName"), o.str("DoctorLastName")),
                institute = o.str("InstituteName"),
            )
        }.sortedByDescending { it.date ?: "" }

    fun labDetail(text: String?): LabDetail? {
        val root = parse(text).obj() ?: return null
        val summary = root.el("detailsSection").obj().el("summary").obj()
        val records = root.list("testSections").flatMap { s ->
            s.obj().list("tests").flatMap { t ->
                t.obj().list("records").mapNotNull { r -> labRecord(r.obj()) }
            }
        }
        return LabDetail(labName = summary.str("labName"), date = summary.str("testDateTime"), records = records)
    }

    private fun labRecord(r: JsonObject?): LabRecord? {
        r ?: return null
        val title = r.str("title") ?: return null
        val scale = r.el("scale").obj()
        val single = r.el("single").obj()
        val value = single.str("value") ?: scale.str("value")
        val sign = single.str("charSign") ?: scale.str("charSign")
        return LabRecord(
            title = title,
            value = listOfNotNull(sign, value).joinToString("").ifEmpty { null },
            unit = single.str("measurementUnit") ?: scale.str("measurementUnit"),
            min = scale.num("minRange"),
            max = scale.num("maxRange"),
            panic = r.num("panicLevel")?.toInt(),
            note = r.el("note").obj().str("text") ?: single.str("remark"),
        )
    }

    /** HistoryTestsSearchBarData → list of (code, name). */
    fun labTestCatalog(text: String?): List<Pair<String, String>> =
        parse(text).obj().list("Tests").mapNotNull { e ->
            val o = e.obj()
            val k = o.str("Key") ?: return@mapNotNull null
            k to (o.str("Value") ?: k)
        }

    fun labSeries(code: String, text: String?): LabSeries? {
        val o = parse(text).obj() ?: return null
        val points = o.list("Items").map { e ->
            val i = e.obj()
            LabPoint(
                date = i.str("TestDate"),
                value = i.num("TestValue") ?: i.num("Result"),
                resultText = i.str("Result"),
                unit = i.str("MeasureUnit"),
                normLow = i.num("NormFrom"),
                normHigh = i.num("NormTo"),
                stickerId = i.str("StickerId"),
            )
        }.sortedBy { it.date ?: "" }
        if (points.isEmpty()) return null
        val name = o.str("TestDesc") ?: o.list("Items").firstNotNullOfOrNull { it.obj().str("TestDesc") } ?: code
        return LabSeries(code = code, name = name, points = points)
    }

    // ---------- medications ----------
    fun activeMeds(text: String?): List<Medication> =
        parse(text).arr().mapNotNull { e ->
            val o = e.obj()
            val name = o.str("description") ?: return@mapNotNull null
            val f = o.el("flags").obj()
            Medication(
                name = name,
                doctor = o.list("doctors").firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.trim() },
                start = f.str("nearestPrescriptionStart"),
                end = f.str("nearestPrescriptionEnd"),
                permanent = f.bool("permenant") ?: false,
            )
        }

    fun purchases(text: String?): List<Purchase> =
        parse(text).obj().list("Items").mapNotNull { e ->
            val o = e.obj()
            Purchase(
                date = o.str("Date"),
                name = o.str("Description") ?: return@mapNotNull null,
                doctor = o.str("DoctorName"),
                pharmacy = o.str("Pharmacy"),
            )
        }.sortedByDescending { it.date ?: "" }

    fun prescriptions(text: String?): List<Prescription> =
        parse(text).obj().list("prescriptions").map { e ->
            val o = e.obj()
            Prescription(
                number = o.str("prescriptionNumber"),
                doctor = o.str("doctorName"),
                specialty = o.str("doctorSpecialty"),
                createdAt = o.str("createdAt"),
                startsAt = o.str("startsAt"),
                endsAt = o.str("endsAt"),
                medicines = o.list("medicines").mapNotNull { it.obj().str("description") },
            )
        }.sortedByDescending { it.createdAt ?: "" }

    // ---------- documents / record ----------
    fun documents(text: String?): List<MedDocument> =
        parse(text).obj().list("Items").mapNotNull { e ->
            val o = e.obj()
            MedDocument(
                date = o.str("FormDate"),
                title = o.str("DocumentDescription") ?: o.str("DocumentTypeDesc") ?: return@mapNotNull null,
                type = o.str("DocumentTypeDesc"),
                doctor = o.str("FormDoctorName"),
                specialty = o.str("FormDoctorSpec"),
                documentKey = o.str("DocumentKey"),
                guid = o.str("DocumentGuid"),
                formCode = o.str("FormCode"),
                formSourceSystem = o.str("FormSourceSystem"),
                available = o.bool("Available") ?: true,
            )
        }.sortedByDescending { it.date ?: "" }

    fun vaccines(text: String?): List<Vaccine> =
        parse(text).obj().list("Items").mapNotNull { e ->
            val o = e.obj()
            Vaccine(date = o.str("Date"), name = o.str("Name") ?: return@mapNotNull null)
        }.sortedByDescending { it.date ?: "" }

    fun examinations(text: String?): List<Examination> =
        parse(text).obj().list("Items").mapNotNull { e ->
            val o = e.obj()
            Examination(
                date = o.str("TestDate"),
                description = o.str("Description") ?: return@mapNotNull null,
                institute = o.str("InstituteName"),
                isEcg = o.bool("IsEKGResult") ?: false,
            )
        }.sortedByDescending { it.date ?: "" }
}
