package com.verified.incar.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max

/**
 * 마이현대 앱 기능을 차 안에서 쓰는 형태로 옮긴 것.
 * 차량 신호로 계산할 수 있는 것(차량 상태·소모품·인사이트·피드)은 실제로 계산하고,
 * 현대 서버가 필요한 것(예약·결제·검색·포인트)은 화면과 흐름만 두고 실행은 목업으로 표시한다.
 */
data class ServiceRecord(val date: String, val odoKm: Double, val item: String)

/** [note]는 예약과 함께 정비소에 보낸 '차가 확인한 사실'(확인됨 보강). */
data class Booking(val date: String, val time: String, val kind: String, val shop: String, val note: String = "")

data class SharedKey(val name: String, val until: String)

/** 하루 단위 주행 기록. 드라이빙 인사이트의 재료이며 점수는 매기지 않는다. */
data class DailyStat(val date: String, val km: Double = 0.0, val brakes: Int = 0, val accels: Int = 0, val socUsed: Int = 0, val tires: List<Float> = emptyList())

/**
 * 소모품 교체 주기 [제안값 · 차종별 정비 주기표 확인 필요].
 * 덜 달려도 시간이 지나면 낡는 것은 [intervalMonths]도 둔다(LubeLogger처럼 거리·기간 중 먼저 오는 쪽). 0이면 거리만 본다.
 */
enum class Consumable(val label: String, val intervalKm: Int, val intervalMonths: Int) {
    ENGINE_OIL("엔진오일·필터", 10_000, 12),
    TIRE_ROTATION("타이어 위치 교환", 10_000, 12),
    WIPER("와이퍼 블레이드", 10_000, 12),
    AIRCON_FILTER("에어컨 필터", 15_000, 12),
    BRAKE_PAD("브레이크 패드", 40_000, 0),
    BRAKE_FLUID("브레이크액", 40_000, 24),
}

/**
 * [remainKm]가 null이면 교체 기록이 없어 아직 판단하지 않는다(처음 쓰는 차를 '교체 시기 지남'으로 몰지 않기 위해).
 * 확인됨 보강: [etaDays]는 최근 하루 주행거리로 본 남은 날짜, [harsh]면 급감속이 잦아 주기를 줄여 계산했다.
 */
data class ConsumableStatus(val item: Consumable, val lastKm: Double?, val remainKm: Double?, val etaDays: Int? = null, val harsh: Boolean = false, val daysLeft: Long? = null) {
    private val kmDue get() = remainKm != null && remainKm <= 0
    private val dateDue get() = daysLeft != null && daysLeft < 0
    val due get() = kmDue || dateDue
    val soon get() = !due && ((remainKm != null && remainKm <= SOON_KM) || (daysLeft != null && daysLeft <= SOON_DAYS))

    /** 거리·기간 중 먼저 닿는 쪽으로 쓴 남은 양. 교체 기록이 없으면 null. */
    val text: String? get() = when {
        remainKm == null -> null
        kmDue -> "%,.0fkm 지남".format(java.util.Locale.ROOT, -remainKm)
        dateDue -> "교체 기간 ${-daysLeft!!}일 지남"
        else -> "%,.0fkm 남음".format(java.util.Locale.ROOT, remainKm) +
            (listOfNotNull(etaDays?.toLong(), daysLeft).minOrNull()?.let { if (it > 365) " · 1년 넘게" else " · 약 ${it}일 뒤" } ?: "")
    }

    companion object {
        /** 기존 거리 기준(1,000km)과 LubeLogger 기본값(30일) [제안값]. */
        const val SOON_KM = 1_000
        const val SOON_DAYS = 30
    }
}

data class WeekInsight(val km: Double, val brakes: Int, val accels: Int, val socUsed: Int, val days: Int) {
    /** 배터리·연료 1%당 주행거리. 쓴 양이 없으면 null. */
    val kmPerPct get() = if (socUsed > 0) km / socUsed else null
}

/** [signal] identifies an alert derived from one current reading, shared with the home checklist. */
data class FeedCard(val title: String, val body: String, val level: Level, val tab: String, val signal: String? = null)

