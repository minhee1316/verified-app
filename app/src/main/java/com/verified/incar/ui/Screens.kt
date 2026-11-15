package com.verified.incar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.shadow
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
        Module.CHAUFFEUR -> (if (e.chauffeur.active) "급감속 ${e.chauffeur.brakes} · 급가속 ${e.chauffeur.accels}" else "시작 대기") to if (e.chauffeur.active) Level.INFO else null
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
                else -> "하차 점검 정상" to Level.OK
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
fun HandoverDiff(a: com.verified.incar.core.CarCheck?, b: com.verified.incar.core.CarCheck?, hide: List<String> = emptyList()) {
    if (a == null || b == null) return
    val lines = VerdictEngine.diffChecks(a, b)
    Column(Modifier.fillMaxWidth().testTag("handover_diff"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text((if (lines.any { !it.startsWith("확인 불가") }) "차가 쓴 인수인계서" else "인수인계서") + " · 인수 ${VerdictEngine.hhmm(a.atMs)} → 돌려받음 ${VerdictEngine.hhmm(b.atMs)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
        // [hide]: 위 숫자 요약에 이미 나온 줄(주행거리·배터리)은 다시 쓰지 않는다
        val shown = lines.filterNot { l -> l.startsWith("확인 불가") || hide.any { l.startsWith(it) } }
        Wrap { if (shown.isEmpty() && hide.isNotEmpty()) Pill("그 밖에 달라진 것 없음", Level.OK) else shown.forEach { Pill(it, diffLevel(it)) } }
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
        Text("차주 직접 체크리스트 · ${phase.label}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Text("현대 기능을 직접 확인하고 눌러 주세요. 차주 확인으로 기록돼요.", fontSize = 16.sp, lineHeight = 23.sp, color = C.sub)
        items.forEachIndexed { i, item ->
            val on = item in prep
            // 안 한 항목은 '!'(판정 주의)가 아니라 빈 원으로: 아직 차주가 확인하지 않았을 뿐이다
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(if (on) { if (C.dark) Color(0xFF20364F) else Tone.blueSoft } else C.bg)
                    .border(if (on) 2.dp else 1.dp, if (on) Color(0xFF2F7BE0) else C.line, RoundedCornerShape(14.dp))
                    .toggleable(on, role = androidx.compose.ui.semantics.Role.Checkbox) { vm.togglePrep(item, valet) }.padding(horizontal = 14.dp, vertical = 14.dp),
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
fun Restricted(u: UiState, lines: List<String>, onDemoStop: (() -> Unit)? = null) {
    val stop = onDemoStop?.takeIf { u.trial && u.simAllowed && !u.carConnected }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxHeight < 440.dp) RestrictedLandscape(u, lines, stop)
        else RestrictedFull(u, lines, stop)
    }
}

@Composable
private fun DemoStop(action: () -> Unit) {
    Row(Modifier.testTag("demo_stop").clip(RoundedCornerShape(10.dp)).background(Color(0xFF304862))
        .clickable(role = Role.Button, onClick = action).heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.StopCircle, null, Modifier.size(20.dp), tint = Color.White)
        Text("시연 멈추기", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 낮은 가로 화면에서도 잠금 안내를 항상 확보한다. 요약은 두 줄 대신 한 줄로 표시한다. */
@Composable
private fun RestrictedLandscape(u: UiState, lines: List<String>, onDemoStop: (() -> Unit)?) {
    val foreground = Color(0xFFF6F8FC)
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color(0xFF152437), Color(0xFF070D17))))
        .testTag("restricted").padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (onDemoStop != null) "시연 주행 중" else "주행 중", Modifier.weight(1f), color = foreground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (onDemoStop != null) DemoStop(onDemoStop) else {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = foreground)
                Text(" 조작 잠김", color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (u.unread("speed")) Text("확인 불가", color = foreground, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                else Text("%.0f".format(u.signals.speedKmh), Modifier.fillMaxWidth().testTag("restricted_speed"), color = foreground, fontSize = 112.sp,
                    lineHeight = 112.sp, fontWeight = FontWeight.Bold, softWrap = false, textAlign = TextAlign.Center)
                Text("km/h", color = Color(0xFFA8B6C9), fontSize = 22.sp)
            }
            Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.take(3).forEachIndexed { index, line ->
                    Row(Modifier.fillMaxWidth().testTag("restricted_status_$index").shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0xFF206BC1), spotColor = Color(0xFF206BC1))
                        .clip(RoundedCornerShape(14.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF142C4D), Color(0xFF07162B))))
                        .border(1.dp, Color(0xFF3579B9), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (line.startsWith("⚠")) Icons.Outlined.Warning else Icons.Outlined.Shield,
                            null, Modifier.size(22.dp), tint = Color(0xFF81B8FF))
                        Text(line, Modifier.weight(1f), color = foreground, fontSize = 18.sp, lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF253143))
            .padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = Color(0xFFFFBD77))
            Spacer(Modifier.width(8.dp))
            Text("정차 후 P단에서 조작할 수 있어요.", Modifier.weight(1f).testTag("restricted_unlock_hint"), color = Color(0xFFFFD4A3),
                fontSize = 18.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RestrictedFull(u: UiState, lines: List<String>, onDemoStop: (() -> Unit)?) {
    val compact = isCompact()
    val foreground = Color(0xFFF6F8FC)
    val muted = Color(0xFFA8B6C9)
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color(0xFF152437), Color(0xFF070D17))))
        .testTag("restricted").padding(horizontal = if (compact) 20.dp else 44.dp, vertical = if (compact) 20.dp else 26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (onDemoStop != null) "시연 주행 중" else "주행 중", Modifier.weight(1f), color = foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            if (onDemoStop != null) DemoStop(onDemoStop) else Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = foreground)
                Text("조작 잠김", color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Split(if (compact) 28.dp else 48.dp, w1 = 1f, w2 = 1f, center = true, first = { cell ->
            Column(cell.padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (u.unread("speed")) Text("확인 불가", color = foreground, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                else Text("%.0f".format(u.signals.speedKmh), Modifier.fillMaxWidth().testTag("restricted_speed"), color = foreground, fontSize = 144.sp,
                    fontWeight = FontWeight.Bold, lineHeight = 150.sp, softWrap = false, textAlign = TextAlign.Center)
                Text("km/h", color = muted, fontSize = 28.sp, fontWeight = FontWeight.Medium, softWrap = false)
            }
        }, second = { cell ->
            Column(cell, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                lines.take(3).forEachIndexed { index, line ->
                    val no = line.firstOrNull()?.takeIf { it in '①'..'⑩' }
                    val body = if (no != null) line.drop(1).trim() else line
                    val (name, value) = body.split(": ", limit = 2).let { it[0] to it.getOrNull(1) }
                    val icon = when (no) {
                        '①' -> Icons.Outlined.LocalParking; '②' -> Icons.Outlined.Person; '③' -> Icons.Outlined.Receipt
                        '④' -> Icons.AutoMirrored.Outlined.Accessible; '⑤' -> Icons.Outlined.School
                        '⑥' -> Icons.Outlined.AirlineSeatReclineExtra; '⑨' -> Icons.Outlined.Flag
                        else -> if (line.startsWith("⚠")) Icons.Outlined.Warning else Icons.Outlined.Shield
                    }
                    val tint = when (no) { '①' -> Color(0xFFFFD875); '⑤' -> Color(0xFF6FE1B2); else -> Color(0xFF81B8FF) }
                    val shape = RoundedCornerShape(if (compact) 36.dp else 22.dp)
                    Row(Modifier.fillMaxWidth().testTag("restricted_status_$index").shadow(if (compact) 3.dp else 10.dp, shape, ambientColor = Color(0xFF206BC1), spotColor = Color(0xFF206BC1)).clip(shape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(if (compact) listOf(Color(0xFF455665), Color(0xFF344553)) else listOf(Color(0xFF142C4D), Color(0xFF07162B))))
                        .border(1.dp, if (compact) Color(0xFF4D6274) else Color(0xFF3579B9), shape)
                        .padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (compact) Box(Modifier.size(38.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(25.dp), tint = Color(0xFF123040))
                        } else Icon(icon, null, Modifier.size(32.dp), tint = tint)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(name, color = foreground, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                            if (value != null) Text(value, color = muted, fontSize = 18.sp)
                        }
                    }
                }
            }
        })
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().shadow(if (compact) 8.dp else 0.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(if (compact) Color(0xFFF9F7F3) else Color(0xFF253143))
            .then(if (compact) Modifier.border(3.dp, Color(0xFFE88A36), RoundedCornerShape(22.dp)) else Modifier)
            .padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(if (compact) 28.dp else 20.dp), tint = if (compact) Color(0xFFBA5A14) else Color(0xFFFFBD77))
            Spacer(Modifier.width(10.dp))
            Text("정차 후 P단에서 조작할 수 있어요.", Modifier.weight(1f).testTag("restricted_unlock_hint"), color = if (compact) Color(0xFF13212C) else Color(0xFFFFD4A3), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------ ① (현대2)
@Composable
fun ValetScreen(vm: VerifiedRuntime, u: UiState) {
    // 세로/가로에서 서로 다른 화면 트리를 쓰더라도 펼친 동의 안내는 유지한다.
    var guideMore by rememberSaveable { mutableStateOf(false) }
    // 폰 세로는 한 단(머리 카드 → 단계 → 할 일 → 근거)으로 따로 그린다. 차 화면은 아래 두 단 그대로
    if (u.engine.lend.active && !u.engine.valet.active) {
        val route = if (u.engine.lend.kind == com.verified.incar.core.HandKind.LEND) "HAND_LEND" else "HAND_SERVICE"
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("진행 중인 맡김이 있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            BigButton("맡긴 차 보기", Modifier.fillMaxWidth()) { vm.navigate.value = route }
        }
        return
    }
    if (isCompact()) { PhoneValet(vm, u, guideMore) { guideMore = !guideMore }; return }
    val v = u.engine.valet
    val (label, level) = statusOf(Module.VALET, u)
    TwoPane(
        tabs = "그림·근거" to "판정",
        // 폰 세로에서는 판정 카드를 그림보다 먼저
        rightFirst = true,
        left = {
            ValetStatusArtwork(u, label, level, Modifier.fillMaxWidth())
            // 차주가 없는 순간의 근거: 어떤 신호를 언제 읽어 판정했는지(차주 폰 알림에도 같은 줄이 간다). 왼쪽에 두어 두 단 높이를 맞춘다
            if (v.proof.isNotEmpty() && v.phase != ValetPhase.CONSENT) Card(Modifier.fillMaxWidth().testTag("valet_proof"), padding = 22.dp) {
                CardHeader(null, "확인 근거", sub = "시각 · 판정 · 읽은 곳")
                ProofLines(v.proof)
            }
        },
        right = { ValetBody(vm, u, phone = false, guideMore = guideMore) { guideMore = !guideMore } },
    )
}

/**
 * ① 판정·조작 칸. 차 화면 오른쪽 단과 폰 화면이 같이 쓴다.
 * [phone]이면 내비 줄은 머리 카드로 올린다.
 */
@Composable
private fun ValetBody(vm: VerifiedRuntime, u: UiState, phone: Boolean, guideMore: Boolean, toggleGuide: () -> Unit) {
    val v = u.engine.valet
    val s = u.signals
    val stoppedSec = u.engine.stoppedSinceMs?.let { (u.nowMs - it) / 1000 } ?: 0
    val estimated = s.gpsLost || !u.caps.locationWithoutRoute
    val place = if (estimated) u.engine.lastPlace else s.place
    val (label, level) = statusOf(Module.VALET, u)
    // 시연을 멈추거나 앱을 다시 켜 '완료 확인 중'에 머문 예시 운행도 처음부터 다시 재생할 수 있게 한다
    if (u.trial && u.simAllowed && !u.carConnected && (!v.active || v.phase == ValetPhase.CONSENT || v.phase == ValetPhase.DRIVING || v.phase == ValetPhase.PRELIM)) Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text(if (v.active) "예시 운행을 재생할 수 있어요" else "처음이라면 전체 과정을 확인해 보세요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
        Text(if (v.active) "현재 시연을 처음부터 다시 시작해 기사 동의·출발·집 도착·하차까지 보여줘요." else "예시 차량으로 기사 동의·출발·집 도착·하차 신호를 차례로 재생해요.", fontSize = 15.sp, color = C.sub)
        BigButton(if (v.active) "전체 과정 다시 시연" else "대리 주차 자동 시연", Modifier.fillMaxWidth().testTag("valet_demo_start"), BtnStyle.OUTLINE) { vm.runScenario("valet") }
    }
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
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("valet_guide")) {
                    val items = listOf(Icons.Outlined.Schedule to "운행 시간·\n거리 기록", Icons.Outlined.MeetingRoom to "주차 후\n문·창문 확인", Icons.Outlined.PhonelinkLock to "상세 기록은\n이 기기에 보관")
                    val stack = maxWidth < (if (phone) 260.dp else 480.dp) || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
                    if (stack) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { (icon, label) ->
                            Row(Modifier.fillMaxWidth().lift(RoundedCornerShape(14.dp), 3.dp).clip(RoundedCornerShape(14.dp)).background(C.card).heightIn(min = 64.dp).padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Icon(icon, null, Modifier.size(32.dp), tint = C.title)
                                Text(label.replace("\n", " "), Modifier.weight(1f), fontSize = if (phone) 16.sp else 22.sp,
                                    lineHeight = if (phone) 23.sp else 30.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                            }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { (icon, label) ->
                            Column(Modifier.weight(1f).lift(RoundedCornerShape(14.dp), 4.dp).clip(RoundedCornerShape(14.dp)).background(C.card).padding(vertical = if (phone) 18.dp else 20.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(icon, null, Modifier.size(if (phone) 38.dp else 52.dp), tint = C.title)
                                Text(label, fontSize = if (phone) 15.sp else 22.sp, lineHeight = if (phone) 22.sp else 30.sp, fontWeight = FontWeight.SemiBold, color = C.ink, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                Text(if (guideMore) "동의·인수·도착 예정·실제 도착 시각, 주행거리·배터리와 주차 전후 문·잠금·창문·트렁크·바퀴·경고·소모품 상태를 대리 이력에 기록해요. " +
                    "집 도착과 경로 이탈을 판단할 때 위치·기어·정차·시동·하차 신호를 사용하고 판단 근거를 남겨요. 상세 기록은 이 기기에 보관하고, 연결한 차주 휴대폰에는 시작·완료·확인 필요 같은 짧은 상태 알림을 보내요."
                    else "기록하는 항목 자세히 보기 ›",
                    Modifier.testTag("valet_guide_more").clickable(role = Role.Button, onClick = toggleGuide).padding(vertical = 6.dp), fontSize = 16.sp, lineHeight = 23.sp, color = C.sub)
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
                val placeKnown = done || (!estimated && !u.unread("gps") &&
                    (!u.carConnected || s.seenAt["gps"]?.let { u.nowMs - it in 0..30_000L } == true))
                // 운전석 착좌는 좌석 구독이 살아 있고 운전석 영역 값까지 받았을 때만 읽은 것으로 본다(엔진 판정과 같은 기준)
                val driverSeatKnown = !u.unread("row2Occupancy") && (u.simAllowed || "driverSeat" in s.seenAt)
                val leftCar = (driverSeatKnown && !s.driverSeated) || (!u.unread("doors") && s.driverDoorOpen)
                val exitKnown = done || leftCar || (driverSeatKnown && !u.unread("doors"))
                // 무엇이 충족됐고 무엇을 기다리는지 한눈에 보이게 2x2 타일로
                Column(Modifier.testTag("valet_checks"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckTile((done || s.gear == Gear.P).takeIf { gearKnown }, "P단 체결", if (gearKnown) null else "확인 불가", Modifier.weight(1f))
                        CheckTile((done || stoppedSec >= 10).takeIf { speedKnown }, "차속 0", if (!speedKnown) "확인 불가" else if (byOff) "시동 OFF로 확정" else if (done) "10초 유지" else if (s.speedKmh > 0) "주행 중" else "${stoppedSec} / 10초", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckTile((done || place == Place.HOME).takeIf { placeKnown }, "집 반경 ${u.settings.pinRadiusM}m 안", if (!placeKnown) "확인 불가" else if (if (done) v.placeEstimated else estimated) "지하 진입 전 위치" else null, Modifier.weight(1f))
                        CheckTile((done || leftCar).takeIf { exitKnown }, "운전석 하차 완료", if (!exitKnown) "확인 불가" else if (byOff) "시동 OFF로 확정" else if (done || leftCar) null else "기다리는 중", Modifier.weight(1f))
                    }
                }
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
                else -> if (v.verificationPending != null) Note(v.verificationPending, Level.INFO)
                    else v.prelimAtMs?.let { Note("완료 확인 중 · ${VerdictEngine.hhmm(it)} 집 앞 P단을 차에 저장했어요. 시동 OFF 또는 운전석 하차를 기다려요", Level.INFO) }
            }
            val returned = v.phase == ValetPhase.COMPLETE || v.phase == ValetPhase.INCOMPLETE
            if (returned) ValetDelivery(u)
            if (!returned && (v.verificationPending != null || listOf("gear", "speed", "gps", "ignition", "doors", "row2Occupancy").any { u.unread(it) })) {
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
            // '맡기기 전' 체크리스트는 동의 대기 때만. 출발한 뒤에는 돌려받을 때 것만 보인다
            if (returned) Card(Modifier.fillMaxWidth(), padding = 24.dp) {
                HandoverDiff(v.checkIn, v.checkOut)
                PrepChecklist(vm, u, true, com.verified.incar.core.PrepPhase.RETURN)
                HistoryLink(vm, u)
            }
        }
    }
}

/**
 * ① 폰 세로: 머리 카드(상태 한 단어·언제부터·도착 예정) → 4단계 진행 → 단계별 할 일(차 화면과 같은 문구·버튼) → 최근 근거 3줄.
 * 큰 탑뷰 그림은 화면 길이만 두 배로 늘려 폰에서는 뺀다.
 */
@Composable
private fun PhoneValet(vm: VerifiedRuntime, u: UiState, guideMore: Boolean, toggleGuide: () -> Unit) {
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
            PhoneSteps(listOf("기사 동의", "운행", "확인 중", "완료"), step, warnLast = v.phase == ValetPhase.INCOMPLETE)
        }
        ValetBody(vm, u, phone = true, guideMore = guideMore, toggleGuide = toggleGuide)
        if (v.phase != ValetPhase.CONSENT) PhoneProof(v.proof, "valet_proof")
    }
}

/** '차에 기록됨'과 '차주에게 전달됨'을 나눠 보여 준다. 알림 서버 접수는 휴대폰 표시까지 뜻하지 않는다. */
@Composable
private fun ValetDelivery(u: UiState) {
    val savedAt = u.engine.valet.result?.timeMs ?: return
    // 알림 목록은 최신순이고, 완료·미완료 알림은 판정과 같은 순간에 만들어진다
    val n = u.notices.firstOrNull { it.title.startsWith("① ") || it.title.startsWith("대리 반납") }
    val example = n?.phoneExample == true || (u.simAllowed && !u.carConnected)
    val (phone, level) = when {
        u.phoneTopic == null -> "차주 폰 미연결" to Level.INFO
        n?.phoneStatus == com.verified.incar.PhoneDelivery.ACCEPTED -> "차주 폰 알림: 알림 서버 접수(휴대폰 표시는 폰에서 확인)" to Level.OK
        n?.phoneStatus == com.verified.incar.PhoneDelivery.QUEUED -> "차주 폰 알림: 전송 대기 · 인터넷이 연결되면 다시 보내요" to Level.WARN
        n?.phoneStatus == com.verified.incar.PhoneDelivery.FAILED -> "차주 폰 알림: 전송 실패" to Level.WARN
        else -> "차주 폰 알림: 보내지 않음" to Level.INFO
    }
    Note((if (example) "예시 값 결과 · " else "") + "차에 결과 저장됨 ${VerdictEngine.hhmm(savedAt)} · $phone", level)
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
        tabs = "판정" to "운전자 모드",
        left = {
            Card(Modifier.fillMaxWidth().testTag("driver_posture"), padding = 16.dp) {
                Image(painterResource(R.drawable.seat_posture), "시트 자세",
                    Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit,
                    colorFilter = if (C.dark) androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFFCBD7E4), androidx.compose.ui.graphics.BlendMode.Modulate) else null)
            }
            Row(
                Modifier.fillMaxWidth().testTag("driver_verdict").lift(RoundedCornerShape(18.dp), 4.dp).clip(RoundedCornerShape(18.dp)).background(if (level == Level.OK) { if (C.dark) Color(0xFF173E35) else Tone.greenSoft } else C.chip)
                    .border(2.dp, if (level == Level.OK) Tone.mint else levelColor(level).copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
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
            if (d.showNewDriverGuide) Note("가입하신 보험사 앱이나 고객센터에서 '임시 운전자 특약'을 확인하세요. 현대 카가드는 보험을 판매하거나 중개하지 않습니다.", Level.WARN)
            d.notes.forEach { Text("· $it", fontSize = 18.sp, color = if ("만료" in it || "아니에요" in it) C.warn else C.sub) }
        },
        top = true,
    )
}

// ------------------------------------------------------------------ 맡김 이력서
@Composable
fun HistoryScreen(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit = {}) {
    val h = u.engine.history
    val r = com.verified.incar.core.MyHyundai.resume(h)
    TwoPane(
        tabs = "요약" to "기록",
        top = true,
        left = {
            Card(Modifier.fillMaxWidth().testTag("history_drive"), padding = 16.dp) {
                val locked = u.ownerPinSet && !u.ownerUnlocked && (u.engine.valet.active || u.engine.lend.active)
                RecordOverviewRow("이번 주 주행", if (locked) "차주 확인 후" else u.thisWeek?.let { "%.1f km".format(it.km) } ?: "기록 없음") { go("INSIGHT") }
                HorizontalDivider(color = C.line)
                RecordOverviewRow("소모품 교체 시기", u.consumables.count { it.due }.takeIf { it > 0 }?.let { "교체 시기 ${it}건" } ?: "모두 여유") { go("SERVICE") }
                HorizontalDivider(color = C.line)
                RecordOverviewRow("주차 위치", u.engine.floor.confirmed?.let { "지하 ${it}층" } ?: "기록 없음") { go("TRUNK") }
            }
            Card(Modifier.fillMaxWidth().testTag("history_summary"), padding = 20.dp) {
                CardHeader(null, "맡김 요약")
                if (h.isEmpty()) {
                    Text("아직 맡긴 기록이 없어요. 차를 맡기면 언제·누가·얼마나 운전했는지 여기에 쌓여요.", fontSize = 17.sp, color = C.sub)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton("대리", Modifier.weight(1f).testTag("history_start_valet"), BtnStyle.TONAL, fontSize = 17.sp) { go("VALET") }
                        BigButton("정비", Modifier.weight(1f), BtnStyle.TONAL, fontSize = 17.sp) { go("HAND_SERVICE") }
                        BigButton("빌려주기", Modifier.weight(1f), BtnStyle.TONAL, fontSize = 17.sp) { go("HAND_LEND") }
                    }
                } else {
                    val brakes = h.filter { it.recordsDrivingStats && it.speedKnown }.sumOf { it.brakes }
                    HandoverMetricSummary(listOf(
                        "총 맡김" to "${h.size}회",
                        "남이 운전" to (if (h.all { it.km == null }) "확인 불가" else "%.1fkm".format(h.sumOf { it.km ?: 0.0 })),
                        "급감속" to (if (h.none { it.recordsDrivingStats && it.speedKnown }) "확인 불가" else "${brakes}회"),
                    ), Modifier.testTag("history_tiles"))
                    // 숫자 칸과 겹치지 않는 것만: 문제 없이 돌려받은 횟수, 확인 불가로 뺀 횟수
                    (listOf(r.lines[0].substringAfter(" · ")) + r.lines.drop(1).filter { "제외" in it })
                        .forEach { Text("· $it", fontSize = 14.sp, lineHeight = 20.sp, color = C.sub) }
                }
                var more by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                Text(if (more) com.verified.incar.core.MyHyundai.RESUME_NOTE else "이 자료는 어디에 쓰나요? ›",
                    Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { more = !more }.padding(vertical = 4.dp),
                    fontSize = 15.sp, color = C.sub)
            }
            BigButton("CSV로 내보내기   ⇪", Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("history_export"), enabled = h.isNotEmpty(), fontSize = 23.sp) { vm.exportHistory() }
            FineCheckCard(h)
        },
        right = {
            if (h.isEmpty()) Card(Modifier.fillMaxWidth(), padding = 24.dp) {
                CardHeader(null, "아직 기록이 없어요")
                Text("대리운전, 차 빌려주기, 정비 맡김을 마치면 이곳에 기록이 생겨요.", fontSize = 17.sp, lineHeight = 25.sp, color = C.sub)
            }
            // 검색·필터: 메모·바뀐 것·근거에서 찾고, 종류·확인 필요·기간으로 좁힌다
            var query by rememberSaveable { mutableStateOf("") }
            var kind by rememberSaveable { mutableStateOf<String?>(null) }
            var needCheck by rememberSaveable { mutableStateOf(false) }
            var days by rememberSaveable { mutableStateOf<Int?>(null) }
            val shown = filterHistory(h, kind, needCheck, days, query, u.nowMs)
            if (h.isNotEmpty()) HistoryFilterBar(query, { query = it }, kind, { kind = it }, needCheck, { needCheck = it }, days, { days = it }, shown.size, h.size)
            if (h.isNotEmpty() && shown.isEmpty()) Text("조건에 맞는 기록이 없어요.", fontSize = 17.sp, color = C.sub)
            // 최근 것부터. 누르면 한 건 상세(시간순 기록·사진·메모·PDF)
            shown.asReversed().forEach { rec ->
                Card(Modifier.fillMaxWidth().testTag("history_record"), padding = 22.dp, onClick = { go("RECORD_${rec.startMs}") }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBubble(if (rec.kind == "대리") Icons.Outlined.LocalParking else if (rec.kind == "맡김") Icons.Outlined.Build else Icons.Outlined.Key, 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(if (rec.kind == "맡김") "정비 맡김" else rec.kind, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
                            Text("${VerdictEngine.date(rec.startMs)} · ${VerdictEngine.hhmm(rec.startMs)} ~ " +
                                (if (VerdictEngine.date(rec.endMs) != VerdictEngine.date(rec.startMs)) VerdictEngine.date(rec.endMs) + " " else "") + VerdictEngine.hhmm(rec.endMs), fontSize = 16.sp, color = C.sub)
                        }
                    }
                    Pill(if (rec.ok) "문제 없이 돌려받음" else "확인 필요", if (rec.ok) Level.OK else Level.WARN)
                    HorizontalDivider(color = C.line)
                    Wrap {
                        Pill("남이 운전 " + (rec.km?.let { "%.1fkm".format(it) } ?: "거리 확인 불가"), Level.INFO)
                        if (rec.recordsDrivingStats) {
                            Pill(if (rec.speedKnown) "최고 %.0fkm/h".format(rec.maxSpeed) else "최고 속도 확인 불가", null)
                            Pill(if (rec.speedKnown) "급감속 ${rec.brakes}회" else "급감속 확인 불가", if (rec.speedKnown && rec.brakes > 0) Level.WARN else null)
                        } else Pill("인수·반납 상태 기록", Level.INFO)
                    }
                    rec.changes.forEach { Text("· $it", fontSize = 16.sp, color = if (it.startsWith("확인 불가")) C.warn else C.sub) }
                    val photos = handoverPhotoCount(androidx.compose.ui.platform.LocalContext.current, rec.startMs, demo = u.trial)
                    if (photos > 0) Text("· 같은 각도 사진 ${photos}장 (이 기기에 저장)", fontSize = 16.sp, color = C.title)
                    if (rec.memo.isNotBlank()) Text("메모 · ${rec.memo}", fontSize = 16.sp, color = C.ink, maxLines = 2)
                    Text("자세히 보기 · PDF ›", Modifier.testTag("history_open_${rec.startMs}"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.title)
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
                        Text("운행 요약", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Text(if (t.pending != null) "방금 끝난 운행" else "최근 운행 내역", fontSize = 17.sp, color = C.ink)
                    }
                    Text("📍", fontSize = 34.sp)
                }
                if (shown != null) {
                    Text("출발: ${shown.from}  →  도착: ${shown.to}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = C.title)
                    Text("주행거리 %.1fkm  •  운전자: %s".format(shown.distKm, shown.driver), fontSize = 18.sp, color = C.sub)
                } else if (t.open != null) {
                    Text("${t.open.from.label}에서 출발 · 기록 중", fontSize = 26.sp, fontWeight = FontWeight.Black, color = C.title)
                }
                // 위 요약에 나온 최근 운행은 목록에서 다시 쓰지 않는다
                t.logs.drop(if (t.pending == null && shown != null) 1 else 0).take(3).forEach { log ->
                    HorizontalDivider(color = C.line)
                    Row(Modifier.fillMaxWidth().testTag("trip_log"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${log.date} · %.1f km".format(log.distKm), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                            // 출발지 점 ─── 도착지 핀
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(9.dp).clip(CircleShape).border(2.dp, C.action, CircleShape))
                                Text("${log.from}", fontSize = 15.sp, color = C.sub)
                                Box(Modifier.weight(1f).height(2.dp).background(C.action.copy(alpha = 0.5f)))
                                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = C.action)
                                Text("${log.to}", fontSize = 15.sp, color = C.sub)
                            }
                        }
                        // 목적별 색: 거래처 방문 = 남색, 출퇴근 = 하늘색, 그 밖 = 보라, 미분류 = 회색
                        val (tagBg, tagFg) = when (log.purpose) {
                            "" -> C.chip to C.sub
                            "거래처 방문" -> Color(0xFF0B2C4A) to Color.White
                            "출퇴근" -> (if (C.dark) Color(0xFF20364F) else Tone.blueSoft) to C.title
                            else -> (if (C.dark) Color(0xFF352D4C) else Tone.purpleSoft) to (if (C.dark) Color(0xFFD0BEFF) else Color(0xFF6B4FD0))
                        }
                        Text(log.purpose.ifEmpty { "미분류" }, Modifier.clip(RoundedCornerShape(50)).background(tagBg).padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tagFg)
                    }
                }
                Text("출발했다가 P단으로 10초 이상 서면 한 건이 기록돼요. 150m쯤보다 짧은 이동은 남기지 않아요.", fontSize = 15.sp, color = C.sub)
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
    Box(Modifier.fillMaxWidth().padding(horizontal = hPad(36.dp), vertical = 22.dp)) { Split(if (compact) 18.dp else 32.dp, center = true, first = { cell ->
        Column(cell, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("뒷좌석 상태", Modifier.semantics { heading() }, color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Wrap {
                listOf(
                    doorsClosed.takeUnless { u.unread("doors") } to ("뒷문 · " + u.read("doors", if (doorsClosed) "닫힘" else "열림")),
                    seated.takeUnless { u.unread(if (u.caps.row2Occupancy) "row2Occupancy" else "belt") } to ("탑승 · " + u.read(if (u.caps.row2Occupancy) "row2Occupancy" else "belt", if (seated) "감지됨" else "감지되지 않음")),
                    s.row2Belt.takeUnless { u.unread("belt") } to ("안전벨트 · " + u.read("belt", if (s.row2Belt) "착용" else "미착용")),
                ).forEach { (good, label) ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(if (good == true) { if (C.dark) Color(0xFF173E35) else Tone.greenSoft } else C.chip).padding(horizontal = 12.dp, vertical = 7.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "$label · " + when (good) { true -> "확인됨"; false -> "확인 필요"; null -> "신호 미수신" } },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(when (good) { true -> Icons.Outlined.CheckCircle; false -> Icons.Outlined.RemoveCircleOutline; null -> Icons.Outlined.HelpOutline }, null, Modifier.size(18.dp), tint = if (good == true) C.ok else C.sub)
                        Text(label, color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (e.phase != EscortPhase.OFF) Text(e.phase.label, color = if (e.phase == EscortPhase.WRONG_SIDE) C.danger else C.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            u.engine.ambient?.let { Text(it.note, color = if (lit) C.ok else C.warn, fontSize = 17.sp) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.card).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val last = e.timeline.lastOrNull()
                // 타임라인은 동행 기록이다. 보호자에게 실제로 가는 알림은 출발·하차 때뿐
                Text(last?.let { "동행 기록 · " + it.text } ?: "출발·하차 시 보호자에게 알리는 기능이에요.", color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (last != null) Column(Modifier.testTag("escort_timeline")) {
                    val steps = e.timeline.takeLast(4)
                    steps.forEachIndexed { i, step ->
                        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(Tone.mint), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White) }
                                if (i < steps.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(C.line))
                            }
                            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(VerdictEngine.hhmm(step.timeMs), Modifier.clip(RoundedCornerShape(6.dp)).background(C.bg).padding(horizontal = 6.dp, vertical = 1.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.title)
                                Text(step.text, fontSize = 16.sp, color = C.ink)
                            }
                        }
                    }
                }
                if (Consent.GUARDIAN !in u.settings.consents) Text("보호자 동의가 꺼져 있어 알림을 보내지 않아요.", color = C.warn, fontSize = 17.sp)
            }
            if (e.phase == EscortPhase.OFF) BigButton("부모님 동행 시작", Modifier.fillMaxWidth()) { vm.act { _, now -> startEscort(now); emptyList() } }
            else BigButton(if (e.phase == EscortPhase.ALIGHTED) "확인" else "동행 끝내기", Modifier.fillMaxWidth()) { vm.act { _, _ -> stopEscort(); emptyList() } }
        }
    }, second = { cell ->
        EscortLightArtwork(u, cell)
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
                    Text("재출발 최고 속도", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
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
                Row(Modifier.testTag("school_rewards"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("30km/h 이하 연속 출발", "${z.streak}회", Modifier.weight(1f), valueColor = C.ok, icon = Icons.Outlined.EmojiEvents, iconBg = if (C.dark) Color(0xFF173E35) else Tone.greenSoft, iconTint = C.ok)
                    StatTile("포인트", "%,dP".format(z.points), Modifier.weight(1f), valueColor = C.title, icon = Icons.Outlined.Paid, iconBg = if (C.dark) Color(0xFF20364F) else Tone.blueSoft)
                }
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
        Card(Modifier.fillMaxWidth().testTag("ride_graph"), padding = 20.dp) {
            val events = c.brakes + c.accels
            CardHeader(null, "승차감 그래프") {
                Pill(if (!c.active) "대기 중" else if (events == 0) "편안함 유지 중" else "흔들림 ${events}회", if (!c.active) null else if (events == 0) Level.OK else Level.WARN)
            }
            Text(if (c.active) "2열 착석·등받이 상태 확인됨" else "2열에 손님이 앉으면 기록을 시작해요", fontSize = 16.sp, color = C.sub)
            // 급감속은 아래로, 급가속은 위로 튀는 선. 횟수를 그림으로 보여 줄 뿐 시간별 기록은 아니다
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(if (isCompact()) 196.dp else 240.dp)) {
                val mid = size.height * 0.5f
                val marks = List(c.brakes.coerceAtMost(8)) { 1f } + List(c.accels.coerceAtMost(8)) { -1f }
                val n = 60
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(0f, mid) }
                for (i in 1..n) {
                    val x = size.width * i / n
                    // 흔들림이 없으면 평평한 선(측정하지 않은 물결은 그리지 않는다)
                    var y = mid
                    marks.forEachIndexed { k, dir ->
                        val center = (k + 1).toFloat() * n / (marks.size + 1)
                        val distance = (i - center) / 2.2f
                        y += dir * size.height * 0.35f * kotlin.math.exp(-distance * distance / 2f)
                    }
                    path.lineTo(x, y)
                }
                val area = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                // 눈금선과 급가속·급감속 기준선(점선)
                for (g in 1..3) drawLine(Color(0x22000000), androidx.compose.ui.geometry.Offset(0f, size.height * g / 4), androidx.compose.ui.geometry.Offset(size.width, size.height * g / 4), strokeWidth = 1.5f)
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                listOf(mid - size.height * 0.3f, mid + size.height * 0.3f).forEach { y ->
                    drawLine(Tone.orange.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 2.5f, pathEffect = dash)
                }
                drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x662F7BE0), Color(0x222FD3A6))))
                drawPath(path, if (marks.isEmpty()) Color(0xFF2F7BE0) else Tone.orange, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
            }
            HorizontalDivider(color = C.line)
            Text("급감속 ${c.brakes}회 · 급가속 ${c.accels}회", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            Text(if (!c.active) "운행 대기 중" else if (events == 0) "편안함 유지 중 · 급감속 0회" else "부드럽게 출발하고 멈춰 주세요",
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (C.dark) Color(0xFF174B3B) else Color(0xFFCDF6DD)).padding(12.dp),
                color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("급감속은 아래로, 급가속은 위로 한 번마다 표시해요. 시간순 기록이 아니라 횟수예요.", fontSize = 14.sp, color = C.sub)
        }
        Text("급감속 순간에는 음성으로 바로 알려 드려요.", Modifier.fillMaxWidth(), fontSize = 16.sp, color = C.sub, textAlign = TextAlign.Center)
        Card(Modifier.fillMaxWidth(), padding = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("등받이 센서가 없는 차:\n쇼퍼 모드 직접 켜기", Modifier.weight(1f), fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = C.ink)
                NavySwitch(c.manual, "쇼퍼 모드 직접 켜기") { on -> vm.act { _, _ -> setChauffeurManual(on); emptyList() } }
            }
        }
        if (!u.caps.row2Recline) Note("이 차는 2열 등받이 각도를 읽을 수 없어 수동으로 켜야 해요.", Level.WARN)
        // 빈 아래쪽에 이번 주 운전 습관(인사이트 기록)을 이어 보여 준다
        DrivingHabitTip(u)
    }
}

@Composable
fun TrunkScreen(vm: VerifiedRuntime, u: UiState) {
    val k = u.engine.trunk
    var showExitDetails by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = if (isCompact()) 16.dp else 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val x = u.engine.exit
        val f = u.engine.floor
        Split(22.dp, first = { cell -> Card(cell) {
                val checks = exitChecks(u)
                val bad = checks.count { it.bad }
                val unknown = checks.count { it.unread }
                @Composable fun ExitResult() {
                    Pill(if (bad > 0) "확인 필요 ${bad}건" else if (unknown > 0) "확인 불가 ${unknown}건" else "모두 확인", if (bad > 0) Level.WARN else if (unknown > 0) Level.INFO else Level.OK)
                }
                if (isCompact() && androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("하차 3초 점검", Modifier.weight(1f).semantics { heading() }, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        ExitResult()
                    }
                    Text("P단에서 운전석 문을 열면 확인해요.", fontSize = 16.sp, color = C.sub)
                } else CardHeader(null, "하차 3초 점검", "P단에서 운전석 문을 열면 확인해요.") { ExitResult() }
                u.settings.carModel?.let { model ->
                    ExitPhotoStage(model, checks, Modifier.fillMaxWidth())
                }
                if (isCompact()) {
                    val light = checks.first { it.label == "실내등" }
                    val lightValue = if (light.unread) "확인 불가" else if (light.bad) light.word else "꺼짐"
                    Row(Modifier.fillMaxWidth().testTag("exit_details_toggle")
                        .clickable(role = Role.Button) { showExitDetails = !showExitDetails }.heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("실내등 $lightValue", Modifier.weight(1f), fontSize = 17.sp, color = if (light.unread) C.sub else if (light.bad) C.warn else C.ink)
                        Text(if (showExitDetails) "상세 접기" else "상세 확인", fontSize = 15.sp, color = C.sub)
                        Icon(if (showExitDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp), tint = C.sub)
                    }
                }
                if (!isCompact() || showExitDetails) Column(Modifier.testTag("exit_tiles"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    checks.chunked(2).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { c ->
                                val value = if (c.unread) "확인 불가" else if (c.bad) c.word else if (c.word == "열림") "닫힘" else "꺼짐"
                                CheckTile(if (c.unread) null else !c.bad, c.label, value, Modifier.weight(1f))
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                when {
                    x.lockReminded -> Note("내린 뒤 1분 동안 문이 잠기지 않았어요. 전송 상태는 ‘보낸 알림’에서 확인해 주세요.", Level.WARN)
                    x.unlockedSinceMs != null -> Note("내린 뒤 문이 아직 잠기지 않았어요. 1분이 지나면 폰으로 알려요.", Level.INFO)
                }
            } }, second = { cell -> Column(cell, verticalArrangement = Arrangement.spacedBy(22.dp)) { Card(Modifier.fillMaxWidth()) {
                val floorHint = "GPS가 끊긴 뒤 램프를 돈 횟수로 층을 추정해요. 맞는 층을 고르면 주차 위치를 기억해요."
                CardHeader(null, "지하 주차층", if (isCompact() && f.confirmed != null) null else floorHint) {
                    when {
                        f.confirmed != null -> Pill("기억함", Level.OK)
                        f.estimate != null -> Pill("확인해 주세요", Level.WARN)
                        f.tracking -> Pill("추적 중", Level.INFO)
                        else -> {}
                    }
                }
                when {
                    f.confirmed == 2 -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${f.place.label} 지하 2층", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Image(painterResource(R.drawable.reference_parking_b2), null,
                            Modifier.weight(0.95f).heightIn(max = 160.dp).aspectRatio(1f).testTag("parking_floor_artwork"), contentScale = ContentScale.Fit)
                    }
                    f.confirmed != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.lift(RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Tone.navy).padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("B${f.confirmed}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Text("지하층", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        Text("${f.place.label} 지하 ${f.confirmed}층", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    }
                    f.estimate != null -> {
                        Text("지하 ${f.estimate}층으로 추정돼요 (램프 ${f.turns}회)", fontSize = 26.sp, fontWeight = FontWeight.Black, color = C.ink)
                        Text("맞는 층을 눌러 주세요", fontSize = 17.sp, color = C.sub)
                        Wrap { (1..5).forEach { n -> Chip("B$n", n == f.estimate, big = true) { vm.act { _, _ -> confirmFloor(n) } } } }
                    }
                    f.tracking -> Text("지하로 들어왔어요 · 램프 ${f.turns}회", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    else -> Text("지하주차장에 들어가면 자동으로 시작해요", fontSize = 20.sp, color = C.sub)
                }
                if (isCompact() && f.confirmed != null) Text(floorHint, fontSize = 14.sp, color = C.sub)
                // 자동 추정에 직접 단서(구역·기둥 번호, 사진)를 붙인다
                ParkingNoteCard(vm, u)
            }
            // 트렁크 짐 카드는 오른쪽 단에 함께 두어(차 화면) 아래로 밀려나 스크롤하지 않게 한다
            Card(Modifier.fillMaxWidth(), padding = 28.dp) {
            run {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
            }
        }
            } })
    }
}

// ------------------------------------------------------------------ ⑧ ⑨ (현대8)
private val shortFinding = mapOf("R01" to "크루즈 해제", "R02" to "와이퍼 AUTO 모드", "R03" to "내기순환 켜짐", "R04" to "주행모드 실내등", "R05" to "열린 곳 있음", "R06" to "워셔액 부족", "R07" to "벨트 풀림")

@Composable
fun DiagScreen(vm: VerifiedRuntime, u: UiState, diag: List<DiagFinding>, go: (String) -> Unit) {
    var detail by rememberSaveable { mutableStateOf(false) }
    val settingsFound = diag.filter { it.code !in setOf("R99", "R06") }
    val m = u.engine.missions
    val done = m.items.filter { it.done }
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(Modifier.fillMaxWidth(), padding = 0.dp) { Box(Modifier.padding(horizontal = if (isCompact()) 18.dp else 24.dp, vertical = 8.dp)) { Split(if (isCompact()) 8.dp else 0.dp, w1 = 0f, center = true, first = { cell ->
            u.settings.carModel?.let { CarPhoto(it, (if (isCompact()) cell else Modifier).width(220.dp).height(100.dp)) }
        }, second = { cell -> Column(cell.padding(start = if (isCompact()) 0.dp else 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("내 차 설정 확인", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink)
                }
                Text("주행거리 " + u.read("odometer", "%.1fkm".format(u.signals.odometerKm)), fontSize = 16.sp, color = C.sub)
            } }) } }
        Column(Modifier.fillMaxWidth().lift(RoundedCornerShape(24.dp), 7.dp).clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF06233B), Color(0xFF14375C))))) {
            Column(Modifier.padding(if (isCompact()) 22.dp else 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Troubleshoot, null, Modifier.size(28.dp), tint = Color(0xFF9CCBF7))
                    Text("이거 고장이야? 확인 도우미", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC8D6E7))
                }
                Text(if (settingsFound.isNotEmpty()) "설정 때문일 수 있어요" else "설정에서는 원인을 못 찾았어요",
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (settingsFound.isNotEmpty()) "(" + settingsFound.joinToString(" · ") { shortFinding[it.code] ?: it.title } + ")"
                    else "경고등이 켜졌거나 계속 이상하면 블루핸즈 점검을 받으세요",
                    fontSize = 20.sp, color = Color(0xFFDFE7EF))
            }
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.12f)).clickable(role = Role.Button) { detail = !detail }
                .heightIn(min = 58.dp).padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("관련 설정 ${settingsFound.size}개", Modifier.weight(1f), color = Color(0xFFDCE7F2), fontSize = 18.sp)
                Text(if (detail) "접기" else "자세히 보기", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
            }
        }
        if (detail) Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            diag.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider(color = C.line)
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
        }
        ProjectionHelpCard()
        if (u.signals.washerLow && !u.unread("washer")) Card(Modifier.fillMaxWidth(), padding = 18.dp, onClick = { go("SERVICE") }) {
            Text("워셔액 부족 감지", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.warn)
            Text("차량 관리에서 정비 예약 보기", fontSize = 17.sp, color = C.title)
        }
        Row(Modifier.fillMaxWidth().testTag("diag_book").lift().clip(RoundedCornerShape(16.dp)).background(Tone.navyBlue).clickable(role = Role.Button) { go("SERVICE") }
            .heightIn(min = 64.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Build, null, Modifier.size(26.dp), tint = Color.White)
            Text("블루핸즈 점검 예약", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
        }
        Text(DIAG_DISCLAIMER, fontSize = 14.sp, color = C.sub)
    }
}

