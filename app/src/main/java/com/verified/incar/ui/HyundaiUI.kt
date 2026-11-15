package com.verified.incar.ui

import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Accessible
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AirlineSeatReclineExtra
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalCarWash
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.ControlKind
import com.verified.incar.core.HandKind
import com.verified.incar.core.Level
import com.verified.incar.core.Module
import com.verified.incar.core.MyHyundai
import com.verified.incar.core.SharedKey
import com.verified.incar.core.VerdictEngine
import java.time.LocalDate

// ================================================================== 마이현대 디자인 언어 컴포넌트

/** 사용 빈도가 높은 차량 조작과 상황별 기능을 먼저 둔다. */
enum class Root(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("홈", Icons.Outlined.Home, Icons.Filled.Home),
    CONTROL("제어", Icons.Outlined.DirectionsCar, Icons.Filled.DirectionsCar),
    HISTORY("기록", Icons.Outlined.Description, Icons.Filled.Description),
    MYPAGE("더보기", Icons.Outlined.Menu, Icons.Filled.Menu),
}

val moduleIcon = mapOf(
    Module.VALET to Icons.Outlined.LocalParking, Module.DRIVER to Icons.Outlined.Badge, Module.TRIP to Icons.Outlined.Receipt,
    Module.ESCORT to Icons.Outlined.Accessible, Module.SCHOOL to Icons.Outlined.School, Module.CHAUFFEUR to Icons.Outlined.AirlineSeatReclineExtra,
    Module.TRUNK to Icons.Outlined.Luggage, Module.DIAG to Icons.AutoMirrored.Outlined.HelpOutline, Module.MISSION to Icons.Outlined.Flag,
    Module.COMFORT to Icons.Outlined.Thermostat,
)

fun shortName(m: Module) = when (m) {
    Module.VALET -> "대리 주차"; Module.DRIVER -> "운전자 확인"; Module.TRIP -> "운행기록부"; Module.ESCORT -> "부축 동행"
    Module.SCHOOL -> "스쿨존"; Module.CHAUFFEUR -> "승차감"; Module.TRUNK -> "하차 점검"; Module.DIAG -> "고장이야?"
    Module.MISSION -> "첫 운전"; Module.COMFORT -> "출발 준비"
}

@Composable
fun BottomNav(selected: String, go: (String) -> Unit) {
    Column {
        HorizontalDivider(color = C.line)
        // 폰 가로처럼 키가 낮으면 아이콘 옆에 이름을 붙여 한 줄로(높이 절반)
        val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
        Row(Modifier.fillMaxWidth().background(C.card).selectableGroup()) {
            Root.entries.forEach { r ->
                val on = r.name == selected
                val m = Modifier.weight(1f).testTag("nav_${r.name}").drawBehind {
                    if (on) drawRoundRect(C.title, androidx.compose.ui.geometry.Offset(size.width * 0.22f, 0f),
                        androidx.compose.ui.geometry.Size(size.width * 0.56f, 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }.selectable(selected = on, role = Role.Tab) { go(r.name) }
                    .heightIn(min = 48.dp).padding(vertical = if (short) 4.dp else 8.dp)
                // 이름은 글자로 읽히므로 아이콘 설명은 비운다(두 번 읽히지 않게)
                val icon = @Composable { Icon(if (on) r.selectedIcon else r.icon, null, Modifier.size(if (short) 22.dp else 26.dp), tint = if (on) C.title else C.sub) }
                val label = @Composable { Text(r.label, fontSize = if (short) 14.sp else 16.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) C.title else C.sub) }
                if (short) Row(m, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) { icon(); label() }
                else Column(m, horizontalAlignment = Alignment.CenterHorizontally) { icon(); label() }
            }
        }
    }
}

/** 차 디스플레이용 세로 메뉴(왼쪽). 하단 탭과 같은 항목·같은 태그. */
@Composable
fun NavRail(selected: String, go: (String) -> Unit) {
    Row {
        Column(Modifier.fillMaxHeight().width(96.dp).background(if (C.dark) Color(0xFF111C2D) else C.card).selectableGroup().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Root.entries.forEach { r ->
                val on = r.name == selected
                Column(Modifier.testTag("nav_${r.name}").size(78.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) C.deep else Color.Transparent)
                    .drawBehind { if (on) drawRoundRect(Color(0xFF42A5F5), size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())) }
                    .selectable(selected = on, role = Role.Tab) { go(r.name) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(if (on) r.selectedIcon else r.icon, null, Modifier.size(30.dp), tint = if (on) Color.White else C.sub)
                    Text(r.label, fontSize = 17.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Color.White else C.sub)
                }
            }
        }
        Box(Modifier.fillMaxHeight().width(1.dp).background(C.line))
    }
}

/** 회색 둥근 사각 안의 선 아이콘 (마이카 메뉴 아이콘). */
@Composable
fun IconBox(icon: ImageVector, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(C.ice), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.58f).dp), tint = C.title)
    }
}

/** 마이카 메뉴 행: 아이콘 상자 · 이름 · (값) · › */
@Composable
fun MenuRow(icon: ImageVector, label: String, value: String? = null, level: Level? = null, tag: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon)
        val valueColor = if (level != null) levelColor(level) else C.sub
        if (isCramped() && value != null) {
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(label, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                Text(value, fontSize = 17.sp, color = valueColor)
            }
        } else {
            Text(label, Modifier.weight(1f).padding(start = 12.dp), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            if (value != null) Text(
                value, if (isCompact()) Modifier.padding(start = 8.dp).widthIn(max = 125.dp) else Modifier, fontSize = 17.sp,
                color = valueColor, textAlign = TextAlign.End,
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(30.dp), tint = C.sub)
    }
}

/** 마이페이지 '고객 서비스'처럼 아이콘 없는 글자 행. */
@Composable
fun TextRow(label: String, value: String? = null, tag: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).clickable(role = Role.Button, onClick = onClick).heightIn(min = 52.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCompact()) Column(Modifier.weight(1f)) {
            Text(label, fontSize = 20.sp, color = C.ink)
            if (value != null) Text(value, fontSize = 16.sp, color = C.sub)
        } else {
            Text(label, Modifier.weight(1f), fontSize = 20.sp, color = C.ink)
            if (value != null) Text(value, fontSize = 17.sp, color = C.sub)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(24.dp), tint = C.sub)
    }
}

/** 카드 전체를 눌러 관련 기능을 연다. */
@Composable
private fun RelatedFeature(title: String, detail: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), padding = 18.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                Text(detail, fontSize = 17.sp, color = C.sub)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(24.dp), tint = C.sub)
        }
    }
}

/** 원격제어 타일: 굵은 이름, 아래 상태(켜져 있으면 파랑), 오른쪽 아래 아이콘. */
@Composable
fun ControlTile(title: String, state: String, active: Boolean, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, busy: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(C.card)
            .border(1.dp, C.line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (isCompact()) 90.dp else 112.dp).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 차 신호로 결과를 기다리는 동안: 아이콘은 그대로 두고 바깥 링만 돈다
            if (busy) SpinnerRing(30.dp, Modifier.testTag("control_busy")) { Icon(icon, null, Modifier.size(16.dp), tint = C.title) }
            else Icon(icon, null, Modifier.size(22.dp), tint = if (enabled) C.title else C.sub)
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
        }
        Text(state, fontSize = 18.sp, color = if (active && enabled) C.title else C.sub)
    }
}

