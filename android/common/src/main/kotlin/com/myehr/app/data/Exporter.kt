package com.myehr.app.data

import com.myehr.app.sync.meuhedetFileId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.ByteArrayOutputStream
import java.time.LocalDate

private val README = """
# Medical records of {{name}}

Exported {{date}} from the MyEHR app. Everything here belongs to one person.
Sources: Meuhedet health fund (synced from its website) and documents the person uploaded
(private doctors, hospitals), marked "source" in each record.

## If you are an AI assistant
These files are your project files; read this first.
- Say where each fact comes from (file name, and the record's date and source) so it can be checked.
  A record describes the day it was made: a diagnosis or medication in an old record may no longer hold.
- Something missing here is not evidence it never happened. The export holds what Meuhedet's website
  shows and what the person uploaded, which is less than their whole medical history.
- Items with source "the person's own note" are their word, not a record. If a note and a record disagree,
  show both with their dates.
- Lab values from other labs may use other methods and ranges: compare each value with its own range.
  See "cautions" in labs.json.
- Explain in plain language. Don't diagnose and don't advise changing a treatment.
  If something looks like it needs a doctor soon, say so plainly.
- Keep the person's name and ID out of anything that leaves this computer (web searches, other services).
- Don't edit these files; write your own notes in a separate folder.

## Files
| File | Contents |
|---|---|
| summary.md | One-page summary for a doctor: diagnoses, allergies, current medications, latest results |
| labs.json | Every lab test over time: date, value, unit, normal range, source, cautions |
| medications.json | Medications: first and last purchase (Meuhedet) or the document's dates (uploads) |
| conditions.json | Diagnoses, allergies and the person's own notes |
| visits.json | Past visits and upcoming appointments |
| documents.json | Documents list (Meuhedet and uploaded), vaccines and tests, with their file names |
| files/ | PDFs from Meuhedet and the uploaded documents, named by date |
""".trimIndent()

/** Builds an AES-256 encrypted ZIP of one person's records, for a doctor or an AI assistant. */
object Exporter {
    fun fileName(today: LocalDate) = "MyEHR-export-$today.zip"

