package com.verified.incar

import android.content.Context
import com.verified.incar.core.Booking
import com.verified.incar.core.CarCheck
import com.verified.incar.core.CarModel
import com.verified.incar.core.HandoverRecord
import com.verified.incar.core.PrepItem
import com.verified.incar.core.DailyStat
import com.verified.incar.core.ServiceRecord
import com.verified.incar.core.SharedKey
import com.verified.incar.core.ComfortPreset
import com.verified.incar.core.Consent
import com.verified.incar.core.DepartureSchedule
import com.verified.incar.core.DriverProfile
import com.verified.incar.core.Flag
import com.verified.incar.core.HandKind
import com.verified.incar.core.LendState
import com.verified.incar.core.OpenTrip
import com.verified.incar.core.TripState
import com.verified.incar.core.Module
import com.verified.incar.core.Persona
import com.verified.incar.core.Pin
import com.verified.incar.core.Place
import com.verified.incar.core.Settings
import com.verified.incar.core.Snapshot
import com.verified.incar.core.TripLog
import com.verified.incar.core.TrunkState
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.ValetState
import org.json.JSONArray
import org.json.JSONObject

/** 앱이 다시 켜져도 이어 가는 세션: 목적을 고르기 전 운행, 진행 중인 운행, 빌려주기·맡김, ⑦ 트렁크 짐, ⑤ 연속 안전 출발. */
data class Session(val pending: TripLog?, val open: OpenTrip?, val lend: LendState, val trunk: TrunkState = TrunkState(), val streak: Int = 0)

/** 차 안 로컬 저장소. 좌표·자세 같은 민감값은 여기에만 두고 서버로 보내지 않는다. */
class Store(private val ctx: Context, private val vault: Vault = Vault.device, ns: String = "") {
    // [ns]: 체험 모드(TRIAL_NS)는 실제 기록과 다른 파일에 써서 예시 기록이 섞이지 않게 한다
    private val prefs = ctx.getSharedPreferences("verified$ns", Context.MODE_PRIVATE)
    // 해마다 쌓이는 운행기록은 따로 둔다(다른 기록을 저장할 때마다 운행기록까지 다시 쓰지 않게)
    private val tripPrefs = ctx.getSharedPreferences("verified_trips$ns", Context.MODE_PRIVATE)

    /** 체험 기록 전체를 지운다(체험 저장소에서만 부른다. 내보낸 CSV는 건드리지 않는다). */
    fun wipeAll() = prefs.edit().clear().commit() and tripPrefs.edit().clear().commit()

    companion object {
        const val TRIAL_NS = "_trial"
    }
    private fun prefsOf(key: String) = if (key == "trips") tripPrefs else prefs

    private fun read(key: String) = prefsOf(key).getString(key, null)?.let(vault::open)

    /** [now]: 대리 1차 완료처럼 전원이 바로 꺼질 수 있는 기록은 동기로 쓴다. */
    private fun write(key: String, value: String, now: Boolean = false) {
        // 암호화 키를 못 얻으면 이번 저장만 건너뛴다(평문으로 쓰거나 앱이 죽지 않게). 원래 기록은 그대로 남는다
        val sealed = runCatching { vault.seal(value) }.getOrNull() ?: return
        val e = prefsOf(key).edit().putString(key, sealed)
        if (now) e.commit() else e.apply()
    }

    /**
     * 저장된 기록이 깨져도 앱이 켜질 때마다 죽지 않도록 기본값으로 시작하고, 원본은 '_broken'으로 남긴다.
     * 처음 깨진 원본을 지키려고 이미 남긴 '_broken'은 덮어쓰지 않는다.
     */
    private fun <T> safe(key: String, default: T, parse: () -> T): T = runCatching(parse).getOrElse {
        val p = prefsOf(key)
        if (!p.contains("${key}_broken")) p.getString(key, null)?.let { raw -> p.edit().putString("${key}_broken", raw).commit() }
        default
    }

