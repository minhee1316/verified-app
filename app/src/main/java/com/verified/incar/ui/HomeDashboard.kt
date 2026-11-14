package com.verified.incar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.verified.incar.R
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.CarModel
import com.verified.incar.core.HandKind
import com.verified.incar.core.Level
import com.verified.incar.core.Module
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.VerdictEngine

@Composable
fun HomeMH(vm: VerifiedRuntime, u: UiState, modules: List<Module>, go: (String) -> Unit) {
    val status = u.homeStatus()
    if (isCompact()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (u.trial) TrialPanel(vm, u, go)
            CarHero(vm, u, go)
            HomeList(u, status, go)
            if (u.trialAvailable) TrialEntry(go)
            if (u.simAllowed && !u.trial) DemoPreviews(go)
        }
    } else {
        TwoUp(28.dp, 20.dp, 20.dp, 16.dp, leftWeight = 1f, left = {
            if (u.trial) TrialPanel(vm, u, go)
            CarHero(vm, u, go)
            if (u.trialAvailable) TrialEntry(go)
            if (u.simAllowed && !u.trial) DemoPreviews(go)
        }, right = {
            HomeList(u, status, go)
        })
    }
}

/** 맨 위: 차 이름 · 배터리 · 주차 상태 → 차 사진 → 빠른 제어 4개. */
@Composable
private fun CarHero(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    val model = u.settings.carModel
    val s = u.signals
    Column(Modifier.fillMaxWidth().testTag("home_hero").padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model?.label ?: "내 차", color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                val state = when {
                    s.uxRestricted -> "주행 중"
                    s.gear == com.verified.incar.core.Gear.P -> "주차됨"
                    else -> "${s.gear}단"
                }
                Text("배터리·연료 ${u.homeReading("soc", "${s.socPct}%")} · ${u.read("gear", state)}", color = C.sub, fontSize = 16.sp)
            }
            if (model == null) HomeActionText("차량 등록") { go("CAR_SETUP") }
        }
        if (model != null) Box(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "외관·실내 사진 보기") { go("VEHICLE_VIEW") },
            contentAlignment = Alignment.Center) {
            CarPhoto(model, Modifier.fillMaxWidth(0.95f).heightIn(max = 200.dp))
        }
        QuickControls(vm, u)
    }
}

/** 기능 목록: 아이콘 · 이름 · 현재 값 · ›. 차량 → 맡기고 돌려받기 → 기록과 관리 → 더 보기 순서. */
@Composable
private fun HomeList(u: UiState, status: HomeStatus, go: (String) -> Unit) {
    val s = u.signals
    val valet = u.engine.valet
    val lend = u.engine.lend
    Card(Modifier.fillMaxWidth().testTag("home_list"), padding = 12.dp) {
        if (valet.active) {
            val action = when (valet.phase) {
                ValetPhase.CONSENT -> "기사 동의하기"
                ValetPhase.COMPLETE, ValetPhase.INCOMPLETE -> "결과 확인하고 돌려받기"
                else -> "진행 상황 보기"
            }
            ActiveHandover("대리 주차 · ${valet.phase.label}", valet.handover?.timeMs, action) { go("VALET") }
        }
        if (lend.active) {
            val service = lend.kind == HandKind.SERVICE
            ActiveHandover(if (service) "정비 맡김 진행 중" else "빌려주기 진행 중", lend.start?.timeMs, "반납 확인하기") {
                go(if (service) "HAND_SERVICE" else "HAND_LEND")
            }
        }
        status.alerts.take(3).forEach { alert ->
            ListItem(Icons.Outlined.Warning, alert.name, alert.value, Modifier.testTag("home_alert_${alert.id}"), warn = true) { go(alert.route) }
        }
        val verdict = status.verdict
        ListItem(Icons.Outlined.DirectionsCar, "컨트롤", "위에서 보며 문·트렁크·좌석 확인", Modifier.testTag("home_control")) { go("CONTROL") }
        ListItem(Icons.Outlined.Thermostat, "실내 온도",
            "설정 ${u.read("hvacTemp", "%.1f℃".format(s.hvacTempC))} · 공조 ${u.read("hvacPower", if (s.hvacPowerOn) "켜짐" else "꺼짐")}") { go("CONTROL") }
        ListItem(if (verdict.level == Level.OK) Icons.Outlined.CheckCircle else Icons.Outlined.Warning, "차 상태", verdict.word,
            Modifier.testTag("home_verdict"), warn = verdict.level == Level.WARN || verdict.level == Level.DANGER) { go(verdict.tab ?: "VEHICLE_VIEW") }
        ListSection("맡기고 돌려받기")
        if (!valet.active && !lend.active) {
            ListItem(Icons.Outlined.LocalParking, "대리 맡기기", "집 앞 P단·시동 OFF로 완료 확인", Modifier.testTag("home_hand_valet")) { go("VALET") }
            ListItem(Icons.Outlined.Build, "정비 맡기기", "맡길 때와 돌려받을 때 비교", Modifier.testTag("home_hand_service")) { go("HAND_SERVICE") }
            ListItem(Icons.Outlined.Key, "빌려주기", "시동·문 열림·급감속 기록", Modifier.testTag("home_hand_lend")) { go("HAND_LEND") }
        }
        ListItem(Icons.Outlined.Description, "맡김 이력서", "지난 기록 모아 보기", Modifier.testTag("home_hand_history")) { go("HISTORY") }
        ListSection("더 보기")
        ListItem(Icons.Outlined.Explore, "상황별 기능", "부축 동행·스쿨존·첫 운전 등") { go("VERIFIED") }
        ListItem(Icons.Outlined.Search, "기능·증상 검색", null, Modifier.testTag("home_search")) { go("SEARCH") }
    }
}

