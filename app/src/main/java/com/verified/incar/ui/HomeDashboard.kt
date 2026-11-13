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
import androidx.compose.material.icons.outlined._3dRotation
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
            HandoverPanel(u, go)
            VehicleOverview(u, status, go)
            if (u.trialAvailable) TrialEntry(go)
            if (u.simAllowed) DemoPreviews(go)
            HomeFeatures(u, modules, go)
        }
    } else {
        TwoUp(28.dp, 20.dp, 20.dp, 16.dp, leftWeight = 1.05f, left = {
            if (u.trial) TrialPanel(vm, u, go)
            HandoverPanel(u, go)
            HomeFeatures(u, modules, go)
        }, right = {
            VehicleOverview(u, status, go)
            if (u.trialAvailable) TrialEntry(go)
            if (u.simAllowed) DemoPreviews(go)
        })
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
        val context = androidx.compose.ui.platform.LocalContext.current
        if (androidx.compose.runtime.remember { hasCar3D(context) })
            HomeLink("3D로 둘러보기", null, Icons.Outlined._3dRotation, Modifier.testTag("demo_CAR_3D")) { go("CAR_3D") }
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
private fun VehicleOverview(u: UiState, status: HomeStatus, go: (String) -> Unit) {
    val compact = isCompact()
    val verdict = status.verdict
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().testTag("home_verdict").clip(shape).background(C.card).border(1.dp, C.line, shape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (compact) 16.dp else 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val model = u.settings.carModel
            if (model == null) Icon(Icons.Outlined.DirectionsCar, null, Modifier.size(30.dp), tint = C.title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(model?.label ?: "내 차", color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("배터리·연료  ${u.homeReading("soc", "${u.signals.socPct}%")}", color = C.sub, fontSize = 16.sp)
            }
            if (model == null) HomeActionText("차량 등록") { go("CAR_SETUP") }
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CarPhoto(model, Modifier.width(if (compact) 120.dp else 150.dp).heightIn(max = 60.dp))
                HomeActionText("차량 사진") { go("VEHICLE_VIEW") }
            }
        }
        Column(Modifier.fillMaxWidth().background(if (C.dark) Color(0xFF233348) else Color(0xFF203445))
            .then(verdict.tab?.let { target -> Modifier.clickable(role = Role.Button) { go(target) } } ?: Modifier)
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                val icon = when (verdict.level) {
                    Level.OK -> Icons.Outlined.CheckCircle
                    Level.WARN, Level.DANGER -> Icons.Outlined.Warning
                    Level.INFO -> Icons.Outlined.Info
                }
                val tint = when (verdict.level) {
                    Level.OK -> Color(0xFF95E6C4)
                    Level.WARN -> Color(0xFFFFD38F)
                    Level.DANGER -> Color(0xFFFFB7B8)
                    Level.INFO -> Color.White
                }
                Icon(icon, null, Modifier.size(26.dp), tint = tint)
                Text(verdict.word, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                if (verdict.tab != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
            }
            if (verdict.level != Level.OK && verdict.detail.isNotEmpty()) Text(verdict.detail, color = Color.White, fontSize = 17.sp)
            Text(verdict.basis, color = Color(0xFFCFDAE2), fontSize = 15.sp)
        }
        status.alerts.take(3).forEach { alert ->
            Row(Modifier.fillMaxWidth().testTag("home_alert_${alert.id}").clickable(role = Role.Button) { go(alert.route) }
                .heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(alert.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.warn)
                    Text(alert.value, fontSize = 15.sp, color = C.sub)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.sub)
            }
        }
        VehicleChecklist(status, go)
    }
}

private data class HandoverAction(val label: String, val icon: ImageVector, val route: String, val tag: String)

private val handoverActions = listOf(
    HandoverAction("대리 맡기기", Icons.Outlined.LocalParking, "VALET", "home_hand_valet"),
    HandoverAction("정비 맡기기", Icons.Outlined.Build, "HAND_SERVICE", "home_hand_service"),
    HandoverAction("빌려주기", Icons.Outlined.Key, "HAND_LEND", "home_hand_lend"),
)

