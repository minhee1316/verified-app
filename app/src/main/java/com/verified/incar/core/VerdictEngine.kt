package com.verified.incar.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

enum class ValetPhase(val label: String) {
    IDLE("꺼짐"), CONSENT("기사 동의 대기"), DRIVING("진행 중 · 집으로 운행"), PRELIM("완료 확인 중"),
    COMPLETE("완료 확인"), INCOMPLETE("확인 필요 · 목적지 밖 하차"), CLOSED_BY_OWNER("차주 직접 종료"),
}

data class ValetState(
    val phase: ValetPhase = ValetPhase.IDLE,
    val handover: Snapshot? = null,
    val result: Snapshot? = null,
    val prelimAtMs: Long? = null,
    val confirmedBy: String? = null,
    /** 자동 판정을 보류하는 이유. 위치 재수신·복구 뒤에도 저장한다. */
    val verificationPending: String? = null,
    val placeEstimated: Boolean = false,
    val restoreMsg: String? = null,
    /** 기사가 차를 움직였는지. 출발 전(키를 받으러 문을 여는 등)에는 완료·미완료를 판정하지 않는다. */
    val moved: Boolean = false,
    /** 확인 근거: 인수부터 판정까지 어떤 신호를 언제 읽었는지. */
    val proof: List<Proof> = emptyList(),
    /** 차가 쓰는 인수인계서: 동의 때(인수)와 완료·미완료 때(도착)의 차 상태. */
    val checkIn: CarCheck? = null,
    val checkOut: CarCheck? = null,
    /** 차주가 직접 확인한 현대 기능 체크리스트. */
    val prep: Set<PrepItem> = emptySet(),
    /** 현대 내비 기준 도착 예정 시각과 경로 이탈 판정(남은 거리 최저값, 늘어난 채 머문 시작 시각). */
    val naviEtaMs: Long? = null,
    val naviMinKm: Float? = null,
    val naviOffSinceMs: Long? = null,
    val naviOffWarned: Boolean = false,
) {
    val tracking get() = phase == ValetPhase.DRIVING || phase == ValetPhase.PRELIM || phase == ValetPhase.INCOMPLETE
    val active get() = phase != ValetPhase.IDLE && phase != ValetPhase.CLOSED_BY_OWNER
}

enum class DriverVerdict(val label: String) { NOT_CHECKED("확인 전"), KNOWN("등록 운전자"), ASK("오늘 운전자 확인 필요"), UNKNOWN("처음 보는 자세") }

data class DriverState(
    val verdict: DriverVerdict = DriverVerdict.NOT_CHECKED,
    val name: String = "-",
    val distance: Float = 0f,
    val notes: List<String> = emptyList(),
    val showNewDriverGuide: Boolean = false,
    /** 마지막으로 판정한 자세. 앉은 뒤 시트를 맞추면 이것과 비교해 다시 판정한다. */
    val judgedPosture: List<Float> = emptyList(),
)

data class TripLog(
    val date: String, val driver: String, val from: String, val to: String,
    val startOdo: Double, val endOdo: Double, val purpose: String, val business: Boolean,
) {
    val distKm get() = endOdo - startOdo
}

data class OpenTrip(val startMs: Long, val startOdo: Double, val from: Place, val driver: String)

data class TripState(val open: OpenTrip? = null, val pending: TripLog? = null, val logs: List<TripLog> = emptyList(), val suggestion: String? = null)

enum class EscortPhase(val label: String) {
    OFF("꺼짐"), BOARDING("승차 확인 중"), READY("출발 준비 완료"), RIDING("운행 중"),
    DEPARTED_UNVERIFIED("확인 없이 출발"), ALIGHT_GUIDE("하차 안내 중"), WRONG_SIDE("차도 쪽 문 열림"), ALIGHTED("하차 완료"),
}

data class TimelineItem(val timeMs: Long, val text: String)

/** [verified]: 문·착석·벨트를 확인하고 출발했는지, [rightOpened]·[wrongSide]: 이번 정차에서 보도 쪽·차도 쪽 문이 열렸는지. */
data class EscortState(
    val phase: EscortPhase = EscortPhase.OFF, val timeline: List<TimelineItem> = emptyList(), val harsh: Int = 0,
    val verified: Boolean = false, val rightOpened: Boolean = false, val wrongSide: Boolean = false,
)

enum class SchoolPhase(val label: String) { IDLE("대기"), WAIT_MOVE("아이 하차 · 출발 대기"), WATCH("천천히 출발 확인 중"), SUCCESS("천천히 출발 성공"), FAIL("서행 기준 초과") }

data class SchoolState(
    val phase: SchoolPhase = SchoolPhase.IDLE,
    val moveStartMs: Long? = null,
    val startOdo: Double = 0.0,
    val maxSpeed: Float = 0f,
    val streak: Int = 0,
    val points: Int = 0,
    val warned: Boolean = false,
)

data class ChauffeurState(val manual: Boolean = false, val active: Boolean = false, val driven: Boolean = false, val brakes: Int = 0, val accels: Int = 0)

data class TrunkState(val loaded: Boolean = false, val loadedAt: String? = null, val askConfirm: Boolean = false, val reminded: Boolean = false, val openedAtMs: Long? = null)

data class DiagFinding(val code: String, val title: String, val detail: String, val action: String? = null)

data class Mission(val id: String, val title: String, val guide: String, val done: Boolean = false, val elapsedSec: Long? = null)

data class MissionState(val active: Boolean = false, val startMs: Long = 0, val items: List<Mission> = MISSIONS)

val MISSIONS = listOf(
    Mission("M01", "와이퍼", "와이퍼를 한 번 작동해 보세요"),
    Mission("M02", "방향지시등", "왼쪽 방향지시등을 켜 보세요"),
    Mission("M03", "전조등", "전조등을 켜 보세요"),
    Mission("M04", "시트", "운전석을 앞으로 당겨 보세요"),
    Mission("M05", "공조", "온도를 1도 올려 보세요"),
    Mission("M06", "문 잠금", "문을 잠가 보세요"),
    Mission("M07", "성에 제거", "앞 유리 성에 제거를 켜 보세요"),
)

/** ⑦ 하차 3초 점검: 내리는 순간 놓치기 쉬운 것들. */
data class ExitCheck(val items: List<String> = emptyList(), val atMs: Long = 0, val unlockedSinceMs: Long? = null, val lockReminded: Boolean = false)

enum class NHoldPhase(val label: String) { OFF("꺼짐"), GUIDE("안내 중"), HOLDING("N단 유지 중"), DONE("P로 돌아옴"), FAILED("문을 열어 P로 바뀜") }

/** ⑨ N단 유지 도우미 (이중주차·자동세차). */
data class NHoldState(val phase: NHoldPhase = NHoldPhase.OFF, val offAtMs: Long? = null, val warnedLong: Boolean = false)

/** ⑦ 지하주차장 층 기억: GPS가 끊긴 뒤 램프 회전 수로 층을 추정한다. */
data class ParkFloor(
    val tracking: Boolean = false,
    val place: Place = Place.OTHER,
    val turns: Int = 0,
    val inTurn: Boolean = false,
    val estimate: Int? = null,
    val confirmed: Int? = null,
    /** 마지막으로 층을 추정할 때의 램프 회전 수. 정차 후 더 돌면 다시 추정한다. */
    val estTurns: Int = 0,
)

/** 차를 넘기는 경우: 가족·지인에게 빌려주기, 정비소·주차대행에 맡기기. 리포트 엔진은 같다. */
enum class HandKind(val label: String, val report: String) {
    LEND("빌려준 차", "빌려준 차 반납 리포트"),
    SERVICE("정비·주차대행 맡김", "맡긴 차 인수 리포트"),
}

/**
 * 보낸 원격 제어가 차에 실제로 반영됐는지 기다리는 중. [expect]는 반영되면 보일 신호 값(경적처럼 신호가 없으면 null).
 * 마이현대는 '요청 완료'만 보여 주고 실패를 늦게 알게 된다는 불만이 있어, 확인됨은 차 신호로 결과를 확인한다.
 */
data class PendingControl(val kind: ControlKind, val expect: Boolean, val atMs: Long, val unconfirmed: Boolean = false)

/** 확인 근거 카드의 결과. 반대 신호가 와야 '실패', 신호가 오지 않으면 '결과 미확인'으로 둔다. */
enum class ControlResult(val label: String, val level: Level) {
    WAITING("확인 중", Level.INFO), CONFIRMED("확인됨", Level.OK), LATE("늦게 확인됨", Level.OK),
    UNCONFIRMED("결과 미확인", Level.WARN), FAILED("실패", Level.DANGER), NO_SIGNAL("확인할 신호 없음", Level.INFO),
}

/** 제어 한 번의 확인 근거: 언제 보냈고, 어떤 신호를 언제 읽어 무엇으로 판정했는지. */
/**
 * 확인 근거 한 줄: 언제([atMs]) 무엇을([what]) 어디서 읽었는지([src]: '차 신호 23:41:05'·'GPS 23:41:03'·'확인 불가'·'시뮬레이터').
 * 차주가 그 자리에 없을 때(대리·맡김·빌려주기) 요약이 아니라 이 줄들이 분쟁 때 근거가 된다.
 */
data class Proof(val atMs: Long, val what: String, val src: String)

/** 차가 쓰는 인수인계서 한 장. 실차에서 읽지 못한 칸은 null(추정값으로 채우지 않는다). */
data class CarCheck(
    val atMs: Long, val odometerKm: Double?, val socPct: Int?, val warnings: Set<String>?,
    val tireLow: Boolean?, val washerLow: Boolean?, val oilLow: Boolean?,
    val windowOpen: Boolean?, val trunkOpen: Boolean?, val doorsLocked: Boolean?,
    val steeringDeg: Float? = null, val speedKmh: Float? = null,
) {
    fun distanceFrom(start: CarCheck?): Double? {
        val before = start?.odometerKm ?: return null
        return odometerKm?.minus(before)?.takeIf { it >= 0 }
    }
}

enum class PrepPhase(val label: String) { BEFORE("맡기기 전"), RETURN("돌려받을 때") }

/**
 * 맡길 때 챙길 현대 순정 기능. 발레 모드·빌트인 캠·디지털 키 2 상태는 다른 앱에 열려 있지 않아 읽을 수 없으므로,
 * 체크는 '차주가 직접 확인함' 기록이다. [done]은 확인 근거 줄에 쓰는 짧은 말.
 */
enum class PrepItem(val label: String, val phase: PrepPhase, val done: String) {
    VALET_MODE("현대 발레 모드 켜기(화면·개인정보 잠금, 운행 기록)", PrepPhase.BEFORE, "발레 모드 켬"),
    BUILTIN_CAM("빌트인 캠 주차 녹화 켜짐 확인", PrepPhase.BEFORE, "빌트인 캠 주차 녹화 켜짐"),
    KEY_EXPIRY("디지털 키 2 공유 기한을 돌려받을 시각에 맞추기", PrepPhase.BEFORE, "디지털 키 공유 기한 맞춤"),
    CAM_SAVE("빌트인 캠 영상 저장: 맡긴 시각~돌려받은 시각", PrepPhase.RETURN, "빌트인 캠 영상 저장"),
    KEY_REVOKE("디지털 키 2 공유 회수", PrepPhase.RETURN, "디지털 키 공유 회수"),
    VALET_OFF("발레 모드 해제 후 마이현대 운행 기록 확인", PrepPhase.RETURN, "발레 모드 해제·운행 기록 확인"),
}

/** 맡김 이력서 한 줄. [kind]: 대리·맡김·빌려주기. [km]는 주행거리를 못 읽었으면 null. */
data class HandoverRecord(
    val kind: String, val startMs: Long, val endMs: Long, val km: Double?, val maxSpeed: Float, val brakes: Int,
    val changes: List<String>, val ok: Boolean, val speedKnown: Boolean = true,
    /** 시간순 근거(인수·시동·문·급감속·반납), 차주 메모, 맡길 때 정한 반납 예정 시각. 예전 기록에는 없다. */
    val proof: List<Proof> = emptyList(), val memo: String = "", val dueMs: Long? = null,
) {
    val recordsDrivingStats get() = kind != "대리"

    /** 예전 버전의 대리 이력도 현재 기사 안내 범위로 맞춘다. */
    fun respectingValetConsent() = if (recordsDrivingStats) this else copy(
        maxSpeed = 0f, brakes = 0, speedKnown = false,
        changes = changes.filterNot { it.startsWith("정차 중 시동") || "급감속" in it || "최고 속도" in it || "km/h 이상 주행" in it },
    )
}

data class ControlEvidence(val kind: ControlKind, val sentAtMs: Long, val steps: List<Pair<Long, String>>, val result: ControlResult)

/** 고장·사고 때 비상등을 켜면 띄우는 대피 안내(비트박스). */
data class EvacState(val active: Boolean = false, val atMs: Long = 0)

/** ② 빌려준 차·맡긴 차 리포트: 점수 없이 사실만. [starts]·[doorOpens]는 넘긴 동안 시동을 켠 횟수와 운전석 문을 연 횟수. */
data class LendState(
    val active: Boolean = false,
    val start: Snapshot? = null,
    val brakes: Int = 0,
    val maxSpeed: Float = 0f,
    val report: List<Pair<String, String>> = emptyList(),
    val kind: HandKind = HandKind.LEND,
    val starts: Int = 0,
    val doorOpens: Int = 0,
    /** 빌려준 동안 차주가 아닌 사람이 앉은 적이 있는지. 있어야 차주 자세가 돌아왔을 때 반납으로 본다. */
    val otherSeen: Boolean = false,
    /** 마지막으로 센 시동이 아직 켜져 있고 차가 움직이지 않았는지. 찾으러 온 차주가 켠 시동을 리포트에서 빼는 데 쓴다. */
    val idleStart: Boolean = false,
    /** 리포트를 만든 시각. 피드에는 [MyHyundai.REPORT_FEED_MS] 동안만 올린다(0이면 예전 저장분). */
    val endedAtMs: Long = 0,
    /** 확인 근거: 인수·시동·문·급감속·반납을 어떤 신호로 언제 읽었는지(최근 [PROOF_MAX]줄). */
    val proof: List<Proof> = emptyList(),
    val checkIn: CarCheck? = null,
    val checkOut: CarCheck? = null,
    val prep: Set<PrepItem> = emptySet(),
    /** 맡긴 곳 좌표(정비소 이탈 판정용, 차 밖으로 보내지 않음)와 이탈 알림을 보냈는지. */
    val startLat: Double? = null,
    val startLon: Double? = null,
    val startAccuracyM: Float? = null,
    val roamWarned: Boolean = false,
    /** 연속된 새 위치 관측으로만 유지하는 이탈 후보. 재시작하면 다시 확인한다. */
    val roamSinceMs: Long? = null,
    val roamLastFixMs: Long? = null,
    /** 정차(P단·0km/h) 중에 줄어든 배터리 %(V2L·유틸리티 모드·공조). [parkedSocRef]는 흔들림을 두 번 세지 않는 기준값. */
    val parkedSocUsed: Int = 0,
    val parkedSocRef: Int? = null,
    /** 맡긴 동안 정차한 채 시동이 켜져 있던 시간과 그 시작 시각, 과속 알림을 보냈는지. */
    val idleMs: Long = 0,
    val idleSinceMs: Long? = null,
    val speedWarned: Boolean = false,
    /** 차주 메모("타이어 교체", "오른쪽 문 기존 흠집")와 반납 예정 시각. 예정이 지나도 반납 전이면 한 번 알린다. */
    val memo: String = "",
    val dueMs: Long? = null,
    val dueWarned: Boolean = false,
)

enum class ComfortPhase(val label: String) { OFF("대기"), REMOTE_RUNNING("원격으로 미리 켜는 중"), APPLIED("탑승 후 적용 완료") }

data class ComfortState(
    val phase: ComfortPhase = ComfortPhase.OFF,
    val preset: ComfortPreset? = null,
    val endsAtMs: Long = 0,
    val forDriver: String? = null,
    val lastScheduledDate: String? = null,
    val message: String? = null,
)

const val DIAG_DISCLAIMER = "관련 있을 수 있는 설정을 먼저 확인해 보세요. 고장 여부는 블루핸즈 점검으로 확인해요."