@Composable
private fun ListSection(title: String) {
    HorizontalDivider(Modifier.padding(top = 6.dp), color = C.line)
    Text(title, Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp), color = C.sub, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ListItem(icon: ImageVector, title: String, sub: String?, modifier: Modifier = Modifier, warn: Boolean = false, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (warn) C.warn else C.ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = if (warn) C.warn else C.sub, fontSize = 15.sp)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(22.dp), tint = C.sub)
    }
}

/** 예시 값으로만 움직이는 화면들이라 체험·시연 모드에서만 연다. */
@Composable
private fun DemoPreviews(go: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("demo_previews"), padding = 16.dp) {
        Text("기능 미리보기", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
        HomeLink("전기차 경로", null, Icons.Outlined.EvStation, Modifier.testTag("demo_EV_ROUTE")) { go("EV_ROUTE") }
        HomeLink("발레 감시", null, Icons.Outlined.LocalParking, Modifier.testTag("demo_VALET_WATCH")) { go("VALET_WATCH") }
        HomeLink("무선 연결 진단", null, Icons.Outlined.Wifi, Modifier.testTag("demo_RADIO_DIAG")) { go("RADIO_DIAG") }
    }
}

/** 차에 연결되지 않은 휴대폰에서 예시 차량 체험으로 들어가는 카드. */
@Composable
private fun TrialEntry(go: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("trial_entry"), padding = 16.dp) {
        Text("예시 차량으로 체험하기", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Text("차가 없어도 대리·정비·빌려주기 과정을 예시 값으로 볼 수 있어요. 체험 기록은 실제 기록과 따로 저장돼요.", fontSize = 17.sp, color = C.sub)
        BigButton("차종 고르고 체험 시작", Modifier.fillMaxWidth(), BtnStyle.OUTLINE, fontSize = 19.sp) { go("TRIAL") }
    }
}

/** 체험 모드 홈 맨 위: 고른 예시 차량과 자동 재생 시나리오. 진행 중인 맡김이 있으면 끝까지 보게 한다. */
@Composable
private fun TrialPanel(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    val busy = u.engine.valet.active || u.engine.lend.active
    Card(Modifier.fillMaxWidth().testTag("trial_panel"), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("체험 모드", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("예시 차량 · ${u.settings.carModel?.label ?: "차종 미선택"}", fontSize = 16.sp, color = C.sub)
            }
            HomeActionText("체험 끝내기", Modifier.testTag("trial_end")) { go("TRIAL") }
        }
        Text(if (busy) "진행 중인 과정을 끝까지 본 뒤 다른 과정을 볼 수 있어요." else "누르면 출발부터 도착까지 자동으로 진행돼요.", fontSize = 17.sp, color = C.sub)
        listOf(
            Triple("대리 맡기기 보기", "valet", Module.VALET.name),
            Triple("정비 맡기기 보기", "service", "HAND_SERVICE"),
            Triple("빌려주기 보기", "lend", "HAND_LEND"),
        ).forEach { (label, scenario, route) ->
            BigButton(label, Modifier.fillMaxWidth().testTag("trial_$scenario"), BtnStyle.TONAL, enabled = !busy, fontSize = 19.sp) {
                vm.runScenario(scenario)
                go(route)
            }
        }
        if (u.settings.carModel?.ev == true) BigButton("전기차 장거리 보기", Modifier.fillMaxWidth().testTag("trial_ev"), BtnStyle.TONAL, enabled = !busy, fontSize = 19.sp) {
            vm.runScenario("ev_trip")
        }
    }
}

@Composable
private fun ActiveHandover(label: String, start: Long?, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("home_active").clip(RoundedCornerShape(10.dp)).background(C.chip)
        .clickable(role = Role.Button, onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listOfNotNull(label, start?.let { "${VerdictEngine.hhmm(it)} 시작" }).joinToString(" · "), color = C.sub, fontSize = 16.sp)
            Text(action, color = C.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.title)
    }
}

@Composable
private fun HomeActionText(label: String, modifier: Modifier = Modifier, action: () -> Unit) {
    Row(modifier.clickable(role = Role.Button, onClick = action).heightIn(min = 48.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(18.dp), tint = C.title)
    }
}

@Composable
private fun HomeLink(label: String, value: String?, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = C.title)
        Text(label, Modifier.weight(1f), color = C.ink, fontSize = 19.sp)
        if (value != null) Text(value, Modifier.weight(0.85f), color = C.sub, fontSize = 16.sp, textAlign = TextAlign.End)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = C.sub)
    }
}