@Composable
fun InfoBanner(text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = C.sub)
        Text(text, Modifier.weight(1f), fontSize = 18.sp, color = C.ink)
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = C.sub)
    }
}

@Composable
fun BoostPanel(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = C.line)
        Text(title, Modifier.padding(top = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
        content()
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)

@Composable
private fun Battery(u: UiState) {
    KeyValue("배터리·연료", u.read("soc", "${u.signals.socPct}%"))
}

@Composable
private fun TwoCol(left: @Composable () -> Unit, right: @Composable () -> Unit, leftWeight: Float = 1f, rightFirst: Boolean = false, tabs: Pair<String, String>? = null, shortHeight: Boolean = false) {
    TwoUp(32.dp, if (shortHeight) 6.dp else 24.dp, 24.dp, 16.dp, leftWeight, rightFirst = rightFirst, tabs = tabs, left = left, right = right)
}

// ================================================================== 샵 (현대샵 · 블루멤버스 · 블루링크 스토어)
@Composable
fun ShopMH(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    val due = u.consumables.filter { it.due || it.soon }.map { it.item.label }
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    TwoCol(left = {
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            SectionTitle("차량용 상품")
            Text("시연용 상품과 가격이에요.", fontSize = 16.sp, color = C.sub)
            MyHyundai.SHOP.forEach { item ->
                HorizontalDivider(color = C.line)
                val open = expanded == item.name
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (open) "상세 접기" else "상세 보기") {
                        expanded = if (open) null else item.name
                    }.padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.name, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = C.sub)
                    }
                    Text(if (item.priceWon > 0) "%,d원".format(item.priceWon) else "차종별 가격 상이", fontSize = 17.sp, color = C.sub)
                    if (due.any { item.name.contains(it.take(3)) || item.note.contains(it.take(3)) })
                        Text("교체 주기가 가까워요", fontSize = 16.sp, color = C.warn)
                    if (open) Text("${item.store} · ${item.note}", fontSize = 16.sp, color = C.sub)
                }
            }
        }
    }, right = {
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            SectionTitle("멤버십과 서비스")
            MenuRow(Icons.Outlined.QrCode2, "블루멤버스 카드", "포인트 시연") { go("WALLET") }
            MenuRow(Icons.Outlined.Search, "주변 검색") { go("CARLIFE") }
            MenuRow(Icons.Outlined.Storefront, "현대샵") { vm.toast("상품 구매는 마이현대의 현대샵에서 이용해 주세요.") }
            MenuRow(Icons.Outlined.ShoppingBag, "블루링크 스토어") { vm.toast("디지털 사양 구매는 마이현대의 블루링크 스토어에서 이용해 주세요.") }
        }
    })
}

// ================================================================== 제어 (원격제어)
@Composable
fun ControlMH(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    val s = u.signals
    val can = u.canControl && u.simAllowed && !u.carConnected
    val pending = u.engine.pendingControl
    val checking = pending?.kind
    val wait = if (pending?.unconfirmed == true) "결과 미확인" else "확인 중…"
    val remoteOn = u.engine.comfort.phase == com.verified.incar.core.ComfortPhase.REMOTE_RUNNING
    // 꺼짐 → 켜짐으로 바뀌는 순간에만 진동(이미 켜진 채 화면에 들어올 때는 울리지 않음)
    val context = androidx.compose.ui.platform.LocalContext.current
    var wasOn by remember { mutableStateOf(remoteOn) }
    androidx.compose.runtime.LaunchedEffect(remoteOn) {
        if (remoteOn && !wasOn && u.settings.startHaptic) com.verified.incar.StartHaptic.play(context, u.settings.carModel?.ev == true)
        wasOn = remoteOn
    }
    var inside by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 넓은 화면(차 디스플레이·가로)은 왼쪽에 차 그림, 오른쪽에 조작을 둬 그림이 잘리지 않게 한다
    val wide = !isCompact()
    val inCar = isCarScreen()
    val shortPhone = !inCar && androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val quickEditing = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val controls = @Composable {
        if (inside) CabinTempBar(vm, u) else QuickControls(vm, u, editState = quickEditing)
        if (checking != null) Text("${checking.label} · $wait", fontSize = 15.sp, color = C.sub)
    }
    TwoCol(leftWeight = if (wide) 1.45f else 1f, shortHeight = shortPhone, left = {
        Column(Modifier.fillMaxWidth().testTag("control_top"), verticalArrangement = Arrangement.spacedBy(if (shortPhone) 8.dp else 18.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(C.chip).padding(4.dp).selectableGroup()) {
                listOf(false to "외부", true to "실내").forEach { (value, label) ->
                    val selected = inside == value
                    Box(Modifier.weight(1f).testTag(if (value) "control_interior" else "control_exterior")
                        .then(if (selected) Modifier.lift(RoundedCornerShape(28.dp), 8.dp) else Modifier)
                        .clip(RoundedCornerShape(28.dp)).background(if (selected) C.card else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab) { inside = value }
                        .heightIn(min = 48.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 22.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) C.title else C.sub)
                    }
                }
            }
            // 개발·시연 안내 문장은 화면에 두지 않는다(README에만 적는다)
            if (u.carConnected && !can) Text("연결된 차량의 현재 상태예요.", fontSize = 15.sp, color = C.sub)
            if (inside && !wide && !isCramped()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CarTopView(vm, u, true, Modifier.weight(0.66f), compactInterior = true)
                    CabinTempRail(vm, u, Modifier.weight(0.34f))
                }
                if (checking != null) Text("${checking.label} · $wait", fontSize = 15.sp, color = C.sub)
            } else {
                Box(Modifier.fillMaxWidth().padding(vertical = if (shortPhone) 0.dp else if (wide) 12.dp else 8.dp), contentAlignment = Alignment.Center) {
                    CarTopView(vm, u, inside, if (wide) Modifier.widthIn(max = if (inside) 700.dp else 480.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
                        maxStageHeight = if (shortPhone && !inside) 180.dp else null)
                }
                if (!wide) controls()
            }
        }
    }, right = {
        if (wide) Card(Modifier.fillMaxWidth().testTag("control_actions"), padding = if (shortPhone) 12.dp else 22.dp) { controls() }
        if (inCar) Column(Modifier.fillMaxWidth().testTag("control_incar"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (inside) ControlShortcuts(go) else {
            RelatedFeature("출발 예약", if (u.settings.schedule.enabled) "%02d:%02d".format(u.settings.schedule.hour, u.settings.schedule.minute) else "출발 시간에 맞춘 실내 준비") { go(Module.COMFORT.name) }
            Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                MenuRow(Icons.Outlined.Luggage, "하차 3초 점검", "창문·전조등·트렁크", tag = "control_trunk") { go(Module.TRUNK.name) }
            }
            Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                MenuRow(Icons.Outlined.Accessible, "부축 동행 · 하차 빛", "보도 쪽 문 안내", tag = "control_escort") { go(Module.ESCORT.name) }
            }
            Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                MenuRow(Icons.Outlined.Flag, "첫 운전 확인", "조작 미션", tag = "control_mission") { go(Module.MISSION.name) }
            }
            }
        } else {
            if (inside && !wide) ControlShortcuts(go)
            Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            MenuRow(Icons.Outlined.Whatshot, "미리 공조", if (remoteOn) "켜짐 · 끄기" else "꺼짐 · 켜기", tag = "control_remote") {
                if (!can) vm.toast("실차 원격 공조는 마이현대에서 이용해 주세요.")
                else vm.act { sig, now -> if (remoteOn) stopRemote() else startRemote(sig, now) }
            }
            // 실내(폰)에서는 위 바로가기 버튼에 출발 예약이 있으므로 목록에서 빼 한 곳에만 둔다
            if (!(inside && !wide)) {
                HorizontalDivider(color = C.line)
                MenuRow(Icons.Outlined.Schedule, "출발 예약", if (u.settings.schedule.enabled) "%02d:%02d".format(u.settings.schedule.hour, u.settings.schedule.minute) else "설정") { go(Module.COMFORT.name) }
            }
            HorizontalDivider(color = C.line)
            MenuRow(Icons.Outlined.DirectionsCar, "외관·실내 사진", "공식 사진", tag = "control_vehicle_view") { go("VEHICLE_VIEW") }
            }
        }
        BigButton("최근 제어 결과 보기", Modifier.fillMaxWidth(), BtnStyle.OUTLINE) { go("CONTROL_HISTORY") }
        u.engine.controlLog.firstOrNull()?.let { ev ->
            Card(Modifier.fillMaxWidth().testTag("control_evidence"), padding = 20.dp) {
                CardHeader(null, "최근 제어 결과", trailing = { Pill(ev.result.label, ev.result.level) })
                Text(ev.kind.label, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = C.ink)
                ev.steps.forEach { (t, line) -> Text("${VerdictEngine.hhmmss(t)}  $line", fontSize = 15.sp, color = C.sub) }
            }
        }
    })
}