data class EngineState(
    val valet: ValetState = ValetState(),
    val driver: DriverState = DriverState(),
    val trip: TripState = TripState(),
    val escort: EscortState = EscortState(),
    val school: SchoolState = SchoolState(),
    val chauffeur: ChauffeurState = ChauffeurState(),
    val trunk: TrunkState = TrunkState(),
    val missions: MissionState = MissionState(),
    val comfort: ComfortState = ComfortState(),
    val exit: ExitCheck = ExitCheck(),
    val nhold: NHoldState = NHoldState(),
    val floor: ParkFloor = ParkFloor(),
    val lend: LendState = LendState(),
    val evac: EvacState = EvacState(),
    val pendingControl: PendingControl? = null,
    /** 최근 제어의 확인 근거(새것이 앞, 최대 5건). */
    val controlLog: List<ControlEvidence> = emptyList(),
    /** 하루 단위 주행 기록(드라이빙 인사이트). */
    val stats: List<DailyStat> = emptyList(),
    val enforcementNotified: Boolean = false,
    val ambient: AmbientCue? = null,
    val lastPlace: Place = Place.HOME,
    val stoppedSinceMs: Long? = null,
    /** 맡김 이력서(오래된 것이 앞). */
    val history: List<HandoverRecord> = emptyList(),
)

/**
 * 차 안에서만 도는 판정 엔진. 신호 한 장면과 시각을 받아 상태를 갱신하고, 화면·음성·알림 효과를 돌려준다.
 * 안드로이드 의존성이 없어 JVM 단위 테스트로 검증한다.
 */
class VerdictEngine(var settings: Settings = Settings(), var caps: Capabilities = Capabilities()) {
    var state = EngineState()
        private set
    private var prev: Signals? = null
    private var prevAtMs = 0L
    private var lastHarshMs = Long.MIN_VALUE / 2
    private var lastFastMs: Long? = null
    private var socRef: Int? = null

    /** 실차에서는 집 핀을 등록해야 ①을 시작한다. 위치를 모르는데 '집 도착'으로 판정하지 않기 위해서다. */
    var requireHomePin = false

    private fun on(m: Module) = m in settings.modules

    /**
     * 앱이 차를 직접 제어해도 되는 차인지. 제어 지원 차종이면서 실제 제어 신호가 하나라도 열려 있어야 한다.
     * 아니면 ⑩ 출발 준비, ① 시트·공조 복원, ④ 하차 조명 같은 제어 기능을 막고 주행 기능만 쓴다.
     */
    fun controlAllowed() = settings.carModel?.controllable == true && (caps.climateControl || caps.seatControl || caps.ambientControl)

    /** GPS가 끊기면(지하주차장) 끊기기 직전 위치를 쓴다. */
    fun effectivePlace(s: Signals): Place = if (placeKnown(s)) s.place else state.lastPlace

    private fun placeKnown(s: Signals) = !s.gpsLost && caps.locationWithoutRoute

    /** 대리 판정은 마지막 장소 추정 대신 현재 수신한 위치만 쓴다. 실차는 30초 이내 관측이 필요하다. */
    fun valetLocationKnown(s: Signals, nowMs: Long): Boolean = placeKnown(s) && known("gps", s) &&
        (!onCar || s.seenAt["gps"]?.let { nowMs - it in 0..30_000L } == true)

    private fun seated2(s: Signals) = if (caps.row2Occupancy) s.row2Seated else s.row2Belt

    /** 실제로 움직이는지. 차속을 못 읽으면(위험 권한 거부) 기어로 대신 본다. */
    private fun moving(s: Signals) = if (caps.speed) s.speedKmh > 0f else !s.parked

    /** N단 이중주차처럼 시동을 끈 정지도 주차로 본다. */
    private fun stopped(s: Signals) = s.parked || (s.speedKmh == 0f && !s.ignitionOn)

    fun update(s: Signals, nowMs: Long, shutdown: Boolean = false): List<Effect> {
        val fx = mutableListOf<Effect>()
        val p = prev ?: s
        val valetSession = state.valet.active
        var st = state
        if (placeKnown(s)) st = st.copy(lastPlace = s.place)
        st = st.copy(stoppedSinceMs = if (s.speedKmh == 0f) st.stoppedSinceMs ?: nowMs else null)
        state = st

        // 급감속·급가속: 초당 8km/h 이상 변화, 2초 안의 중복은 한 번으로 본다
        var brake = false
        var accel = false
        val dt = (nowMs - prevAtMs) / 1000.0
        if (prev != null && dt in 0.2..3.0 && nowMs - lastHarshMs > 2_000) {
            val rate = (s.speedKmh - p.speedKmh) / dt
            brake = rate <= -HARSH_KMH_PER_S
            accel = rate >= HARSH_KMH_PER_S
            if (brake || accel) lastHarshMs = nowMs
        }

        val place = effectivePlace(s)
        val stoppedMs = state.stoppedSinceMs?.let { nowMs - it } ?: 0L
        valet(s, stoppedMs, nowMs, shutdown, fx)
        if (on(Module.DRIVER)) driver(s, p, nowMs, fx)
        comfort(s, p, prev == null, nowMs, fx)
        if (on(Module.TRIP)) trip(s, p, place, stoppedMs, nowMs, shutdown, fx)
        escort(s, stoppedMs, nowMs, brake, fx)
        if (on(Module.SCHOOL)) school(s, p, place, nowMs, fx)
        if (on(Module.CHAUFFEUR) && !valetSession) chauffeur(s, brake, accel, fx)
        if (on(Module.TRUNK)) trunk(s, p, place, nowMs, fx)
        missions(s, p, nowMs, fx)
        nhold(s, p, nowMs, fx)
        if (on(Module.TRUNK)) {
            exitCheck(s, p, nowMs, fx)
            floor(s, p, fx)
        }
        // 맡김은 ② 모듈을 켜지 않아도 기록한다
        if (on(Module.DRIVER) || state.lend.active) lend(s, p, nowMs, brake, fx)
        evac(s, p, nowMs, fx)
        controlCheck(s, nowMs, fx)
        if (s.parked && s.tireKpa.size == 4) state = state.copy(stats = MyHyundai.recordTires(state.stats, date(nowMs), s.tireKpa))
        if (prev != null) driveStats(s, p, nowMs, brake && !valetSession, accel && !valetSession)
        enforcement(s, fx)

        // 속도가 바뀐 때만 기준 시각을 옮긴다. 타이머·다른 신호 갱신이 끼면 dt가 짧아져 급감속이 부풀려진다
        if (prev == null || s.speedKmh != p.speedKmh) prevAtMs = nowMs
        prev = s
        return fx
    }

    // ---------------------------------------------------------------- ① 대리 안심 주차
    /** 진행 중(동의 대기·운행·1차 완료·미완료·완료 보고)이면 다시 시작하지 않는다. 미완료 경고를 지우는 우회로를 막는다. */
    fun startValet(): List<Effect> {
        val refuse = when {
            state.lend.active -> "진행 중인 맡김을 먼저 반납해 주세요."
            state.valet.phase !in setOf(ValetPhase.IDLE, ValetPhase.CLOSED_BY_OWNER) -> "이미 대리 모드가 진행 중이에요. 차주가 대리 모드를 끝낸 뒤 다시 시작할 수 있어요."
            requireHomePin && Place.HOME !in settings.pins -> "설정 → 장소 등록에서 집을 먼저 등록해 주세요. 위치를 모르면 집 도착을 판정할 수 없어요."
            else -> null
        }
        if (refuse != null) return listOf(Effect.Toast(refuse))
        state = state.copy(valet = ValetState(phase = ValetPhase.CONSENT))
        return emptyList()
    }

    fun confirmValetConsent(s: Signals, nowMs: Long): List<Effect> {
        if (state.valet.phase != ValetPhase.CONSENT || state.lend.active) return emptyList()
        settings = settings.copy(consents = settings.consents + Consent.VALET_DRIVER, consentAt = settings.consentAt + (Consent.VALET_DRIVER to nowMs))
        // 동의 대기 중에 체크한 현대 기능 체크리스트와 그 근거는 이어 간다
        val before = state.valet
        state = state.copy(valet = ValetState(
            phase = ValetPhase.DRIVING, handover = snap(s, nowMs), proof = before.proof + handoverProof("인수", s, nowMs),
            checkIn = carCheck(s, nowMs), prep = before.prep,
        ))
        val toast = Effect.Toast("인수 상태를 저장했습니다. 집으로 안내를 시작합니다.")
        val notice = Effect.Notify("차주 폰", "대리운전 시작", "기사님이 동의했고 인수 시각을 기록했어요.", Level.INFO)
        return listOfNotNull(toast, notice, settings.pins[Place.HOME]?.let { Effect.NaviHome(it) })
    }

    fun ownerCloseValet(): List<Effect> {
        val v = state.valet
        if (v.phase != ValetPhase.INCOMPLETE) return emptyList()
        record("대리", v.handover?.timeMs ?: 0, v.result?.timeMs ?: 0, v.checkIn, v.checkOut, 0f, 0, ok = false, proof = v.proof)
        state = state.copy(valet = state.valet.copy(phase = ValetPhase.CLOSED_BY_OWNER))
        return listOf(Effect.Notify("차주 폰", "① 차주 승인으로 대리 종료", "미완료 위치에서 차주가 직접 종료를 승인했습니다.", Level.INFO))
    }

    /** 자동 판정에 필요한 신호를 못 읽은 경우 차주가 직접 인수한 사실만 기록한다. */
    fun manualValetReturn(s: Signals, nowMs: Long): List<Effect> {
        val v = state.valet
        if (v.phase != ValetPhase.DRIVING && v.phase != ValetPhase.PRELIM) return emptyList()
        if (s.uxRestricted) return listOf(Effect.Toast("정차한 뒤 반납을 확인해 주세요."))
        val out = carCheck(s, nowMs)
        val reason = "차주가 직접 반납 확인 · 주차 완료 자동 판정 없음"
        record("대리", v.handover?.timeMs ?: nowMs, nowMs, v.checkIn, out, 0f, 0, ok = false, note = reason, proof = v.proof + Proof(nowMs, reason, "차주 확인"))
        state = state.copy(valet = v.copy(phase = ValetPhase.CLOSED_BY_OWNER, result = snap(s, nowMs), checkOut = out,
            confirmedBy = reason, proof = v.proof + Proof(nowMs, reason, "차주 확인")))
        return listOf(Effect.Notify("차주 폰", "대리 반납 · 차주 직접 확인", reason, Level.INFO))
    }

    fun endValet() {
        state = state.copy(valet = ValetState())
    }

    /**
     * 재부팅은 시동 OFF·정상 인계의 증거가 아니다. 미확정 상태만 복구하고 새 신호로 다시 판정한다.
     * 이미 확정된 결과는 보존하며 복구만으로 알림이나 성공 이력을 만들지 않는다.
     */
    fun recoverValet(saved: ValetState, rebooted: Boolean = true): List<Effect> {
        val restored = if (saved.tracking && rebooted) saved.copy(
            prelimAtMs = null,
            verificationPending = "재부팅 후 위치와 차량 신호를 다시 확인하고 있어요. 자동 완료는 아직 확인되지 않았어요.",
        ) else saved
        state = state.copy(valet = restored, stoppedSinceMs = null)
        prev = null
        return emptyList()
    }

    /** 시트를 뒤로 미는 제어라 뒷좌석이 비어 있을 때만 허용한다. */
    fun requestRestore(s: Signals): List<Effect> {
        val msg = when {
            !controlAllowed() -> "이 차는 앱 제어를 지원하지 않아요. 메모리 1번 버튼으로 시트를 맞춰 주세요."
            Flag.CONTROL_RESTORE !in settings.flags -> "시트·공조 복원 기능이 아직 승인되지 않았어요. 메모리 버튼을 눌러 주세요."
            !caps.seatControl -> "이 차종은 앱의 시트 제어를 지원하지 않아요. 메모리 1번 버튼을 눌러 주세요."
            s.gear != Gear.P -> "P단에서만 복원할 수 있어요."
            !s.driverSeated -> "운전석에 앉은 뒤 복원해 주세요."
            seated2(s) -> "뒷좌석에 사람이나 짐이 있을 수 있어 시트를 옮기지 않았어요."
            else -> null
        }
        state = state.copy(valet = state.valet.copy(restoreMsg = msg ?: "차주 시트·공조 설정으로 복원했어요."))
        return if (msg == null) listOf(Effect.RestoreSeat, Effect.Toast("차주 설정 복원 완료")) else listOf(Effect.Toast(msg))
    }

    private fun valet(s: Signals, stoppedMs: Long, nowMs: Long, shutdown: Boolean, fx: MutableList<Effect>) {
        var v = state.valet
        if (!v.tracking) return
        val driverLeft = (known("driverSeat", s) && !s.driverSeated) || (known("doors", s) && s.driverDoorOpen)
        val engineOff = shutdown || (known("ignition", s) && !s.ignitionOn)
        val parked = known("speed", s) && s.speedKmh == 0f && ((known("gear", s) && s.gear == Gear.P) || engineOff)
        val movingNow = known("speed", s) && s.speedKmh > 0f
        val locationKnown = valetLocationKnown(s, nowMs)
        // 집에서 P단으로 1차 완료를 잡은 뒤 차가 움직이지 않았으면, 반경 경계의 GPS 흔들림으로 '집 밖'이 찍혀도 집으로 본다
        // (움직이거나 위치를 잃거나 재부팅하면 prelimAtMs가 지워진다)
        val home = locationKnown && (s.place == Place.HOME || v.prelimAtMs != null)
        if (locationKnown) v = v.copy(verificationPending = null)
        when {
            !stopped(s) && (!onCar || movingNow) -> v = v.copy(phase = ValetPhase.DRIVING, prelimAtMs = null, moved = true,
                verificationPending = if (locationKnown) null else "위치 신호를 기다리고 있어요. 목적지 도착 판정을 보류합니다.")
            !v.moved || !parked -> {}
            !locationKnown -> v = v.copy(
                phase = if (v.phase == ValetPhase.INCOMPLETE) v.phase else ValetPhase.PRELIM,
                prelimAtMs = null,
                verificationPending = "위치 신호를 기다리고 있어요. 완료·목적지 밖 하차 판정을 보류합니다.",
            )
            // 목적지 밖 하차(미완료)는 차가 다시 움직이거나 차주가 끝낼 때까지 유지한다(서 있는 차의 GPS 흔들림으로 '완료'가 되지 않게)
            v.phase == ValetPhase.INCOMPLETE -> {}
            home -> {
                v = v.copy(verificationPending = null)
                if (v.prelimAtMs == null) {
                    v = v.copy(phase = ValetPhase.PRELIM, prelimAtMs = nowMs, placeEstimated = !placeKnown(s))
                    v = v.copy(proof = v.proof + Proof(nowMs, if (s.gear == Gear.P) "P단" else "정차·시동 OFF", src("gear", s)) + placeProof(s, nowMs, v.placeEstimated))
                    fx += Effect.Toast("P단 확인 · 완료 확인 중(기록 저장됨)")
                }
                val by = when {
                    engineOff -> "시동 OFF"
                    stoppedMs >= VALET_HOLD_MS && driverLeft -> "10초 정지 + 운전석 하차"
                    else -> null
                }
                if (by != null) {
                    val why = if (by == "시동 OFF") listOf(Proof(nowMs, "시동 OFF", src("ignition", s)))
                        else listOf(Proof(nowMs, "10초 정지", src("speed", s)), Proof(nowMs, "운전석 하차", src(if (s.driverDoorOpen) "doors" else "driverSeat", s)))
                    val out = carCheck(s, nowMs)
                    val eta = v.naviEtaMs?.let { listOf(Proof(nowMs, "예정 ${hhmm(it)} · 실제 ${hhmm(nowMs)}", "현대 내비 기준")) } ?: emptyList()
                    val changes = v.checkIn?.let { diffChecks(it, out) }
                    v = v.copy(
                        phase = ValetPhase.COMPLETE, result = snap(s, nowMs), confirmedBy = by, checkOut = out,
                        proof = v.proof + why + handoverProof("도착", s, nowMs) + eta,
                    )
                    record("대리", v.handover?.timeMs ?: nowMs, nowMs, v.checkIn, out, 0f, 0, ok = true, proof = v.proof)
                    fx += Effect.Notify("차주 폰", "① 대리 주차 완료", completeBody(v), Level.OK)
                    // 본문에는 '바뀐 것'을 따로 붙였으므로 근거 줄은 알림 뒤에 더한다
                    changes?.let { v = v.copy(proof = v.proof + changesProof(it, nowMs)) }
                    fx += Effect.Speak("주차 완료가 확인됐어요. 수고하셨습니다.")
                }
            }
            driverLeft || engineOff -> if (v.phase != ValetPhase.INCOMPLETE) {
                val out = carCheck(s, nowMs)
                val changes = v.checkIn?.let { diffChecks(it, out) }
                v = v.copy(phase = ValetPhase.INCOMPLETE, result = snap(s, nowMs), placeEstimated = false, checkOut = out, verificationPending = null)
                v = v.copy(proof = v.proof + Proof(nowMs, if (engineOff) "시동 OFF" else "운전석 하차", src(if (engineOff) "ignition" else if (s.driverDoorOpen) "doors" else "driverSeat", s)) + placeProof(s, nowMs, v.placeEstimated))
                val where = if (placeKnown(s)) "${s.place.label} 부근" else "위치 미확인(직접 확인 필요)"
                fx += Effect.Notify(
                    "차주 폰", if (driverLeft) "① 집이 아닌 곳에서 기사가 내렸어요" else "① 목적지 밖에서 시동이 꺼졌어요",
                    "$where. 직접 운전하지 마시고 대리 재호출이나 긴급출동을 이용하세요. 근거: ${proofLine(v.proof)}" +
                        (changes?.let { " · 바뀐 것: " + it.joinToString(", ") } ?: ""), Level.DANGER,
                )
                changes?.let { v = v.copy(proof = v.proof + changesProof(it, nowMs)) }
                fx += Effect.Speak("목적지가 아닌 곳이에요. 차주 확인 전까지 잠시만 기다려 주세요.")
            }
            else -> v = v.copy(phase = ValetPhase.DRIVING, prelimAtMs = null)
        }
        if (v.phase == ValetPhase.DRIVING) v = navi(v, s, nowMs, fx)
        state = state.copy(valet = v)
    }

