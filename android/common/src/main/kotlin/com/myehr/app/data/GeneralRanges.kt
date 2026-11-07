package com.myehr.app.data

import java.time.LocalDate
import java.time.Period

/** A published source for a general range, shown in the app with its PubMed link. */
data class RangeSource(val title: String, val pmid: String) {
    val url get() = if (pmid.isEmpty()) "" else "https://pubmed.ncbi.nlm.nih.gov/$pmid/"
    /** Set by the user, not taken from a publication. */
    val userDefined get() = pmid.isEmpty()
}

object Sources {
    val ATP3 = RangeSource("NCEP ATP III, JAMA 2001", "11368702")
    val NHLBI_KIDS = RangeSource("NHLBI Expert Panel (children), Pediatrics 2011", "22084329")
    val ADA = RangeSource("ADA Standards of Care 2025, Diabetes Care", "39651986")
    val ENDO_VITD = RangeSource("Endocrine Society vitamin D guideline, JCEM 2011", "21646368")
    val WHO_HB = RangeSource("WHO hemoglobin cutoffs 2024 (summary), Indian Pediatr 2024", "38910369")
    val ACG_ALT = RangeSource("ACG liver chemistries guideline, Am J Gastroenterol 2017", "27995906")
    val SAFETY_ALT = RangeSource("SAFETY study (children), Gastroenterology 2010", "20064512")
    val NHANES_TSH = RangeSource("NHANES III thyroid reference, JCEM 2002", "11836274")
    val USER = RangeSource("טווח שהגדרתם", "")
    val KDIGO = RangeSource("KDIGO 2024 chronic kidney disease guideline, Kidney Int", "38490803")
}

enum class Sex { M, F }

/** One band: applies to [sex] (null = both) between ages [fromAge, toAge) in years. */
class Band(val sex: Sex?, val fromAge: Double, val toAge: Double, val low: Double?, val high: Double?, val source: RangeSource)

private class Test(val units: Set<String>, val matches: (String) -> Boolean, val bands: List<Band>, val unitOk: ((String) -> Boolean)? = null) {
    fun unitMatches(u: String) = unitOk?.invoke(u) ?: (u in units)
}

