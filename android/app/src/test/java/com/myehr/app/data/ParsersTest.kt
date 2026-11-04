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
