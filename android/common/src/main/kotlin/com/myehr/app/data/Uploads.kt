package com.myehr.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A lab value read from an uploaded document. [code] links it to a Meuhedet test when it is the same test. */
@Serializable
data class UpLab(
    val name: String,
    val code: String? = null,
    val value: String? = null,
    val unit: String? = null,
    val low: Double? = null,
    val high: Double? = null,
    val date: String? = null,
)

@Serializable
data class UpMed(val name: String, val dose: String? = null, val start: String? = null, val end: String? = null)

/** A diagnosis, allergy or the user's own note. Added by hand or from a document; always editable. */
@Serializable
data class Condition(
    val id: String,
    val type: String,
    val text: String,
    val date: String? = null,
    /** Where it came from: an uploaded document's source, or null when the user typed it. */
    val source: String? = null,
    val uploadId: String? = null,
) {
    companion object {
        const val DIAGNOSIS = "diagnosis"
        const val ALLERGY = "allergy"
        const val NOTE = "note"
        val labels = mapOf(DIAGNOSIS to "אבחנה", ALLERGY to "רגישות / אלרגיה", NOTE to "הערה שלי")
    }
}

/** One page of a document as picked by the user (already shrunk for sending). */
class Page(val bytes: ByteArray, val mime: String)

/** A document the user added. Its pages are kept encrypted in the file vault as "<id>_<n>". */
@Serializable
data class Upload(
    val id: String,
    val addedAt: Long,
    val mimes: List<String>,
    val date: String? = null,
    val source: String? = null,
    val title: String? = null,
    val type: String? = null,
    val labs: List<UpLab> = emptyList(),
    val meds: List<UpMed> = emptyList(),
) {
    fun pageId(i: Int) = "${id}_$i"
}

/** Encrypted file storage (Android: Keystore key; desktop: keychain key). */
interface FileVault {
    fun put(id: String, bytes: ByteArray)
    fun get(id: String): ByteArray?
    fun delete(id: String)
    fun has(id: String): Boolean = get(id) != null
}

object UpKind {
    const val UPLOAD = "upload"
    const val CONDITION = "condition"
}

val upJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }

fun Upload.toJson() = upJson.encodeToString(this)
fun Condition.toJson() = upJson.encodeToString(this)

/** Same test name, ignoring case, spaces and punctuation. */
fun sameTestName(a: String, b: String): Boolean {
    fun n(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    return n(a).isNotEmpty() && n(a) == n(b)
}

fun newId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