/** 홈 맨 위 한 줄 판정(BMW 'ALL GOOD'·Audi 'securely parked'처럼). [basis]는 언제·무엇으로 판정했는지. */
data class HomeVerdict(val word: String, val detail: String, val level: Level, val tab: String?, val basis: String)

/** 주변 검색 결과(목업). 실제 검색은 Pleos NaviHelper·지도 API 승인 후 연결한다. */
/** 충전기 조건. 환경공단 충전기 정보의 커넥터·상태·이용 제한·위치 안내 항목에 맞춘 구조. */
data class Charger(val connector: String, val indoor: Boolean, val working: Boolean, val open: Boolean, val spot: String)

data class Poi(val category: String, val name: String, val distanceKm: Double, val note: String, val charger: Charger? = null)

/** 경로 끝까지 갔을 때의 배터리. [reasons]는 평소보다 줄여 잡은 이유. */
data class TripBattery(val arrivalPct: Int, val reasons: List<String>) {
    val low get() = arrivalPct < MyHyundai.TRIP_LOW_PCT
}

/** 무선 프로젝션 증상별 점검 순서. 앱이 연결을 고치지는 못하고 순서만 안내한다. */
data class ProjectionHelp(val symptom: String, val steps: List<String>)

data class ShopItem(val store: String, val name: String, val priceWon: Int, val note: String)

enum class ControlKind(val label: String) { LOCK("문 잠금"), UNLOCK("문 잠금 해제"), CLIMATE_ON("공조 켜기"), CLIMATE_OFF("공조 끄기"), HAZARD("비상등"), HORN("경적") }

object MyHyundai {
    /** 100% 기준 주행가능거리 [제안값 · 차종별 공인 거리로 교체]. */
    const val RANGE_KM_FULL = 475
    /** 기온이 낮을 때 주행가능거리 비율 [제안값]. */
    const val COLD_RANGE_FACTOR = 0.8
    /** 시속 [FAST_KMH] 이상이면 공기 저항으로 주행가능거리를 줄여 본다 [제안값]. */
    const val FAST_RANGE_FACTOR = 0.85
    const val FAST_KMH = 110f
    const val TRIP_LOW_PCT = 10
    /** 100km당 급감속이 이 횟수 이상이면 브레이크 패드를 가혹 조건으로 본다 [제안값]. */
    const val HARSH_BRAKES_PER_100KM = 5.0
    const val HARSH_FACTOR = 0.7

    /**
     * 마지막 교체 기록부터 남은 거리를 계산한다. 주행 기록이 있으면 예상 날짜와 급감속 보정까지 더한다.
     * [today]를 주면 교체한 날부터의 기간도 본다(덜 달린 차의 엔진오일·브레이크액).
     */
    fun consumables(odoKm: Double, records: List<ServiceRecord>, stats: List<DailyStat> = emptyList(), today: LocalDate? = null): List<ConsumableStatus> {
        val daily = avgDailyKm(stats)
        val harsh = harshBraking(stats)
        return Consumable.entries.map { c ->
            val lastRecord = records.filter { it.item == c.label }.maxByOrNull { it.odoKm }
            val last = lastRecord?.odoKm
            val shortened = harsh && c == Consumable.BRAKE_PAD
            val remain = last?.let { it + c.intervalKm * (if (shortened) HARSH_FACTOR else 1.0) - odoKm }
            val eta = if (remain != null && remain > 0 && daily != null) (remain / daily).toInt() else null
            val doneOn = lastRecord?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            val daysLeft = if (today == null || doneOn == null || c.intervalMonths == 0) null
                else ChronoUnit.DAYS.between(today, doneOn.plusMonths(c.intervalMonths.toLong()))
            ConsumableStatus(c, last, remain, eta, shortened, daysLeft)
        }
    }

    /** 기록이 있는 기간의 하루 평균 주행거리. 하루치 기록으로 너무 크게 잡지 않도록 최소 7일로 나눈다. */
    fun avgDailyKm(stats: List<DailyStat>): Double? {
        val dates = stats.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        if (dates.isEmpty()) return null
        val span = ChronoUnit.DAYS.between(dates.min(), dates.max()) + 1
        return (stats.sumOf { it.km } / max(7L, span)).takeIf { it > 0 }
    }

