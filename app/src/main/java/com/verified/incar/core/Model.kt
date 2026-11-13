package com.verified.incar.core

enum class Gear { P, R, N, D }

enum class Place(val label: String) { HOME("집"), MART("마트"), HOSPITAL("병원"), SCHOOL("학교"), OTHER("그 밖의 곳") }

enum class Wiper { OFF, AUTO, LOW }

enum class Turn { OFF, LEFT, RIGHT }

/** 시트 자세 벡터 축: 앞뒤, 높이, 등받이, 쿠션, 헤드레스트 (0..1 정규화). */
object Postures {
    val OWNER = listOf(0.42f, 0.55f, 0.30f, 0.50f, 0.60f)
    val SPOUSE = listOf(0.25f, 0.70f, 0.22f, 0.45f, 0.48f)
    val AMBIGUOUS = listOf(0.55f, 0.42f, 0.48f, 0.40f, 0.70f)
    val STRANGER = listOf(0.85f, 0.15f, 0.70f, 0.20f, 0.90f)

    /**
     * RMS 거리. 실차는 시트 축을 3개만 줄 수 있어 양쪽에 다 있는 앞쪽 축끼리만 비교한다.
     * 등록하지 않은 자세(빈 목록)는 비교할 수 없어 최대 거리를 준다.
     */
    fun distance(a: List<Float>, b: List<Float>): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 1f
        return kotlin.math.sqrt((0 until n).sumOf { i -> ((a[i] - b[i]) * (a[i] - b[i])).toDouble() } / n).toFloat()
    }
}

/** 차량에서 읽는 신호 한 장면. 실차는 CarSignalSource, 시연은 SimSignalSource가 채운다. */
data class Signals(
    val gear: Gear = Gear.P,
    val speedKmh: Float = 0f,
    val ignitionOn: Boolean = true,
    val place: Place = Place.HOME,
    val gpsLost: Boolean = false,
    val driverSeated: Boolean = true,
    val driverDoorOpen: Boolean = false,
    val posture: List<Float> = Postures.OWNER,
    val passengerSeated: Boolean = false,
    val passengerBelt: Boolean = false,
    val row2Seated: Boolean = false,
    val row2Belt: Boolean = false,
    val row2RightDoorOpen: Boolean = false,
    val row2LeftDoorOpen: Boolean = false,
    val row2Reclined: Boolean = false,
    val trunkOpen: Boolean = false,
    val windowOpen: Boolean = false,
    val steeringDeg: Float = 0f,
    val socPct: Int = 80,
    val odometerKm: Double = 14820.4,
    val doorsLocked: Boolean = false,
    val hvacTempC: Float = 21f,
    val hvacPowerOn: Boolean = true,
    val fanSpeed: Int = 2,
    val details: VehicleDetails = VehicleDetails(),
    val defrosterOn: Boolean = false,
    val recircOn: Boolean = false,
    val acOn: Boolean = true,
    val wiper: Wiper = Wiper.OFF,
    val turnSignal: Turn = Turn.OFF,
    val lowBeamOn: Boolean = false,
    val cruiseOverride: Boolean = false,
    val ambientByDriveMode: Boolean = false,
    val washerLow: Boolean = false,
    /** 순정 안전하차보조(SEA)·후측방 경고가 울리는 중. 켜지면 앱 조명은 무조건 양보한다. */
    val seaWarning: Boolean = false,
    /** 순정 내비·통화·경고음이 오디오 포커스를 가져간 상태. */
    val audioFocusLost: Boolean = false,
    /** 열선·통풍 단계 0(꺼짐)~3. */
    val seatHeatDriver: Int = 0,
    val seatHeatPassenger: Int = 0,
    val seatHeatRear: Int = 0,
    val seatVentDriver: Int = 0,
    val steeringHeat: Boolean = false,
    val interiorLightOn: Boolean = false,
    val tirePressureLow: Boolean = false,
    /** 바퀴별 공기압 kPa(앞왼·앞오·뒤왼·뒤오). 네 바퀴를 모두 읽기 전에는 비어 있다. */
    val tireKpa: List<Float> = emptyList(),
    val outsideTempC: Float = 15f,
    val hazardOn: Boolean = false,
    /** 지자체 주정차 단속 구역 안(연동 목업: 시뮬레이터에서 넣는다). */
    val enforcementZone: Boolean = false,
    /** 시스템 CarUxRestrictions가 운전 방해 최적화를 요구하는 중. */
    val systemUxRestricted: Boolean = false,
    /** 차량이 알려 준 모델명(INFO_MODEL). 실차에서 차종 자동 인식에 쓴다. */
    val modelName: String = "",
    /** 신호 이름 → 차에서 마지막으로 읽은 시각(판정 시계 ms). 시뮬레이터 값에는 없다. 확인 근거 카드·제어 판정에 쓴다. */
    val seenAt: Map<String, Long> = emptyMap(),
    /** 켜진 경고등·점검 표시 이름(예: "엔진", "브레이크", "엔진오일", "타이어 저압"). Pleos Vehicle SDK Safety 도메인이 채운다. */
    val warnings: Set<String> = emptySet(),
    /** 엔진오일 부족 표시(Pleos Safety). */
    val oilLow: Boolean = false,
    /** 마지막 GPS 좌표(PlaceTracker). 좌표는 차 밖으로 보내지 않고 거리 계산에만 쓴다. */
    val lat: Double? = null,
    val lon: Double? = null,
    /** GPS가 보고한 수평 오차 반경(m). 값이 없으면 실제 정비소 이탈을 확정하지 않는다. */
    val gpsAccuracyM: Float? = null,
    /** 현대 내비(Pleos NaviHelper) 안내 중인 목적지 이름과 남은 거리·시간. 안내가 없으면 null. */
    val naviDest: String? = null,
    val naviRemainKm: Float? = null,
    val naviRemainMin: Int? = null,
) {
    val parked get() = gear == Gear.P && speedKmh == 0f
    /** 시스템 주행 제한을 우선하고, 기어·속도로도 한 번 더 막는다. */
    val uxRestricted get() = systemUxRestricted || gear != Gear.P || speedKmh > 0f
}

