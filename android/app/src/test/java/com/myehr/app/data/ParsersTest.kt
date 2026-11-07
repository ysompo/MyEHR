package com.myehr.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// All values below are invented test data.
class ParsersTest {
    @Test fun person() {
        val p = Parsers.person("""{"Id":"000000001","FullName":"Test Person","IsChild":true}""")!!
        assertEquals("000000001", p.id); assertEquals("Test Person", p.name); assertTrue(p.isChild)
        assertNull(Parsers.person("not json"))
        assertEquals("A B", Parsers.person("""{"Guid":"g-1","FirstName":"A","LastName":"B"}""")!!.name)
    }

    @Test fun labVisitsSortedNewestFirst() {
        val v = Parsers.labVisits("""{"Items":[
          {"StickerId":"11","LabCode":"2","TestDate":"2020-01-01T00:00:00","DoctorFirstName":"Dr","DoctorLastName":"One"},
          {"StickerId":"12","LabCode":"2","TestDate":"2023-06-01T00:00:00"},
          {"LabCode":"2"}],"TotalCount":3}""")
        assertEquals(listOf("12", "11"), v.map { it.stickerId })
        assertEquals("Dr One", v[1].doctor)
    }

    @Test fun labDetailRecords() {
        val d = Parsers.labDetail("""{"detailsSection":{"summary":{"labName":"Lab","testDateTime":"2023-06-01T08:00:00+03:00"}},
          "testSections":[{"code":1,"tests":[{"code":5,"records":[
            {"title":"TEST A","panicLevel":0,"scale":{"minRange":1,"maxRange":5,"value":7,"measurementUnit":"u"},"single":{"value":"7","charSign":"","measurementUnit":"u"}},
            {"title":"TEST B","single":{"value":"neg"},"note":{"text":"note"}}]}]}]}""")!!
        assertEquals(2, d.records.size)
        assertTrue(d.records[0].outOfRange)
        assertEquals("note", d.records[1].note)
        assertFalse(d.records[1].outOfRange)
    }

    @Test fun zeroRangeMeansNone() {
        val s = Parsers.labSeries("8", """{"TestDesc":"HDL","Items":[
            {"TestDate":"2026-01-01T00:00:00","Result":"44","TestValue":44,"NormFrom":"0","NormTo":"0"},
            {"TestDate":"2026-02-01T00:00:00","Result":"45","TestValue":45,"NormFrom":"0","NormTo":"0","TestNormLow":"40","TestNormHigh":"0"}]}""")!!
        assertNull(s.points[0].normLow); assertNull(s.points[0].normHigh)
        assertFalse(s.points[0].outOfRange)
        assertEquals(40.0, s.points[1].normLow!!, 0.0); assertNull(s.points[1].normHigh)
        assertEquals("\u2066≥ 40\u2069", range(40.0, null))
    }

    @Test fun generalRangeWhenLabGivesNone() {
        val hdl = Parsers.labSeries("8", """{"TestDesc":"HDL-CHOLEST.","Items":[
            {"TestDate":"2026-10-28T00:00:00","Result":"44","TestValue":44,"MeasureUnit":"MG/DL","NormFrom":"0","NormTo":"0"}]}""")!!
        val man = demographics(Parsers.parse("""{"Gender":"M","BirthDate":"1980-01-01T00:00:00"}"""))
        val woman = demographics(Parsers.parse("""{"Gender":"F","BirthDate":"1980-01-01T00:00:00"}"""))
        val child = demographics(Parsers.parse("""{"Gender":"F","BirthDate":"2016-01-01T00:00:00"}"""))
        GeneralRanges.apply(hdl, man).points.single().let { assertEquals(40.0, it.normLow!!, 0.0); assertFalse(it.outOfRange); assertEquals("טווח כללי", it.rangeLabel) }
        assertTrue(GeneralRanges.apply(hdl, woman).points.single().outOfRange)
        assertEquals(45.0, GeneralRanges.apply(hdl, child).points.single().normLow!!, 0.0)
        // Unknown sex: no sex-specific band, so no range rather than a guess.
        assertNull(GeneralRanges.apply(hdl, Demographics(null, null, 45.0)).points.single().normLow)
        // Other unit: no range.
        assertNull(GeneralRanges.find("HDL", "mmol/L", man, "2026-10-28"))
        assertNull(GeneralRanges.find("LDL/HDL RATIO", "mg/dL", man, "2026-10-28"))
        assertEquals(5.6, GeneralRanges.find("HEMOGLOBIN A1C", "%", man, "2026-10-28")!!.high!!, 0.0)
        assertEquals(13.0, GeneralRanges.find("HEMOGLOBIN", "g/dL", man, "2026-10-28")!!.low!!, 0.0)
        assertNull(GeneralRanges.find("GLUCOSE - URINE", "mg/dL", man, "2026-10-28"))
        GeneralRanges.find("TSH", "µIU/mL", man, "2026-10-28")!!.let { assertEquals(0.45, it.low!!, 0.0); assertEquals(4.12, it.high!!, 0.0) }
        assertEquals(60.0, GeneralRanges.find("eGFR", "mL/min/1.73m2", man, "2026-10-28")!!.low!!, 0.0)
        assertNull(GeneralRanges.find("eGFR", "mL/min/1.73m2", child, "2026-10-28"))
        assertEquals(180.0, GeneralRanges.find("VITAMIN B12", "pg/mL", child, "2026-10-28")!!.low!!, 0.0)
        assertEquals(10.0, GeneralRanges.find("WBC", "10^3/µL", man, "2026-10-28")!!.high!!, 0.0)
        assertNull(GeneralRanges.find("WBC", "/µL", man, "2026-10-28"))
        assertEquals(0.88, GeneralRanges.find("CREATININE", "mg/dL", woman, "2026-10-28")!!.high!!, 0.0)
        assertNull(GeneralRanges.find("CREATININE", "mg/dL", man, "2026-10-28"))
        assertNull(GeneralRanges.find("CREATININE - URINE", "mg/dL", woman, "2026-10-28"))
        assertTrue(GeneralRanges.find("VITAMIN B12", "pg/mL", man, "2026-10-28")!!.source.userDefined)
    }