    /** 100km 이상 달린 기록에서 급감속이 잦은가. 점수를 매기지 않고 패드 주기 계산에만 쓴다. */
    fun harshBraking(stats: List<DailyStat>): Boolean {
        val km = stats.sumOf { it.km }
        return km >= 100 && stats.sumOf { it.brakes } * 100.0 / km >= HARSH_BRAKES_PER_100KM
    }

    /** 정비 예약에 함께 보내는 '차가 확인한 사실'. 고장 판정이 아니라 읽은 값만 적는다. */
    fun serviceNote(s: Signals, cons: List<ConsumableStatus>, findings: List<DiagFinding>): List<String> = buildList {
        add("주행거리 %,.0fkm".format(java.util.Locale.ROOT, s.odometerKm))
        cons.filter { it.due || it.soon }.forEach { add(it.item.label + if (it.due) " 교체 시기 지남" else " 교체 임박") }
        if (s.tirePressureLow) add("타이어 공기압 낮음")
        if (s.washerLow) add("워셔액 부족")
        findings.filter { it.code != "R99" && it.code != "R06" }.forEach { add("확인 도우미: ${it.title}") }
    }

    /** 긴급출동 상담원에게 요청과 함께 보내는 차 상황. 요청 버튼을 누를 때만 보낸다. */
    fun emergencySummary(s: Signals, st: EngineState): List<String> = listOfNotNull(
        "위치 " + (st.floor.confirmed?.let { "${st.floor.place.label} 지하 ${it}층" } ?: s.place.label),
        "배터리·연료 ${s.socPct}%",
        "외기 %.0f℃".format(s.outsideTempC),
        "시동 " + if (s.ignitionOn) "켜짐" else "꺼짐",
        "탑승 ${listOf(s.driverSeated, s.passengerSeated, s.row2Seated).count { it }}명",
        "타이어 공기압 낮음".takeIf { s.tirePressureLow },
        "비상등 켜짐".takeIf { s.hazardOn },
    )

    /** 자동 세차 들어가기 전 점검. 와이퍼가 AUTO면 세차 물에 반응해 움직이다 손상될 수 있다. */
    fun washCheck(s: Signals): List<Pair<String, Boolean>> = listOf(
        "창문 닫힘" to !s.windowOpen,
        "트렁크 닫힘" to !s.trunkOpen,
        "와이퍼 끔 (AUTO면 세차 중에 움직여요)" to (s.wiper == Wiper.OFF),
    )

    /** 충전소까지 가면 남을 배터리 %. 기온이 낮으면 주행가능거리를 줄여 본다. */
    fun arrivalSoc(socPct: Int, km: Double, cold: Boolean, fast: Boolean = false): Int {
        val kmPerPct = RANGE_KM_FULL * (if (cold) COLD_RANGE_FACTOR else 1.0) * (if (fast) FAST_RANGE_FACTOR else 1.0) / 100
        return socPct - ceil(km / kmPerPct).toInt()
    }

    /** 내비 목적지까지 남은 거리로 도착 배터리를 어림한다. 고도·바람은 읽을 수 없어 넣지 않는다. */
    fun tripBattery(s: Signals): TripBattery? {
        val km = s.naviRemainKm ?: return null
        val cold = s.outsideTempC <= VerdictEngine.WINTER_C
        val fast = s.speedKmh >= FAST_KMH
        return TripBattery(arrivalSoc(s.socPct, km.toDouble(), cold, fast), listOfNotNull("추운 날씨".takeIf { cold }, "고속 주행".takeIf { fast }))
    }

    /** 단속 일시가 어느 맡김에 들어가는지 찾는다. */
    fun handoverAt(history: List<HandoverRecord>, atMs: Long) = history.lastOrNull { atMs in it.startMs..it.endMs }

