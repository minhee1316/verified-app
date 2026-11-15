package com.verified.incar

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import com.verified.incar.core.Capabilities
import com.verified.incar.core.CarModel
import com.verified.incar.core.Gear
import com.verified.incar.core.HandKind
import com.verified.incar.core.LendState
import com.verified.incar.core.Module
import com.verified.incar.core.OpenTrip
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import com.verified.incar.core.Settings
import com.verified.incar.core.Signals
import com.verified.incar.core.VehicleDetails
import com.verified.incar.vehicle.VehicleSignalSource
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 서비스가 늦게 준비되거나 재시작돼도 예시 데이터와 실제 운행 세션을 섞지 않는다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RuntimeConnectionLifecycleTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun withRuntime(check: (VerifiedRuntime, LateVehicleSource) -> Unit) {
        val flags = app.applicationInfo.flags
        app.applicationInfo.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        Store(app).saveSettings(Settings(personas = setOf(Persona.NIGHT_OUT), carModel = CarModel.AVANTE, parkingNote = "실제 차량 메모"))
        lateinit var source: LateVehicleSource
        val runtime = VerifiedRuntime(app, false, PhoneRelay::publish) { signals, _ ->
            LateVehicleSource(signals).also { source = it }
        }
        try {
            check(runtime, source)
        } finally {
            runtime.close()
            app.applicationInfo.flags = flags
        }
    }

    /** 실차 연결이 끊긴 동안에는 시작할 수 없는 체험 카드를 띄우지 않는다(실제 기록을 예시 값과 섞지 않는 규칙과 같게). */
    @Test fun droppedCar_doesNotOfferTrialThatCannotStart() = withRuntime { runtime, source ->
        source.ready()
        idle()
        source.drop()
        idle()
        val u = runtime.ui.value
        assertFalse(u.carConnected)
        assertFalse(u.simAllowed)
        assertFalse(u.trialAvailable)
        assertFalse(runtime.startTrial(CarModel.TUCSON))
    }

    /** AAOS가 2열 착좌를 못 읽어도 Pleos에서 뒷좌석 착좌 값을 실제로 받으면 착좌 센서로 판정한다(받기 전에는 그대로). */
    @Test fun rearOccupancyFromPleos_enablesSeatSensorOnlyAfterRealReading() = withRuntime { runtime, source ->
        source.ready()
        idle()
        assertFalse(runtime.ui.value.caps.row2Occupancy)
        source.emit { it.copy(details = VehicleDetails(occupied = mapOf(com.verified.incar.core.SeatPart.DRIVER to com.verified.incar.core.AreaValue(true, 1L)))) }
        idle()
        assertFalse("운전석 값만으로는 뒷좌석 착좌를 지원한다고 보지 않는다", runtime.ui.value.caps.row2Occupancy)
        source.emit { it.copy(details = VehicleDetails(occupied = mapOf(com.verified.incar.core.SeatPart.REAR_RIGHT to com.verified.incar.core.AreaValue(false, 2L)))) }
        idle()
        assertTrue(runtime.ui.value.caps.row2Occupancy)
        source.drop()
        idle()
        assertFalse(runtime.ui.value.caps.row2Occupancy)
    }

    @Test fun lateConnection_switchesToRealStoreAndCancelsExampleScenario() = withRuntime { runtime, source ->
        assertTrue(runtime.ui.value.trial)
        runtime.settings { it.copy(carModel = CarModel.TUCSON, parkingNote = "시연 차량 메모") }
        runtime.act { s, now -> startLend(s, now, HandKind.SERVICE) }
        runtime.act { s, now -> endLend(s, now) }
        assertEquals(1, Store(app, ns = Store.TRIAL_NS).loadHistory().size)
        assertTrue(Store(app).loadHistory().isEmpty())
        val scenario = runtime.runScenario("valet_quick_off")
        idle()
        assertTrue(scenario.isActive)
        assertTrue(source.paused > 0)

        source.ready()
        idle()
        val actual = runtime.ui.value
        assertTrue(actual.carConnected)
        assertFalse(actual.trial)
        assertFalse(actual.simAllowed)
        assertTrue(scenario.isCancelled)
        assertEquals(0, source.paused)
        assertEquals(CarModel.AVANTE, actual.settings.carModel)
        assertEquals("실제 차량 메모", actual.settings.parkingNote)
        assertTrue(actual.engine.history.isEmpty())
        assertTrue(actual.signals.seenAt.isEmpty())
        assertEquals(0.0, actual.signals.odometerKm, 0.0)
        assertEquals(0, actual.signals.socPct)
        assertTrue(actual.signals.posture.isEmpty())
        assertEquals("시연 차량 메모", Store(app, ns = Store.TRIAL_NS).loadSettings().parkingNote)
        assertEquals(1, Store(app, ns = Store.TRIAL_NS).loadHistory().size)

        runtime.settings { it.copy(parkingNote = "실제 차량 메모 수정") }
        assertEquals("실제 차량 메모 수정", Store(app).loadSettings().parkingNote)
        assertEquals("시연 차량 메모", Store(app, ns = Store.TRIAL_NS).loadSettings().parkingNote)
    }

    @Test fun dropAndReconnect_preserveActiveHandoverAndDoNotReturnToDemo() = withRuntime { runtime, source ->
        source.ready()
        idle()
        runtime.act { s, now -> startLend(s, now, HandKind.SERVICE, memo = "진행 중인 실제 정비") }
        val handover = runtime.ui.value.engine.lend
        assertTrue(handover.active)

        source.drop()
        idle()
        assertFalse(runtime.ui.value.carConnected)
        assertFalse(runtime.ui.value.simAllowed)
        assertFalse(runtime.ui.value.trial)
        assertEquals(handover, runtime.ui.value.engine.lend)
        assertFalse(runtime.ui.value.caps.speed)
        val disconnected = runtime.signals.value
        runtime.sim { it.copy(odometerKm = 999_999.0, socPct = 99) }
        assertEquals(disconnected, runtime.signals.value)

        source.ready()
        idle()
        assertTrue(runtime.ui.value.carConnected)
        assertFalse(runtime.ui.value.simAllowed)
        assertEquals(handover, runtime.ui.value.engine.lend)
        assertTrue(runtime.ui.value.caps.speed)
        assertTrue(runtime.ui.value.signals.seenAt.isEmpty())
        assertTrue(Store(app).loadSession().lend.active)
        assertEquals("진행 중인 실제 정비", Store(app).loadSession().lend.memo)
    }

    @Test fun modelArrivingAfterConnection_updatesRealCarWithoutChangingExampleStore() = withRuntime { runtime, source ->
        runtime.settings { it.copy(carModel = CarModel.GRANDEUR) }
        source.ready()
        idle()
        assertEquals(CarModel.AVANTE, runtime.ui.value.settings.carModel)
        source.emitModel("TUCSON")
        idle()
        assertEquals("raw=${runtime.signals.value.modelName}, ui=${runtime.ui.value.signals.modelName}, " +
            "subscribers=${runtime.signals.subscriptionCount.value}, connected=${runtime.ui.value.carConnected}",
            CarModel.TUCSON, runtime.ui.value.settings.carModel)
        assertEquals(CarModel.GRANDEUR, Store(app, ns = Store.TRIAL_NS).loadSettings().carModel)
    }

    @Test fun synchronousPermissionReconnect_doesNotInventIgnitionOffOrDiscardOpenTrip() = withRuntime { runtime, source ->
        source.ready()
        idle()
        val now = runtime.ui.value.nowMs
        source.emit { it.copy(gear = Gear.D, speedKmh = 32f, ignitionOn = true, odometerKm = 14_000.0,
            seenAt = mapOf("gear" to now, "speed" to now, "ignition" to now, "odometer" to now)) }
        idle()
        runtime.settings { it.copy(extraModules = it.extraModules + Module.TRIP) }
        val open = OpenTrip(now - 5_000, 14_000.0, Place.OTHER, "실제 운전자")
        runtime.act { _, _ -> loadSession(null, open, LendState()); emptyList() }
        assertEquals(open, runtime.ui.value.engine.trip.open)
        assertTrue(runtime.ui.value.signals.ignitionOn)

        source.drop()
        idle()
        source.synchronousConnectAvailable = true
        runtime.onPermissions()
        idle()
        assertTrue(runtime.ui.value.carConnected)
        assertFalse(runtime.ui.value.simAllowed)
        assertEquals(open, runtime.ui.value.engine.trip.open)
        assertTrue(runtime.ui.value.engine.trip.logs.isEmpty())
        assertTrue(runtime.ui.value.signals.ignitionOn)
        assertEquals(14_000.0, runtime.ui.value.signals.odometerKm, 0.0)
    }

    /** AAOS lifecycle처럼 연결 상태를 먼저 알리고 실제 신호는 나중에 전달한다. */
    private class LateVehicleSource(private val signals: MutableStateFlow<Signals>) : VehicleSignalSource {
        override var connected = false
            private set
        private val unavailable = Capabilities(row2Occupancy = false, row2Recline = false, seatPosture = false,
            ambientControl = false, seatControl = false, climateControl = false, seatHeat = false,
            seatVent = false, steeringHeat = false, speed = false)
        override var caps = unavailable
            private set
        override val live = emptySet<String>()
        override var paused = 0
        private var listener: (Boolean) -> Unit = {}
        private val main = Handler(Looper.getMainLooper())
        var synchronousConnectAvailable = false

        override fun setConnectionListener(listener: (Boolean) -> Unit) { this.listener = listener }
        override fun connect(): Boolean {
            if (synchronousConnectAvailable) {
                synchronousConnectAvailable = false
                ready()
            }
            return connected
        }
        override fun resubscribeIfGranted() = false
        override fun refresh(name: String) = Unit
        override fun disconnect() { connected = false }

        fun ready() {
            connected = true
            caps = unavailable.copy(speed = true)
            main.post { listener(true) }
        }

        fun drop() {
            connected = false
            caps = unavailable
            signals.value = signals.value.copy(seenAt = emptyMap(), details = VehicleDetails())
            main.post { listener(false) }
        }

        fun emitModel(name: String) {
            emit { it.copy(modelName = name) }
        }

        fun emit(change: (Signals) -> Signals) {
            main.post { signals.value = change(signals.value) }
        }
    }
}