@Composable
private fun ControlShortcuts(go: (String) -> Unit) {
    val columns = if (isCramped()) 1 else 2
    val shortcuts = listOf(
        Triple("출발 예약", Module.COMFORT.name, "control_schedule"),
        Triple("하차 3초 점검", Module.TRUNK.name, "control_trunk"),
        Triple("부축 동행·하차 빛", Module.ESCORT.name, "control_escort"),
        Triple("첫 운전 확인", Module.MISSION.name, "control_mission"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        shortcuts.chunked(columns).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, route, tag) ->
                    Box(Modifier.weight(1f).testTag(tag)
                        .lift(RoundedCornerShape(16.dp), 8.dp).clip(RoundedCornerShape(16.dp)).background(C.surface)
                        .clickable(role = Role.Button) { go(route) }.heightIn(min = 70.dp).padding(14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(label, color = C.ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

// ================================================================== 카가드 기능 모음
@Composable
fun VerifiedMH(u: UiState, modules: List<Module>, go: (String) -> Unit) {
    SituationDirectory(u, modules, go)
}

// ================================================================== 기능·증상 검색
@Composable
fun SearchMH(go: (String) -> Unit, showMock: Boolean = true) {
    FeatureSearch(go, showMock)
}

// ================================================================== 마이페이지 (사람 아이콘)
@Composable
private fun MoreMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), padding = 16.dp) { Column(Modifier.fillMaxWidth(), content = content) }
}

@Composable
private fun MoreMenuRow(label: String, value: String? = null, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 52.dp).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            if (isCramped() && value != null) Text(value, fontSize = 15.sp, color = C.sub)
        }
        if (!isCramped() && value != null) {
            val notices = tag == "tab_NOTICE" && value.endsWith("건") && !value.startsWith("0")
            Text(value, Modifier.clip(RoundedCornerShape(50)).background(if (notices) Color(0xFFF38A14) else C.chip)
                .padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 16.sp,
                fontWeight = FontWeight.Medium, color = if (notices) Color.White else C.ink)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(23.dp), tint = C.title)
    }
}

@Composable
fun MyPageMH(u: UiState, modules: List<Module>, go: (String) -> Unit) {
    val locked = !u.ownerUnlocked && (u.engine.valet.active || u.engine.lend.active)
    val example = u.simAllowed && !u.carConnected
    TwoCol(left = {
        Box(Modifier.fillMaxWidth().testTag("more_profile").heightIn(min = 182.dp)
            .clickable(role = Role.Button, onClickLabel = if (example) "예시 차종 바꾸기" else "차량 등록·변경") { go(if (example) "TRIAL" else "CAR_SETUP") }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp).lift(RoundedCornerShape(20.dp), 8.dp).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF08234A), Color(0xFF243D65), Color(0xFF596B8D))))
                .heightIn(min = 150.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text((if (example) "체험 차량" else "차주님") + (u.settings.carModel?.let { " · ${it.label}" } ?: ""),
                        Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White)
                }
                if (u.settings.carModel == null) Text(if (example) "등록 없이 둘러보세요" else "차량을 등록해 주세요", fontSize = 18.sp, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(68.dp))
            }
            u.settings.carModel?.let { CarPhoto(it, Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.73f).height(125.dp)) }
        }
        SectionTitle("차량")
        MoreMenuGroup {
            MoreMenuRow("차량 관리", tag = "tab_SERVICE") { go("SERVICE") }
            MoreMenuRow("드라이빙 인사이트", tag = "tab_INSIGHT") { go("INSIGHT") }
            MoreMenuRow("디지털 키", tag = "tab_KEY") { go("KEY") }
            MoreMenuRow("카라이프", tag = "tab_CARLIFE") { go("CARLIFE") }
            if (u.simAllowed) MoreMenuRow("월렛", tag = "tab_WALLET") { go("WALLET") }
            if (u.simAllowed && !u.trial) MoreMenuRow("샵", tag = "tab_SHOP") { go("SHOP") }
        }
    }, right = {
        SectionTitle("카가드 기능")
        MoreMenuGroup {
            if (example) MoreMenuRow("체험 순서 보기", tag = "more_demo_guide") { go("VERIFIED") }
            MoreMenuRow("상황별 기능", "${modules.size}개", tag = "tab_VERIFIED") { go("VERIFIED") }
            MoreMenuRow("맡김 이력서", "${u.engine.history.size}건", tag = "tab_HISTORY") { go("HISTORY") }
        }
        SectionTitle("앱")
        MoreMenuGroup {
            MoreMenuRow("보낸 알림", if (locked) "차주 확인 후" else "${u.notices.size}건", tag = "tab_NOTICE") { go("NOTICE") }
            MoreMenuRow("차량 연결·신호 상태", if (u.carConnected) "연결됨" else "연결 전", tag = "tab_SIGNALS") { go("SIGNALS") }
            MoreMenuRow("앱 설정", tag = "tab_SETTINGS") { go("SETTINGS") }
        }
    })
}