    /** 설정의 다른 칸이 깨져도 차주 PIN은 따로 읽어, 기본값으로 시작하면서 잠금이 풀리지 않게 한다. */
    fun loadSettings(): Settings {
        val d = Settings()
        val s = safe("settings", d) { parseSettings() }
        // 읽기에 성공했으면 PIN도 이미 읽었다. 기록이 깨졌을 때(safe가 같은 기본값을 돌려줌)만 PIN을 따로 다시 읽는다
        if (s !== d) return s
        val o = runCatching { read("settings")?.let { JSONObject(it) } }.getOrNull() ?: return s
        val hash = o.optString("ownerPinHash").ifEmpty { null } ?: return s
        return s.copy(ownerPinHash = hash, ownerPinSalt = o.optString("ownerPinSalt").ifEmpty { null }, ownerRecoveryHash = o.optString("ownerRecoveryHash").ifEmpty { null })
    }

    private fun parseSettings(): Settings {
        val o = read("settings")?.let { JSONObject(it) } ?: return Settings()
        fun <E : Enum<E>> set(key: String, parse: (String) -> E) = o.optJSONArray(key)?.strings()?.mapNotNull { runCatching { parse(it) }.getOrNull() }?.toSet()
        val d = Settings()
        return Settings(
            personas = set("personas", Persona::valueOf) ?: d.personas,
            extraModules = set("extraModules", Module::valueOf) ?: d.extraModules,
            flags = set("flags", Flag::valueOf) ?: d.flags,
            consents = set("consents", Consent::valueOf) ?: d.consents,
            profiles = o.optJSONArray("profiles")?.let { a ->
                (0 until a.length()).map { i ->
                    val p = a.getJSONObject(i)
                    val v = p.getJSONArray("posture")
                    DriverProfile(p.getString("name"), (0 until v.length()).map { v.getDouble(it).toFloat() }, p.getBoolean("insured"))
                }
            } ?: d.profiles,
            insuranceScope = o.optString("insuranceScope", d.insuranceScope),
            policyEndDate = o.optString("policyEndDate", d.policyEndDate),
            businessMode = o.optBoolean("businessMode", d.businessMode),
            carModel = o.optString("carModel").ifEmpty { null }?.let { runCatching { CarModel.valueOf(it) }.getOrNull() },
            comfort = o.optJSONObject("comfort")?.let { c -> c.keys().asSequence().associateWith { k -> preset(c.getJSONObject(k)) } } ?: d.comfort,
            schedule = o.optJSONObject("schedule")?.let {
                DepartureSchedule(it.getBoolean("enabled"), it.getInt("hour"), it.getInt("minute"), it.getJSONArray("days").let { a -> (0 until a.length()).map(a::getInt).toSet() }, it.getInt("leadMin"))
            } ?: d.schedule,
            autoApplyOnBoard = o.optBoolean("autoApplyOnBoard", d.autoApplyOnBoard),
            pins = o.optJSONObject("pins")?.let { p ->
                p.keys().asSequence().mapNotNull { k ->
                    runCatching { Place.valueOf(k) }.getOrNull()?.let { place -> place to p.getJSONObject(k).let { Pin(it.getDouble("lat"), it.getDouble("lon")) } }
                }.toMap()
            } ?: d.pins,
            pinRadiusM = o.optInt("pinRadiusM", d.pinRadiusM),
            rampTurnsPerFloor = o.optJSONObject("rampTurns")?.let { r ->
                r.keys().asSequence().mapNotNull { k -> runCatching { Place.valueOf(k) to r.getDouble(k).toFloat() }.getOrNull() }.toMap()
            } ?: d.rampTurnsPerFloor,
            serviceLog = o.optJSONArray("serviceLog")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { r -> ServiceRecord(r.getString("date"), r.getDouble("odo"), r.getString("item")) } }
            } ?: d.serviceLog,
            bookings = o.optJSONArray("bookings")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { r -> Booking(r.getString("date"), r.getString("time"), r.getString("kind"), r.getString("shop"), r.optString("note")) } }
            } ?: d.bookings,
            sharedKeys = o.optJSONArray("sharedKeys")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { r -> SharedKey(r.getString("name"), r.getString("until")) } }
            } ?: d.sharedKeys,
            enforcementAlert = o.optBoolean("enforcementAlert", d.enforcementAlert),
            memberPoints = o.optInt("memberPoints", d.memberPoints),
            ownerPinHash = o.optString("ownerPinHash").ifEmpty { null },
            ownerPinSalt = o.optString("ownerPinSalt").ifEmpty { null },
            ownerRecoveryHash = o.optString("ownerRecoveryHash").ifEmpty { null },
            consentAt = o.optJSONObject("consentAt")?.let { c ->
                c.keys().asSequence().mapNotNull { k -> runCatching { Consent.valueOf(k) to c.getLong(k) }.getOrNull() }.toMap()
            } ?: d.consentAt,
        )
    }

    fun saveSettings(s: Settings) {
        val o = JSONObject()
            .put("personas", JSONArray(s.personas.map { it.name }))
            .put("extraModules", JSONArray(s.extraModules.map { it.name }))
            .put("flags", JSONArray(s.flags.map { it.name }))
            .put("consents", JSONArray(s.consents.map { it.name }))
            .put("profiles", JSONArray(s.profiles.map {
                JSONObject().put("name", it.name).put("posture", JSONArray(it.posture.map(Float::toDouble))).put("insured", it.insured)
            }))
            .put("insuranceScope", s.insuranceScope)
            .put("policyEndDate", s.policyEndDate)
            .put("businessMode", s.businessMode)
            .put("carModel", s.carModel?.name ?: "")
            .put("comfort", JSONObject(s.comfort.mapValues { (_, p) ->
                JSONObject().put("title", p.title).put("tempC", p.tempC.toDouble()).put("driverHeat", p.driverHeat).put("passengerHeat", p.passengerHeat)
                    .put("rearHeat", p.rearHeat).put("driverVent", p.driverVent).put("steeringHeat", p.steeringHeat).put("defrost", p.defrost)
            }))
            .put("schedule", s.schedule.let {
                JSONObject().put("enabled", it.enabled).put("hour", it.hour).put("minute", it.minute).put("days", JSONArray(it.days.sorted())).put("leadMin", it.leadMin)
            })
            .put("autoApplyOnBoard", s.autoApplyOnBoard)
            .put("pins", JSONObject(s.pins.map { (k, v) -> k.name to JSONObject().put("lat", v.lat).put("lon", v.lon) }.toMap()))
            .put("pinRadiusM", s.pinRadiusM)
            .put("consentAt", JSONObject(s.consentAt.mapKeys { it.key.name }))
            .put("serviceLog", JSONArray(s.serviceLog.map { JSONObject().put("date", it.date).put("odo", it.odoKm).put("item", it.item) }))
            .put("bookings", JSONArray(s.bookings.map { JSONObject().put("date", it.date).put("time", it.time).put("kind", it.kind).put("shop", it.shop).put("note", it.note) }))
            .put("sharedKeys", JSONArray(s.sharedKeys.map { JSONObject().put("name", it.name).put("until", it.until) }))
            .put("enforcementAlert", s.enforcementAlert)
            .put("memberPoints", s.memberPoints)
            .put("ownerPinHash", s.ownerPinHash.orEmpty()).put("ownerPinSalt", s.ownerPinSalt.orEmpty())
            .put("ownerRecoveryHash", s.ownerRecoveryHash.orEmpty())
            .put("rampTurns", JSONObject(s.rampTurnsPerFloor.map { (k, v) -> k.name to v.toDouble() }.toMap()))
        write("settings", o.toString())
    }

    fun loadTrips(): List<TripLog> {
        // 예전 'verified'의 운행기록은 새 파일에 쓴 뒤에만 지운다(옮기다 꺼져도 잃지 않게). 암호문은 같은 키라 그대로 옮긴다
        prefs.getString("trips", null)?.let { old ->
            if (tripPrefs.contains("trips") || tripPrefs.edit().putString("trips", old).commit()) prefs.edit().remove("trips").commit()
        }
        return safe("trips", emptyList()) { parseTrips() }
    }

    private fun parseTrips(): List<TripLog> {
        val a = read("trips")?.let { JSONArray(it) } ?: return emptyList()
        return (0 until a.length()).map { i -> tripOf(a.getJSONObject(i)) }
    }

    private fun tripOf(o: JSONObject) = TripLog(o.getString("date"), o.getString("driver"), o.getString("from"), o.getString("to"),
        o.getDouble("startOdo"), o.getDouble("endOdo"), o.getString("purpose"), o.getBoolean("business"))

    private fun tripJson(it: TripLog) = JSONObject().put("date", it.date).put("driver", it.driver).put("from", it.from).put("to", it.to)
        .put("startOdo", it.startOdo).put("endOdo", it.endOdo).put("purpose", it.purpose).put("business", it.business)

    /** [now]: 시동 OFF 때는 전원이 바로 끊길 수 있어 동기로 쓴다. */
    fun saveTrips(logs: List<TripLog>, now: Boolean = false) {
        write("trips", JSONArray(logs.map(::tripJson)).toString(), now)
    }

    private fun snapJson(s: Snapshot?) = s?.let { JSONObject().put("t", it.timeMs).put("soc", it.socPct).put("odo", it.odometerKm).put("window", it.windowOpen).put("steer", it.steeringDeg.toDouble()) }

    private fun snapOf(o: JSONObject?) = o?.let { Snapshot(it.getLong("t"), it.getInt("soc"), it.getDouble("odo"), it.getBoolean("window"), it.getDouble("steer").toFloat()) }

    /** 목적을 고르기 전인 운행, 진행 중인 운행, 빌려주기·맡김. 앱이 다시 켜져도 이어 가도록 따로 저장한다. */
    fun saveSession(t: TripState, l: LendState, k: TrunkState = TrunkState(), streak: Int = 0, now: Boolean = false) {
        val o = JSONObject()
            .put("trunk", JSONObject().put("loaded", k.loaded).put("loadedAt", k.loadedAt ?: "").put("reminded", k.reminded))
            .put("streak", streak)
        t.pending?.let { o.put("pending", tripJson(it)) }
        t.open?.let { o.put("open", JSONObject().put("startMs", it.startMs).put("startOdo", it.startOdo).put("from", it.from.name).put("driver", it.driver)) }
        o.put("lend", JSONObject().put("active", l.active).put("start", snapJson(l.start)).put("brakes", l.brakes).put("maxSpeed", l.maxSpeed.toDouble())
            .put("kind", l.kind.name).put("starts", l.starts).put("doorOpens", l.doorOpens).put("otherSeen", l.otherSeen).put("endedAtMs", l.endedAtMs)
            .put("report", JSONArray(l.report.map { JSONArray(listOf(it.first, it.second)) })).put("proof", proofJson(l.proof))
            .putOpt("checkIn", checkJson(l.checkIn)).putOpt("checkOut", checkJson(l.checkOut)).put("prep", JSONArray(l.prep.map { it.name }))
            .putOpt("startLat", l.startLat).putOpt("startLon", l.startLon).putOpt("startAccuracy", l.startAccuracyM)
            .put("roamWarned", l.roamWarned).put("parkedSocUsed", l.parkedSocUsed)
            .put("idleMs", l.idleMs).putOpt("idleSince", l.idleSinceMs).put("speedWarned", l.speedWarned))
        write("session", o.toString(), now)
    }

    fun loadSession(): Session = safe("session", Session(null, null, LendState())) {
        val o = read("session")?.let { JSONObject(it) } ?: return@safe Session(null, null, LendState())
        val open = o.optJSONObject("open")?.let { OpenTrip(it.getLong("startMs"), it.getDouble("startOdo"), runCatching { Place.valueOf(it.getString("from")) }.getOrDefault(Place.OTHER), it.getString("driver")) }
        val lend = o.optJSONObject("lend")?.let { l ->
            LendState(
                active = l.getBoolean("active"), start = snapOf(l.optJSONObject("start")), brakes = l.getInt("brakes"), maxSpeed = l.getDouble("maxSpeed").toFloat(),
                report = l.optJSONArray("report")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getString(0) to p.getString(1) } } } ?: emptyList(),
                kind = runCatching { HandKind.valueOf(l.getString("kind")) }.getOrDefault(HandKind.LEND),
                starts = l.optInt("starts"), doorOpens = l.optInt("doorOpens"), otherSeen = l.optBoolean("otherSeen"), endedAtMs = l.optLong("endedAtMs"),
                proof = proofOf(l.optJSONArray("proof")),
                checkIn = checkOf(l.optJSONObject("checkIn")), checkOut = checkOf(l.optJSONObject("checkOut")), prep = prepOf(l.optJSONArray("prep")),
                startLat = l.optDoubleOrNull("startLat"), startLon = l.optDoubleOrNull("startLon"),
                startAccuracyM = l.optDoubleOrNull("startAccuracy")?.toFloat(),
                roamWarned = l.optBoolean("roamWarned"), parkedSocUsed = l.optInt("parkedSocUsed"),
                idleMs = l.optLong("idleMs"), idleSinceMs = if (l.has("idleSince")) l.getLong("idleSince") else null, speedWarned = l.optBoolean("speedWarned"),
            )
        } ?: LendState()
        // 트렁크·연속 기록은 나중에 더한 칸이라 opt로 읽는다(예전 기록에서 세션 전체를 잃지 않게)
        val trunk = o.optJSONObject("trunk")?.let { TrunkState(loaded = it.optBoolean("loaded"), loadedAt = it.optString("loadedAt").ifEmpty { null }, reminded = it.optBoolean("reminded")) } ?: TrunkState()
        Session(o.optJSONObject("pending")?.let(::tripOf), open, lend, trunk, o.optInt("streak"))
    }

    /** 보낸 알림(보관 중인 알림 포함). 앱이 다시 켜져도 보관한 알림을 잃지 않게 저장한다. */
    private fun noticesJson(list: List<SentNotice>) = JSONArray(list.map {
            JSONObject().put("t", it.timeMs).put("target", it.target).put("title", it.title).put("body", it.body).put("level", it.level.name).put("delivered", it.delivered)
                .putOpt("phoneStatus", it.phoneStatus?.name).putOpt("phoneId", it.phoneId)
                .put("phoneExample", it.phoneExample).put("phoneAttempts", it.phoneAttempts).put("phoneRetryAt", it.phoneRetryAt)
        }).toString()

    fun saveNotices(list: List<SentNotice>): Boolean = runCatching {
        prefs.edit().putString("notices", vault.seal(noticesJson(list))).commit()
    }.getOrDefault(false)

    fun loadPhoneTopic(): String? = runCatching { read("phoneTopic")?.takeIf(PhoneRelay::validTopic) }.getOrNull()

    // 구독을 해제하면 대기열도 같은 트랜잭션에서 닫아 다음 구독자에게 옛 기록이 가지 않게 한다.
    fun savePhonePairing(topic: String?, notices: List<SentNotice>): Boolean = runCatching {
        val edit = prefs.edit().putString("notices", vault.seal(noticesJson(notices)))
        if (topic == null) edit.remove("phoneTopic") else edit.putString("phoneTopic", vault.seal(topic))
        edit.commit()
    }.getOrDefault(false)

    fun loadNotices(): List<com.verified.incar.SentNotice> = safe("notices", emptyList()) {
        val a = read("notices")?.let { JSONArray(it) } ?: return@safe emptyList()
        (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                com.verified.incar.SentNotice(
                    it.getLong("t"), it.getString("target"), it.getString("title"), it.getString("body"),
                    runCatching { com.verified.incar.core.Level.valueOf(it.getString("level")) }.getOrDefault(com.verified.incar.core.Level.INFO), it.optBoolean("delivered", true),
                    phoneStatus = runCatching { PhoneDelivery.valueOf(it.getString("phoneStatus")) }.getOrNull(),
                    phoneId = it.optString("phoneId").ifEmpty { null }, phoneExample = it.optBoolean("phoneExample"),
                    phoneAttempts = it.optInt("phoneAttempts"), phoneRetryAt = it.optLong("phoneRetryAt"),
                )
            }
        }
    }

    /** 차주 PIN을 틀린 횟수. 앱을 강제로 껐다 켜서 시도 횟수를 되돌리지 못하게 남긴다(비밀값이 아니라 평문). */
    fun saveLock(tries: Int, rounds: Int, locked: Boolean) {
        prefs.edit().putInt("lock_tries", tries).putInt("lock_rounds", rounds).putBoolean("lock_locked", locked).commit()
    }

    fun loadLock() = Triple(prefs.getInt("lock_tries", 0), prefs.getInt("lock_rounds", 0), prefs.getBoolean("lock_locked", false))

    private fun proofJson(p: List<com.verified.incar.core.Proof>) = JSONArray(p.map { JSONObject().put("t", it.atMs).put("w", it.what).put("s", it.src) })

    // 근거는 나중에 더한 칸이라 없으면 빈 목록(예전 기록도 읽힌다)
    private fun proofOf(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getJSONObject(i).let { o -> com.verified.incar.core.Proof(o.getLong("t"), o.getString("w"), o.getString("s")) } } } ?: emptyList()

    fun loadValet(): ValetState = safe("valet", ValetState()) { parseValet() }

    private fun parseValet(): ValetState {
        val o = read("valet")?.let { JSONObject(it) } ?: return ValetState()
        fun snap(k: String) = snapOf(o.optJSONObject(k))
        val state = ValetState(
            phase = runCatching { ValetPhase.valueOf(o.getString("phase")) }.getOrDefault(ValetPhase.IDLE),
            handover = snap("handover"),
            result = snap("result"),
            prelimAtMs = if (o.has("prelimAt")) o.getLong("prelimAt") else null,
            confirmedBy = o.optString("confirmedBy").ifEmpty { null },
            placeEstimated = o.optBoolean("placeEstimated"),
            moved = o.optBoolean("moved", true),
            proof = proofOf(o.optJSONArray("proof")),
            checkIn = checkOf(o.optJSONObject("checkIn")), checkOut = checkOf(o.optJSONObject("checkOut")), prep = prepOf(o.optJSONArray("prep")),
            naviEtaMs = if (o.has("naviEta")) o.getLong("naviEta") else null, naviOffWarned = o.optBoolean("naviOffWarned"),
        )
        if (o.has("idleMs") || o.has("idleSince") || o.has("maxSpeed") || o.has("harshBrake")) saveValet(state)
        return state
    }

    // 인수인계서·체크리스트·이력서는 나중에 더한 칸이라 opt로 읽는다(예전 기록도 그대로 읽힌다)
    private fun JSONObject.optDoubleOrNull(k: String) = if (has(k)) getDouble(k) else null

    private fun checkJson(c: CarCheck?) = c?.let {
        JSONObject().put("t", it.atMs).putOpt("odo", it.odometerKm).putOpt("soc", it.socPct).putOpt("warn", it.warnings?.let { w -> JSONArray(w.toList()) })
            .putOpt("tire", it.tireLow).putOpt("washer", it.washerLow).putOpt("oil", it.oilLow)
            .putOpt("window", it.windowOpen).putOpt("trunk", it.trunkOpen).putOpt("lock", it.doorsLocked)
            .putOpt("steering", it.steeringDeg?.toDouble()).putOpt("speed", it.speedKmh?.toDouble())
    }

    private fun checkOf(o: JSONObject?) = o?.let {
        fun b(k: String) = if (it.has(k)) it.getBoolean(k) else null
        CarCheck(
            it.getLong("t"), it.optDoubleOrNull("odo"), if (it.has("soc")) it.getInt("soc") else null, it.optJSONArray("warn")?.strings()?.toSet(),
            b("tire"), b("washer"), b("oil"), b("window"), b("trunk"), b("lock"), it.optDoubleOrNull("steering")?.toFloat(), it.optDoubleOrNull("speed")?.toFloat(),
        )
    }

    private fun prepOf(a: JSONArray?) = a?.strings()?.mapNotNull { runCatching { PrepItem.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet()

    /** 맡김 이력서(대리·맡김·빌려주기). 5년 보관은 불러올 때 거른다. */
    fun saveHistory(h: List<HandoverRecord>, now: Boolean = false) {
        write("history", JSONArray(h.map { original ->
            val it = original.respectingValetConsent()
            JSONObject().put("kind", it.kind).put("start", it.startMs).put("end", it.endMs).putOpt("km", it.km)
                .put("speedKnown", it.speedKnown).put("changes", JSONArray(it.changes)).put("ok", it.ok)
                .also { o -> if (it.recordsDrivingStats) o.put("max", it.maxSpeed.toDouble()).put("brakes", it.brakes) }
        }).toString(), now)
    }

    fun loadHistory(): List<HandoverRecord> = safe("history", emptyList()) {
        val a = read("history")?.let { JSONArray(it) } ?: return@safe emptyList()
        var migrated = false
        val history = (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                val record = HandoverRecord(it.getString("kind"), it.getLong("start"), it.getLong("end"), it.optDoubleOrNull("km"), it.optDouble("max", 0.0).toFloat(),
                    it.optInt("brakes"), it.optJSONArray("changes")?.strings() ?: emptyList(), it.getBoolean("ok"), it.optBoolean("speedKnown", it.optDouble("max", 0.0) > 0.0))
                val cleaned = record.respectingValetConsent()
                if (cleaned != record || (!record.recordsDrivingStats && (it.has("max") || it.has("brakes")))) migrated = true
                cleaned
            }
        }
        if (migrated) saveHistory(history, now = true)
        history
    }

    /** 1차 완료가 전원 차단 전에 남도록 동기(commit)로 쓴다. */
    fun saveValet(v: ValetState) {
        val o = JSONObject().put("phase", v.phase.name).put("handover", snapJson(v.handover)).put("result", snapJson(v.result))
            .put("confirmedBy", v.confirmedBy ?: "").put("placeEstimated", v.placeEstimated).put("moved", v.moved).put("proof", proofJson(v.proof))
            .putOpt("checkIn", checkJson(v.checkIn)).putOpt("checkOut", checkJson(v.checkOut)).put("prep", JSONArray(v.prep.map { it.name }))
            .putOpt("naviEta", v.naviEtaMs).put("naviOffWarned", v.naviOffWarned)
        v.prelimAtMs?.let { o.put("prelimAt", it) }
        write("valet", o.toString(), now = true)
    }

    fun loadStats(): List<DailyStat> = safe("stats", emptyList()) { parseStats() }

    private fun parseStats(): List<DailyStat> {
        val a = read("stats")?.let { JSONArray(it) } ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it).let { r -> DailyStat(
                r.getString("date"), r.getDouble("km"), r.getInt("brakes"), r.getInt("accels"), r.getInt("soc"),
                tires = r.optJSONArray("tires")?.let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } } ?: emptyList(),
            ) } }
    }

    fun saveStats(stats: List<DailyStat>, now: Boolean = false) {
        write("stats", JSONArray(stats.map {
            JSONObject().put("date", it.date).put("km", it.km).put("brakes", it.brakes).put("accels", it.accels).put("soc", it.socUsed)
                .put("tires", JSONArray(it.tires.map(Float::toDouble)))
        }).toString(), now)
    }

    /** 내 기록 지우기: 운행기록·대리 기록·주행 통계를 지운다(설정은 호출한 쪽에서 덮어쓴다). */
    /** '내 기록 지우기': 깨진 기록 사본과 내보낸 운행기록부 CSV까지 지운다. */
    fun clearRecords() {
        // 가장 민감한 출발지·도착지 기록부터 지운다
        tripPrefs.edit().clear().commit()
        // 다운로드 폴더에 내보낸 CSV(이 앱이 만든 것만 보인다)
        runCatching {
            ctx.contentResolver.delete(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("운행기록부_%.csv", "맡김이력서_%.csv"))
        }
        prefs.edit().remove("trips").remove("valet").remove("stats").remove("session").remove("notices").remove("notices_broken")
            .remove("history").remove("history_broken").remove("phoneTopic")
            .remove("settings_broken").remove("trips_broken").remove("valet_broken").remove("stats_broken").remove("session_broken").commit()
        listOfNotNull(ctx.getExternalFilesDir(null), ctx.filesDir).forEach { dir ->
            dir.listFiles { f -> (f.name.startsWith("운행기록부_") || f.name.startsWith("맡김이력서_")) && f.name.endsWith(".csv") }?.forEach { it.delete() }
        }
    }

    private fun preset(o: JSONObject) = ComfortPreset(
        o.getString("title"), o.getDouble("tempC").toFloat(), o.getInt("driverHeat"), o.getInt("passengerHeat"),
        o.getInt("rearHeat"), o.getInt("driverVent"), o.getBoolean("steeringHeat"), o.getBoolean("defrost"),
    )

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
