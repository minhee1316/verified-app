package com.verified.incar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.R
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.Capabilities
import com.verified.incar.core.CarModel
import com.verified.incar.core.ComfortPhase
import com.verified.incar.core.ComfortPreset
import com.verified.incar.core.DepartureSchedule
import com.verified.incar.core.Consent
import com.verified.incar.core.DIAG_DISCLAIMER
import com.verified.incar.core.DiagFinding
import com.verified.incar.core.DriverVerdict
import com.verified.incar.core.EscortPhase
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Level
import com.verified.incar.core.Module
import com.verified.incar.core.NHoldPhase
import com.verified.incar.core.Persona
import com.verified.incar.core.Place
import com.verified.incar.core.SchoolPhase
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.VerdictEngine

/** 홈 카드와 탭에 쓰는 모듈 한 줄 상태. */
fun statusOf(m: Module, u: UiState): Pair<String, Level?> {
    val e = u.engine
    return when (m) {
        Module.VALET -> (if (e.valet.phase == ValetPhase.IDLE) "준비됨" else e.valet.phase.label) to when (e.valet.phase) {
            ValetPhase.COMPLETE, ValetPhase.IDLE -> Level.OK
            ValetPhase.INCOMPLETE -> Level.DANGER
            else -> Level.INFO
        }
        Module.DRIVER -> (if (e.driver.verdict == DriverVerdict.NOT_CHECKED) "확인 전" else e.driver.name) to when (e.driver.verdict) {
            // 등록 운전자라도 입력한 특약에 없으면 초록으로 보이지 않게
            DriverVerdict.KNOWN -> if (u.settings.profiles.any { it.name == e.driver.name && it.insured }) Level.OK else Level.WARN
            DriverVerdict.ASK -> Level.WARN
            DriverVerdict.UNKNOWN -> Level.DANGER
            else -> null
        }
        Module.TRIP -> (if (e.trip.pending != null) "목적 선택 대기" else if (e.trip.open != null) "기록 중" else "운행 ${e.trip.logs.size}건") to Level.INFO
        Module.ESCORT -> (if (e.escort.phase == EscortPhase.OFF) "대기 중" else e.escort.phase.label) to when (e.escort.phase) {
            EscortPhase.WRONG_SIDE, EscortPhase.DEPARTED_UNVERIFIED -> Level.DANGER
            EscortPhase.ALIGHTED, EscortPhase.READY -> Level.OK
            EscortPhase.OFF -> null
            else -> Level.INFO
        }
        Module.SCHOOL -> "${e.school.phase.label} · ${e.school.streak}회 연속" to when (e.school.phase) {
            SchoolPhase.FAIL -> Level.WARN
            SchoolPhase.SUCCESS -> Level.OK
            else -> null
        }
        Module.CHAUFFEUR -> (if (e.chauffeur.active) "${e.chauffeur.score}점" else "시작 대기") to if (e.chauffeur.active) Level.INFO else null
        Module.TRUNK -> {
            val checks = exitChecks(u)
            val bad = checks.count { it.bad }
            val unknown = checks.count { it.unread }
            when {
                e.exit.items.isNotEmpty() && bad > 0 -> "확인 필요 ${bad}건" to Level.WARN
                e.trunk.loaded -> "트렁크 짐 있음" to Level.WARN
                unknown > 0 -> "확인 불가 ${unknown}건" to Level.INFO
                e.floor.confirmed != null -> "지하 ${e.floor.confirmed}층 주차" to Level.INFO
                bad > 0 -> "하차 시 점검" to null
                else -> "모두 확인됨" to Level.OK
            }
        }
        Module.DIAG -> "증상 확인" to null
        Module.MISSION -> "${e.missions.items.count { it.done }}/7 완료" to Level.INFO
        Module.COMFORT -> if (u.settings.carModel == null) "차량 등록 필요" to Level.WARN else (if (e.comfort.phase == ComfortPhase.OFF) (if (u.settings.schedule.enabled) "%02d:%02d 예약".format(u.settings.schedule.hour, u.settings.schedule.minute) else "대기") else e.comfort.phase.label) to
            if (e.comfort.phase == ComfortPhase.OFF) Level.INFO else Level.OK
    }
}

/**
 * 제출용 실차에서는 구독 후 첫 값까지 받아야 읽은 것으로 본다. GPS는 별도 위치 공급자에서 온다.
 * 시연 모드는 사용자가 넣은 모의 신호도 읽을 수 있다.
 */
fun UiState.unread(name: String): Boolean {
    if (simAllowed) return false
    val sourceAvailable = if (name == "gps") locationOn else carConnected && name in liveSignals
    return !sourceAvailable || name !in signals.seenAt
}

fun UiState.read(name: String, text: String) = if (unread(name)) "확인 불가" else text

/** ⑦ 하차 점검 한 항목. [bad]는 읽은 신호로 켜짐·열림일 때만. */
class ExitItem(val label: String, on: Boolean, val word: String, val unread: Boolean) {
    val bad = on && !unread
}

/** CarBridge 구독 이름 → 화면 이름. '이 차에서 읽는 신호' 목록에 쓴다. */
val SIGNAL_LABELS = linkedMapOf(
    "gear" to "기어", "speed" to "차속", "ignition" to "시동", "odometer" to "주행거리", "soc" to "배터리·연료",
    "doors" to "문", "lock" to "문 잠금", "window" to "창문", "row2Occupancy" to "착좌", "belt" to "벨트", "posture" to "시트 자세",
    "row2Recline" to "2열 등받이", "steering" to "조향각", "ac" to "에어컨", "hvacTemp" to "설정 온도", "defroster" to "성에 제거",
    "recirc" to "내기 순환", "seatHeat" to "열선·통풍", "steeringHeat" to "핸들 열선", "wiper" to "와이퍼", "turn" to "방향지시등",
    "hazard" to "비상등", "lowBeam" to "전조등", "interiorLight" to "실내등", "cruise" to "크루즈", "outsideTemp" to "외기 온도",
    "tirePressure" to "타이어 공기압",
    "warnings" to "경고등", "oil" to "엔진오일", "washer" to "워셔액", "tire" to "타이어 저압", "navi" to "현대 내비", "gps" to "GPS",
)

/** 인수인계서 한 줄의 색: 새로 켜진 경고·열림은 주의, 달라진 것 없음은 정상, 거리·배터리 같은 사실은 정보. */
private fun diffLevel(d: String) = when {
    d == VerdictEngine.NO_CHANGE -> Level.OK
    "새로" in d || d.endsWith("열림") || d.endsWith("잠금 풀림") -> Level.WARN
    else -> Level.INFO
}

