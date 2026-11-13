package com.verified.incar.vehicle

import com.verified.incar.core.DetailChannel
import com.verified.incar.core.VehicleDetails
import com.verified.incar.core.recordArea

import android.car.Car
import android.car.VehicleAreaWheel
import android.car.VehicleGear
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.verified.incar.core.Capabilities
import com.verified.incar.core.Gear
import com.verified.incar.core.Signals
import com.verified.incar.core.Turn
import com.verified.incar.core.Wiper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

/** 그룹 구독과 별도로, 판단에 필요한 영역이 실제로 수신됐을 때만 시각을 남긴다. */
internal fun Signals.areaReading(key: String, known: Boolean, atMs: Long) =
    copy(seenAt = if (known) seenAt + (key to atMs) else seenAt - key)

/**
 * AAOS CarPropertyManager 신호를 Signals 흐름에 덮어쓴다.
 * 구독이 거부된 신호(권한·차종 미지원)는 시뮬레이터 패널 값이 그대로 남고, Capabilities에서 '미지원'으로 표시된다.
 * Pleos Vehicle SDK(앰비언트·시트 제어 등)는 승인 후 같은 방식으로 붙인다.
 */
class CarBridge(
    private val ctx: Context,
    private val signals: MutableStateFlow<Signals>,
    /** 판정 시계(Runtime.now). 신호를 읽은 시각을 Signals.seenAt에 남긴다. */
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    var connected = false
        private set
    var caps = Capabilities()
        private set
    /** 실차에서 받아 오는 신호 이름. 화면에 '실차' 표시를 붙이는 데 쓴다. */
    val live = mutableSetOf<String>()

    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private var uxManager: CarUxRestrictionsManager? = null
    private val callbacks = mutableListOf<CarPropertyManager.CarPropertyEventCallback>()
    /** 직접 조회(refresh)용: 신호 이름 → 속성과 적용 함수. */
    private val readers = mutableMapOf<String, Pair<Int, (Signals, CarPropertyValue<*>) -> Signals>>()
    private val lastRefresh = mutableMapOf<String, Long>()
    private val occupied = mutableMapOf<Int, Boolean>()
    private val belted = mutableMapOf<Int, Boolean>()
    private val doorOpen = mutableMapOf<Int, Boolean>()
    private val doorLocked = mutableMapOf<Int, Boolean>()
    private val windowOpen = mutableMapOf<Int, Boolean>()
    private val postureAxes = mutableMapOf<Int, Float>()
    /** 재생 중인 시연 시나리오 수. 0보다 크면 차 신호를 넣지 않는다(콜백·코루틴 모두 메인 스레드). */
    var paused = 0
    private var grantedAtConnect = emptySet<String>()
    private val main = Handler(Looper.getMainLooper())
    private val hazardOff = Runnable { signals.update { it.copy(hazardOn = false) } }

    private fun granted() = RUNTIME_PERMISSIONS.filter { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }.toSet()

    /**
     * 연결한 뒤 차 권한을 새로 받았으면 구독을 다시 건다. 권한 없이 실패한 구독은 나중에 허용돼도 살아나지 않고,
     * 옛 CarPropertyManager는 권한으로 거른 속성 목록을 들고 있어 Car를 새로 만든다. 다시 연결했으면 true.
     */
    fun resubscribeIfGranted(): Boolean {
        if (!connected || granted() == grantedAtConnect) return false
        disconnect()
        return connect()
    }

    fun connect(): Boolean {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return false
        val c = runCatching { Car.createCar(ctx) }.getOrNull() ?: return false
        val m = runCatching { c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager }.getOrNull() ?: return false
        car = c
        pm = m
        grantedAtConnect = granted()
        // 주행 중 화면 제한은 시스템이 정한 CarUxRestrictions를 따른다
        runCatching {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            fun apply(r: CarUxRestrictions?) = signals.update { it.copy(systemUxRestricted = r?.isRequiresDistractionOptimization == true) }
            apply(ux.currentCarUxRestrictions)
            ux.registerListener { apply(it) }
            uxManager = ux
            live += "uxRestrictions"
        }
        sub("gear", VehiclePropertyIds.GEAR_SELECTION) { s, v -> gearOf(v.value as Int)?.let { s.copy(gear = it) } ?: s }
        val speedOk = sub("speed", VehiclePropertyIds.PERF_VEHICLE_SPEED, 2f) { s, v -> s.copy(speedKmh = abs(v.value as Float) * 3.6f) }
        // VehicleIgnitionState: ON=4, START=5
        sub("ignition", VehiclePropertyIds.IGNITION_STATE) { s, v -> s.copy(ignitionOn = (v.value as Int) >= 4) }
        sub("steering", VehiclePropertyIds.PERF_STEERING_ANGLE, 1f) { s, v -> s.copy(steeringDeg = v.value as Float) }
        // 첫 변경 이벤트 전까지 시뮬레이터 기본값이 주행거리로 쓰이지 않게 한 번 읽어 둔다
        runCatching { m.getFloatProperty(VehiclePropertyIds.PERF_ODOMETER, 0) }.onSuccess { km -> signals.update { it.copy(odometerKm = km.toDouble()) } }
        sub("odometer", VehiclePropertyIds.PERF_ODOMETER, 1f) { s, v -> s.copy(odometerKm = (v.value as Float).toDouble()) }
        // 하이브리드도 EV 용량을 주므로 연료 탱크가 있으면 연료 잔량을 쓴다(구동 배터리를 '배터리·연료'로 보지 않게)
        val fuelMl = runCatching { m.getFloatProperty(VehiclePropertyIds.INFO_FUEL_CAPACITY, 0) }.getOrDefault(0f)
        // shortcut: 전기차 현재 용량(노화 반영)은 연결 때 한 번만 읽음, 전기차 차종이 생기면 구독으로
        val evWh = runCatching { m.getFloatProperty(VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY, 0) }.getOrDefault(0f).takeIf { it > 0f }
            ?: runCatching { m.getFloatProperty(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, 0) }.getOrDefault(0f)
        val (socId, socCap) = if (fuelMl > 0f) VehiclePropertyIds.FUEL_LEVEL to fuelMl else VehiclePropertyIds.EV_BATTERY_LEVEL to evWh
        if (socCap > 0f) sub("soc", socId, 1f) { s, v ->
            s.copy(socPct = ((v.value as Float) / socCap * 100).toInt().coerceIn(0, 100))
        }
        // DOOR_POS 영역: VHAL VehicleAreaDoor ROW_1_LEFT=0x1, ROW_2_LEFT=0x10, ROW_2_RIGHT=0x40, REAR=0x20000000
        sub("doors", VehiclePropertyIds.DOOR_POS) { s, v ->
            doorOpen[v.areaId] = (v.value as Int) > 0
            s.copy(
                driverDoorOpen = doorOpen[0x1] == true,
                row2LeftDoorOpen = doorOpen[0x10] == true,
                row2RightDoorOpen = doorOpen[0x40] == true,
                trunkOpen = doorOpen[0x20000000] == true,
            ).recordArea(DetailChannel.DOOR, v.areaId, doorOpen[v.areaId], clock())
                .let { if (v.areaId == 0x20000000) stamp(it, "trunk") else it }
        }
        val lockAreas = runCatching { m.getCarPropertyConfig(VehiclePropertyIds.DOOR_LOCK)?.areaIds?.toSet() }.getOrNull().orEmpty()
        sub("lock", VehiclePropertyIds.DOOR_LOCK) { s, v ->
            doorLocked[v.areaId] = v.value as Boolean
            val locked = doorLocked.values.all { it }
            s.copy(doorsLocked = locked).recordArea(DetailChannel.LOCK, v.areaId, doorLocked[v.areaId], clock())
                .areaReading("lockStatus", !locked || (lockAreas.isNotEmpty() && doorLocked.keys.containsAll(lockAreas)), clock())
        }
        val windowAreas = runCatching { m.getCarPropertyConfig(VehiclePropertyIds.WINDOW_POS)?.areaIds?.toSet() }.getOrNull().orEmpty()
        sub("window", VehiclePropertyIds.WINDOW_POS) { s, v ->
            windowOpen[v.areaId] = (v.value as Int) > 0
            val open = windowOpen.values.any { it }
            s.copy(windowOpen = open).areaReading("windowStatus", open || (windowAreas.isNotEmpty() && windowOpen.keys.containsAll(windowAreas)), clock())
        }
        // VehicleAreaSeat: ROW_1_LEFT=0x1, ROW_1_RIGHT=0x4, ROW_2_*=0x10/0x20/0x40
        val row2 = setOf(0x10, 0x20, 0x40)
        val occ = sub("row2Occupancy", VehiclePropertyIds.SEAT_OCCUPANCY) { s, v ->
            // VehicleSeatOccupancyState: UNKNOWN=0, VACANT=1, OCCUPIED=2. 모름은 '비었음'으로 보지 않고 이전 값을 둔다
            occupied[v.areaId] = when (v.value as Int) {
                2 -> true
                1 -> false
                else -> return@sub s.recordArea(DetailChannel.OCCUPANCY, v.areaId, null, clock())
                    .let { if (v.areaId == 0x1) it.areaReading("driverSeat", false, clock()) else it }
            }
            s.copy(
                driverSeated = occupied[0x1] ?: s.driverSeated,
                passengerSeated = occupied[0x4] == true,
                row2Seated = row2.any { occupied[it] == true },
            ).recordArea(DetailChannel.OCCUPANCY, v.areaId, occupied[v.areaId], clock())
                .let { if (v.areaId == 0x1) stamp(it, "driverSeat") else it }
        }
        sub("belt", VehiclePropertyIds.SEAT_BELT_BUCKLED) { s, v ->
            belted[v.areaId] = v.value as Boolean
            s.copy(passengerBelt = belted[0x4] == true, row2Belt = row2.any { belted[it] == true })
                .recordArea(DetailChannel.BELT, v.areaId, belted[v.areaId], clock())
        }
        val posture = listOf(VehiclePropertyIds.SEAT_FORE_AFT_POS, VehiclePropertyIds.SEAT_HEIGHT_POS, VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS)
        val postureOk = posture.map { id ->
            sub("posture", id) { s, v ->
                if (v.areaId != 0x1) return@sub s
                postureAxes[id] = normalize(id, v)
                s.copy(posture = posture.map { postureAxes[it] ?: 0.5f })
            }
        }.all { it }
        val recline = sub("row2Recline", VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS) { s, v ->
            if (v.areaId in row2) s.copy(row2Reclined = normalize(VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS, v) > 0.5f) else s
        }
        sub("hvacTemp", VehiclePropertyIds.HVAC_TEMPERATURE_SET) { s, v -> s.copy(hvacTempC = v.value as Float) }
        sub("hvacPower", VehiclePropertyIds.HVAC_POWER_ON) { s, v -> s.copy(hvacPowerOn = v.value as Boolean) }
        sub("fanSpeed", VehiclePropertyIds.HVAC_FAN_SPEED) { s, v -> s.copy(fanSpeed = v.value as Int) }
        sub("defroster", VehiclePropertyIds.HVAC_DEFROSTER) { s, v -> s.copy(defrosterOn = v.value as Boolean) }
        sub("recirc", VehiclePropertyIds.HVAC_RECIRC_ON) { s, v -> s.copy(recircOn = v.value as Boolean) }
        sub("ac", VehiclePropertyIds.HVAC_AC_ON) { s, v -> s.copy(acOn = v.value as Boolean) }
        // WindshieldWipersSwitch: OFF=1, AUTO=13
        sub("wiper", VehiclePropertyIds.WINDSHIELD_WIPERS_SWITCH) { s, v ->
            s.copy(wiper = when (v.value as Int) { 1 -> Wiper.OFF; 13 -> Wiper.AUTO; else -> Wiper.LOW })
        }
        // VehicleTurnSignal: RIGHT=1, LEFT=2. STATE는 서명 권한이라, 안 되면 레버 상태(SWITCH, Android 16+ 위험 권한)로 본다
        val turn: (Signals, CarPropertyValue<*>) -> Signals = { s, v ->
            s.copy(turnSignal = when (v.value as Int) { 2 -> Turn.LEFT; 1 -> Turn.RIGHT; else -> Turn.OFF })
        }
        sub("turn", VehiclePropertyIds.TURN_SIGNAL_STATE, apply = turn) || sub("turn", VehiclePropertyIds.TURN_SIGNAL_SWITCH, apply = turn)
        sub("lowBeam", VehiclePropertyIds.HEADLIGHTS_STATE) { s, v -> s.copy(lowBeamOn = (v.value as Int) == 1) }
        // 비상등: 고장·사고 때 대피 안내(비트박스)를 여는 신호. VehicleLightState ON=1
        val hazard = sub("hazard", VehiclePropertyIds.HAZARD_LIGHTS_STATE) { s, v -> s.copy(hazardOn = (v.value as Int) == 1) }
        // 3P 앱은 HAZARD_LIGHTS_STATE(서명 권한)를 못 읽어 양쪽 깜빡이 빛(LEFT|RIGHT=3)으로 본다(Android 16+, 위험 권한)
        if (!hazard) sub("hazard", VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE) { s, v ->
            main.removeCallbacks(hazardOff)
            // 깜빡임 사이 꺼짐마다 대피 안내가 닫혔다 다시 말하지 않게, 꺼진 뒤 2초 동안 다시 안 켜질 때만 끈다
            // shortcut: 문 잠금 확인 깜빡임도 잠깐 비상등으로 보일 수 있음, 실차 확인 후 2회 이상 깜빡임 조건 검토
            if (((v.value as Int) and 3) == 3) s.copy(hazardOn = true) else { if (s.hazardOn) main.postDelayed(hazardOff, 2_000); s }
        }
        // CruiseControlState: USER_OVERRIDE=3, SUSPENDED=4
        sub("cruise", VehiclePropertyIds.CRUISE_CONTROL_STATE) { s, v -> s.copy(cruiseOverride = (v.value as Int) in 3..4) }

        runCatching { m.getProperty(String::class.java, VehiclePropertyIds.INFO_MODEL, 0).value }.getOrNull()?.let { name ->
            signals.update { it.copy(modelName = name) }
        }
        // HVAC_SEAT_TEMPERATURE: 양수 = 열선 단계, 음수 = 통풍 단계 (영역별)
        val seatTemp = sub("seatHeat", VehiclePropertyIds.HVAC_SEAT_TEMPERATURE) { s, v ->
            val lv = v.value as Int
            when (v.areaId) {
                0x1 -> s.copy(seatHeatDriver = lv.coerceAtLeast(0), seatVentDriver = (-lv).coerceAtLeast(0))
                0x4 -> s.copy(seatHeatPassenger = lv.coerceAtLeast(0))
                in row2 -> s.copy(seatHeatRear = lv.coerceAtLeast(0))
                else -> s
            }
        }
        val wheelHeat = sub("steeringHeat", VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT) { s, v -> s.copy(steeringHeat = (v.value as Int) > 0) }

        // 하차 점검·겨울 점검용 신호
        sub("interiorLight", VehiclePropertyIds.CABIN_LIGHTS_STATE) { s, v -> s.copy(interiorLightOn = (v.value as Int) == 1) }
        sub("outsideTemp", VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, 0.1f) { s, v -> s.copy(outsideTempC = v.value as Float) }
        val tireLow = mutableMapOf<Int, Boolean>()
        val tireKpa = FloatArray(4)
        val wheel = mapOf(
            VehicleAreaWheel.WHEEL_LEFT_FRONT to 0, VehicleAreaWheel.WHEEL_RIGHT_FRONT to 1,
            VehicleAreaWheel.WHEEL_LEFT_REAR to 2, VehicleAreaWheel.WHEEL_RIGHT_REAR to 3,
        )
        sub("tirePressure", VehiclePropertyIds.TIRE_PRESSURE, 0.1f) { s, v ->
            // 바퀴별 '매우 낮음' 기준값이 있으면 그것을, 없으면 200kPa [제안값]을 쓴다
            val critical = runCatching { pm?.getFloatProperty(VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE, v.areaId) }.getOrNull() ?: 200f
            tireLow[v.areaId] = (v.value as Float) < critical
            wheel[v.areaId]?.let { tireKpa[it] = v.value as Float }
            // 네 바퀴를 모두 읽은 뒤에만 바퀴별 값을 넘겨, 일부만 읽은 값이 누설로 보이지 않게 한다
            s.copy(tirePressureLow = tireLow.values.any { it }, tireKpa = if (tireKpa.all { it > 0f }) tireKpa.toList() else s.tireKpa)
        }

        // 착좌를 읽어도 2열 영역이 없으면(조수석 에어백용만 주는 차) 2열 착좌는 모르는 것으로 본다
        val occAreas = runCatching { m.getCarPropertyConfig(VehiclePropertyIds.SEAT_OCCUPANCY)?.areaIds }.getOrNull() ?: IntArray(0)
        caps = Capabilities(
            speed = speedOk,
            row2Occupancy = occ && occAreas.any { it in row2 },
            row2Recline = recline,
            seatPosture = postureOk,
            // 위치는 PlaceTracker가 AAOS LocationManager로 경로 없이 받는다. 첫 위치 전에는 Runtime이 '그 밖의 곳'으로 둔다
            locationWithoutRoute = true,
            // 앰비언트·시트 제어는 Pleos Vehicle SDK 승인 후 연결 [확인 필요]
            ambientControl = false,
            seatControl = false,
            // 읽기는 AAOS 표준 속성으로 하되, 켜고 끄는 제어는 Pleos Vehicle SDK Control 승인 전이라 막아 둔다
            climateControl = false,
            seatHeat = seatTemp,
            seatVent = seatTemp,
            steeringHeat = wheelHeat,
        )
        connected = true
        return true
    }

    fun disconnect() {
        callbacks.forEach { cb -> runCatching { pm?.unregisterCallback(cb) } }
        callbacks.clear()
        runCatching { uxManager?.unregisterListener() }
        runCatching { car?.disconnect() }
        main.removeCallbacks(hazardOff)
        live.clear()
        readers.clear()
        doorOpen.clear()
        doorLocked.clear()
        windowOpen.clear()
        occupied.clear()
        belted.clear()
        signals.update { it.copy(details = VehicleDetails()) }
        signals.update { it.copy(seenAt = it.seenAt - setOf("trunk", "lockStatus", "windowStatus", "driverSeat")) }
        connected = false
    }

    private fun normalize(id: Int, v: CarPropertyValue<*>): Float {
        val cfg = runCatching { pm?.getCarPropertyConfig(id) }.getOrNull()
        val min = (runCatching { cfg?.getMinValue(v.areaId) }.getOrNull() as? Int) ?: 0
        val max = (runCatching { cfg?.getMaxValue(v.areaId) }.getOrNull() as? Int) ?: 100
        return if (max == min) 0.5f else ((v.value as Int) - min).toFloat() / (max - min)
    }

    /** 구독 성공 여부를 돌려준다. 권한 거부·미지원 속성은 false. */
    private fun sub(name: String, id: Int, rate: Float = CarPropertyManager.SENSOR_RATE_ONCHANGE, apply: (Signals, CarPropertyValue<*>) -> Signals): Boolean {
        val m = pm ?: return false
        val detail = when (name) {
            "doors" -> DetailChannel.DOOR
            "lock" -> DetailChannel.LOCK
            "row2Occupancy" -> DetailChannel.OCCUPANCY
            "belt" -> DetailChannel.BELT
            else -> null
        }
        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (paused > 0) return
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) {
                    detail?.let { channel -> signals.update { it.recordArea(channel, value.areaId, null, clock()) } }
                    return
                }
                runCatching { signals.update { stamp(apply(it, value), name) } }
            }

            override fun onErrorEvent(propId: Int, zone: Int) {
                if (paused == 0) detail?.let { channel -> signals.update { it.recordArea(channel, zone, null, clock()) } }
            }
        }
        val ok = runCatching { m.getCarPropertyConfig(id) != null && m.registerCallback(cb, id, rate) }.getOrDefault(false)
        if (ok) {
            callbacks += cb
            live += name
            readers[name] = id to apply
        }
        return ok
    }

    private fun stamp(s: Signals, name: String) = s.copy(seenAt = s.seenAt + (name to clock()))

    /**
     * 확인 근거용 직접 조회: 구독으로 받은 값과 별개로 지금 값을 한 번 읽어 시각을 남긴다.
     * 차를 자꾸 깨우지 않도록 같은 신호는 [REFRESH_MIN_MS]에 한 번만 읽는다. 읽지 못하면 아무것도 바꾸지 않는다(결과 미확인).
     */
    fun refresh(name: String) {
        val m = pm ?: return
        val (id, apply) = readers[name] ?: return
        val t = clock()
        if (paused > 0 || lastRefresh[name]?.let { t - it < REFRESH_MIN_MS } == true) return
        lastRefresh[name] = t
        val areas = runCatching { m.getCarPropertyConfig(id)?.areaIds?.toList() }.getOrNull() ?: return
        for (area in areas.ifEmpty { listOf(0) }) {
            val v = runCatching { m.getProperty<Any>(id, area) }.getOrNull() ?: continue
            if (v.status != CarPropertyValue.STATUS_AVAILABLE) continue
            runCatching { signals.update { stamp(apply(it, v), name) } }
        }
    }

    companion object {
        /** 같은 신호를 직접 다시 읽는 최소 간격 [제안값]. */
        const val REFRESH_MIN_MS = 5_000L
        /** 실행 중 동의가 필요한 차 신호 위험 권한(MainActivity가 요청). _3P·READ_CAR_SEATS·READ_CAR_EXTERIOR_LIGHTS는 Android 16+ 차에만 있다. */
        val RUNTIME_PERMISSIONS = listOf(
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_ENERGY",
            "android.car.permission.CAR_MILEAGE_3P",
            "android.car.permission.CAR_TIRES_3P",
            "android.car.permission.READ_CAR_STEERING_3P",
            "android.car.permission.READ_CAR_SEATS",
            "android.car.permission.READ_CAR_EXTERIOR_LIGHTS",
        )
    }
}

/**
 * VHAL 기어 값. 구글 CarGearViewer 예제처럼 0(GEAR_UNKNOWN)은 모름으로 두고 직전 값을 지킨다
 * (D로 읽으면 주차 중인 차를 주행 중으로 판단한다). 수동 1~9단은 앞으로 가는 기어라 D로 본다.
 */
internal fun gearOf(value: Int): Gear? = when (value) {
    VehicleGear.GEAR_UNKNOWN -> null
    VehicleGear.GEAR_PARK -> Gear.P
    VehicleGear.GEAR_REVERSE -> Gear.R
    VehicleGear.GEAR_NEUTRAL -> Gear.N
    else -> Gear.D
}
