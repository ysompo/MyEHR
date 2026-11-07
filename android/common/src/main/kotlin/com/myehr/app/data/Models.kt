package com.myehr.app.data

/** App-level models. Every field is optional because the site's answers vary. */

data class Person(
    val id: String,
    val name: String,
    val isChild: Boolean = false,
    val lastSync: Long? = null,
)

data class LabVisit(
    val stickerId: String,
    val labCode: String,
    val date: String?,          // ISO local date-time as sent by the site
    val doctor: String?,
    val institute: String?,
)

data class LabRecord(
    val title: String,
    val value: String?,
    val unit: String?,
    val min: Double?,
    val max: Double?,
    val panic: Int?,
    val note: String?,
) {
    val numeric: Double? get() = value?.replace(",", ".")?.trim()?.toDoubleOrNull()
    val outOfRange: Boolean
        get() {
            val v = numeric ?: return false
            return (min != null && v < min) || (max != null && v > max)
        }
}

data class LabDetail(
    val labName: String?,
    val date: String?,
    val records: List<LabRecord>,
)

data class LabPoint(
    val date: String?,
    val value: Double?,
    val resultText: String?,
    val unit: String?,
    val normLow: Double?,
    val normHigh: Double?,
    val stickerId: String?,
    /** null = from Meuhedet; otherwise where an uploaded document came from (e.g. "Hospital X"). */
    val source: String? = null,
    /** Set when Meuhedet gave no range and a published general range is used instead. */
    val generalRange: RangeSource? = null,
) {
    /** "טווח תקין" (the lab's) or "טווח כללי" (published, not the lab's). */
    val rangeLabel: String get() = when { generalRange?.userDefined == true -> "טווח שהגדרתם"; generalRange != null -> "טווח כללי"; else -> "טווח תקין" }
    val outOfRange: Boolean
        get() = value != null && ((normLow != null && value < normLow) || (normHigh != null && value > normHigh))
}

data class LabSeries(
    val code: String,
    val name: String,
    val points: List<LabPoint>,
) {
    val unit: String? get() = points.firstNotNullOfOrNull { it.unit }
    val latest: LabPoint? get() = points.maxByOrNull { it.date ?: "" }

    /** Things that make the graph misleading, in plain Hebrew; empty when there are none. */
    fun cautions(): List<String> = buildList {
        val text = points.count { it.value == null && !it.resultText.isNullOrBlank() }
        if (text > 0) add("$text תוצאות הן טקסט ולא מספר, ולכן אינן בגרף.")
        val units = points.mapNotNull { it.unit?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        if (units.size > 1) add("יחידת המדידה השתנתה (${units.joinToString(" / ")}). השוואה בין התקופות עלולה להטעות.")
        val ranges = points.filter { it.normLow != null || it.normHigh != null }.map { it.normLow to it.normHigh }.distinct()
        if (ranges.size > 1) add("הטווח התקין השתנה לאורך השנים. כל תוצאה נבדקת מול הטווח שלה; הרצועה מציגה את האחרון.")
        points.mapNotNull { it.generalRange }.distinct().forEach {
            add(if (it.userDefined) "מאוחדת לא שלחה טווח. מוצג טווח שהגדרתם, לא של המעבדה." else "מאוחדת לא שלחה טווח. מוצג טווח כללי שפורסם (${it.title}), לא של המעבדה.")
        }
        if (points.any { it.source != null }) add("חלק מהתוצאות ממסמכים שהעליתם ממעבדות אחרות. שיטות מדידה שונות יכולות לתת ערכים שונים.")
    }
}

data class Medication(
    val name: String,
    val doctor: String?,
    val start: String?,
    val end: String?,
    val permanent: Boolean,
)

data class Purchase(
    val date: String?,
    val name: String,
    val doctor: String?,
    val pharmacy: String?,
)

data class Prescription(
    val number: String?,
    val doctor: String?,
    val specialty: String?,
    val createdAt: String?,
    val startsAt: String?,
    val endsAt: String?,
    val medicines: List<String>,
)

data class MedDocument(
    val date: String?,
    val title: String,
    val type: String?,
    val doctor: String?,
    val specialty: String?,
    val documentKey: String?,
    val guid: String?,
    val formCode: String?,
    val formSourceSystem: String?,
    val available: Boolean,
)

data class Vaccine(val date: String?, val name: String)

data class Examination(val date: String?, val description: String, val institute: String?, val isEcg: Boolean)

/** An appointment (upcoming) or a past visit. Times are local ISO strings from the site. */
data class Visit(
    val start: String?,
    val end: String?,
    val specialty: String?,
    val doctor: String?,
    val place: String?,
    val upcoming: Boolean,
)
