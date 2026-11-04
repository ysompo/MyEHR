package com.myehr.app.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.myehr.app.data.LabDetail
import com.myehr.app.data.LabPoint
import com.myehr.app.data.LabRecord
import com.myehr.app.data.LabSeries
import com.myehr.app.data.LabVisit
import com.myehr.app.data.MedDocument
import com.myehr.app.data.Medication
import com.myehr.app.data.Person
import com.myehr.app.data.Prescription
import com.myehr.app.data.Purchase
import com.myehr.app.data.Vaccine
import com.myehr.app.data.Examination
import com.myehr.app.ui.AppTopBar
import com.myehr.app.ui.DocsScreen
import com.myehr.app.ui.HomeTabs
import com.myehr.app.ui.LabDetailScreen
import com.myehr.app.ui.LabSeriesList
import com.myehr.app.ui.LabSeriesScreen
import com.myehr.app.ui.LabVisitList
import com.myehr.app.ui.LockScreen
import com.myehr.app.ui.MedsScreen
import com.myehr.app.ui.PersonChips
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with INVENTED demo data (no real people or results).
 * Run: ./gradlew testDebugUnitTest -Pdemo  → images in app/build/demo/
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DemoScreens {
    private val people = listOf(Person("1", "אבא"), Person("2", "אמא"), Person("3", "ילד", isChild = true))

    private fun series(code: String, name: String, unit: String, low: Double, high: Double, vararg v: Pair<String, Double>) =
        LabSeries(code, name, v.map { (d, x) -> LabPoint("${d}T08:00:00", x, x.toString(), unit, low, high, null) })

    private val allSeries = listOf(
        series("1", "המוגלובין", "g/dL", 12.0, 16.0, "2019-03-01" to 13.1, "2020-05-10" to 12.8, "2021-09-15" to 13.5, "2023-01-20" to 11.6, "2024-06-02" to 12.9, "2025-08-11" to 13.4),
        series("2", "גלוקוז בצום", "mg/dL", 70.0, 100.0, "2020-05-10" to 92.0, "2022-02-03" to 98.0, "2023-01-20" to 104.0, "2025-08-11" to 95.0),
        series("3", "כולסטרול LDL", "mg/dL", 0.0, 130.0, "2021-09-15" to 141.0, "2023-01-20" to 128.0, "2025-08-11" to 118.0),
        series("4", "ויטמין D", "ng/mL", 20.0, 100.0, "2022-02-03" to 17.0, "2023-01-20" to 24.0, "2025-08-11" to 31.0),
        series("5", "TSH", "mIU/L", 0.4, 4.0, "2021-09-15" to 1.9, "2025-08-11" to 2.2),
    )

    @Composable
    private fun Frame(title: String = "התיק הרפואי שלי", back: Boolean = false, home: Boolean = false, tab: Int = 0, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Scaffold(topBar = { AppTopBar(title, onBack = if (back) ({}) else null, onSync = if (home) ({}) else null) }) { pad ->
                        Column(Modifier.padding(pad).fillMaxSize()) {
                            if (home) { PersonChips(people, "1") {}; HomeTabs(tab) {} }
                            content()
                        }
                    }
                }
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) = captureRoboImage("build/demo/$name.png") { content() }

    @Test fun s1_lock() = shot("1_lock") {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MaterialTheme { Surface(Modifier.fillMaxSize()) { LockScreen(null) {} } } }
    }

    @Test fun s2_byTest() = shot("2_tests") { Frame(home = true, tab = 1) { LabSeriesList(allSeries) {} } }

    @Test fun s3_graph() = shot("3_graph") { Frame(back = true) { LabSeriesScreen(allSeries[0]) } }

    @Test fun s4_byDate() = shot("4_by_date") {
        Frame(home = true, tab = 2) {
            LabVisitList(listOf(
                LabVisit("11", "2", "2025-08-11T08:00:00", "ד\"ר לדוגמה", "מעבדה לדוגמה"),
                LabVisit("12", "2", "2024-06-02T08:00:00", "ד\"ר לדוגמה", "מעבדה לדוגמה"),
                LabVisit("13", "2", "2023-01-20T08:00:00", "ד\"ר אחר", "מעבדה לדוגמה"),
            )) {}
        }
    }

    @Test fun s5_detail() = shot("5_detail") {
        Frame(back = true) {
            LabDetailScreen(LabDetail("ספירת דם", "2023-01-20T08:00:00", listOf(
                LabRecord("HEMOGLOBIN", "11.6", "g/dL", 12.0, 16.0, 0, null),
                LabRecord("WBC", "6.4", "K/uL", 4.0, 10.0, 0, null),
                LabRecord("PLATELETS", "250", "K/uL", 150.0, 450.0, 0, null),
                LabRecord("GLUCOSE", "104", "mg/dL", 70.0, 100.0, 0, "בצום"),
            )))
        }
    }

    @Test fun s6_meds() = shot("6_meds") {
        Frame(home = true, tab = 3) {
            MedsScreen(
                listOf(Medication("תרופה לדוגמה 10 מ\"ג", "ד\"ר לדוגמה", null, "2026-02-01T00:00:00", true)),
                listOf(Prescription("1", "ד\"ר לדוגמה", "רפואת משפחה", "2025-08-11T10:00:00", null, null, listOf("תרופה לדוגמה 10 מ\"ג"))),
                listOf(Purchase("2025-08-12T12:00:00", "תרופה לדוגמה 10 מ\"ג", "ד\"ר לדוגמה", "בית מרקחת לדוגמה")),
            )
        }
    }

    @Test fun s7_docs() = shot("7_docs") {
        Frame(home = true, tab = 4) {
            DocsScreen(
                listOf(MedDocument("2025-07-01T09:00:00", "הפניה לרופא עיניים", "הפניה", "ד\"ר לדוגמה", "רפואת משפחה", null, null, null, null, true)),
                listOf(Vaccine("2024-10-15T00:00:00", "שפעת עונתית")),
                listOf(Examination("2024-03-03T00:00:00", "א.ק.ג", "מכון לדוגמה", true)),
            )
        }
    }
}
