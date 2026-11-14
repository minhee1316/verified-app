package com.verified.incar

import android.app.Application
import com.verified.incar.core.Capabilities
import com.verified.incar.core.CarModel
import com.verified.incar.core.Effect
import com.verified.incar.core.EngineState
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Place
import com.verified.incar.core.Level
import com.verified.incar.core.Settings
import com.verified.incar.core.Signals
import com.verified.incar.core.VerdictEngine
import com.verified.incar.core.Pin
import com.verified.incar.vehicle.CarBridge
import com.verified.incar.vehicle.PlaceTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

/** delivered는 이전 버전의 오프라인 보관 표시. 실제 휴대폰 전송 상태는 phoneStatus에만 기록한다. */
data class SentNotice(
    val timeMs: Long, val target: String, val title: String, val body: String, val level: Level,
    val delivered: Boolean = true, val phoneStatus: PhoneDelivery? = null, val phoneId: String? = null,
    val phoneExample: Boolean = false, val phoneAttempts: Int = 0, val phoneRetryAt: Long = 0,
)

data class UiState(
    val signals: Signals = Signals(),
    val engine: EngineState = EngineState(),
    val settings: Settings = Settings(),
    val caps: Capabilities = Capabilities(),
    val notices: List<SentNotice> = emptyList(),
    val toast: String = VerifiedRuntime.IDLE_TOAST,
    val carConnected: Boolean = false,
    val liveSignals: Set<String> = emptySet(),
    val nowMs: Long = 0,
    val drivingLines: List<String> = emptyList(),
    val diag: List<com.verified.incar.core.DiagFinding> = emptyList(),
    /** 앱 제어 지원 차인지. 아니면 제어 기능을 숨기고 주행 기능만 보여 준다. */
    val canControl: Boolean = false,
    /** 실제 GPS로 장소를 판정 중인지(위치 권한 + 핀 등록). */
    val locationOn: Boolean = false,
    /** ⑩ 겨울 출발 점검 항목(외기 3℃ 이하일 때만). */
    val winter: List<String> = emptyList(),
    // ---- 마이현대
    val consumables: List<com.verified.incar.core.ConsumableStatus> = emptyList(),
    val thisWeek: com.verified.incar.core.WeekInsight? = null,
    val lastWeek: com.verified.incar.core.WeekInsight? = null,
    val feed: List<com.verified.incar.core.FeedCard> = emptyList(),
    // ---- 차주 잠금
    val ownerPinSet: Boolean = false,
    val ownerUnlocked: Boolean = true,
    val simAllowed: Boolean = false,
    /** 휴대폰 체험 모드(예시 차량·예시 값). [trialAvailable]이면 홈에서 체험을 시작할 수 있다. */
    val trial: Boolean = false,
    val trialAvailable: Boolean = false,
    val phoneTopic: String? = null,
    // ---- 서버 연결 (마이현대는 서버가 끊기면 앱 전체가 멈춘다는 불만이 많다)
    val online: Boolean = true,
    val simOffline: Boolean = false,
    val simControlFail: Boolean = false,
)

/**
 * 판정 엔진·신호·저장소를 묶은 앱 전체 런타임. 화면이 아니라 [VerifiedApp]이 들고 있어서,
 * 내비를 앞에 띄워도 [MonitorService]가 프로세스를 살려 두는 동안 판정이 계속된다.
 * [simTicker]: 1초 타이머. 화면 테스트는 시간을 skipSeconds로만 움직이려고 끈다.
 */
