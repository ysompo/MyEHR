package com.myehr.app.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTool
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

const val MODEL = "claude-opus-5-5"

private val SYSTEM = """
You answer questions about one person's own medical records, for that person, inside a private family app.
Use the tools to look up only the data you need. Dates are real. A medication's first purchase date is its start date.

Rules:
- Answer in the same language as the question (Hebrew or English). Plain text, no markdown, short.
- Give the numbers and dates you used (e.g. "last hemoglobin: 13.4 g/dL on 11/08/2025").
- For trends, say the direction and the change over time, and mention out-of-range results.
- For medication effects, compare results before and after the start date, and say clearly that a change over time
  does not prove the medication caused it.
- Do not diagnose or recommend treatment. When a result is out of range or a change looks important, suggest asking their doctor.
- If the data needed is not available, say so plainly. Missing data is not proof something never happened:
  the records hold only what Meuhedet's site shows and what the person uploaded.
- Say where each fact comes from and its date: "Meuhedet" or "an uploaded document" (e.g. "LDL 120, 03/2024, uploaded document").
- The person's own notes are their word, not a record. If a note and a record disagree, show both with their dates.
""".trimIndent()

/** One question → answer. Tool calls run on the phone; [beforeSend] is the consent + guard hook. */
class AiAgent(
    apiKey: String,
    private val tools: AiTools,
    private val guard: Guard,
    /** Called before a tool result is sent. Return false to refuse sending. */
    private val beforeSend: suspend (ToolOutput) -> Boolean,
    baseUrl: String? = null, // tests only
) {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey)
        .apply { if (baseUrl != null) baseUrl(baseUrl) }.build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Everything sent in this question, for the on-phone log. */
    val sent = mutableListOf<String>()

    private fun betaTool(spec: ToolSpec): BetaTool = BetaTool.builder()
        .name(spec.name)
        .description(spec.description)
        .inputSchema(
            BetaTool.InputSchema.builder()
                .properties(
                    BetaTool.InputSchema.Properties.builder().apply {
                        spec.properties.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
                    }.build()
                )
                .required(spec.required)
                .build()
        )
        .build()

    suspend fun ask(question: String): String = withContext(Dispatchers.IO) {
        val q = guard.scrub(question)
        sent += "שאלה: $q"
        val builder = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM)
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
            // If Claude Opus 5.5 declines, the server retries on a fallback model instead of failing.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(q)
        tools.specs.forEach { builder.addTool(betaTool(it)) }

        repeat(12) {
            val response = client.beta().messages().create(builder.build())
            val reason = response.stopReason().orElse(null)
            if (reason == BetaStopReason.REFUSAL) return@withContext "לא ניתן לענות על השאלה הזו."
            val uses = response.content().mapNotNull { it.toolUse().orElse(null) }
            if (reason != BetaStopReason.TOOL_USE || uses.isEmpty()) {
                return@withContext response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n").trim()
                    .ifEmpty { "לא התקבלה תשובה." }
            }
            builder.addMessage(response)
            val results = uses.map { use ->
                val input = runCatching { json.parseToJsonElement(com.anthropic.core.jsonMapper().writeValueAsString(use._input())).jsonObject }
                    .getOrElse { JsonObject(emptyMap()) }
                val out = tools.run(use.name(), input)
                val allowed = beforeSend(out)
                val content = if (allowed) guard.scrub(out.json) else """{"error":"the user declined to share this data"}"""
                sent += "${out.summary}: $content"
                BetaContentBlockParam.ofToolResult(
                    BetaToolResultBlockParam.builder().toolUseId(use.id()).content(content).isError(out.isError || !allowed).build()
                )
            }
            builder.addUserMessageOfContentBlockParams(results)
        }
        "השאלה דרשה יותר מדי שלבים. נסו לנסח אותה בפשטות."
    }
}