    /** "2026-11-10 14:30"처럼 숫자 12자리가 들어 있는 일시를 읽는다. */
    fun parseDateTime(text: String): Long? {
        val digits = text.filter { it.isDigit() }.takeIf { it.length == 12 } ?: return null
        return runCatching {
            java.time.LocalDateTime.parse(digits, java.time.format.DateTimeFormatter.ofPattern("uuuuMMddHHmm"))
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    val CHARGER_FILTERS: List<Pair<String, (Charger) -> Boolean>> = listOf(
        "사용 가능" to { it.working }, "외부 출입 가능" to { it.open }, "지하·실내" to { it.indoor }, "DC콤보" to { it.connector == "DC콤보" },
    )

    val PROJECTION_HELP = listOf(
        ProjectionHelp("자꾸 끊겨요", listOf("폰 설정에서 Android Auto의 배터리 사용을 '제한 없음'으로 바꿔 보세요.", "무선 충전 패드에서 폰이 뜨거우면 패드에서 내려 보세요.", "VPN·광고 차단 앱을 잠시 꺼 보세요.", "폰을 가방이나 뒷주머니 대신 앞좌석 쪽에 두세요.")),
        ProjectionHelp("연결이 안 돼요", listOf("폰의 핫스팟을 끄세요.", "폰의 Wi-Fi와 블루투스가 켜져 있는지 확인하세요.", "차량 업데이트 직후라면 폰과 차 양쪽에서 등록을 지우고 다시 연결해 보세요.")),
        ProjectionHelp("다른 폰으로 넘어가요", listOf("차의 폰 연결 목록에서 운전자 폰을 우선으로 지정하세요.", "동승자 폰은 자동 연결을 끄거나 목록에서 지우세요.")),
        ProjectionHelp("톨게이트·터널에서 멈춰요", listOf("전파 간섭이나 GPS 끊김일 수 있어요. 통과 후 잠시 기다려 보세요.", "주행 중에는 조작하지 말고, 안전한 곳에 세운 뒤 다시 연결하세요.", "자주 반복되면 유선 연결이나 차량 내비를 쓰세요.")),
        ProjectionHelp("유선도 안 돼요", listOf("무선 연결을 해제한 뒤 케이블을 다시 꽂아 보세요.", "충전 전용이 아닌 데이터 케이블인지 확인하세요.", "차의 데이터 지원 USB 포트에 꽂았는지 확인하세요.")),
        ProjectionHelp("목적지가 사라져요", listOf("연결이 돌아오면 내비 앱의 최근 목적지에서 다시 고르세요.", "끊김이 잦은 경로에서는 차량 내비로 안내받으세요.")),
    )

    /** 오늘이 속한 주(월~일)와 그 전 주를 비교한다. */
    fun weeks(stats: List<DailyStat>, today: LocalDate): Pair<WeekInsight, WeekInsight> {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        fun week(from: LocalDate): WeekInsight {
            val days = stats.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> !d.isBefore(from) && d.isBefore(from.plusDays(7)) } == true }
            return WeekInsight(days.sumOf { it.km }, days.sumOf { it.brakes }, days.sumOf { it.accels }, days.sumOf { it.socUsed }, days.size)
        }
        return week(monday) to week(monday.minusDays(7))
    }

    /** 하루 기록에 이번 신호 변화분을 더한다. 최근 60일만 남긴다. */
    fun addToDay(stats: List<DailyStat>, date: String, km: Double, brake: Boolean, accel: Boolean, socDrop: Int): List<DailyStat> {
        val today = stats.firstOrNull { it.date == date } ?: DailyStat(date)
        val next = today.copy(
            km = today.km + km, brakes = today.brakes + if (brake) 1 else 0,
            accels = today.accels + if (accel) 1 else 0, socUsed = today.socUsed + max(0, socDrop),
        )
        return (listOf(next) + stats.filter { it.date != date }).sortedByDescending { it.date }.take(60)
    }

    val WHEELS = listOf("왼쪽 앞", "오른쪽 앞", "왼쪽 뒤", "오른쪽 뒤")
    /** 한 바퀴만 다른 바퀴보다 이만큼 더 빠지면 느린 누설로 본다 [제안값]. */
    const val LEAK_KPA = 10f

