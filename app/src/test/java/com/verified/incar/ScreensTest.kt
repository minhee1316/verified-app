package com.verified.incar

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import com.verified.incar.core.Consent
import com.verified.incar.core.DriverVerdict
import com.verified.incar.core.EscortPhase
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import com.verified.incar.core.Postures
import com.verified.incar.core.SchoolPhase
import com.verified.incar.core.Signals
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.Wiper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 실제 앱 화면을 시나리오대로 그려 verified-app/screenshots 에 PNG로 남긴다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: VerifiedRuntime

    private fun start(personas: Set<Persona> = Persona.entries.toSet()) {
        vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        vm.settings { it.copy(personas = personas, flags = Flag.entries.toSet(), consents = Consent.entries.toSet()) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
    }

    private fun sig(change: (Signals) -> Signals) {
        vm.sim(change)
        rule.waitForIdle()
    }

    private fun skip(sec: Int) {
        vm.skipSeconds(sec)
        rule.waitForIdle()
    }

    private val roots = setOf("HOME", "CONTROL", "HISTORY", "MYPAGE")

    /** 하단 탭은 바로, 나머지 화면은 마이페이지(사람 아이콘)를 거쳐 연다. */
    private fun tab(id: String) {
        if (id in roots) rule.onNodeWithTag("nav_$id").performClick()
        else {
            // 마이현대처럼 사람 아이콘은 메인 탭에만 있으므로 홈을 거쳐 마이페이지로 들어간다
            rule.onNodeWithTag("nav_HOME").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("nav_MYPAGE").performClick()
            rule.waitForIdle()
            // 기능 10개는 더보기에서 "상황별 기능" 한 줄로 합쳐져 있어 화면 경로로 바로 연다
            if (rule.onAllNodesWithTag("tab_$id").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithTag("tab_$id").performScrollTo().performClick()
            else vm.navigate.value = id
        }
        rule.waitForIdle()
    }

    private fun click(label: String) {
        rule.onAllNodesWithText(label, substring = true).onFirst().performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("../screenshots").apply { mkdirs() }
        // captureToImage()는 Robolectric에서 강제 재그리기 대기로 멈춰서, 루트 View를 직접 그린다
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun e() = vm.ui.value.engine

    @Test fun s01_onboarding() {
        start(emptySet())
        shot("01_온보딩_생활유형")
    }

    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun phoneSubscriptionFromNotices_handlesOfflineAndCanForgetTopic() {
        start()
        tab("NOTICE")
        click("차주 휴대폰 알림")
        shot("58_폰_휴대폰알림연결")
        vm.setSimOffline(true)
        click("동의하고 연결 시작")
        val topic = requireNotNull(vm.ui.value.phoneTopic)
        rule.onNodeWithText(topic).assertDoesNotExist()
        click("연결 QR·주제 보기")
        rule.onNodeWithText(topic).assertExists()
        click("연결 코드 숨기기")
        click("시험 알림 보내기")
        assertEquals(PhoneDelivery.QUEUED, vm.ui.value.notices.first().phoneStatus)
        click("휴대폰 연결 해제")
        assertNull(vm.ui.value.phoneTopic)
        assertTrue(vm.ui.value.notices.none { it.phoneStatus == PhoneDelivery.QUEUED })
    }

    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun releaseHandsetCanStartValetDemoWithoutHomeRegistration() {
        val app = RuntimeEnvironment.getApplication()
        val flags = app.applicationInfo.flags
        try {
            app.applicationInfo.flags = flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE.inv()
            start()
            tab("VALET")
            assertTrue(vm.ui.value.trial)
            assertTrue(vm.ui.value.settings.pins.isEmpty())
            rule.onNodeWithText("장소 등록 열기").assertDoesNotExist()
            rule.onNodeWithText("동의하고 출발").assertExists()
            rule.onNodeWithTag("valet_consent").performScrollTo().performClick()
            rule.waitForIdle()
            assertEquals(ValetPhase.DRIVING, e().valet.phase)
        } finally {
            app.applicationInfo.flags = flags
        }
    }

    /** 폰 세로(411×891dp): 두 단 화면이 한 단으로 쌓이고 잘리지 않는다. */
    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun s34_phonePortrait() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        rule.waitForIdle()
        shot("44_폰세로_홈")
        tab("CONTROL")
        shot("45_폰세로_제어")
        valetDriveHome()
        sig { it.copy(place = Place.HOME, speedKmh = 10f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        sig { it.copy(driverSeated = false, driverDoorOpen = true) }
        skip(11)
        assertEquals(ValetPhase.COMPLETE, e().valet.phase)
        shot("46_폰세로_대리완료")
        // 한 단으로 쌓였으면 완료 판정 카드가 화면 폭 안에 들어온다
        val w = rule.activity.window.decorView.width
        val card = rule.onAllNodesWithText("완료 판정 4가지").onFirst().fetchSemanticsNode().boundsInRoot
        assertTrue("판정 카드가 폭 안에: $card / $w", card.right <= w)
    }

    /** 모든 화면을 폰 세로로(키를 늘려 스크롤 아래까지) 그려 build/phone_audit 에 남긴다. 크기·비율 점검용. */
    @Config(qualifiers = "w411dp-h2600dp-port-mdpi")
    @Test fun audit_phonePortraitAllScreens() = auditAll("port")

    @Config(qualifiers = "w891dp-h411dp-land-mdpi")
    @Test fun audit_phoneLandscapeAllScreens() = auditAll("land")

    private fun auditAll(tag: String) {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        val dir = File("build/phone_audit").apply { mkdirs() }
        val ids = roots.toList() + listOf("CARLIFE", "WALLET", "SERVICE", "INSIGHT", "KEY", "NOTICE", "SETTINGS") + com.verified.incar.core.Module.entries.map { it.name }
        ids.forEach { id ->
            runCatching { tab(id) }.onFailure { println("audit: $id 열기 실패 ${it.message?.take(80)}") }
            rule.waitForIdle()
            val root = rule.activity.window.decorView
            val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bmp))
            File(dir, "${tag}_$id.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** 폰 홈: 판정 카드와 차 상태 체크리스트가 같은 말을 한다(창문이 열리면 둘 다 '확인 필요'). */
    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun s51_phoneHomeVerdictMatchesChecklist() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("예시 · 이상 없음").fetchSemanticsNodes().isNotEmpty())
        shot("51_폰홈_모두확인됨")
        sig { it.copy(windowOpen = true) }
        assertTrue(rule.onAllNodesWithText("확인 필요 1건").fetchSemanticsNodes().isNotEmpty())
        shot("52_폰홈_확인필요")
    }

    @Test fun s02_home() {
        start()
        shot("02_홈")
    }

    @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    @Test fun phoneBackRestoresSituationAfterOpeningControl() {
        start()
        // 홈은 등록 전에도 예시 차량을 보여 준다. 이 검사는 기능 선택·뒤로가기 복원을 검증한다.
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        rule.waitForIdle()
        rule.onNodeWithText("더 뉴 그랜저").assertExists()
        tab("VERIFIED")
        rule.onNodeWithTag("situation_PARKING").performScrollTo().performClick()
        rule.onNodeWithTag("feature_CONTROL").performScrollTo().performClick()
        rule.onNodeWithTag("control_top").assertExists()
        rule.onAllNodesWithContentDescription("뒤로").onFirst().performClick()
        rule.onNodeWithTag("feature_CONTROL").assertExists()
        rule.onNodeWithTag("situation_HANDOVER").performScrollTo().performClick()
        rule.onNodeWithTag("directory_comparison").performScrollTo().performClick()
        rule.onNodeWithText(com.verified.incar.core.Situation.HANDOVER.verified).assertExists()
        shot("57_폰_상황별_기능")
    }

    private fun valetDriveHome() {
        vm.act { _, _ -> startValet(); emptyList() }
        rule.waitForIdle()
        tab("VALET")
        shot("03a_대리_기사동의")
        vm.act { s, now -> confirmValetConsent(s, now) }
        sig { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER) }
        // 시연값: 틱 없이 찍으면 '이동 0.0km'가 되므로 PT 시나리오와 같은 거리·배터리를 넣는다
        sig { it.copy(odometerKm = it.odometerKm + 4.8, socPct = it.socPct - 1) }
    }

    @Test fun s03_valetComplete() {
        start()
        valetDriveHome()
        sig { it.copy(place = Place.HOME, speedKmh = 10f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        assertEquals(ValetPhase.PRELIM, e().valet.phase)
        sig { it.copy(driverSeated = false, driverDoorOpen = true) }
        skip(11)
        assertEquals(ValetPhase.COMPLETE, e().valet.phase)
        // 반경은 설정값, 이동 거리는 넘긴 순간과 완료 순간의 차이
        assertTrue(rule.onAllNodesWithText("집 반경 100m 안", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("이동 4.8km").fetchSemanticsNodes().isNotEmpty())
        // 확인 근거: 인수 → P단 → 위치 → 하차 → 도착. 차주 폰 본문에도 시각·출처가 붙는다
        assertTrue(rule.onAllNodesWithTag("valet_proof").fetchSemanticsNodes().isNotEmpty())
        assertEquals(listOf("인수", "P단", "집", "10초", "운전석", "도착", "바뀐"), e().valet.proof.map { it.what.substringBefore(" ") })
        val body = vm.ui.value.notices.first { "대리 주차 완료" in it.title }.body
        assertTrue(body, "근거:" in body && "P단(시뮬레이터)" in body && " → " in body)
        shot("03b_대리_완료_비교")
        // 완료 뒤 차주가 다시 앉아도 판정 근거 줄은 ✓로 남는다(지금 신호가 아니라 판정 결과)
        sig { it.copy(driverSeated = true, driverDoorOpen = false) }
        assertTrue(rule.onAllNodesWithText("!").fetchSemanticsNodes().isEmpty())
        tab("NOTICE")
        shot("14_보낸알림")
    }

    @Test fun s04_valetQuickShutdown() {
        start()
        valetDriveHome()
        sig { it.copy(place = Place.HOME, gear = Gear.P, speedKmh = 0f) }
        sig { it.copy(ignitionOn = false) }
        assertEquals(ValetPhase.COMPLETE, e().valet.phase)
        assertEquals("시동 OFF", e().valet.confirmedBy)
        shot("04_대리_시동바로OFF_완료")
    }

    @Test fun s05_valetGarage() {
        start()
        valetDriveHome()
        sig { it.copy(place = Place.HOME, speedKmh = 10f) }
        sig { it.copy(gpsLost = true, place = Place.OTHER, speedKmh = 5f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, driverSeated = false, driverDoorOpen = true) }
        skip(11)
        assertEquals(ValetPhase.PRELIM, e().valet.phase)
        rule.onNodeWithText("위치 신호를 기다리고 있어요. 완료·목적지 밖 하차 판정을 보류합니다.").assertExists()
        shot("05_대리_지하주차장_판정보류")
    }

    @Test fun s06_valetOutside() {
        start()
        valetDriveHome()
        sig { it.copy(gear = Gear.P, speedKmh = 0f, windowOpen = true, steeringDeg = 35f) }
        sig { it.copy(driverSeated = false, driverDoorOpen = true) }
        assertEquals(ValetPhase.INCOMPLETE, e().valet.phase)
        shot("06_대리_미완료_경고")
    }

    @Test fun s07_driverUnknown() {
        start()
        sig { it.copy(posture = Postures.STRANGER) }
        assertEquals(DriverVerdict.UNKNOWN, e().driver.verdict)
        tab("DRIVER")
        shot("07_운전자_처음보는자세")
    }

    @Test fun s08_tripPending() {
        start()
        sig { it.copy(gear = Gear.D, speedKmh = 40f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, place = Place.OTHER, odometerKm = it.odometerKm + 12.2) }
        skip(11)
        assertTrue(e().trip.pending != null)
        tab("TRIP")
        shot("08_운행기록부_목적선택")
    }

    @Test fun s09_escort() {
        start()
        // 조명 제어 차종이어야 하차 빛 안내와 SEA 양보가 서로 다른 화면이 된다
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        vm.act { _, now -> startEscort(now); emptyList() }
        sig { it.copy(row2Seated = true, row2Belt = true) }
        assertEquals(EscortPhase.READY, e().escort.phase)
        sig { it.copy(gear = Gear.D, speedKmh = 30f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, place = Place.HOSPITAL) }
        skip(4)
        assertEquals(EscortPhase.ALIGHT_GUIDE, e().escort.phase)
        tab("ESCORT")
        assertEquals(com.verified.incar.core.Level.OK, e().ambient!!.right)
        shot("09a_동행_하차빛안내")
        sig { it.copy(seaWarning = true) }
        assertNull(e().ambient!!.right)
        shot("09b_동행_SEA경고_조명양보")
    }

    @Test fun s10_school() {
        start()
        sig { it.copy(place = Place.SCHOOL, row2Seated = true, row2Belt = true) }
        sig { it.copy(row2Seated = false, row2Belt = false) }
        assertEquals(SchoolPhase.WAIT_MOVE, e().school.phase)
        sig { it.copy(gear = Gear.D, speedKmh = 22f) }
        skip(61)
        assertEquals(SchoolPhase.SUCCESS, e().school.phase)
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        tab("SCHOOL")
        shot("10_스쿨존_성공")
    }

    @Test fun s11_drivingAndChauffeur() {
        start()
        sig { it.copy(gear = Gear.D, speedKmh = 50f, row2Seated = true, row2Belt = true, row2Reclined = true) }
        skip(1)
        // 엔진은 차속이 바뀔 때만 기준 샘플을 새로 잡고, 0.2~3초 떨어진 두 샘플로만 감속률을 잰다. 판정 시간은 부팅 후 경과 시간이라 그 시계를 움직인다
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(500))
        sig { it.copy(speedKmh = 30f) }
        assertEquals(1, e().chauffeur.brakes)
        shot("11_주행중_3줄요약")
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        tab("CHAUFFEUR")
        shot("12_승차감_점수")
    }

    @Test fun s12_diagAndMissions() {
        start()
        sig { it.copy(wiper = Wiper.AUTO, recircOn = true, acOn = false, washerLow = true) }
        tab("DIAG")
        shot("13_확인도우미")
        sig { it.copy(wiper = Wiper.OFF) }
        tab("MISSION")
        click("미션 시작")
        sig { it.copy(wiper = Wiper.LOW, doorsLocked = true) }
        assertEquals(2, e().missions.items.count { it.done })
        shot("15_첫운전_미션")
    }

    @Test fun s13_trunkAndSettings() {
        start()
        sig { it.copy(place = Place.MART, trunkOpen = true) }
        skip(6)
        sig { it.copy(trunkOpen = false) }
        assertTrue(e().trunk.loaded)
        tab("TRUNK")
        shot("16_트렁크_확인질문")
        tab("SETTINGS")
        shot("17_설정")
    }

    @Test fun s13b_comfortLockedWithoutCar() {
        start()
        tab("COMFORT")
        shot("17b_출발준비_차량미등록")
        click("디 올 뉴 투싼")
        assertEquals(com.verified.incar.core.CarModel.TUCSON, vm.ui.value.settings.carModel)
    }

    @Test fun s13c_otherCarDrivingOnly() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.OTHER) }
        rule.waitForIdle()
        shot("02c_홈_주행기능모드")
        rule.onNodeWithTag("nav_MYPAGE").performClick()
        rule.waitForIdle()
        // 그 밖의 차량은 출발 준비(⑩)가 빠져 기능 10개 중 9개
        assertTrue(rule.onAllNodesWithText("9개").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun s20_nightModeAndWipe() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        com.verified.incar.ui.C.forceDark = true
        try {
            rule.waitForIdle()
            shot("20_야간모드_홈")
            tab("VALET")
            shot("21_야간모드_대리")
            tab("SETTINGS")
            click("내 기록 지우기")
            click("한 번 더 누르면")
            assertTrue(vm.ui.value.notices.isEmpty())
            assertTrue(vm.ui.value.engine.trip.logs.isEmpty())
            shot("22_야간모드_설정_기록지우기")
        } finally {
            com.verified.incar.ui.C.forceDark = null
        }
    }

    @Test fun s25_myHyundai() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        sig { it.copy(gear = Gear.D, speedKmh = 50f, odometerKm = it.odometerKm + 3.0) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        tab("HOME")
        shot("27_마이현대_홈")
        tab("SHOP")
        shot("27b_마이현대_샵")
        tab("VERIFIED")
        shot("27c_확인됨_기능모음")
        tab("CONTROL")
        rule.onNodeWithTag("quick_lock").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(vm.ui.value.signals.doorsLocked)
        shot("28_마이현대_원격제어")
        // 최근 4주 하루 40km, 100km당 급감속 6회 → 엔진오일 예상 날짜, 브레이크 패드 가혹 조건
        val odo = vm.ui.value.signals.odometerKm
        val today = java.time.LocalDate.now()
        vm.settings { it.copy(serviceLog = listOf(
            com.verified.incar.core.ServiceRecord(today.minusMonths(8).toString(), odo - 9_500, "엔진오일·필터"),
            com.verified.incar.core.ServiceRecord(today.minusMonths(8).toString(), odo - 2_000, "브레이크 패드"),
        )) }
        vm.act { _, _ -> loadStats((0 until 28).map { com.verified.incar.core.DailyStat(today.minusDays(it.toLong()).toString(), 40.0, if (it % 2 == 0) 5 else 0) }); emptyList() }
        assertTrue(vm.ui.value.consumables.first { it.item == com.verified.incar.core.Consumable.BRAKE_PAD }.harsh)
        tab("SERVICE")
        click("블루핸즈 정비 예약")
        shot("30_마이현대_차량관리")
        tab("INSIGHT")
        shot("31_마이현대_드라이빙인사이트")
        tab("CARLIFE")
        rule.onNodeWithTag("mh_SOS").performClick(); rule.waitForIdle()
        shot("32_마이현대_카라이프")
        click("긴급출동 요청하기")
        assertTrue(vm.ui.value.notices.any { "긴급출동" in it.title })
        tab("WALLET")
        shot("33_마이현대_월렛")
        rule.onNodeWithTag("nav_HOME").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("nav_MYPAGE").performClick(); rule.waitForIdle()
        shot("34_마이페이지")
        tab("VALET")
        assertTrue(vm.ui.value.feed.isNotEmpty())
    }

    @Config(qualifiers = "w320dp-h700dp-port-mdpi")
    @Test fun insightDateSelectionShowsDailyRecordsAndRollingTotal() {
        start()
        val today = java.time.LocalDate.parse(com.verified.incar.core.VerdictEngine.date(vm.ui.value.nowMs))
        val yesterday = today.minusDays(1)
        val zeroDay = today.minusDays(2)
        val missingDay = today.minusDays(3)
        vm.act { _, _ ->
            loadStats(listOf(
                com.verified.incar.core.DailyStat(today.toString(), km = 4.0),
                com.verified.incar.core.DailyStat(yesterday.toString(), km = 17.5, brakes = 3, accels = 8),
                com.verified.incar.core.DailyStat(zeroDay.toString(), km = 0.0),
                com.verified.incar.core.DailyStat(today.minusDays(7).toString(), km = 800.0),
            ))
            emptyList()
        }
        tab("INSIGHT")
        rule.onNodeWithTag("insight_day_$today").assertIsSelected()
        rule.onNodeWithTag("insight_rolling_total").assertTextEquals("%,.1f km".format(21.5))
        rule.onNodeWithTag("insight_day_$yesterday").performScrollTo().performClick().assertIsSelected()
        rule.onAllNodesWithText("%,.1f km".format(17.5)).onFirst().assertExists()
        rule.onNodeWithText("3회").assertExists()
        rule.onNodeWithText("8회").assertExists()
        rule.onNodeWithTag("insight_day_$missingDay").performScrollTo().performClick()
        rule.onNodeWithText("이 날짜에는 주행 기록이 없어요.").assertExists()
        rule.onNodeWithText("8회").assertDoesNotExist()
        rule.onNodeWithTag("insight_day_$zeroDay").performScrollTo().performClick()
        rule.onNodeWithText("이 날짜에는 주행 기록이 없어요.").assertDoesNotExist()
        rule.onAllNodesWithText("%,.1f km".format(0.0)).onFirst().assertExists()
    }

    @Test fun s26_ownerPin() {
        start()
        vm.setOwnerPin("2468")
        vm.lockOwner()
        rule.onNodeWithTag("nav_HOME").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("nav_MYPAGE").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("tab_SETTINGS").performScrollTo().performClick(); rule.waitForIdle()
        "1111".forEach { rule.onNodeWithTag("pin_$it").performClick() }
        rule.onNodeWithTag("pin_확인").performClick(); rule.waitForIdle()
        "24".forEach { rule.onNodeWithTag("pin_$it").performClick() }
        rule.waitForIdle()
        shot("35_차주잠금_PIN")
        "68".forEach { rule.onNodeWithTag("pin_$it").performClick() }
        rule.onNodeWithTag("pin_확인").performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("차주 잠금 (PIN)").fetchSemanticsNodes().isNotEmpty())
        shot("36_설정_차주잠금")
    }

    @Test fun s27_privateScreensDuringValet() {
        start()
        vm.setOwnerPin("2468")
        vm.act { _, _ -> startValet(); emptyList() }
        // 대리가 시작되면 다시 잠기고, 운행기록부 같은 차주 생활 화면은 PIN을 거친다
        tab("TRIP")
        rule.onNodeWithTag("pin_pad", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("취소").performClick(); rule.waitForIdle()
        rule.onNodeWithTag("pin_pad", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun s28_serviceLeakEvac() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        // 정비소에 맡김 → 직원이 시동을 켜고 달림 → 찾음
        vm.act { s, now -> startLend(s, now, com.verified.incar.core.HandKind.SERVICE); emptyList() }
        sig { it.copy(ignitionOn = false, driverSeated = false) }
        sig { it.copy(ignitionOn = true, driverSeated = true, driverDoorOpen = true) }
        sig { it.copy(driverDoorOpen = false, gear = Gear.D, speedKmh = 84f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, odometerKm = it.odometerKm + 12.6) }
        vm.act { s, now -> endLend(s, now) }
        // 일주일 동안 왼쪽 뒤만 천천히 빠진 기록
        vm.act { _, now ->
            val today = java.time.LocalDate.parse(com.verified.incar.core.VerdictEngine.date(now))
            loadStats((0 until 7).map { d -> com.verified.incar.core.DailyStat(today.minusDays(d.toLong()).toString(), 24.0, tires = listOf(248f + d * 0.3f, 248f + d * 0.3f, 236f + d * 2.2f, 248f + d * 0.3f)) })
            emptyList()
        }
        sig { it.copy(tireKpa = listOf(248f, 248f, 236f, 248f)) }
        assertTrue(vm.ui.value.feed.any { "왼쪽 뒤" in it.title })
        tab("SERVICE")
        shot("37a_차량관리_타이어누설")
        rule.waitForIdle()
        shot("37_차량관리_맡김리포트_타이어")
        // 고속 주행 뒤 멈춰 비상등
        sig { it.copy(gear = Gear.D, speedKmh = 92f) }
        sig { it.copy(speedKmh = 0f) }
        sig { it.copy(hazardOn = true) }
        // 대피 안내는 P단에서만 한다(정체 중 D단 비상등은 뒤차 경고)
        sig { it.copy(gear = Gear.P) }
        assertTrue(e().evac.active)
        shot("38_주행중_비상등_대피")
        sig { it.copy(trunkOpen = true) }
        tab("CARLIFE")
        shot("39_카라이프_비트박스")
    }

    @Test fun s29_searchAndVerifiedControl() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        rule.onNodeWithTag("home_search").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("search_box").performTextInput("와이퍼"); rule.waitForIdle()
        shot("40_검색_와이퍼")
        rule.onNodeWithTag("search_DIAG", useUnmergedTree = true).performClick(); rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("이거 고장이야?", substring = true).fetchSemanticsNodes().isNotEmpty())
        rule.onAllNodesWithContentDescription("뒤로").onFirst().performClick()
        rule.onNodeWithTag("search_DIAG").assertExists()
        rule.onNodeWithText("와이퍼").assertExists()
        // 차가 명령을 받지 못하면 '요청 완료'가 아니라 확인 중 → 실패로 알린다
        vm.setSimControlFail(true)
        tab("CONTROL")
        rule.onNodeWithTag("quick_lock").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e().pendingControl != null)
        shot("41_제어_차신호확인중")
        skip(16)
        // 신호가 오지 않았으니 실패가 아니라 '결과 미확인'. 확인 근거 카드가 보낸 시각과 판정을 보여 준다
        assertTrue(vm.ui.value.notices.any { "결과 미확인" in it.title })
        assertTrue(rule.onAllNodesWithTag("control_evidence").fetchSemanticsNodes().isNotEmpty())
        shot("43_제어_확인근거_결과미확인")
        vm.setSimOffline(true)
        vm.enablePhoneNotifications()
        vm.act { _, _ -> listOf(com.verified.incar.core.Effect.Notify("차주 폰", "⑦ 문이 잠기지 않았어요", "보관 시연", com.verified.incar.core.Level.WARN)) }
        tab("NOTICE")
        shot("42_보낸알림_서버끊김_보관")
    }

    @Test fun s14_comfortRemote() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        sig { it.copy(driverSeated = false, ignitionOn = false, doorsLocked = true) }
        tab("COMFORT")
        click("지금 미리 켜기")
        assertEquals(com.verified.incar.core.ComfortPhase.REMOTE_RUNNING, e().comfort.phase)
        shot("18_출발준비_원격실행")
    }

    @Test fun s15_comfortBoarding() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.AVANTE) }
        sig { it.copy(driverSeated = false, ignitionOn = false, doorsLocked = true) }
        sig { it.copy(driverSeated = true, ignitionOn = true, doorsLocked = false, posture = Postures.SPOUSE) }
        assertEquals(com.verified.incar.core.ComfortPhase.APPLIED, e().comfort.phase)
        assertEquals(3, vm.ui.value.signals.seatHeatDriver)
        tab("COMFORT")
        click("배우자 설정")
        shot("19_출발준비_탑승적용_배우자")
        tab("HOME")
        shot("02b_홈_차종등록")
    }

    @Test fun s21_exitCheckAndFloor() {
        start()
        sig { it.copy(place = Place.HOME, gear = Gear.D, speedKmh = 15f) }
        sig { it.copy(gpsLost = true, place = Place.OTHER, speedKmh = 10f) }
        repeat(2) {
            sig { it.copy(steeringDeg = 30f) }
            sig { it.copy(steeringDeg = 0f) }
        }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, windowOpen = true, lowBeamOn = true) }
        sig { it.copy(driverDoorOpen = true) }
        assertEquals(2, e().floor.estimate)
        assertEquals(2, e().exit.items.size)
        tab("TRUNK")
        shot("23_하차점검_주차층추정")
        // 창문을 닫고 등을 끄면 홈·확인됨 탭 건수도 바로 사라진다(문을 다시 열 때까지 남지 않게)
        assertEquals("확인 필요 2건", com.verified.incar.ui.statusOf(com.verified.incar.core.Module.TRUNK, vm.ui.value).first)
        sig { it.copy(windowOpen = false, lowBeamOn = false) }
        assertTrue(!com.verified.incar.ui.statusOf(com.verified.incar.core.Module.TRUNK, vm.ui.value).first.startsWith("확인 필요"))
    }

    @Test fun s30_valetButtonOpensConsentAndCancels() {
        start()
        tab("DRIVER")
        rule.onNodeWithText("대리운전 모드").performClick(); rule.waitForIdle()
        // ②에서 켜도 기사님이 동의 화면을 본다
        assertEquals(ValetPhase.CONSENT, e().valet.phase)
        assertTrue(rule.onAllNodesWithText("기사님께 드리는 안내", substring = true).fetchSemanticsNodes().isNotEmpty())
        // 동의 대기 중에 다시 누르면 취소된다(PIN을 정하지 않은 차는 바로)
        tab("DRIVER")
        rule.onNodeWithText("대리운전 모드").performClick(); rule.waitForIdle()
        assertEquals(ValetPhase.IDLE, e().valet.phase)
    }

    @Test fun s31_registerPostureRejudgesNow() {
        start()
        sig { it.copy(posture = Postures.STRANGER) }
        assertEquals(DriverVerdict.UNKNOWN, e().driver.verdict)
        val alerts = vm.ui.value.notices.count { "처음 보는" in it.title }
        tab("SETTINGS")
        click("지금 자세로 등록")
        // 등록한 순간 다시 판정하고, 처음 보는 자세 알림은 다시 보내지 않는다
        assertEquals(DriverVerdict.KNOWN, e().driver.verdict)
        assertEquals("차주", e().driver.name)
        assertEquals(alerts, vm.ui.value.notices.count { "처음 보는" in it.title })
    }

    @Test fun s32_knownButNotInsuredIsWarn() {
        start()
        sig { it.copy(posture = Postures.SPOUSE) }
        assertEquals(DriverVerdict.KNOWN, e().driver.verdict)
        assertEquals(com.verified.incar.core.Level.OK, com.verified.incar.ui.statusOf(com.verified.incar.core.Module.DRIVER, vm.ui.value).second)
        vm.settings { s -> s.copy(profiles = s.profiles.map { if (it.name == "배우자") it.copy(insured = false) else it }) }
        rule.waitForIdle()
        assertEquals(com.verified.incar.core.Level.WARN, com.verified.incar.ui.statusOf(com.verified.incar.core.Module.DRIVER, vm.ui.value).second)
        tab("DRIVER")
        assertTrue(rule.onAllNodesWithText("입력하신 특약에 없음", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun s33_expiredKeyCanBeSharedAgain() {
        start()
        vm.settings { it.copy(sharedKeys = listOf(com.verified.incar.core.SharedKey("자녀", "2000-01-01"))) }
        tab("KEY")
        assertTrue(rule.onAllNodesWithText("공유한 사람이 없어요").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("key_share").performScrollTo().performClick(); rule.waitForIdle()
        val keys = vm.ui.value.settings.sharedKeys
        assertEquals(1, keys.size)
        assertEquals(java.time.LocalDate.now().plusDays(7).toString(), keys.single().until)
    }

    @Test fun s22_nHoldGuide() {
        start()
        tab("MISSION")
        click("안내 시작")
        shot("24_N단유지_도우미")
        // N으로 바꾸면 기어가 P가 아니라 주행 제한 화면이 된다
        sig { it.copy(gear = Gear.N) }
        sig { it.copy(ignitionOn = false, driverSeated = false) }
        assertEquals(com.verified.incar.core.NHoldPhase.HOLDING, e().nhold.phase)
    }

    @Test fun s23_winterCheck() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.TUCSON) }
        sig { it.copy(outsideTempC = -6f, washerLow = true, tirePressureLow = true, driverSeated = false, ignitionOn = false, doorsLocked = true) }
        assertTrue(vm.ui.value.winter.size >= 2)
        tab("COMFORT")
        shot("25_겨울출발점검")
    }

    @Test fun s24_lendReport() {
        start()
        vm.act { s, now -> startLend(s, now); emptyList() }
        sig { it.copy(driverSeated = false) }
        sig { it.copy(driverSeated = true, posture = Postures.SPOUSE) }
        sig { it.copy(gear = Gear.D, speedKmh = 60f) }
        vm.sim { it.copy(speedKmh = 40f) }
        skip(1)
        sig { it.copy(gear = Gear.P, speedKmh = 0f, odometerKm = it.odometerKm + 18.4) }
        sig { it.copy(driverSeated = false) }
        sig { it.copy(driverSeated = true, posture = Postures.OWNER) }
        assertTrue(e().lend.report.isNotEmpty())
        tab("DRIVER")
        shot("26_빌려준차_반납리포트")
    }

    /** 정비소 맡김 한 번(12.6km 주행) + 빌려주기 한 번 → 이력서 2장. */
    private fun twoHandovers() {
        vm.act { s, now -> startLend(s, now, com.verified.incar.core.HandKind.SERVICE); emptyList() }
        sig { it.copy(gear = Gear.D, speedKmh = 64f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, odometerKm = it.odometerKm + 3.2, warnings = setOf("엔진")) }
        vm.act { s, now -> endLend(s, now) }
        vm.act { s, now -> startLend(s, now); emptyList() }
        sig { it.copy(odometerKm = it.odometerKm + 18.4, socPct = it.socPct - 6) }
        vm.act { s, now -> endLend(s, now) }
        rule.waitForIdle()
        assertEquals(listOf("맡김", "빌려주기"), e().history.map { it.kind })
    }

    @Test fun s47_valetPrepNaviHandover() {
        start()
        vm.act { _, _ -> startValet(); emptyList() }
        rule.waitForIdle()
        tab("VALET")
        // 동의 대기 중에 차주가 맡기기 전 현대 기능을 직접 확인해 체크한다(앱이 읽을 수 없는 기능)
        rule.onNodeWithTag("prep_BEFORE").assertExists()
        click("현대 발레 모드 켜기")
        assertTrue(com.verified.incar.core.PrepItem.VALET_MODE in e().valet.prep)
        assertTrue(e().valet.proof.any { "차주 확인" in it.what })
        vm.act { s, now -> confirmValetConsent(s, now) }
        sig { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER) }
        sig { it.copy(naviDest = "집", naviRemainKm = 12f, naviRemainMin = 18) }
        // 주행 중에는 대리 화면이 잠기므로 3줄 요약에 도착 예정 시각이 붙는다
        assertTrue(e().valet.naviEtaMs != null)
        assertTrue(vm.ui.value.drivingLines.toString(), vm.ui.value.drivingLines.any { "도착 예정" in it })
        sig { it.copy(odometerKm = it.odometerKm + 4.8, socPct = it.socPct - 1, warnings = setOf("엔진"), oilLow = true) }
        sig { it.copy(place = Place.HOME, speedKmh = 10f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        assertTrue(rule.onAllNodesWithText("현대 내비 기준", substring = true).fetchSemanticsNodes().isNotEmpty())
        sig { it.copy(driverSeated = false, driverDoorOpen = true) }
        skip(11)
        assertEquals(ValetPhase.COMPLETE, e().valet.phase)
        // 돌려받으면 인수인계서(바뀐 것)와 돌려받을 때 체크리스트가 보인다
        rule.onNodeWithTag("handover_diff").assertExists()
        assertTrue(rule.onAllNodesWithText("경고등 새로 켜짐: 엔진").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("엔진오일 부족 새로 표시").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("prep_RETURN").performScrollTo()
        click("빌트인 캠 영상 저장")
        assertTrue(com.verified.incar.core.PrepItem.CAM_SAVE in e().valet.prep)
        shot("47_대리_현대기능체크_인수인계서")
        // 리포트 아래 줄로 이력서에 간다
        rule.onNodeWithTag("to_HISTORY").performScrollTo().performClick(); rule.waitForIdle()
        rule.onNodeWithTag("history_summary").assertExists()
        assertEquals("대리", e().history.single().kind)
    }

    @Test fun s48_history() {
        start()
        tab("HISTORY")
        assertTrue(rule.onAllNodesWithText("아직 기록이 없어요").fetchSemanticsNodes().isNotEmpty())
        twoHandovers()
        assertEquals(2, rule.onAllNodesWithTag("history_record").fetchSemanticsNodes().size)
        // 총 맡김 횟수는 숫자 칸에 한 번만 나온다
        rule.onNodeWithTag("history_tiles").assertExists()
        assertTrue(rule.onAllNodesWithText("총 맡김").fetchSemanticsNodes().isNotEmpty() && rule.onAllNodesWithText("2회").fetchSemanticsNodes().isNotEmpty())
        shot("48_맡김이력서")
        rule.onNodeWithTag("history_export").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(vm.ui.value.toast, "맡김 이력서 CSV" in vm.ui.value.toast)
    }

    @Test fun s49_serviceRoamWarning() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        sig { it.copy(lat = 37.5665, lon = 126.978) }
        vm.act { s, now -> startLend(s, now, com.verified.incar.core.HandKind.SERVICE); emptyList() }
        // 시뮬레이터 '정비소에서 멀리 이동'과 같은 값: 주행거리 +6km, 위도 +0.0135°(약 1.5km)
        sig { it.copy(odometerKm = it.odometerKm + 6, lat = 37.5665 + 0.0135) }
        assertFalse(e().lend.roamWarned)
        repeat(3) { skip(10) }
        assertTrue(e().lend.roamWarned)
        assertTrue(vm.ui.value.notices.any { "정비소를 벗어났어요" in it.title })
        tab("HAND_SERVICE")
        rule.onNodeWithTag("service_hero").performScrollTo()
        assertTrue(rule.onAllNodesWithText("맡긴 차가 정비소를 벗어났어요", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("49_맡김_이탈경고")
    }

    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun s50_phonePortraitHistory() {
        start()
        twoHandovers()
        tab("HISTORY")
        shot("50_폰세로_이력서")
        val w = rule.activity.window.decorView.width
        rule.onAllNodesWithTag("history_record").fetchSemanticsNodes().forEach { assertTrue("${it.boundsInRoot} / $w", it.boundsInRoot.right <= w) }
    }

    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun homeHandoverShortcuts_openOnlyTheChosenFlowAndReturnToItsReport() {
        start()
        fun tap(tag: String) {
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.waitForIdle()
        }
        tap("home_hand_service")
        rule.onNodeWithText("블루핸즈 정비 예약").assertDoesNotExist()
        tap("service_start")
        assertEquals(com.verified.incar.core.HandKind.SERVICE, e().lend.kind)
        assertTrue(vm.ui.value.notices.any { it.title == "정비 맡기기 시작" })
        tab("HOME")
        tap("home_active")
        tap("service_end")
        assertTrue(e().lend.active)
        click("기록 마치기")
        rule.onNodeWithTag("handover_diff").assertExists()
        assertEquals(1, e().history.size)
        shot("59_폰_정비반납요약")

        tab("HOME")
        tap("home_hand_lend")
        rule.onNodeWithText("운전자 모드 설정").assertDoesNotExist()
        rule.onNodeWithTag("lend_start").assertExists()
        tab("HOME")
        tap("home_hand_valet")
        assertEquals(ValetPhase.IDLE, e().valet.phase)
        tap("valet_consent")
        assertEquals(ValetPhase.DRIVING, e().valet.phase)
    }

    /** 폰 세로 맡김 화면: 대리 동의 → 운행 → 완료(길게 눌러 끝내기), 빌려주기 시작 → 반납, 정비 맡김 → 이탈 경고 → 찾음. */
    @Config(qualifiers = "w411dp-h891dp-port-mdpi")
    @Test fun s53_phoneHandoverScreens() {
        start()
        vm.settings { it.copy(carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        vm.act { _, _ -> startValet(); emptyList() }
        rule.waitForIdle()
        tab("VALET")
        rule.onNodeWithTag("valet_steps").assertExists()
        click("동의하고 출발")
        assertEquals(ValetPhase.DRIVING, e().valet.phase)
        sig { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER, naviDest = "집", naviRemainKm = 12f, naviRemainMin = 18) }
        sig { it.copy(odometerKm = it.odometerKm + 4.8, socPct = it.socPct - 1) }
        sig { it.copy(place = Place.HOME, speedKmh = 10f) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f) }
        assertEquals(ValetPhase.PRELIM, e().valet.phase)
        // 머리 카드에 언제부터·도착 예정(현대 내비)이 붙는다. 큰 탑뷰 그림은 폰에서 뺀다
        assertTrue(rule.onAllNodesWithText("도착 예정", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithContentDescription("주차 탑뷰").fetchSemanticsNodes().isEmpty())
        shot("53_폰_대리진행")
        sig { it.copy(driverSeated = false, driverDoorOpen = true) }
        skip(11)
        assertEquals(ValetPhase.COMPLETE, e().valet.phase)
        rule.onNodeWithTag("valet_proof").assertExists()
        // 근거는 최근 3줄만, 나머지는 '모두 보기'
        assertTrue(rule.onAllNodesWithText("모두 보기", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("54_폰_대리완료")
        // 한 번 눌러서는 종료되지 않는다. 길게 누르거나 두 번째 확인을 선택해야 한다.
        rule.onNodeWithTag("valet_end").performScrollTo().performClick(); rule.waitForIdle()
        assertEquals(ValetPhase.COMPLETE, e().valet.phase)
        rule.onNodeWithTag("valet_end").performScrollTo().performTouchInput { longClick() }; rule.waitForIdle()
        assertTrue(!e().valet.active)

        // 빌려주기 시작·반납은 빌려주기 화면 한 곳에서만(운전자 확인 화면에는 없음)
        tab("DRIVER")
        rule.onNodeWithTag("lend_start").assertDoesNotExist()
        tab("HAND_LEND")
        rule.onNodeWithTag("lend_start").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e().lend.active)
        sig { it.copy(driverSeated = false) }
        sig { it.copy(driverSeated = true, posture = com.verified.incar.core.Postures.SPOUSE) }
        sig { it.copy(gear = Gear.P, speedKmh = 0f, odometerKm = it.odometerKm + 18.4, socPct = it.socPct - 6) }
        rule.onNodeWithTag("lend_end").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e().lend.active)
        rule.onNodeWithText("취소").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e().lend.active)
        rule.onNodeWithTag("lend_end").performScrollTo().performClick()
        rule.onNodeWithText("기록 마치기").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(!e().lend.active && e().lend.report.isNotEmpty())
        rule.onNodeWithTag("handover_diff").assertExists()
        rule.onNodeWithTag("nav_HOME").performClick(); rule.waitForIdle()
        tab("DRIVER")
        shot("55_폰_빌려주기")

        sig { it.copy(lat = 37.5665, lon = 126.978) }
        tab("SERVICE")
        rule.onNodeWithTag("service_start").assertDoesNotExist()
        tab("HAND_SERVICE")
        rule.onNodeWithTag("service_start").performScrollTo().performClick(); rule.waitForIdle()
        assertTrue(e().lend.active && e().lend.kind == com.verified.incar.core.HandKind.SERVICE)
        sig { it.copy(odometerKm = it.odometerKm + 6, lat = 37.5665 + 0.0135) }
        assertFalse(e().lend.roamWarned)
        repeat(3) { skip(10) }
        assertTrue(e().lend.roamWarned)
        assertTrue(rule.onAllNodesWithText("정비소를 벗어났어요", substring = true).fetchSemanticsNodes().isNotEmpty())
        shot("56_폰_정비맡김")
        rule.onNodeWithTag("service_end").performScrollTo().performTouchInput { longClick() }; rule.waitForIdle()
        assertTrue(!e().lend.active && e().lend.report.isNotEmpty())
        val w = rule.activity.window.decorView.width
        rule.onAllNodesWithTag("service_hero").fetchSemanticsNodes().forEach { assertTrue("${it.boundsInRoot} / $w", it.boundsInRoot.right <= w) }
    }
}