/** 차종이 실제로 제공하는 신호/제어. 없으면 대체 판정으로 내려간다. */
data class Capabilities(
    val row2Occupancy: Boolean = true,
    val row2Recline: Boolean = true,
    val seatPosture: Boolean = true,
    val locationWithoutRoute: Boolean = true,
    val ambientControl: Boolean = true,
    val seatControl: Boolean = true,
    val climateControl: Boolean = true,
    val seatHeat: Boolean = true,
    val seatVent: Boolean = true,
    val steeringHeat: Boolean = true,
    /** 차속(위험 권한)을 읽을 수 있는지. 못 읽으면 출발을 기어로 대신 본다. */
    val speed: Boolean = true,
)

/**
 * 앱을 설치할 수 있는 Pleos Connect 탑재 차종 (2026-11-02 공개 보도 기준).
 * 열선·통풍 같은 편의 사양은 트림마다 달라서 여기 적지 않고 차량 신호(Capabilities)로 확인한다.
 */
enum class CarModel(val label: String, val since: String, val display: String, val aliases: List<String>, val controllable: Boolean = true, val ev: Boolean = false) {
    GRANDEUR("더 뉴 그랜저", "2026년 5월 출시 · 10월 OTA", "17인치", listOf("그랜저", "GRANDEUR", "AZERA")),
    AVANTE("디 올 뉴 아반떼", "2026년 6월 공개 · 3분기 판매", "14.6 / 12.9인치", listOf("아반떼", "AVANTE", "ELANTRA")),
    TUCSON("디 올 뉴 투싼", "2026년 10월 계약 시작", "-", listOf("투싼", "TUCSON")),
    IONIQ3("아이오닉 3", "전기차 · 출시 일정 확인 필요", "-", listOf("아이오닉 3", "아이오닉3", "IONIQ 3", "IONIQ3"), ev = true),
    /** 제어가 확인되지 않은 차. 판정·안내·기록 같은 주행 기능만 쓴다. */
    OTHER("그 밖의 차량", "앱 제어 미지원 · 주행 기능만", "-", emptyList(), controllable = false),
    ;

    companion object {
        fun detect(modelName: String): CarModel? = entries.firstOrNull { m -> m.aliases.any { modelName.uppercase().contains(it.uppercase()) } }
    }
}

