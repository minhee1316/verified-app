package com.verified.incar

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.verified.incar.core.Capabilities
import com.verified.incar.core.CarModel
import com.verified.incar.core.Consent
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Persona
import com.verified.incar.core.Pin
import com.verified.incar.core.Place
import com.verified.incar.core.Settings
import com.verified.incar.core.Signals
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.VerdictEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 실차(AAOS) 점검에서 찾은 문제를 다시 생기지 않게 막는 테스트. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-mdpi")
class RealCarAuditTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** 실차 연결 때와 같은 준비(경로 없이 위치, 집 핀 필수, 장소 초기화)에서도 GPS로 집 도착을 보면 ①이 완료된다. */
    @Test fun valet_realCarSetup_gpsHomeCompletes() {
        val e = VerdictEngine(Settings(personas = setOf(Persona.NIGHT_OUT), flags = Flag.entries.toSet(), consents = Consent.entries.toSet(), carModel = CarModel.GRANDEUR))
        // shortcut: CarBridge는 JVM에서 만들 수 없어 caps 값을 복사함, CarBridge가 바뀌면 함께 고친다
        e.caps = Capabilities(locationWithoutRoute = true, ambientControl = false, seatControl = false, climateControl = false)
        e.requireHomePin = true
        e.settings = e.settings.copy(pins = mapOf(Place.HOME to Pin(37.5, 127.0)))
        e.forgetPlace()
        val pickup = Signals(place = Place.OTHER, driverSeated = true)
        e.update(pickup, 0)
        e.startValet()
        e.confirmValetConsent(pickup, 0)
        e.update(pickup.copy(gear = Gear.D, speedKmh = 30f), 1_000)
        val home = pickup.copy(gear = Gear.P, speedKmh = 0f, place = Place.HOME)
        e.update(home, 2_000)
        e.update(home.copy(ignitionOn = false), 4_000, shutdown = true)
        assertEquals(ValetPhase.COMPLETE, e.state.valet.phase)
    }

    /** 주행이 시작되면 떠 있던 PIN 패드와 기다리던 차주 조작을 버리고, 앱바 알림 아이콘도 눌리지 않는다. */
    @Test fun pinPad_closesAndAppBarLocksWhileDriving() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), flags = Flag.entries.toSet(), consents = Consent.entries.toSet()) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        vm.setOwnerPin("2468")
        vm.act { _, _ -> startValet(); emptyList() }
        rule.onNodeWithTag("nav_HOME").performClick(); rule.waitForIdle()
        vm.navigate.value = "TRIP"; rule.waitForIdle()
        rule.onNodeWithTag("pin_pad", useUnmergedTree = true).assertExists()

        vm.sim { it.copy(gear = Gear.D, speedKmh = 30f) }; rule.waitForIdle()
        rule.onNodeWithTag("pin_pad", useUnmergedTree = true).assertDoesNotExist()
        assertNull(vm.ownerRequest.value)
        rule.onNodeWithTag("nav_NOTICE").assertIsNotEnabled().performClick(); rule.waitForIdle()

        vm.sim { it.copy(gear = Gear.P, speedKmh = 0f) }; rule.waitForIdle()
        rule.onNodeWithTag("pin_pad", useUnmergedTree = true).assertDoesNotExist()
    }
}
