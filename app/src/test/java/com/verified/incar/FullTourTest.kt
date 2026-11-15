package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.verified.incar.core.CarModel
import com.verified.incar.core.Consent
import com.verified.incar.core.DailyStat
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.HandKind
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import com.verified.incar.core.Postures
import com.verified.incar.core.Signals
import com.verified.incar.core.Turn
import com.verified.incar.core.VerdictEngine
import com.verified.incar.core.Wiper
import com.verified.incar.ui.HandoverPhotos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 설명 자료(PDF)용 전체 화면 캡처. screenshots/tour 에 번호 순서로 남긴다.
 * 빈 화면이 아니라 실제로 쓴 뒤의 모습이 보이도록, 예시 신호로 대리·정비·빌려주기·운행을 먼저 진행한 다음 찍는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1900dp-port-hdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FullTourTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: VerifiedRuntime
    private var captureFolder = "tour"
    private val app get() = RuntimeEnvironment.getApplication()

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("../screenshots/$captureFolder").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun start() {
        vm = VerifiedRuntime(app, simTicker = false)
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
    private fun sig(change: (Signals) -> Signals) { vm.sim(change); vm.skipSeconds(1); rule.waitForIdle() }
    private fun skip(sec: Int) { vm.skipSeconds(sec); rule.waitForIdle() }
    private fun quiet() { vm.toast(VerifiedRuntime.IDLE_TOAST); rule.waitForIdle() }

    private fun photo(id: Int, mirror: Boolean = false): Bitmap {
        val src = android.graphics.BitmapFactory.decodeResource(app.resources, id)
        val m = android.graphics.Matrix().apply { if (mirror) preScale(-1f, 1f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    fun phoneViewport() {
        captureFolder = "reference"
        phone()
    }

    @Test fun phone() {
        start()
        val base = vm.ui.value.signals
        // 최근 7일 주행 통계
        vm.act { _, now ->
            val today = java.time.LocalDate.parse(VerdictEngine.date(now))
            loadStats(listOf(18.2, 31.5, 12.4, 26.0, 33.1, 9.6, 14.8).mapIndexed { d, km -> DailyStat(today.minusDays(6L - d).toString(), km, brakes = d % 2, accels = (d + 1) % 2, socUsed = (km / 5).toInt()) })
            emptyList()
        }
        // 업무 운행 2건
        vm.settings { it.copy(businessMode = true) }
        sig { it.copy(place = Place.HOME) }
        listOf(Triple(12.4, "거래처 방문", Place.OTHER), Triple(8.5, "출퇴근", Place.HOME)).forEach { (km, purpose, to) ->
            sig { it.copy(gear = Gear.D, speedKmh = 40f) }
            sig { it.copy(odometerKm = it.odometerKm + km) }
            sig { it.copy(speedKmh = 0f, gear = Gear.P, place = to) }
            skip(12)
            vm.act { _, _ -> selectPurpose(purpose) }
        }
        vm.act { s, now -> addService("엔진오일", s, now) }
        quiet()
        go("TRIP", "p21_운행기록부")

        // ---- 대리: 시작 전 → 운행 → 확인 중 → 완료
        go("VALET", "p19_대리주차")
        vm.act { _, _ -> startValet() }
        vm.act { s, now -> confirmValetConsent(s, now) }
        sig { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER, posture = Postures.STRANGER) }
        shot("p30_홈_대리진행중")
        sig { it.copy(odometerKm = it.odometerKm + 4.8, socPct = it.socPct - 1) }
        sig { it.copy(place = Place.HOME, speedKmh = 15f) }
        sig { it.copy(speedKmh = 0f, gear = Gear.P) }
        skip(3)
        quiet()
        go("VALET", "p29_대리_기사동의")
        sig { it.copy(driverSeated = false, driverDoorOpen = true) }
        skip(10)
        vm.act { _, _ -> endValet(); emptyList() }
        sig { it.copy(driverSeated = true, driverDoorOpen = false, posture = Postures.OWNER) }

        // ---- 정비 맡김: 사진 5장 → 직원 운행 → 돌려받음(같은 각도 비교)
        val shots = mapOf("FRONT" to photo(R.drawable.hy_grandeur_front), "REAR" to photo(R.drawable.hy_grandeur_rear),
            "LEFT" to photo(R.drawable.hy_grandeur_profile), "RIGHT" to photo(R.drawable.hy_grandeur_profile, mirror = true),
            "DASH" to photo(R.drawable.hy_grandeur_cabin))
        val pending = HandoverPhotos.pendingDir(app, HandKind.SERVICE)
        quiet()
        // 사진 시각은 실제처럼 찍은 순간(앱 시계)으로 남긴다
        shots.forEach { (angle, b) -> HandoverPhotos.save(pending, HandoverPhotos.BEFORE, angle, b).setLastModified(vm.ui.value.nowMs) }
        vm.act { s, now -> startLend(s, now, HandKind.SERVICE, memo = "엔진오일 교체 · 기존 오른쪽 뒷문 흠집 있음", dueMs = now + 3 * 3_600_000L) }
        quiet()
        skip(50 * 60)
        quiet()
        go("HAND_SERVICE", "r01_정비맡김_진행중")
        sig { it.copy(driverDoorOpen = true) }
        sig { it.copy(driverDoorOpen = false, ignitionOn = true) }
        sig { it.copy(gear = Gear.D, speedKmh = 40f) }
        sig { it.copy(speedKmh = 0f, gear = Gear.P, odometerKm = it.odometerKm + 3.2, socPct = it.socPct - 1) }
        sig { it.copy(ignitionOn = false) }
        skip(70 * 60)
        quiet()
        val serviceStart = vm.ui.value.engine.lend.start!!.timeMs
        shots.forEach { (angle, b) -> HandoverPhotos.save(HandoverPhotos.recordDir(app, serviceStart), HandoverPhotos.AFTER, angle, b).setLastModified(vm.ui.value.nowMs) }
        vm.act { s, now -> endLend(s, now) }
        quiet()
        go("HAND_SERVICE", "p17_정비맡기기")
        go("RECORD_$serviceStart", "p31_맡김기록_상세")

        // ---- 빌려주기: 급감속 2회가 있는 반납 리포트. 반납 예정(1시간)보다 늦어 알림이 한 번 간다
        skip(3 * 3600)
        vm.act { s, now -> startLend(s, now, HandKind.LEND, memo = "주말 가족 여행 · 동생", dueMs = now + 3_600_000L) }
        sig { it.copy(driverDoorOpen = true) }
        sig { it.copy(driverDoorOpen = false, ignitionOn = true) }
        // 속도가 바뀐 지 3초 안에 초당 8km/h 넘게 줄면 급감속 한 번(천천히 올리고 급하게 줄인다)
        sig { it.copy(gear = Gear.D, speedKmh = 45f) }
        skip(2); sig { it.copy(speedKmh = 62f) }
        skip(2); sig { it.copy(speedKmh = 80f) }
        sig { it.copy(speedKmh = 40f) }
        skip(2); sig { it.copy(speedKmh = 55f) }
        skip(2); sig { it.copy(speedKmh = 70f) }
        sig { it.copy(speedKmh = 28f) }
        skip(2); sig { it.copy(speedKmh = 20f) }
        skip(2); sig { it.copy(speedKmh = 10f) }
        skip(2)
        sig { it.copy(speedKmh = 0f, gear = Gear.P, odometerKm = it.odometerKm + 32.0, socPct = it.socPct - 8) }
        sig { it.copy(ignitionOn = false) }
        skip(95 * 60)
        vm.act { s, now -> endLend(s, now) }
        quiet()
        go("HAND_LEND", "p18_빌려주기")

        // ---- 부축 동행: 병원 도착, 하차 빛 안내가 켜진 순간(단계마다 실제처럼 시간이 흐른다)
        vm.act { _, now -> startEscort(now); emptyList() }
        skip(90)
        sig { it.copy(row2RightDoorOpen = true) }
        sig { it.copy(row2Seated = true) }
        skip(60)
        sig { it.copy(row2RightDoorOpen = false, row2Belt = true) }
        skip(120)
        sig { it.copy(gear = Gear.D, speedKmh = 30f, place = Place.OTHER) }
        skip(14 * 60)
        sig { it.copy(place = Place.HOSPITAL, speedKmh = 0f, gear = Gear.P) }
        skip(4)
        quiet()
        go("ESCORT", "p22_부축동행")
        sig { it.copy(row2RightDoorOpen = true, row2Belt = false) }
        sig { it.copy(row2Seated = false) }
        sig { it.copy(row2RightDoorOpen = false) }
        vm.act { _, _ -> stopEscort(); emptyList() }

        // ---- 스쿨존: 아이가 내린 뒤 22km/h로 재출발
        sig { it.copy(place = Place.SCHOOL, row2Seated = true, row2Belt = true) }
        sig { it.copy(row2Belt = false, row2RightDoorOpen = true) }
        sig { it.copy(row2Seated = false) }
        sig { it.copy(row2RightDoorOpen = false) }
        sig { it.copy(gear = Gear.D, speedKmh = 18f) }
        sig { it.copy(speedKmh = 22f) }
        skip(60)
        sig { it.copy(speedKmh = 0f, gear = Gear.P) }

        // ---- 첫 운전 미션 3개 완료, 운전자 판정, 승차감
        sig { base.copy(odometerKm = it.odometerKm, socPct = it.socPct, doorsLocked = true) }
        vm.act { _, now -> startMissions(now); emptyList() }
        sig { it.copy(wiper = Wiper.AUTO) }
        skip(6)
        sig { it.copy(turnSignal = Turn.LEFT) }
        skip(5)
        sig { it.copy(lowBeamOn = true) }
        sig { it.copy(wiper = Wiper.OFF, turnSignal = Turn.OFF, lowBeamOn = false) }
        sig { it.copy(driverSeated = false) }
        sig { it.copy(driverSeated = true, posture = Postures.OWNER) }
        vm.act { _, _ -> setChauffeurManual(true); emptyList() }
        quiet()

        // ---- 화면 캡처
        go("HOME", "p01_홈")
        tap("nav_CONTROL"); shot("p02_제어_외부")
        tap("control_interior"); shot("p03_제어_실내")
        tap("nav_MYPAGE"); shot("p05_더보기")
        listOf(
            "VEHICLE_VIEW" to "p07_외관실내사진", "SERVICE" to "p08_차량관리", "INSIGHT" to "p09_드라이빙인사이트",
            "KEY" to "p10_디지털키", "CARLIFE" to "p11_카라이프", "VERIFIED" to "p12_상황별기능", "SEARCH" to "p13_검색",
            "NOTICE" to "p14_보낸알림", "SETTINGS" to "p15_설정", "CAR_SETUP" to "p16_차량등록",
            "DRIVER" to "p20_운전자확인", "SCHOOL" to "p23_스쿨존", "DIAG" to "p26_고장이야",
            "MISSION" to "p27_첫운전", "COMFORT" to "p28_출발준비",
        ).forEach { (route, name) -> go(route, name) }
        // 뒷좌석에 손님이 탄 상태의 승차감 화면
        sig { it.copy(row2Seated = true, row2Belt = true) }
        quiet()
        go("CHAUFFEUR", "p24_승차감")
        sig { it.copy(row2Seated = false, row2Belt = false) }

        // ---- 지하 주차: 램프 두 번 → 지하 2층 확인. 하차 점검·기록 화면에 주차 위치가 보인다
        sig { it.copy(gpsLost = true) }
        sig { it.copy(gear = Gear.D, speedKmh = 12f, steeringDeg = 200f) }
        sig { it.copy(steeringDeg = 0f) }
        sig { it.copy(steeringDeg = 200f) }
        sig { it.copy(steeringDeg = 0f) }
        sig { it.copy(speedKmh = 0f, gear = Gear.P) }
        vm.act { _, _ -> confirmFloor(2) }
        vm.settings { it.copy(parkingNote = "C구역 32번 기둥 · 엘리베이터 옆", parkingNoteAtMs = vm.ui.value.nowMs) }
        quiet()
        go("TRUNK", "p25_하차점검")
        go("SIGNALS", "p32_연결신호상태")
        go("HISTORY", "p04_기록")
        val rec = vm.ui.value.engine.history.first { it.kind == "맡김" }
        val mid = (rec.startMs + rec.endMs) / 2
        rule.onNodeWithTag("fine_input").performScrollTo().performTextInput("${VerdictEngine.date(mid)} ${VerdictEngine.hhmm(mid)}")
        rule.waitForIdle()
        rule.onNodeWithTag("fine_check").performScrollTo()
        shot("r04_과태료_날짜대조")
    }

    @Test @Config(qualifiers = "w1408dp-h792dp-land-car-mdpi")
    fun car() {
        start()
        shot("c01_차화면_홈")
        tap("nav_CONTROL"); shot("c02_차화면_제어")
        tap("control_interior"); shot("c02b_차화면_제어_실내")
        go("VALET", "c04_차화면_대리시작")
        vm.act { _, _ -> startValet() }
        go("VALET", "c05_차화면_대리_기사동의")
        vm.act { _, _ -> endValet(); emptyList() }
        go("TRUNK", "c07_차화면_하차점검")
        go("MISSION", "c08_차화면_첫운전")
        // 부축 동행: 병원 도착, 하차 빛 안내(단계마다 시간이 흐른다)
        vm.act { _, now -> startEscort(now); emptyList() }
        skip(90)
        sig { it.copy(row2RightDoorOpen = true) }
        sig { it.copy(row2Seated = true) }
        skip(60)
        sig { it.copy(row2RightDoorOpen = false, row2Belt = true) }
        skip(120)
        sig { it.copy(gear = Gear.D, speedKmh = 30f, place = Place.OTHER) }
        skip(14 * 60)
        sig { it.copy(place = Place.HOSPITAL, speedKmh = 0f, gear = Gear.P) }
        skip(4)
        quiet()
        go("ESCORT", "c06_차화면_부축동행")
        // 주행 중: 켜져 있는 기능이 오른쪽 카드로 보인다
        sig { it.copy(gear = Gear.D, speedKmh = 40f, place = Place.OTHER) }
        shot("c03_차화면_주행중")
    }
}
