package com.verified.incar.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.telltale.TelltaleData
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.DoorLockStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.content.Context
import android.content.Intent
import com.verified.incar.core.Signals
import com.verified.incar.core.DetailChannel
import com.verified.incar.core.recordArea
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * 현대 Pleos Vehicle SDK의 읽기(get·구독) API로 차 신호를 Signals에 덮어쓴다. CarBridge처럼 값과 seenAt 시각을 함께 남긴다.
 * 제어(set) API는 쓰지 않는다. Pleos Vehicle 서비스가 없는 폰·Robolectric·다른 차에서는 아무것도 하지 않는다.
 */
class PleosBridge(
    private val ctx: Context,
    private val signals: MutableStateFlow<Signals>,
    /** 판정 시계(Runtime.now). */
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
    /** 시연 시나리오 재생 중이면 true. 그동안은 차 신호를 넣지 않는다. */
    private val paused: () -> Boolean = { false },
) {
    /** 실제로 값을 한 번이라도 받은 신호 이름(구독 성공이 아니라 수신 기준). SDK 콜백 스레드에서 더해진다. */
    val live: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // SDK 콜백(바인더 스레드)이 끊긴 연결인지 확인하므로 volatile
    @Volatile private var vehicle: Vehicle? = null
    val connected get() = vehicle != null
    private val offs = mutableListOf<() -> Unit>()
    private val doorOpen = ConcurrentHashMap<DoorValue, Boolean>()
    private val doorLocked = ConcurrentHashMap<DoorValue, Boolean>()
    private val windowOpen = ConcurrentHashMap<WindowValue, Boolean>()
    private val occupied = ConcurrentHashMap<SeatValue, Boolean>()
    private val belted = ConcurrentHashMap<SeatValue, Boolean>()
    private val ignore: (Exception) -> Unit = {}

    /**
     * Pleos Vehicle 서비스에 붙어 읽기 구독을 건다. 서비스가 없으면 false.
     * [skip]: CarBridge(AAOS)가 이미 읽는 신호 이름. 두 곳이 같은 값을 번갈아 덮어쓰지 않게 건너뛴다.
     */
    fun connect(skip: Set<String> = emptySet()): Boolean {
        if (vehicle != null) return true
        if (!available(ctx, VEHICLE_ACTION, VEHICLE_PKG)) return false
        val v = runCatching { Vehicle(ctx).also { it.initialize() } }.getOrNull() ?: return false
        vehicle = v
        // 연결 전 호출은 SDK가 줄 세웠다가 보낸다. 실패(권한·미지원 영역)는 onFailed로 와서 그 신호만 '확인 불가'로 남는다
        if ("doors" !in skip) runCatching { doors(v) }
        if ("lock" !in skip) runCatching { locks(v) }
        if ("window" !in skip) runCatching { windows(v) }
        if ("row2Occupancy" !in skip) runCatching { seats(v) }
        if ("belt" !in skip) runCatching { belts(v) }
        runCatching { safety(v, tire = "tirePressure" !in skip) }
        if ("odometer" !in skip) runCatching { odometer(v) }
        if ("soc" !in skip) runCatching { battery(v) }
        return true
    }

    fun disconnect() {
        // 먼저 끊어, 늦게 도착한 콜백이 끊긴 연결의 값을 다시 '받음'으로 남기지 않게 한다
        val v = vehicle
        vehicle = null
        offs.forEach { runCatching(it) }
        offs.clear()
        runCatching { v?.release() }
        // 끊긴 연결에서 받은 값을 지금 받은 값으로 보지 않는다(CarBridge.clearSubscriptions와 같은 기준)
        val stale = live.toSet()
        live.clear()
        listOf(doorOpen, doorLocked, windowOpen, occupied, belted).forEach { it.clear() }
        if (stale.isNotEmpty()) signals.update { it.copy(seenAt = it.seenAt - stale) }
    }

    private fun put(name: String, apply: (Signals) -> Signals) {
        if (paused() || vehicle == null) return
        live += name
        runCatching { signals.update { s -> apply(s).let { it.copy(seenAt = it.seenAt + (name to clock())) } } }
    }

    /** 구독을 걸고, 성공하면 끊을 때 같은 객체·영역으로 해제한다(SDK 안내). */
    private fun <L> listen(l: L, reg: (L) -> Unit, unreg: (L) -> Unit) {
        runCatching { reg(l) }.onSuccess { offs += { unreg(l) } }
    }

    private fun doors(v: Vehicle) {
        val d = v.getDoor()
        for (dv in listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT, DoorValue.REAR)) {
            val area = DoorArea(dv)
            val f = { p: Int? ->
                readArea(DetailChannel.DOOR, doorId(dv), p?.let { it > 0 })
                if (p != null) {
                    doorOpen[dv] = p > 0
                    // 못 읽은 문은 이전 값을 둔다(읽은 문 하나로 나머지를 '닫힘'으로 꾸미지 않게)
                    put("doors") {
                        it.copy(
                            driverDoorOpen = doorOpen[DoorValue.ROW_1_LEFT] ?: it.driverDoorOpen,
                            row2LeftDoorOpen = doorOpen[DoorValue.ROW_2_LEFT] ?: it.row2LeftDoorOpen,
                            row2RightDoorOpen = doorOpen[DoorValue.ROW_2_RIGHT] ?: it.row2RightDoorOpen,
                            trunkOpen = doorOpen[DoorValue.REAR] ?: it.trunkOpen,
                        ).let { next -> if (dv == DoorValue.REAR) next.areaReading("trunk", true, clock()) else next }
                    }
                }
            }
            runCatching { d.getCurrentDoorPosition(area, f, ignore) }
            listen(object : DoorPositionListener {
                override fun onPositionUpdated(pos: Int?) = f(pos)
                override fun onFailed(e: Exception) { readArea(DetailChannel.DOOR, doorId(dv), null) }
            }, { d.registerDoorPosition(area, it) }, { d.unregisterDoorPosition(area, it) })
        }
    }

    private fun locks(v: Vehicle) {
        val d = v.getDoor()
        val doors = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
        for (dv in doors) {
            val area = DoorArea(dv)
            val f = { b: Boolean? ->
                readArea(DetailChannel.LOCK, doorId(dv), b)
                if (b != null) {
                    doorLocked[dv] = b
                    put("lock") {
                        val locked = doorLocked.values.all { value -> value }
                        it.copy(doorsLocked = locked).areaReading("lockStatus", !locked || doorLocked.keys.containsAll(doors), clock())
                    }
                }
            }
            runCatching { d.getDoorLockState(area, f, ignore) }
            listen(object : DoorLockStateListener {
                override fun onDoorLockStateUpdated(locked: Boolean?) = f(locked)
                override fun onFailed(e: Exception) { readArea(DetailChannel.LOCK, doorId(dv), null) }
            }, { d.registerDoorLockState(area, it) }, { d.unregisterDoorLockState(area, it) })
        }
    }

    private fun windows(v: Vehicle) {
        val w = v.getWindow()
        val windows = listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
        for (wv in windows) {
            val area = WindowArea(wv)
            val f = { p: Int? ->
                if (p != null) {
                    windowOpen[wv] = p > 0
                    put("window") {
                        val open = windowOpen.values.any { value -> value }
                        it.copy(windowOpen = open).areaReading("windowStatus", open || windowOpen.keys.containsAll(windows), clock())
                    }
                }
            }
            runCatching { w.getCurrentWindowPosition(area, f, ignore) }
            listen(object : WindowPositionListener {
                override fun onWindowPositionUpdated(pos: Int?) = f(pos)
                override fun onFailed(e: Exception) {}
            }, { w.registerWindowPosition(area, it) }, { w.unregisterWindowPosition(area, it) })
        }
    }

    private fun seats(v: Vehicle) {
        val st = v.getSeat()
        for (sv in SEATS) {
            val area = SeatArea(sv)
            val f = { b: Boolean? ->
                readArea(DetailChannel.OCCUPANCY, seatId(sv), b)
                if (b != null) {
                    occupied[sv] = b
                    put("row2Occupancy") {
                        it.copy(
                            driverSeated = occupied[SeatValue.ROW_1_LEFT] ?: it.driverSeated,
                            passengerSeated = occupied[SeatValue.ROW_1_RIGHT] ?: it.passengerSeated,
                            row2Seated = ROW2.any { r -> occupied[r] == true },
                        ).let { next -> if (sv == SeatValue.ROW_1_LEFT) next.areaReading("driverSeat", true, clock()) else next }
                    }
                }
            }
            runCatching { st.getCurrentSeatOccupancy(area, f, ignore) }
            listen(object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(occ: Boolean?) = f(occ)
                override fun onFailed(e: Exception) { readArea(DetailChannel.OCCUPANCY, seatId(sv), null) }
            }, { st.registerSeatOccupancy(area, it) }, { st.unregisterSeatOccupancy(area, it) })
        }
    }

    private fun belts(v: Vehicle) {
        val st = v.getSeat()
        for (sv in SEATS) {
            val area = SeatArea(sv)
            val f = { b: Boolean? ->
                readArea(DetailChannel.BELT, seatId(sv), b)
                if (b != null) {
                    belted[sv] = b
                    put("belt") { it.copy(passengerBelt = belted[SeatValue.ROW_1_RIGHT] ?: it.passengerBelt, row2Belt = ROW2.any { r -> belted[r] == true }) }
                }
            }
            runCatching { st.getCurrentSeatBeltBuckledState(area, f, ignore) }
            listen(object : SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) = f(buckled)
                override fun onFailed(e: Exception) { readArea(DetailChannel.BELT, seatId(sv), null) }
            }, { st.registerSeatBeltBuckledState(area, it) }, { st.unregisterSeatBeltBuckledState(area, it) })
        }
    }

    private fun readArea(channel: DetailChannel, areaId: Int, value: Boolean?) {
        if (!paused() && vehicle != null) signals.update { it.recordArea(channel, areaId, value, clock()) }
    }

    private fun doorId(area: DoorValue) = when (area) {
        DoorValue.ROW_1_LEFT -> 0x1
        DoorValue.ROW_1_RIGHT -> 0x4
        DoorValue.ROW_2_LEFT -> 0x10
        DoorValue.ROW_2_RIGHT -> 0x40
        DoorValue.REAR -> 0x20000000
        else -> 0
    }

    private fun seatId(area: SeatValue) = when (area) {
        SeatValue.ROW_1_LEFT -> 0x1
        SeatValue.ROW_1_RIGHT -> 0x4
        SeatValue.ROW_2_LEFT -> 0x10
        SeatValue.ROW_2_CENTER -> 0x20
        SeatValue.ROW_2_RIGHT -> 0x40
        else -> 0
    }

    private fun safety(v: Vehicle, tire: Boolean) {
        val sf = v.getSafety()
        val oil = { o: OilLevelState? -> oilLow(o)?.let { low -> put("oil") { it.copy(oilLow = low) } }; Unit }
        runCatching { sf.getEngineOilLevelState(oil, ignore) }
        listen(object : OilLevelStateListener {
            override fun onOilLevelUpdated(state: OilLevelState?) = oil(state)
            override fun onFailed(e: Exception) {}
        }, { sf.registerEngineOilLevelState(it) }, { sf.unregisterEngineOilLevelState(it) })

        val washer = { b: Boolean? -> if (b != null) put("washer") { it.copy(washerLow = b) } }
        runCatching { sf.hasLowWasherFluidWarning(washer, ignore) }
        listen(object : LowWasherFluidWaringListener {
            override fun onLowWasherFluidWaringUpdated(low: Boolean?) = washer(low)
            override fun onFailed(e: Exception) {}
        }, { sf.registerLowWasherFluidWaringState(it) }, { sf.unregisterLowWasherFluidWaringState(it) })

        // 경고등은 PRIVATE_CAR_SAFETY(제휴 승인 권한)라 승인 전에는 onFailed로 오고 '확인 불가'로 남는다
        val tell = { t: Telltales? ->
            if (t != null) {
                put("warnings") { it.copy(warnings = warnings(t.telltales)) }
                // 타이어 공기압 API도 같은 승인 권한이라, 저압은 TPMS 경고등으로 본다
                if (tire) put("tire") { it.copy(tirePressureLow = tpmsOn(t.telltales)) }
            }
        }
        runCatching { sf.getTelltalesInfo(tell, ignore) }
        listen(object : TelltaleInfoListener {
            override fun onTelltaleInfoUpdated(t: Telltales?) = tell(t)
            override fun onFailed(e: Exception) {}
        }, { sf.registerTelltales(it) }, { sf.unregisterTelltales(it) })
    }

    private fun odometer(v: Vehicle) {
        val o = v.getOdometer()
        val f = { km: Float? -> if (km != null) put("odometer") { it.copy(odometerKm = km.toDouble()) } }
        runCatching { o.getCurrentDistanceDriven(f, ignore) }
        listen(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(km: Float?) = f(km)
            override fun onFailed(e: Exception) {}
        }, { o.registerDistanceDriven(it) }, { o.unregisterDistanceDriven(it) })
    }

    private fun battery(v: Vehicle) {
        val b = v.getEvBattery()
        // shortcut: 전기차 SOC만 읽음(연료차는 실패로 와서 '확인 불가'), 하이브리드에 붙으면 Fuel.registerFuelPercent로 바꾼다
        val f = { pct: Int? -> if (pct != null) put("soc") { it.copy(socPct = pct.coerceIn(0, 100)) } }
        runCatching { b.getEvBatteryStateOfCharge(f, ignore) }
        listen(object : EvBatteryStateOfChargeListener {
            override fun onEvBatteryStateOfChargeUpdated(pct: Int?) = f(pct)
            override fun onFailed(e: Exception) {}
        }, { b.registerEvBatteryStateOfCharge(it) }, { b.unregisterEvBatteryStateOfCharge(it) })
    }

    companion object {
        const val VEHICLE_PKG = "ai.pleos.playground.service.vehicle"
        const val VEHICLE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"
        private val ROW2 = listOf(SeatValue.ROW_2_LEFT, SeatValue.ROW_2_CENTER, SeatValue.ROW_2_RIGHT)
        private val SEATS = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT) + ROW2

        /** 차주에게 보여 줄 경고등 이름. 주차 브레이크·도어·안전벨트처럼 평소에도 켜지는 표시등과 TPMS(타이어 칸으로 따로 봄)는 뺀다. */
        val WARNING_LABELS = mapOf(
            TelltaleType.ENGINE to "엔진",
            TelltaleType.ENGINE_OIL_PRESSURE to "엔진오일 압력",
            TelltaleType.ENGINE_TEMPERATURE to "엔진 과열",
            TelltaleType.ABS to "ABS",
            TelltaleType.ESC to "차체자세제어(ESC)",
            TelltaleType.MDPS to "조향 보조(MDPS)",
            TelltaleType.AIRBAG to "에어백",
            TelltaleType.LOW_VOLT_BATTERY to "충전(12V 배터리)",
            TelltaleType.MASTER_WARNING to "주의",
            TelltaleType.EV_SYSTEM to "전기차 시스템",
            TelltaleType.HEV_SYSTEM to "하이브리드 시스템",
            TelltaleType.FCEV_SYSTEM to "수소차 시스템",
            TelltaleType.POWER_DOWN to "출력 제한",
            TelltaleType.RBS to "회생제동",
            TelltaleType.PARTICLE_FILTER to "배기 필터",
            TelltaleType.SCR to "요소수",
            TelltaleType.H2_LEAK to "수소 누출",
            TelltaleType.LOW_FUEL to "연료 부족",
            TelltaleType.LOW_EV_BATTERY to "배터리 부족",
        )

        private fun on(t: TelltaleData) = t.state == TelltaleState.ON || t.state == TelltaleState.BLINK

        /** Pleos 경고등 목록 → Signals.warnings. */
        fun warnings(list: List<TelltaleData>): Set<String> = list.filter(::on).mapNotNull { WARNING_LABELS[it.type] }.toSet()

        fun tpmsOn(list: List<TelltaleData>) = list.any { it.type == TelltaleType.TPMS && on(it) }

        /** 엔진오일 수준 → 부족 여부. ERROR·null은 모름(null)이라 값을 바꾸지 않는다. */
        fun oilLow(o: OilLevelState?): Boolean? = when (o) {
            OilLevelState.LOW, OilLevelState.CRITICALLY_LOW -> true
            OilLevelState.NORMAL, OilLevelState.HIGH -> false
            else -> null
        }

        /** Pleos 서비스가 설치된 차인지. SDK 매니페스트의 queries 덕에 패키지가 보인다. 폰·Robolectric에서는 비어 있다. */
        fun available(ctx: Context, action: String, pkg: String) =
            runCatching { ctx.packageManager.queryIntentServices(Intent(action).setPackage(pkg), 0).isNotEmpty() }.getOrDefault(false)
    }
}
