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
) {
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
