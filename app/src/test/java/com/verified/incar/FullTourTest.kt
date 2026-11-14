package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.verified.incar.core.CarModel
import com.verified.incar.core.Consent
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 설명 자료(PDF)용 전체 화면 캡처. screenshots/tour 에 번호 순서로 남긴다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1900dp-port-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullTourTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: VerifiedRuntime

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("../screenshots/tour").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun start() {
        vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
    }

    private fun go(route: String, name: String) {
        vm.navigate.value = route
        rule.waitForIdle()
        shot(name)
    }

    private fun tap(tag: String) { rule.onNodeWithTag(tag).performClick(); rule.waitForIdle() }

    @Test fun phone() {
        start()
        shot("p01_홈")
        tap("nav_CONTROL"); shot("p02_제어_외부")
        tap("control_interior"); shot("p03_제어_실내")
        tap("nav_HISTORY"); shot("p04_기록")
        tap("nav_MYPAGE"); shot("p05_더보기")
        listOf(
            "VEHICLE_VIEW" to "p07_외관실내사진", "SERVICE" to "p08_차량관리", "INSIGHT" to "p09_드라이빙인사이트",
            "KEY" to "p10_디지털키", "CARLIFE" to "p11_카라이프", "VERIFIED" to "p12_상황별기능", "SEARCH" to "p13_검색",
            "NOTICE" to "p14_보낸알림", "SETTINGS" to "p15_설정", "CAR_SETUP" to "p16_차량등록",
            "HAND_SERVICE" to "p17_정비맡기기", "HAND_LEND" to "p18_빌려주기",
            "VALET" to "p19_대리주차", "DRIVER" to "p20_운전자확인", "TRIP" to "p21_운행기록부", "ESCORT" to "p22_부축동행",
            "SCHOOL" to "p23_스쿨존", "CHAUFFEUR" to "p24_승차감", "TRUNK" to "p25_하차점검", "DIAG" to "p26_고장이야",
            "MISSION" to "p27_첫운전", "COMFORT" to "p28_출발준비",
        ).forEach { (route, name) -> go(route, name) }
        // 대리 진행 중: 기사 동의 → 운행
        vm.act { _, _ -> startValet(); emptyList() }
        go("VALET", "p29_대리_기사동의")
        vm.act { s, now -> confirmValetConsent(s, now) }
        vm.sim { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER) }
        rule.waitForIdle()
        go("HOME", "p30_홈_대리진행중")
    }

    @Test @Config(qualifiers = "w1408dp-h792dp-land-mdpi")
    fun car() {
        start()
        shot("c01_차화면_홈")
        tap("nav_CONTROL"); shot("c02_차화면_제어")
        vm.sim { it.copy(gear = Gear.D, speedKmh = 40f) }
        rule.waitForIdle()
        shot("c03_차화면_주행중")
    }
}
