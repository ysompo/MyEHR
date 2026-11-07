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

    @Test fun vaultEncryptsFiles() {
        val dir = kotlin.io.path.createTempDirectory("vault").toFile()
        val v = DesktopVault(dir) { ByteArray(32) { 7 } }
        v.put("a_0", "SECRET-PAGE".toByteArray())
        assertEquals("SECRET-PAGE", String(v.get("a_0")!!))
        assertFalse(dir.listFiles()!!.single().readBytes().toString(Charsets.ISO_8859_1).contains("SECRET-PAGE"))
        v.delete("a_0"); assertEquals(null, v.get("a_0"))
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

        // A document from a private lab: one new value for hemoglobin, one on a day Meuhedet already has (dropped).
        val repo = com.myehr.app.data.Repo(s)
        repo.saveUpload(id, com.myehr.app.data.Upload("u1", 1L, listOf("image/jpeg"), "2024-03-05", "מעבדה פרטית לדוגמה", "בדיקת דם", "lab",
            labs = listOf(com.myehr.app.data.UpLab("Hemoglobin", code = "1", value = "12.9", unit = "g/dL", low = 12.0, high = 16.0),
                com.myehr.app.data.UpLab("המוגלובין", value = "99", date = "2025-08-11"),
                com.myehr.app.data.UpLab("Vitamin D", value = "18", unit = "ng/mL", low = 30.0, high = 100.0))))
        repo.saveCondition(id, com.myehr.app.data.Condition("c1", com.myehr.app.data.Condition.ALLERGY, "פניצילין", "2019-01-01"))
        val hb = repo.series(id, "1")!!
        assertEquals(4, hb.points.size, "uploaded value added, same-day duplicate dropped")
        assertEquals("מעבדה פרטית לדוגמה", hb.points.first { it.date!!.startsWith("2024-03-05") }.source)
        assertTrue(repo.labSeries(id).any { it.name == "Vitamin D" })
        assertEquals(1, repo.dashboard(s.people(), s.people().first { it.id == id }).conditions.size)

        PlatformFonts.family = DesktopPlatform.rubik
        val platform = DesktopPlatform(makeStore = { s }, makeVault = { DesktopVault(File(f.parentFile, "vault-test")) { ByteArray(32) } })
        renderTo("desktop_adddoc.png", platform) { com.myehr.app.ui.AddDocScreen(s.people(), s.people().first { it.id == id }) {} }
        renderTo("desktop_docs.png", platform) { com.myehr.app.ui.DocsScreen(emptyList(), emptyList(), emptyList(), repo.uploads(id)) }
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

    private fun renderTo(name: String, platform: DesktopPlatform, content: @androidx.compose.runtime.Composable () -> Unit) {
        ImageComposeScene(width = 640, height = 700, density = Density(1f)) {
            CompositionLocalProvider(LocalPlatform provides platform) {
                MyEhrTheme(TextSize.NORMAL, dark = false) { androidx.compose.material3.Surface { content() } }
            }
        }.use { scene ->
            repeat(3) { scene.render(it * 100_000_000L); Thread.sleep(200) }
            File("build/demo").mkdirs(); File("build/demo/$name").writeBytes(scene.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        }
    }
}
