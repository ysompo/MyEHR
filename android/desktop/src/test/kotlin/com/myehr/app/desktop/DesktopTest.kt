package com.myehr.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.myehr.app.data.Person
import com.myehr.app.sync.Kind
import com.myehr.app.ui.App
import com.myehr.app.ui.LocalPlatform
import com.myehr.app.ui.MyEhrTheme
import com.myehr.app.ui.PlatformFonts
import com.myehr.app.ui.TextSize
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Invented demo data only.
class DesktopTest {
    private fun store(file: File) = DesktopStore(file) { ByteArray(32) { it.toByte() } }

    @Test fun storeEncryptsAndRoundTrips() {
        val f = File.createTempFile("myehr", ".db").apply { deleteOnExit() }
        val s = store(f)
        s.putPerson(Person("000000001", "אבא"))
        s.put("000000001", Kind.LAB_LIST, "", """{"Items":[{"StickerId":"SECRET-MARKER"}]}""")
        assertEquals("אבא", s.people().single().name)
        assertTrue(s.get("000000001", Kind.LAB_LIST, "")!!.contains("SECRET-MARKER"))
        assertTrue(s.fetchedAt("000000001", Kind.LAB_LIST, "") != null)
        val raw = f.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("SECRET-MARKER"), "values must be encrypted on disk")
    }

    @Test fun rendersHome() {
        val f = File.createTempFile("myehr-demo", ".db").apply { deleteOnExit() }
        val s = store(f)
        val id = "000000001"
        s.putPerson(Person(id, "אבא", lastSync = 1762200000000)); s.putPerson(Person("000000002", "אמא")); s.putPerson(Person("000000003", "נועה", isChild = true))
        s.put(id, Kind.LAB_HISTORY, "1", """{"TestDesc":"המוגלובין","Items":[
            {"TestDate":"2021-09-15T08:00:00","Result":"13.5","TestValue":13.5,"MeasureUnit":"g/dL","NormFrom":"12","NormTo":"16"},
            {"TestDate":"2023-01-20T08:00:00","Result":"11.6","TestValue":11.6,"MeasureUnit":"g/dL","NormFrom":"12","NormTo":"16"},
            {"TestDate":"2025-08-11T08:00:00","Result":"13.4","TestValue":13.4,"MeasureUnit":"g/dL","NormFrom":"12","NormTo":"16"}]}""")
        s.put(id, Kind.LAB_HISTORY, "2", """{"TestDesc":"גלוקוז בצום","Items":[
            {"TestDate":"2022-02-03T08:00:00","Result":"98","TestValue":98,"MeasureUnit":"mg/dL","NormFrom":"70","NormTo":"100"},
            {"TestDate":"2025-08-11T08:00:00","Result":"107","TestValue":107,"MeasureUnit":"mg/dL","NormFrom":"70","NormTo":"100"}]}""")
        s.put(id, Kind.MEDS_PURCHASED, "", """{"Items":[{"Date":"2022-05-01T00:00:00","Description":"סטטין לדוגמה 20 מ\"ג"}]}""")
        s.put(id, Kind.MEDS_ACTIVE, "", """[{"description":"סטטין לדוגמה 20 מ\"ג","flags":{"permenant":true}}]""")

        PlatformFonts.family = DesktopPlatform.rubik
        val platform = DesktopPlatform { s }
        ImageComposeScene(width = 640, height = 1100, density = Density(1f)) {
            CompositionLocalProvider(LocalPlatform provides platform) {
                MyEhrTheme(TextSize.NORMAL, dark = false) { App(TextSize.NORMAL, {}) }
            }
        }.use { scene ->
            repeat(5) { scene.render(it * 100_000_000L) ; Thread.sleep(300) }
            val png = scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/demo").mkdirs(); File("build/demo/desktop_home.png").writeBytes(png)
        }
    }
}
