package com.myehr.app.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.myehr.app.ai.AskScreen
import com.myehr.app.ai.MedSummary
import com.myehr.app.data.LabPoint
import com.myehr.app.data.LabSeries
import com.myehr.app.data.LabVisit
import com.myehr.app.data.Person
import com.myehr.app.ui.BottomBar
import com.myehr.app.ui.Dashboard
import com.myehr.app.ui.DashboardData
import com.myehr.app.ui.LabSeriesScreen
import com.myehr.app.ui.LabsScreen
import com.myehr.app.ui.LockScreen
import com.myehr.app.ui.MyEhrTheme
import com.myehr.app.ui.Look
import com.myehr.app.ui.PineLook
import com.myehr.app.ui.OceanLook
import com.myehr.app.ui.OliveLook
import com.myehr.app.ui.ContrastLook
import com.myehr.app.ui.SettingsScreen
import com.myehr.app.ui.SubHeader
import com.myehr.app.ui.Tab
import com.myehr.app.ui.TextSize
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
    private val me = Person("1", "אבא", lastSync = 1762200000000)
    private val people = listOf(me, Person("2", "אמא"), Person("3", "נועה", isChild = true))

    private fun series(code: String, name: String, unit: String, low: Double, high: Double, vararg v: Pair<String, Double>) =
        LabSeries(code, name, v.map { (d, x) -> LabPoint("${d}T08:00:00", x, x.toString(), unit, low, high, null) })

    private val all = listOf(
        series("1", "המוגלובין", "g/dL", 12.0, 16.0, "2019-03-01" to 13.1, "2020-05-10" to 12.8, "2021-09-15" to 13.5, "2023-01-20" to 11.6, "2024-06-02" to 12.9, "2025-08-11" to 13.4),
        series("2", "גלוקוז בצום", "mg/dL", 70.0, 100.0, "2020-05-10" to 92.0, "2022-02-03" to 98.0, "2023-01-20" to 104.0, "2025-08-11" to 107.0),
        series("3", "כולסטרול LDL", "mg/dL", 0.0, 130.0, "2021-09-15" to 151.0, "2022-06-01" to 146.0, "2023-01-20" to 128.0, "2025-08-11" to 118.0),
        series("4", "ויטמין D", "ng/mL", 20.0, 100.0, "2022-02-03" to 17.0, "2023-01-20" to 24.0, "2025-08-11" to 18.5),
        series("5", "TSH", "mIU/L", 0.4, 4.0, "2021-09-15" to 1.9, "2023-01-20" to 2.4, "2025-08-11" to 2.2),
        series("6", "קריאטינין", "mg/dL", 0.7, 1.3, "2021-09-15" to 0.9, "2025-08-11" to 0.95),
    )
    private val data = DashboardData(
        people, me,
        flagged = all.filter { it.latest!!.outOfRange },
        latest = all.sortedByDescending { it.latest!!.date },
        meds = listOf(MedSummary("סטטין לדוגמה 20 מ\"ג", "2022-05-01", "2025-08-01", 14, true, true), MedSummary("ויטמין D3 לדוגמה", "2025-08-20", "2025-10-01", 2, true, false)),
    )

    @Composable
    private fun Screen(tab: Tab? = Tab.HOME, content: @Composable () -> Unit) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = { if (tab != null) BottomBar(tab) {} }) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) { content() }
            }
        }
    }

    private fun shot(name: String, dark: Boolean = false, size: TextSize = TextSize.NORMAL, look: Look = PineLook, content: @Composable () -> Unit) =
        captureRoboImage("build/demo/$name.png") { MyEhrTheme(size, dark, look) { content() } }

    @Test fun opt_1_pine() = shot("opt_1_pine") { Screen { dash() } }
    @Test fun opt_2_ocean() = shot("opt_2_ocean", look = OceanLook) { Screen { dash() } }
    @Test fun opt_3_olive() = shot("opt_3_olive", look = OliveLook) { Screen { dash() } }
    @Test fun opt_4_contrast() = shot("opt_4_contrast", look = ContrastLook) { Screen { dash() } }
    @Test fun opt_4_contrast_dark() = shot("opt_4_contrast_dark", dark = true, look = ContrastLook) { Screen { dash() } }
    @Test fun opt_4_contrast_trend() = shot("opt_4_contrast_trend", look = ContrastLook) { Screen(null) { LabSeriesScreen(all[2], data.meds) } }
    @Test fun opt_1_pine_trend() = shot("opt_1_pine_trend") { Screen(null) { LabSeriesScreen(all[2], data.meds) } }
    @Test fun opt_2_ocean_trend() = shot("opt_2_ocean_trend", look = OceanLook) { Screen(null) { LabSeriesScreen(all[2], data.meds) } }
    @Test fun opt_3_olive_trend() = shot("opt_3_olive_trend", look = OliveLook) { Screen(null) { LabSeriesScreen(all[2], data.meds) } }

    private val dash: @Composable () -> Unit = { Dashboard(data, {}, {}, {}, {}, {}) }

    @Test fun a_home() = shot("a_home") { Screen { dash() } }
    @Test fun b_home_dark() = shot("b_home_dark", dark = true) { Screen { dash() } }
    @Test fun c_home_large() = shot("c_home_large", size = TextSize.XLARGE) { Screen { dash() } }
    @Test fun d_trend() = shot("d_trend") { Screen(null) { SubHeader("מגמה לאורך זמן") {}; LabSeriesScreen(all[2], data.meds) } }
    @Test fun e_labs() = shot("e_labs") {
        Screen(Tab.LABS) { LabsScreen(all, listOf(LabVisit("1", "2", "2025-08-11T08:00:00", "ד\"ר לדוגמה", "מעבדה לדוגמה")), true, {}, {}, {}) }
    }
    @Test fun f_ask() = shot("f_ask") { Screen(Tab.ASK) { AskScreen(me, "איך השתנה הכולסטרול מאז שהתחלתי סטטין?") } }
    @Test fun g_settings() = shot("g_settings") { Screen(null) { SubHeader("הגדרות") {}; SettingsScreen(TextSize.LARGE, {}, PineLook, {}, true) {} } }
    @Test fun h_lock() = shot("h_lock") { Surface(Modifier.fillMaxSize()) { LockScreen(null) {} } }
}