/** 차가 쓰는 인수인계서: 인수 때와 돌려받을 때 차 상태 비교. 읽지 못한 칸은 추정하지 않고 '확인 불가'로 따로 쓴다. */
@Composable
fun HandoverDiff(a: com.verified.incar.core.CarCheck?, b: com.verified.incar.core.CarCheck?) {
    if (a == null || b == null) return
    val lines = VerdictEngine.diffChecks(a, b)
    Column(Modifier.fillMaxWidth().testTag("handover_diff"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text((if (lines.any { !it.startsWith("확인 불가") }) "차가 쓴 인수인계서" else "인수인계서") + " · 인수 ${VerdictEngine.hhmm(a.atMs)} → 돌려받음 ${VerdictEngine.hhmm(b.atMs)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Wrap { lines.filterNot { it.startsWith("확인 불가") }.forEach { Pill(it, diffLevel(it)) } }
        lines.firstOrNull { it.startsWith("확인 불가") }?.let { Text("$it · 이 차에서 읽지 못해 비교하지 않았어요", fontSize = 16.sp, color = C.warn) }
    }
}

/**
 * 현대 기능 체크리스트(발레 모드·빌트인 캠·디지털 키 2). 이 기능들은 다른 앱이 읽을 수 없어 체크는 차주 확인 기록이다.
 * [valet]: ① 대리면 true, 빌려주기·맡김이면 false.
 */
@Composable
fun PrepChecklist(vm: VerifiedRuntime, u: UiState, valet: Boolean, phase: com.verified.incar.core.PrepPhase) {
    val prep = if (valet) u.engine.valet.prep else u.engine.lend.prep
    val items = com.verified.incar.core.PrepItem.entries.filter { it.phase == phase }
    Column(Modifier.fillMaxWidth().testTag("prep_${phase.name}")) {
        Text("현대 기능 체크 · ${phase.label}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Text("발레 모드·빌트인 캠·디지털 키 2는 확인됨이 읽을 수 없어요. 차주님이 직접 확인하고 눌러 주세요(차주 확인으로 기록돼요).", fontSize = 16.sp, lineHeight = 23.sp, color = C.sub)
        items.forEachIndexed { i, item ->
            val on = item in prep
            // 안 한 항목은 '!'(판정 주의)가 아니라 빈 원으로: 아직 차주가 확인하지 않았을 뿐이다
            Row(
                Modifier.fillMaxWidth().toggleable(on, role = androidx.compose.ui.semantics.Role.Checkbox) { vm.togglePrep(item, valet) }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) CheckMark(true) else Box(Modifier.size(32.dp).clip(CircleShape).border(2.dp, C.line, CircleShape))
                // 앞 공백 대신 여백: 폰에서 두 줄로 넘어가도 둘째 줄이 첫 줄과 맞춰진다
                Text(vm.prepLabel(item, valet), Modifier.weight(1f).padding(start = 14.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            }
            if (i < items.lastIndex) HorizontalDivider(color = C.line)
        }
    }
}

/** 맡김 이력서로 가는 줄(리포트 아래). */
@Composable
fun HistoryLink(vm: VerifiedRuntime, u: UiState) =
    TextRow("맡김 이력서 보기", "${u.engine.history.size}건", tag = "to_HISTORY") { vm.navigate.value = "HISTORY" }

/** 확인 근거 줄: '시각  무엇 · 출처'. 길면 첫 줄(인수·넘김)과 최근 줄만 보여 준다. */
@Composable
fun ProofLines(proof: List<com.verified.incar.core.Proof>, max: Int = 7) {
    val shown = if (proof.size <= max) proof else proof.take(1) + proof.takeLast(max - 1)
    shown.forEachIndexed { i, p ->
        if (i == 1 && proof.size > max) Text("… ${proof.size - max}줄 더 있음", fontSize = 15.sp, color = C.sub)
        Text("${VerdictEngine.hhmmss(p.atMs)}  ${p.what} · ${p.src}", fontSize = 16.sp, color = if (p.src == "확인 불가") C.warn else C.sub)
    }
}

fun exitChecks(u: UiState) = with(u.signals) {
    listOf(
        ExitItem("창문", windowOpen, "열림", u.unread("window")),
        ExitItem("전조등", lowBeamOn, "켜짐", u.unread("lowBeam")),
        ExitItem("실내등", interiorLightOn, "켜짐", u.unread("interiorLight")),
        ExitItem("트렁크", trunkOpen, "열림", u.unread("doors")),
    )
}

/** 화면 공통 틀: 좌우 2단, 넓은 바깥 여백. */
@Composable
private fun TwoPane(left: @Composable () -> Unit, right: @Composable () -> Unit, leftWeight: Float = 1f, top: Boolean = false, rightFirst: Boolean = false, tabs: Pair<String, String>? = null) {
    TwoUp(36.dp, 28.dp, 40.dp, 18.dp, leftWeight, top, rightFirst = rightFirst, tabs = tabs, left = left, right = right)
}

@Composable
private fun Dot(level: Level?) = Box(Modifier.size(12.dp).clip(CircleShape).background(levelColor(level)))

// ------------------------------------------------------------------ 첫 실행
@Composable
fun Onboarding(vm: VerifiedRuntime, u: UiState) {
    var picked by rememberSaveable { mutableStateOf(listOf<Persona>()) }
    var showCar by rememberSaveable { mutableStateOf(false) }
    var showSignals by rememberSaveable { mutableStateOf(false) }
    TwoPane(
        top = true,
        left = {
            Text("어떤 상황에서 차를 쓰세요?", Modifier.semantics { heading() }, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text("하나 이상 고르면 내 홈에 관련 기능을 모아 드려요. 선택은 설정에서 바꿀 수 있어요.", color = C.sub, fontSize = 18.sp, lineHeight = 26.sp)
            Card(Modifier.fillMaxWidth(), padding = 0.dp) {
                Column {
                    Persona.entries.forEachIndexed { i, persona ->
                        val chosen = persona in picked
                        Row(
                            Modifier.fillMaxWidth().background(if (chosen) C.ice else C.card)
                                .toggleable(chosen, role = Role.Checkbox) { checked -> picked = if (checked) picked + persona else picked - persona }
                                .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(persona.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                                Text(persona.modules.sortedBy { it.ordinal }.joinToString(" · ") { shortName(it) }, fontSize = 16.sp, color = C.sub)
                            }
                            Checkbox(chosen, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = C.navy, uncheckedColor = C.sub, checkmarkColor = Color.White))
                        }
                        if (i < Persona.entries.lastIndex) HorizontalDivider(color = C.line)
                    }
                }
            }
        },
        right = {
            Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text("차량 설정", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = C.ink)
                Text("차종은 나중에 등록해도 돼요.", fontSize = 17.sp, color = C.sub)
                BigButton(if (showCar) "차종 선택 닫기" else u.settings.carModel?.let { "차종 변경 · ${it.label}" } ?: "차종 선택", Modifier.fillMaxWidth(), BtnStyle.OUTLINE, fontSize = 17.sp) { showCar = !showCar }
                if (showCar) CarModelPicker(vm, u)
                TextRow("차량 신호 지원 정보", if (showSignals) "접기" else "보기") { showSignals = !showSignals }
                if (showSignals) {
                    if (u.simAllowed) Text("시연 환경에서 설정한 지원 항목이에요.", color = C.sub, fontSize = 16.sp)
                    if (!u.simAllowed && !u.carConnected) Text("차량 연결을 기다리고 있어요.", color = C.sub, fontSize = 16.sp)
                    else CapsSummary(u.caps)
                }
            }
            BigButton("시작하기", Modifier.fillMaxWidth(), enabled = picked.isNotEmpty()) { vm.settings { it.copy(personas = picked.toSet()) } }
        },
    )
}

@Composable
fun CapsSummary(c: Capabilities) {
    listOf(
        "2열 탑승 감지" to if (c.row2Occupancy) "좌석 센서" else "벨트 또는 직접 선택",
        "2열 등받이 각도" to if (c.row2Recline) "센서 지원" else "쇼퍼 모드 직접 선택",
        "운전자 구분" to if (c.seatPosture) "시트 자세" else "오늘 운전자 선택",
        "위치 판정" to if (c.locationWithoutRoute) "GPS" else "마지막 수신 위치",
        "하차 안내" to if (c.ambientControl) "조명 지원" else "화면·음성",
        "시트 복원" to if (c.seatControl) "제어 지원" else "메모리 버튼 안내",
    ).forEach { (label, value) -> KeyValue(label, value) }
}

/** 주행 중: 큰 속도 + 3줄 요약 유리 카드. 글자만 보이고 누를 것은 없다. */
@Composable
fun Restricted(u: UiState, lines: List<String>) {
    val compact = isCompact()
    Column(Modifier.fillMaxSize().background(Color(0xFF101B2A)).padding(horizontal = if (compact) 16.dp else 36.dp, vertical = if (compact) 14.dp else 20.dp)) {
        Text("주행 중 · 조작 잠김", color = Color(0xFFB6D8FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Split(if (compact) 24.dp else 40.dp, w1 = 0.8f, w2 = 1.2f, center = true, first = { cell ->
            Column(cell) {
                Text("현재 속도", color = Color(0xFFBEC9D8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.Bottom) {
                    if (u.unread("speed")) Text("확인 불가", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    else {
                        Text("%.0f".format(u.signals.speedKmh), color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Bold, lineHeight = 94.sp, softWrap = false)
                        Text(" km/h", color = Color(0xFFBEC9D8), fontSize = 24.sp, modifier = Modifier.padding(bottom = 12.dp), softWrap = false)
                    }
                }
            }
        }, second = { cell ->
            Column(
                cell.clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B2A3B)).padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text("주행 요약", color = Color(0xFFBEC9D8), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                lines.forEachIndexed { i, line ->
                    if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    val no = line.firstOrNull()?.takeIf { it in '①'..'⑩' }
                    val body = if (no != null) line.drop(1).trim() else line
                    val (name, value) = body.split(": ", limit = 2).let { it[0] to it.getOrNull(1) }
                    Text(listOfNotNull(name, value).joinToString(" · "), Modifier.padding(vertical = 8.dp), color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        })
        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
        Text("정차 후 P단에서 조작할 수 있어요.", color = Color(0xFFBEC9D8), fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ------------------------------------------------------------------ ① (현대2)
@Composable
fun ValetScreen(vm: VerifiedRuntime, u: UiState) {
    // 폰 세로는 한 단(머리 카드 → 단계 → 할 일 → 근거)으로 따로 그린다. 차 화면은 아래 두 단 그대로
    if (u.engine.lend.active && !u.engine.valet.active) {
        val route = if (u.engine.lend.kind == com.verified.incar.core.HandKind.LEND) "HAND_LEND" else "HAND_SERVICE"
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("진행 중인 맡김이 있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            BigButton("맡긴 차 보기", Modifier.fillMaxWidth()) { vm.navigate.value = route }
        }
        return
    }
    if (isCompact()) { PhoneValet(vm, u); return }
    val v = u.engine.valet
    val (label, level) = statusOf(Module.VALET, u)
    TwoPane(
        tabs = "그림·근거" to "판정",
        // 폰 세로에서는 판정 카드를 그림보다 먼저
        rightFirst = true,
        left = {
            Card(Modifier.fillMaxWidth(), padding = 20.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    // 초록 원은 완료일 때만 색으로 보여 준다 (미완료를 완료로 오해하지 않도록)
                    Image(
                        // 다크 모드에서는 흰 바탕이 네모로 튀므로 원 모양 그림을 원으로 잘라 밝은 원판처럼 보이게 한다
                        painterResource(R.drawable.valet_top), "주차 탑뷰",
                        if (C.dark) Modifier.padding(top = 20.dp).height(340.dp).aspectRatio(520f / 506f).clip(CircleShape) else Modifier.fillMaxWidth().height(360.dp).padding(top = 20.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = if (v.phase == ValetPhase.COMPLETE) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    )
                    Pill(if (v.phase == ValetPhase.COMPLETE) "완료" else label, level, big = true)
                }
            }
            // 차주가 없는 순간의 근거: 어떤 신호를 언제 읽어 판정했는지(차주 폰 알림에도 같은 줄이 간다). 왼쪽에 두어 두 단 높이를 맞춘다
            if (v.proof.isNotEmpty() && v.phase != ValetPhase.CONSENT) Card(Modifier.fillMaxWidth().testTag("valet_proof"), padding = 22.dp) {
                CardHeader(null, "확인 근거", sub = "시각 · 판정 · 읽은 곳")
                ProofLines(v.proof)
            }
        },
        right = { ValetBody(vm, u, phone = false) },
    )
}

/**
 * ① 판정·조작 칸. 차 화면 오른쪽 단과 폰 화면이 같이 쓴다.
 * [phone]이면 내비 줄은 머리 카드로 올린다.
 */
@Composable
private fun ValetBody(vm: VerifiedRuntime, u: UiState, phone: Boolean) {
    val v = u.engine.valet
    val s = u.signals
    val stoppedSec = u.engine.stoppedSinceMs?.let { (u.nowMs - it) / 1000 } ?: 0
    val estimated = s.gpsLost || !u.caps.locationWithoutRoute
    val place = if (estimated) u.engine.lastPlace else s.place
    val (label, level) = statusOf(Module.VALET, u)
    when (v.phase) {
        ValetPhase.IDLE, ValetPhase.CLOSED_BY_OWNER, ValetPhase.CONSENT -> {
            if (v.phase == ValetPhase.CLOSED_BY_OWNER) Card(Modifier.fillMaxWidth()) {
                CardHeader(null, "차주가 직접 종료한 기록")
                v.checkOut?.let { Text("${VerdictEngine.hhmm(it.atMs)} · 주차 완료 자동 판정 없음", fontSize = 17.sp, color = C.sub) }
                HandoverDiff(v.checkIn, v.checkOut)
                HistoryLink(vm, u)
            }
            Card {
                CardHeader(null, "기사님께 드리는 안내")
                Text("동의·인수·도착 예정·실제 도착 시각, 주행거리·배터리와 주차 전후 문·잠금·창문·트렁크·바퀴·경고·소모품 상태를 대리 이력에 기록해요.", fontSize = 19.sp, lineHeight = 28.sp, color = C.ink)
                Text("집 도착과 경로 이탈을 판단할 때 위치·기어·정차·시동·하차 신호를 사용하고 판단 근거를 남겨요. 상세 기록은 이 기기에 보관하고, 연결한 차주 휴대폰에는 시작·완료·확인 필요 같은 짧은 상태 알림을 보내요.", fontSize = 17.sp, lineHeight = 26.sp, color = C.sub)
                if ((u.carConnected || !u.simAllowed) && Place.HOME !in u.settings.pins) {
                    Note("집 도착을 확인하려면 차주가 집을 먼저 등록해 주세요.", Level.WARN)
                    BigButton("장소 등록 열기", Modifier.fillMaxWidth()) { vm.navigate.value = "SETTINGS" }
                } else BigButton("동의하고 출발", Modifier.fillMaxWidth().testTag("valet_consent")) {
                    vm.act { sig, now ->
                        if (state.valet.phase == ValetPhase.CONSENT) confirmValetConsent(sig, now)
                        else startValet().ifEmpty { confirmValetConsent(sig, now) }
                    }
                }
            }
            if (v.phase == ValetPhase.CONSENT) Card(Modifier.fillMaxWidth(), padding = 24.dp) { PrepChecklist(vm, u, true, com.verified.incar.core.PrepPhase.BEFORE) }
        }
        else -> {
            Card(Modifier.fillMaxWidth(), padding = 24.dp) {
                // 폰에서는 상태 알약이 머리 카드와 겹치므로 뺀다
                CardHeader(null, "완료 판정 4가지", sub = "목적지 · ${Place.HOME.label}", trailing = if (phone) null else { { Pill(label, level) } })
                // 완료 뒤에는 지금 신호가 아니라 판정 결과로 보여 준다(차주가 앉아도 '하차'가 !로 바뀌지 않게)
                val done = v.phase == ValetPhase.COMPLETE
                val byOff = v.confirmedBy?.startsWith("시동 OFF") == true
                val gearKnown = done || !u.unread("gear")
                val speedKnown = done || !u.unread("speed")
                val placeKnown = done || !u.unread("gps")
                val leftCar = (!u.unread("row2Occupancy") && !s.driverSeated) || (!u.unread("doors") && s.driverDoorOpen)
                val exitKnown = done || leftCar || (!u.unread("row2Occupancy") && !u.unread("doors"))
                CheckLine((done || s.gear == Gear.P).takeIf { gearKnown }, "P단 체결", if (gearKnown) null else "확인 불가")
                CheckLine((done || stoppedSec >= 10).takeIf { speedKnown }, "차속 0 (10초 유지)", if (!speedKnown) "확인 불가" else if (byOff) "시동 OFF로 확정" else if (done) null else if (s.speedKmh > 0) "주행 중" else "${stoppedSec}초")
                CheckLine((done || place == Place.HOME).takeIf { placeKnown }, "집 반경 ${u.settings.pinRadiusM}m 안", if (!placeKnown) "확인 불가" else if (if (done) v.placeEstimated else estimated) "지하 진입 전 위치" else null)
                CheckLine((done || leftCar).takeIf { exitKnown }, "운전석 하차 완료", if (!exitKnown) "확인 불가" else if (byOff) "시동 OFF로 확정" else null, divider = false)
                val h = v.checkIn
                val r = v.checkOut
                if (r != null) Wrap {
                    Pill("배터리 " + (if (h?.socPct != null && r.socPct != null) "${h.socPct}→${r.socPct}%" else "확인 불가"), Level.INFO)
                    Pill("이동 " + (r.distanceFrom(h)?.let { "%.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가"), Level.INFO)
                    val window = when (r.windowOpen) { true -> "열림"; false -> "닫힘"; null -> "확인 불가" }
                    Pill("창문 $window", when (r.windowOpen) { true -> Level.WARN; false -> Level.OK; null -> Level.INFO })
                    val turned = r.steeringDeg?.let { kotlin.math.abs(it) >= VerdictEngine.WHEEL_TURNED_DEG }
                    val wheels = when (turned) { true -> "%+.0f°".format(r.steeringDeg); false -> "정렬"; null -> "확인 불가" }
                    Pill("바퀴 $wheels", when (turned) { true -> Level.WARN; false -> Level.OK; null -> Level.INFO })
                }
            }
            when (v.phase) {
                ValetPhase.COMPLETE -> {
                    HoldButton("대리 모드 끝내기", Modifier.testTag("valet_end")) {
                        vm.ownerOnly { vm.act { _, _ -> if (state.valet.phase == ValetPhase.COMPLETE) endValet(); emptyList() } }
                    }
                    if (u.canControl) BigButton("차주 시트·공조 설정 복원  ↻", Modifier.fillMaxWidth()) { vm.act { sig, _ -> requestRestore(sig) } }
                    else Note("주행 기능 모드: 메모리 1번 버튼으로 차주 시트를 맞춰 주세요.")
                    v.restoreMsg?.let { Note(it) }
                }
                ValetPhase.INCOMPLETE -> {
                    Note("차주님께: 집 앞이라도 직접 운전하지 마세요. 대리 재호출이나 긴급출동을 이용하세요.", Level.DANGER)
                    BigButton("차주 승인: 여기서 종료", Modifier.fillMaxWidth(), BtnStyle.SOFT_DANGER) { vm.ownerOnly { vm.act { _, _ -> ownerCloseValet() } } }
                }
                else -> v.prelimAtMs?.let { Note("P단 진입 즉시 1차 완료 저장 (${VerdictEngine.hhmm(it)}) · 시동을 바로 꺼도 완료로 확정돼요", Level.OK) }
            }
            val returned = v.phase == ValetPhase.COMPLETE || v.phase == ValetPhase.INCOMPLETE
            if (!returned && listOf("gear", "speed", "gps", "ignition", "doors", "row2Occupancy").any { u.unread(it) }) {
                Note("일부 차량 신호를 읽지 못했어요. 차를 실제로 돌려받았다면 차주 확인으로 기록을 마칠 수 있어요.")
                HoldButton("차주가 직접 반납 확인", Modifier.testTag("valet_manual_return"), BtnStyle.OUTLINE) {
                    vm.ownerOnly { vm.act { sig, now -> manualValetReturn(sig, now) } }
                }
            }
            if (!returned && !phone) v.naviEtaMs?.let { eta ->
                val left = listOfNotNull(s.naviRemainKm?.let { "%.1fkm".format(it) }, s.naviRemainMin?.let { "${it}분" }).joinToString("·")
                Note("집으로 안내 중 · ${VerdictEngine.hhmm(eta)} 도착 예정" + (if (left.isNotEmpty()) " · 남은 $left" else "") + " (현대 내비 기준)")
            }
            if (!returned && !phone && v.naviOffWarned) Note("집 가는 경로를 벗어난 기록이 있어요", Level.WARN)
            Card(Modifier.fillMaxWidth(), padding = 24.dp) {
                if (returned) {
                    HandoverDiff(v.checkIn, v.checkOut)
                    PrepChecklist(vm, u, true, com.verified.incar.core.PrepPhase.RETURN)
                    HistoryLink(vm, u)
                } else PrepChecklist(vm, u, true, com.verified.incar.core.PrepPhase.BEFORE)
            }
        }
    }
}

/**
 * ① 폰 세로: 머리 카드(상태 한 단어·언제부터·도착 예정) → 4단계 진행 → 단계별 할 일(차 화면과 같은 문구·버튼) → 최근 근거 3줄.
 * 큰 탑뷰 그림은 화면 길이만 두 배로 늘려 폰에서는 뺀다.
 */
@Composable
private fun PhoneValet(vm: VerifiedRuntime, u: UiState) {
    val v = u.engine.valet
    val s = u.signals
    val (label, level) = statusOf(Module.VALET, u)
    val returned = v.phase == ValetPhase.COMPLETE || v.phase == ValetPhase.INCOMPLETE
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PhoneHero("① 대리 안심 주차", label, level, tag = "valet_hero") {
            v.handover?.timeMs?.let { PhoneHeroLine("${VerdictEngine.hhmm(it)}부터" + if (returned) "" else if (u.carConnected || u.simAllowed) " · 차가 기록 중이에요" else " · 차량 미연결 · 시각만 기록 중이에요") }
            if (!returned) v.naviEtaMs?.let { eta ->
                PhoneHeroLine("${VerdictEngine.hhmm(eta)} 도착 예정" + (s.naviRemainKm?.let { " · %.1fkm 남음".format(it) } ?: "") + " (현대 내비 기준)")
            }
            if (!returned && v.naviOffWarned) PhoneHeroWarn("집 가는 경로를 벗어난 기록이 있어요")
            if (u.simAllowed && !u.carConnected) PhoneHeroLine("차량 연결 전 · 예시 값 기준")
        }
        if (v.active) Card(Modifier.fillMaxWidth().testTag("valet_steps"), padding = 16.dp) {
            val step = when (v.phase) { ValetPhase.CONSENT -> 0; ValetPhase.DRIVING -> 1; ValetPhase.PRELIM -> 2; else -> 3 }
            PhoneSteps(listOf("기사 동의", "운행", "P단 1차", "완료"), step, warnLast = v.phase == ValetPhase.INCOMPLETE)
        }
        ValetBody(vm, u, phone = true)
        if (v.phase != ValetPhase.CONSENT) PhoneProof(v.proof, "valet_proof")
    }
}

// ------------------------------------------------------------------ ② (현대3)
@Composable
fun DriverScreen(vm: VerifiedRuntime, u: UiState) {
    val d = u.engine.driver
    val level = statusOf(Module.DRIVER, u).second
    val pill = when (d.verdict) {
        DriverVerdict.KNOWN -> "시트 자세 판정: ${d.name} 일치 " + if (level == Level.OK) "(${u.settings.insuranceScope} 특약 내)" else "(입력하신 특약에 없음 · 보장 범위 확인 필요)"
        DriverVerdict.ASK -> "비슷한 자세예요 · 오늘 운전자를 골라 주세요"
        DriverVerdict.UNKNOWN -> "등록되지 않은 자세 (거리 %.2f) · 보장 범위 확인 필요".format(d.distance)
        DriverVerdict.NOT_CHECKED -> if (u.engine.valet.active) "대리운전 모드 진행 중" else "운전석에 앉으면 확인해요"
    }
    TwoPane(
        tabs = "판정·빌려주기" to "운전자 모드",
        left = {
            HandoverCard(vm, u, com.verified.incar.core.HandKind.LEND)
            Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                Image(painterResource(if (C.dark) R.drawable.seat_posture_cut else R.drawable.seat_posture), "시트 자세", Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Fit)
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.chip).border(2.dp, levelColor(level).copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckMark(level == Level.OK, 28.dp)
                Text("  $pill", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.ink)
            }
        },
        right = {
            Text("운전자 모드 설정", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.ink)
            val known = d.verdict == DriverVerdict.KNOWN
            BigButton("등록 운전자  ✓", Modifier.fillMaxWidth(), if (known) BtnStyle.FILLED else BtnStyle.OUTLINE) {
                // 차를 넘긴 동안에는 다른 사람이 차주 자세 학습을 바꾸지 못하게 차주 PIN을 거친다
                val learn = { vm.act { sig, _ -> chooseToday("KNOWN", sig) } }
                if (u.engine.valet.active || u.engine.lend.active) vm.ownerOnly(learn) else learn()
            }
            BigButton("대리운전 모드", Modifier.fillMaxWidth(), if (u.engine.valet.active) BtnStyle.FILLED else BtnStyle.OUTLINE) {
                // 동의 대기 중에 다시 누르면 차주 확인 뒤 끈다(잘못 눌렀거나 부른 대리를 취소할 때). 켤 때는 기사님이 동의 화면을 보게 ①로 간다
                if (u.engine.valet.phase == ValetPhase.CONSENT) vm.ownerOnly { vm.act { _, _ -> endValet(); emptyList() }; vm.toast("대리운전 모드를 취소했어요") }
                else { vm.act { sig, _ -> chooseToday("VALET", sig) }; vm.navigate.value = Module.VALET.name }
            }
            BigButton("새 운전자 (보험 확인 안내)", Modifier.fillMaxWidth(), if (d.showNewDriverGuide) BtnStyle.FILLED else BtnStyle.OUTLINE) { vm.act { sig, _ -> chooseToday("NEW", sig) } }
            if (d.showNewDriverGuide) Note("가입하신 보험사 앱이나 고객센터에서 '임시 운전자 특약'을 확인하세요. 확인됨은 보험을 판매하거나 중개하지 않습니다.", Level.WARN)
            d.notes.forEach { Text("· $it", fontSize = 18.sp, color = if ("만료" in it || "아니에요" in it) C.warn else C.sub) }
        },
        top = true,
    )
}

// ------------------------------------------------------------------ 맡김 이력서
@Composable
fun HistoryScreen(vm: VerifiedRuntime, u: UiState) {
    val h = u.engine.history
    val r = com.verified.incar.core.MyHyundai.resume(h)
    TwoPane(
        tabs = "요약" to "기록",
        top = true,
        left = {
            ScreenTitle("", "맡김 이력서", "대리·정비 맡김·빌려주기 때 차가 읽은 기록을 모았어요.")
            Card(Modifier.fillMaxWidth().testTag("history_summary"), padding = 24.dp) {
                CardHeader(null, "요약")
                r.lines.forEach { Text("· $it", fontSize = 19.sp, lineHeight = 27.sp, color = C.ink) }
                Note(com.verified.incar.core.MyHyundai.RESUME_NOTE)
                BigButton("CSV로 내보내기   ⇪", Modifier.fillMaxWidth().testTag("history_export"), enabled = h.isNotEmpty(), fontSize = 19.sp) { vm.exportHistory() }
            }
            FineCheckCard(h)
        },
        right = {
            if (h.isEmpty()) Card(Modifier.fillMaxWidth(), padding = 24.dp) {
                CardHeader(null, "아직 기록이 없어요")
                Text("대리운전, 차 빌려주기, 정비 맡김을 마치면 이곳에 기록이 생겨요.", fontSize = 17.sp, lineHeight = 25.sp, color = C.sub)
            }
            // 최근 것부터
            h.asReversed().forEach { rec ->
                Card(Modifier.fillMaxWidth().testTag("history_record"), padding = 22.dp) {
                    CardHeader(
                        null, rec.kind,
                        sub = "${VerdictEngine.date(rec.startMs)} ${VerdictEngine.hhmm(rec.startMs)} ~ " +
                            (if (VerdictEngine.date(rec.endMs) != VerdictEngine.date(rec.startMs)) VerdictEngine.date(rec.endMs) + " " else "") + VerdictEngine.hhmm(rec.endMs),
                    ) { Pill(if (rec.ok) "문제 없이 돌려받음" else "확인 필요", if (rec.ok) Level.OK else Level.WARN) }
                    Wrap {
                        Pill("남이 운전 " + (rec.km?.let { "%.1fkm".format(it) } ?: "거리 확인 불가"), Level.INFO)
                        if (rec.recordsDrivingStats) {
                            Pill(if (rec.speedKnown) "최고 %.0fkm/h".format(rec.maxSpeed) else "최고 속도 확인 불가", null)
                            Pill(if (rec.speedKnown) "급감속 ${rec.brakes}회" else "급감속 확인 불가", if (rec.speedKnown && rec.brakes > 0) Level.WARN else null)
                        } else Pill("인수·반납 상태 기록", Level.INFO)
                    }
                    rec.changes.forEach { Text("· $it", fontSize = 16.sp, color = if (it.startsWith("확인 불가")) C.warn else C.sub) }
                }
            }
        },
    )
}

// ------------------------------------------------------------------ ③ (현대4)
@Composable
fun TripScreen(vm: VerifiedRuntime, u: UiState) {
    val t = u.engine.trip
    val shown = t.pending ?: t.logs.firstOrNull()
    // 고를 운행이 있으면 같은 도착지 추천만 칠한다(다른 곳의 지난 목적은 저장된 것처럼 보이지 않게)
    val selected = if (t.pending != null) t.suggestion else t.logs.firstOrNull()?.purpose
    TwoPane(
        left = {
            ScreenTitle("3", "업무 운행기록부")
            Column {
                Text("실시간 주행 기록 상태", fontSize = 18.sp, color = C.sub)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (u.settings.businessMode) "업무 운행 ON" else "사적 운행", Modifier.weight(1f), fontSize = 46.sp, fontWeight = FontWeight.Black, color = C.title)
                    NavySwitch(u.settings.businessMode, "업무 운행") { on -> vm.settings { it.copy(businessMode = on) } }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("trip summary", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Text(if (t.pending != null) "방금 끝난 운행" else "최근 운행 내역", fontSize = 17.sp, color = C.ink)
                    }
                    Text("📍", fontSize = 34.sp)
                }
                if (shown != null) {
                    Text("출발: ${shown.from}  →  도착: ${shown.to}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.title)
                    Text("주행거리 %.1fkm  •  운전자: %s".format(shown.distKm, shown.driver), fontSize = 18.sp, color = C.sub)
                } else if (t.open != null) {
                    Text("${t.open.from.label}에서 출발 · 기록 중", fontSize = 26.sp, fontWeight = FontWeight.Black, color = C.title)
                } else Text("출발했다가 P단으로 10초 이상 서면\n한 건이 기록돼요. 150m쯤보다 짧은 이동은 남기지 않아요.", fontSize = 20.sp, color = C.sub)
            }
        },
        right = {
            Text("운행 목적 선택", fontSize = 18.sp, color = C.sub)
            Wrap(10.dp) {
                VerdictEngine.PURPOSES.forEach { purpose ->
                    Chip(purpose, purpose == selected, big = true) { if (t.pending != null) vm.act { _, _ -> selectPurpose(purpose) } }
                }
            }
            if (t.pending != null) Text(
                t.suggestion?.takeIf { it in VerdictEngine.PURPOSES }?.let { "추천: '$it'(같은 도착지의 지난 목적). 눌러야 저장돼요. 다음 운행이 끝날 때까지 고르지 않으면 '미분류'로 남고, 업무 거리에는 넣지 않아요." }
                    ?: "다음 운행이 끝날 때까지 고르지 않으면 '미분류'로 남고, 업무 거리에는 넣지 않아요.",
                fontSize = 18.sp, color = C.sub,
            )
            BigButton("국세청 서식 기준 CSV 내보내기   ⇪", Modifier.fillMaxWidth(), enabled = t.logs.isNotEmpty()) { vm.exportTrips() }
            t.logs.take(3).forEach { KeyValue("${it.date}  ${it.from} → ${it.to}", "%.1fkm · %s".format(it.distKm, it.purpose.ifEmpty { "-" })) }
        },
    )
}

// ------------------------------------------------------------------ ④ (현대5)
@Composable
fun EscortScreen(vm: VerifiedRuntime, u: UiState) {
    val e = u.engine.escort
    val s = u.signals
    val seated = if (u.caps.row2Occupancy) s.row2Seated else s.row2Belt
    val doorsClosed = !s.row2RightDoorOpen && !s.row2LeftDoorOpen
    val lit = u.engine.ambient?.right != null
    val compact = isCompact()
    Box(Modifier.fillMaxWidth().background(C.night).padding(horizontal = hPad(36.dp), vertical = 22.dp)) { Split(if (compact) 18.dp else 32.dp, center = true, first = { cell ->
        Column(cell, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("뒷좌석 상태", Modifier.semantics { heading() }, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("뒷문 · " + u.read("doors", if (doorsClosed) "닫힘" else "열림"), color = Color.White, fontSize = 19.sp)
                Text("탑승 · " + u.read(if (u.caps.row2Occupancy) "row2Occupancy" else "belt", if (seated) "감지됨" else "감지되지 않음"), color = Color.White, fontSize = 19.sp)
                Text("안전벨트 · " + u.read("belt", if (s.row2Belt) "착용" else "미착용"), color = Color.White, fontSize = 19.sp)
            }
            if (e.phase != EscortPhase.OFF) Text(e.phase.label, color = if (e.phase == EscortPhase.WRONG_SIDE) Color(0xFFFF8A9B) else C.blue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            u.engine.ambient?.let { Text(it.note, color = if (lit) Color(0xFF7EE2B8) else Color(0xFFFFC46B), fontSize = 17.sp) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.09f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val last = e.timeline.lastOrNull()
                // 타임라인은 동행 기록이다. 보호자에게 실제로 가는 알림은 출발·하차 때뿐
                Text(last?.let { "동행 기록 · " + it.text } ?: "출발·하차 시 보호자에게 알리는 기능이에요.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (last != null) Text(e.timeline.takeLast(4).joinToString("  ›  ") { VerdictEngine.hhmm(it.timeMs) + " " + it.text }, color = Color(0xFFA9B4C8), fontSize = 17.sp)
                if (Consent.GUARDIAN !in u.settings.consents) Text("보호자 동의가 꺼져 있어 알림을 보내지 않아요.", color = Color(0xFFFFC46B), fontSize = 17.sp)
            }
            if (e.phase == EscortPhase.OFF) BigButton("부모님 동행 시작", Modifier.fillMaxWidth(), BtnStyle.CYAN) { vm.act { _, now -> startEscort(now); emptyList() } }
            else BigButton(if (e.phase == EscortPhase.ALIGHTED) "확인" else "동행 끝내기", Modifier.fillMaxWidth(), BtnStyle.CYAN) { vm.act { _, _ -> stopEscort(); emptyList() } }
        }
    }, second = { cell ->
        Box(cell.height(if (compact) 240.dp else 360.dp), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.escort_ambient), "실내 조명", Modifier.fillMaxSize().alpha(if (lit) 1f else 0.45f), contentScale = ContentScale.Fit)
        }
    }) }
}

// ------------------------------------------------------------------ ⑤ (현대6)
@Composable
fun SchoolScreen(vm: VerifiedRuntime, u: UiState) {
    val z = u.engine.school
    TwoPane(
        left = {
            Text("학교에서 아이가 내린 뒤", Modifier.semantics { heading() }, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text("등록한 학교에서 뒷좌석 하차를 감지하면 재출발 속도를 확인해요.", color = C.sub, fontSize = 18.sp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (u.unread("speed")) Text("속도 확인 불가", color = C.sub, fontSize = 24.sp)
                else SpeedGauge(z.maxSpeed, VerdictEngine.SCHOOL_LIMIT_KMH) {
                    Text("재출발 최고 속도", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.0f".format(z.maxSpeed), fontSize = 72.sp, fontWeight = FontWeight.Black, color = C.ink)
                        Text(" km/h", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink, modifier = Modifier.padding(bottom = 14.dp))
                    }
                    when (z.phase) {
                        SchoolPhase.SUCCESS -> Pill("30km/h 이하로 출발", Level.OK)
                        SchoolPhase.FAIL -> Pill("기준 30km/h 초과", Level.WARN)
                        else -> Pill(z.phase.label, Level.INFO)
                    }
                }
            }
        },
        right = {
            Card(Modifier.fillMaxWidth()) {
                CardHeader(null, "최근 출발 기록")
                KeyValue("30km/h 이하 연속 출발", "${z.streak}회")
                KeyValue("시연 포인트", "%,dP".format(z.points))
                Text("블루멤버스 포인트 연동을 가정한 시연값이에요.", color = C.sub, fontSize = 16.sp)
                TextRow("학교 위치 설정", if (Place.SCHOOL in u.settings.pins) "등록됨" else "등록하기") { vm.navigate.value = "SETTINGS" }
            }
        },
    )
}

// ------------------------------------------------------------------ ⑥ ⑦ (현대7)
@Composable
fun ChauffeurScreen(vm: VerifiedRuntime, u: UiState) {
    val c = u.engine.chauffeur
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Card(Modifier.fillMaxWidth(), padding = if (isCompact()) 22.dp else 36.dp) {
            // 넓은 화면은 예전처럼 글과 원 사이 간격 없이
            Split(if (isCompact()) 16.dp else 0.dp, center = true, first = { cell ->
                Column(cell, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("뒷좌석 승차감", fontSize = 32.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text(
                        (if (c.active) "2열 착석·등받이 젖힘 감지" else "2열에 손님이 앉으면 시작해요") + "\n급감속 ${c.brakes}회 · 급가속 ${c.accels}회",
                        fontSize = 24.sp, lineHeight = 34.sp, color = C.sub,
                    )
                    Text("급감속 순간에는 음성으로 바로 알려 드려요.", fontSize = 16.sp, color = C.sub)
                }
            }, second = { cell ->
                // 넓은 화면에서는 오른쪽 끝, 폰 세로에서는 아래 가운데
                Box(cell, contentAlignment = if (isCompact()) Alignment.Center else Alignment.CenterEnd) { ScoreRing(if (c.active) c.score else null, if (isCompact()) 200.dp else 280.dp) }
            }, w2 = 0f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("등받이 센서가 없는 차: 쇼퍼 모드 직접 켜기", Modifier.weight(1f), fontSize = 18.sp, color = C.ink)
            NavySwitch(c.manual, "쇼퍼 모드 직접 켜기") { on -> vm.act { _, _ -> setChauffeurManual(on); emptyList() } }
        }
        if (!u.caps.row2Recline) Note("이 차는 2열 등받이 각도를 읽을 수 없어 수동으로 켜야 해요.", Level.WARN)
    }
}

@Composable
fun TrunkScreen(vm: VerifiedRuntime, u: UiState) {
    val k = u.engine.trunk
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val x = u.engine.exit
        val f = u.engine.floor
        Split(22.dp, first = { cell -> Card(cell) {
                val checks = exitChecks(u)
                val bad = checks.count { it.bad }
                val unknown = checks.count { it.unread }
                CardHeader(null, "하차 3초 점검", "P단에서 운전석 문을 열면 확인해요.") {
                    Pill(if (bad > 0) "확인 필요 ${bad}건" else if (unknown > 0) "확인 불가 ${unknown}건" else "모두 확인", if (bad > 0) Level.WARN else if (unknown > 0) Level.INFO else Level.OK)
                }
                checks.forEach { c ->
                    val value = if (c.unread) "확인 불가" else if (c.bad) c.word else if (c.word == "열림") "닫힘" else "꺼짐"
                    CheckLine(if (c.unread) null else !c.bad, c.label, value, divider = c.label != "트렁크")
                }
                when {
                    x.lockReminded -> Note("내린 뒤 1분 동안 문이 잠기지 않았어요. 전송 상태는 ‘보낸 알림’에서 확인해 주세요.", Level.WARN)
                    x.unlockedSinceMs != null -> Note("내린 뒤 문이 아직 잠기지 않았어요. 1분이 지나면 폰으로 알려요.", Level.INFO)
                }
            } }, second = { cell -> Card(cell) {
                CardHeader(null, "지하 주차층", "GPS가 끊긴 뒤 램프를 돈 횟수로 층을 추정해요. 맞는 층을 고르면 주차 위치를 기억해요.") {
                    when {
                        f.confirmed != null -> Pill("기억함", Level.OK)
                        f.estimate != null -> Pill("확인해 주세요", Level.WARN)
                        f.tracking -> Pill("추적 중", Level.INFO)
                        else -> {}
                    }
                }
                when {
                    f.confirmed != null -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("B${f.confirmed}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = C.title)
                        Text("  ${f.place.label} 지하 ${f.confirmed}층", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    f.estimate != null -> {
                        Text("지하 ${f.estimate}층으로 추정돼요 (램프 ${f.turns}회)", fontSize = 26.sp, fontWeight = FontWeight.Black, color = C.ink)
                        Text("맞는 층을 눌러 주세요", fontSize = 17.sp, color = C.sub)
                        Wrap { (1..5).forEach { n -> Chip("B$n", n == f.estimate, big = true) { vm.act { _, _ -> confirmFloor(n) } } } }
                    }
                    f.tracking -> Text("지하로 들어왔어요 · 램프 ${f.turns}회", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    else -> Text("지하주차장에 들어가면 자동으로 시작해요", fontSize = 20.sp, color = C.sub)
                }
            } })
        Card(Modifier.fillMaxWidth(), padding = 28.dp) {
            Split(if (isCompact()) 18.dp else 0.dp, center = true, first = { cell ->
                Column(cell, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CardHeader(null, "트렁크 짐 확인 알림")
                    Text(if (k.loaded) "짐 알림이 켜져 있어요" else "기억해 둔 짐이 없어요", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text(if (k.loaded) "(${k.loadedAt})" else "밖에서 5초 이상 열었다 닫으면 짐으로 기억해요", fontSize = 22.sp, color = C.sub)
                    if (k.askConfirm) {
                        Text("방금 짐을 실으셨나요?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BigButton("네, 실었어요", fontSize = 19.sp) { vm.act { _, _ -> confirmTrunk(true); emptyList() } }
                            BigButton("아니요", style = BtnStyle.TONAL, fontSize = 19.sp) { vm.act { _, _ -> confirmTrunk(false); emptyList() } }
                        }
                    } else if (k.loaded) BigButton("괜찮아요 (알림 끄기)", style = BtnStyle.TONAL, fontSize = 19.sp) { vm.act { _, _ -> confirmTrunk(false); emptyList() } }
                }
            }, second = { cell ->
                Image(painterResource(if (C.dark) R.drawable.trunk_suv_cut else R.drawable.trunk_suv), "트렁크", cell.height(if (isCompact()) 160.dp else 200.dp).alpha(if (k.loaded) 1f else 0.6f), contentScale = ContentScale.Fit)
            })
        }
    }
}

// ------------------------------------------------------------------ ⑧ ⑨ (현대8)
private val shortFinding = mapOf("R01" to "크루즈 해제", "R02" to "와이퍼 AUTO 모드", "R03" to "내기순환 켜짐", "R04" to "주행모드 실내등", "R05" to "열린 곳 있음", "R06" to "워셔액 부족", "R07" to "벨트 풀림")

@Composable
fun DiagScreen(vm: VerifiedRuntime, u: UiState, diag: List<DiagFinding>, go: (String) -> Unit) {
    var detail by remember { mutableStateOf(false) }
    val settingsFound = diag.filter { it.code !in setOf("R99", "R06") }
    val m = u.engine.missions
    val done = m.items.filter { it.done }
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(Modifier.fillMaxWidth(), padding = 0.dp) { Box(Modifier.padding(horizontal = if (isCompact()) 18.dp else 24.dp, vertical = 8.dp)) { Split(if (isCompact()) 8.dp else 0.dp, w1 = 0f, center = true, first = { cell ->
            Image(painterResource(if (C.dark) R.drawable.ioniq_mini_cut else R.drawable.ioniq_mini), "내 차", (if (isCompact()) cell else Modifier).width(220.dp).height(100.dp), contentScale = ContentScale.Fit)
        }, second = { cell -> Column(cell.padding(start = if (isCompact()) 0.dp else 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("내 차 설정 확인", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink)
                }
                Text("주행거리 " + u.read("odometer", "%.1fkm".format(u.signals.odometerKm)) + " · 경고등·고장 코드는 확인 대상이 아니에요", fontSize = 16.sp, color = C.sub)
            } }) } }
        Split(22.dp, w1 = 1.15f, first = { cell -> Card(cell) {
                CardHeader(null, "이거 고장이야? 확인 도우미")
                Text(if (settingsFound.isNotEmpty()) "설정 때문일 수 있어요" else "설정에서는 원인을 못 찾았어요", fontSize = 34.sp, fontWeight = FontWeight.Black, color = C.ink)
                Text(
                    if (settingsFound.isNotEmpty()) "(" + settingsFound.joinToString(" · ") { shortFinding[it.code] ?: it.title } + ")" else "경고등이 켜졌거나 계속 이상하면 블루핸즈 점검을 받으세요",
                    fontSize = 20.sp, color = C.ink,
                )
                LabelRow({ m -> Box(m) { Pill("관련 설정 ${settingsFound.size}개", Level.INFO, big = true) } }) {
                    BigButton(if (detail) "접기" else "자세히 보기", style = BtnStyle.ACTION, fontSize = 19.sp) { detail = !detail }
                }
            } }, second = { cell -> Card(cell, onClick = { go(Module.MISSION.name) }) {
                CardHeader(null, "첫 운전 조작 미션") { Text("미션 열기", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.title) }
                GradientProgress(done.size / 7f, Modifier.fillMaxWidth())
                Text(if (done.isEmpty()) "아직 완료한 미션이 없어요" else "완료 · " + done.joinToString(" · ") { it.title }, fontSize = 19.sp, color = C.ink)
                Text("남은 미션: " + m.items.filter { !it.done }.joinToString(" / ") { it.title }.ifEmpty { "없음" }, fontSize = 19.sp, color = C.ink)
            } })
        if (detail) Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            Text(DIAG_DISCLAIMER, fontSize = 18.sp, color = C.warn)
            diag.forEach { f ->
                HorizontalDivider(color = C.line)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${f.code} · ${f.title}", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = C.ink)
                        Text(f.detail, color = C.sub, fontSize = 16.sp)
                    }
                    when (f.action) {
                        "M01", "M07" -> BigButton("조작 방법 보기", style = BtnStyle.TONAL, fontSize = 16.sp) {
                            // 이미 진행 중인 미션 기록은 지우지 않는다
                            if (!u.engine.missions.active) vm.act { _, now -> startMissions(now); emptyList() }
                            go(Module.MISSION.name)
                        }
                        // 차량 관리의 정비 예약은 ⑧ 확인 내용을 함께 보낸다
                        "SERVICE" -> BigButton("정비 예약", style = BtnStyle.TONAL, fontSize = 16.sp) { go("SERVICE") }
                        else -> {}
                    }
                }
            }
        } else Text(DIAG_DISCLAIMER, fontSize = 17.sp, color = C.sub)
        ProjectionHelpCard()
        if (u.signals.washerLow && !u.unread("washer")) Card(Modifier.fillMaxWidth(), padding = 18.dp, onClick = { go("SERVICE") }) {
            Text("워셔액 부족 감지", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.warn)
            Text("차량 관리에서 정비 예약 보기", fontSize = 17.sp, color = C.title)
        }
    }
}

@Composable
fun MissionScreen(vm: VerifiedRuntime, u: UiState) {
    val m = u.engine.missions
    val done = m.items.count { it.done }
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("9", "첫 운전 조작 미션", "실제로 스위치를 조작하면 차 신호로 확인해요 (P단에서만)") }
            if (!m.active) BigButton("미션 시작") { vm.act { _, now -> startMissions(now); emptyList() } }
        }
        // N단 유지 안내 중이면 지금 할 일이므로 맨 위에 둔다
        val nholdOn = u.engine.nhold.phase != NHoldPhase.OFF
        if (nholdOn) NHoldCard(vm, u)
        GradientProgress(done / 7f, Modifier.fillMaxWidth())
        Wrap(16.dp) {
            m.items.forEach {
                Card(Modifier.width(250.dp), padding = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(it.title, Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Black, color = C.title)
                        if (it.done) CheckMark(true, 28.dp)
                    }
                    Text(it.guide, fontSize = 16.sp, color = C.sub)
                    Text(it.elapsedSec?.let { s -> "${s}초 만에 성공" } ?: if (m.active) "조작을 기다리는 중" else "-", fontSize = 18.sp, color = if (it.done) C.ok else C.sub)
                }
            }
        }
        if (!nholdOn) NHoldCard(vm, u)
    }
}

/** 이중주차·자동세차용 N단 유지 모드 안내. 절차와 3분 규칙은 현대 취급설명서 기준. */
@Composable
private fun NHoldCard(vm: VerifiedRuntime, u: UiState) {
    val n = u.engine.nhold
    val s = u.signals
    Card(Modifier.fillMaxWidth()) {
        CardHeader(null, "N단 유지 도우미", "이중주차나 자동세차 때 시동을 끄고 N단을 유지하는 순서를 안내해요.", kicker = "현대 취급설명서 기준") {
            Pill(n.phase.label, if (n.phase == NHoldPhase.FAILED) Level.WARN else if (n.phase == NHoldPhase.OFF) null else Level.OK)
            Spacer(Modifier.width(12.dp))
            if (n.phase == NHoldPhase.OFF) BigButton("안내 시작", fontSize = 19.sp) { vm.act { _, _ -> startNHold(); emptyList() } }
            else BigButton("끝내기", style = BtnStyle.TONAL, fontSize = 19.sp) { vm.act { _, _ -> stopNHold(); emptyList() } }
        }
        if (n.phase != NHoldPhase.OFF) {
            CheckLine(true, "전자식 파킹 브레이크·오토홀드 해제", "직접 확인")
            CheckLine(s.gear == Gear.N, "브레이크를 밟고 N으로 변속", "${s.gear}단")
            CheckLine(true, "계기판 안내가 뜨면 OK 버튼 길게 누르기", "직접 확인")
            CheckLine(n.phase == NHoldPhase.HOLDING || n.phase == NHoldPhase.DONE, "\"N단이 유지됩니다\" 확인 후 시동 끄기", if (s.ignitionOn) "시동 켜짐" else "시동 꺼짐", divider = false)
            Note("시동을 끈 뒤 3분 안에 운전석 문을 열면 P로 바뀌어요. 이 상태에서는 문이 잠기지 않고, 오래 두면 배터리가 방전될 수 있어요.", Level.WARN)
        }
    }
}

// ------------------------------------------------------------------ 알림 · 설정
@Composable
fun NoticeScreen(u: UiState, openPhoneSettings: () -> Unit) {
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("보낸 알림", fontSize = 32.sp, fontWeight = FontWeight.Black, color = C.title)
        TextRow("차주 휴대폰 알림", if (u.phoneTopic == null) "연결하기" else "연결 설정") { openPhoneSettings() }
        if (!u.online && u.phoneTopic != null) Note("네트워크 연결을 기다리는 중이에요. 전송 대기 중인 알림은 연결이 돌아오면 다시 시도해요.", Level.WARN)
        if (u.notices.isEmpty()) Text("아직 보낸 알림이 없어요.", color = C.sub, fontSize = 18.sp)
        u.notices.forEach { n ->
            Card(Modifier.fillMaxWidth(), padding = 22.dp) {
                // 폰 세로에서는 받는 곳·시각을 본문 아래 줄에 둔다(옆에 두면 본문이 좁은 칸에 갇히고 겹친다)
                if (isCompact()) Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 8.dp)) { Dot(n.level) }
                    Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(n.title, fontWeight = FontWeight.Black, fontSize = 21.sp, color = C.ink)
                        Text(n.body, color = C.sub, fontSize = 16.sp)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text("${n.target} · ${VerdictEngine.hhmm(n.timeMs)}", color = C.sub, fontSize = 16.sp)
                            Text(n.phoneStatus.displayText(), fontSize = 16.sp, color = C.sub)
                        }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(n.level)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(n.title, fontWeight = FontWeight.Black, fontSize = 21.sp, color = C.ink)
                        Text(n.body, color = C.sub, fontSize = 16.sp)
                    }
                    Text(n.phoneStatus.displayText(), fontSize = 16.sp, color = C.sub)
                    Spacer(Modifier.width(12.dp))
                    Text("${n.target} · ${VerdictEngine.hhmm(n.timeMs)}", color = C.sub, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: VerifiedRuntime, u: UiState) {
    val st = u.settings
    TwoUp(36.dp, 28.dp, 24.dp, 18.dp, rightTop = if (isCompact()) 0.dp else 56.dp, tabs = "기능·동의" to "내 차·보안", left = {
            Text("설정", fontSize = 32.sp, fontWeight = FontWeight.Black, color = C.title)
            TextRow("차주 휴대폰 알림", if (u.phoneTopic == null) "연결하기" else "연결 설정") { vm.navigate.value = "PHONE_LINK" }
            Panel {
                Text("생활 유형 (홈에 올릴 기능)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Wrap { Persona.entries.forEach { p -> Chip(p.label, p in st.personas) { vm.settings { it.copy(personas = if (p in it.personas) it.personas - p else it.personas + p) } } } }
                Text("기능 하나씩 추가", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Wrap {
                    Module.entries.forEach { m ->
                        val fromPersona = st.personas.any { m in it.modules } && m !in st.extraModules
                        Chip(shortName(m), m in st.modules, Modifier.semantics { contentDescription = m.title }) {
                            // 생활 유형에서 켜진 기능은 여기서 꺼지지 않으므로 이유를 알려 준다
                            if (fromPersona) vm.toast("${m.no} ${m.title}은(는) 고른 생활 유형에 들어 있어요. 위에서 생활 유형을 빼 주세요.")
                            else vm.settings { it.copy(extraModules = if (m in it.extraModules) it.extraModules - m else it.extraModules + m) }
                        }
                    }
                }
            }
            Panel {
                Text(if (u.simAllowed) "시연용 차량 기능" else "차량 지원 기능", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (!u.simAllowed && !u.carConnected) Text("차량 연결을 기다리고 있어요.", fontSize = 16.sp, color = C.sub)
                else if (!u.simAllowed) CapsSummary(u.caps)
                else Wrap {
                    Chip("2열 착좌", u.caps.row2Occupancy) { vm.caps { it.copy(row2Occupancy = !it.row2Occupancy) } }
                    Chip("2열 등받이", u.caps.row2Recline) { vm.caps { it.copy(row2Recline = !it.row2Recline) } }
                    Chip("시트 자세", u.caps.seatPosture) { vm.caps { it.copy(seatPosture = !it.seatPosture) } }
                    Chip("경로 없이 위치", u.caps.locationWithoutRoute) { vm.caps { it.copy(locationWithoutRoute = !it.locationWithoutRoute) } }
                    Chip("앰비언트 제어", u.caps.ambientControl) { vm.caps { it.copy(ambientControl = !it.ambientControl) } }
                    Chip("시트 제어", u.caps.seatControl) { vm.caps { it.copy(seatControl = !it.seatControl) } }
                }
            }
            // 차종마다 읽히는 신호가 다르다. 읽지 못한 신호는 정상으로 보지 않고 '확인 불가'로 둔다
            Panel {
                Text("이 차에서 읽는 신호", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (u.simAllowed) Text("시연용 신호를 사용할 수 있어요.", fontSize = 16.sp, color = C.sub)
                else {
                    val (ok, no) = SIGNAL_LABELS.entries.partition { !u.unread(it.key) }
                    Text("읽음 ${ok.size}개" + if (ok.isEmpty()) "" else " · " + ok.joinToString("·") { it.value }, fontSize = 16.sp, color = C.ink)
                    Text("확인 불가 ${no.size}개" + if (no.isEmpty()) "" else " · " + no.joinToString("·") { it.value }, fontSize = 16.sp, color = C.sub)
                }
            }
            Panel {
                Text("승인 기능 · 사람별 동의", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Wrap { Flag.entries.forEach { f -> Chip(f.label, f in st.flags) { vm.settings { it.copy(flags = if (f in it.flags) it.flags - f else it.flags + f) } } } }
                Wrap {
                    Consent.entries.forEach { c ->
                        Chip(c.label + (st.consentAt[c]?.let { " · " + VerdictEngine.date(it).substring(5) + " " + VerdictEngine.hhmm(it) } ?: ""), c in st.consents) {
                            val now = System.currentTimeMillis()
                            vm.settings { if (c in it.consents) it.copy(consents = it.consents - c, consentAt = it.consentAt - c) else it.copy(consents = it.consents + c, consentAt = it.consentAt + (c to now)) }
                        }
                    }
                }
            }
        }, right = {
            OwnerPinSettings(vm, u)
            Panel { CarModelPicker(vm, u) }
            Panel {
                Text(if (u.locationOn) "장소 등록 · GPS 사용 중" else "장소 등록", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("차를 그 장소에 세운 뒤 누르면 지금 위치가 등록돼요. 좌표는 차 안에만 저장하고, 판정 결과(집 도착 등)만 써요.", fontSize = 18.sp, color = C.sub)
                listOf(Place.HOME, Place.SCHOOL, Place.HOSPITAL, Place.MART).forEach { place ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(place.label + if (place in st.pins) "  ✓ 등록됨" else "", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (place in st.pins) C.ok else C.ink)
                        Chip("현재 위치로 등록", null) { vm.registerPin(place) }
                        if (place in st.pins) {
                            Spacer(Modifier.width(8.dp))
                            Chip("삭제", null) { vm.removePin(place) }
                        }
                    }
                }
                Wrap { listOf(50, 100, 200).forEach { r -> Chip("반경 ${r}m", st.pinRadiusM == r) { vm.settings { it.copy(pinRadiusM = r) } } } }
            }
            Panel {
                Text("운전자와 보험 (입력값 · 보험사 연동 아님)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                st.profiles.forEachIndexed { i, p ->
                    LabelRow({ m -> Text(p.name, m, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }) {
                        Chip("특약 포함", p.insured) { vm.settings { s -> s.copy(profiles = s.profiles.mapIndexed { j, q -> if (j == i) q.copy(insured = !q.insured) else q }) } }
                        Spacer(Modifier.width(8.dp))
                        Chip("지금 자세로 등록", null) {
                            vm.act { sig, _ ->
                                settings = settings.copy(profiles = settings.profiles.mapIndexed { j, q -> if (j == i) q.copy(posture = sig.posture) else q })
                                rejudgeDriver()
                                emptyList()
                            }
                            vm.toast("${p.name} 자세를 등록했어요.")
                        }
                    }
                }
                Wrap { listOf("1인 한정", "부부 한정", "가족 한정", "누구나").forEach { sc -> Chip(sc, st.insuranceScope == sc) { vm.settings { it.copy(insuranceScope = sc) } } } }
                var date by remember(st.policyEndDate) { mutableStateOf(st.policyEndDate) }
                OutlinedTextField(date, { date = it; if (Regex("""\d{4}-\d{2}-\d{2}""").matches(it)) vm.settings { s -> s.copy(policyEndDate = it) } }, label = { Text("보험 만료일 (yyyy-MM-dd)") }, singleLine = true, textStyle = androidx.compose.material3.LocalTextStyle.current.copy(color = C.ink))
            }
            BigButton("처음 화면(생활 유형 선택)으로", Modifier.fillMaxWidth(), BtnStyle.TONAL, fontSize = 18.sp) { vm.settings { it.copy(personas = emptySet(), extraModules = emptySet()) } }
            // 실수로 지우지 않도록 두 번 눌러야 지운다
            var confirmWipe by remember { mutableStateOf(false) }
            BigButton(if (confirmWipe) "한 번 더 누르면 모두 지워요" else "내 기록 지우기", Modifier.fillMaxWidth(), if (confirmWipe) BtnStyle.FILLED else BtnStyle.OUTLINE, fontSize = 18.sp) {
                if (confirmWipe) vm.wipeMyData()
                confirmWipe = !confirmWipe
            }
            Text("운행기록·대리 기록·알림·등록 자세·장소 좌표·동의 기록을 지워요. 모든 기록은 이 차 안에만 저장돼요.", fontSize = 16.sp, color = C.sub)
        })
}

// ------------------------------------------------------------------ 차종 등록
@Composable
fun CarModelPicker(vm: VerifiedRuntime, u: UiState) {
    val selected = u.settings.carModel
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("내 차 등록", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
        Wrap {
            CarModel.entries.forEach { model ->
                Chip(model.label, selected == model, Modifier.testTag("car_${model.name}")) {
                    vm.ownerOnly { vm.settings { it.copy(carModel = model) } }
                }
            }
        }
        val support = when (selected?.controllable) {
            true -> "출발 준비·시트 복원·하차 조명은 차량 사양과 제어 권한에 따라 사용할 수 있어요."
            false -> "이 차에서는 운행 판정·안내·기록 기능을 사용해요."
            null -> "차종을 고르면 사용할 수 있는 기능을 알려 드려요."
        }
        Text(support, fontSize = 17.sp, color = C.sub)
        selected?.let { model ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CarPhoto(model, Modifier.fillMaxWidth(0.7f).heightIn(max = 110.dp))
                Text(if (model == CarModel.OTHER) "공식 사진 준비 중" else "공식 사진 · 연식·색상·트림은 다를 수 있어요", fontSize = 15.sp, color = C.sub)
            }
        }
    }
}

// ------------------------------------------------------------------ ⑩ 출발 준비
@Composable
fun ComfortScreen(vm: VerifiedRuntime, u: UiState) {
    val c = u.engine.comfort
    val st = u.settings
    var who by remember { mutableStateOf(st.comfort.keys.firstOrNull() ?: "차주") }
    val preset = st.comfort[who] ?: ComfortPreset.WARM
    fun save(p: ComfortPreset) = vm.settings { it.copy(comfort = it.comfort + (who to p)) }
    fun saveSch(n: DepartureSchedule) = vm.settings { it.copy(schedule = n) }
    val sch = st.schedule
    if (st.carModel == null || !u.canControl) {
        TwoPane(
            tabs = "이용 안내" to "차종 등록",
            left = {
                Card(Modifier.fillMaxWidth()) {
                    Text(if (st.carModel == null) "차량을 먼저 등록해 주세요" else "주행 기능을 사용하는 차예요", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.title)
                    Text(
                        if (st.carModel == null) "원격으로 미리 켜기, 출발 예약, 탑승 자동 적용은 등록한 제어 지원 차에서만 동작해요. 내 차가 아닌 차를 원격으로 켜는 일을 막기 위해서예요."
                        else "이 차에서는 판정·안내·기록 같은 주행 기능만 쓸 수 있어요.",
                        fontSize = 18.sp, lineHeight = 27.sp, color = C.sub,
                    )
                }
            },
            right = { Card(Modifier.fillMaxWidth()) { CarModelPicker(vm, u) } },
        )
        return
    }
    TwoPane(
        tabs = "좌석·공조" to "미리 켜기·예약",
        top = true,
        left = {
            ScreenTitle("10", "출발 준비", "미리 켜 둘 실내 온도와 좌석 설정을 고르세요.", kicker = st.carModel?.label)
            Wrap {
                st.comfort.keys.forEach { name -> Chip("$name 설정", name == who, big = true) { who = name } }
            }
            Wrap {
                ComfortPreset.PRESETS.forEach { p ->
                    Chip("${p.title} · %.0f℃".format(p.tempC), preset == p) { save(p) }
                }
            }
            Card(Modifier.fillMaxWidth(), padding = 22.dp) {
                // 폰 세로에서는 '실내 온도' 이름을 위에, 온도 조절을 아래 한 줄에 둔다(옆에 두면 이름이 쪼개진다)
                androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("실내 온도", if (isCompact()) Modifier.fillMaxWidth() else Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BigButton("−", Modifier.semantics { contentDescription = "온도 1도 낮추기" }, style = BtnStyle.TONAL, enabled = preset.tempC > 16f, fontSize = 22.sp) { save(preset.copy(tempC = (preset.tempC - 1).coerceAtLeast(16f))) }
                        Text("  %.0f℃  ".format(preset.tempC), fontSize = 34.sp, fontWeight = FontWeight.Black, color = C.title)
                        BigButton("+", Modifier.semantics { contentDescription = "온도 1도 높이기" }, style = BtnStyle.TONAL, enabled = preset.tempC < 30f, fontSize = 22.sp) { save(preset.copy(tempC = (preset.tempC + 1).coerceAtMost(30f))) }
                    }
                }
                // 열선과 통풍은 같은 좌석에서 동시에 켜지지 않으므로 하나를 켜면 다른 하나를 끈다
                Level4("운전석 열선", preset.driverHeat, u.caps.seatHeat) { save(preset.copy(driverHeat = it, driverVent = if (it > 0) 0 else preset.driverVent)) }
                Level4("운전석 통풍", preset.driverVent, u.caps.seatVent) { save(preset.copy(driverVent = it, driverHeat = if (it > 0) 0 else preset.driverHeat)) }
                Level4("조수석 열선", preset.passengerHeat, u.caps.seatHeat) { save(preset.copy(passengerHeat = it)) }
                Level4("뒷좌석 열선", preset.rearHeat, u.caps.seatHeat) { save(preset.copy(rearHeat = it)) }
                Wrap {
                    Chip("핸들 열선" + if (!u.caps.steeringHeat) " (미지원)" else "", preset.steeringHeat && u.caps.steeringHeat, enabled = u.caps.steeringHeat) { save(preset.copy(steeringHeat = !preset.steeringHeat)) }
                    Chip("앞 유리 성에 제거", preset.defrost) { save(preset.copy(defrost = !preset.defrost)) }
                }
            }
        },
        right = {
            val running = c.phase == ComfortPhase.REMOTE_RUNNING
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.deep).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Kicker(c.phase.label, dark = true)
                Text(
                    if (running) "남은 시간 ${((c.endsAtMs - u.nowMs + 59_999) / 60_000).coerceAtLeast(0)}분" else "${who}님 · ${preset.title}",
                    color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black,
                )
                Text((if (c.phase != ComfortPhase.OFF) c.preset ?: preset else preset).summary, color = Color(0xFFB9CBE6), fontSize = 17.sp)
                if (running) BigButton("원격 공조 끄기", Modifier.fillMaxWidth(), BtnStyle.TONAL) { vm.act { _, _ -> stopRemote() } }
                else BigButton("지금 미리 켜기 (원격)", Modifier.fillMaxWidth(), BtnStyle.CYAN) { vm.act { s, now -> startRemote(s, now, who) } }
                c.message?.let { Text(it, color = Color.White, fontSize = 18.sp) }
            }
            if (u.winter.isNotEmpty()) Card(Modifier.fillMaxWidth(), padding = 24.dp) {
                CardHeader(null, "겨울 출발 점검", "외기 " + u.read("outsideTemp", "%.0f℃".format(u.signals.outsideTempC))) {
                    Pill("확인 ${u.winter.size}건", Level.WARN)
                }
                u.winter.forEachIndexed { i, it -> CheckLine(false, it, divider = i < u.winter.lastIndex) }
            }
            Card(Modifier.fillMaxWidth(), padding = 22.dp) {
                CardHeader(null, "출발 예약") { NavySwitch(sch.enabled, "출발 예약") { on -> saveSch(sch.copy(enabled = on)) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BigButton("−", style = BtnStyle.TONAL, fontSize = 20.sp) { val m = (sch.hour * 60 + sch.minute - 10 + 1440) % 1440; saveSch(sch.copy(hour = m / 60, minute = m % 60)) }
                    // 폰에서는 −·+ 사이 남은 폭만 쓰고 시각만(카드 제목이 '출발 예약'), 큰 글씨에서도 +가 화면 밖으로 밀리지 않게
                    Text((if (isCompact()) "%02d:%02d" else "  %02d:%02d 출발  ").format(sch.hour, sch.minute), if (isCompact()) Modifier.weight(1f) else Modifier, fontSize = 30.sp, fontWeight = FontWeight.Black, color = C.title, textAlign = TextAlign.Center, maxLines = 1)
                    BigButton("+", style = BtnStyle.TONAL, fontSize = 20.sp) { val m = (sch.hour * 60 + sch.minute + 10) % 1440; saveSch(sch.copy(hour = m / 60, minute = m % 60)) }
                }
                // 좁으면 요일이 다음 줄로 넘어가게
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, d ->
                        Chip(d, (i + 1) in sch.days) { saveSch(sch.copy(days = if ((i + 1) in sch.days) sch.days - (i + 1) else sch.days + (i + 1))) }
                    }
                }
                Text("출발 ${sch.leadMin}분 전에 차주 설정으로 미리 켜고, ${VerdictEngine.REMOTE_MINUTES}분 뒤 자동으로 꺼요.", fontSize = 17.sp, color = C.sub)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("탑승하면 그 운전자 설정으로 자동 적용", Modifier.weight(1f), fontSize = 18.sp, color = C.ink)
                NavySwitch(st.autoApplyOnBoard, "탑승하면 운전자 설정 자동 적용") { on -> vm.settings { it.copy(autoApplyOnBoard = on) } }
            }
            Text(
                "원격 실행은 P단·문 잠김·탑승자 없음·배터리 ${VerdictEngine.REMOTE_MIN_SOC}% 이상일 때만 해요. 처음 보는 운전자에게는 개인 설정을 적용하지 않아요. " +
                    "시동이 꺼진 차는 화면도 꺼져 있어 원격 실행은 마이현대 원격 공조로 넘깁니다(연동 목업).",
                fontSize = 17.sp, color = C.sub,
            )
        },
    )
}

@Composable
private fun Level4(label: String, level: Int, supported: Boolean, set: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label + if (!supported) " (미지원)" else "", fontSize = 18.sp, color = if (supported) C.ink else C.sub)
        if (supported) Wrap(6.dp) {
            listOf("끔", "1단", "2단", "3단").forEachIndexed { i, t -> Chip(t, level == i, Modifier.semantics { contentDescription = "$label $t" }) { set(i) } }
        }
    }
}