// ================================================================== 차량 관리 (상태 · 소모품 · 정비 · 예약)
@Composable
fun ServiceMH(vm: VerifiedRuntime, u: UiState) {
    val s = u.signals
    var booking by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    TwoCol(
        tabs = "상태·정비" to "소모품",left = {
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            SectionTitle("차량 상태")
            // 실차에서 읽지 못한 신호는 기본값 대신 '확인 불가'로 쓴다. 문은 닫혀 있어도 안 잠겼을 수 있어 '잠금 해제'로
            val leak = MyHyundai.slowLeak(u.engine.stats)
            val tiles = listOf(
                Triple(Icons.Outlined.Lock, "문", u.read("lock", if (s.doorsLocked) "잠김" else "잠금 해제")),
                Triple(Icons.Outlined.Window, "창문", u.read("window", if (s.windowOpen) "열림" else "닫힘")),
                Triple(Icons.Outlined.Luggage, "트렁크", u.read("doors", if (s.trunkOpen) "열림" else "닫힘")),
                Triple(Icons.Outlined.TireRepair, "타이어 · 워셔액", u.read("tirePressure", if (s.tirePressureLow) "공기압 낮음" else leak?.let { "${MyHyundai.WHEELS[it.first]} 천천히 빠짐" } ?: "정상") + " · " + u.read("washer", if (s.washerLow) "부족" else "정상")),
                Triple(Icons.Outlined.Thermostat, "외기 · 실내 설정", u.read("outsideTemp", "%.0f℃".format(s.outsideTempC)) + " · " + u.read("hvacTemp", "%.0f℃".format(s.hvacTempC))),
                Triple(Icons.Outlined.LocalParking, "주차 위치", u.engine.floor.confirmed?.let { "${u.engine.floor.place.label} 지하 ${it}층" } ?: u.read("gps", s.place.label)),
            )
            val tileColumns = if (isCramped()) 2 else 3
            Column(Modifier.testTag("service_tiles"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                tiles.chunked(tileColumns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { (icon, label, value) ->
                            Column(Modifier.weight(1f).heightIn(min = 108.dp).padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(icon, null, Modifier.size(32.dp), tint = C.title)
                                Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.ink, textAlign = TextAlign.Center)
                                Text(value, fontSize = 15.sp, color = C.ink, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(tileColumns - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (!u.unread("tirePressure") && s.tireKpa.size == 4) KeyValue("바퀴별 공기압 (앞왼·앞오·뒤왼·뒤오)", s.tireKpa.joinToString(" · ") { "%.0f".format(it) } + " kPa")
        }
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            SectionTitle("충전 · 배터리 모니터링")
            Battery(u)
            // 막대 안에 %를 그리므로 읽지 못한 배터리는 막대를 숨긴다(바퀴별 공기압 줄과 같은 방식)
            if (!u.unread("soc")) GradientProgress(s.socPct / 100f, Modifier.fillMaxWidth())
            if (s.outsideTempC <= VerdictEngine.WINTER_C) Text("기온이 낮아 주행가능거리가 평소보다 줄 수 있어요.", fontSize = 16.sp, color = C.warn)
        }
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            SectionTitle("정비 이력")
            if (u.settings.serviceLog.isEmpty()) Text("아직 기록이 없어요", fontSize = 17.sp, color = C.sub)
            u.settings.serviceLog.take(3).forEach { KeyValue("${it.date} · %,.0fkm".format(it.odoKm), it.item) }
            u.settings.bookings.firstOrNull()?.let {
                InfoBanner("${it.kind} 예약 시연 · ${it.date} ${it.time} · ${it.shop}")
                if (it.note.isNotEmpty()) Text("함께 기록한 차 상태: ${it.note}", fontSize = 16.sp, color = C.sub)
            }
            if (!booking) BigButton("블루핸즈 정비 예약", Modifier.fillMaxWidth(), fontSize = 19.sp) { booking = true }
            else {
                BoostPanel("예약할 때 참고할 차 상태") {
                    MyHyundai.serviceNote(s, u.consumables, u.diag).forEach { Text("· $it", fontSize = 17.sp, color = C.ink) }
                }
                BookingPicker("정비", "블루핸즈", vm) { booking = false }
            }
        }
    }, right = {
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            SectionTitle("소모품 관리")
            Text("교체 주기는 제안값이에요 · 차종별 정비 주기표 확인 필요 · 거리와 기간 중 먼저 오는 쪽 기준 · 예상 날짜는 최근 주행거리 기준", fontSize = 16.sp, color = C.sub)
            u.consumables.forEach { c ->
                LabelRow({ m -> Column(m) {
                        Text(c.item.label, fontSize = 19.sp, color = C.ink)
                        if (c.harsh) Text("급감속이 잦아 주기를 %.0f%% 줄여 계산했어요".format((1 - MyHyundai.HARSH_FACTOR) * 100), fontSize = 15.sp, color = C.warn)
                    } }) {
                    Pill(
                        c.text ?: "기록 없음",
                        when { c.remainKm == null -> null; c.due -> Level.WARN; c.soon -> Level.INFO; else -> Level.OK },
                    )
                    Spacer(Modifier.width(8.dp))
                    Chip("교체 기록", null) { vm.act { sig, now -> addService(c.item.label, sig, now) } }
                }
            }
        }
    })
}

@Composable
private fun BookingPicker(kind: String, shop: String, vm: VerifiedRuntime, done: () -> Unit) {
    val days = listOf(1L, 2L, 3L).map { LocalDate.now().plusDays(it).toString() }
    var day by androidx.compose.runtime.saveable.rememberSaveable(kind, shop) { mutableStateOf(days.first()) }
    Text("예약 시연 · 선택한 시간은 앱에만 기록돼요. 업체에 실제 예약이 접수되지는 않아요.",
        Modifier.testTag("booking_demo_notice"), fontSize = 16.sp, color = C.sub)
    Wrap { days.forEach { d -> Chip(d.substring(5), d == day) { day = d } } }
    Wrap {
        listOf("10:00", "14:00", "17:00").forEach { t ->
            Chip("$t 예약", null) {
                vm.act { sig, now -> book(kind, day, t, shop, sig, now) }
                done()
            }
        }
    }
}

// ================================================================== 드라이빙 인사이트
@Composable
fun InsightMH(u: UiState) {
    val today = java.time.Instant.ofEpochMilli(u.nowMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val records = u.engine.stats.associateBy { it.date }
    val recent = dates.mapNotNull { records[it.toString()] }
    var chosen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val selected = dates.firstOrNull { it.toString() == chosen } ?: today
    val dayRecord = records[selected.toString()]
    fun shortDate(date: LocalDate) = "${date.monthValue}/${date.dayOfMonth}"
    fun distance(km: Double) = "%,.1f km".format(km)

    TwoCol(left = {
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            CardHeader(null, "날짜별 주행 거리")
            DrivingDistanceChart(dates, records.mapValues { it.value.km }, selected, { chosen = it.toString() })
            HorizontalDivider(color = C.line)
            Text("${selected.monthValue}월 ${selected.dayOfMonth}일" + if (selected == today) " · 오늘" else "", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.ink)
            if (dayRecord == null) Text("이 날짜에는 주행 기록이 없어요.", color = C.sub, fontSize = 17.sp)
            else {
                KeyValue("주행 거리", distance(dayRecord.km))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrivingEventTile("급가속", "${dayRecord.accels}회", false, Modifier.weight(1f))
                    DrivingEventTile("급감속", "${dayRecord.brakes}회", true, Modifier.weight(1f))
                }
            }
        }
    }, right = {
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            CardHeader(null, "최근 7일 합계", "${shortDate(dates.first())} ~ ${shortDate(today)}")
            Text(if (recent.isEmpty()) "주행 기록 없음" else distance(recent.sumOf { it.km }), Modifier.testTag("insight_rolling_total"), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = C.ink)
            HorizontalDivider(color = C.line)
            CardHeader(null, "주간 비교", "월요일부터 일요일까지의 기록이에요.")
            KeyValue("이번 주 주행 거리", u.thisWeek?.takeIf { it.days > 0 }?.let { distance(it.km) } ?: "기록 없음")
            KeyValue("지난주 주행 거리", u.lastWeek?.takeIf { it.days > 0 }?.let { distance(it.km) } ?: "기록 없음")
            KeyValue("배터리·연료 1%당", u.thisWeek?.kmPerPct?.let(::distance) ?: "계산할 기록 없음")
        }
    })
}

