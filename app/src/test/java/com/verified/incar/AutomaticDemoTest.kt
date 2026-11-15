package com.verified.incar

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Looper
import com.verified.incar.core.*
import com.verified.incar.vehicle.VehicleSignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** 디버그 허용이 없는 실제 배포 조건에서 등록 없는 시연과 실제 차량 경계를 검사한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomaticDemoTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var originalFlags = 0
    private val runtimes = mutableListOf<VerifiedRuntime>()

    @Before fun releaseBuild() {
        originalFlags = app.applicationInfo.flags
        app.applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    @After fun cleanup() {
        runtimes.forEach { it.close() }
        app.applicationInfo.flags = originalFlags
    }

    private fun runtime() = VerifiedRuntime(app, simTicker = false).also { runtimes += it }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test fun freshRelease_canUseLockClimateAndRemoteWithoutRegistering() {
        val vm = runtime()
        assertTrue(vm.ui.value.trial)
        assertTrue(vm.ui.value.simAllowed)
        assertTrue(vm.ui.value.settings.onboarded)
        assertTrue(vm.ui.value.canControl)
        assertEquals(CarModel.GRANDEUR, vm.ui.value.settings.carModel)
        assertNull(Store(app).loadSettings().carModel)
        vm.act { s, now -> control(ControlKind.UNLOCK, s, now) }
        idle()
        assertFalse(vm.ui.value.signals.doorsLocked)
        vm.act { s, now -> control(ControlKind.CLIMATE_ON, s, now) }
        idle()
        assertTrue(vm.ui.value.signals.hvacPowerOn)
        vm.sim { it.copy(gear = Gear.P, speedKmh = 0f, doorsLocked = true, driverSeated = false, ignitionOn = false) }
        idle()
        vm.act { s, now -> startRemote(s, now) }
        idle()
        assertEquals(ComfortPhase.REMOTE_RUNNING, vm.ui.value.engine.comfort.phase)
        assertEquals(vm.ui.value.settings.comfort.getValue("차주").tempC, vm.ui.value.signals.hvacTempC, 0.01f)
    }

    @Test fun upgrade_preservesRealSettingsAndRecordsAndKeepsDemoChoices() {
        val real = Store(app)
        val now = System.currentTimeMillis()
        val saved = Settings(carModel = CarModel.AVANTE, personas = setOf(Persona.NIGHT_OUT), flags = setOf(Flag.TTS))
        val record = HandoverRecord("맡김", now - 3_600_000, now, 2.5, 30f, 0, emptyList(), true)
        real.saveSettings(saved)
        real.saveHistory(listOf(record), now = true)
        val vm = runtime()
        assertEquals(CarModel.GRANDEUR, vm.ui.value.settings.carModel)
        assertTrue(vm.ui.value.engine.history.isEmpty())
        vm.settings { it.copy(carModel = CarModel.TUCSON, flags = it.flags - Flag.REMOTE) }
        vm.act { s, time -> startLend(s, time, HandKind.SERVICE) }
        vm.act { s, time -> endLend(s, time) }
        assertEquals(saved, real.loadSettings())
        assertEquals(listOf(record), real.loadHistory())
        val reopened = runtime()
        assertEquals(CarModel.TUCSON, reopened.ui.value.settings.carModel)
        assertFalse(Flag.REMOTE in reopened.ui.value.settings.flags)
        assertEquals(1, reopened.ui.value.engine.history.size)
    }

    private class FakeVehicle(private val signals: MutableStateFlow<Signals>) : VehicleSignalSource {
        var available = false
        override var connected = false
        override var paused = 0
        override val caps = Capabilities(climateControl = false, seatControl = false, ambientControl = false)
        override val live = setOf("gear", "speed", "ignition")
        override fun connect(): Boolean {
            connected = available
            if (connected) signals.value = signals.value.copy(gear = Gear.D, speedKmh = 42f, modelName = "GRANDEUR", place = Place.OTHER,
                gpsLost = true, hvacPowerOn = false, seenAt = mapOf("gear" to 1L, "speed" to 1L, "ignition" to 1L))
            return connected
        }
        override fun resubscribeIfGranted() = false
        override fun refresh(name: String) = Unit
        override fun disconnect() { connected = false }
    }

    @Test fun liveConnection_cancelsDemoAndDoesNotCarryExampleFlagsOrSignalsIntoVehicle() {
        lateinit var source: FakeVehicle
        val real = Settings(carModel = CarModel.GRANDEUR, personas = setOf(Persona.NIGHT_OUT), flags = setOf(Flag.TTS))
        Store(app).saveSettings(real)
        val vm = VerifiedRuntime(app, false, PhoneRelay::publish) { signals, _ -> FakeVehicle(signals).also { source = it } }
            .also { runtimes += it }
        val scenario = vm.runScenario("valet")
        idle()
        assertTrue(vm.simAllowed)
        source.available = true
        vm.onPermissions()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertTrue(scenario.isCancelled)
        assertTrue(vm.ui.value.carConnected)
        assertFalse(vm.ui.value.trial)
        assertFalse(vm.ui.value.simAllowed)
        assertFalse(vm.ui.value.canControl)
        assertEquals(real.flags, vm.ui.value.settings.flags)
        assertFalse(vm.ui.value.engine.valet.active)
        assertEquals(42f, vm.ui.value.signals.speedKmh, 0.01f)
        assertTrue(vm.ui.value.signals.posture.isEmpty())
        assertFalse(vm.ui.value.signals.driverSeated)
        assertFalse(vm.ui.value.signals.acOn)
        assertEquals(0.0, vm.ui.value.signals.odometerKm, 0.01)
        assertEquals(setOf("gear", "speed", "ignition"), vm.ui.value.signals.seenAt.keys)
        vm.sim { it.copy(speedKmh = 0f, gear = Gear.P, place = Place.HOME) }
        vm.stopDemoDriving()
        vm.caps { it.copy(climateControl = true) }
        vm.act { s, now -> control(ControlKind.CLIMATE_ON, s, now) }
        vm.act { s, now -> startRemote(s, now) }
        idle()
        assertEquals(42f, vm.ui.value.signals.speedKmh, 0.01f)
        assertFalse(vm.ui.value.caps.climateControl)
        assertFalse(vm.ui.value.signals.hvacPowerOn)
        assertEquals(ComfortPhase.OFF, vm.ui.value.engine.comfort.phase)
        assertNull(vm.ui.value.engine.pendingControl)
        // 연결을 잃어도 예시 신호로 자동 복귀하지 않는다.
        source.available = false
        source.disconnect()
        vm.onPermissions()
        vm.sim { it.copy(speedKmh = 0f) }
        idle()
        assertFalse(vm.simAllowed)
        assertEquals(42f, vm.ui.value.signals.speedKmh, 0.01f)
    }

    @Test fun demoStillRejectsRemoteWhenParkDoorOrBatteryConditionsFail() {
        val vm = runtime()
        listOf(
            Signals(gear = Gear.D, speedKmh = 5f, doorsLocked = true, driverSeated = false, ignitionOn = false) to "P단",
            Signals(doorsLocked = false, driverSeated = false, ignitionOn = false) to "잠겨",
            Signals(socPct = 10, doorsLocked = true, driverSeated = false, ignitionOn = false) to "배터리",
        ).forEach { (rejected, reason) ->
            vm.sim { rejected }
            idle()
            vm.act { s, now -> startRemote(s, now) }
            assertEquals(ComfortPhase.OFF, vm.ui.value.engine.comfort.phase)
            assertTrue(vm.ui.value.engine.comfort.message.orEmpty().contains(reason))
        }
    }

    @Test fun scenarioDoubleTap_startsOnlyOnePlaybackBeforeTheFirstDelay() {
        val vm = runtime()
        val first = vm.runScenario("valet")
        val duplicate = vm.runScenario("service")
        idle()
        assertTrue(first.isActive)
        assertTrue(duplicate.isCompleted)
        assertTrue(vm.ui.value.toast.contains("시연이 진행 중"))
        assertFalse(vm.ui.value.engine.lend.active)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertTrue(first.isCompleted && !first.isCancelled)
        assertEquals(ValetPhase.COMPLETE, vm.ui.value.engine.valet.phase)
        assertEquals(listOf("대리"), vm.ui.value.engine.history.map { it.kind })
    }

    @Test fun stopDemoDriving_cancelsFutureScenarioSignalsAndKeepsTheCurrentSession() {
        val vm = runtime()
        val job = vm.runScenario("valet")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(Gear.D, vm.ui.value.signals.gear)
        val handover = vm.ui.value.engine.valet.handover
        vm.stopDemoDriving()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertTrue(job.isCancelled)
        assertFalse(vm.ui.value.signals.uxRestricted)
        assertEquals(Gear.P, vm.ui.value.signals.gear)
        assertEquals(0f, vm.ui.value.signals.speedKmh, 0.01f)
        assertEquals(handover, vm.ui.value.engine.valet.handover)
        assertEquals(ValetPhase.DRIVING, vm.ui.value.engine.valet.phase)
    }

    @Test fun stopDemoDriving_unlocksAnAlreadyCompletedEvScenario() {
        val vm = runtime()
        val job = vm.runScenario("ev_trip")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertTrue(job.isCompleted)
        assertTrue(vm.ui.value.signals.uxRestricted)
        vm.stopDemoDriving()
        idle()
        assertFalse(vm.ui.value.signals.uxRestricted)
        assertEquals(Gear.P, vm.ui.value.signals.gear)
        assertEquals(0f, vm.ui.value.signals.speedKmh, 0.01f)
    }
}