/** ⑩ 출발 준비 설정 한 벌. 온도는 섭씨, 열선·통풍은 0~3단. */
data class ComfortPreset(
    val title: String,
    val tempC: Float,
    val driverHeat: Int = 0,
    val passengerHeat: Int = 0,
    val rearHeat: Int = 0,
    val driverVent: Int = 0,
    val steeringHeat: Boolean = false,
    val defrost: Boolean = false,
) {
    val summary get() = buildList {
        add("%.0f℃".format(tempC))
        if (driverHeat > 0) add("운전석 열선 ${driverHeat}단")
        if (passengerHeat > 0) add("조수석 열선 ${passengerHeat}단")
        if (rearHeat > 0) add("뒷좌석 열선 ${rearHeat}단")
        if (driverVent > 0) add("운전석 통풍 ${driverVent}단")
        if (steeringHeat) add("핸들 열선")
        if (defrost) add("성에 제거")
    }.joinToString(" · ")

    companion object {
        val WARM = ComfortPreset("따뜻하게", 24f, driverHeat = 2, passengerHeat = 2, steeringHeat = true, defrost = true)
        val COOL = ComfortPreset("시원하게", 20f, driverVent = 2)
        val SEAT = ComfortPreset("좌석만 따뜻하게", 22f, driverHeat = 3)
        val PRESETS = listOf(WARM, COOL, SEAT)
    }
}

/** 출발 시각 예약. days는 1(월)~7(일). */
data class DepartureSchedule(val enabled: Boolean = false, val hour: Int = 7, val minute: Int = 30, val days: Set<Int> = setOf(1, 2, 3, 4, 5), val leadMin: Int = 10)

enum class Module(val no: String, val title: String) {
    VALET("①", "대리 안심 주차"),
    DRIVER("②", "운전자 범위 지킴이"),
    TRIP("③", "업무 운행기록부"),
    ESCORT("④", "부축 동행 + 하차 빛 안내"),
    SCHOOL("⑤", "스쿨존 내려주고 천천히"),
    CHAUFFEUR("⑥", "뒷좌석 승차감 코치"),
    TRUNK("⑦", "트렁크·하차 점검"),
    DIAG("⑧", "\"이거 고장이야?\" 확인 도우미"),
    MISSION("⑨", "첫 운전 확인"),
    COMFORT("⑩", "출발 준비 (미리 공조·열선)"),
}

/** 페르소나별 가지치기: 첫 실행에 고른 생활 유형의 모듈만 홈에 올린다. */
enum class Persona(val label: String, val modules: Set<Module>) {
    NIGHT_OUT("대리운전을 자주 불러요", setOf(Module.VALET, Module.DRIVER, Module.DIAG)),
    FAMILY_SHARE("가족과 차를 같이 써요", setOf(Module.DRIVER, Module.TRUNK, Module.DIAG)),
    PARENT_CARE("부모님을 모시고 다녀요", setOf(Module.ESCORT, Module.CHAUFFEUR)),
    SCHOOL_RUN("아이 등하교를 시켜요", setOf(Module.SCHOOL, Module.TRUNK)),
    BUSINESS("업무용으로 써요(사업자)", setOf(Module.TRIP, Module.DRIVER)),
    NEW_DRIVER("이 차가 처음이에요", setOf(Module.MISSION, Module.DIAG)),
}

enum class Flag(val label: String) {
    CONTROL_RESTORE("시트·공조 복원 (승인 필요)"),
    AMBIENT_GUIDE("앰비언트 하차 안내 (승인 필요)"),
    TTS("음성 안내 (승인 필요)"),
    CLIMATE("공조·열선 제어 (승인 필요)"),
    REMOTE("마이현대 원격 공조 연동 (승인 필요)"),
}

enum class Consent(val label: String) { OWNER("차주"), VALET_DRIVER("대리기사"), GUARDIAN("보호자"), CHAUFFEUR("쇼퍼 기사") }

data class DriverProfile(val name: String, val posture: List<Float>, val insured: Boolean)