// ================================================================== 디지털 키
/** 오늘까지 유효한 공유 키인지. 날짜가 깨진 키는 남겨 둔다(차주가 직접 해제). */
fun SharedKey.live(today: LocalDate = LocalDate.now()) = runCatching { !LocalDate.parse(until).isBefore(today) }.getOrDefault(true)

@Composable
fun KeyMH(vm: VerifiedRuntime, u: UiState) {
    var who by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("자녀") }
    TwoCol(left = {
        DigitalKeyArtwork(u.settings.carModel?.label ?: "내 차")
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            SectionTitle("공유 중인 키")
            val keys = u.settings.sharedKeys.filter { it.live() }
            if (keys.isEmpty()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.chip)
                .padding(horizontal = 18.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
                Text("공유한 사람이 없어요", fontSize = 18.sp, color = C.sub)
            }
            keys.forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    IconBox(Icons.Outlined.Key)
                    Text("${k.name}   ${k.until}까지", Modifier.weight(1f).padding(start = 16.dp), fontSize = 20.sp, color = C.ink)
                    BigButton("해제", style = BtnStyle.OUTLINE, fontSize = 17.sp) { vm.ownerOnly { vm.act { _, _ -> revokeKey(k.name) } } }
                }
            }
            val until = LocalDate.now().plusDays(7).toString()
            Wrap(8.dp) {
                listOf("자녀", "부모님", "친구").forEach { n ->
                    val on = n == who
                    Row(Modifier.testTag("key_who_$n").clip(RoundedCornerShape(50)).background(if (on) C.ice else C.card)
                        .border(if (on) 1.8.dp else 1.dp, if (on) C.title else C.line, RoundedCornerShape(50))
                        .selectable(on, role = Role.RadioButton) { who = n }.heightIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SculptedIconCell(6, size = 28.dp)
                        Text(n, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                    }
                }
            }
            BigButton("$who · 7일 키 공유하기", Modifier.fillMaxWidth().testTag("key_share"), fontSize = 19.sp) {
                // 기한이 지난 키는 공유 중이 아니므로 지운 뒤 공유한다(같은 사람에게 다시 공유할 수 있게)
                vm.ownerOnly { vm.act { _, _ -> settings = settings.copy(sharedKeys = settings.sharedKeys.filter { it.live() }); shareKey(who, until) } }
            }
        }
    }, right = {
        RelatedFeature("운전자 확인", "운전자를 등록하고 입력한 보험 운전자 범위를 확인해요.") { vm.navigate.value = Module.DRIVER.name }
        RelatedFeature("빌려준 차 반납 리포트", if (u.engine.lend.active) "빌려주는 중이에요" else "돌려받을 때 이동 거리와 배터리 변화를 볼 수 있어요.") { vm.navigate.value = "HAND_LEND" }
    })
}

