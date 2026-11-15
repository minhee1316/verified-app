package com.verified.incar.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.group
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.outlined.Cyclone
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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

/** 실사 외부와 밝은 실내 시트 도해. 상태 글자와 터치 영역은 이미지와 별도로 유지한다. */
@Composable
fun CarTopView(vm: VerifiedRuntime, u: UiState, interior: Boolean, modifier: Modifier = Modifier, compactInterior: Boolean = false, maxStageHeight: Dp? = null) {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = isCarScreen() || config.screenWidthDp > config.screenHeightDp
    if (interior && landscape) {
        PhotoCabinControls(vm, u, modifier)
        return
    }
    val d = u.topDetails()
    val s = u.signals
    val dark = C.dark
    val heatKnown = !u.unread("seatHeat")
    val heats = if (heatKnown) listOf(s.seatHeatDriver, s.seatHeatPassenger, s.seatHeatRear, s.seatHeatRear, s.seatHeatRear) else emptyList()
    BoxWithConstraints(modifier.widthIn(max = 440.dp).testTag(if (interior) "topview_interior" else "topview_exterior")) {
        val w = maxWidth
        // Rotation/layout transitions can briefly collapse the available width. Do not
        // divide by that width when positioning native door and seat hit targets.
        if (w <= 0.dp || !w.value.isFinite()) return@BoxWithConstraints
        val carW = if (interior) w * if (compactInterior) 0.96f else 0.70f
            else minOf(w * 0.48f, maxStageHeight?.times(0.48f) ?: w)
        val h = carW / if (interior) 0.52f else 0.48f
        val carFrac = carW / w
        val compactHeight = maxStageHeight != null
        Box(Modifier.fillMaxWidth().height(h)) {
            if (interior) {
                Canvas(Modifier.align(Alignment.Center).size(carW, h)) { drawPhoneCabin(dark, heats) }
            } else {
                val locked = s.doorsLocked
                val lockKnown = !u.unread("lock")
                Box(Modifier.align(Alignment.Center).size(carW, h)
                    .testTag("topview_lock")
                    .clickable(role = Role.Button, onClickLabel = if (locked) "잠금 해제하기" else "잠그기") {
                        if (!u.canRemote) vm.toast("실차 원격 잠금은 마이현대에서 이용해 주세요.")
                        else vm.act { sig, now -> control(if (sig.doorsLocked) ControlKind.UNLOCK else ControlKind.LOCK, sig, now) }
                    }.semantics { contentDescription = "차량 전체 잠금 " + if (!lockKnown) "확인 불가" else if (locked) "잠김" else "잠금 해제" },
                    contentAlignment = Alignment.Center) {
                    TopCarPicture(u, Modifier.fillMaxSize())
                    if (!lockKnown) Text("확인 불가", Modifier.clip(RoundedCornerShape(20.dp)).background(C.card).padding(8.dp),
                        color = C.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            @Composable fun At(fx: Float, fy: Float, bw: Dp, bh: Dp, content: @Composable () -> Unit) =
                Box(Modifier.offset(w * fx - bw / 2, h * fy - bh / 2).size(bw, bh), contentAlignment = Alignment.Center) { content() }
            fun onCar(cx: Float) = 0.5f + (cx - 0.5f) * carFrac

            if (!interior) {
                val pillW = minOf(86.dp, w * 0.25f).coerceAtLeast(48.dp)
                val side = (pillW / 2 + 2.dp) / w
                val frontLabelY = if (compactHeight) 0.26f else 0.38f
                val rearLabelY = if (compactHeight) 0.61f else 0.69f
                val frontDoorY = if (compactHeight) 0.39f else 0.49f
                val rearDoorY = 0.69f
                val lineColor = C.title
                val trunkWidth = minOf(w * 0.48f, 140.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceIn(1f, 1.5f))
                val trunkY = if (compactHeight) 0.9f else 0.94f
                Canvas(Modifier.fillMaxSize()) {
                    val x0 = (pillW + 2.dp).toPx()
                    val x1 = size.width * (0.5f - carFrac * 0.43f)
                    listOf(frontLabelY to frontDoorY, rearLabelY to rearDoorY).forEach { (labelY, doorY) ->
                        val y0 = size.height * labelY
                        val y1 = size.height * doorY
                        listOf(false, true).forEach { right ->
                            fun x(value: Float) = if (right) size.width - value else value
                            val elbow = (x0 + x1) / 2f
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x(x0), y0)
                                lineTo(x(elbow - 5.dp.toPx()), y0)
                                quadraticTo(x(elbow), y0, x(elbow), y0 + (y1 - y0) * 0.25f)
                                lineTo(x(elbow), y1 - (y1 - y0) * 0.25f)
                                quadraticTo(x(elbow), y1, x(elbow + 5.dp.toPx()), y1)
                                lineTo(x(x1), y1)
                            }
                            drawPath(path, lineColor.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                            drawCircle(Color.White, 4.5.dp.toPx(), Offset(x(x1), y1))
                            drawCircle(lineColor, 3.dp.toPx(), Offset(x(x1), y1))
                        }
                    }
                    val trunkPoint = Offset(size.width * 0.5f, size.height * trunkY)
                    drawLine(lineColor.copy(alpha = 0.85f), trunkPoint, Offset(size.width - trunkWidth.toPx(), trunkPoint.y), 1.2.dp.toPx())
                    drawCircle(Color.White, 4.5.dp.toPx(), trunkPoint)
                    drawCircle(lineColor, 3.dp.toPx(), trunkPoint)
                }
                listOf(DoorPart.FRONT_LEFT to (side to frontLabelY), DoorPart.FRONT_RIGHT to (1f - side to frontLabelY),
                    DoorPart.REAR_LEFT to (side to rearLabelY), DoorPart.REAR_RIGHT to (1f - side to rearLabelY)).forEach { (part, position) ->
                    val open = d.doorOpen[part]?.value
                    val lock = d.doorLocked[part]?.value
                    val known = open != null && lock != null
                    At(position.first, position.second, pillW, 44.dp) {
                        StatePill(when { open == true -> "열림"; !known -> "미수신"; lock == false -> "풀림"; else -> "잠김" },
                            null, when { open == true -> PillTone.WARN; !known -> PillTone.PLAIN; lock == false -> PillTone.UNLOCK; else -> PillTone.ON },
                            Modifier.semantics { contentDescription = "${part.label} ${d.doorLabel(part)}" }.testTag("topview_door_${part.name}")) {
                            vm.toast("${part.label} · ${d.doorLabel(part)}")
                        }
                    }
                }
                val trunkOpen = d.doorOpen[DoorPart.TRUNK]?.value
                val trunkLabel = when (trunkOpen) { true -> "트렁크 열림"; false -> "트렁크 닫힘"; null -> "트렁크 미수신" }
                At(1f - (trunkWidth / 2 + 2.dp) / w, trunkY, trunkWidth, 44.dp) {
                    StatePill(trunkLabel, null, if (trunkOpen == true) PillTone.WARN else PillTone.PLAIN,
                        Modifier.semantics { contentDescription = trunkLabel }.testTag("topview_trunk")) {
                        vm.toast(when (trunkOpen) { true -> "트렁크가 열려 있어요."; false -> "트렁크가 닫혀 있어요."; null -> "트렁크 신호를 받지 못했어요." })
                    }
                }
            } else {
                val positions = listOf(SeatPart.DRIVER to (0.25f to 0.435f), SeatPart.PASSENGER to (0.75f to 0.435f),
                    SeatPart.REAR_LEFT to (0.20f to 0.75f), SeatPart.REAR_CENTER to (0.50f to 0.75f), SeatPart.REAR_RIGHT to (0.80f to 0.75f))
                positions.forEach { (seat, position) ->
                    val front = seat == SeatPart.DRIVER || seat == SeatPart.PASSENGER
                    val sw = minOf(if (front) 72.dp else 54.dp, carW * if (front) 0.33f else 0.24f).coerceAtLeast(44.dp)
                    At(onCar(position.first), position.second, sw, if (front) 76.dp else 64.dp) {
                        VehicleSeatControl(vm, u, seat, photo = false, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleSeatControl(vm: VerifiedRuntime, u: UiState, seat: SeatPart, photo: Boolean, modifier: Modifier = Modifier) {
    val s = u.signals
    val d = u.topDetails()
    val known = !u.unread("seatHeat")
    val heat = when (seat) { SeatPart.DRIVER -> s.seatHeatDriver; SeatPart.PASSENGER -> s.seatHeatPassenger; else -> s.seatHeatRear }
    val warn = d.occupied[seat]?.value == true && d.belted[seat]?.value == false
    val active = known && heat > 0
    val color = if (warn) C.danger else if (photo) Color.White else if (active) Color(0xFF263344) else Color(0xFF8F96A1)
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .then(if (warn) Modifier.border(2.dp, C.danger, RoundedCornerShape(12.dp)) else Modifier)
        .clickable(role = Role.Button) {
            if (seat == SeatPart.DRIVER) vm.setCabin(CabinSetting.from(s).copy(driverHeat = (s.seatHeatDriver + 1) % 4))
            else vm.toast("${seat.label} · ${d.seatLabel(seat)}")
        }.semantics { contentDescription = "${seat.label} ${d.seatLabel(seat)}" + if (!known) " 열선 확인 불가" else if (heat > 0) " 열선 ${heat}단" else " 열선 꺼짐" }
        .testTag("topview_seat_${seat.name}"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (active) HeatWaves(Modifier.size(if (photo) 22.dp else 26.dp), if (photo) Color.White else Color(0xFFE27825))
        Text(if (warn) "벨트" else if (!known) "—" else if (active) "${heat}단" else "끔",
            Modifier.then(if (photo) Modifier.clip(RoundedCornerShape(20.dp)).background(if (active) Color(0xBBDD6B22) else Color(0x990F253B)).padding(horizontal = 7.dp, vertical = 3.dp) else Modifier),
            color = color, fontSize = if (photo) 14.sp else 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhotoCabinControls(vm: VerifiedRuntime, u: UiState, modifier: Modifier) {
    val s = u.signals
    val heats = if (u.unread("seatHeat")) emptyList() else listOf(s.seatHeatDriver, s.seatHeatPassenger, s.seatHeatRear, s.seatHeatRear, s.seatHeatRear)
    BoxWithConstraints(modifier.widthIn(max = 640.dp).testTag("topview_interior")) {
        val w = maxWidth
        val h = w * 0.50f
        Box(Modifier.fillMaxWidth().height(h)) {
            CabinPhoto(Modifier.fillMaxSize(), landscape = true, heats = heats)
            listOf(SeatPart.DRIVER to (0.48f to 0.67f), SeatPart.PASSENGER to (0.48f to 0.32f),
                SeatPart.REAR_LEFT to (0.70f to 0.70f), SeatPart.REAR_CENTER to (0.70f to 0.50f), SeatPart.REAR_RIGHT to (0.70f to 0.30f)).forEach { (seat, position) ->
                VehicleSeatControl(vm, u, seat, photo = true,
                    Modifier.offset(w * position.first - 26.dp, h * position.second - 28.dp).size(52.dp, 56.dp))
            }
        }
    }
}

/** 출발 준비는 참고안처럼 가로로 누운 실사 실내에 열선 조작을 얹는다. */
@Composable
fun SeatHeatMap(driver: Int, passenger: Int, rear: Int, modifier: Modifier = Modifier, onDriver: () -> Unit, onPassenger: () -> Unit, onRear: () -> Unit) {
    BoxWithConstraints(modifier.aspectRatio(1.8f).testTag("comfort_seats")) {
        val w = maxWidth
        val h = maxHeight
        CabinPhoto(Modifier.fillMaxSize(), landscape = true, heats = listOf(driver, passenger, rear, rear, rear))
        @Composable fun Seat(fx: Float, fy: Float, label: String, level: Int, tag: String, onClick: () -> Unit) {
            Column(Modifier.offset(w * fx - 30.dp, h * fy - 28.dp).size(60.dp, 56.dp).clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = "$label 열선 " + if (level > 0) "${level}단" else "끔" }.testTag(tag),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (level > 0) HeatWaves(Modifier.size(22.dp), Color.White)
                Text(if (level > 0) "${level}단" else "끔", Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (level > 0) Color(0xC0E57826) else Color(0xAA20334B)).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Seat(0.48f, 0.67f, "운전석", driver, "comfort_seat_driver", onDriver)
        Seat(0.48f, 0.32f, "조수석", passenger, "comfort_seat_passenger", onPassenger)
        Seat(0.70f, 0.50f, "뒷좌석", rear, "comfort_seat_rear", onRear)
    }
}

private enum class PillTone { ON, PLAIN, UNLOCK, WARN }

/** 참고 이미지의 흰색 상태 알약. 정상 상태도 채움색 대신 문구로 구분한다. */
@Composable
private fun StatePill(label: String, icon: ImageVector?, tone: PillTone, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = C.dark
    val fg = when (tone) { PillTone.WARN, PillTone.UNLOCK -> if (dark) Color(0xFFFFBE76) else Color(0xFFA95D1A); else -> C.ink }
    val bg = if (dark) Color(0xFF354051) else Color.White
    val edge = if (tone == PillTone.WARN || tone == PillTone.UNLOCK) fg.copy(alpha = 0.55f) else if (dark) Color(0xFF778496) else Color(0xFFAFB5BF)
    BoxWithConstraints(modifier.fillMaxSize().clickable(role = Role.Button, onClick = onClick)) {
        val tight = maxWidth < 80.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
        Row(Modifier.fillMaxSize().lift(RoundedCornerShape(24.dp), 5.dp).clip(RoundedCornerShape(24.dp)).background(bg)
            .border(1.2.dp, edge, RoundedCornerShape(24.dp)).padding(horizontal = if (tight) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
            if (icon != null && !tight) Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Text(label, color = fg, fontSize = if (tight && label == "미수신") 12.sp else 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

/** 실사 기반 차량 그래픽. 정확한 차종 외관은 차량 둘러보기의 공식 사진으로 제공한다. */
@Composable
internal fun TopCarPicture(u: UiState, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.verified.incar.R.drawable.reference_sedan_top), null,
        modifier, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
}

/** 동행 화면은 지붕을 걷어낸 실사 실내를 사용한다. */
@Composable
internal fun AlightLightCar(modifier: Modifier = Modifier, showLights: Boolean = true) {
    Box(modifier) {
        CabinPhoto(Modifier.fillMaxSize(), landscape = false)
        if (showLights) Canvas(Modifier.fillMaxSize()) {
            listOf(Triple(0.92f, 0.36f, Color(0xFF3DDC97)), Triple(0.08f, 0.50f, Color(0xFFFF7A7D))).forEach { (x, y, color) ->
                val from = Offset(size.width * x, size.height * y)
                val to = Offset(size.width * x, size.height * (y + 0.18f))
                drawLine(color.copy(alpha = 0.18f), from, to, size.width * 0.065f, StrokeCap.Round)
                drawLine(color, from, to, size.width * 0.016f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun CabinPhoto(modifier: Modifier, landscape: Boolean, heats: List<Int> = emptyList()) {
    BoxWithConstraints(modifier) {
        val imageWidth = if (landscape) maxHeight else maxWidth
        val imageHeight = if (landscape) maxWidth else maxHeight
        val artworkWidth = minOf(imageWidth, imageHeight * 0.5f)
        Box(Modifier.align(Alignment.Center).requiredSize(artworkWidth, artworkWidth * 2f)
            .then(if (landscape) Modifier.graphicsLayer { rotationZ = -90f } else Modifier)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.verified.incar.R.drawable.reference_sedan_cabin), null,
                Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            if (heats.any { it > 0 }) Canvas(Modifier.fillMaxSize()) { drawPhotoSeatHeat(heats) }
        }
    }
}

@Composable
private fun HeatWaves(modifier: Modifier, color: Color) = Canvas(modifier) {
    listOf(0.22f, 0.5f, 0.78f).forEach { x ->
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * x, size.height * 0.88f)
            cubicTo(size.width * (x + 0.20f), size.height * 0.58f, size.width * (x - 0.20f), size.height * 0.40f, size.width * x, size.height * 0.10f)
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.1.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** 둥근 아이콘 버튼 + 아래 이름. 결과를 기다리는 중이면 링이 돈다. 길게 누르기는 선택(빠른 제어 편집). */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun RoundAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = C.title, onDark: Boolean = false,
                busy: Boolean = false, active: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val car = isCarScreen()
    val shape = if (car) RoundedCornerShape(16.dp) else CircleShape
    Column(modifier
        .combinedClickable(role = Role.Button, onClickLabel = label, onLongClickLabel = onLongClick?.let { "순서 바꾸기" }, onLongClick = onLongClick, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(if (car) 64.dp else 62.dp).lift(shape, if (car) 3.dp else 7.dp).clip(shape)
            .background(if (car && active) C.ice else C.card)
            .border(if (car) 1.dp else if (active) 5.dp else 2.dp, if (car) C.line else if (active) Color(0xFF0878DC) else C.line, shape)
            .padding(6.dp), contentAlignment = Alignment.Center) {
            if (busy) SpinnerRing(36.dp, Modifier.testTag("control_busy")) { Icon(icon, null, Modifier.size(20.dp), tint = tint) }
            else Icon(icon, null, Modifier.size(if (car) 28.dp else 30.dp), tint = tint)
        }
        Text(label, color = if (onDark) Color.White else C.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/**
 * 홈·제어 화면의 빠른 제어 줄: 잠금 · 공조 · 비상등 · 경적.
 * 아무 버튼이나 길게 누르면 편집: 화살표로 순서를 바꾸고 완료를 누른다(테슬라·루시드·폴스타처럼 순서를 사용자가 정함).
 */
@Composable
fun QuickControls(vm: VerifiedRuntime, u: UiState, modifier: Modifier = Modifier, editState: androidx.compose.runtime.MutableState<Boolean>? = null) {
    if (isCarScreen()) return CarQuickControls(vm, u, modifier)
    val s = u.signals
    val pending = u.engine.pendingControl
    val busy = pending?.takeUnless { it.unconfirmed }?.kind
    val localEditing = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var editing by (editState ?: localEditing)
    fun send(kind: (com.verified.incar.core.Signals) -> ControlKind) {
        if (!u.canRemote) vm.toast("실제 차량의 원격 제어는 마이현대에서 이용해 주세요.")
        else vm.act { sig, now -> control(kind(sig), sig, now) }
    }
    data class Item(val icon: ImageVector, val label: String, val busy: Boolean, val active: Boolean, val run: () -> Unit)
    val items = mapOf(
        "LOCK" to Item(if (s.doorsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen, u.read("lock", if (s.doorsLocked) "잠김" else "잠금 해제"),
            busy == ControlKind.LOCK || busy == ControlKind.UNLOCK, s.doorsLocked) { send { if (it.doorsLocked) ControlKind.UNLOCK else ControlKind.LOCK } },
        "AC" to Item(CabinFanIcon, u.read("ac", if (s.acOn) "공조 켜짐" else "공조"),
            busy == ControlKind.CLIMATE_ON || busy == ControlKind.CLIMATE_OFF, s.acOn) { send { if (it.acOn) ControlKind.CLIMATE_OFF else ControlKind.CLIMATE_ON } },
        "HAZARD" to Item(Icons.Filled.Warning, "비상등", busy == ControlKind.HAZARD, s.hazardOn) { send { ControlKind.HAZARD } },
        "HORN" to Item(Icons.Filled.Campaign, "경적", false, false) { send { ControlKind.HORN } },
    )
    val order = com.verified.incar.core.normalizeQuickOrder(u.settings.quickOrder)
    val tagOf = mapOf("LOCK" to "quick_lock", "AC" to "quick_ac", "HAZARD" to "quick_hazard", "HORN" to "quick_horn")
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val columns = if (maxWidth < 310.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) 2 else 4
    Column(Modifier.fillMaxWidth().testTag("quick_controls"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        order.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            row.forEach { key ->
                val i = order.indexOf(key)
                val it = items.getValue(key)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    RoundAction(it.icon, it.label, Modifier.testTag(tagOf.getValue(key)), tint = if (key == "HAZARD") C.danger else C.title, busy = it.busy, active = it.active,
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
        }
        if (editing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("화살표로 순서를 바꿔요", Modifier.weight(1f), fontSize = 14.sp, color = C.sub)
            androidx.compose.material3.TextButton({ editing = false }, Modifier.testTag("quick_done")) { Text("완료", color = C.title, fontWeight = FontWeight.Bold) }
        }
        if (!editing) u.engine.controlLog.firstOrNull()?.let { result ->
            val demo = u.simAllowed && !u.carConnected
            val status = when {
                demo && result.kind == ControlKind.HORN -> "경적 시연 요청됨"
                else -> "${if (demo) "시연 · " else ""}${result.kind.label} · ${result.result.label}"
            }
            Row(Modifier.fillMaxWidth().testTag("quick_feedback").semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (result.result.level == com.verified.incar.core.Level.OK) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    null, Modifier.size(18.dp), tint = if (result.result.level == com.verified.incar.core.Level.OK) C.ok else C.sub)
                Text(status, fontSize = 14.sp, color = C.sub)
            }
        }
    }
    }
}

/**
 * 차 디스플레이의 빠른 제어: 차 안에 탄 사람이 바로 쓰는 것만(공조 · 운전석 열선 · 성에 제거 · 바람).
 * 경적·비상등·원격 잠금은 차 밖에서 휴대폰으로 쓰는 기능이라 차 화면에서는 뺀다.
 */
@Composable
private fun CarQuickControls(vm: VerifiedRuntime, u: UiState, modifier: Modifier) {
    val s = u.signals
    val setting = CabinSetting.from(s)
    fun change(next: CabinSetting) = vm.setCabin(next)
    Row(modifier.fillMaxWidth().testTag("quick_controls"), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(Icons.Outlined.AcUnit, u.read("hvacPower", if (s.hvacPowerOn) "공조 켜짐" else "공조"), Modifier.weight(1f).testTag("carquick_ac"), active = s.hvacPowerOn) {
            change(setting.copy(power = !s.hvacPowerOn, fanSpeed = if (s.hvacPowerOn) 0 else maxOf(2, s.fanSpeed)))
        }
        RoundAction(Icons.Outlined.Whatshot, if (s.seatHeatDriver > 0) "열선 ${s.seatHeatDriver}단" else "운전석 열선", Modifier.weight(1f).testTag("carquick_seat"), active = s.seatHeatDriver > 0) {
            change(setting.copy(driverHeat = (s.seatHeatDriver + 1) % 4))
        }
        RoundAction(Icons.Outlined.Air, "성에 제거", Modifier.weight(1f).testTag("carquick_defrost"), active = s.defrosterOn) {
            change(setting.copy(defrost = !s.defrosterOn))
        }
        // 받지 못한 바람 세기는 '꺼짐'이 아니라 확인 불가로 보인다(옆 공조 칸과 같은 기준)
        RoundAction(Icons.Outlined.Cyclone, "바람 " + u.read("fanSpeed", if (s.fanSpeed == 0) "꺼짐" else "${s.fanSpeed}단"), Modifier.weight(1f).testTag("carquick_fan"), active = !u.unread("fanSpeed") && s.fanSpeed > 0) {
            change(setting.copy(power = true, fanSpeed = s.fanSpeed % 7 + 1))
        }
    }
}

/** 폰 실내 보기: 좌석 옆에서 온도와 공조를 바로 조절한다. */
@Composable
fun CabinTempRail(vm: VerifiedRuntime, u: UiState, modifier: Modifier = Modifier) {
    val s = u.signals
    val setting = CabinSetting.from(s)
    fun change(next: CabinSetting) = vm.setCabin(next)
    BoxWithConstraints(modifier) {
    val stackActions = maxWidth < 112.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
    Column(Modifier.fillMaxWidth().testTag("cabin_bar")
        .lift(RoundedCornerShape(20.dp), 9.dp).clip(RoundedCornerShape(20.dp)).background(C.card).padding(horizontal = 4.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("실내 공조", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("${u.read("hvacPower", if (s.hvacPowerOn) "켜짐" else "꺼짐")} · 바람 ${u.read("fanSpeed", if (s.fanSpeed == 0) "꺼짐" else "${s.fanSpeed}단")}",
            color = C.sub, fontSize = 12.sp, textAlign = TextAlign.Center)
        androidx.compose.material3.IconButton({ change(setting.copy(temperatureC = s.hvacTempC + 0.5f)) }, Modifier.size(52.dp).testTag("cabin_temp_up")) {
            Icon(Icons.Outlined.KeyboardArrowUp, "온도 높이기", Modifier.size(34.dp), tint = C.title)
        }
        Text(u.read("hvacTemp", "%.1f".format(s.hvacTempC)), Modifier.testTag("cabin_temp"), color = C.ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("°C", color = C.sub, fontSize = 13.sp)
        androidx.compose.material3.IconButton({ change(setting.copy(temperatureC = s.hvacTempC - 0.5f)) }, Modifier.size(52.dp).testTag("cabin_temp_down")) {
            Icon(Icons.Outlined.KeyboardArrowDown, "온도 낮추기", Modifier.size(34.dp), tint = C.title)
        }
        val power = @Composable {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.IconButton({ change(setting.copy(power = !s.hvacPowerOn, fanSpeed = if (s.hvacPowerOn) 0 else maxOf(2, s.fanSpeed))) },
                    Modifier.size(46.dp).testTag("cabin_power").clip(CircleShape).background(if (s.hvacPowerOn) C.title.copy(alpha = 0.12f) else C.bg)) {
                    Icon(Icons.Outlined.PowerSettingsNew, if (s.hvacPowerOn) "공조 끄기" else "공조 켜기", Modifier.size(23.dp), tint = C.title)
                }
                Text(if (s.hvacPowerOn) "끄기" else "켜기", fontSize = 11.sp, color = C.sub)
            }
        }
        val defrost = @Composable {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.IconButton({ change(setting.copy(defrost = !s.defrosterOn)) },
                    Modifier.size(46.dp).testTag("cabin_defrost").clip(CircleShape).background(if (s.defrosterOn) C.title.copy(alpha = 0.12f) else C.bg)) {
                    Icon(Icons.Outlined.Air, "성에 제거", Modifier.size(23.dp), tint = C.title)
                }
                Text("성에 제거", fontSize = 11.sp, color = C.sub)
            }
        }
        if (stackActions) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            power()
            defrost()
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            power()
            defrost()
        }
    }
    }
}

/** 실내 보기 아래: 공조 전원 · 온도 < 22.0° > · 성에 제거. */
@Composable
fun CabinTempBar(vm: VerifiedRuntime, u: UiState) {
    val s = u.signals
    val setting = CabinSetting.from(s)
    fun change(next: CabinSetting) = vm.setCabin(next)
    Column(Modifier.fillMaxWidth().testTag("cabin_bar"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("실내 공조 ${u.read("hvacPower", if (s.hvacPowerOn) "켜짐" else "꺼짐")} · 바람 ${u.read("fanSpeed", if (s.fanSpeed == 0) "꺼짐" else "${s.fanSpeed}단")}", color = C.sub, fontSize = 17.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            androidx.compose.material3.IconButton({ change(setting.copy(temperatureC = s.hvacTempC - 0.5f)) }, Modifier.size(60.dp).testTag("cabin_temp_down")) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "온도 낮추기", Modifier.size(36.dp), tint = C.title)
            }
            Text(u.read("hvacTemp", "%.1f°".format(s.hvacTempC)), Modifier.testTag("cabin_temp"), color = C.ink, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.IconButton({ change(setting.copy(temperatureC = s.hvacTempC + 0.5f)) }, Modifier.size(60.dp).testTag("cabin_temp_up")) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "온도 높이기", Modifier.size(36.dp), tint = C.title)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Outlined.PowerSettingsNew, if (s.hvacPowerOn) "끄기" else "켜기", Modifier.testTag("cabin_power"), active = s.hvacPowerOn) {
                change(setting.copy(power = !s.hvacPowerOn, fanSpeed = if (s.hvacPowerOn) 0 else maxOf(2, s.fanSpeed)))
            }
            RoundAction(Icons.Outlined.Air, "성에 제거", Modifier.testTag("cabin_defrost"), active = s.defrosterOn) {
                change(setting.copy(defrost = !s.defrosterOn))
            }
        }
    }
}

/** 참고 화면의 다섯 날개 공조 아이콘. 선화 눈송이와 달리 작은 원형 버튼에서도 또렷하다. */
private val CabinFanIcon: ImageVector by lazy {
    ImageVector.Builder(name = "CabinFan", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        repeat(5) { blade ->
            group(rotate = blade * 72f, pivotX = 12f, pivotY = 12f) {
                path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                    moveTo(12f, 12f)
                    curveTo(9.4f, 10f, 8.7f, 6.1f, 10f, 3.6f)
                    curveTo(11.9f, 0.1f, 15.9f, 1.4f, 16f, 4.5f)
                    curveTo(16.1f, 7.4f, 13.1f, 8.6f, 12f, 12f)
                    close()
                }
            }
        }
    }.build()
}