    fun build(repo: Repo, files: FileVault, person: Person, password: CharArray, today: LocalDate = LocalDate.now()): ByteArray {
        val p = person.id
        val out = ByteArrayOutputStream()
        val used = mutableSetOf<String>()
        fun unique(name: String): String {
            var n = name; var i = 2
            while (!used.add(n)) n = name.substringBeforeLast('.') + "_" + (i++) + "." + name.substringAfterLast('.')
            return n
        }
        ZipOutputStream(out, password).use { zip ->
            fun add(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipParameters().apply {
                    fileNameInZip = name
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                })
                zip.write(bytes); zip.closeEntry()
            }
            fun addJson(name: String, e: JsonElement) = add(name, prettyJson.encodeToString(JsonElement.serializer(), e).toByteArray())

            add("README.md", README.replace("{{name}}", person.name).replace("{{date}}", today.toString()).toByteArray())
            add("summary.md", summary(repo, person, today).toByteArray())

            addJson("labs.json", buildJsonArray {
                repo.labSeries(p).forEach { s ->
                    add(buildJsonObject {
                        put("test", s.name); put("unit", s.unit)
                        put("cautions", JsonArray(s.cautions().map(::JsonPrimitive)))
                        put("results", buildJsonArray {
                            s.points.forEach { pt ->
                                add(buildJsonObject {
                                    put("date", pt.date?.take(10)); put("value", pt.value); put("result_text", pt.resultText)
                                    put("unit", pt.unit); put("norm_low", pt.normLow); put("norm_high", pt.normHigh)
                                    put("out_of_range", pt.outOfRange); put("source", pt.source ?: "Meuhedet")
                                    pt.generalRange?.let { g -> put("range_source", "general published range, not the lab's: ${g.title} ${g.url}") }
                                })
                            }
                        })
                    })
                }
            })
            addJson("medications.json", buildJsonObject {
                put("summary", buildJsonArray {
                    repo.aiData(p).medications().forEach { m ->
                        add(buildJsonObject {
                            put("name", m.name); put("first_date", m.firstPurchase?.take(10)); put("last_date", m.lastPurchase?.take(10))
                            put("purchases", m.purchases); put("active", m.active); put("permanent", m.permanent); put("source", m.source ?: "Meuhedet")
                        })
                    }
                })
                put("prescriptions", buildJsonArray {
                    repo.prescriptions(p).forEach { r ->
                        add(buildJsonObject {
                            put("created", r.createdAt?.take(10)); put("from", r.startsAt?.take(10)); put("to", r.endsAt?.take(10))
                            put("doctor", r.doctor); put("specialty", r.specialty); put("medicines", JsonArray(r.medicines.map(::JsonPrimitive)))
                        })
                    }
                })
                put("from_uploaded_documents", buildJsonArray {
                    repo.uploadedMeds(p).forEach { (m, u) ->
                        add(buildJsonObject { put("name", m.name); put("dose", m.dose); put("start", m.start ?: u.date); put("end", m.end); put("source", u.source) })
                    }
                })
            })
            addJson("conditions.json", buildJsonArray {
                repo.conditions(p).forEach { c ->
                    add(buildJsonObject {
                        put("type", c.type); put("text", c.text); put("date", c.date)
                        put("source", when { c.type == Condition.NOTE -> "the person's own note"; c.source == null -> "added by the person"; else -> c.source })
                    })
                }
            })
            addJson("visits.json", buildJsonObject {
                fun visits(list: List<Visit>) = buildJsonArray {
                    list.forEach { v -> add(buildJsonObject { put("start", v.start); put("end", v.end); put("specialty", v.specialty); put("doctor", v.doctor); put("place", v.place) }) }
                }
                put("past", visits(repo.pastVisits(p))); put("upcoming", visits(repo.upcomingVisits(p)))
            })

            // Files: Meuhedet PDFs, then uploaded documents.
            val docs = buildJsonArray {
                repo.labVisits(p).forEach { v ->
                    val bytes = files.get(meuhedetFileId(p, "lab", v.stickerId)) ?: return@forEach
                    val name = unique("files/${v.date?.take(10) ?: "undated"}_lab-result_${safe(v.institute ?: v.stickerId)}.pdf")
                    add(name, bytes)
                    add(buildJsonObject { put("type", "lab result"); put("date", v.date?.take(10)); put("lab", v.institute); put("file", name) })
                }
                repo.documents(p).forEach { d ->
                    val bytes = d.guid?.let { files.get(meuhedetFileId(p, "doc", it + d.documentKey)) }
                    val name = bytes?.let { unique("files/${d.date?.take(10) ?: "undated"}_${safe(d.title)}.pdf") }
                    if (bytes != null) add(name!!, bytes)
                    add(buildJsonObject {
                        put("type", d.type ?: "document"); put("title", d.title); put("date", d.date?.take(10)); put("doctor", d.doctor)
                        put("specialty", d.specialty); put("file", name); put("source", "Meuhedet")
                    })
                }
                repo.uploads(p).forEach { u ->
                    val names = u.mimes.indices.mapNotNull { i ->
                        val bytes = files.get(u.pageId(i)) ?: return@mapNotNull null
                        val ext = if (u.mimes[i] == "application/pdf") "pdf" else "jpg"
                        unique("files/${u.date ?: "undated"}_${safe(u.title ?: u.source ?: "uploaded")}_${i + 1}.$ext").also { add(it, bytes) }
                    }
                    add(buildJsonObject {
                        put("type", u.type ?: "uploaded document"); put("title", u.title); put("date", u.date); put("source", u.source)
                        put("files", JsonArray(names.map(::JsonPrimitive)))
                    })
                }
                repo.examinations(p).forEach { e -> add(buildJsonObject { put("type", "test"); put("title", e.description); put("date", e.date?.take(10)); put("institute", e.institute) }) }
                repo.vaccines(p).forEach { v -> add(buildJsonObject { put("type", "vaccine"); put("title", v.name); put("date", v.date?.take(10)) }) }
            }
            addJson("documents.json", docs)
        }
        return out.toByteArray()
    }

    /** A one-page summary a doctor can read. */
    fun summary(repo: Repo, person: Person, today: LocalDate): String = buildString {
        val p = person.id
        appendLine("# ${person.name}: medical summary")
        appendLine("Exported $today from MyEHR (Meuhedet records + uploaded documents).")
        appendLine()
        val conds = repo.conditions(p)
        listOf(Condition.DIAGNOSIS to "Diagnoses", Condition.ALLERGY to "Allergies and sensitivities", Condition.NOTE to "Own notes").forEach { (t, title) ->
            val list = conds.filter { it.type == t }
            appendLine("## $title")
            if (list.isEmpty()) appendLine("- none recorded in this app")
            list.forEach { appendLine("- ${it.text}" + listOfNotNull(it.date, it.source).joinToString(", ").let { s -> if (s.isEmpty()) "" else " ($s)" }) }
            appendLine()
        }
        appendLine("## Current medications")
        val meds = repo.aiData(p).medications().filter { it.active }
        if (meds.isEmpty()) appendLine("- none marked active")
        meds.forEach { appendLine("- ${it.name}" + (it.firstPurchase?.let { d -> " (since ${d.take(10)})" } ?: "")) }
        appendLine()
        appendLine("## Latest lab results")
        appendLine("| Test | Value | Normal range | Date | Source |")
        appendLine("|---|---|---|---|---|")
        repo.labSeries(p).sortedBy { it.name }.forEach { s ->
            val l = s.latest ?: return@forEach
            val flag = if (l.outOfRange) " ⚠" else ""
            appendLine("| ${s.name} | ${l.resultText ?: l.value ?: ""} ${l.unit ?: ""}$flag | ${l.normLow ?: ""}–${l.normHigh ?: ""}${if (l.generalRange != null) " (general, not the lab's)" else ""} | ${l.date?.take(10) ?: ""} | ${l.source ?: "Meuhedet"} |")
        }
    }

    private fun safe(s: String) = s.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(60)
    private val prettyJson = kotlinx.serialization.json.Json { prettyPrint = true }
}