// ================================================================== 카라이프
@Composable
fun CarLifeMH(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    var category by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("충전소") }
    var chargerFilters by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(emptySet<String>()) }
    var wash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sos by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    TwoCol(left = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().testTag("life_tiles"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LifeTile(Icons.Outlined.LocalParking, "대리운전", Color(0xFFE1E3FF), C.title, Modifier.weight(1f)) { vm.act { _, _ -> callValet() }; go(Module.VALET.name) }
                    LifeTile(Icons.Outlined.LocalCarWash, "세차", Color(0xFFC7EBF1), Color(0xFF0B7A88), Modifier.weight(1f)) { wash = !wash }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LifeTile(Icons.Outlined.Notifications, if (u.settings.enforcementAlert) "주정차 단속 알림\n켜짐" else "주정차 단속 알림\n꺼짐", Color(0xFFFFF0B6), Color(0xFFAF7926),
                        Modifier.weight(1f).semantics { contentDescription = "주정차 단속 알림" }) { vm.ownerOnly { vm.settings { it.copy(enforcementAlert = !it.enforcementAlert) } } }
                    LifeTile(Icons.Outlined.SupportAgent, "긴급출동", Color(0xFFFFE3E3), C.danger, Modifier.weight(1f).testTag("mh_SOS")) { sos = !sos }
                }
            }
            if (wash) {
                BoostPanel("자동 세차 들어가기 전에") {
                    val items = MyHyundai.washCheck(u.signals)
                    // washCheck 순서(창문·트렁크·와이퍼)와 같은 실차 신호 이름. 읽지 못한 값에 ✓를 붙이지 않는다
                    val names = listOf("window", "doors", "wiper")
                    items.forEachIndexed { i, (label, ok) ->
                        val unknown = u.unread(names.getOrElse(i) { "" })
                        CheckLine(ok && !unknown, label, if (unknown) "확인 불가" else null, divider = i < items.lastIndex)
                    }
                }
                BookingPicker("세차", "자동 세차장", vm) { wash = false }
                BigButton("자동 세차라면 ⑨ N단 유지 도우미", Modifier.fillMaxWidth(), BtnStyle.TONAL, fontSize = 17.sp) { go(Module.MISSION.name) }
            }
            if (u.engine.evac.active) BoostPanel("고장·사고 때 대피 순서 (비트박스)") {
                val s = u.signals
                CheckLine(s.hazardOn, "비상등 켜기")
                CheckLine(s.trunkOpen, "트렁크 열기 (뒤차에 알리기)")
                CheckLine(!s.driverSeated && !s.passengerSeated && !s.row2Seated, "가드레일 밖으로 대피")
                CheckLine(false, "신고: 112·119, 한국도로공사 1588-2504", "직접", divider = false)
                Text("차 안이나 차 뒤에 머물지 마세요. 앱은 사고를 감지하지 않고, 비상등을 켰을 때 순서를 안내해요.", fontSize = 15.sp, color = C.sub)
            }
            if (sos) {
                BoostPanel("상담원에게 함께 보낼 차 상황") {
                    Wrap { MyHyundai.emergencySummary(u.signals, u.engine).forEach { Pill(it, null) } }
                    Text("이 화면에서는 차 상황을 요약해 앱 안에 시연 기록을 남겨요. 실제 출동은 마이현대에서 신청하세요.", fontSize = 16.sp, color = C.sub)
                }
                BigButton("긴급출동 요청하기 · 시연", Modifier.fillMaxWidth(), fontSize = 19.sp) { vm.act { sig, _ -> emergency(sig) }; sos = false }
            }
        }
    }, right = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("주변 검색")
            Wrap { MyHyundai.POIS.map { it.category }.distinct().forEach { c -> Chip(c, c == category) { category = c } } }
            if (category == "충전소") Wrap {
                MyHyundai.CHARGER_FILTERS.forEach { (label, _) ->
                    Chip(label, label in chargerFilters, Modifier.testTag("charger_$label")) { chargerFilters = if (label in chargerFilters) chargerFilters - label else chargerFilters + label }
                }
            }
            val shownPois = MyHyundai.POIS.filter { p ->
                p.category == category && (category != "충전소" || MyHyundai.CHARGER_FILTERS.all { (label, test) -> label !in chargerFilters || p.charger?.let(test) == true })
            }
            if (shownPois.isEmpty()) Text("조건에 맞는 충전소가 없어요. 조건을 줄여 보세요.", fontSize = 17.sp, color = C.sub)
            shownPois.forEach { p ->
                Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(p.name, Modifier.weight(1f), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Text("%.1fkm".format(p.distanceKm), fontSize = 18.sp, color = C.sub)
                    }
                    Text(p.note, fontSize = 16.sp, color = C.sub)
                    p.charger?.let { Text("${it.connector} · ${it.spot}", fontSize = 14.sp, color = C.sub) }
                    val arrival = if (p.category == "충전소" && !u.unread("soc"))
                        MyHyundai.arrivalSoc(u.signals.socPct, p.distanceKm, u.signals.outsideTempC <= VerdictEngine.WINTER_C) else null
                    val batteryBadge: @Composable (Modifier) -> Unit = { badgeModifier ->
                        if (arrival != null) {
                            Row(badgeModifier.clip(RoundedCornerShape(10.dp))
                                .background(Brush.horizontalGradient(if (arrival < 5) listOf(Tone.orangeSoft, Color(0xFFFFE9D8)) else listOf(Color(0xFF62E9A3), Color(0xFFE1FFED))))
                                .heightIn(min = 54.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Outlined.Bolt, null, Modifier.size(18.dp), tint = Color(0xFF174B35))
                                Text("도착하면 배터리 약 $arrival%" + if (arrival < 5) " · 도착이 어려울 수 있어요" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF174B35))
                            }
                        }
                    }
                    val guideButton: @Composable (Modifier) -> Unit = { buttonModifier ->
                        BigButton("길 안내", buttonModifier, style = BtnStyle.FILLED, fontSize = 15.sp) {
                            vm.toast("예시 장소: ${p.name} · %.1fkm. 실제 길 안내는 차량 내비게이션에서 시작해 주세요.".format(p.distanceKm))
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f || maxWidth < 280.dp || (arrival != null && arrival < 5)
                        if (stacked) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            batteryBadge(Modifier.fillMaxWidth())
                            guideButton(Modifier.align(Alignment.End))
                        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (arrival != null) batteryBadge(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                            guideButton(Modifier)
                        }
                    }
                }
            }
        }
    })
}

/** 카라이프 위쪽 아이콘 타일. */
@Composable
private fun LifeTile(icon: ImageVector, label: String, bg: Color, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.lift(shape, 4.dp).clip(shape).background(Brush.linearGradient(if (C.dark) listOf(C.chip, C.card) else listOf(bg, bg.copy(alpha = 0.72f))))
        .border(1.dp, if (C.dark) bg.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.65f), shape)
        .clickable(role = Role.Button, onClick = onClick).heightIn(min = 142.dp).padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            if (icon == Icons.Outlined.LocalParking) SculptedIconCell(0, size = 42.dp)
            else Icon(icon, null, Modifier.size(36.dp), tint = if (C.dark) bg else tint)
            SculptedIconCell(5, size = 58.dp)
        }
        Text(label, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
    }
}

// ================================================================== 월렛
@Composable
fun WalletMH(vm: VerifiedRuntime, u: UiState) {
    val school = u.engine.school.points
    TwoCol(left = {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.navy).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row {
                Text("시연 포인트", Modifier.weight(1f), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("%,dP".format(u.settings.memberPoints + school), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Row {
                Text("소멸예정", Modifier.weight(1f), color = Color(0xFFB9CBE6), fontSize = 18.sp)
                Text("0P", color = Color(0xFFB9CBE6), fontSize = 18.sp)
            }
        }
        Card(Modifier.fillMaxWidth(), padding = 24.dp) {
            Barcode(Modifier.fillMaxWidth().height(90.dp))
            Text("블루멤버스 카드", Modifier.fillMaxWidth(), fontSize = 18.sp, color = C.ink, textAlign = TextAlign.Center)
        }
    }, right = {
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            MenuRow(Icons.Outlined.Info, "블루멤버스란?") { vm.toast("블루멤버스 안내는 마이현대 폰 앱에서 볼 수 있어요") }
            MenuRow(Icons.Outlined.School, "스쿨존 재출발 적립", if (school > 0) "+%,dP".format(school) else "${u.engine.school.streak}회") { vm.navigate.value = Module.SCHOOL.name }
            MenuRow(Icons.Outlined.Storefront, "제휴처 안내") { vm.toast("제휴처 안내는 마이현대 폰 앱에서 볼 수 있어요") }
            MenuRow(Icons.Outlined.AccountBalanceWallet, "포인트 사용") { vm.toast("포인트 결제는 주행 안전을 위해 폰에서 해요") }
        }
    })
}

/**
 * 폰 맡김 화면 머리 카드: 폰 홈 판정 카드와 같은 네이비. 작은 이름 · 큰 상태 한 단어(BMW 'ALL GOOD') · 아래 줄(언제부터·도착 예정·경고).
 */
@Composable
fun PhoneHero(kicker: String, word: String, level: Level?, tag: String? = null, lines: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF203445)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(kicker, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (level) { Level.OK -> Icons.Filled.Verified; Level.INFO, null -> Icons.Outlined.Info; else -> Icons.Outlined.Warning },
                null, Modifier.size(26.dp), tint = when (level) {
                    Level.OK -> Color(0xFF95E6C4)
                    Level.WARN -> Color(0xFFFFD38F)
                    Level.DANGER -> Color(0xFFFFB7B8)
                    else -> Color.White
                },
            )
            Text(word, Modifier.padding(start = 8.dp), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        lines()
    }
}

@Composable
fun PhoneHeroLine(text: String) = Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp)

/** 네이비 위의 경고 줄: 밝은 호박색(주의색 그대로는 어두운 바탕에서 묻힌다). */
@Composable
fun PhoneHeroWarn(text: String) = Text("⚠ $text", color = Color(0xFFFCD34D), fontSize = 15.sp, fontWeight = FontWeight.Bold)

/**
 * 단계 표시: 점 + 짧은 이름. 지난 단계는 ✓, 지금 단계는 네이비 번호, 남은 단계는 빈 원.
 * [warnLast]면 마지막 단계를 '!'(미완료)로. 320dp에서도 한 줄에 들어가게 이름은 짧게 쓴다.
 */