/** Words of a test name, lower case, Latin and Hebrew letters/digits only. */
private fun tokens(name: String) = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
private fun flat(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
private fun isUrine(name: String) = flat(name).let { "urine" in it || "שתן" in it }

private val TESTS = listOf(
    // Lipids (mg/dL). Adults: ATP III. Children (<18): NHLBI 2011 "acceptable".
    Test(setOf("mg/dl"), { n -> flat(n).let { "hdl" in it && "non" !in it && "ratio" !in it } }, listOf(
        Band(null, 0.0, 18.0, 45.0, null, Sources.NHLBI_KIDS),
        Band(Sex.M, 18.0, 200.0, 40.0, null, Sources.ATP3),
        Band(Sex.F, 18.0, 200.0, 50.0, null, Sources.ATP3),
    )),
    Test(setOf("mg/dl"), { n -> flat(n).let { "ldl" in it && "vldl" !in it && "ratio" !in it } }, listOf(
        Band(null, 0.0, 18.0, null, 110.0, Sources.NHLBI_KIDS),
        Band(null, 18.0, 200.0, null, 100.0, Sources.ATP3),
    )),
    Test(setOf("mg/dl"), { n -> flat(n).let { "trigl" in it || "טריגליצ" in it } && !isUrine(n) }, listOf(
        Band(null, 0.0, 10.0, null, 75.0, Sources.NHLBI_KIDS),
        Band(null, 10.0, 18.0, null, 90.0, Sources.NHLBI_KIDS),
        Band(null, 18.0, 200.0, null, 150.0, Sources.ATP3),
    )),
    Test(setOf("mg/dl"), { n -> flat(n).let { ("cholest" in it || "כולסטרול" in it) && listOf("hdl", "ldl", "non", "ratio").none { x -> x in it } } }, listOf(
        Band(null, 0.0, 18.0, null, 170.0, Sources.NHLBI_KIDS),
        Band(null, 18.0, 200.0, null, 200.0, Sources.ATP3),
    )),
    // Glucose (fasting, blood) and HbA1c: ADA, all ages.
    Test(setOf("mg/dl"), { n -> flat(n).let { ("gluc" in it || "גלוקוז" in it) && "tolerance" !in it } && !isUrine(n) }, listOf(
        Band(null, 0.0, 200.0, 70.0, 99.0, Sources.ADA),
    )),
    Test(setOf("%"), { n -> flat(n).let { "a1c" in it || "מסוכרר" in it || "glycated" in it || "glycosylated" in it } }, listOf(
        Band(null, 0.0, 200.0, null, 5.6, Sources.ADA),
    )),
    // Vitamin D (25-OH): deficiency below 20 ng/mL (Endocrine Society 2011).
    Test(setOf("ng/ml"), { n -> flat(n).let { "vitamind" in it || "25oh" in it || "ויטמיןd" in it } }, listOf(
        Band(null, 0.0, 200.0, 20.0, null, Sources.ENDO_VITD),
    )),
    // Hemoglobin (g/dL): WHO 2024 anemia cutoffs (lower limit only; not pregnancy).
    Test(setOf("g/dl"), { n -> flat(n).let { ("hemoglobin" in it || "haemoglobin" in it || "המוגלובין" in it || tokens(n).any { t -> t == "hgb" || t == "hb" }) &&
            listOf("a1c", "מסוכרר", "glyc", "mch", "ממוצע").none { x -> x in it } } }, listOf(
        Band(null, 0.5, 2.0, 10.5, null, Sources.WHO_HB),
        Band(null, 2.0, 5.0, 11.0, null, Sources.WHO_HB),
        Band(null, 5.0, 12.0, 11.5, null, Sources.WHO_HB),
        Band(null, 12.0, 15.0, 12.0, null, Sources.WHO_HB),
        Band(Sex.F, 15.0, 200.0, 12.0, null, Sources.WHO_HB),
        Band(Sex.M, 15.0, 200.0, 13.0, null, Sources.WHO_HB),
    )),
    // ALT (U/L): upper limits of healthy values. Adults: ACG 2017 (upper end of 29–33 men, 19–25 women). Children: SAFETY.
    Test(setOf("u/l", "iu/l"), { n -> tokens(n).any { it == "alt" || it == "gpt" || it == "sgpt" } }, listOf(
        Band(Sex.M, 0.0, 18.0, null, 26.0, Sources.SAFETY_ALT),
        Band(Sex.F, 0.0, 18.0, null, 22.0, Sources.SAFETY_ALT),
        Band(Sex.M, 18.0, 200.0, null, 33.0, Sources.ACG_ALT),
        Band(Sex.F, 18.0, 200.0, null, 25.0, Sources.ACG_ALT),
    )),
    // TSH (mIU/L = µIU/mL), age 12+: 2.5th–97.5th percentile of the NHANES III reference population.
    Test(setOf("miu/l", "uiu/ml", "mu/l", "miu/ml"), { n -> tokens(n).any { it == "tsh" } || "תירוטרופין" in flat(n) }, listOf(
        Band(null, 12.0, 200.0, 0.45, 4.12, Sources.NHANES_TSH),
    )),
    // Set by the user (no publication): B12, white cells, creatinine (adults).
    Test(setOf("pg/ml"), { n -> flat(n).let { "b12" in it || "cobalamin" in it } }, listOf(
        Band(null, 0.0, 200.0, 180.0, 650.0, Sources.USER),
    )),
    Test(emptySet(), { n -> tokens(n).any { it == "wbc" } || flat(n).let { "leukocyt" in it || "leucocyt" in it || "whitebloodcell" in it || "לויקוציט" in it || "כדוריותלבנות" in it } }, listOf(
        Band(null, 0.0, 200.0, 3.0, 10.0, Sources.USER),
    ), unitOk = { u -> u.contains("10^3/ul") || u.contains("10*3/ul") || u.contains("10e3/ul") || u.contains("10^9/l") || u.contains("10*9/l") || u.contains("10e9/l") || u == "k/ul" || u == "thou/ul" || u == "x1000/ul" }),
    Test(setOf("mg/dl"), { n -> flat(n).let { ("creatinin" in it || "קריאטינין" in it) && listOf("clearance", "ratio", "kinase", "cpk").none { x -> x in it } } && !isUrine(n) }, listOf(
        Band(null, 18.0, 200.0, 0.40, 0.88, Sources.USER),
    )),
    // eGFR (mL/min/1.73m²), adults: below 60 is the KDIGO threshold for chronic kidney disease (G3a–G5).
    Test(emptySet(), { n -> flat(n).let { "egfr" in it || ("gfr" in it && "מחושב" in it) } }, listOf(
        Band(null, 18.0, 200.0, 60.0, null, Sources.KDIGO),
    ), unitOk = { it.startsWith("ml/min") }),
)

/** The person's sex and birth date, as far as the member record says. */
data class Demographics(val sex: Sex?, val birth: LocalDate?, val ageNow: Double?) {
    /** Age in years on [date] (yyyy-MM-dd…). */
    fun ageOn(date: String?): Double? {
        val d = runCatching { LocalDate.parse(date!!.take(10)) }.getOrNull() ?: return null
        if (birth != null) return Period.between(birth, d).let { it.years + it.months / 12.0 }
        val now = ageNow ?: return null
        return now - Period.between(d, LocalDate.now()).let { it.years + it.months / 12.0 }
    }
}

object GeneralRanges {
    /** A general range for this test, person and date, or null when none applies. */
    fun find(testName: String, unit: String?, who: Demographics, date: String?): Band? {
        val u = unit?.lowercase()?.replace(" ", "")?.replace('µ', 'u')?.replace('μ', 'u') ?: return null
        val age = who.ageOn(date) ?: return null
        val test = TESTS.firstOrNull { it.unitMatches(u) && it.matches(testName) } ?: return null
        return test.bands.firstOrNull { b -> age >= b.fromAge && age < b.toAge && (b.sex == null || b.sex == who.sex) }
    }

    /** Fills points that have no range at all with a general one (marked with its source). */
    fun apply(s: LabSeries, who: Demographics): LabSeries {
        if (s.points.all { it.normLow != null || it.normHigh != null }) return s
        return s.copy(points = s.points.map { p ->
            if (p.normLow != null || p.normHigh != null) return@map p
            val b = find(s.name, p.unit ?: s.unit, who, p.date) ?: return@map p
            p.copy(normLow = b.low, normHigh = b.high, generalRange = b.source)
        })
    }
}

/** Reads sex and birth date from the member record ("Gender": M/F/1/2/זכר/נקבה; "BirthDate"; "Age"). */
fun demographics(member: kotlinx.serialization.json.JsonElement?): Demographics {
    val o = member as? kotlinx.serialization.json.JsonObject ?: return Demographics(null, null, null)
    fun s(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()
    val sex = when (s("Gender")?.lowercase()) {
        "m", "male", "1", "זכר", "ז" -> Sex.M
        "f", "female", "2", "נקבה", "נ" -> Sex.F
        else -> null
    }
    val birth = runCatching { LocalDate.parse(s("BirthDate")!!.take(10)) }.getOrNull()
    return Demographics(sex, birth, s("Age")?.toDoubleOrNull())
}