@Composable
fun MissionScreen(vm: VerifiedRuntime, u: UiState) {
    val m = u.engine.missions
    val done = m.items.count { it.done }
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        val nholdOn = u.engine.nhold.phase != NHoldPhase.OFF
        if (nholdOn) NHoldCard(vm, u)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val pct = done * 100 / m.items.size.coerceAtLeast(1)
            ArcGauge(done / m.items.size.coerceAtLeast(1).toFloat(), if (isCompact()) 128.dp else 170.dp, stroke = 14.dp,
                color = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF04CE6A), Color(0xFF00B968)))) {
                Text("$pct%", fontSize = if (isCompact()) 34.sp else 44.sp, fontWeight = FontWeight.Black, color = C.ink)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("실제로 스위치를 조작하면\n차 신호로 확인해요", fontSize = if (isCompact()) 20.sp else 28.sp,
                    lineHeight = if (isCompact()) 28.sp else 36.sp, fontWeight = FontWeight.Bold, color = C.ink)
                GradientProgress(done / m.items.size.coerceAtLeast(1).toFloat(), Modifier.fillMaxWidth())
                Text("미션 진행률 · ${m.items.size}개 중 ${done}개", Modifier.testTag("mission_progress"), fontSize = 15.sp, color = C.sub)
            }
        }
        if (!m.active) BigButton("미션 시작", Modifier.fillMaxWidth()) { vm.act { _, now -> startMissions(now); emptyList() } }
        val cols = if (isCompact()) 1 else 2
        val current = m.items.indexOfFirst { !it.done }
        Column(Modifier.fillMaxWidth().testTag("mission_list"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            m.items.withIndex().chunked(cols).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { (i, it) ->
                        val shape = RoundedCornerShape(18.dp)
                        val next = m.active && i == current
                        val ink = if (it.done) Color(0xFF002F21) else C.ink
                        Row(Modifier.weight(1f).lift(shape, if (it.done) 5.dp else 2.dp).clip(shape)
                            .background(if (it.done) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF0BD66B), Color(0xFF00C86C))) else C.surface)
                            .then(if (next) Modifier.border(2.dp, C.title, shape) else Modifier)
                            .heightIn(min = 94.dp).padding(horizontal = 18.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("${i + 1}", fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = ink)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(it.title, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, color = ink)
                                Text(if (it.done) "확인 완료 ✓ " + (it.elapsedSec?.let { seconds -> "${seconds}초 만에 성공" } ?: "차 신호 확인") else it.guide,
                                    fontSize = 16.sp, lineHeight = 23.sp, color = if (it.done) ink else C.sub)
                            }
                            if (it.done) Icon(Icons.Outlined.Check, "확인 완료", Modifier.padding(top = 6.dp).size(28.dp), tint = ink)
                        }
                    }
                    if (rowItems.size < cols) Spacer(Modifier.weight(1f))
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
    Column(Modifier.padding(horizontal = hPad(36.dp), vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Card(Modifier.fillMaxWidth(), padding = 22.dp, onClick = openPhoneSettings) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (u.phoneTopic == null) "차주 휴대폰 알림 연결하기" else "차주 휴대폰 알림 연결 설정", Modifier.weight(1f), fontSize = 22.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold, color = C.ink)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(24.dp), tint = C.ink)
            }
        }
        if (!u.online && u.phoneTopic != null) Note("네트워크 연결을 기다리는 중이에요. 전송 대기 중인 알림은 연결이 돌아오면 다시 시도해요.", Level.WARN)
        if (u.notices.isEmpty()) Text("아직 보낸 알림이 없어요.", color = C.sub, fontSize = 18.sp)
        Column {
            u.notices.forEachIndexed { i, n ->
                val (icon, tone) = when {
                    "성공" in n.title -> Icons.Outlined.Check to Color(0xFF079D68)
                    n.level == Level.WARN || n.level == Level.DANGER || "급감속 0회" !in n.body && "급감속" in n.body && "리포트" in n.title -> Icons.Outlined.PriorityHigh to Color(0xFFE9892B)
                    "리포트" in n.title -> Icons.Outlined.DirectionsCar to Color(0xFF237DDF)
                    "완료" in n.title -> Icons.Outlined.Check to Color(0xFF237DDF)
                    else -> Icons.Outlined.Shield to C.navy
                }
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).testTag("notice_item"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        IconBubble(icon, 34.dp, Color.White, tone)
                        if (i < u.notices.lastIndex) Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(tone.copy(alpha = 0.85f)))
                    }
                    Column(Modifier.weight(1f).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${n.target} · ${VerdictEngine.hhmm(n.timeMs)}", Modifier.align(Alignment.End).clip(RoundedCornerShape(50)).background(C.chip).padding(horizontal = 9.dp, vertical = 4.dp),
                            fontSize = 13.sp, color = C.sub)
                        Text(n.title.replace(Regex("^[①-⑩]\\s*"), ""), fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp,
                            color = if ("성공" in n.title) C.ok else C.ink)
                        Text(n.body, color = C.ink, fontSize = 17.sp, lineHeight = 25.sp, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        n.phoneStatus.displayText().takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 14.sp, color = C.sub) }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: VerifiedRuntime, u: UiState) {
    val st = u.settings
    // 한 번만 보여 주는 복구 코드: 회전으로 오른쪽 칸이 탭 뒤로 숨거나 주행·화면 재생성으로 화면이 다시 그려져도 차주가 닫을 때까지 띄운다
    var recovery by rememberSaveable { mutableStateOf<String?>(null) }
    // PIN이 꺼졌으면(복구 코드로 끈 경우 포함) 그 PIN의 복구 코드는 다시 띄우지 않는다
    if (!u.ownerPinSet && recovery != null) recovery = null
    recovery?.let { code -> RecoveryCodeDialog(code) { recovery = null } }
    TwoUp(36.dp, 28.dp, 24.dp, 18.dp, rightTop = if (isCompact()) 0.dp else 56.dp, tabs = "기능·동의" to "내 차·보안", left = {
            Text("설정", fontSize = 32.sp, fontWeight = FontWeight.Black, color = C.title)
            TextRow("차주 휴대폰 알림", if (u.phoneTopic == null) "연결하기" else "연결 설정") { vm.navigate.value = "PHONE_LINK" }
            Accordion("생활 유형 (홈에 올릴 기능)", "set_persona", Icons.Outlined.Groups, "${st.personas.size}개 선택됨") {
                Wrap { Persona.entries.forEach { p -> Chip(p.label, p in st.personas) { vm.settings { it.copy(personas = if (p in it.personas) it.personas - p else it.personas + p) } } } }
            }
            Accordion("기능 하나씩 추가", "set_modules", Icons.Outlined.Extension, "${st.modules.size}개 켜짐") {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("원격 시동 알림", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
                        Text("휴대폰 진동", fontSize = 16.sp, color = C.sub)
                    }
                    Box(Modifier.testTag("start_haptic")) { NavySwitch(u.settings.startHaptic, "휴대폰 진동") { on -> vm.settings { it.copy(startHaptic = on) } } }
                }
                Text("미리 공조가 켜지면 소리 없이 진동으로 알려요. 내연기관은 짧은 떨림, 전기차는 부드럽게 한 번이에요. 진동 장치가 없는 차 화면에서는 울리지 않아요.", fontSize = 15.sp, color = C.sub)
            }
            Accordion("차량 지원 기능", "set_caps", Icons.Outlined.DirectionsCar) {
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
            Accordion("이 차에서 읽는 신호", "set_signals", Icons.Outlined.Sensors) {
                if (u.simAllowed) Text("시연용 신호를 사용할 수 있어요.", fontSize = 16.sp, color = C.sub)
                else {
                    val (ok, no) = SIGNAL_LABELS.entries.partition { !u.unread(it.key) }
                    Text("읽음 ${ok.size}개" + if (ok.isEmpty()) "" else " · " + ok.joinToString("·") { it.value }, fontSize = 16.sp, color = C.ink)
                    Text("확인 불가 ${no.size}개" + if (no.isEmpty()) "" else " · " + no.joinToString("·") { it.value }, fontSize = 16.sp, color = C.sub)
                }
            }
            Accordion("승인 기능 · 사람별 동의", "set_consent", Icons.Outlined.VerifiedUser, "${st.flags.size + st.consents.size}개 켜짐") {
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
            OwnerPinSettings(vm, u) { recovery = it }
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
                var date by rememberSaveable(st.policyEndDate) { mutableStateOf(st.policyEndDate) }
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

/** 제목을 누르면 펼치고 접는 설정 묶음. 폰에서는 접어 두고, 넓은 차 화면에서는 펼쳐 둔다. */
@Composable
private fun Accordion(title: String, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, summary: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val wide = !isCompact()
    var open by rememberSaveable { mutableStateOf(wide) }
    Panel {
        Row(Modifier.fillMaxWidth().testTag(tag).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = if (open) "접기" else "펼치기") { open = !open }.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBubble(icon, 38.dp)
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
            if (summary != null) Text(summary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.action, maxLines = 1, softWrap = false)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.sub)
        }
        if (open) content()
    }
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
        selected?.let { model ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tone.studio).padding(12.dp)) {
                CarPhoto(model, Modifier.fillMaxWidth(0.92f).heightIn(max = 200.dp))
                Text(if (model == CarModel.OTHER) "공식 사진 준비 중" else "공식 사진 · 연식·색상·트림은 다를 수 있어요", fontSize = 15.sp, color = C.sub)
            }
        }
        if (selected == null) Text("차종을 고르면 사용할 수 있는 기능을 알려 드려요.", fontSize = 17.sp, color = C.sub)
        else {
            Text("이 차에서 쓸 수 있는 기능", Modifier.testTag("car_features"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = C.ink)
            val features = (if (selected.controllable) listOf(Icons.Outlined.Thermostat to "출발 준비", Icons.Outlined.AirlineSeatReclineExtra to "시트 복원", Icons.Outlined.Lightbulb to "하차 조명") else emptyList()) +
                listOf(Icons.Outlined.LocalParking to "대리 확인", Icons.Outlined.Description to "맡김 기록", Icons.Outlined.Receipt to "운행 기록")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                features.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { (icon, label) ->
                            Column(Modifier.weight(1f).lift(RoundedCornerShape(14.dp), 3.dp).clip(RoundedCornerShape(14.dp)).background(C.bg).padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconBubble(icon, 42.dp)
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ ⑩ 출발 준비
@Composable
fun ComfortScreen(vm: VerifiedRuntime, u: UiState) {
    val c = u.engine.comfort
    val st = u.settings
    var who by rememberSaveable { mutableStateOf(st.comfort.keys.firstOrNull() ?: "차주") }
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
        top = true,
        left = {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(C.chip).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                st.comfort.keys.forEach { name ->
                    val selected = name == who
                    Box(Modifier.weight(1f).shadow(if (selected) 5.dp else 0.dp, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50)).background(if (selected) C.navy else Color.Transparent)
                        .selectable(selected, role = Role.Tab) { who = name }.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("$name 설정", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else C.sub)
                    }
                }
            }
            Wrap {
                ComfortPreset.PRESETS.forEach { p ->
                    Chip("${p.title} · %.0f℃".format(p.tempC), preset == p) { save(p) }
                }
            }
            // 열선과 통풍은 같은 좌석에서 동시에 켜지지 않으므로 하나를 켜면 다른 하나를 끈다
            if (u.caps.seatHeat) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Tone.studio), contentAlignment = Alignment.Center) {
                        ComfortPerspectiveArtwork(preset.driverHeat, preset.passengerHeat, preset.rearHeat, Modifier.fillMaxWidth().widthIn(max = 620.dp),
                            onDriver = { val n = (preset.driverHeat + 1) % 4; save(preset.copy(driverHeat = n, driverVent = if (n > 0) 0 else preset.driverVent)) },
                            onPassenger = { save(preset.copy(passengerHeat = (preset.passengerHeat + 1) % 4)) },
                            onRear = { save(preset.copy(rearHeat = (preset.rearHeat + 1) % 4)) })
                    }
                } else Text("좌석 열선 (미지원)", fontSize = 18.sp, color = C.sub)
            Card(Modifier.fillMaxWidth(), padding = 22.dp) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stackTemperature = maxWidth < 300.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
                    @Composable fun TemperatureControls() = Row(verticalAlignment = Alignment.CenterVertically) {
                        BigButton("−", Modifier.semantics { contentDescription = "온도 1도 낮추기" }, style = BtnStyle.TONAL, enabled = preset.tempC > 16f, fontSize = 22.sp) { save(preset.copy(tempC = (preset.tempC - 1).coerceAtLeast(16f))) }
                        Text("  %.0f℃  ".format(preset.tempC), fontSize = 30.sp, fontWeight = FontWeight.Black, color = C.title)
                        BigButton("+", Modifier.semantics { contentDescription = "온도 1도 높이기" }, style = BtnStyle.TONAL, enabled = preset.tempC < 30f, fontSize = 22.sp) { save(preset.copy(tempC = (preset.tempC + 1).coerceAtMost(30f))) }
                    }
                    if (stackTemperature) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("실내 온도", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        TemperatureControls()
                    } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("실내 온도", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        TemperatureControls()
                    }
                }
                Level4("운전석 통풍", preset.driverVent, u.caps.seatVent) { save(preset.copy(driverVent = it, driverHeat = if (it > 0) 0 else preset.driverHeat)) }
                HorizontalDivider(color = C.line.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("핸들 열선" + if (!u.caps.steeringHeat) " (미지원)" else "", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                    if (u.caps.steeringHeat) NavySwitch(preset.steeringHeat, "핸들 열선") { save(preset.copy(steeringHeat = it)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("앞 유리 성에 제거", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                    NavySwitch(preset.defrost, "앞 유리 성에 제거") { save(preset.copy(defrost = it)) }
                }
                if (u.caps.seatHeat) Text("좌석을 누르면 열선 끔 → 1단 → 2단 → 3단", fontSize = 15.sp, color = C.sub)
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
                    "시동이 꺼진 차는 화면도 꺼져 있어 원격 실행은 마이현대 원격 공조로 넘깁니다.",
                fontSize = 17.sp, color = C.sub,
            )
        },
    )
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun Level4(label: String, level: Int, supported: Boolean, set: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label + if (!supported) " (미지원)" else "", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (supported) C.ink else C.sub)
            if (supported) Text(if (level == 0) "끔" else "${level}단", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.title)
        }
        if (supported) androidx.compose.material3.Slider(value = level.toFloat(), onValueChange = { set(kotlin.math.round(it).toInt().coerceIn(0, 3)) },
            valueRange = 0f..3f, steps = 2, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            thumb = {
                Box(Modifier.size(28.dp).shadow(5.dp, CircleShape).background(Color.White, CircleShape)
                    .border(1.dp, C.line, CircleShape))
            },
            track = {
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                    val start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f)
                    val end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f)
                    drawLine(C.line, start, end, size.height, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    if (level > 0) drawLine(C.navy, start, end.copy(x = size.width * level / 3f), size.height,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            })
    }
}