    @Test fun labSeriesCautions() {
        val s = Parsers.labSeries("7", """{"TestDesc":"TEST B","Items":[
            {"TestDate":"2020-01-01T00:00:00","Result":"NEGATIVE","TestValue":0,"MeasureUnit":"mg/dL","NormFrom":"1","NormTo":"5"},
            {"TestDate":"2021-01-01T00:00:00","Result":"3","TestValue":3,"MeasureUnit":"mg/dL","NormFrom":"1","NormTo":"5"},
            {"TestDate":"2022-01-01T00:00:00","Result":"0.2","TestValue":0.2,"MeasureUnit":"mmol/L","NormFrom":"0.1","NormTo":"0.3"}]}""")!!
        assertNull(s.points.first().value) // text answer, not a 0
        assertEquals(3, s.cautions().size) // text, unit change, range change
    }

    @Test fun labSeriesWithNormRange() {
        val s = Parsers.labSeries("100", """{"ShowTestsGraph":true,"TestDesc":"TEST A","Items":[
          {"TestDate":"2023-01-01T00:00:00","Result":"4.5","MeasureUnit":"u","NormFrom":"1.0","NormTo":"5.0","TestValue":4.5},
          {"TestDate":"2021-01-01T00:00:00","Result":"6,2","MeasureUnit":"u","NormFrom":"1.0","NormTo":"5.0"}]}""")!!
        assertEquals("TEST A", s.name)
        assertEquals("2021-01-01T00:00:00", s.points.first().date)
        assertEquals(6.2, s.points.first().value!!, 0.001)
        assertTrue(s.points.first().outOfRange)
        assertFalse(s.latest!!.outOfRange)
        assertNull(Parsers.labSeries("1", """{"Items":[]}"""))
    }

    @Test fun catalog() {
        assertEquals(listOf("1" to "A", "2" to "2"), Parsers.labTestCatalog("""{"Tests":[{"Key":"1","Value":"A"},{"Key":2}]}"""))
    }

    @Test fun meds() {
        val a = Parsers.activeMeds("""[{"id":"1","description":"MED A","flags":{"permenant":true,"nearestPrescriptionEnd":"2024-01-01T00:00:00"},"doctors":["Dr X"]}]""")
        assertEquals("MED A", a[0].name); assertTrue(a[0].permanent); assertEquals("Dr X", a[0].doctor)
        val p = Parsers.prescriptions("""{"prescriptions":[{"doctorName":"Dr","createdAt":"2024-02-01T00:00:00+02:00","medicines":[{"description":"MED A"}]}]}""")
        assertEquals(listOf("MED A"), p[0].medicines)
        assertEquals("Pharm", Parsers.purchases("""{"Items":[{"Date":"2024-01-01T00:00:00","Description":"MED A","Pharmacy":"Pharm"}]}""")[0].pharmacy)
        assertTrue(Parsers.activeMeds("{}").isEmpty())
    }

    @Test fun documentsVaccinesExams() {
        val d = Parsers.documents("""{"Items":[{"FormDate":"2024-01-01T00:00:00","DocumentDescription":"Doc","DocumentKey":"k","DocumentGuid":"g","FormSourceSystem":1,"Available":true}]}""")
        assertEquals("1", d[0].formSourceSystem)
        assertEquals("Vac", Parsers.vaccines("""{"Items":[{"Date":"2020-01-01T00:00:00","Name":"Vac"}]}""")[0].name)
        assertTrue(Parsers.examinations("""{"Items":[{"TestDate":"2020-01-01T00:00:00","Description":"ECG","IsEKGResult":true}]}""")[0].isEcg)
    }

    @Test fun dateFormat() {
        assertEquals("01/05/2024", fmtDate("2024-05-01T08:30:00"))
        assertEquals("", fmtDate(null))
    }

    @Test fun visits() {
        val up = Parsers.upcomingVisits("""{"Items":[
          {"AppointmentDateAndTime":"2030-02-01T09:30:00+02:00","ToTime":"2030-02-01T09:45:00","SpecialityName":"Spec","ProviderName":"Dr X","ClinicLocation":"Clinic","StreetName":"Street","StreetNum":"1","City":"Town"},
          {"FromTime":"2030-01-01T08:00:00","SpecialityName":"Early"}]}""")
        assertEquals(listOf("Early", "Spec"), up.map { it.specialty })
        assertEquals("Clinic, Street 1, Town", up[1].place)
        val past = Parsers.pastVisits("""{"Appointments":[{"VisitDate":"2020-01-01T00:00:00","ProviderFirstName":"A","ProviderLastName":"B","SpecialityName":"S","LocationName":"L"}]}""")
        assertEquals("A B", past[0].doctor); assertFalse(past[0].upcoming)
    }
}