    /** ① 현대 내비가 알려 준 남은 시간·거리로 도착 예정을 알리고, 남은 거리가 늘어난 채 머물면 경로 이탈로 본다. */
    private fun navi(v0: ValetState, s: Signals, nowMs: Long, fx: MutableList<Effect>): ValetState {
        var v = v0
        val from = s.seenAt["navi"]?.let { "현대 내비 ${hhmmss(it)}" } ?: if (onCar) "확인 불가" else "시뮬레이터"
        val km = s.naviRemainKm
        val min = s.naviRemainMin
        if (v.naviEtaMs == null && min != null) {
            val eta = nowMs + min * 60_000L
            v = v.copy(naviEtaMs = eta, proof = v.proof + Proof(nowMs, "집으로 안내 · 도착 예정 ${hhmm(eta)}", from))
            fx += Effect.Notify(
                "차주 폰", "① 집으로 출발 · 도착 예정 ${hhmm(eta)} (현대 내비 기준)",
                "남은 거리 " + (km?.let { "%.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가") + " · 남은 시간 ${min}분", Level.INFO,
            )
        }
        if (km != null && !v.naviOffWarned) {
            val low = minOf(v.naviMinKm ?: km, km)
            val since = if (km >= low + NAVI_OFF_KM) v.naviOffSinceMs ?: nowMs else null
            v = v.copy(naviMinKm = low, naviOffSinceMs = since)
            if (since != null && nowMs - since >= NAVI_OFF_MS) {
                val what = "경로 이탈 · 남은 거리 %.1fkm (가장 가까울 때 %.1fkm)".format(java.util.Locale.ROOT, km, low)
                v = v.copy(naviOffWarned = true, naviOffSinceMs = null, proof = v.proof + Proof(nowMs, what, from))
                fx += Effect.Notify("차주 폰", "① 집 가는 경로를 벗어났어요", "$what. 1분 넘게 집에서 멀어진 채예요(현대 내비 기준).", Level.WARN)
            }
        }
        return v
    }

    /** 인수·반납 때 실제로 읽은 값을 사용한다. 나중에 들어온 신호로 이전 기록을 채우지 않는다. */
    private fun completeBody(v: ValetState): String {
        val a = v.checkIn
        val b = v.checkOut ?: return "인수·반납 비교 확인 불가 · 완료 근거: ${v.confirmedBy}"
        val distance = b.distanceFrom(a)?.let { "%.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가"
        val energy = if (a?.socPct != null && b.socPct != null) "${a.socPct}→${b.socPct}%" else "확인 불가"
        val moved = "이동 $distance · 배터리 $energy · "
        val wheel = b.steeringDeg?.let { if (abs(it) >= WHEEL_TURNED_DEG) "바퀴 %+.0f° 틀어짐".format(java.util.Locale.ROOT, it) else "바퀴 정렬" } ?: "바퀴 확인 불가"
        val window = when (b.windowOpen) { true -> "창문 열림"; false -> "창문 닫힘"; null -> "창문 확인 불가" }
        val est = if (v.placeEstimated) " · 지하 진입 전 위치로 판정" else ""
        val chain = if (v.proof.isEmpty()) "${v.confirmedBy}" else proofLine(v.proof)
        val changed = v.checkIn?.let { a -> v.checkOut?.let { " · 바뀐 것: " + diffChecks(a, it).joinToString(", ") } } ?: ""
        return "$moved$window · $wheel$changed · 근거: $chain$est"
    }

    /** 차가 쓰는 인수인계서. 실차에서 읽은 적 없는 신호는 null로 둔다. */
    fun carCheck(s: Signals, nowMs: Long): CarCheck {
        fun <T> k(name: String, v: T): T? = v.takeIf { known(name, s) }
        return CarCheck(
            nowMs, k("odometer", s.odometerKm), k("soc", s.socPct), k("warnings", s.warnings),
            // 타이어 저압은 Pleos(tire)나 AAOS 공기압(tirePressure) 어느 쪽으로 읽어도 된다
            s.tirePressureLow.takeIf { known("tire", s) || known("tirePressure", s) },
            k("washer", s.washerLow), k("oil", s.oilLow), k("windowStatus", s.windowOpen), k("trunk", s.trunkOpen), k("lockStatus", s.doorsLocked),
            k("steering", s.steeringDeg), k("speed", s.speedKmh),
        )
    }

    private fun changesProof(changes: List<String>, nowMs: Long) =
        Proof(nowMs, "바뀐 것: " + changes.joinToString(", "), if (onCar) "인수·반납 차 신호 비교" else "시뮬레이터")

    /** 맡긴 동안 정차한 채 시동이 켜진 시간. 신호가 드문드문 와도 되도록 시작 시각으로 잰다. */
    private fun idleStep(idleMs: Long, sinceMs: Long?, s: Signals, nowMs: Long): Pair<Long, Long?> {
        val idling = known("ignition", s) && known("speed", s) && s.ignitionOn && s.speedKmh == 0f
        return when {
            idling -> idleMs to (sinceMs ?: nowMs)
            sinceMs != null -> (idleMs + nowMs - sinceMs) to null
            else -> idleMs to null
        }
    }

    private fun idleText(idleMs: Long, sinceMs: Long?, nowMs: Long): String? {
        val total = idleMs + (sinceMs?.let { nowMs - it } ?: 0)
        return "${total / 60_000}분".takeIf { total >= IDLE_NOTE_MS }
    }

    private fun handoverNote(idleMs: Long, sinceMs: Long?, speedWarned: Boolean, nowMs: Long) = listOfNotNull(
        idleText(idleMs, sinceMs, nowMs)?.let { "정차 중 시동 $it" },
        "${HANDOVER_SPEED_KMH.toInt()}km/h 이상 주행".takeIf { speedWarned },
    ).joinToString(" · ").ifEmpty { null }

    /** 맡긴 차가 [HANDOVER_SPEED_KMH]를 넘으면 차주에게 한 번 알리고 근거 한 줄을 돌려준다. */
    private fun speedAlert(kind: String, s: Signals, nowMs: Long, fx: MutableList<Effect>): Proof? {
        if (!known("speed", s) || s.speedKmh < HANDOVER_SPEED_KMH) return null
        val what = "%.0fkm/h 주행".format(java.util.Locale.ROOT, s.speedKmh)
        fx += Effect.Notify("차주 폰", "맡긴 차 과속 · $kind", "${hhmm(nowMs)} $what", Level.WARN)
        return Proof(nowMs, what, src("speed", s))
    }

    /** 맡김 이력서에 한 줄 더한다. 새 경고등·저압·부족 표시가 생겼으면 '문제 없이 돌려받음'에서 뺀다. */
    private fun record(kind: String, startMs: Long, endMs: Long, a: CarCheck?, b: CarCheck?, maxSpeed: Float, brakes: Int, ok: Boolean, note: String? = null,
                       proof: List<Proof> = emptyList(), memo: String = "", dueMs: Long? = null) {
        val changes = (if (a != null && b != null) diffChecks(a, b) else listOf("확인 불가: 인수·반납 상태 기록 없음")) + listOfNotNull(note)
        val km = b?.distanceFrom(a)
        val speedKnown = b?.speedKmh != null || maxSpeed > 0f
        val verified = ok && (kind == "대리" || speedKnown) && changes.none { "새로" in it || it.startsWith("확인 불가") }
        val record = HandoverRecord(kind, startMs, endMs, km, maxSpeed, brakes, changes, verified, speedKnown, proof, memo.trim(), dueMs).respectingValetConsent()
        state = state.copy(history = state.history + record)
    }

    /** 이력서 한 건의 차주 메모를 고친다(맡김 기록 상세 화면). */
    fun setRecordMemo(startMs: Long, memo: String) {
        state = state.copy(history = state.history.map { if (it.startMs == startMs) it.copy(memo = memo.trim().take(MEMO_MAX)) else it })
    }

    /** 맡기는 중인 건의 메모·반납 예정 시각을 고친다. 예정을 바꾸면 지남 알림을 다시 보낼 수 있다. */
    fun setLendPlan(memo: String, dueMs: Long?) {
        val l = state.lend
        state = state.copy(lend = l.copy(memo = memo.trim().take(MEMO_MAX), dueMs = dueMs, dueWarned = l.dueWarned && l.dueMs == dueMs))
    }

    /** 반납 예정이 지났는데 아직 알리지 않았는지(쉬는 동안 깨울지 정할 때 쓴다). */
    fun lendOverdue(nowMs: Long) = state.lend.let { it.active && it.dueMs != null && !it.dueWarned && nowMs > it.dueMs }

    fun loadHistory(h: List<HandoverRecord>) {
        state = state.copy(history = h.map { it.respectingValetConsent() })
    }

    /**
     * 현대 기능 체크리스트 체크·해제(화면에서 차주 전용으로 부른다). [valet]이면 ① 대리, 아니면 빌려주기·맡김(끝난 뒤 리포트에도 체크 가능).
     * 앱이 이 기능들의 상태를 읽을 수 없어, 근거 줄의 출처는 '차주 확인'이다.
     */
    fun togglePrep(item: PrepItem, nowMs: Long, valet: Boolean = state.valet.phase != ValetPhase.IDLE): List<Effect> {
        val p = Proof(nowMs, "${item.done} · 차주 확인", "차주 확인(앱이 읽을 수 없는 현대 기능)")
        if (valet) {
            val v = state.valet
            if (v.phase == ValetPhase.IDLE) return listOf(Effect.Toast("대리 모드를 시작한 뒤 체크할 수 있어요."))
            val on = item !in v.prep
            state = state.copy(valet = v.copy(prep = if (on) v.prep + item else v.prep - item, proof = if (on) v.proof + p else v.proof))
            // 완료 기록을 이미 남겼으면(돌려받을 때 체크리스트) 이력서 기록에도 같은 근거 줄을 붙인다
            if (on && v.phase == ValetPhase.COMPLETE) v.result?.timeMs?.let { end -> addRecordedProof("대리", end, p) }
        } else {
            val l = state.lend
            if (!l.active && l.report.isEmpty()) return listOf(Effect.Toast("빌려주기·맡김을 시작한 뒤 체크할 수 있어요."))
            val on = item !in l.prep
            state = state.copy(lend = l.copy(prep = if (on) l.prep + item else l.prep - item, proof = if (on) capped(l.proof + p) else l.proof))
            // 반납 확인 뒤에 한 체크도 그 맡김의 이력서·PDF에 남긴다(다음 맡김을 시작하면 이 상태는 사라진다)
            if (on && !l.active) addRecordedProof(if (l.kind == HandKind.SERVICE) "맡김" else "빌려주기", l.endedAtMs, p)
        }
        return emptyList()
    }

    /** 이미 남긴 이력서 기록(같은 종류·끝난 시각)에 근거 줄을 더한다. 기존 근거는 지우지 않고, 같은 확인은 한 번만 남긴다. */
    private fun addRecordedProof(kind: String, endMs: Long, p: Proof) {
        state = state.copy(history = state.history.map { r ->
            if (r.kind == kind && r.endMs == endMs && r.proof.none { it.what == p.what }) r.copy(proof = r.proof + p) else r
        })
    }

    /** 체크리스트 문구. 빌트인 캠 영상 저장은 찾아야 할 시간 범위(맡긴 시각~돌려받은 시각)를 그대로 보여 준다. */
    fun prepLabel(item: PrepItem, valet: Boolean = state.valet.phase != ValetPhase.IDLE): String {
        if (item != PrepItem.CAM_SAVE) return item.label
        val from = if (valet) state.valet.handover?.timeMs else state.lend.checkIn?.atMs
        val to = if (valet) state.valet.result?.timeMs else state.lend.endedAtMs.takeIf { it > 0 && !state.lend.active }
        from ?: return item.label
        fun at(ms: Long) = "${date(ms).drop(5).replace('-', '/')} ${hhmm(ms)}"
        return "빌트인 캠 영상 저장: ${at(from)}~${to?.let(::at) ?: "지금"}"
    }

    private fun capped(p: List<Proof>) = if (p.size > PROOF_MAX) p.take(1) + p.takeLast(PROOF_MAX - 1) else p

    /** 실차에 연결돼 있는지(Runtime이 정한다). 시뮬레이터 값과 '실차에서 못 읽은 값'을 가르는 데 쓴다. */
    var onCar = false

    /** 이 신호를 믿을 수 있는지: 차에서 읽었거나, 시뮬레이터 시연 중이거나. */
    fun known(name: String, s: Signals) = name in s.seenAt || !onCar

    /** 근거 줄의 출처. */
    fun src(name: String, s: Signals) = s.seenAt[name]?.let { "차 신호 ${hhmmss(it)}" } ?: if (onCar) "확인 불가" else "시뮬레이터"

    private fun placeProof(s: Signals, nowMs: Long, estimated: Boolean): Proof {
        val where = if (s.place == Place.HOME) "집 반경 ${settings.pinRadiusM}m 안" else "${s.place.label} 부근"
        val from = when {
            estimated -> "지하 진입 전 마지막 GPS"
            else -> s.seenAt["gps"]?.let { "GPS ${hhmmss(it)}" } ?: if (onCar) "확인 불가" else "시뮬레이터"
        }
        return Proof(nowMs, where, from)
    }

    private fun handoverProof(label: String, s: Signals, nowMs: Long) = Proof(
        nowMs,
        "$label · 주행거리 " + (if (known("odometer", s)) "%.1fkm".format(java.util.Locale.ROOT, s.odometerKm) else "확인 불가") +
            " · 배터리 " + (if (known("soc", s)) "${s.socPct}%" else "확인 불가"),
        src("odometer", s),
    )

    /** 알림 본문용 한 줄: '23:41:05 P단(차 신호 23:41:05) → …'. */
    fun proofLine(p: List<Proof>) = p.joinToString(" → ") { "${hhmmss(it.atMs)} ${it.what}(${it.src})" }

    private fun snap(s: Signals, nowMs: Long) = Snapshot(nowMs, s.socPct, s.odometerKm, s.windowOpen, s.steeringDeg)

    // ---------------------------------------------------------------- ② 운전자 범위 지킴이
    private fun driver(s: Signals, p: Signals, nowMs: Long, fx: MutableList<Effect>) {
        // 대리 결과 보고(완료·차주 종료 대기)가 남아 있어도 기사가 운전 중일 때만 대리기사로 본다
        if (state.valet.tracking || state.valet.phase == ValetPhase.CONSENT) {
            state = state.copy(driver = DriverState(name = "대리기사 (① 모드)"))
            return
        }
        if (!s.driverSeated) {
            state = state.copy(driver = DriverState())
            return
        }
        // 앉은 순간의 시트(앞 사람 위치)로만 판정하지 않고, 시트를 맞추면 마지막 판정 자세와 비교해 다시 판정한다
        val changed = state.driver.verdict == DriverVerdict.NOT_CHECKED || Postures.distance(state.driver.judgedPosture, s.posture) > 0.02f
        if (s.gear != Gear.P || !changed) return
        val before = state.driver.verdict
        // 대리 종료 직후 아직 앉아 있는 기사 자세로는 경고하지 않는다(내리면 위에서 초기화)
        val afterValet = state.driver.name == "대리기사 (① 모드)"
        val d = judgeDriver(s.posture).copy(judgedPosture = s.posture)
        state = state.copy(driver = d)
        val l = state.lend
        if (l.active) {
            val isOwner = d.verdict == DriverVerdict.KNOWN && d.name == settings.profiles.firstOrNull()?.name
            if (!isOwner && !l.otherSeen) state = state.copy(lend = l.copy(otherSeen = true))
            // 다른 사람이 탄 뒤 차주 자세가 다시 확인되면 반납으로 본다. 맡김은 직원이 시트를 그대로 둘 수 있어 차주가 직접 끝낸다
            // 시연 예시 신호는 앱을 다시 켜면 기본값(차주 착석)으로 돌아가므로, 복구 직후 첫 신호는 반납으로 보지 않는다
            else if (isOwner && l.otherSeen && l.kind == HandKind.LEND && s.parked && (onCar || prev != null)) fx += endLend(s, nowMs)
        }
        // 맡김은 차주가 업체에 넘긴 것이라 보험 범위 경고 없이 리포트로만 남긴다(① 대리처럼)
        val service = l.active && l.kind == HandKind.SERVICE
        if (d.verdict == DriverVerdict.UNKNOWN && before != DriverVerdict.UNKNOWN && !afterValet && !service) {
            fx += Effect.Notify(
                "차주 폰", "② 처음 보는 운전 자세",
                "${settings.insuranceScope} 차량에 등록되지 않은 자세(거리 %.2f)가 감지됐어요. 출발 전에 보장 범위를 확인하세요.".format(java.util.Locale.ROOT, d.distance),
                Level.WARN,
            )
            fx += Effect.Speak("처음 뵙는 운전 자세예요. 출발 전에 보험 운전자 범위를 확인해 주세요.")
        }
    }

    fun judgeDriver(posture: List<Float>, today: LocalDate = LocalDate.now()): DriverState {
        val notes = mutableListOf<String>()
        runCatching { LocalDate.parse(settings.policyEndDate) }.getOrNull()?.let {
            if (it.isBefore(today)) notes += "입력하신 보험 만료일(${settings.policyEndDate})이 지났어요."
        }
        notes += "보험 판정은 입력하신 정보 기준이에요. 정확한 보장 범위는 보험사에서 확인하세요."
        if (!caps.seatPosture || settings.profiles.isEmpty()) {
            return DriverState(DriverVerdict.ASK, "수동 시트: 오늘 운전자를 골라 주세요", 0f, notes)
        }
        // 자세를 등록한 사람만 비교한다. 아무도 등록하지 않았으면 '처음 보는 자세' 알림 대신 등록을 안내한다
        val registered = settings.profiles.filter { it.posture.isNotEmpty() }
        if (registered.isEmpty()) return DriverState(DriverVerdict.ASK, "등록된 자세가 없어요 · 설정에서 '지금 자세로 등록'", 0f, notes)
        val best = registered.minBy { Postures.distance(it.posture, posture) }
        val dist = Postures.distance(best.posture, posture)
        val verdict = when {
            dist <= KNOWN_DIST -> DriverVerdict.KNOWN
            dist <= ASK_DIST -> DriverVerdict.ASK
            else -> DriverVerdict.UNKNOWN
        }
        if (verdict == DriverVerdict.KNOWN && !best.insured) notes.add(0, "입력하신 보험 정보로는 ${best.name}님이 보장 대상이 아니에요.")
        val name = when (verdict) {
            DriverVerdict.KNOWN -> best.name
            DriverVerdict.ASK -> "${best.name}님과 비슷한 자세"
            else -> "등록되지 않은 자세"
        }
        return DriverState(verdict, name, dist, notes)
    }

    /** 오늘 운전자 선택. OWNER는 현재 자세를 가장 가까운 프로필에 섞어 학습한다. */
    fun chooseToday(choice: String, s: Signals): List<Effect> = when (choice) {
        "KNOWN" -> {
            val best = settings.profiles.filter { it.posture.isNotEmpty() }.minByOrNull { Postures.distance(it.posture, s.posture) }
            // 누구인지 모를 만큼 다른 자세는 가장 가까운 사람(대개 차주) 프로필에 섞지 않는다
            if (best == null || Postures.distance(best.posture, s.posture) > ASK_DIST) {
                listOf(Effect.Toast("등록된 자세와 많이 달라요. 설정 → 운전자와 보험에서 '지금 자세로 등록'을 눌러 주세요."))
            } else {
                val learned = best.posture.zip(s.posture) { a, b -> (a * 2 + b) / 3 }
                settings = settings.copy(profiles = settings.profiles.map { if (it == best) it.copy(posture = learned) else it })
                state = state.copy(driver = judgeDriver(s.posture).copy(verdict = DriverVerdict.KNOWN, name = best.name, judgedPosture = s.posture))
                listOf(Effect.Toast("${best.name}님 자세를 추가로 학습했어요."))
            }
        }
        "VALET" -> startValet().ifEmpty { listOf(Effect.Toast("대리운전 모드로 전환합니다.")) }
        else -> {
            state = state.copy(driver = state.driver.copy(showNewDriverGuide = true))
            listOf(Effect.Toast("새 운전자 안내를 표시합니다."))
        }
    }

    // ---------------------------------------------------------------- ③ 업무 운행기록부
    private fun trip(s: Signals, p: Signals, place: Place, stoppedMs: Long, nowMs: Long, shutdown: Boolean, fx: MutableList<Effect>) {
        fun ready(sample: Signals) = !onCar || (known("odometer", sample) && known("gear", sample) && known("speed", sample) &&
            sample.odometerKm.isFinite() && sample.odometerKm >= 0.0 && sample.speedKmh.isFinite() && sample.speedKmh >= 0f)
        fun isStopped(sample: Signals) = if (!onCar) stopped(sample) else sample.speedKmh == 0f &&
            (sample.gear == Gear.P || (known("ignition", sample) && !sample.ignitionOn))
        // 실차 초기 기본값(0km/P)을 출발·도착 근거로 쓰지 않는다. 필요한 신호가 올 때까지 열린 운행도 보존한다.
        if (!ready(s)) return
        var t = state.trip
        // 정지에서 벗어나는 순간만 연다(N단·시동 OFF로 서 있는 동안 다시 열리지 않게)
        // D 신호가 주행계보다 먼저 왔다면 모든 값이 갖춰진 첫 장면부터 기록한다.
        val startObserved = isStopped(p) || (onCar && (prev == null || !ready(p)))
        if (t.open == null && !isStopped(s) && startObserved) {
            t = t.copy(open = OpenTrip(nowMs, s.odometerKm, place, currentDriverName()))
        } else if (t.open != null && isStopped(s) && (shutdown || stoppedMs >= TRIP_END_MS)) {
            val o = t.open!!
            t = t.copy(open = null)
            // 재주차·기어만 바꾼 이동은 운행으로 남기지 않고, 목적을 고르지 않은 앞 운행도 그대로 둔다
            if (s.odometerKm - o.startOdo >= TRIP_MIN_KM) {
                // 앞 운행 목적을 끝내 고르지 않았으면 새 운행을 남길 때 '미분류'로 넘긴다
                t.pending?.let { t = t.copy(logs = listOf(it.copy(purpose = UNCLASSIFIED)) + t.logs, pending = null) }
                val log = TripLog(date(nowMs), o.driver, o.from.label, place.label, o.startOdo, s.odometerKm, "", settings.businessMode)
                t = if (!settings.businessMode) {
                    fx += Effect.Toast("사적 운행으로 기록했어요 (업무 비용에서 제외).")
                    t.copy(logs = listOf(log.copy(purpose = "사적 운행")) + t.logs)
                } else {
                    fx += Effect.Toast("운행이 끝났어요. 운행 목적을 골라 주세요.")
                    t.copy(pending = log, suggestion = t.logs.firstOrNull { it.to == log.to && it.business && it.purpose != UNCLASSIFIED }?.purpose)
                }
            }
        }
        state = state.copy(trip = t)
    }

    fun selectPurpose(purpose: String): List<Effect> {
        val t = state.trip
        val pend = t.pending ?: return emptyList()
        val business = purpose != PRIVATE_PURPOSE
        state = state.copy(trip = t.copy(pending = null, suggestion = null, logs = listOf(pend.copy(purpose = purpose, business = business)) + t.logs))
        return listOf(Effect.Toast("운행 목적 [$purpose] 저장"))
    }

    /** 실차에서 첫 위치를 받기 전에는 '집'으로 가정하지 않는다. */
    fun forgetPlace() {
        state = state.copy(lastPlace = Place.OTHER)
    }

    /** 앱이 다시 켜졌을 때 끝났지만 목적을 고르지 않은 운행, 진행 중인 운행, 빌려주기·맡김을 이어 간다. */
    fun loadSession(pending: TripLog?, open: OpenTrip?, lend: LendState, trunk: TrunkState = TrunkState(), streak: Int = 0) {
        state = state.copy(trip = state.trip.copy(pending = pending, open = open),
            lend = lend.copy(roamSinceMs = null, roamLastFixMs = null), trunk = trunk, school = state.school.copy(streak = streak))
    }

    /** 판정한 자세만 비워 다음 신호에서 바로 다시 판정한다. 결과는 남겨 같은 결과면 차주 폰에 또 알리지 않는다. */
    fun rejudgeDriver() { state = state.copy(driver = state.driver.copy(judgedPosture = emptyList())) }

    fun loadTrips(logs: List<TripLog>) {
        state = state.copy(trip = state.trip.copy(logs = logs))
    }

    /** 운행기록부 '사용자' 칸. 확인하지 못한 사람을 차주로 적지 않는다. */
    private fun currentDriverName() = when {
        state.valet.tracking || state.valet.phase == ValetPhase.CONSENT -> "대리기사"
        state.lend.active -> if (state.lend.kind == HandKind.SERVICE) "정비·주차대행" else "빌린 사람"
        state.driver.verdict == DriverVerdict.KNOWN -> state.driver.name
        state.driver.verdict == DriverVerdict.UNKNOWN -> "등록되지 않은 운전자"
        else -> "차주(미확인)"
    }

    // ---------------------------------------------------------------- ④ 부축 동행
    fun startEscort(nowMs: Long) {
        state = state.copy(escort = EscortState(EscortPhase.BOARDING, listOf(TimelineItem(nowMs, "동행 시작"))))
    }

    fun stopEscort() {
        state = state.copy(escort = EscortState(), ambient = null)
    }

    private fun escort(s: Signals, stoppedMs: Long, nowMs: Long, brake: Boolean, fx: MutableList<Effect>) {
        var e = state.escort
        if (e.phase == EscortPhase.OFF) {
            state = state.copy(ambient = null)
            return
        }
        val seated = seated2(s)
        val anyDoor = s.row2RightDoorOpen || s.row2LeftDoorOpen
        fun log(text: String) { e = e.copy(timeline = e.timeline + TimelineItem(nowMs, text)) }
        fun guardian(title: String, body: String, level: Level) {
            if (Consent.GUARDIAN in settings.consents) fx += Effect.Notify("보호자", title, body, level)
            else fx += Effect.Toast("보호자 동의가 없어 알림을 보내지 않았어요.")
        }
        if (brake && e.phase == EscortPhase.RIDING) e = e.copy(harsh = e.harsh + 1)
        if ((e.phase == EscortPhase.ALIGHT_GUIDE || e.phase == EscortPhase.WRONG_SIDE) && s.row2RightDoorOpen) e = e.copy(rightOpened = true)
        val next = when (e.phase) {
            EscortPhase.BOARDING, EscortPhase.READY -> when {
                // 기어만 바꾸고 서 있으면 출발이 아니다
                moving(s) -> if (e.phase == EscortPhase.READY) EscortPhase.RIDING else EscortPhase.DEPARTED_UNVERIFIED
                seated && s.row2Belt && !anyDoor -> EscortPhase.READY
                else -> EscortPhase.BOARDING
            }
            EscortPhase.RIDING, EscortPhase.DEPARTED_UNVERIFIED -> if (s.parked && stoppedMs >= 3_000) EscortPhase.ALIGHT_GUIDE else e.phase
            EscortPhase.ALIGHT_GUIDE, EscortPhase.WRONG_SIDE -> when {
                moving(s) -> EscortPhase.RIDING
                s.row2LeftDoorOpen -> EscortPhase.WRONG_SIDE
                // 문이 열린 뒤에만 하차로 본다(벨트로 착석을 대신하는 차에서 벨트만 풀어도 하차로 보지 않도록)
                !seated && !anyDoor && (e.rightOpened || e.wrongSide) -> EscortPhase.ALIGHTED
                else -> EscortPhase.ALIGHT_GUIDE
            }
            else -> e.phase
        }
        if (next != e.phase) when (next) {
            EscortPhase.READY -> log("문 닫힘 · 착석 · 벨트 확인")
            EscortPhase.RIDING -> if (e.phase == EscortPhase.READY) {
                e = e.copy(verified = true)
                log("출발"); guardian("④ 출발했어요", "문·착석·벨트를 확인하고 출발했어요.", Level.INFO)
            } else {
                // 하차 판정은 이번 정차의 문만 본다
                e = e.copy(rightOpened = false, wrongSide = false)
                log("다시 출발")
            }
            EscortPhase.DEPARTED_UNVERIFIED -> { log("확인 없이 출발"); guardian("④ 확인 없이 출발", "벨트 또는 착석이 확인되지 않은 채 출발했어요.", Level.WARN) }
            EscortPhase.ALIGHT_GUIDE -> if (e.phase != EscortPhase.WRONG_SIDE) {
                log("도착 · 하차 안내")
                if (!s.seaWarning) fx += Effect.Speak("도착했어요. 오른쪽 보도 쪽 문으로 내려 주세요.")
            }
            EscortPhase.WRONG_SIDE -> {
                e = e.copy(wrongSide = true)
                log("차도 쪽 문 열림 경고"); fx += Effect.Speak("차도 쪽 문이에요. 오른쪽 보도 쪽으로 내려 주세요.")
            }
            EscortPhase.ALIGHTED -> {
                log("하차 완료")
                when {
                    !e.rightOpened -> guardian("④ 하차 완료 · 차도 쪽", "차도 쪽 문으로 내리셨어요. 괜찮으신지 확인해 주세요. 급감속 ${e.harsh}회.", Level.WARN)
                    e.wrongSide -> guardian("④ 도착·하차 완료", "보도 쪽으로 하차했어요(차도 쪽 문이 한 번 열렸어요). 급감속 ${e.harsh}회.", Level.OK)
                    else -> guardian("④ 도착·하차 완료", "보도 쪽으로 하차를 마쳤어요. 급감속 ${e.harsh}회.", Level.OK)
                }
            }
            else -> {}
        }
        e = e.copy(phase = next)
        val ambient = if (next == EscortPhase.ALIGHT_GUIDE || next == EscortPhase.WRONG_SIDE) when {
            !controlAllowed() -> AmbientCue(null, null, "이 차는 앱 조명 제어를 지원하지 않아요 · 화면과 음성으로 안내")
            Flag.AMBIENT_GUIDE !in settings.flags || !caps.ambientControl -> AmbientCue(null, null, "조명 제어 미승인 · 화면과 음성으로만 안내")
            s.seaWarning -> AmbientCue(null, null, "순정 안전하차보조 경고 중 · 앱 조명을 끄고 순정 경고에 양보")
            else -> AmbientCue(Level.DANGER, Level.OK, "오른쪽(보도 쪽) 초록 · 왼쪽(차도 쪽) 빨강")
        } else null
        state = state.copy(escort = e, ambient = ambient)
    }

    // ---------------------------------------------------------------- ⑤ 스쿨존
    private fun school(s: Signals, p: Signals, place: Place, nowMs: Long, fx: MutableList<Effect>) {
        var z = state.school
        val alight = place == Place.SCHOOL && s.parked && seated2(p) && !seated2(s)
        z = when (z.phase) {
            SchoolPhase.IDLE, SchoolPhase.SUCCESS, SchoolPhase.FAIL -> if (alight) {
                fx += Effect.Speak("아이가 내렸어요. 1분 동안 천천히 출발해 주세요.")
                z.copy(phase = SchoolPhase.WAIT_MOVE, warned = false, maxSpeed = 0f)
            } else z
            SchoolPhase.WAIT_MOVE -> if (!s.parked) z.copy(phase = SchoolPhase.WATCH, moveStartMs = nowMs, startOdo = s.odometerKm, maxSpeed = s.speedKmh) else z
            SchoolPhase.WATCH -> {
                var w = z.copy(maxSpeed = maxOf(z.maxSpeed, s.speedKmh))
                if (s.speedKmh > SCHOOL_LIMIT_KMH && !w.warned) {
                    // 주행 중에도 허용되는 음성으로 그 순간 알려 준다 (사후 성적표 한계 보완)
                    fx += Effect.Speak("어린이 하차 구역이에요. 시속 30킬로미터 이하로 천천히 가 주세요.")
                    w = w.copy(warned = true)
                }
                val done = nowMs - (w.moveStartMs ?: nowMs) >= SCHOOL_WATCH_MS || s.odometerKm - w.startOdo >= SCHOOL_WATCH_KM
                if (!done) w
                else if (w.maxSpeed <= SCHOOL_LIMIT_KMH) {
                    fx += Effect.Notify("차주 폰", "⑤ 천천히 출발 성공", "최고 %.0fkm/h · 블루멤버스 +100P".format(w.maxSpeed), Level.OK)
                    w.copy(phase = SchoolPhase.SUCCESS, streak = w.streak + 1, points = w.points + 100)
                } else w.copy(phase = SchoolPhase.FAIL, streak = 0)
            }
        }
        state = state.copy(school = z)
    }

    // ---------------------------------------------------------------- ⑥ 뒷좌석 승차감
    fun setChauffeurManual(on: Boolean) {
        state = state.copy(chauffeur = state.chauffeur.copy(manual = on))
    }

    private fun chauffeur(s: Signals, brake: Boolean, accel: Boolean, fx: MutableList<Effect>) {
        var c = state.chauffeur
        val reclined = caps.row2Recline && s.row2Reclined
        val active = seated2(s) && (reclined || c.manual)
        if (!seated2(s)) {
            state = state.copy(chauffeur = ChauffeurState(manual = c.manual))
            return
        }
        c = c.copy(active = active)
        if (active) {
            if (!s.parked) c = c.copy(driven = true)
            if (brake) {
                c = c.copy(brakes = c.brakes + 1)
                fx += Effect.Speak("뒷좌석 손님이 계세요. 조금 더 부드럽게 멈춰 주세요.")
            }
            if (accel) c = c.copy(accels = c.accels + 1)
        }
        state = state.copy(chauffeur = c)
    }

    // ---------------------------------------------------------------- ⑦ 트렁크
    private fun trunk(s: Signals, p: Signals, place: Place, nowMs: Long, fx: MutableList<Effect>) {
        var k = state.trunk
        if (!p.trunkOpen && s.trunkOpen) {
            k = k.copy(openedAtMs = nowMs)
            if (place == Place.HOME) k = k.copy(loaded = false, askConfirm = false)
        } else if (p.trunkOpen && !s.trunkOpen && place != Place.HOME) {
            // 앱이 켜지기 전부터 열려 있었으면 연 시간을 모르므로 짐을 실었는지 한 번 묻는다
            val dur = k.openedAtMs?.let { nowMs - it } ?: TRUNK_LOAD_MS
            k = if (dur >= TRUNK_LOAD_MS) {
                k.copy(loaded = true, loadedAt = "${place.label} ${hhmm(nowMs)}", askConfirm = true, reminded = false)
            } else {
                fx += Effect.Toast("트렁크를 잠깐 열었다 닫아 짐을 실은 것으로 보지 않았어요.")
                k
            }
        }
        if (k.loaded && !k.reminded && place == Place.HOME && s.parked && (s.driverDoorOpen || !s.driverSeated)) {
            k = k.copy(reminded = true)
            fx += Effect.Speak("트렁크에 ${k.loadedAt}에 실은 짐이 있어요.")
        }
        state = state.copy(trunk = k)
    }

    fun confirmTrunk(loaded: Boolean) {
        state = state.copy(trunk = state.trunk.copy(loaded = loaded, askConfirm = false))
    }

    // ---------------------------------------------------------------- ⑩ 출발 준비
    /**
     * 시동이 꺼진 차를 미리 데우거나 식힌다. 차량 화면이 꺼진 상태라 실제 실행은 마이현대 원격 공조가 맡고,
     * 이 앱은 조건 확인·설정·예약만 한다.
     */
    fun startRemote(s: Signals, nowMs: Long, driverName: String = "차주", scheduled: Boolean = false): List<Effect> {
        if (settings.carModel == null) return refuseUnregistered()
        if (!controlAllowed() || !caps.climateControl) return refuseNoControl()
        if (s.driverSeated && s.ignitionOn) return applyNow(s, nowMs, driverName, "이미 타고 계셔서 바로 적용했어요.")
        val preset = settings.comfort[driverName] ?: ComfortPreset.WARM
        val refuse = when {
            Flag.REMOTE !in settings.flags -> "마이현대 원격 공조 연동이 아직 승인되지 않았어요."
            s.gear != Gear.P -> "P단에 주차된 차에서만 원격으로 켤 수 있어요."
            !s.doorsLocked -> "문이 모두 잠겨 있어야 원격으로 켤 수 있어요."
            s.socPct < REMOTE_MIN_SOC -> "배터리가 ${REMOTE_MIN_SOC}% 미만이라 주행 거리를 지키려고 켜지 않았어요."
            else -> null
        }
        if (refuse != null) {
            state = state.copy(comfort = state.comfort.copy(message = refuse))
            return listOf(Effect.Toast(refuse))
        }
        state = state.copy(comfort = state.comfort.copy(
            phase = ComfortPhase.REMOTE_RUNNING, preset = preset, endsAtMs = nowMs + REMOTE_MINUTES * 60_000L, forDriver = driverName,
            lastScheduledDate = if (scheduled) date(nowMs) else state.comfort.lastScheduledDate,
            message = "${REMOTE_MINUTES}분 뒤 자동으로 꺼져요.",
        ))
        return listOf(
            Effect.RemoteClimate(preset, REMOTE_MINUTES),
            Effect.Notify(
                "차주 폰", "⑩ 출발 준비 시작" + if (scheduled) " (예약)" else "",
                "${driverName}님 설정: ${preset.summary} · ${REMOTE_MINUTES}분 뒤 자동 종료" +
                    winterCheck(s, preset).let { if (it.isEmpty()) "" else " · 출발 전 점검: " + it.joinToString(", ") },
                Level.OK,
            ),
        )
    }

    fun stopRemote(): List<Effect> {
        state = state.copy(comfort = state.comfort.copy(phase = ComfortPhase.OFF, message = "원격 공조를 껐어요."))
        return listOf(Effect.Toast("원격 공조를 껐어요."))
    }

    /** 탑승한 운전자에게 맞춰 차 안에서 바로 적용한다. 지원하지 않는 항목은 빼고 적용한다. */
    fun applyNow(s: Signals, nowMs: Long, driverName: String, why: String): List<Effect> {
        if (settings.carModel == null) return refuseUnregistered()
        if (!controlAllowed()) return refuseNoControl()
        val base = settings.comfort[driverName] ?: ComfortPreset.WARM
        if (Flag.CLIMATE !in settings.flags || !caps.climateControl) {
            val msg = "공조 제어가 승인되지 않아 추천만 보여 드려요: ${base.summary}"
            state = state.copy(comfort = state.comfort.copy(message = msg))
            return listOf(Effect.Toast(msg))
        }
        val p = base.copy(
            driverHeat = if (caps.seatHeat) base.driverHeat else 0,
            passengerHeat = if (caps.seatHeat && s.passengerSeated) base.passengerHeat else 0,
            rearHeat = if (caps.seatHeat && seated2(s)) base.rearHeat else 0,
            driverVent = if (caps.seatVent) base.driverVent else 0,
            steeringHeat = caps.steeringHeat && base.steeringHeat,
        )
        state = state.copy(comfort = state.comfort.copy(phase = ComfortPhase.APPLIED, preset = p, forDriver = driverName, message = why))
        return listOf(Effect.ApplyClimate(p), Effect.Toast("⑩ ${driverName}님 설정 적용: ${p.summary}"))
    }

    private fun refuseNoControl(): List<Effect> {
        val msg = "이 차는 앱 제어를 지원하지 않아 주행 기능만 사용할 수 있어요."
        state = state.copy(comfort = state.comfort.copy(message = msg))
        return listOf(Effect.Toast(msg))
    }

    private fun refuseUnregistered(): List<Effect> {
        val msg = "차량을 먼저 등록해 주세요. 등록한 차에서만 원격·예약·탑승 적용이 동작해요."
        state = state.copy(comfort = state.comfort.copy(message = msg))
        return listOf(Effect.Toast(msg))
    }

    private fun comfort(s: Signals, p: Signals, first: Boolean, nowMs: Long, fx: MutableList<Effect>) {
        // 차량을 등록하지 않으면 예약·탑승 자동 적용을 모두 쉰다
        if (settings.carModel == null || !controlAllowed()) return
        val c = state.comfort
        if (c.phase == ComfortPhase.REMOTE_RUNNING && nowMs >= c.endsAtMs) {
            state = state.copy(comfort = c.copy(phase = ComfortPhase.OFF, message = "${REMOTE_MINUTES}분이 지나 자동으로 껐어요."))
            fx += Effect.Toast("⑩ 원격 공조 자동 종료")
        }
        // 예약: 실제로는 서버가 시각에 맞춰 원격 공조를 요청한다. 시연에서는 엔진 시각으로 같은 규칙을 돌린다
        val sch = settings.schedule
        if (sch.enabled && state.comfort.phase == ComfortPhase.OFF) {
            val t = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())
            val depart = sch.hour * 60 + sch.minute
            val nowMin = t.hour * 60 + t.minute
            // 00:05 출발에 10분 전이면 전날 23:55부터다. 출발 날짜로 요일과 '이미 시도함'을 본다
            val departDay = if (nowMin < depart) t.toLocalDate() else t.toLocalDate().plusDays(1)
            val until = depart - nowMin + if (nowMin < depart) 0 else 24 * 60
            if (departDay.dayOfWeek.value in sch.days && until in 1..sch.leadMin && state.comfort.lastScheduledDate != departDay.toString()) {
                fx += startRemote(s, nowMs, "차주", scheduled = true)
                // 문이 안 잠겼거나 승인이 없어 거절돼도 같은 출발에 대해 다시 시도하지 않는다(안내가 반복되지 않도록)
                state = state.copy(comfort = state.comfort.copy(lastScheduledDate = departDay.toString()))
            }
        }
        // 탑승: 운전석에 앉고 시동이 켜진 순간, ②가 알아본 운전자의 설정을 적용한다.
        // 차량 화면은 시동 뒤에 켜지므로 앱의 첫 신호에서 이미 앉아 있어도 탑승으로 본다
        val boarded = s.driverSeated && s.ignitionOn && s.gear == Gear.P && (first || !(p.driverSeated && p.ignitionOn))
        if (boarded && settings.autoApplyOnBoard && !(state.valet.tracking || state.valet.phase == ValetPhase.CONSENT)) {
            val d = state.driver
            when {
                d.verdict == DriverVerdict.UNKNOWN -> {
                    state = state.copy(comfort = state.comfort.copy(message = "처음 보는 운전자라 개인 설정을 적용하지 않았어요."))
                    fx += Effect.Toast("⑩ 처음 보는 운전자라 개인 설정을 적용하지 않았어요.")
                }
                // 제어 승인 전에는 자동 적용 시도 대신 ⑩ 화면에 추천만 남긴다
                Flag.CLIMATE !in settings.flags || !caps.climateControl ->
                    state = state.copy(comfort = state.comfort.copy(message = "공조 제어 승인 전이라 탑승 시 자동 적용은 꺼져 있어요."))
                state.comfort.phase != ComfortPhase.APPLIED -> {
                    val name = if (d.verdict == DriverVerdict.KNOWN && d.name in settings.comfort) d.name else "차주"
                    fx += applyNow(s, nowMs, name, "탑승하자마자 적용했어요.")
                }
            }
        }
        if (!s.driverSeated && state.comfort.phase == ComfortPhase.APPLIED) state = state.copy(comfort = state.comfort.copy(phase = ComfortPhase.OFF))
    }

    // ---------------------------------------------------------------- ⑦ 하차 3초 점검
    private fun exitCheck(s: Signals, p: Signals, nowMs: Long, fx: MutableList<Effect>) {
        var x = state.exit
        if (!p.driverDoorOpen && s.driverDoorOpen && s.parked) {
            // 트렁크 짐은 trunk()가 따로 알리므로 여기서는 방전·도난으로 이어지는 것만 본다
            val items = listOfNotNull(
                "창문 열림".takeIf { s.windowOpen }, "전조등 켜짐".takeIf { s.lowBeamOn },
                "실내등 켜짐".takeIf { s.interiorLightOn }, "트렁크 열림".takeIf { s.trunkOpen },
            )
            x = ExitCheck(items, nowMs)
            if (items.isNotEmpty()) fx += Effect.Speak("내리기 전에 확인하세요. ${items.joinToString(", ")}.")
        }
        val away = !s.driverSeated && !s.driverDoorOpen && s.parked && !s.ignitionOn
        x = if (away && !s.doorsLocked) {
            val since = x.unlockedSinceMs ?: nowMs
            if (!x.lockReminded && nowMs - since >= LOCK_REMIND_MS) {
                fx += Effect.Notify("차주 폰", "⑦ 문이 잠기지 않았어요", "내린 지 1분이 지났는데 문이 잠기지 않았어요. 마이현대 원격 잠금으로 잠글 수 있어요.", Level.WARN)
                x.copy(unlockedSinceMs = since, lockReminded = true)
            } else x.copy(unlockedSinceMs = since)
        } else x.copy(unlockedSinceMs = null)
        state = state.copy(exit = x)
    }

    // ---------------------------------------------------------------- ⑦ 지하주차장 층 기억
    private fun floor(s: Signals, p: Signals, fx: MutableList<Effect>) {
        var f = state.floor
        // GPS가 끊기는 순간을 지하 진입으로 본다
        if (s.gpsLost && !p.gpsLost) f = ParkFloor(tracking = true, place = state.lastPlace)
        if (f.tracking && !s.parked) {
            val big = abs(s.steeringDeg) >= RAMP_WHEEL_DEG
            f = when {
                // 주차 칸에 넣을 때 끝까지 돌리는 조작(후진·서행)은 램프 회전이 아니다
                big && !f.inTurn && s.gear == Gear.D && s.speedKmh >= RAMP_MIN_KMH -> f.copy(turns = f.turns + 1, inTurn = true)
                !big && abs(s.steeringDeg) < RAMP_WHEEL_DEG / 2 && f.inTurn -> f.copy(inTurn = false)
                else -> f
            }
        }
        // 차단기·정산기 앞에서 P단을 넣었다가 더 내려가도 다시 추정한다
        if (f.tracking && s.parked && f.confirmed == null && f.turns > 0 && f.turns != f.estTurns) {
            val perFloor = settings.rampTurnsPerFloor[f.place] ?: 1f
            val est = Math.round(f.turns / perFloor).coerceAtLeast(1)
            if (est != f.estimate) fx += Effect.Toast("⑦ 지하 ${est}층으로 추정돼요. 맞는지 눌러 확인해 주세요.")
            f = f.copy(estimate = est, estTurns = f.turns)
        }
        // 다시 지상으로 나가면 다음 주차를 위해 비운다
        if (!s.gpsLost && p.gpsLost) f = ParkFloor()
        state = state.copy(floor = f)
    }

    fun confirmFloor(floorNo: Int): List<Effect> {
        val f = state.floor
        if (f.turns > 0) settings = settings.copy(rampTurnsPerFloor = settings.rampTurnsPerFloor + (f.place to f.turns / floorNo.toFloat()))
        state = state.copy(floor = f.copy(confirmed = floorNo, estimate = floorNo))
        return listOf(Effect.Notify("차주 폰", "⑦ 주차 위치 기억", "${f.place.label} 지하 ${floorNo}층에 주차했어요.", Level.INFO))
    }

    // ---------------------------------------------------------------- ⑨ N단 유지 도우미
    fun startNHold() {
        state = state.copy(nhold = NHoldState(NHoldPhase.GUIDE))
    }

    fun stopNHold() {
        state = state.copy(nhold = NHoldState())
    }

    private fun nhold(s: Signals, p: Signals, nowMs: Long, fx: MutableList<Effect>) {
        val n = state.nhold
        val next = when (n.phase) {
            NHoldPhase.GUIDE -> when {
                s.gear == Gear.N && p.ignitionOn && !s.ignitionOn -> {
                    fx += Effect.Speak("N단 유지 중이에요. 3분 안에 운전석 문을 열면 P로 바뀌어요. 이 상태에서는 문이 잠기지 않아요.")
                    n.copy(phase = NHoldPhase.HOLDING, offAtMs = nowMs)
                }
                // N에서는 조작 화면이 잠기므로 남은 단계를 음성으로 알린다
                s.gear == Gear.N && p.gear != Gear.N -> {
                    fx += Effect.Speak("계기판에 안내가 뜨면 OK 버튼을 길게 누르고, 'N단이 유지됩니다'가 보이면 시동을 꺼 주세요.")
                    n
                }
                else -> n
            }
            NHoldPhase.HOLDING -> {
                val held = nowMs - (n.offAtMs ?: nowMs)
                when {
                    // 시동을 다시 켜고 N에서 벗어나 출발하면 유지가 끝난 것으로 본다
                    s.ignitionOn && s.gear != Gear.N && s.gear != Gear.P -> NHoldState()
                    !s.ignitionOn && s.gear == Gear.P && s.driverDoorOpen && held < NHOLD_DOOR_MS -> {
                        fx += Effect.Toast("⑨ 시동을 끈 뒤 3분 안에 운전석 문을 열어 P로 바뀌었어요.")
                        n.copy(phase = NHoldPhase.FAILED)
                    }
                    s.gear == Gear.P -> n.copy(phase = NHoldPhase.DONE)
                    !n.warnedLong && held >= NHOLD_LONG_MS -> {
                        fx += Effect.Notify("차주 폰", "⑨ N단 유지 30분째", "배터리가 방전될 수 있고 문이 잠기지 않은 상태예요. 이동이 끝났으면 P로 바꿔 주세요.", Level.WARN)
                        n.copy(warnedLong = true)
                    }
                    else -> n
                }
            }
            else -> n
        }
        state = state.copy(nhold = next)
    }

    // ---------------------------------------------------------------- ⑩ 겨울 출발 점검
    /** 외기가 [WINTER_C] 이하일 때 출발 전에 볼 것들. */
    fun winterCheck(s: Signals, preset: ComfortPreset? = null): List<String> =
        if (s.outsideTempC > WINTER_C) emptyList() else listOfNotNull(
            "워셔액 부족".takeIf { s.washerLow },
            "타이어 공기압 낮음".takeIf { s.tirePressureLow },
            "배터리·연료 ${s.socPct}%".takeIf { s.socPct < WINTER_LOW_SOC },
            "앞 유리 성에 제거 켜기 추천".takeIf { preset != null && !preset.defrost },
        )

    // ---------------------------------------------------------------- 고장·사고 때 대피 안내(비트박스)
    /**
     * 빠르게 달리다 P단에 세우고 비상등을 켜면 한국도로공사 행동 요령(비상등 → 트렁크 → 대피 → 신고)을 안내한다.
     * 사고를 감지하는 것이 아니라 운전자가 켠 비상등에 답한다. 비상등을 끄거나 다시 출발하면 닫는다.
     */
    private fun evac(s: Signals, p: Signals, nowMs: Long, fx: MutableList<Effect>) {
        if (s.speedKmh >= EVAC_FAST_KMH) lastFastMs = nowMs
        var e = state.evac
        val recentFast = lastFastMs?.let { nowMs - it <= EVAC_WINDOW_MS } == true
        // 정체 때 D단 정차에서 켠 비상등(뒤차 경고)에는 대피 안내를 하지 않는다
        if (!e.active && s.hazardOn && s.parked && recentFast) {
            e = EvacState(active = true, atMs = nowMs)
            fx += Effect.Speak("비상등을 켜셨네요. 트렁크를 열고, 가드레일 밖으로 먼저 대피한 뒤 신고하세요.")
            fx += Effect.Toast("대피 순서: 비상등 → 트렁크 열기 → 가드레일 밖 대피 → 신고")
        }
        if (e.active && (!s.hazardOn || s.speedKmh > 10f)) e = EvacState()
        if (e != state.evac) state = state.copy(evac = e)
    }

    // ---------------------------------------------------------------- ② 빌려준 차 반납 리포트
    fun startLend(s: Signals, nowMs: Long, kind: HandKind = HandKind.LEND, memo: String = "", dueMs: Long? = null): List<Effect> {
        if (state.lend.active || state.valet.active) return listOf(Effect.Toast("이미 진행 중인 맡김이 있어요. 반납한 뒤 새로 시작해 주세요."))
        val gps = serviceGpsUsable(s, nowMs)
        state = state.copy(lend = LendState(
            active = true, start = snap(s, nowMs), kind = kind, proof = listOf(handoverProof("넘김", s, nowMs)),
            checkIn = carCheck(s, nowMs), startLat = s.lat.takeIf { gps }, startLon = s.lon.takeIf { gps },
            startAccuracyM = (s.gpsAccuracyM ?: 0f).takeIf { gps },
            memo = memo.trim().take(MEMO_MAX), dueMs = dueMs?.takeIf { it > nowMs },
        ))
        val title = if (kind == HandKind.SERVICE) "정비 맡기기 시작" else "빌려주기 시작"
        return listOf(Effect.Notify("차주 폰", title, "맡긴 시각과 차량 상태를 기록했어요.", Level.INFO))
    }

    fun endLend(s: Signals, nowMs: Long): List<Effect> {
        val l = state.lend
        if (!l.active) return emptyList()
        val startMs = l.start?.timeMs ?: l.checkIn?.atMs ?: l.proof.firstOrNull { it.what.startsWith("넘김") }?.atMs
        val out = carCheck(s, nowMs)
        val beforeSoc = l.checkIn?.socPct
        val mins = startMs?.let { ((nowMs - it) / 60_000).coerceAtLeast(0) }
        val wheel = if (abs(s.steeringDeg) >= WHEEL_TURNED_DEG) "%+.0f° 틀어짐".format(s.steeringDeg) else "정렬"
        // 리포트를 보려고 차주가 켠 시동은 넘긴 동안의 일이 아니다.
        // shortcut: 직원이 시동을 켜 둔 채 움직이지 않고 넘기면 그 1회도 빠짐, 착좌 기록으로 구분할 때 보강
        val starts = l.starts - if (s.ignitionOn && l.idleStart) 1 else 0
        val report = listOf(
            "이용 시간" to when { mins == null -> "확인 불가"; mins < 60 -> "${mins}분"; else -> "${mins / 60}시간 ${mins % 60}분" },
            "이동 거리" to (out.distanceFrom(l.checkIn)?.let { "%.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가"),
            "배터리·연료" to if (beforeSoc != null && out.socPct != null) "$beforeSoc% → ${out.socPct}%" else "확인 불가",
            "최고 속도" to if (known("speed", s)) "%.0fkm/h".format(java.util.Locale.ROOT, l.maxSpeed) else "확인 불가",
            "급감속" to if (known("speed", s)) "${l.brakes}회" else "확인 불가",
            "시동 켠 횟수" to if (known("ignition", s)) "${starts}회" else "확인 불가",
            "운전석 문 연 횟수" to if (known("doors", s)) "${l.doorOpens}회" else "확인 불가",
            "창문" to if (!known("windowStatus", s)) "확인 불가" else if (s.windowOpen) "열림" else "닫힘",
            "바퀴" to if (known("steering", s)) wheel else "확인 불가",
        ) + listOfNotNull(
            ("정차 중 배터리 사용" to "${l.parkedSocUsed}% (V2L·유틸리티 모드·공조 등)").takeIf { l.parkedSocUsed > 0 && known("soc", s) },
            idleText(l.idleMs, l.idleSinceMs, nowMs)?.let { "정차 중 시동" to it },
        )
        val changes = l.checkIn?.let { diffChecks(it, out) }
        // 맡긴 동안처럼 첫 줄('넘김' 인수 상태)은 남기고 나머지를 최근 줄로 줄인다
        val proof = capped(l.proof + handoverProof("돌려받음", s, nowMs) + listOfNotNull(changes?.let { changesProof(it, nowMs) }))
        if (startMs != null) record(if (l.kind == HandKind.SERVICE) "맡김" else "빌려주기", startMs, nowMs, l.checkIn, out, l.maxSpeed, l.brakes, ok = !l.roamWarned && !l.speedWarned,
            note = handoverNote(l.idleMs, l.idleSinceMs, l.speedWarned, nowMs), proof = proof, memo = l.memo, dueMs = l.dueMs)
        state = state.copy(lend = LendState(report = report, kind = l.kind, endedAtMs = nowMs, proof = proof, checkIn = l.checkIn, checkOut = out, prep = l.prep, roamWarned = l.roamWarned, parkedSocUsed = l.parkedSocUsed))
        val body = report.joinToString(" · ") { "${it.first} ${it.second}" } + (changes?.let { " · 바뀐 것: " + it.joinToString(", ") } ?: "") +
            " · 근거 ${proof.size}줄(시각·출처)은 차 화면 리포트에 있어요: ${proofLine(listOf(proof.first(), proof.last()))}"
        return listOf(Effect.Notify("차주 폰", "② ${l.kind.report}", body, Level.INFO))
    }

    private fun lend(s: Signals, p: Signals, nowMs: Long, brake: Boolean, fx: MutableList<Effect>) {
        var l = state.lend
        if (!l.active) return
        // 반납 예정 시각이 지났는데 아직 반납 확인 전이면 한 번 알린다
        if (l.dueMs != null && !l.dueWarned && nowMs > l.dueMs) {
            fx += Effect.Notify("차주 폰", "반납 예정 시각이 지났어요", "${l.kind.label} · 예정 ${hhmm(l.dueMs)} · 아직 반납 확인 전이에요", Level.WARN)
            l = l.copy(dueWarned = true, proof = capped(l.proof + Proof(nowMs, "반납 예정 ${hhmm(l.dueMs)} 지남", "차주 설정")))
        }
        val ignitionKnown = known("ignition", s) && known("ignition", p)
        val started = ignitionKnown && !p.ignitionOn && s.ignitionOn
        val doorOpened = known("doors", s) && known("doors", p) && !p.driverDoorOpen && s.driverDoorOpen
        val braking = brake && known("speed", s) && known("speed", p)
        l = l.copy(
            maxSpeed = if (known("speed", s)) maxOf(l.maxSpeed, s.speedKmh) else l.maxSpeed, brakes = l.brakes + if (braking) 1 else 0,
            starts = l.starts + if (started) 1 else 0,
            idleStart = s.parked && s.ignitionOn && (started || l.idleStart),
            doorOpens = l.doorOpens + if (doorOpened) 1 else 0,
        )
        // 넘긴 동안 일어난 일을 시각·출처와 함께 남긴다(분쟁 때 '언제'가 근거가 된다)
        val ev = listOfNotNull(
            Proof(nowMs, "시동 ON", src("ignition", s)).takeIf { started },
            Proof(nowMs, "시동 OFF", src("ignition", s)).takeIf { ignitionKnown && p.ignitionOn && !s.ignitionOn },
            Proof(nowMs, "운전석 문 열림", src("doors", s)).takeIf { doorOpened },
            Proof(nowMs, "급감속 · %.0fkm/h".format(java.util.Locale.ROOT, p.speedKmh), src("speed", s)).takeIf { braking },
        )
        if (ev.isNotEmpty()) l = l.copy(proof = capped(l.proof + ev))
        // 정차 중 배터리 사용: 79↔80 흔들림을 두 번 세지 않게 정차 중 최저값보다 내려간 만큼만 센다(충전으로 오르면 기준을 옮긴다)
        l = if (s.parked && p.parked && known("soc", s) && known("soc", p)) {
            val ref = l.parkedSocRef ?: p.socPct
            l.copy(parkedSocUsed = l.parkedSocUsed + (ref - s.socPct).coerceAtLeast(0), parkedSocRef = if (s.socPct > ref + 2) s.socPct else minOf(ref, s.socPct))
        } else l.copy(parkedSocRef = null)
        idleStep(l.idleMs, l.idleSinceMs, s, nowMs).let { (idle, since) -> l = l.copy(idleMs = idle, idleSinceMs = since) }
        if (!l.speedWarned) speedAlert(if (l.kind == HandKind.SERVICE) "정비" else "빌려주기", s, nowMs, fx)?.let { l = l.copy(speedWarned = true, proof = capped(l.proof + it)) }
        if (l.kind == HandKind.SERVICE && !l.roamWarned) l = roam(l, s, nowMs, fx)
        state = state.copy(lend = l)
    }

    private fun serviceGpsUsable(s: Signals, nowMs: Long): Boolean {
        val lat = s.lat ?: return false
        val lon = s.lon ?: return false
        val accuracy = s.gpsAccuracyM ?: if (onCar) return false else 0f
        val fixAt = s.seenAt["gps"] ?: if (onCar) return false else nowMs
        return !s.gpsLost && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 &&
            accuracy.isFinite() && accuracy in 0f..SERVICE_GPS_ACCURACY_M &&
            nowMs - fixAt in 0..SERVICE_GPS_MAX_AGE_MS
    }

    /** 실제 주행·시동·정확한 위치가 함께 확인되고 새 GPS 관측이 연속될 때만 이탈을 확정한다. */
    private fun roam(l: LendState, s: Signals, nowMs: Long, fx: MutableList<Effect>): LendState {
        val km = l.checkIn?.odometerKm?.takeIf { known("odometer", s) && it.isFinite() && it >= 0 }?.let {
            (s.odometerKm - it).takeIf { distance -> distance.isFinite() }
        }
        val lat = l.startLat
        val lon = l.startLon
        val startAccuracy = l.startAccuracyM
        if (!serviceGpsUsable(s, nowMs) || lat == null || lon == null || startAccuracy == null ||
            km == null || km < SERVICE_MOVED_KM || !known("ignition", s) || !s.ignitionOn)
            return l.copy(roamSinceMs = null, roamLastFixMs = null)
        val m = PlaceResolver.distanceM(lat, lon, checkNotNull(s.lat), checkNotNull(s.lon))
        if (m - startAccuracy - (s.gpsAccuracyM ?: 0f) < SERVICE_ROAM_M)
            return l.copy(roamSinceMs = null, roamLastFixMs = null)
        val fixAt = s.seenAt["gps"] ?: nowMs
        val lastFix = l.roamLastFixMs
        if (fixAt == lastFix && nowMs - fixAt <= SERVICE_FIX_GAP_MS) return l
        val continuous = lastFix != null && fixAt > lastFix && fixAt - lastFix <= SERVICE_FIX_GAP_MS
        val since = l.roamSinceMs.takeIf { continuous } ?: fixAt
        val candidate = l.copy(roamSinceMs = since, roamLastFixMs = fixAt)
        if (fixAt - since < SERVICE_ROAM_HOLD_MS) return candidate
        val what = "주행 %.1fkm · 맡긴 곳에서 약 %,.0fm · 위치 30초 연속 확인".format(java.util.Locale.ROOT, km, m)
        fx += Effect.Notify("차주 폰", "맡긴 차가 정비소를 벗어났어요", "$what. 시운전일 수도 있으니 업체에 확인해 보세요.", Level.WARN)
        return candidate.copy(roamWarned = true, proof = capped(l.proof +
            Proof(nowMs, "정비소 벗어남 · $what", src("gps", s) + " · " + src("odometer", s) + " · " + src("ignition", s))))
    }

    // ---------------------------------------------------------------- 마이현대: 드라이빙 인사이트 재료
    private fun driveStats(s: Signals, p: Signals, nowMs: Long, brake: Boolean, accel: Boolean) {
        fun speedReady(sample: Signals) = !onCar || (known("speed", sample) && sample.speedKmh.isFinite() && sample.speedKmh >= 0f)
        fun motionReady(sample: Signals) = known("gear", sample) && speedReady(sample)
        fun odoReady(sample: Signals) = !onCar || (known("odometer", sample) && sample.odometerKm.isFinite() && sample.odometerKm >= 0.0)
        fun socReady(sample: Signals) = !onCar || (known("soc", sample) && sample.socPct in 0..100)
        val motionObserved = motionReady(s) && motionReady(p)
        val driving = motionObserved && !(s.parked && p.parked)
        // 서 있는 동안 늘어난 거리(첫 실제 값이 시뮬레이터 기본값을 덮는 순간 등)는 주행이 아니다
        // 양쪽 주행계가 실제 수신된 구간만 더해 0→14,000km 초기 수신을 5km 이동으로 오인하지 않는다.
        val km = if (driving && odoReady(s) && odoReady(p)) (s.odometerKm - p.odometerKm).coerceIn(0.0, 5.0) else 0.0
        // 배터리 표시가 79↔80으로 흔들려도 두 번 세지 않도록 주행 중 최저값보다 내려간 만큼만 센다.
        // 주차 중 소모(원격 공조 등)는 1%당 거리를 흐리므로 세지 않고 기준만 옮긴다
        val energyObserved = socReady(s) && socReady(p)
        val ref = socRef ?: if (socReady(p)) p.socPct else s.socPct
        val socDrop = if (driving && energyObserved) (ref - s.socPct).coerceAtLeast(0) else 0
        socRef = when {
            !socReady(s) -> null
            !energyObserved || !driving || s.socPct > ref + 2 -> s.socPct
            else -> minOf(ref, s.socPct)
        }
        val braking = brake && speedReady(s) && speedReady(p)
        val accelerating = accel && speedReady(s) && speedReady(p)
        if (km <= 0.0 && !braking && !accelerating && socDrop <= 0) return
        state = state.copy(stats = MyHyundai.addToDay(state.stats, date(nowMs), km, braking, accelerating, socDrop))
    }

    fun loadStats(stats: List<DailyStat>) {
        state = state.copy(stats = stats)
    }

    // ---------------------------------------------------------------- 마이현대: 컨트롤
    fun control(kind: ControlKind, s: Signals, nowMs: Long = 0): List<Effect> {
        val refuse = when {
            !controlAllowed() -> "이 차는 앱 제어를 지원하지 않아요. 마이현대 폰 앱의 컨트롤을 이용해 주세요."
            kind == ControlKind.HORN && !s.parked -> "경적은 P단에서만 울릴 수 있어요."
            else -> null
        }
        if (refuse != null) return listOf(Effect.Toast(refuse))
        // 반영되면 보일 신호. 경적은 신호로 확인할 수 없어 기다리지 않는다
        val expect = when (kind) {
            ControlKind.LOCK -> true
            ControlKind.UNLOCK -> false
            ControlKind.CLIMATE_ON -> true
            ControlKind.CLIMATE_OFF -> false
            ControlKind.HAZARD -> !s.hazardOn
            ControlKind.HORN -> null
        }
        if (expect != null) {
            if (state.pendingControl != null) noteControl(nowMs, "새 제어 요청으로 이전 요청 확인을 종료함", ControlResult.UNCONFIRMED)
            state = state.copy(pendingControl = PendingControl(kind, expect, nowMs))
        }
        val name = controlSignal(kind)
        val before = if (name == null) "경적은 반영을 알려 주는 차 신호가 없어요"
            else "보낼 때 ${signalNote(name, s, nowMs)}: ${controlValue(kind, s)}"
        logControl(ControlEvidence(kind, nowMs, listOf(nowMs to "${kind.label} 요청 보냄", nowMs to before), if (expect == null) ControlResult.NO_SIGNAL else ControlResult.WAITING))
        val sent = if (expect != null) "${kind.label} 요청을 보냈어요 · 차 신호로 확인 중" else "${kind.label} 요청을 보냈어요 (경적은 차 신호로 확인할 수 없어요)"
        // 확인됨 보강: 잠금으로는 막히지 않는 열린 곳을 함께 알린다. 차주 폰 알림은 잠금이 차 신호로 확인된 뒤에 보낸다(controlCheck)
        val open = openSpots(s)
        val toast = if (kind == ControlKind.LOCK && open.isNotEmpty()) "$sent · ${open.joinToString("·")}은(는) 아직 열려 있어요" else sent
        return listOf(Effect.Control(kind), Effect.Toast(toast))
    }

    private fun openSpots(s: Signals) = listOfNotNull("창문".takeIf { s.windowOpen }, "트렁크".takeIf { s.trunkOpen })

    /** 반영을 확인할 차 신호 이름(CarBridge 구독 이름과 같다). */
    fun controlSignal(kind: ControlKind) = when (kind) {
        ControlKind.LOCK, ControlKind.UNLOCK -> "lock"
        ControlKind.CLIMATE_ON, ControlKind.CLIMATE_OFF -> "ac"
        ControlKind.HAZARD -> "hazard"
        ControlKind.HORN -> null
    }

    private fun controlValue(kind: ControlKind, s: Signals) = when (kind) {
        ControlKind.LOCK, ControlKind.UNLOCK -> if (s.doorsLocked) "잠김" else "잠금 해제"
        ControlKind.CLIMATE_ON, ControlKind.CLIMATE_OFF -> if (s.acOn) "공조 켜짐" else "공조 꺼짐"
        ControlKind.HAZARD -> if (s.hazardOn) "비상등 켜짐" else "비상등 꺼짐"
        ControlKind.HORN -> "-"
    }

    /** 이 신호를 언제 읽었는지. 읽은 기록이 없으면(시뮬레이터 기본값) 그렇게 말한다. */
    private fun signalNote(name: String, s: Signals, nowMs: Long): String {
        val at = s.seenAt[name] ?: return "시뮬레이터 값(차에서 읽은 기록 없음)"
        val ago = (nowMs - at).coerceAtLeast(0) / 1000
        return if (ago < 2) "방금 읽은 차 신호" else if (ago < 120) "${ago}초 전 읽은 차 신호" else "${ago / 60}분 전 읽은 차 신호"
    }

    fun loadControls(log: List<ControlEvidence>, nowMs: Long = System.currentTimeMillis()) {
        state = state.copy(pendingControl = null, controlLog = log.take(30).map { ev ->
            if (ev.result == ControlResult.WAITING) ev.copy(result = ControlResult.UNCONFIRMED,
                steps = ev.steps + (nowMs to "앱 재시작으로 결과 확인이 중단됨 · 차에서 직접 확인해 주세요")) else ev
        })
    }

    private fun logControl(ev: ControlEvidence) {
        state = state.copy(controlLog = (listOf(ev) + state.controlLog).take(30))
    }

    /** 가장 최근 근거에 한 줄을 더하고 결과를 바꾼다. */
    private fun noteControl(nowMs: Long, line: String, result: ControlResult) {
        val pending = state.pendingControl ?: return
        val index = state.controlLog.indexOfFirst { it.kind == pending.kind && it.sentAtMs == pending.atMs }
        if (index < 0) return
        state = state.copy(controlLog = state.controlLog.mapIndexed { i, ev ->
            if (i == index) ev.copy(steps = ev.steps + (nowMs to line), result = result) else ev
        })
    }

    /**
     * 원격 제어가 차 신호로 반영됐는지 본다. 명령마다 기다리는 시간이 다르다([verifyMs]).
     * 시간이 지나도 맞지 않을 때, 보낸 뒤 읽은 차 신호가 반대 상태면 '실패', 신호가 아예 오지 않았으면 '결과 미확인'으로 두고
     * [CONTROL_LATE_MS]까지 더 기다려 늦게 반영되면 다시 알린다(명령 수락과 상태 반영은 늦게 맞을 수 있다).
     */
    private fun controlCheck(s: Signals, nowMs: Long, fx: MutableList<Effect>) {
        val pc = state.pendingControl ?: return
        val name = controlSignal(pc.kind) ?: return
        val actual = when (pc.kind) {
            ControlKind.LOCK, ControlKind.UNLOCK -> s.doorsLocked
            ControlKind.CLIMATE_ON, ControlKind.CLIMATE_OFF -> s.acOn
            ControlKind.HAZARD -> s.hazardOn
            ControlKind.HORN -> return
        }
        val elapsed = nowMs - pc.atMs
        val secs = elapsed / 1000
        // 보낸 뒤에 다시 읽은 신호인지(CarBridge가 읽을 때마다 시각을 남긴다)
        val freshRead = s.seenAt[name]?.let { it >= pc.atMs } == true
        when {
            actual == pc.expect -> {
                val late = pc.unconfirmed
                fx += Effect.Toast("✓ ${pc.kind.label} ${if (late) "늦게 " else ""}확인됨 · 차 신호로 반영을 확인했어요")
                noteControl(nowMs, "${secs}초 뒤 ${signalNote(name, s, nowMs)}: ${controlValue(pc.kind, s)}", if (late) ControlResult.LATE else ControlResult.CONFIRMED)
                if (late) fx += Effect.Notify("차주 폰", "✓ ${pc.kind.label} 늦게 확인됨", "보낸 지 ${secs}초 뒤 차 신호로 반영을 확인했어요.", Level.OK)
                // 잠금이 차 신호로 확인된 뒤에만 '잠갔지만'을 보낸다
                val open = openSpots(s)
                if (pc.kind == ControlKind.LOCK && open.isNotEmpty()) fx += Effect.Notify(
                    "차주 폰", "문은 잠갔지만 ${open.joinToString("·")}이(가) 열려 있어요",
                    "열어 둔 채면 비가 들이치거나 물건을 꺼내 갈 수 있어요. 차에서 닫아 주세요.", Level.WARN,
                )
                state = state.copy(pendingControl = null)
            }
            pc.unconfirmed && elapsed >= CONTROL_LATE_MS -> {
                fx += Effect.Notify(
                    "차주 폰", "${pc.kind.label} 요청이 끝내 확인되지 않았어요",
                    "${CONTROL_LATE_MS / 60_000}분 동안 차 신호로 반영을 확인하지 못했어요. 차에서 직접 확인해 주세요.", Level.WARN,
                )
                noteControl(nowMs, "${secs}초 동안 확인하지 못함 → 차에서 직접 확인 필요", ControlResult.UNCONFIRMED)
                state = state.copy(pendingControl = null)
            }
            pc.unconfirmed || elapsed < verifyMs(pc.kind) -> Unit
            freshRead -> {
                fx += Effect.Notify(
                    "차주 폰", "${pc.kind.label} 실패 · 차가 '${controlValue(pc.kind, s)}'(으)로 알려 왔어요",
                    "보낸 뒤 차 신호를 다시 읽었지만 반영되지 않았어요. 다시 시도하거나 차에서 직접 확인해 주세요.", Level.WARN,
                )
                noteControl(nowMs, "${secs}초 뒤 ${signalNote(name, s, nowMs)}: ${controlValue(pc.kind, s)} → 반대 상태", ControlResult.FAILED)
                state = state.copy(pendingControl = null)
            }
            else -> {
                fx += Effect.Notify(
                    "차주 폰", "${pc.kind.label} 결과 미확인 · 차 신호가 아직 오지 않았어요",
                    "실패로 단정하지 않고 ${CONTROL_LATE_MS / 60_000}분까지 기다려요. 반영되면 다시 알려 드려요.", Level.INFO,
                )
                noteControl(nowMs, "${secs}초 동안 새 차 신호가 오지 않음 → 결과 미확인, 더 기다림", ControlResult.UNCONFIRMED)
                state = state.copy(pendingControl = pc.copy(unconfirmed = true))
            }
        }
    }

    // ---------------------------------------------------------------- 마이현대: 차량 서비스·카라이프·디지털 키
    fun addService(item: String, s: Signals, nowMs: Long): List<Effect> {
        settings = settings.copy(serviceLog = listOf(ServiceRecord(date(nowMs), s.odometerKm, item)) + settings.serviceLog)
        return listOf(Effect.Toast("$item 교체를 %.0fkm 시점으로 기록했어요.".format(s.odometerKm)))
    }

    /** 블루핸즈 정비·세차 예약 요청. 정비는 차가 확인한 사실을 함께 보낸다. 실제 예약은 마이현대 서버가 필요해 목업이다. */
    fun book(kind: String, date: String, time: String, shop: String, s: Signals, nowMs: Long = System.currentTimeMillis()): List<Effect> {
        val note = if (kind != "정비") "" else
            MyHyundai.serviceNote(s, MyHyundai.consumables(s.odometerKm, settings.serviceLog, state.stats, Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()), diagnose(s)).joinToString(" · ")
        settings = settings.copy(bookings = listOf(Booking(date, time, kind, shop, note)) + settings.bookings.take(4))
        return listOf(Effect.Notify("차주 폰", "$kind 예약 요청 (목업)", "$date $time · $shop" + if (note.isEmpty()) "" else " · 함께 보낸 차 상태: $note", Level.INFO))
    }

    /** 긴급출동 요청. 상담원이 되묻지 않도록 차 상황을 함께 보낸다(연동 목업). */
    fun emergency(s: Signals): List<Effect> = listOf(
        Effect.Notify("긴급출동 상담원", "긴급출동 요청 (연동 목업)", MyHyundai.emergencySummary(s, state).joinToString(" · "), Level.WARN),
        Effect.Toast("긴급출동을 요청했어요. 차 상황을 함께 보냈어요 (연동 목업)."),
    )

    fun callValet(): List<Effect> {
        startValet().let { if (it.isNotEmpty()) return it }
        return listOf(Effect.Notify("차주 폰", "대리운전 호출 요청 (카라이프 연동 목업)", "기사님이 오시면 차 화면에서 동의를 받고 ① 대리 안심 주차가 시작돼요.", Level.INFO))
    }

    /** 디지털 키 공유. 키를 받은 사람은 ② 운전자로 등록되고, 첫 탑승 때 자세를 등록하면 알아본다. */
    fun shareKey(name: String, until: String): List<Effect> {
        if (settings.sharedKeys.any { it.name == name }) return listOf(Effect.Toast("$name 님에게 이미 공유했어요."))
        val profiles = if (settings.profiles.any { it.name == name }) settings.profiles else settings.profiles + DriverProfile(name, emptyList(), insured = false)
        settings = settings.copy(sharedKeys = settings.sharedKeys + SharedKey(name, until), profiles = profiles)
        return listOf(Effect.Notify("차주 폰", "디지털 키 공유 (목업)", "$name 님 · $until 까지. 보험 운전자 범위를 꼭 확인하세요.", Level.WARN))
    }

    fun revokeKey(name: String): List<Effect> {
        settings = settings.copy(sharedKeys = settings.sharedKeys.filter { it.name != name })
        return listOf(Effect.Toast("$name 님의 디지털 키 공유를 해제했어요."))
    }

    /** 주정차 단속 알림: 차를 두고 내린 곳이 단속 구역이면 한 번 알린다. */
    private fun enforcement(s: Signals, fx: MutableList<Effect>) {
        val away = s.parked && !s.driverSeated && !s.ignitionOn
        if (settings.enforcementAlert && away && s.enforcementZone && !state.enforcementNotified) {
            fx += Effect.Notify("차주 폰", "주정차 단속 구역이에요 (지자체 알림 연동 목업)", "단속 카메라 구역에 주차돼 있어요. 차를 옮겨 주세요.", Level.WARN)
            state = state.copy(enforcementNotified = true)
        }
        if (!away) state = state.copy(enforcementNotified = false)
    }

    // ---------------------------------------------------------------- ⑧ 확인 도우미
    fun diagnose(s: Signals): List<DiagFinding> {
        val out = mutableListOf<DiagFinding>()
        if (s.cruiseOverride) out += DiagFinding("R01", "크루즈가 해제된 상태예요", "브레이크를 밟으면 크루즈가 해제되는 설정과 관련 있을 수 있어요.", "다시 켜는 방법")
        if (s.wiper == Wiper.AUTO) out += DiagFinding("R02", "와이퍼가 자동(AUTO) 모드예요", "비가 감지되면 와이퍼가 저절로 움직일 수 있어요.", "M01")
        if (s.recircOn && !s.acOn) out += DiagFinding("R03", "내기순환만 켜져 있어요", "에어컨이 꺼진 채 내기순환이면 유리에 김이 서릴 수 있어요.", "M07")
        if (s.ambientByDriveMode) out += DiagFinding("R04", "주행 모드 연동 실내등이에요", "주행 모드에 따라 실내등 색이 바뀌는 설정이에요.")
        val open = listOfNotNull(
            "왼쪽 뒷문".takeIf { s.row2LeftDoorOpen }, "오른쪽 뒷문".takeIf { s.row2RightDoorOpen },
            "트렁크".takeIf { s.trunkOpen }, "창문".takeIf { s.windowOpen },
        )
        if (open.isNotEmpty()) out += DiagFinding("R05", "열려 있는 곳이 있어요", open.joinToString(", ") + "이(가) 완전히 닫히지 않았어요.")
        if (s.washerLow) out += DiagFinding("R06", "워셔액이 부족해요", "소모품 보충이 필요해요.", "SERVICE")
        val unbelted = listOfNotNull(
            "조수석".takeIf { s.passengerSeated && !s.passengerBelt }, "뒷좌석".takeIf { seated2(s) && !s.row2Belt && caps.row2Occupancy },
        )
        if (unbelted.isNotEmpty()) out += DiagFinding("R07", "벨트가 풀린 좌석이 있어요", unbelted.joinToString(", ") + " 벨트가 풀려 있으면 경고음이 울려요.")
        if (out.isEmpty()) out += DiagFinding("R99", "읽을 수 있는 설정에서는 관련 항목을 찾지 못했어요", "경고등이 켜졌거나 계속 이상하면 블루핸즈 점검을 받으세요.", "SERVICE")
        return out
    }

    // ---------------------------------------------------------------- ⑨ 첫 운전 확인
    fun startMissions(nowMs: Long) {
        state = state.copy(missions = MissionState(true, nowMs))
    }

    private fun missions(s: Signals, p: Signals, nowMs: Long, fx: MutableList<Effect>) {
        val m = state.missions
        if (!m.active || !s.parked) return
        val hit = mapOf(
            "M01" to (p.wiper == Wiper.OFF && s.wiper != Wiper.OFF),
            "M02" to (p.turnSignal != Turn.LEFT && s.turnSignal == Turn.LEFT),
            "M03" to (!p.lowBeamOn && s.lowBeamOn),
            "M04" to (p.posture.firstOrNull() != s.posture.firstOrNull()),
            "M05" to (p.hvacTempC != s.hvacTempC),
            "M06" to (!p.doorsLocked && s.doorsLocked),
            "M07" to (!p.defrosterOn && s.defrosterOn),
        )
        val items = m.items.map {
            if (!it.done && hit[it.id] == true) {
                fx += Effect.Toast("⑨ ${it.title} 미션 성공")
                it.copy(done = true, elapsedSec = (nowMs - m.startMs) / 1000)
            } else it
        }
        state = state.copy(missions = m.copy(items = items))
    }

    /** 내 기록 지우기: 판정 상태와 기록을 처음으로 되돌린다. */
    fun reset() {
        state = EngineState()
        prev = null
    }

    /** 시간이 지나야 판정되는 조건(10초 정지, 60초 서행, 10분 자동 종료 등)을 보는 중인지. */
    fun needsTick(): Boolean {
        val st = state
        // ①③④의 정지 시간 조건은 정차(P단·시동 OFF)에서만 보므로 달리거나 D단 정지 중에는 깨우지 않는다
        val stoppedNow = prev?.let { stopped(it) } == true
        return stoppedNow && (st.valet.tracking || st.trip.open != null ||
            st.escort.phase == EscortPhase.RIDING || st.escort.phase == EscortPhase.DEPARTED_UNVERIFIED) ||
            st.school.phase == SchoolPhase.WATCH || st.comfort.phase == ComfortPhase.REMOTE_RUNNING ||
            (st.nhold.phase == NHoldPhase.HOLDING && !st.nhold.warnedLong) ||
            (st.exit.unlockedSinceMs != null && !st.exit.lockReminded) || st.pendingControl != null ||
            // ① 경로 이탈은 남은 거리가 늘어난 채 1분 머물러야 판정하므로, 내비 값이 다시 오지 않아도 깨운다
            st.valet.naviOffSinceMs != null
    }

    /** 주행 중에는 조작 없이 읽기만 하는 3줄 요약. */
    fun drivingLines(): List<String> {
        val st = state
        return listOfNotNull(
            "⚠ 대피: 트렁크 열고 가드레일 밖으로".takeIf { st.evac.active },
            // 주행 중에는 충전 앱을 번갈아 보지 않아도 되게 도착 배터리를 요약에 띄운다
            prev?.takeIf { settings.carModel?.ev == true && known("soc", it) }?.let(MyHyundai::tripBattery)
                ?.takeIf { it.low }?.let { "배터리: 도착 약 ${it.arrivalPct.coerceAtLeast(0)}% · 충전 경유 권장" },
            // N단은 조작 화면이 잠기므로 남은 단계를 요약에 띄운다(N을 벗어나면 지난 단계가 남지 않게)
            "⑨ N단 유지: ${if (st.nhold.phase == NHoldPhase.GUIDE) "OK 길게 누른 뒤 시동 끄기" else st.nhold.phase.label}"
                .takeIf { prev?.gear == Gear.N && (st.nhold.phase == NHoldPhase.GUIDE || st.nhold.phase == NHoldPhase.HOLDING) },
            // 주행 중엔 대리 화면이 잠기므로 현대 내비 도착 예정 시각을 요약에 붙인다
            ("① 대리: ${st.valet.phase.label}" + (st.valet.naviEtaMs?.takeIf { st.valet.phase == ValetPhase.DRIVING }?.let { " · ${hhmm(it)} 도착 예정" } ?: "")).takeIf { st.valet.active },
            "④ 부축 동행: ${st.escort.phase.label}".takeIf { st.escort.phase != EscortPhase.OFF },
            "⑤ 스쿨존: ${st.school.phase.label} · 최고 %.0fkm/h".format(st.school.maxSpeed).takeIf { st.school.phase == SchoolPhase.WATCH },
            "⑥ 승차감: 급감속 ${st.chauffeur.brakes}회 · 급가속 ${st.chauffeur.accels}회".takeIf { st.chauffeur.active },
            "③ 운행 기록: ${st.trip.open?.from?.label}에서 출발".takeIf { st.trip.open != null },
            "② 운전자: ${st.driver.name}".takeIf { st.driver.verdict != DriverVerdict.NOT_CHECKED },
        ).take(3).ifEmpty { listOf("현대 카가드가 차 상태를 지켜보고 있어요") }
    }

    companion object {
        const val VALET_HOLD_MS = 10_000L
        const val TRIP_END_MS = 10_000L
        const val HARSH_KMH_PER_S = 8.0
        const val WHEEL_TURNED_DEG = 30f
        const val KNOWN_DIST = 0.10f
        const val ASK_DIST = 0.25f
        const val SCHOOL_LIMIT_KMH = 30f
        const val SCHOOL_WATCH_MS = 60_000L
        const val SCHOOL_WATCH_KM = 0.3
        const val TRUNK_LOAD_MS = 5_000L
        /** 원격 공조 최대 실행 시간과 최소 배터리 [제안값 · 블루링크 정책 확인 필요]. */
        const val REMOTE_MINUTES = 10
        const val REMOTE_MIN_SOC = 20
        /** 하차 뒤 미잠금 알림까지 [제안값]. */
        const val LOCK_REMIND_MS = 60_000L
        /** 이 속도 이상으로 달리다 2분 안에 P단에 세우고 비상등을 켜면 대피 안내 [제안값]. */
        const val EVAC_FAST_KMH = 60f
        /** 원격 제어가 차 신호로 반영되기를 기다리는 가장 긴 기본 시간(공조) [제안값]. 명령별 시간은 [verifyMs]. */
        const val CONTROL_VERIFY_MS = 20_000L
        /** '결과 미확인' 뒤 늦은 반영을 더 기다리는 시간(보낸 때부터) [제안값]. */
        const val CONTROL_LATE_MS = 120_000L
        /** 빌려주기·맡김 근거로 남길 최대 줄 수(첫 줄 '넘김'은 늘 남긴다). */
        const val PROOF_MAX = 20
        /** 맡김 메모 최대 글자 수. */
        const val MEMO_MAX = 200
        /** ① 경로 이탈: 내비 남은 거리가 가장 가까웠을 때보다 이만큼 늘어난 채 이 시간 머물면 [제안값]. */
        const val NAVI_OFF_KM = 1.5f
        const val NAVI_OFF_MS = 60_000L
        /** 정비소 이탈 확인 기준 [실측 전 제안값]. 위치 오차 반경도 거리에서 뺀다. */
        const val SERVICE_MOVED_KM = 0.2
        const val SERVICE_ROAM_HOLD_MS = 30_000L
        const val SERVICE_GPS_ACCURACY_M = 50f
        const val SERVICE_GPS_MAX_AGE_MS = 30_000L
        const val SERVICE_FIX_GAP_MS = 15_000L
        /** 맡긴 차 과속 알림 기준과 이력에 남길 공회전 최소 시간 [제안값]. */
        const val HANDOVER_SPEED_KMH = 130f
        const val IDLE_NOTE_MS = 5 * 60_000L
        const val SERVICE_ROAM_M = 1000.0
        const val NO_CHANGE = "인수 때와 달라진 것 없음"

        /** 인수 때([a])와 돌려받을 때([b])를 비교한 '바뀐 것' 줄. 한쪽이라도 못 읽은 칸은 '확인 불가'로 모은다. */
        fun diffChecks(a: CarCheck, b: CarCheck): List<String> {
            val out = mutableListOf<String>()
            val unknown = mutableListOf<String>()
            fun <T : Any> both(label: String, x: T?, y: T?, f: (T, T) -> Unit) { if (x == null || y == null) unknown += label else f(x, y) }
            fun flag(label: String, x: Boolean?, y: Boolean?, on: String) = both(label, x, y) { p, q ->
                if (!p && q) out += "$on 새로 표시" else if (p && !q) out += "$on 표시 꺼짐"
            }
            fun open(label: String, x: Boolean?, y: Boolean?, t: String, f: String) = both(label, x, y) { p, q -> if (p != q) out += "$label ${if (p) t else f} → ${if (q) t else f}" }
            both("경고등", a.warnings, b.warnings) { p, q ->
                (q - p).takeIf { it.isNotEmpty() }?.let { out += "경고등 새로 켜짐: " + it.joinToString(", ") }
                (p - q).takeIf { it.isNotEmpty() }?.let { out += "경고등 꺼짐: " + it.joinToString(", ") }
            }
            flag("타이어", a.tireLow, b.tireLow, "타이어 저압")
            flag("엔진오일", a.oilLow, b.oilLow, "엔진오일 부족")
            flag("워셔액", a.washerLow, b.washerLow, "워셔액 부족")
            both("주행거리", a.odometerKm, b.odometerKm) { p, q -> if (q - p >= 0.05) out += "주행거리 +%.1fkm".format(java.util.Locale.ROOT, q - p) }
            both("배터리", a.socPct, b.socPct) { p, q -> if (p != q) out += "배터리 $p% → $q%" }
            open("창문", a.windowOpen, b.windowOpen, "열림", "닫힘")
            open("트렁크", a.trunkOpen, b.trunkOpen, "열림", "닫힘")
            open("문", a.doorsLocked, b.doorsLocked, "잠김", "잠금 풀림")
            if (unknown.isNotEmpty()) out += "확인 불가: " + unknown.joinToString(", ")
            return out.ifEmpty { listOf(NO_CHANGE) }
        }
        /** 명령별 확인 대기 시간 [제안값]: 문·비상등은 바로 바뀌고 공조는 늦게 켜진다. 실차 측정으로 고쳐야 한다. */
        fun verifyMs(kind: ControlKind) = when (kind) {
            ControlKind.LOCK, ControlKind.UNLOCK -> 10_000L
            ControlKind.HAZARD -> 5_000L
            ControlKind.CLIMATE_ON, ControlKind.CLIMATE_OFF -> CONTROL_VERIFY_MS
            ControlKind.HORN -> 0L
        }
        const val EVAC_WINDOW_MS = 120_000L
        /** 현대 취급설명서: 시동 OFF 후 3분 안에 운전석 문을 열면 P로 전환. */
        const val NHOLD_DOOR_MS = 3 * 60_000L
        /** N단 장시간 유지 경고 [제안값]. */
        const val NHOLD_LONG_MS = 30 * 60_000L
        /** 램프 회전으로 볼 앞바퀴 조향각 [제안값]. */
        const val RAMP_WHEEL_DEG = 25f
        /** 램프 회전으로 셀 최저 전진 속도. 주차 칸 진입(후진·서행)을 뺀다 [제안값]. */
        const val RAMP_MIN_KMH = 5f
        /** 이보다 짧은 이동(재주차·기어만 바꿈)은 운행으로 남기지 않는다. 0.1km 단위 계기판 값 한 칸 사이로 둔다 [제안값]. */
        const val TRIP_MIN_KM = 0.15
        /** 겨울 점검을 켜는 외기 온도·배터리 기준 [제안값]. */
        const val WINTER_C = 3f
        const val WINTER_LOW_SOC = 30
        const val PRIVATE_PURPOSE = "비업무(사적 운행)"
        val PURPOSES = listOf("거래처 방문", "출퇴근", "회의·세미나", "현장 점검", PRIVATE_PURPOSE)

        fun hhmm(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
        fun hhmmss(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        /** 운행기록부 보관 기간. 법인 장부 보관 5년(세무 자료 기준, 법령 원문 [확인 필요])이 지난 기록은 지운다. */
        const val TRIP_KEEP_YEARS = 5L

        fun keepRecent(logs: List<TripLog>, today: java.time.LocalDate): List<TripLog> = logs.filter {
            runCatching { !java.time.LocalDate.parse(it.date).isBefore(today.minusYears(TRIP_KEEP_YEARS)) }.getOrDefault(true)
        }

        /** 엑셀이 =, +, -, @로 시작하는 칸을 수식으로 실행하지 않도록 앞에 '를 붙인다. */
        fun csvSafe(v: String) = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v

        /** 목적을 고르지 않은 운행. 업무 거리에 넣지 않는다. */
        const val UNCLASSIFIED = "미분류"

        /**
         * 국세청 「업무용승용차 운행기록부」 서식 기준 열 순서(서식 원문 대조 [확인 필요]).
         * 법인명·사업자등록번호·자동차등록번호는 앱에 입력할 곳이 없어 빈칸으로 두고 담당자가 채운다.
         */
        fun tripCsv(logs: List<TripLog>, carModel: CarModel? = null): String = buildString {
            fun row(vararg v: String) = append(v.joinToString(",") { "\"" + csvSafe(it).replace("\"", "\"\"") + "\"" }).append('\n')
            fun km(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
            val sorted = logs.sortedBy { it.date }
            append('\uFEFF') // 엑셀 한글 깨짐 방지 BOM
            row("사업연도", "법인명", "사업자등록번호", "차종", "자동차등록번호")
            row(sorted.firstOrNull()?.date?.take(4).orEmpty(), "", "", carModel?.label.orEmpty(), "")
            row("사용일자", "사용자(부서)", "사용자(성명)", "주행 전 계기판 거리(km)", "주행 후 계기판 거리(km)", "주행거리(km)", "출퇴근용(km)", "일반 업무용(km)", "비고")
            var total = 0.0
            var work = 0.0
            sorted.forEach {
                val d = km(it.distKm)
                val counted = it.business && it.purpose != UNCLASSIFIED
                total += it.distKm
                if (counted) work += it.distKm
                // 출발지·도착지는 서식 열이 아니라 방문 근거로 비고에 남긴다
                row(it.date, "", it.driver, km(it.startOdo), km(it.endOdo), d,
                    if (counted && it.purpose == "출퇴근") d else "", if (counted && it.purpose != "출퇴근") d else "",
                    "${it.purpose} (${it.from}→${it.to})")
            }
            row("사업연도 총주행거리(km)", "사업연도 업무용 사용거리(km)", "업무사용비율(%)")
            row(km(total), km(work), if (total > 0) km(work / total * 100) else "")
        }
    }
}