    /** 주차 중 읽은 바퀴별 공기압의 하루 최저값을 남긴다. */
    fun recordTires(stats: List<DailyStat>, date: String, kpa: List<Float>): List<DailyStat> {
        if (kpa.size != 4) return stats
        val today = stats.firstOrNull { it.date == date } ?: DailyStat(date)
        val tires = if (today.tires.size == 4) today.tires.zip(kpa) { a, b -> minOf(a, b) } else kpa
        if (tires == today.tires) return stats
        return (listOf(today.copy(tires = tires)) + stats.filter { it.date != date }).sortedByDescending { it.date }.take(60)
    }

    /**
     * 순정 TPMS는 기준치(약 20~25% 저하)에 닿아야 켜진다. 최근 7일 중 3일 이상 기록이 있으면 네 바퀴를 서로 비교해
     * 한 바퀴만 더 빠졌는지 본다. 기온이 내려가면 네 바퀴가 같이 낮아지므로 서로 비교하면 그 영향이 빠진다.
     * 돌려주는 값: (바퀴 번호, 다른 바퀴보다 더 빠진 kPa). 없으면 null.
     */
    fun slowLeak(stats: List<DailyStat>): Pair<Int, Float>? {
        val days = stats.filter { it.tires.size == 4 }.sortedBy { it.date }.takeLast(7)
        if (days.size < 3) return null
        val drop = (0 until 4).map { days.first().tires[it] - days.last().tires[it] }
        val worst = drop.indices.maxBy { drop[it] }
        val others = drop.filterIndexed { i, _ -> i != worst }.sorted()[1]
        val extra = drop[worst] - others
        return if (extra >= LEAK_KPA && drop[worst] >= LEAK_KPA) worst to extra else null
    }

    /** 반납·인수 리포트를 피드에 올려 두는 시간 [제안값]. 리포트 자체는 ②·차량 관리 화면에 남는다. */
    const val REPORT_FEED_MS = 24 * 60 * 60_000L

    /**
     * [lastReadAtMs]에는 홈에 표시한 항목 중 가장 최근 수신 시각만 전달한다.
     */
    fun verdict(feed: List<FeedCard>, lastReadAtMs: Long?, carConnected: Boolean, simAllowed: Boolean, unread: Int): HomeVerdict {
        val needs = feed.filter { it.level == Level.WARN || it.level == Level.DANGER }
        val firstIssue = needs.firstOrNull { it.level == Level.DANGER } ?: needs.firstOrNull()
        val basis = when {
            simAllowed && carConnected -> "시연 모드 · 차량 신호와 예시 값 사용"
            simAllowed -> "차량 연결 전 · 예시 값 기준"
            !carConnected -> "차량에 연결하면 상태를 확인할 수 있어요"
            lastReadAtMs == null -> "아직 차 상태 신호를 받지 못했어요"
            else -> "최근 차 신호 ${VerdictEngine.hhmm(lastReadAtMs)}" + if (unread > 0) " · 확인 불가 ${unread}건" else ""
        }
        return when {
            firstIssue != null -> HomeVerdict("확인 필요 ${needs.size}건", firstIssue.title, firstIssue.level, firstIssue.tab, basis)
            !simAllowed && !carConnected -> HomeVerdict("차량 미연결", "", Level.INFO, null, basis)
            !simAllowed && lastReadAtMs == null -> HomeVerdict("확인 중", "", Level.INFO, null, basis)
            !simAllowed && unread > 0 -> HomeVerdict("일부 확인 불가", "", Level.INFO, null, basis)
            simAllowed -> HomeVerdict("예시 · 이상 없음", "지금 챙길 것이 없어요", Level.OK, null, basis)
            else -> HomeVerdict("읽은 항목 모두 정상", "지금 챙길 것이 없어요", Level.OK, null, basis)
        }
    }

