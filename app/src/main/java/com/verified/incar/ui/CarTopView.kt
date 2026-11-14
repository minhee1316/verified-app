package com.verified.incar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.CabinSetting
import com.verified.incar.core.ControlKind
import com.verified.incar.core.DoorPart
import com.verified.incar.core.SeatPart
import com.verified.incar.core.VehicleDetails

/** 화면에 그릴 차 신호: 실차면 받은 값, 체험이면 예시 값. */
internal fun UiState.topDetails() = when {
    carConnected -> signals.details
    simAllowed -> VehicleDetails.example(signals, nowMs)
    else -> VehicleDetails()
}

/** 원격 명령을 보낼 수 있는지(체험 차량). 실차 원격 제어는 마이현대가 담당한다. */
internal val UiState.canRemote get() = canControl && simAllowed && !carConnected

/**
 * 위에서 내려다본 차. 직접 그린 단순한 도형이라 차종과 관계없이 같은 모양이다.
 * 외부: 가운데 잠금, 네 문과 트렁크 상태. 실내: 다섯 좌석(운전석 열선 조절, 착석·벨트 상태).
 */
@Composable
fun CarTopView(vm: VerifiedRuntime, u: UiState, interior: Boolean, modifier: Modifier = Modifier) {
    val d = u.topDetails()
    val s = u.signals
    val body = if (C.dark) Color(0xFF3B4658) else Color(0xFFE4E8EE)
    val glass = if (C.dark) Color(0xFF0E1522) else Color(0xFF2B3442)
    val seatColor = if (C.dark) Color(0xFF55627A) else Color(0xFFBCC4D0)
    BoxWithConstraints(modifier.widthIn(max = 260.dp).aspectRatio(0.5f).testTag(if (interior) "topview_interior" else "topview_exterior")) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) { drawCar(body, glass, seatColor, interior) }
        @Composable fun At(fx: Float, fy: Float, size: Dp, content: @Composable () -> Unit) =
            Box(Modifier.offset(w * fx - size / 2, h * fy - size / 2).size(size), contentAlignment = Alignment.Center) { content() }

        if (!interior) {
            val locked = s.doorsLocked
            At(0.5f, 0.52f, 64.dp) {
                RoundAction(if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (locked) "잠김" else "잠금 해제",
                    Modifier.testTag("topview_lock"), tint = Color.White, onDark = true) {
                    if (!u.canRemote) vm.toast("실차 원격 잠금은 마이현대에서 이용해 주세요.")
                    else vm.act { sig, now -> control(if (sig.doorsLocked) ControlKind.UNLOCK else ControlKind.LOCK, sig, now) }
                }
            }
            At(0.5f, 0.88f, 96.dp) {
                Text(if (d.doorOpen[DoorPart.TRUNK]?.value == true || s.trunkOpen) "트렁크\n열림" else "트렁크\n닫힘",
                    Modifier.testTag("topview_trunk"), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            }
            listOf(DoorPart.FRONT_LEFT to (0.06f to 0.42f), DoorPart.FRONT_RIGHT to (0.94f to 0.42f),
                DoorPart.REAR_LEFT to (0.06f to 0.62f), DoorPart.REAR_RIGHT to (0.94f to 0.62f)).forEach { (part, p) ->
                // 문 열림 = 주황 채움, 닫혔지만 잠금 해제 = 회색 테두리, 닫힘·잠김 = 초록
                val open = d.doorOpen[part]?.value == true
                val unlocked = d.doorLocked[part]?.value == false
                At(p.first, p.second, 40.dp) {
                    Box(Modifier.size(22.dp).clip(CircleShape)
                        .background(when { open -> C.warn; unlocked -> Color.White; else -> C.ok })
                        .border(if (unlocked && !open) 3.dp else 2.dp, if (unlocked && !open) C.sub else Color.White, CircleShape)
                        .clickable(role = Role.Button) { vm.toast("${part.label} · ${d.doorLabel(part)}") }
                        .semantics { contentDescription = "${part.label} ${d.doorLabel(part)}" }
                        .testTag("topview_door_${part.name}"))
                }
            }
        } else {
            listOf(SeatPart.DRIVER to (0.34f to 0.42f), SeatPart.PASSENGER to (0.66f to 0.42f),
                SeatPart.REAR_LEFT to (0.30f to 0.64f), SeatPart.REAR_CENTER to (0.50f to 0.64f), SeatPart.REAR_RIGHT to (0.70f to 0.64f)).forEach { (seat, p) ->
                val warn = d.occupied[seat]?.value == true && d.belted[seat]?.value == false
                val heat = when (seat) {
                    SeatPart.DRIVER -> s.seatHeatDriver
                    SeatPart.PASSENGER -> s.seatHeatPassenger
                    else -> s.seatHeatRear
                }
                At(p.first, p.second, 52.dp) {
                    Column(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                        .then(if (warn) Modifier.border(2.dp, C.danger, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable(role = Role.Button) {
                            // 허용 여부(체험 차량·지원 차종·정차)는 setCabin이 판단하고 안내한다
                            if (seat == SeatPart.DRIVER) vm.setCabin(CabinSetting.from(s).copy(driverHeat = (s.seatHeatDriver + 1) % 4))
                            else vm.toast("${seat.label} · ${d.seatLabel(seat)}")
                        }
                        .semantics { contentDescription = "${seat.label} ${d.seatLabel(seat)}" + if (heat > 0) " 열선 ${heat}단" else "" }
                        .testTag("topview_seat_${seat.name}"),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        // 열선이 켜진 좌석만 아이콘을 보여 준다(꺼진 좌석은 비워 둔다)
                        if (heat > 0) {
                            Icon(Icons.Outlined.Whatshot, null, Modifier.size(20.dp), tint = Color(0xFFFF8A4C))
                            Text("${heat}단", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else if (warn) Text("벨트", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawCar(body: Color, glass: Color, seat: Color, interior: Boolean) {
    val w = size.width
    val h = size.height
    val tire = Color(0xFF1A1D22)
    // 바퀴(차체 밖으로 살짝)
    listOf(0.20f, 0.78f).forEach { y ->
        listOf(0.09f, 0.79f).forEach { x ->
            drawRoundRect(tire, Offset(w * x, h * y), Size(w * 0.12f, h * 0.11f), CornerRadius(w * 0.03f))
        }
    }
    // 사이드미러
    drawOval(body, Offset(w * 0.04f, h * 0.30f), Size(w * 0.12f, h * 0.04f))
    drawOval(body, Offset(w * 0.84f, h * 0.30f), Size(w * 0.12f, h * 0.04f))
    // 차체
    drawRoundRect(body, Offset(w * 0.13f, h * 0.03f), Size(w * 0.74f, h * 0.94f), CornerRadius(w * 0.30f, w * 0.26f))
    if (!interior) {
        // 앞유리 · 지붕(유리) · 뒷유리
        val ws = Path().apply {
            moveTo(w * 0.22f, h * 0.36f); lineTo(w * 0.78f, h * 0.36f); lineTo(w * 0.72f, h * 0.27f)
            quadraticTo(w * 0.5f, h * 0.245f, w * 0.28f, h * 0.27f); close()
        }
        drawPath(ws, glass)
        drawRoundRect(glass.copy(alpha = 0.85f), Offset(w * 0.23f, h * 0.375f), Size(w * 0.54f, h * 0.29f), CornerRadius(w * 0.05f))
        val rear = Path().apply {
            moveTo(w * 0.24f, h * 0.675f); lineTo(w * 0.76f, h * 0.675f); lineTo(w * 0.71f, h * 0.75f)
            quadraticTo(w * 0.5f, h * 0.765f, w * 0.29f, h * 0.75f); close()
        }
        drawPath(rear, glass)
        // 트렁크 덮개
        drawRoundRect(glass.copy(alpha = 0.55f), Offset(w * 0.25f, h * 0.80f), Size(w * 0.50f, h * 0.15f), CornerRadius(w * 0.10f))
        // 문 경계선
        listOf(0.37f, 0.53f, 0.68f).forEach { y ->
            drawLine(glass.copy(alpha = 0.35f), Offset(w * 0.13f, h * y), Offset(w * 0.22f, h * y), strokeWidth = 2f)
            drawLine(glass.copy(alpha = 0.35f), Offset(w * 0.78f, h * y), Offset(w * 0.87f, h * y), strokeWidth = 2f)
        }
    } else {
        // 실내 바닥 · 대시보드 · 센터 콘솔
        drawRoundRect(glass, Offset(w * 0.19f, h * 0.24f), Size(w * 0.62f, h * 0.56f), CornerRadius(w * 0.08f))
        drawRoundRect(seat.copy(alpha = 0.6f), Offset(w * 0.19f, h * 0.24f), Size(w * 0.62f, h * 0.05f), CornerRadius(w * 0.04f))
        drawRoundRect(seat.copy(alpha = 0.7f), Offset(w * 0.46f, h * 0.36f), Size(w * 0.08f, h * 0.15f), CornerRadius(w * 0.03f))
        // 좌석 바탕(앞 2, 뒤 3)
        listOf(0.34f, 0.66f).forEach { x -> drawRoundRect(seat, Offset(w * (x - 0.10f), h * 0.37f), Size(w * 0.20f, h * 0.11f), CornerRadius(w * 0.04f)) }
        drawRoundRect(seat, Offset(w * 0.21f, h * 0.59f), Size(w * 0.58f, h * 0.11f), CornerRadius(w * 0.04f))
    }
}

/** 둥근 아이콘 버튼 + 아래 이름. 결과를 기다리는 중이면 링이 돈다. 길게 누르기는 선택(빠른 제어 편집). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun RoundAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = C.title, onDark: Boolean = false,
                busy: Boolean = false, active: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .combinedClickable(role = Role.Button, onClickLabel = label, onLongClickLabel = onLongClick?.let { "순서 바꾸기" }, onLongClick = onLongClick, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (busy) SpinnerRing(30.dp, Modifier.testTag("control_busy")) { Icon(icon, null, Modifier.size(16.dp), tint = tint) }
        else Icon(icon, null, Modifier.size(26.dp), tint = if (active) Color(0xFF3B9BFF) else tint)
        Text(label, color = if (onDark) Color.White else C.sub, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/**
 * 홈·제어 화면의 빠른 제어 줄: 잠금 · 공조 · 비상등 · 경적.
 * 아무 버튼이나 길게 누르면 편집: 화살표로 순서를 바꾸고 완료를 누른다(테슬라·루시드·폴스타처럼 순서를 사용자가 정함).
 */
@Composable
fun QuickControls(vm: VerifiedRuntime, u: UiState, modifier: Modifier = Modifier) {
    val s = u.signals
    val pending = u.engine.pendingControl
    val busy = pending?.takeUnless { it.unconfirmed }?.kind
    var editing by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    fun send(kind: (com.verified.incar.core.Signals) -> ControlKind) {
        if (!u.canRemote) vm.toast(if (u.settings.carModel == null) "차량을 등록하면 원격 제어를 쓸 수 있어요." else "실차 원격 제어는 마이현대에서 이용해 주세요.")
        else vm.act { sig, now -> control(kind(sig), sig, now) }
    }
    data class Item(val icon: ImageVector, val label: String, val busy: Boolean, val active: Boolean, val run: () -> Unit)
    val items = mapOf(
        "LOCK" to Item(if (s.doorsLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, u.read("lock", if (s.doorsLocked) "잠김" else "잠금 해제"),
            busy == ControlKind.LOCK || busy == ControlKind.UNLOCK, false) { send { if (it.doorsLocked) ControlKind.UNLOCK else ControlKind.LOCK } },
        "AC" to Item(Icons.Outlined.AcUnit, u.read("ac", if (s.acOn) "공조 켜짐" else "공조"),
            busy == ControlKind.CLIMATE_ON || busy == ControlKind.CLIMATE_OFF, s.acOn) { send { if (it.acOn) ControlKind.CLIMATE_OFF else ControlKind.CLIMATE_ON } },
        "HAZARD" to Item(Icons.Outlined.Warning, "비상등", busy == ControlKind.HAZARD, s.hazardOn) { send { ControlKind.HAZARD } },
        "HORN" to Item(Icons.Outlined.Campaign, "경적", false, false) { send { ControlKind.HORN } },
    )
    val order = com.verified.incar.core.normalizeQuickOrder(u.settings.quickOrder)
    val tagOf = mapOf("LOCK" to "quick_lock", "AC" to "quick_ac", "HAZARD" to "quick_hazard", "HORN" to "quick_horn")
    Column(modifier.fillMaxWidth().testTag("quick_controls"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            order.forEachIndexed { i, key ->
                val it = items.getValue(key)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    RoundAction(it.icon, it.label, Modifier.testTag(tagOf.getValue(key)), busy = it.busy, active = it.active,
                        onLongClick = { editing = true }) { if (editing) Unit else it.run() }
                    if (editing) Row {
                        androidx.compose.material3.IconButton({ vm.settings { st -> st.copy(quickOrder = com.verified.incar.core.moveQuick(st.quickOrder, key, -1)) } },
                            Modifier.size(40.dp).testTag("quick_left_$key"), enabled = i > 0) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "${it.label} 앞으로", tint = if (i > 0) C.ink else C.line)
                        }
                        androidx.compose.material3.IconButton({ vm.settings { st -> st.copy(quickOrder = com.verified.incar.core.moveQuick(st.quickOrder, key, 1)) } },
                            Modifier.size(40.dp).testTag("quick_right_$key"), enabled = i < order.lastIndex) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "${it.label} 뒤로", tint = if (i < order.lastIndex) C.ink else C.line)
                        }
                    }
                }
            }
        }
        if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("화살표로 순서를 바꿔요", Modifier.weight(1f), fontSize = 14.sp, color = C.sub)
            androidx.compose.material3.TextButton({ editing = false }, Modifier.testTag("quick_done")) { Text("완료", color = C.title, fontWeight = FontWeight.Bold) }
        }
    }
}

/** 실내 보기 아래: 공조 전원 · 온도 < 22.0° > · 성에 제거. */
@Composable
fun CabinTempBar(vm: VerifiedRuntime, u: UiState) {
    val s = u.signals
    val setting = CabinSetting.from(s)
    fun change(next: CabinSetting) = vm.setCabin(next)
    Column(Modifier.fillMaxWidth().testTag("cabin_bar"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("실내 공조 ${u.read("hvacPower", if (s.hvacPowerOn) "켜짐" else "꺼짐")} · 바람 ${if (s.fanSpeed == 0) "꺼짐" else "${s.fanSpeed}단"}", color = C.sub, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RoundAction(Icons.Outlined.PowerSettingsNew, if (s.hvacPowerOn) "끄기" else "켜기", Modifier.testTag("cabin_power"), active = s.hvacPowerOn) {
                change(setting.copy(power = !s.hvacPowerOn, fanSpeed = if (s.hvacPowerOn) 0 else maxOf(2, s.fanSpeed)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton({ change(setting.copy(temperatureC = s.hvacTempC - 0.5f)) }, Modifier.size(48.dp).testTag("cabin_temp_down")) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "온도 낮추기", tint = C.ink)
                }
                Text(u.read("hvacTemp", "%.1f°".format(s.hvacTempC)), Modifier.testTag("cabin_temp"), color = C.ink, fontSize = 36.sp, fontWeight = FontWeight.Light)
                androidx.compose.material3.IconButton({ change(setting.copy(temperatureC = s.hvacTempC + 0.5f)) }, Modifier.size(48.dp).testTag("cabin_temp_up")) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "온도 높이기", tint = C.ink)
                }
            }
            RoundAction(Icons.Outlined.Air, "성에 제거", Modifier.testTag("cabin_defrost"), active = s.defrosterOn) {
                change(setting.copy(defrost = !s.defrosterOn))
            }
        }
    }
}