@Composable
fun PhoneSteps(labels: List<String>, current: Int, warnLast: Boolean = false) {
    // 큰 글씨·아주 좁은 폰에서는 한글이 글자 단위로 쪼개지므로('기사 동/의') 띄어쓰기에서 줄을 바꾼다
    val cramped = isCramped()
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, t ->
            val last = i == labels.lastIndex
            val done = i < current || (i == current && last && !warnLast)
            val warn = i == current && last && warnLast
            val now = i == current && !done && !warn
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 점 사이 잇는 선: 지나온 쪽은 초록
                    Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else if (i <= current) C.ok else C.line))
                    when {
                        done -> CheckMark(true, 28.dp)
                        warn -> Box(Modifier.size(28.dp).clip(CircleShape).background(C.danger), contentAlignment = Alignment.Center) {
                            Text("!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        }
                        else -> Box(
                            Modifier.size(34.dp).then(if (now) Modifier.border(3.dp, Color(0x663B9BFF), CircleShape) else Modifier).padding(3.dp)
                                .clip(CircleShape).background(if (now) Color(0xFF2F7BE0) else Color.Transparent).border(2.dp, if (now) Color(0xFF2F7BE0) else C.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("${i + 1}", color = if (now) Color.White else C.sub, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(if (last) Color.Transparent else if (i < current) C.ok else C.line))
                }
                Text(
                    if (cramped) t.replace(' ', '\n') else t, Modifier.padding(horizontal = 2.dp), fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2,
                    color = if (now || warn) C.ink else C.sub, fontWeight = if (now || warn) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 폰 확인 근거 카드: 최근 3줄만, 나머지는 '모두 보기'로 편다. */
@Composable
fun PhoneProof(proof: List<com.verified.incar.core.Proof>, tag: String) {
    if (proof.isEmpty()) return
    var all by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().testTag(tag), padding = 16.dp) {
        Text("확인 근거", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Text("시각 · 판정 · 읽은 곳", fontSize = 14.sp, color = C.sub)
        ProofLines(if (all) proof else proof.takeLast(3), max = Int.MAX_VALUE)
        if (proof.size > 3) Text(
            if (all) "접기" else "모두 보기 (${proof.size}줄)",
            Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${tag}_all").clickable(role = Role.Button) { all = !all }.padding(vertical = 12.dp),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.title, textAlign = TextAlign.Center,
        )
    }
}

/** 길게 눌러 확정하거나, 한 번 누른 뒤 명시적으로 확인할 수 있다. */
@Composable
fun HoldButton(text: String, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.SOFT_OK, onConfirm: () -> Unit) {
    var confirming by androidx.compose.runtime.saveable.rememberSaveable(text) { mutableStateOf(false) }
    fun finish() {
        confirming = false
        onConfirm()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BigButton(text, modifier.fillMaxWidth(), style, fontSize = 18.sp, onLongClick = ::finish) { confirming = true }
        if (confirming) {
            Text("이 기록을 마칠까요?", fontSize = 18.sp, color = C.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("취소", Modifier.weight(1f), BtnStyle.OUTLINE, fontSize = 18.sp) { confirming = false }
                BigButton("기록 마치기", Modifier.weight(1f), fontSize = 18.sp, onClick = ::finish)
            }
        }
    }
}

@Composable
fun HandoverScreen(vm: VerifiedRuntime, u: UiState, kind: HandKind) {
    Box(Modifier.fillMaxWidth().padding(horizontal = hPad(40.dp), vertical = 24.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 860.dp)) { HandoverCard(vm, u, kind, focused = true) }
    }
}

/** 홈 바로가기와 차량 관리·운전자 화면이 같은 인수·반납 기록을 사용한다. */
@Composable
fun HandoverCard(vm: VerifiedRuntime, u: UiState, kind: HandKind, focused: Boolean = false) {
    val l = u.engine.lend
    val lend = kind == HandKind.LEND
    val mine = l.kind == kind
    val active = l.active && mine
    // 다시 맡기기: 끝난 리포트(끝난 시각)만 접고 맡기기 전 화면(사진·메모·반납 예정)으로 돌아간다
    var reopened by androidx.compose.runtime.saveable.rememberSaveable(kind) { mutableStateOf<Long?>(null) }
    val returned = !l.active && mine && l.report.isNotEmpty() && reopened != l.endedAtMs
    val currentRoute = when {
        l.active && !mine -> if (l.kind == HandKind.LEND) "HAND_LEND" else "HAND_SERVICE"
        u.engine.valet.active && !active -> "VALET"
        else -> null
    }
    if (currentRoute != null) {
        Card(Modifier.fillMaxWidth(), padding = 18.dp) {
            Text("진행 중인 맡김이 있어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            val label = when (currentRoute) { "HAND_LEND" -> "빌려준 차 보기"; "HAND_SERVICE" -> "정비 맡김 보기"; else -> "대리 주차 보기" }
            BigButton(label, Modifier.fillMaxWidth()) { vm.navigate.value = currentRoute }
        }
        return
    }
    var details by androidx.compose.runtime.saveable.rememberSaveable(kind, active, l.endedAtMs) { mutableStateOf(!focused) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.fillMaxWidth().testTag(if (lend) "lend_hero" else "service_hero"), padding = 24.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBubble(if (lend) Icons.Outlined.Key else Icons.Outlined.Build, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (lend) "빌려주기" else "정비·주차대행 맡김", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(if (returned) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null,
                            Modifier.size(22.dp), tint = if (returned) C.ok else C.title)
                        Text(when { active -> if (lend) "빌려주는 중" else "맡기는 중"; returned -> "돌려받음"; else -> "맡기기 전" },
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (returned) C.ok else C.title)
                        if (returned && l.endedAtMs > 0) Text("· ${VerdictEngine.hhmm(l.endedAtMs)}", fontSize = 18.sp, color = C.sub)
                    }
                }
            }
            if (active) l.start?.timeMs?.let { st ->
                Text("${VerdictEngine.hhmm(st)}부터 " + if (u.carConnected || u.simAllowed) "차가 기록 중이에요" else "시각을 기록 중이에요", fontSize = 21.sp, color = C.ink)
                val m = ((u.nowMs - st) / 60_000).coerceAtLeast(0)
                Row(Modifier.testTag("handover_live"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(C.action))
                    Text("기록 중 · 경과 " + if (m < 60) "${m}분" else "${m / 60}시간 ${m % 60}분", color = C.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!active && !returned) Text("지금 차 상태를 기록하고, 돌려받을 때 달라진 점을 확인해요.", fontSize = 18.sp, color = C.sub)
            if (!returned && u.simAllowed && !u.carConnected) Text("차량 연결 전 · 예시 값 기준", fontSize = 16.sp, color = C.sub)
            else if (returned && l.proof.any { it.src == "시뮬레이터" }) Text("예시 값으로 만든 기록", fontSize = 16.sp, color = C.sub)
            if (active) l.checkIn?.let { check ->
                val distance = check.odometerKm?.let { "%,.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가"
                val energy = check.socPct?.let { "$it%" } ?: "확인 불가"
                HorizontalDivider(color = C.line)
                Text("맡길 때 주행계 $distance · 배터리 $energy", fontSize = 16.sp, color = C.sub)
            }
            // 반납 예정 · 메모(맡기는 중)
            if (active) {
                l.dueMs?.let { due ->
                    // 예정이 지나면 바로 '지남'으로(0분 남음으로 1분 가까이 남지 않게)
                    val left = Math.floorDiv(due - u.nowMs, 60_000L)
                    fun span(mins: Long) = if (mins < 60) "${mins}분" else "${mins / 60}시간 ${mins % 60}분"
                    Text("반납 예정 ${VerdictEngine.hhmm(due)} · " + if (left >= 0) "${span(left)} 남음" else "${span(-left)} 지남",
                        Modifier.testTag("lend_due"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (left < 0) Tone.orange else C.ink)
                }
                if (l.memo.isNotBlank()) Text("메모 · ${l.memo}", Modifier.testTag("lend_memo_shown"), fontSize = 16.sp, color = C.ink)
                var editPlan by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
                TextRow(if (editPlan) "고치기 닫기" else "메모·반납 예정 고치기", tag = "lend_plan_edit") { editPlan = !editPlan }
                if (editPlan) LendPlanEditor(u.nowMs, l.memo, l.dueMs, "저장") { m, d ->
                    vm.act { _, _ -> setLendPlan(m, d); emptyList() }
                    editPlan = false
                }
            }
            if (!lend && mine && l.roamWarned && (active || returned)) l.proof.lastOrNull { it.what.startsWith("정비소 벗어남") }?.let {
                Note("${VerdictEngine.hhmm(it.atMs)} 맡긴 차가 정비소를 벗어났어요 · ${it.what.substringAfter(" · ")}", Level.WARN)
            }
        }
        // 돌려받은 뒤: 이동 거리 · 급감속 · 배터리를 큰 숫자로, 급감속이 있으면 주황 테두리 카드
        val summary = @Composable {
            val rep = l.report.toMap()
            Card(Modifier.fillMaxWidth().testTag("handover_summary"), padding = 12.dp) {
                RecordMetricStrip(listOf("이동 거리", "급감속", "배터리·연료").map { it to (rep[it] ?: "확인 불가") })
            }
            val brakes = rep["급감속"]?.removeSuffix("회")?.toIntOrNull() ?: 0
            if (brakes > 0) Row(Modifier.fillMaxWidth().testTag("handover_brake_alert").clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF6EE)).border(2.dp, Tone.orange, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Warning, null, Modifier.size(28.dp), tint = Tone.orange)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("급감속 ${brakes}회 감지됨", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    // 차가 남긴 근거 줄에서 급감속이 일어난 시각과 그때 속도를 보여 준다
                    val hits = l.proof.filter { it.what.startsWith("급감속") }
                    if (hits.isEmpty()) Text("언제·몇 km/h에서였는지는 세부 기록에 있어요.", fontSize = 15.sp, color = C.sub)
                    else Wrap(6.dp) {
                        hits.takeLast(4).forEach {
                            Text("${VerdictEngine.hhmm(it.atMs)} · ${it.what.substringAfter("· ")}에서", Modifier.clip(RoundedCornerShape(50)).background(Tone.orangeSoft).padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Tone.orange)
                        }
                    }
                }
            }
        }
        // 빌려주기는 숫자 요약을 먼저, 정비는 사진 비교를 먼저 보여 준다
        if (returned) summary()
        // 맡길 때·돌려받을 때 같은 각도 사진. 돌려받은 뒤에는 이력서 기록(시작 시각)에 붙은 사진을 나란히 비교
        val recordKind = if (lend) "빌려주기" else "맡김"
        val photoStart = if (active) l.start?.timeMs else if (returned) u.engine.history.lastOrNull { it.kind == recordKind }?.startMs else null
        HandoverPhotoPanel(vm, kind, photoStart, active, returned, demo = u.trial)
        // 맡기기 전: 반납 예정 시각과 메모를 정해 두면 맡김 기록에 함께 남는다
        // 끝난 맡김마다 새로 정한다(지난번에 적은 메모·예정이 다음 맡김에 몰래 붙지 않게)
        var planMemo by androidx.compose.runtime.saveable.rememberSaveable(kind, l.endedAtMs) { mutableStateOf("") }
        var planDue by androidx.compose.runtime.saveable.rememberSaveable(kind, l.endedAtMs) { mutableStateOf<Long?>(null) }
        if (!active && !returned) Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            // 끝난 맡김마다 편집기 입력을 새로 시작한다(잠금으로 보관된 지난 입력이 다음 맡김에 되살아나지 않게)
            androidx.compose.runtime.key(l.endedAtMs) {
                LendPlanEditor(u.nowMs, "", null, null) { m, d -> planMemo = m; planDue = d }
            }
        }
        if (active) HoldButton("반납 확인", Modifier.testTag(if (lend) "lend_end" else "service_end")) {
            val startMs = l.start?.timeMs
            vm.ownerOnly { vm.act { sig, now ->
                if (state.lend.kind == kind && state.lend.start?.timeMs == startMs) endLend(sig, now) else emptyList()
            } }
        }
        if (returned) Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text(kind.report, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
            // 이동 거리·배터리는 맨 위 숫자 요약에 있으니 여기서는 빼고 이용 시간만
            l.report.take(3).filterNot { it.first in listOf("이동 거리", "급감속", "배터리·연료") }.forEach { (k, v) -> KeyValue(k, v) }
            HorizontalDivider(color = C.line)
            HandoverDiff(l.checkIn, l.checkOut, hide = listOf("주행거리", "배터리"))
            photoStart?.let { st -> TextRow("이 기록 자세히 보기 · PDF", tag = "handover_record_detail") { vm.navigate.value = "RECORD_$st" } }
            HistoryLink(vm, u)
        }
        if (!active) BigButton(
            if (returned) "다시 " + (if (lend) "빌려주기" else "맡기기") else if (lend) "차 빌려주기" else "맡김 시작",
            Modifier.fillMaxWidth().testTag(if (lend) "lend_start" else "service_start"),
            style = if (returned) BtnStyle.OUTLINE else BtnStyle.FILLED, fontSize = 18.sp,
        ) {
            if (returned) reopened = l.endedAtMs
            else {
                val memo = planMemo
                val due = planDue
                vm.ownerOnly { vm.act { sig, now -> startLend(sig, now, kind, memo, due) } }
            }
        }
        if (active || returned) {
            if (focused) TextRow(if (details) "세부 기록 접기" else "체크리스트·세부 기록 보기") { details = !details }
            if (details) {
                Card(Modifier.fillMaxWidth(), padding = 16.dp) {
                    if (returned) l.report.drop(3).forEach { (k, v) -> KeyValue(k, v) }
                    PrepChecklist(vm, u, false, if (active) com.verified.incar.core.PrepPhase.BEFORE else com.verified.incar.core.PrepPhase.RETURN)
                }
                PhoneProof(l.proof, if (lend) "lend_proof" else "service_proof")
            }
        }
    }
}