class VerifiedRuntime internal constructor(
    private val app: Application, simTicker: Boolean, private val sendPhone: (String, SentNotice) -> PhoneRelay.Result,
) {
    constructor(app: Application, simTicker: Boolean = true) : this(app, simTicker, PhoneRelay::publish)
    companion object {
        /** 안내할 것이 없을 때 안내줄 문구. */
        const val IDLE_TOAST = "확인됨이 차 상태를 지켜보고 있어요"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 휴대폰에서 차주가 고른 체험 모드: 예시 값으로 움직이고 기록은 따로 둔다. 차(자동차 OS)에서는 쓰지 않는다
    private val automotive = app.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE)
    val trial = !automotive && TrialMode.isOn(app)
    private val store = Store(app, ns = if (trial) Store.TRIAL_NS else "")
    private val speaker = Speaker(app)
    private val engine = VerdictEngine(store.loadSettings())
    val signals = MutableStateFlow(Signals())
    private val car = CarBridge(app, signals) { now() }
    private val pleos = com.verified.incar.vehicle.PleosBridge(app, signals, { now() }) { car.paused > 0 }
    private val navi = com.verified.incar.vehicle.PleosNavi(app, signals, { now() }) { car.paused > 0 }
    private val places = PlaceTracker(app, signals, { now() }) { engine.settings.pins to engine.settings.pinRadiusM }
    private val _ui = MutableStateFlow(UiState())
    private val phoneWake = Channel<Unit>(Channel.CONFLATED)

    // ---- 차주 잠금: 차에 탄 다른 사람(대리기사·빌린 사람)이 차주 전용 조작을 하지 못하게 한다
    private var lock = com.verified.incar.core.OwnerLock()
    private var wasHandedOver = false
    /** PIN을 기다리는 차주 전용 조작. 이것이 있으면 화면이 PIN 패드를 띄운다. */
    val ownerRequest = MutableStateFlow<(() -> Unit)?>(null)
    // 개발(debug) 빌드는 실차에서도 시뮬레이터를 연다
    private val debuggable = (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    /** 실차에 연결된 제출용 빌드에서는 시뮬레이터가 실제 신호를 덮어쓰지 못한다(예: 대리기사가 '집 도착·P단'을 꾸미는 것). */
    val simAllowed get() = debuggable || trial

    /** 체험을 고를 수 있는지: 차 신호에 연결되지 않은 휴대폰·차 화면(개발 빌드 제외). */
    private val trialAvailable get() = !debuggable && !trial && !car.connected &&
        !engine.state.valet.active && !engine.state.lend.active

    /** 고른 차종으로 체험 저장소를 새로 만든다. 호출한 화면이 앱을 다시 시작해야 체험 모드로 열린다. */
    fun startTrial(model: CarModel): Boolean {
        if (!trialAvailable) return false
        val trialStore = Store(app, ns = Store.TRIAL_NS)
        trialStore.wipeAll()
        // 차주 PIN과 생활 유형은 이어 가고, 장소·동의·운전자 자세는 예시 기본값으로 시작한다
        val d = Settings()
        trialStore.saveSettings(engine.settings.copy(carModel = model, pins = d.pins, consents = d.consents, consentAt = d.consentAt, profiles = d.profiles))
        return TrialMode.set(app, true)
    }

    /** 체험 기록을 지우고 실제 모드로 돌아간다. 호출한 화면이 앱을 다시 시작한다. */
    fun endTrial(): Boolean {
        if (!trial) return false
        val off = TrialMode.set(app, false)
        Store(app, ns = Store.TRIAL_NS).wipeAll()
        return off
    }

    /** 차주 잠금 시간은 차 시계가 아니라 부팅 후 경과 시간으로 잰다(시계를 바꿔 잠금을 줄이지 못하게). */
    private fun mono() = android.os.SystemClock.elapsedRealtime()

    fun ownerLocked() = engine.settings.ownerPinHash != null && mono() >= lock.unlockedUntilMs

    /** 차주 전용 조작. PIN을 정하지 않았거나 방금 풀었으면 바로 하고, 아니면 PIN 패드를 거친다. */
    fun ownerOnly(action: () -> Unit) {
        if (ownerLocked()) ownerRequest.value = action else action()
    }

    /** PIN을 넣어 본다. 맞으면 기다리던 조작을 하고 null, 틀리면 화면에 보일 문장을 돌려준다. */
    fun unlockOwner(pin: String): String? {
        val s = engine.settings
        val (next, error) = com.verified.incar.core.OwnerPin.attempt(pin, s.ownerPinHash ?: return null, s.ownerPinSalt.orEmpty(), lock, mono())
        lock = next
        saveLock()
        if (error == null) {
            val action = ownerRequest.value
            ownerRequest.value = null
            action?.invoke()
        }
        step()
        return error
    }

    // ---- 서버 연결: 실제 네트워크 + 시연용 '서버 끊김'
    private var netOnline = true
    private var simOffline = false
    private var simControlFail = false
    private val online get() = netOnline && !simOffline

    private fun setNet(v: Boolean) {
        val was = online
        netOnline = v
        onlineChanged(was)
    }

    fun setSimOffline(v: Boolean) {
        val was = online
        simOffline = v
        onlineChanged(was)
    }

    fun setSimControlFail(v: Boolean) {
        simControlFail = v
        publish()
    }

    fun enablePhoneNotifications() = ownerOnly {
        if (_ui.value.phoneTopic == null) setPhonePairing(PhoneRelay.newTopic())
    }

    fun disablePhoneNotifications() = ownerOnly { setPhonePairing(null) }

    private fun setPhonePairing(topic: String?) {
        val notices = _ui.value.notices.map {
            if (it.phoneStatus == PhoneDelivery.QUEUED) it.copy(phoneStatus = null, phoneId = null) else it
        }
        if (!store.savePhonePairing(topic, notices)) return toast("휴대폰 알림 설정을 저장하지 못했어요. 다시 시도해 주세요.")
        _ui.update { it.copy(phoneTopic = topic, notices = notices) }
        phoneWake.trySend(Unit)
    }

    fun sendPhoneTest() = ownerOnly {
        if (_ui.value.phoneTopic != null) handle(listOf(
            Effect.Notify("차주 폰", PhoneRelay.TEST_TITLE, "휴대폰에서 시험 알림을 확인해 주세요.", Level.INFO)
        ))
    }

    private fun saveNotices(notices: List<SentNotice>): Boolean {
        // 화면 이력은 50건. 연결을 기다리는 알림은 밀려나도 대기열에 남겨 둔다.
        val retained = notices.filterIndexed { index, n -> index < 50 || n.phoneStatus == PhoneDelivery.QUEUED }
        if (!store.saveNotices(retained)) {
            toast("알림을 저장하지 못했어요. 기기 저장 공간을 확인해 주세요.")
            return false
        }
        _ui.update { it.copy(notices = retained) }
        return true
    }

    /** 한 번에 하나씩 처리한다. 재시작하면 저장된 재시도 시각부터 이어 간다. */
    private suspend fun deliverPhoneNotices() {
        while (true) {
            val topic = _ui.value.phoneTopic
            val pending = _ui.value.notices.asReversed().filter { it.phoneStatus == PhoneDelivery.QUEUED }
            if (topic == null || !online || pending.isEmpty()) {
                phoneWake.receive()
                continue
            }
            val notice = pending.minBy { it.phoneRetryAt }
            val wait = notice.phoneRetryAt - System.currentTimeMillis()
            if (wait > 0) {
                withTimeoutOrNull(wait) { phoneWake.receive() }
                continue
            }
            val result = withContext(Dispatchers.IO) { sendPhone(topic, notice) }
            if (_ui.value.phoneTopic != topic) continue
            val attempts = notice.phoneAttempts.coerceIn(0, 10) + 1
            val backoff = (5_000L shl (attempts - 1)).coerceAtMost(900_000L)
            val retryAt = if (result.status == PhoneDelivery.QUEUED)
                System.currentTimeMillis() + maxOf(backoff, result.retryAfterMs) else 0
            // 서버가 받은 건을 저장 실패 때문에 곧바로 다시 보내지 않고 저장만 재시도한다.
            while (_ui.value.phoneTopic == topic) {
                val updated = _ui.value.notices.map {
                    if (it.phoneId == notice.phoneId && it.phoneStatus == PhoneDelivery.QUEUED)
                        it.copy(phoneStatus = result.status, phoneAttempts = attempts, phoneRetryAt = retryAt) else it
                }
                if (saveNotices(updated)) break
                delay(30_000)
            }
        }
    }

    /** 끊기면 판정은 차 안에서 계속된다고 알리고, 돌아오면 보관했던 알림을 보낸다. */
    private fun onlineChanged(was: Boolean) {
        phoneWake.trySend(Unit)
        // 다시 켜졌을 때 남은 보관 알림도 연결되면 보낸다
        if (was == online && (!online || _ui.value.notices.all { it.delivered })) return publish()
        if (!online) toast("인터넷 연결이 끊겼어요. 차량 기록은 계속 저장해요.")
        else {
            val waiting = _ui.value.notices.count { !it.delivered }
            if (waiting > 0) saveNotices(_ui.value.notices.map { it.copy(delivered = true) })
            toast(if (waiting > 0) "인터넷이 연결됐어요. 오프라인 중 기록한 알림 ${waiting}건이 있어요." else "인터넷이 연결됐어요.")
        }
        publish()
    }

    private fun saveLock() = store.saveLock(lock.tries, lock.rounds, mono() < lock.lockedUntilMs)

    fun cancelOwnerRequest() {
        ownerRequest.value = null
    }

    /**
     * null이면 PIN을 끈다. 정한 직후에는 차주가 앞에 있으므로 열린 상태로 둔다.
     * PIN을 정하면 복구 코드를 새로 만들어 돌려준다(화면에 한 번만 보여 준다).
     */
    fun setOwnerPin(pin: String?): String? {
        if (pin != null && !com.verified.incar.core.OwnerPin.valid(pin)) return null
        val salt = com.verified.incar.core.OwnerPin.newSalt()
        val recovery = pin?.let { com.verified.incar.core.OwnerPin.newRecoveryCode() }
        settings {
            if (pin == null) it.copy(ownerPinHash = null, ownerPinSalt = null, ownerRecoveryHash = null)
            else it.copy(ownerPinHash = com.verified.incar.core.OwnerPin.hash(pin, salt), ownerPinSalt = salt, ownerRecoveryHash = com.verified.incar.core.OwnerPin.hash(recovery!!, salt))
        }
        lock = com.verified.incar.core.OwnerLock(unlockedUntilMs = mono() + com.verified.incar.core.OwnerPin.UNLOCK_MS)
        saveLock()
        step()
        toast(if (pin == null) "차주 PIN을 껐어요" else "차주 PIN을 정했어요. 대리나 차 빌려주기를 시작하면 자동으로 잠겨요.")
        return recovery
    }

    /** PIN을 잊었을 때: 복구 코드가 맞으면 PIN을 끄고 기다리던 조작을 한다. 틀린 횟수는 PIN과 같은 잠금 규칙을 쓴다. */
    fun recoverOwner(code: String): String? {
        val s = engine.settings
        val (next, error) = com.verified.incar.core.OwnerPin.attempt(code, s.ownerRecoveryHash ?: return "복구 코드가 없어요", s.ownerPinSalt.orEmpty(), lock, mono())
        lock = next
        saveLock()
        if (error != null) {
            step()
            return error.replace("PIN이", "복구 코드가")
        }
        val action = ownerRequest.value
        ownerRequest.value = null
        setOwnerPin(null)
        action?.invoke()
        toast("복구 코드로 PIN을 껐어요. 설정에서 새 PIN을 정해 주세요.")
        return null
    }

    fun lockOwner() {
        lock = lock.copy(unlockedUntilMs = 0)
        step()
    }
    val ui: StateFlow<UiState> = _ui
    /** 음성 호출 등 화면 밖에서 요청한 탭 이동. 화면이 받아서 비운다. */
    val navigate = MutableStateFlow<String?>(null)

    /** 시연용 시간 건너뛰기. 실차 모드에서는 쓰지 않는다. */
    private var clockOffsetMs = 0L
    // 판정 시간은 부팅 후 경과 시간으로 잰다. 시동 직후 차가 시계를 맞춰도 10초 유지·1분 미잠금 같은 판정이 튀지 않는다
    private var wallBase = System.currentTimeMillis()
    private var monoBase = android.os.SystemClock.elapsedRealtime()
    private fun now(): Long {
        val t = wallBase + (android.os.SystemClock.elapsedRealtime() - monoBase)
        // 차 시계가 1분 넘게 바뀌었으면, 시간을 재는 판정이 없을 때만 날짜 기준을 다시 맞춘다
        if (kotlin.math.abs(System.currentTimeMillis() - t) > 60_000 && !engine.needsTick()) {
            wallBase = System.currentTimeMillis()
            monoBase = android.os.SystemClock.elapsedRealtime()
            return wallBase + clockOffsetMs
        }
        return t + clockOffsetMs
    }

    /** 잠든 타이머를 바로 깨운다(대리 시작처럼 시간을 재야 하는 판정이 막 켜졌을 때). */
    private val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private var lastIgnition = true
    private var savedStats = emptyList<com.verified.incar.core.DailyStat>()
    private var savedSession: Any? = null

    /** 저장하는 세션 내용. 트렁크의 열린 시각·확인 질문은 재시작 뒤 쓸모없어 저장도 비교도 하지 않는다. */
    private fun sessionKey() = Triple(
        Triple(engine.state.trip.pending, engine.state.trip.open, engine.state.lend),
        engine.state.trunk.copy(askConfirm = false, openedAtMs = null), engine.state.school.streak,
    )

    init {
        // 앱을 강제로 껐다 켜서 PIN 시도 횟수·잠금을 되돌리지 못하게 이어 간다
        store.loadLock().let { (tries, rounds, locked) ->
            lock = com.verified.incar.core.OwnerLock(
                tries = tries, rounds = rounds,
                lockedUntilMs = if (locked) mono() + (com.verified.incar.core.OwnerPin.LOCKOUT_MS shl (rounds - 1).coerceIn(0, 6)) else 0,
            )
        }
        val connected = car.connect()
        if (connected || !simAllowed) {
            engine.onCar = true
            engine.requireHomePin = true
            engine.forgetPlace()
            signals.update { it.copy(place = Place.OTHER, gpsLost = true) }
            engine.settings = engine.settings.copy(profiles = engine.settings.profiles.map {
                if (it.posture == com.verified.incar.core.Postures.OWNER || it.posture == com.verified.incar.core.Postures.SPOUSE) it.copy(posture = emptyList()) else it
            })
            engine.caps = if (connected) car.caps else Capabilities(
                row2Occupancy = false, row2Recline = false, seatPosture = false,
                ambientControl = false, seatControl = false, climateControl = false,
                seatHeat = false, seatVent = false, steeringHeat = false, speed = false,
            )
        }
        if (connected) {
            // 실차가 알려 준 모델명으로 차종을 자동 등록한다
            // 알아보지 못하면 차주가 고른 차종을 두고, 고른 적이 없을 때만 '그 밖의 차량'으로 주행 기능만 쓴다
            engine.settings = engine.settings.copy(carModel = CarModel.detect(signals.value.modelName) ?: engine.settings.carModel ?: CarModel.OTHER)
            // 현대 Pleos 차면 AAOS로 못 읽는 신호(경고·오일·워셔액 등)를 더 읽는다. 서비스가 없으면 둘 다 아무것도 안 한다
            pleos.connect(skip = car.live.toSet())
            navi.connect()
        }
        startLocation()
        engine.loadTrips(VerdictEngine.keepRecent(store.loadTrips(), java.time.LocalDate.now()))
        engine.loadStats(store.loadStats())
        engine.loadHistory(com.verified.incar.core.MyHyundai.keepHistory(store.loadHistory(), java.time.LocalDate.now()))
        store.loadSession().let { ss ->
            engine.loadSession(ss.pending, ss.open, ss.lend, ss.trunk, ss.streak)
        }
        // 방금 불러온 기록은 이미 저장돼 있다(켤 때마다 다시 암호화해 쓰지 않게)
        savedStats = engine.state.stats
        savedSession = sessionKey()
        _ui.update { it.copy(notices = store.loadNotices(), phoneTopic = store.loadPhoneTopic()) }
        // 실제 네트워크가 끊기면(지하·음영 지역) 알림을 보관한다
        runCatching {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            cm?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) { scope.launch { setNet(true) } }
                override fun onLost(network: android.net.Network) { scope.launch { setNet(false) } }
            })
            // 망 없이 켜지면 콜백이 오지 않는다(onLost는 받은 망에만 옴). 그래서 등록 뒤 지금 상태를 직접 본다
            if (cm != null) netOnline = cm.activeNetwork != null
        }
        scope.launch { deliverPhoneNotices() }
        val savedValet = store.loadValet()
        // 부팅 추정 시각은 복구 안내에만 사용한다. 재부팅 자체를 주차 완료 근거로 쓰지 않는다.
        val bootAtMs = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val recovered = engine.recoverValet(savedValet, rebooted = (savedValet.prelimAtMs ?: 0) < bootAtMs)
        handle(recovered)
        if (engine.state.valet != savedValet) store.saveValet(engine.state.valet)
        scope.launch { signals.collect { step() } }
        if (simTicker) scope.launch {
            var lastScheduleMs = 0L
            while (true) {
                // 실차에서 시간 조건(10초 정지 등)을 보는 판정이 없으면 10초에 한 번만 깨어난다(심사 기준: 불필요한 타이머 최소화).
                // 그사이 판정이 켜지면 wake로 바로 깨운다
                val idle = if (!car.connected) !simAllowed else !engine.needsTick()
                kotlinx.coroutines.withTimeoutOrNull(if (idle) 10_000L else 1_000L) { wake.receive() }
                places.checkLost()
                val scheduleDue = engine.settings.schedule.enabled && now() - lastScheduleMs >= 30_000
                if (scheduleDue) lastScheduleMs = now()
                val tick = !car.connected || engine.needsTick() || scheduleDue
                // 실차에서 쉬는 동안에도 5분 차주 재잠금을 화면에 반영한다(열어 둔 설정이 닫히게)
                if (!tick) { if (_ui.value.ownerUnlocked && ownerLocked()) publish(); continue }
                if (!car.connected && simAllowed) signals.update { s ->
                    // 시뮬레이터: 속도만큼 주행거리·배터리를 흘려 보낸다
                    val km = s.speedKmh / 3600.0
                    s.copy(odometerKm = s.odometerKm + km, socPct = if (km > 0 && Math.random() < 0.02) (s.socPct - 1).coerceAtLeast(0) else s.socPct)
                }
                step()
            }
        }
    }

    /** 저장할 상태의 바꾸기 전 모습. 바뀐 것만 다시 암호화해 저장한다(운행기록은 해마다 쌓인다). */
    private class Snap(e: VerdictEngine) {
        val valet = e.state.valet.saved()
        val trips = e.state.trip.logs
        val session = Triple(e.state.trip.pending, e.state.trip.open, e.state.lend)
        val history = e.state.history
    }

    private fun persist(b: Snap, shutdown: Boolean) {
        if (engine.state.valet.saved() != b.valet) store.saveValet(engine.state.valet)
        if (engine.state.history != b.history) store.saveHistory(engine.state.history, now = true)
        if (engine.state.trip.logs != b.trips) store.saveTrips(engine.state.trip.logs, now = shutdown)
        val key = sessionKey()
        val session = key.first
        // 빌려주기·맡김 기록은 주행 중 신호마다 바뀌므로 P단·시동 OFF·사용자 조작 때만 저장한다
        val important = b.session.first != session.first || b.session.second != session.second || b.session.third.active != session.third.active
        if (key != savedSession && (signals.value.parked || shutdown || important)) {
            store.saveSession(engine.state.trip, engine.state.lend, engine.state.trunk, engine.state.school.streak, now = shutdown)
            savedSession = key
        }
    }

    private fun step() {
        // 제출용 휴대폰에서는 수동 맡김·반납과 휴대폰 전송만 진행한다. 연결 전 초기 신호로 자동 판정하지 않는다.
        if (!car.connected && !simAllowed) return publish()
        val s = signals.value
        val shutdown = lastIgnition && !s.ignitionOn
        lastIgnition = s.ignitionOn
        // 시동 OFF(Suspend): 음성을 멈추고 오디오 포커스를 반납한다
        if (shutdown) speaker.stop()
        val before = Snap(engine)
        val wasTicking = engine.needsTick()
        // Suspend 진입 중에는 방금 멈춘 음성을 다시 틀지 않는다. N단 유지는 시동이 ACC로 남아 Suspend가 아니다
        handle(engine.update(s, now(), shutdown), quiet = shutdown && s.gear != Gear.N)
        if (!wasTicking && engine.needsTick()) wake.trySend(Unit)
        persist(before, shutdown)
        // 주행 통계는 달리는 동안 계속 바뀌므로 P단이나 시동 OFF 때만 저장한다(N단 유지로 끄는 경우 포함)
        if ((s.parked || shutdown) && engine.state.stats != savedStats) {
            store.saveStats(engine.state.stats, now = shutdown)
            savedStats = engine.state.stats
        }
        publish()
    }

    private fun handle(fx: List<Effect>, quiet: Boolean = false) {
        // 안내줄에는 한 문장만 남으므로 차주 알림 > 음성 > 일반 안내 순으로 고른다
        var toast: String? = null
        var rank = 0
        fun show(text: String, r: Int) { if (r >= rank) { toast = text; rank = r } }
        val sent = mutableListOf<SentNotice>()
        // 시뮬레이터 신호 변경은 안내를 정한 뒤 적용한다(바뀐 신호로 도는 판정의 안내가 앞 안내에 덮이지 않게)
        val later = mutableListOf<(Signals) -> Signals>()
        for (e in fx) when (e) {
            is Effect.Notify -> {
                val queue = _ui.value.phoneTopic != null && e.target == "차주 폰" && "목업" !in e.title
                sent += SentNotice(now(), e.target, e.title, e.body, e.level, delivered = online,
                    phoneStatus = if (queue) PhoneDelivery.QUEUED else null,
                    phoneId = if (queue) UUID.randomUUID().toString() else null,
                    phoneExample = !car.connected && simAllowed)
                show(if (queue) "휴대폰 전송 대기 · ${e.title}" else "기록했어요 · ${e.title}", 3)
            }
            is Effect.Speak -> {
                // 못 말한 실제 이유를 보여 준다(음성을 끈 차주에게 순정 오디오 탓으로 보이지 않게)
                val why = when {
                    Flag.TTS !in engine.settings.flags -> "음성 안내 꺼짐"
                    quiet -> "시동 OFF"
                    signals.value.audioFocusLost -> "순정 오디오 우선"
                    else -> speaker.speak(e.text)
                }
                show(if (why == null) "🔊 ${e.text}" else "🔇 $why · 화면 안내: ${e.text}", 2)
            }
            is Effect.Toast -> show(e.text, 1)
            // 실차에서는 시트가 실제로 움직였을 때 신호로 들어오므로, 앱이 자세를 꾸며 넣지 않는다(시뮬레이터에서만)
            // 제어 결과도 시뮬레이터를 쓸 수 있을 때만 꾸며 넣는다(자동차 OS 제출용 빌드는 연결에 실패해도 실차로 본다)
            Effect.RestoreSeat -> if (!car.connected && simAllowed) engine.settings.profiles.firstOrNull()?.let { owner -> later.add { it.copy(posture = owner.posture, hvacTempC = 21f) } }
            // shortcut: 실차 공조 제어는 Pleos Vehicle SDK 승인 후 연결, 지금은 시뮬레이터 신호에만 반영
            // shortcut: 실차 제어는 Pleos Vehicle SDK Control 승인 후 연결, 지금은 시뮬레이터 신호에만 반영
            // 시연: '제어 무응답'을 켜면 차가 명령을 받지 못한 것처럼 신호를 바꾸지 않는다
            is Effect.Control -> if (!car.connected && simAllowed && !simControlFail) later.add {
                when (e.kind) {
                    com.verified.incar.core.ControlKind.LOCK -> it.copy(doorsLocked = true)
                    com.verified.incar.core.ControlKind.UNLOCK -> it.copy(doorsLocked = false)
                    com.verified.incar.core.ControlKind.CLIMATE_ON -> it.copy(acOn = true, hvacPowerOn = true, fanSpeed = maxOf(1, it.fanSpeed))
                    com.verified.incar.core.ControlKind.CLIMATE_OFF -> it.copy(acOn = false, hvacPowerOn = false, fanSpeed = 0, seatHeatDriver = 0, seatVentDriver = 0)
                    com.verified.incar.core.ControlKind.HAZARD -> it.copy(hazardOn = !it.hazardOn)
                    com.verified.incar.core.ControlKind.HORN -> it
                }
            } else if (car.connected) engine.controlSignal(e.kind)?.let { name ->
                // 판정 직전에 차 신호를 한 번 직접 읽어, 반대 상태(실패)와 신호 없음(결과 미확인)을 가른다
                scope.launch { delay(VerdictEngine.verifyMs(e.kind) - 1_000); car.refresh(name); wake.trySend(Unit) }
            }
            // 현대 내비가 없는 차·폰에서는 아무것도 하지 않는다(대리기사가 직접 목적지를 넣는다)
            is Effect.NaviHome -> navi.routeHome(e.home.lat, e.home.lon, Place.HOME.label)
            is Effect.ApplyClimate, is Effect.RemoteClimate -> {
                val p = if (e is Effect.ApplyClimate) e.preset else (e as Effect.RemoteClimate).preset
                if (!car.connected && simAllowed) later.add {
                    it.copy(
                        hvacTempC = p.tempC, hvacPowerOn = true, fanSpeed = maxOf(1, it.fanSpeed),
                        seatHeatDriver = p.driverHeat, seatHeatPassenger = p.passengerHeat, seatHeatRear = p.rearHeat,
                        seatVentDriver = p.driverVent, steeringHeat = p.steeringHeat, defrosterOn = p.defrost || it.defrosterOn, acOn = p.driverVent > 0 || it.acOn,
                    )
                }
            }
        }
        toast?.let { this.toast(it) }
        if (sent.isNotEmpty() && saveNotices(sent.reversed() + _ui.value.notices)) phoneWake.trySend(Unit)
        later.forEach { signals.update(it) }
    }

    private fun publish() {
        // 대리기사·빌린 사람에게 차를 넘기는 순간 차주 잠금을 다시 건다
        val handedOver = engine.state.valet.active || engine.state.lend.active
        if (handedOver && !wasHandedOver) lock = lock.copy(unlockedUntilMs = 0)
        wasHandedOver = handedOver
        val s = signals.value
        // 주행 중에는 3줄 요약만 보이므로 소모품·주간 기록·피드는 P단 화면일 때만 다시 계산한다
        val full = !s.uxRestricted
        val today = java.time.Instant.ofEpochMilli(now()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val cons = if (full) com.verified.incar.core.MyHyundai.consumables(s.odometerKm, engine.settings.serviceLog, engine.state.stats, today) else null
        val weeks = if (full) com.verified.incar.core.MyHyundai.weeks(engine.state.stats, today) else null
        _ui.update {
            it.copy(
                signals = s, engine = engine.state, settings = engine.settings, caps = engine.caps,
                carConnected = car.connected, liveSignals = car.live + pleos.live + navi.live, nowMs = now(),
                drivingLines = engine.drivingLines(), diag = engine.diagnose(s), canControl = engine.controlAllowed(),
                locationOn = places.running && engine.settings.pins.isNotEmpty(),
                winter = engine.winterCheck(s, engine.settings.comfort["차주"]),
                consumables = cons ?: it.consumables,
                thisWeek = if (weeks != null) weeks.first.takeIf { engine.state.stats.isNotEmpty() } else it.thisWeek,
                lastWeek = if (weeks != null) weeks.second.takeIf { engine.state.stats.isNotEmpty() } else it.lastWeek,
                feed = if (cons != null) com.verified.incar.core.MyHyundai.feed(s, engine.state, engine.settings, cons, now()) else it.feed,
                ownerPinSet = engine.settings.ownerPinHash != null, ownerUnlocked = !ownerLocked(), simAllowed = simAllowed,
                trial = trial, trialAvailable = trialAvailable,
                online = online, simOffline = simOffline, simControlFail = simControlFail,
            )
        }
    }

    /** 엔진 조작을 실행하고, 바뀐 설정을 저장한 뒤 화면을 갱신한다. */
    fun act(block: VerdictEngine.(Signals, Long) -> List<Effect>) {
        val before = engine.settings
        val snap = Snap(engine)
        handle(engine.block(signals.value, now()))
        if (engine.settings != before) store.saveSettings(engine.settings)
        persist(snap, shutdown = false)
        wake.trySend(Unit)
        step()
    }

    fun settings(change: (Settings) -> Settings) = act { _, _ -> settings = change(settings); emptyList() }

    /** 차종 지원 칩. 실차 제출용 빌드에서는 차가 알려 준 감지값을 덮어쓰지 못한다(sim과 같은 규칙). */
    fun caps(change: (Capabilities) -> Capabilities) {
        if (simAllowed) act { _, _ -> caps = change(caps); emptyList() } else toast("실차에 연결된 동안에는 차가 알려 준 지원 여부를 바꿀 수 없어요")
    }

    fun sim(change: (Signals) -> Signals) {
        if (simAllowed) signals.update(change)
    }

    fun setCabin(setting: com.verified.incar.core.CabinSetting) = ownerOnly {
        if (car.connected || !simAllowed) return@ownerOnly toast("실차 공조는 제어 권한과 차량 지원 확인이 필요해요.")
        if (signals.value.uxRestricted) return@ownerOnly toast("정차한 뒤 실내 설정을 바꿔 주세요.")
        if (engine.settings.carModel?.controllable != true) return@ownerOnly toast("먼저 지원 차종을 선택해 주세요.")
        signals.update(setting::applyTo)
        publish()
        toast("체험 차량에 실내 설정을 반영했어요.")
    }

    fun setExampleDetails(change: (com.verified.incar.core.VehicleDetails) -> com.verified.incar.core.VehicleDetails) = ownerOnly {
        if (car.connected || !simAllowed || signals.value.uxRestricted) return@ownerOnly
        signals.update { s ->
            val details = change(com.verified.incar.core.VehicleDetails.example(s, now()))
            val rear = com.verified.incar.core.SeatPart.entries.drop(2)
            val occupiedRear = rear.filter { details.occupied[it]?.value == true }
            s.copy(
                details = details,
                doorsLocked = com.verified.incar.core.DoorPart.entries.filter { it != com.verified.incar.core.DoorPart.TRUNK }
                    .all { details.doorLocked[it]?.value == true },
                passengerBelt = details.belted[com.verified.incar.core.SeatPart.PASSENGER]?.value == true,
                row2Seated = occupiedRear.isNotEmpty(),
                row2Belt = occupiedRear.isNotEmpty() && occupiedRear.all { details.belted[it]?.value == true },
            )
        }
        publish()
    }

    fun skipSeconds(sec: Int) {
        if (!simAllowed) return
        clockOffsetMs += sec * 1_000L
        step()
    }

    fun toast(text: String) = _ui.update { it.copy(toast = text) }

    /** 위치 권한을 받은 뒤 호출한다. 권한이 없으면 아무것도 하지 않는다. */
    fun startLocation() {
        places.start()
        publish()
    }

    /** 권한 창에 답한 뒤·화면으로 돌아올 때 부른다. 차 권한을 새로 받았으면 차 신호를 다시 구독하고 위치를 켠다. */
    fun onPermissions() {
        if (car.resubscribeIfGranted()) engine.caps = car.caps
        startLocation()
    }

    /** 지금 차가 있는 곳을 [place]로 등록한다. */
    fun registerPin(place: Place) {
        val loc = places.last ?: return toast("아직 위치를 받지 못했어요. 위치 권한과 GPS를 확인해 주세요.")
        settings { it.copy(pins = it.pins + (place to Pin(loc.latitude, loc.longitude))) }
        toast("현재 위치를 ${place.label}(으)로 등록했어요. 좌표는 차 안에만 저장해요.")
    }

    fun removePin(place: Place) = settings { it.copy(pins = it.pins - place) }

    /**
     * 내 기록 지우기. 운행기록, 대리 기록, 보낸 알림, 등록한 시트 자세, 장소 좌표, 동의 기록을 지운다.
     * 생활 유형·차종·출발 준비 설정처럼 기록이 아닌 선택은 남긴다.
     */
    fun wipeMyData() {
        store.clearRecords()
        // 지운 알림이 뒤따르는 판정의 새 알림과 함께 다시 저장되지 않게 먼저 비운다
        _ui.update { it.copy(notices = emptyList(), phoneTopic = null) }
        phoneWake.trySend(Unit)
        engine.reset()
        // 실차는 init과 같게 위치 모름·시연 자세 미등록으로 다시 시작한다
        if (engine.onCar) engine.forgetPlace()
        val d = Settings()
        val profiles = if (engine.onCar) d.profiles.map { p -> p.copy(posture = emptyList()) } else d.profiles
        settings { it.copy(profiles = profiles, pins = emptyMap(), consentAt = emptyMap(), consents = d.consents) }
        // 핀을 지웠으니 지난 장소 판정도 버린다(settings 뒤라야 비운 자세로 운전자를 판정한다)
        if (engine.onCar) signals.update { it.copy(place = Place.OTHER, gpsLost = true) }
        toast("내 기록을 지웠어요. 운행기록·대리 기록·알림·자세·장소·동의 기록이 삭제됐어요.")
    }

    /** PT·영상용 자동 재생. 실제 신호 순서를 그대로 흘려 엔진이 스스로 판정하게 한다. */
    fun runScenario(name: String) = scope.launch {
        if (!simAllowed) return@launch
        // 차속·외기 같은 연속 신호와 GPS는 값이 같아도 다시 와 재생 값을 덮어쓰므로, 재생하는 동안만 받지 않는다
        car.paused++
        places.paused = true
        coroutineContext.job.invokeOnCompletion { car.paused--; places.paused = car.paused > 0 }
        suspend fun to(delayMs: Long = 900, change: (Signals) -> Signals) { signals.update(change); delay(delayMs) }
        val base = Signals(row2Seated = false, row2Belt = false)
        signals.value = base
        delay(300)
        when (name) {
            "valet", "valet_quick_off", "valet_garage", "valet_outside" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.VALET) }
                act { _, _ -> endValet(); startValet() }
                delay(600)
                act { s, now -> confirmValetConsent(s, now) }
                to { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER, posture = com.verified.incar.core.Postures.STRANGER) }
                // 달린 만큼 거리·배터리도 바뀐다(대리 화면이 '이동 0.0km'로 나오지 않게)
                to { it.copy(odometerKm = it.odometerKm + 4.8, socPct = it.socPct - 1) }
                if (name == "valet_outside") {
                    to { it.copy(speedKmh = 0f, gear = Gear.P) }
                    to { it.copy(driverSeated = false, driverDoorOpen = true, windowOpen = true, steeringDeg = 35f) }
                    return@launch
                }
                to { it.copy(place = Place.HOME, speedKmh = 15f) }
                if (name == "valet_garage") to { it.copy(gpsLost = true, place = Place.OTHER, speedKmh = 8f) }
                to { it.copy(speedKmh = 0f, gear = Gear.P) }
                if (name == "valet_quick_off") to(1_500) { it.copy(ignitionOn = false) }
                else {
                    to { it.copy(driverSeated = false, driverDoorOpen = true) }
                    skipSeconds(10)
                }
            }
            "escort" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.ESCORT, consents = it.consents + com.verified.incar.core.Consent.GUARDIAN, flags = it.flags + Flag.AMBIENT_GUIDE) }
                act { _, now -> startEscort(now); emptyList() }
                to { it.copy(row2RightDoorOpen = true) }
                to { it.copy(row2Seated = true) }
                to { it.copy(row2RightDoorOpen = false, row2Belt = true) }
                to(1_500) { it.copy(gear = Gear.D, speedKmh = 30f, place = Place.OTHER) }
                to { it.copy(place = Place.HOSPITAL, speedKmh = 0f, gear = Gear.P) }
                skipSeconds(4)
                delay(1_500)
                to(1_500) { it.copy(row2LeftDoorOpen = true) }
                to { it.copy(row2LeftDoorOpen = false, row2RightDoorOpen = true, row2Belt = false) }
                to { it.copy(row2Seated = false) }
                to { it.copy(row2RightDoorOpen = false) }
            }
            "school" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.SCHOOL) }
                to { it.copy(place = Place.SCHOOL, row2Seated = true, row2Belt = true) }
                to { it.copy(row2Belt = false, row2RightDoorOpen = true) }
                to { it.copy(row2Seated = false) }
                to { it.copy(row2RightDoorOpen = false) }
                to(1_500) { it.copy(gear = Gear.D, speedKmh = 18f) }
                to(1_500) { it.copy(speedKmh = 26f) }
                skipSeconds(60)
            }
            "stranger" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.DRIVER) }
                to { it.copy(driverSeated = false, driverDoorOpen = true) }
                to { it.copy(driverSeated = true, driverDoorOpen = false, posture = com.verified.incar.core.Postures.STRANGER) }
            }
            "exit" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.TRUNK) }
                to { it.copy(windowOpen = true, lowBeamOn = true, interiorLightOn = true) }
                to(1_500) { it.copy(driverDoorOpen = true) }
                to { it.copy(driverSeated = false, driverDoorOpen = false, ignitionOn = false, doorsLocked = false) }
                skipSeconds(61)
            }
            "nhold" -> {
                act { _, _ -> startNHold(); emptyList() }
                to(4_000) { it.copy(gear = Gear.N) }
                to(1_500) { it.copy(ignitionOn = false, driverSeated = false) }
                skipSeconds(30 * 60)
            }
            "floor" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.TRUNK) }
                to { it.copy(place = Place.HOME, gear = Gear.D, speedKmh = 15f) }
                to { it.copy(gpsLost = true, place = Place.OTHER, speedKmh = 10f) }
                repeat(2) {
                    to(700) { it.copy(steeringDeg = 30f) }
                    to(700) { it.copy(steeringDeg = 0f) }
                }
                to { it.copy(gear = Gear.P, speedKmh = 0f) }
            }
            "lend" -> {
                settings { it.copy(extraModules = it.extraModules + com.verified.incar.core.Module.DRIVER) }
                act { s, now -> startLend(s, now) }
                to { it.copy(driverSeated = false) }
                to { it.copy(driverSeated = true, posture = com.verified.incar.core.Postures.SPOUSE) }
                to(1_500) { it.copy(gear = Gear.D, speedKmh = 62f) }
                to(1_000) { it.copy(speedKmh = 135f) }
                to { it.copy(speedKmh = 62f) }
                // 18.4km를 달린 만큼 시간과 배터리도 흐른다(리포트가 '0분', '80% → 80%'로 나오지 않게)
                skipSeconds(35 * 60)
                to(1_000) { it.copy(speedKmh = 40f) }
                to { it.copy(gear = Gear.P, speedKmh = 0f, odometerKm = it.odometerKm + 18.4, socPct = it.socPct - 4, windowOpen = true) }
                to { it.copy(driverSeated = false) }
                to { it.copy(driverSeated = true, posture = com.verified.incar.core.Postures.OWNER) }
            }
            "service" -> {
                act { s, now -> startLend(s, now, com.verified.incar.core.HandKind.SERVICE) }
                to { it.copy(driverSeated = false, ignitionOn = false) }
                to { it.copy(driverSeated = true, driverDoorOpen = true, posture = com.verified.incar.core.Postures.STRANGER) }
                to { it.copy(driverDoorOpen = false, ignitionOn = true) }
                to(1_500) { it.copy(gear = Gear.D, speedKmh = 84f) }
                skipSeconds(25 * 60)
                to { it.copy(gear = Gear.P, speedKmh = 0f, odometerKm = it.odometerKm + 12.6, socPct = it.socPct - 3, ignitionOn = false) }
                to { it.copy(ignitionOn = true) }
                act { s, now -> endLend(s, now) }
            }
            "ev_trip" -> to { it.copy(gear = Gear.D, speedKmh = 115f, socPct = 60, outsideTempC = -5f, naviDest = "강릉", naviRemainKm = 180f, naviRemainMin = 120) }
            "leak" -> {
                // 일주일 동안 왼쪽 뒤만 13kPa 빠지고, 나머지는 기온 탓에 2kPa씩 함께 내려간 기록
                act { _, now ->
                    val today = java.time.LocalDate.parse(VerdictEngine.date(now))
                    loadStats((0 until 7).map { d ->
                        com.verified.incar.core.DailyStat(today.minusDays(d.toLong()).toString(), 24.0, tires = listOf(248f + d * 0.3f, 248f + d * 0.3f, 236f + d * 2.2f, 248f + d * 0.3f))
                    })
                    emptyList()
                }
                to { it.copy(tireKpa = listOf(248f, 248f, 236f, 248f)) }
            }
            "evac" -> {
                to(1_000) { it.copy(gear = Gear.D, speedKmh = 92f) }
                to { it.copy(speedKmh = 35f) }
                to { it.copy(speedKmh = 0f) }
                to(1_500) { it.copy(hazardOn = true) }
                to(1_500) { it.copy(gear = Gear.P) }
                to { it.copy(trunkOpen = true) }
            }
            "harsh" -> {
                to(1_000) { it.copy(gear = Gear.D, speedKmh = 50f, row2Seated = true, row2Belt = true, row2Reclined = true) }
                to { it.copy(speedKmh = 30f) }
            }
        }
    }

    /**
     * 국세청 서식 기준 CSV를 공용 '다운로드' 폴더(MediaStore, 권한 불필요)에 쓴다. 앱 전용 폴더는 차 밖으로 꺼낼 수 없다.
     * 다운로드에 못 쓰는 차에서는 지금처럼 앱 폴더에 남긴다. 둘 다 '내 기록 지우기'로 지운다.
     */
    fun exportTrips() = saveCsv("운행기록부_${VerdictEngine.date(now())}.csv", VerdictEngine.tripCsv(engine.state.trip.logs, engine.settings.carModel), "국세청 서식 기준 CSV")

    /** 맡김 이력서 CSV(인증중고차 매각·보험 설명 자료). 운행기록부와 같은 경로에 쓰고 '내 기록 지우기'로 지운다. */
    fun exportHistory() = saveCsv("맡김이력서_${VerdictEngine.date(now())}.csv", com.verified.incar.core.MyHyundai.historyCsv(engine.state.history), "맡김 이력서 CSV")

    /** 현대 기능 체크리스트 체크·해제. 차주 전용(PIN). [valet]: ① 대리 화면이면 true, 빌려주기·맡김이면 false. */
    fun togglePrep(item: com.verified.incar.core.PrepItem, valet: Boolean) = ownerOnly { act { _, now -> togglePrep(item, now, valet) } }

    fun prepLabel(item: com.verified.incar.core.PrepItem, valet: Boolean) = engine.prepLabel(item, valet)

    private fun saveCsv(name: String, csv: String, what: String) {
        val saved = runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = app.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            // 쓰다 실패하면 빈 파일을 남기지 않는다
            runCatching { app.contentResolver.openOutputStream(uri)!!.use { it.write(csv.toByteArray()) } }
                .onFailure { app.contentResolver.delete(uri, null, null) }.getOrThrow()
        }.isSuccess
        if (saved) return toast("${what}를 다운로드 폴더에 저장했어요: $name")
        val f = File(app.getExternalFilesDir(null) ?: app.filesDir, name)
        f.writeText(csv)
        toast("${what}를 차 안에 저장했어요: $name (차 밖으로 꺼내기는 서버 연동 후 지원)")
    }

    fun close() {
        scope.cancel()
        places.stop()
        navi.disconnect()
        pleos.disconnect()
        car.disconnect()
        speaker.shutdown()
    }
}

// 저장하지 않는 칸(급감속 수·복원 안내)만 바뀐 것은 다시 쓰지 않는다(대리 운행 중 메인 스레드 동기 저장을 막는다)
private fun com.verified.incar.core.ValetState.saved() = copy(restoreMsg = null, naviMinKm = null, naviOffSinceMs = null)

/** 체험 모드 켜짐 표시. 기록이 아니라 모드 스위치라 암호화하지 않는다. */
internal object TrialMode {
    private fun prefs(ctx: android.content.Context) = ctx.getSharedPreferences("verified_mode", android.content.Context.MODE_PRIVATE)
    fun isOn(ctx: android.content.Context) = prefs(ctx).getBoolean("trial", false)
    fun set(ctx: android.content.Context, on: Boolean) = prefs(ctx).edit().putBoolean("trial", on).commit()
}