@Composable
private fun HandoverPanel(u: UiState, go: (String) -> Unit) {
    val valet = u.engine.valet
    val lend = u.engine.lend
    Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("맡기고 돌려받기", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
            HomeActionText("맡김 이력서", Modifier.testTag("home_hand_history")) { go("HISTORY") }
        }
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
        if (!valet.active && !lend.active) HandoverActions(go)
    }
}

@Composable
private fun HandoverActions(go: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 250.dp || LocalDensity.current.fontScale >= 1.3f) Column {
            handoverActions.forEach { action ->
                HomeLink(action.label, null, action.icon, Modifier.testTag(action.tag)) { go(action.route) }
            }
        } else Row(Modifier.fillMaxWidth()) {
            handoverActions.forEach { action ->
                Column(Modifier.weight(1f).testTag(action.tag).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { go(action.route) }.heightIn(min = 64.dp).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(action.icon, null, Modifier.size(26.dp), tint = C.title)
                    Text(action.label, color = C.ink, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            }
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
private fun HomeSearch(go: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("home_search")
        .clickable(role = Role.Button) { go("SEARCH") }.heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Search, null, Modifier.size(24.dp), tint = C.title)
        Text("기능·증상 검색", Modifier.weight(1f), color = C.ink, fontSize = 19.sp)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.sub)
    }
}

@Composable
private fun VehicleChecklist(status: HomeStatus, go: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("home_checklist").padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
            .clickable(role = Role.Button) { expanded = !expanded }.heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("차 상태", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
            Text(status.checklistSummary, color = C.ink, fontSize = 16.sp)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.sub)
        }
        val checks = if (expanded) status.visibleChecks else emptyList()
        checks.forEach { check ->
            HorizontalDivider(color = C.line)
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { go(check.route) }.heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(check.name, Modifier.weight(1f), fontSize = 18.sp, color = C.ink)
                Column(Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                    Text(check.value, color = C.ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.End)
                    check.readAtMs?.let { Text("${VerdictEngine.hhmm(it)} 수신", color = C.sub, fontSize = 14.sp) }
                }
            }
        }
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
private fun HomeFeatures(u: UiState, modules: List<Module>, go: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        HomeSearch(go)
        HorizontalDivider(color = C.line)
        Row(Modifier.fillMaxWidth().testTag("home_more").semantics { stateDescription = if (expanded) "펼쳐짐" else "접힘" }
            .clickable(role = Role.Button) { expanded = !expanded }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("기록과 자주 쓰는 기능", Modifier.weight(1f), color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.sub)
        }
        if (!expanded) return@Card
        Text("기록과 관리", color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val protectRecords = u.ownerPinSet && !u.ownerUnlocked && (u.engine.valet.active || u.engine.lend.active)
        val distance = if (protectRecords) null else u.thisWeek?.let { "%.1f km".format(it.km) } ?: "기록 없음"
        HomeLink("이번 주 주행", distance, Icons.Outlined.Receipt) { go("INSIGHT") }
        HomeLink("소모품 교체 시기", null, Icons.Outlined.Build) { go("SERVICE") }
        HomeLink("주차 위치", u.engine.floor.confirmed?.let { "지하 ${it}층" }, Icons.Outlined.LocalParking) { go("TRUNK") }
        HorizontalDivider(color = C.line)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("자주 쓰는 기능", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
            HomeActionText("편집") { go("SETTINGS") }
        }
        modules.forEach { module ->
            val alert = statusOf(module, u).takeIf { it.second == Level.DANGER }
            HomeLink(shortName(module), alert?.first, moduleIcon[module] ?: Icons.Outlined.Badge) { go(module.name) }
        }
        HomeActionText("상황별 기능 보기") { go("VERIFIED") }
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