    /** 맞춤형 피드. [nowMs]가 없으면 리포트 카드를 시간으로 거르지 않는다. */
    fun feed(s: Signals, st: EngineState, settings: Settings, cons: List<ConsumableStatus>, nowMs: Long? = null): List<FeedCard> = buildList {
        if (st.evac.active) add(FeedCard("비상등을 켜셨어요 · 먼저 대피하세요", "비상등 → 트렁크 열기 → 가드레일 밖 대피 → 신고(112·119, 한국도로공사 1588-2504)", Level.DANGER, "CARLIFE"))
        slowLeak(st.stats)?.let { (w, kpa) ->
            add(FeedCard("${WHEELS[w]} 바퀴만 천천히 빠지고 있어요", "최근 며칠 사이 다른 바퀴보다 %.0fkPa 더 낮아졌어요. 못이 박혔을 수 있으니 점검을 받아 보세요".format(kpa), Level.WARN, "SERVICE"))
        }
        cons.filter { it.due || it.soon }.forEach {
            val text = it.text ?: return@forEach
            add(FeedCard("${it.item.label} 교체 " + if (it.due) "시기 지남" else "임박", "$text · 블루핸즈 예약을 도와드려요", if (it.due) Level.WARN else Level.INFO, "SERVICE", signal = "odometer"))
        }
        if (s.washerLow) add(FeedCard("워셔액이 부족해요", "가까운 블루핸즈나 주유소에서 보충하세요", Level.WARN, "SERVICE", signal = "washer"))
        if (s.tirePressureLow) add(FeedCard("타이어 공기압이 낮아요", "출발 전에 공기압을 확인하세요", Level.WARN, "SERVICE", signal = "tire"))
        st.floor.confirmed?.let { add(FeedCard("주차 위치", "${st.floor.place.label} 지하 ${it}층", Level.INFO, Module.TRUNK.name)) }
        if (st.lend.report.isNotEmpty() && (nowMs == null || nowMs - st.lend.endedAtMs < REPORT_FEED_MS)) add(FeedCard(
            st.lend.kind.report, st.lend.report.take(3).joinToString(" · ") { "${it.first} ${it.second}" }, Level.INFO,
            // 맡김 리포트는 ② 화면이 아니라 차량 관리에 있다
            if (st.lend.kind == HandKind.SERVICE) "SERVICE" else Module.DRIVER.name,
        ))
        if (st.valet.phase == ValetPhase.COMPLETE) add(FeedCard("대리 주차 완료", "완료 근거: ${st.valet.confirmedBy}", Level.OK, Module.VALET.name))
        if (settings.carModel?.ev == true) tripBattery(s)?.takeIf { it.low }?.let { t ->
            add(FeedCard("도착하면 배터리 약 ${t.arrivalPct.coerceAtLeast(0)}%",
                (t.reasons.joinToString("·").ifEmpty { "남은 거리" }) + " 기준이에요. 경로 중 충전소를 미리 정하세요", Level.WARN, "CARLIFE", signal = "soc"))
        }
        if (s.socPct < 20) add(FeedCard("배터리·연료 ${s.socPct}%", "충전소·주유소를 찾아 드릴까요?", Level.WARN, "MH_LIFE", signal = "soc"))
        settings.bookings.firstOrNull()?.let { add(FeedCard("예약: ${it.kind}", "${it.date} ${it.time} · ${it.shop}", Level.INFO, "SERVICE")) }
        if (isEmpty()) add(FeedCard("오늘은 챙길 것이 없어요", "차 상태가 모두 정상 범위예요", Level.OK, "SERVICE"))
    }

    /** 맡김 이력서 요약. [kmUnknown]은 주행거리를 못 읽어 합계에서 뺀 횟수. */
    data class Resume(val total: Int, val ok: Int, val km: Double, val kmUnknown: Int, val brakes: Int, val speedUnknown: Int = 0) {
        val lines get() = listOf(
            "총 ${total}회 맡김 · 문제 없이 돌려받음 ${ok}회",
            "남이 운전한 거리 합계 %.1fkm".format(java.util.Locale.ROOT, km) + if (kmUnknown > 0) " (거리 확인 불가 ${kmUnknown}회 제외)" else "",
            "정비·빌려주기 급감속 합계 ${brakes}회" + if (speedUnknown > 0) " (확인 불가 ${speedUnknown}회 제외)" else "",
        )
    }

    const val RESUME_NOTE = "인증중고차 매각·자동차보험(UBI) 상담 때 보여 줄 설명 자료예요. 차가 읽은 기록이며 현대차·보험사의 인증 자료는 아니에요."