data class Settings(
    val personas: Set<Persona> = emptySet(),
    val extraModules: Set<Module> = emptySet(),
    val flags: Set<Flag> = setOf(Flag.TTS),
    val consents: Set<Consent> = setOf(Consent.OWNER),
    val profiles: List<DriverProfile> = listOf(
        DriverProfile("차주", Postures.OWNER, insured = true),
        DriverProfile("배우자", Postures.SPOUSE, insured = true),
    ),
    val insuranceScope: String = "부부 한정",
    /** yyyy-MM-dd. 사용자가 입력한 값이며 보험사 DB와 연동되지 않는다. */
    val policyEndDate: String = "2027-03-31",
    val businessMode: Boolean = true,
    val carModel: CarModel? = null,
    /** 운전자 이름 → 그 사람의 출발 준비 설정. ②가 알아본 운전자에 맞춰 적용한다. */
    val comfort: Map<String, ComfortPreset> = mapOf("차주" to ComfortPreset.WARM, "배우자" to ComfortPreset.SEAT),
    val schedule: DepartureSchedule = DepartureSchedule(),
    val autoApplyOnBoard: Boolean = true,
    /** 등록 장소 좌표와 판정 반경. 차 안에만 저장한다. [제안값] 반경 100m(대단지 입구~주차동 간격 고려). */
    val pins: Map<Place, Pin> = emptyMap(),
    val pinRadiusM: Int = 100,
    /** 사람별 동의 시각(epoch ms). 분쟁 때 '언제 동의했는지'를 보여 주기 위해 남긴다. */
    val consentAt: Map<Consent, Long> = emptyMap(),
    /** 장소별 '층당 램프 회전 수'. 사용자가 층을 확정할 때마다 학습한다. */
    val rampTurnsPerFloor: Map<Place, Float> = emptyMap(),
    // ---- 마이현대 기능
    val serviceLog: List<ServiceRecord> = emptyList(),
    val bookings: List<Booking> = emptyList(),
    val sharedKeys: List<SharedKey> = emptyList(),
    val enforcementAlert: Boolean = true,
    /** 블루멤버스 보유 포인트(연동 목업). ⑤ 스쿨존 적립분은 따로 더한다. */
    val memberPoints: Int = 0,
    /** 차주 PIN의 PBKDF2 해시와 소금. PIN 자체는 저장하지 않는다. */
    val ownerPinHash: String? = null,
    val ownerPinSalt: String? = null,
    /** PIN을 잊었을 때 쓰는 12자리 복구 코드의 해시(같은 소금). */
    val ownerRecoveryHash: String? = null,
) {
    val onboarded get() = personas.isNotEmpty()
    val modules: Set<Module> get() = personas.flatMap { it.modules }.toSet() + extraModules
}

data class Snapshot(val timeMs: Long, val socPct: Int, val odometerKm: Double, val windowOpen: Boolean, val steeringDeg: Float)

enum class Level { INFO, OK, WARN, DANGER }

sealed interface Effect {
    /** 차에 남길 알림. 연결을 설정한 차주에게는 런타임이 사건 종류만 전송한다. */
    data class Notify(val target: String, val title: String, val body: String, val level: Level) : Effect
    data class Speak(val text: String) : Effect
    data class Toast(val text: String) : Effect
    /** 시트·공조를 차주 설정으로 되돌리라는 제어 요청. 승인된 차종에서만 나온다. */
    data object RestoreSeat : Effect
    /** 차 안에서 공조·열선을 바로 바꾸는 제어 요청 (탑승 직후). */
    data class ApplyClimate(val preset: ComfortPreset) : Effect
    /** 시동이 꺼진 차를 미리 데우거나 식히는 원격 요청. 차량 화면이 꺼져 있으므로 마이현대 원격 공조로 넘긴다(연동 목업). */
    data class RemoteClimate(val preset: ComfortPreset, val minutes: Int) : Effect
    /** 마이현대 '컨트롤'을 차 안에서 실행하는 제어 요청. 앱 제어 지원 차에서만 나온다. */
    data class Control(val kind: ControlKind) : Effect
    /** ① 대리 시작: 현대 내비(Pleos NaviHelper)에 등록한 집 핀으로 길 안내를 맡긴다. 좌표는 차 안 내비로만 넘긴다. */
    data class NaviHome(val home: Pin) : Effect
}

data class AmbientCue(val left: Level?, val right: Level?, val note: String)
