package com.myehr.app.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaBase64PdfSource
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaImageBlockParam
import com.anthropic.models.beta.messages.BetaRequestDocumentBlock
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaToolChoiceTool
import com.anthropic.models.beta.messages.MessageCreateParams
import com.myehr.app.data.Condition
import com.myehr.app.data.Page
import com.myehr.app.data.UpLab
import com.myehr.app.data.UpMed
import com.myehr.app.data.newId
import com.myehr.app.data.upJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.Base64

/** What Claude found in a document, before the user approves it. */
@Serializable
data class Findings(
    val date: String? = null,
    val source: String? = null,
    val title: String? = null,
    val type: String? = null,
    val labs: List<UpLab> = emptyList(),
    val meds: List<UpMed> = emptyList(),
    val diagnoses: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
) {
    fun conditions(uploadId: String): List<Condition> =
        diagnoses.map { Condition(newId(), Condition.DIAGNOSIS, it, date, source, uploadId) } +
            allergies.map { Condition(newId(), Condition.ALLERGY, it, date, source, uploadId) }
}

private val PROMPT = """
This is a medical document (lab report, discharge letter, visit summary or prescription) from a private doctor or a hospital.
Read every page and call save_findings once with what it contains. Copy values exactly as printed; do not guess.
- Dates as yyyy-MM-dd. "date" is the document's date; a lab's "date" only if it differs.
- Lab names as printed (Hebrew or English). If a lab is the same test as one in this list, put its code in "code":
%s
- Medications the document says to take or that were given: name, dose, start/end dates if printed.
- Diagnoses and allergies/sensitivities as short phrases.
- "source": the hospital, clinic or doctor's practice name. "title": a short title. "type": lab, letter, summary, prescription or other.
- Never include the patient's name, ID number, address or phone anywhere.
""".trimIndent()

private val schema: Map<String, Any> = mapOf(
    "date" to mapOf("type" to "string"),
    "source" to mapOf("type" to "string"),
    "title" to mapOf("type" to "string"),
    "type" to mapOf("type" to "string", "enum" to listOf("lab", "letter", "summary", "prescription", "other")),
    "labs" to mapOf("type" to "array", "items" to mapOf("type" to "object", "required" to listOf("name"), "properties" to mapOf(
        "name" to mapOf("type" to "string"), "code" to mapOf("type" to "string"), "value" to mapOf("type" to "string"),
        "unit" to mapOf("type" to "string"), "low" to mapOf("type" to "number"), "high" to mapOf("type" to "number"),
        "date" to mapOf("type" to "string"),
    ))),
    "meds" to mapOf("type" to "array", "items" to mapOf("type" to "object", "required" to listOf("name"), "properties" to mapOf(
        "name" to mapOf("type" to "string"), "dose" to mapOf("type" to "string"),
        "start" to mapOf("type" to "string"), "end" to mapOf("type" to "string"),
    ))),
    "diagnoses" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
    "allergies" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
)

/** Sends a document to Claude (only after the user agreed) and returns the structured findings. */
class DocReader(apiKey: String, baseUrl: String? = null) {
    private val client = AnthropicOkHttpClient.builder().apiKey(apiKey).apply { if (baseUrl != null) baseUrl(baseUrl) }.build()

    suspend fun read(pages: List<Page>, knownTests: List<Pair<String, String>>): Findings = withContext(Dispatchers.IO) {
        val b64 = Base64.getEncoder()
        val blocks = pages.map { p ->
            if (p.mime == "application/pdf") BetaContentBlockParam.ofDocument(
                BetaRequestDocumentBlock.builder().source(BetaBase64PdfSource.builder().data(b64.encodeToString(p.bytes)).build()).build()
            ) else BetaContentBlockParam.ofImage(
                BetaImageBlockParam.builder().source(
                    BetaBase64ImageSource.builder().data(b64.encodeToString(p.bytes))
                        .mediaType(if (p.mime == "image/png") BetaBase64ImageSource.MediaType.IMAGE_PNG else BetaBase64ImageSource.MediaType.IMAGE_JPEG)
                        .build()
                ).build()
            )
        }
        val tests = knownTests.joinToString("\n") { (c, n) -> "  $c: $n" }.ifEmpty { "  (none)" }
        val tool = BetaTool.builder().name("save_findings").description("Save what the document contains.")
            .inputSchema(BetaTool.InputSchema.builder().properties(
                BetaTool.InputSchema.Properties.builder().apply { schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build()
            ).build()).build()
        val params = MessageCreateParams.builder()
            .model(MODEL).maxTokens(16000L)
            .addTool(tool)
            .toolChoice(BetaToolChoiceTool.builder().name("save_findings").build())
            .addUserMessageOfContentBlockParams(blocks + BetaContentBlockParam.ofText(PROMPT.format(tests)))
            .build()
        val response = client.beta().messages().create(params)
        val use = response.content().firstNotNullOfOrNull { it.toolUse().orElse(null) } ?: error("no findings")
        val text = com.anthropic.core.jsonMapper().writeValueAsString(use._input())
        upJson.decodeFromString(Findings.serializer(), text)
    }
}
