package com.verified.incar.ui

import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
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
    // 화면 배치가 한 단 ↔ 두 단으로 바뀌어도 순서 편집을 이어 간다.
    val favoriteEditing = rememberSaveable { mutableStateOf(false) }
    val quickEditing = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (isCompact()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CarHero(vm, u, go, quickEditing)
            HomeList(vm, u, status, go, favoriteEditing)
            if (u.trial) TrialPanel(vm, u, go)
            if (u.trialAvailable) TrialEntry(go)
        }
    } else {
        TwoUp(28.dp, 20.dp, 20.dp, 16.dp, leftWeight = 1.45f, left = {
            CarHero(vm, u, go, quickEditing)
            // 차 화면: 차 안에서 바로 시작하는 두 가지를 큰 카드로
            if (isCarScreen()) Row(Modifier.fillMaxWidth().testTag("home_car_actions"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(Triple(Icons.Outlined.LocalParking, "대리 안심 주차 시작", "VALET"), Triple(Icons.Outlined.Luggage, "하차 3초 점검", "TRUNK")).forEach { (icon, label, route) ->
                    Row(Modifier.weight(1f).lift(RoundedCornerShape(16.dp), 8.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (route == "VALET") Tone.navyBlue else Tone.bluePurple).clickable(role = Role.Button) { go(route) }
                        .heightIn(min = 76.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, Modifier.size(32.dp), tint = Color.White)
                        Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (u.trialAvailable) TrialEntry(go)
        }, right = {
            HomeList(vm, u, status, go, favoriteEditing)
            if (u.trial) TrialPanel(vm, u, go)
        })
    }
}

/** 맨 위: 차 이름 · 배터리 · 주차 상태 → 차 사진 → 빠른 제어 4개. */
@Composable
private fun CarHero(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit, quickEditing: androidx.compose.runtime.MutableState<Boolean>) {
    val model = u.settings.carModel ?: CarModel.GRANDEUR.takeIf { u.simAllowed && !u.carConnected }
    val s = u.signals
    val shortPhone = !isCarScreen() && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    Column(Modifier.fillMaxWidth().testTag("home_hero").padding(horizontal = 4.dp, vertical = if (shortPhone) 4.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (shortPhone) 8.dp else 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(model?.label ?: "내 차", color = C.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                val state = when {
                    s.uxRestricted -> "주행 중"
                    s.gear == com.verified.incar.core.Gear.P -> "주차됨"
                    else -> "${s.gear}단"
                }
                Text("${if (u.simAllowed && !u.carConnected) "시연 · " else ""}배터리·연료 ${u.homeReading("soc", "${s.socPct}%")} · ${u.read("gear", state)}",
                    Modifier.testTag("home_vehicle_status"), color = C.sub, fontSize = 16.sp)
            }
            if (model == null) HomeActionText("차량 등록") { go("CAR_SETUP") }
            else if (shortPhone) CarPhoto(model, Modifier.width(132.dp).height(74.dp)
                .clickable(role = Role.Button, onClickLabel = "외관·실내 사진 보기") { go("VEHICLE_VIEW") })
        }
        if (model != null && !shortPhone) Box(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "외관·실내 사진 보기") { go("VEHICLE_VIEW") },
            contentAlignment = Alignment.Center) {
            if (model == CarModel.GRANDEUR) {
                if (isCarScreen() && C.dark) androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(280.dp)) {
                    val platform = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.25f)
                    val origin = androidx.compose.ui.geometry.Offset(size.width * 0.06f, size.height * 0.71f)
                    drawOval(Brush.verticalGradient(listOf(Color(0xFF777D88), Color(0xFF282B34)), origin.y, origin.y + platform.height), origin, platform)
                    drawOval(Color.White.copy(alpha = 0.18f), origin, platform, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.verified.incar.R.drawable.reference_sedan_hero),
                    "차량 외관 일러스트 · 누르면 공식 사진 보기",
                    Modifier.fillMaxWidth().height(if (isCompact()) 172.dp else 280.dp).clipToBounds(),
                    alignment = Alignment.Center, contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else CarPhoto(model, Modifier.fillMaxWidth().height(if (isCompact()) 218.dp else 272.dp))
        }
        QuickControls(vm, u, editState = quickEditing)
    }
}

/** 기능 목록: 아이콘 · 이름 · 현재 값 · ›. 차량 → 맡기고 돌려받기 → 기록과 관리 → 더 보기 순서. */
@Composable
private fun HomeList(vm: VerifiedRuntime, u: UiState, status: HomeStatus, go: (String) -> Unit, favoriteEditing: androidx.compose.runtime.MutableState<Boolean>) {
    val s = u.signals
    val valet = u.engine.valet
    val lend = u.engine.lend
    Column(Modifier.fillMaxWidth().testTag("home_list"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            Card(Modifier.fillMaxWidth(), padding = 12.dp) {
                ListItem(Icons.Outlined.Warning, alert.name, alert.value, Modifier.testTag("home_alert_${alert.id}"), warn = true) { go(alert.route) }
            }
        }
        val verdict = status.verdict
        if (!isCompact()) {
            Card(Modifier.fillMaxWidth(), padding = 8.dp) {
                ListItem(Icons.Outlined.DirectionsCar, "컨트롤", "문·트렁크·좌석 확인", Modifier.testTag("home_control")) { go("CONTROL") }
            }
            Card(Modifier.fillMaxWidth(), padding = 8.dp) {
                ListItem(Icons.Outlined.Thermostat, "실내 온도",
                    "설정 ${u.read("hvacTemp", "%.1f℃".format(s.hvacTempC))} · 공조 ${u.read("hvacPower", if (s.hvacPowerOn) "켜짐" else "꺼짐")}") { go("CONTROL") }
            }
        }
        if (!isCompact()) Card(Modifier.fillMaxWidth().testTag("home_verdict"), padding = 14.dp, onClick = { go(verdict.tab ?: "VEHICLE_VIEW") }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (verdict.level == Level.OK) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    null, Modifier.size(26.dp), tint = levelColor(verdict.level))
                Column(Modifier.weight(1f)) {
                    Text(verdict.word, color = C.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(verdict.basis, color = C.sub, fontSize = 14.sp)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = C.sub)
            }
        }
        Text("맡기고 돌려받기", Modifier.padding(top = 8.dp), color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        val tiles = (if (!valet.active && !lend.active) listOf(
            HandTile(Icons.Outlined.LocalParking, "대리 맡기기", "home_hand_valet", "VALET"),
            HandTile(Icons.Outlined.Build, "정비 맡기기", "home_hand_service", "HAND_SERVICE"),
            HandTile(Icons.Outlined.Key, "빌려주기", "home_hand_lend", "HAND_LEND"),
        ) else emptyList()) + HandTile(Icons.Outlined.Description, "맡김 이력서", "home_hand_history", "HISTORY")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 310.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        Row(Modifier.weight(1f).testTag(t.tag)
                            .lift(RoundedCornerShape(18.dp), 10.dp).clip(RoundedCornerShape(18.dp)).background(C.surface)
                            .clickable(role = Role.Button) { go(t.route) }.heightIn(min = 84.dp).padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            SculptedIcon(t.icon, size = 43.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(t.label, color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                if (t.route != "HISTORY") Text(when (t.route) {
                                    "VALET" -> "준비됨"
                                    "HAND_SERVICE" -> "시작하기"
                                    else -> "시작하기"
                                }, color = C.sub, fontSize = 15.sp)
                            }
                        }
                    }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
        }
        Text("더 보기", Modifier.padding(top = 8.dp), color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Card(Modifier.fillMaxWidth(), padding = 10.dp) {
            ListItem(Icons.Outlined.Explore, "상황별 기능", "부축 동행·스쿨존·첫 운전 등") { go("VERIFIED") }
            HorizontalDivider(color = C.line)
            ListItem(Icons.Outlined.Search, "기능·증상 검색", null, Modifier.testTag("home_search")) { go("SEARCH") }
            if (isCompact()) ListItem(Icons.Outlined.DirectionsCar, "컨트롤", "문·트렁크·좌석 확인", Modifier.testTag("home_control")) { go("CONTROL") }
        }
        if (isCompact()) Card(Modifier.fillMaxWidth().testTag("home_verdict"), padding = 16.dp, onClick = { go(verdict.tab ?: "VEHICLE_VIEW") }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(if (verdict.level == Level.OK) Icons.Outlined.CheckCircle else Icons.Outlined.Info, null, Modifier.size(28.dp), tint = levelColor(verdict.level))
                Column(Modifier.weight(1f)) {
                    Text(verdict.word, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(verdict.basis, fontSize = 15.sp, color = C.sub)
                }
            }
        }
        HomePersonalization(vm, u, go, favoriteEditing)
    }
}

private data class HandTile(val icon: ImageVector, val label: String, val tag: String, val route: String)

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

/** 차에 연결되지 않은 휴대폰에서 예시 차량 체험으로 들어가는 카드. */
@Composable
private fun TrialEntry(go: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("trial_entry"), padding = 16.dp) {
        Text("예시 차량으로 체험하기", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Text("차가 없어도 대리·정비·빌려주기 과정을 예시 값으로 볼 수 있어요. 체험 기록은 실제 기록과 따로 저장돼요.", fontSize = 17.sp, color = C.sub)
        BigButton("차종 고르고 체험 시작", Modifier.fillMaxWidth(), BtnStyle.OUTLINE, fontSize = 19.sp) { go("TRIAL") }
    }
}

/** 직접 제어 아래에서 선택할 수 있는 자동 시연. 진행 중인 맡김이 있으면 끝까지 보게 한다. */
@Composable
private fun TrialPanel(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    val busy = u.engine.valet.active || u.engine.lend.active
    Card(Modifier.fillMaxWidth().testTag("trial_panel"), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("자동 시연", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("예시 차량 · ${u.settings.carModel?.label ?: "차종 미선택"}", fontSize = 16.sp, color = C.sub)
            }
            HomeActionText("시연 설정", Modifier.testTag("trial_end")) { go("TRIAL") }
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
    Row(Modifier.fillMaxWidth().testTag("home_active").clip(RoundedCornerShape(16.dp)).background(C.card).border(1.dp, C.line, RoundedCornerShape(16.dp))
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