    fun resume(history: List<HandoverRecord>) = Resume(
        history.size, history.count { it.ok }, history.sumOf { it.km ?: 0.0 }, history.count { it.km == null },
        history.filter { it.recordsDrivingStats && it.speedKnown }.sumOf { it.brakes },
        history.count { it.recordsDrivingStats && !it.speedKnown },
    )

    /** 운행기록부와 같은 5년만 남긴다. */
    fun keepHistory(history: List<HandoverRecord>, today: LocalDate) = history.filter {
        !LocalDate.parse(VerdictEngine.date(it.endMs)).isBefore(today.minusYears(VerdictEngine.TRIP_KEEP_YEARS))
    }

    fun historyCsv(history: List<HandoverRecord>): String = buildString {
        fun row(vararg v: String) = append(v.joinToString(",") { "\"" + VerdictEngine.csvSafe(it).replace("\"", "\"\"") + "\"" }).append('\n')
        fun at(ms: Long) = "${VerdictEngine.date(ms)} ${VerdictEngine.hhmm(ms)}"
        append('﻿') // 엑셀 한글 깨짐 방지 BOM
        row("종류", "맡긴 시각", "돌려받은 시각", "남이 운전한 거리(km)", "최고 속도(km/h)", "급감속(회)", "바뀐 것", "문제 없이 돌려받음")
        history.map { it.respectingValetConsent() }.sortedBy { it.startMs }.forEach {
            row(it.kind, at(it.startMs), at(it.endMs), it.km?.let { k -> "%.1f".format(java.util.Locale.ROOT, k) } ?: "확인 불가",
                if (!it.recordsDrivingStats) "대리 기록 제외" else if (it.speedKnown) "%.0f".format(java.util.Locale.ROOT, it.maxSpeed) else "확인 불가",
                if (!it.recordsDrivingStats) "대리 기록 제외" else if (it.speedKnown) "${it.brakes}" else "확인 불가", it.changes.joinToString(" / "), if (it.ok) "예" else "아니요")
        }
        resume(history).lines.forEach { row(it) }
        row(RESUME_NOTE)
    }

    val POIS = listOf(
        Poi("충전소", "현대 E-pit (목업)", 2.4, "초고속 350kW", Charger("DC콤보", indoor = false, working = true, open = true, spot = "지상 입구 옆")),
        Poi("충전소", "공영주차장 완속 충전기 (목업)", 0.8, "완속 7kW", Charger("AC완속", indoor = false, working = true, open = true, spot = "1구역")),
        Poi("충전소", "마트 지하 급속 충전기 (목업)", 1.6, "급속 100kW", Charger("DC콤보", indoor = true, working = true, open = true, spot = "지하 2층 C-14 기둥")),
        Poi("충전소", "아파트 충전기 (목업)", 0.4, "급속 50kW · 입주민 전용", Charger("DC콤보", indoor = true, working = true, open = false, spot = "지하 1층")),
        Poi("충전소", "휴게소 급속 충전기 (목업)", 6.2, "급속 100kW · 점검 중", Charger("DC콤보", indoor = false, working = false, open = true, spot = "주유소 뒤편")),
        Poi("맛집", "근처 한식당 (목업)", 1.1, "주차 가능"),
        Poi("주차장", "공영주차장 (목업)", 0.5, "30분 무료"),
        Poi("세차장", "자동 세차장 (목업)", 1.9, "N단 유지 필요"),
        Poi("블루핸즈", "블루핸즈 (목업)", 3.2, "정비·소모품"),
    )

    val SHOP = listOf(
        ShopItem("현대샵", "순정 와이퍼 블레이드 세트 (목업)", 32_000, "와이퍼 교체 주기 도래 시 추천"),
        ShopItem("현대샵", "실내 에어컨 필터 (목업)", 18_000, "15,000km 주기"),
        ShopItem("현대샵", "트렁크 정리함 (목업)", 45_000, "⑦ 트렁크 짐 확인과 함께"),
        ShopItem("블루링크 스토어", "디지털 사양 추가 (목업)", 0, "가격·제공 사양은 차종별 상이"),
    )
}
