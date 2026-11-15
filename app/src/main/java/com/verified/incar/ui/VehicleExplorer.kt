package com.verified.incar.ui

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirlineSeatReclineNormal
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.AreaValue
import com.verified.incar.core.CabinSetting
import com.verified.incar.core.CarModel
import com.verified.incar.core.DoorPart
import com.verified.incar.core.SeatPart
import com.verified.incar.core.VehicleDetails
import com.verified.incar.core.VehicleFocus
import com.verified.incar.core.VerdictEngine

@Composable
fun VehicleExplorer(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit, focus: VehicleFocus = VehicleFocus()) {
    val example = u.simAllowed && !u.carConnected
    val model = u.settings.carModel ?: CarModel.GRANDEUR.takeIf { example }
    val details = when {
        u.carConnected -> u.signals.details
        example -> VehicleDetails.example(u.signals, u.nowMs)
        else -> VehicleDetails()
    }
    var inside by rememberSaveable(model, focus) { mutableStateOf(focus.seat != null) }
    var selected by rememberSaveable(model, focus) { mutableStateOf(focus.entryDoor.name) }
    var selectedSeat by rememberSaveable(model, focus) { mutableStateOf(focus.seat?.name) }
    var selectionMade by rememberSaveable(model, focus) { mutableStateOf(focus.door != null || focus.seat != null) }
    var showSeats by rememberSaveable(model, focus) { mutableStateOf(focus.seat != null) }
    val door = DoorPart.valueOf(selected)
    val seat = selectedSeat?.let(SeatPart::valueOf)
    fun chooseDoor(part: DoorPart) {
        selected = part.name
        selectedSeat = null
        selectionMade = true
        inside = part != DoorPart.TRUNK
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = hPad(20.dp), vertical = 12.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (model == null) Card(Modifier.fillMaxWidth(), padding = 22.dp) {
                Text("내 차 둘러보기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.title)
                Text("차종을 등록하면 해당 차량의 공식 사진을 볼 수 있어요.", color = C.sub, fontSize = 17.sp)
                BigButton("차량 등록", Modifier.testTag("explorer_register")) { go("CAR_SETUP") }
            } else {
                if (example) Row(Modifier.fillMaxWidth().testTag("explorer_models").horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CarModel.entries.filter { it != CarModel.OTHER }.forEach { candidate ->
                        Chip(candidate.label, candidate == model, Modifier.testTag("explorer_model_${candidate.name}")) {
                            vm.ownerOnly { vm.settings { it.copy(carModel = candidate) } }
                        }
                    }
                } else Text(model.label, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.title)

                ExplorerImageTabs(inside, onChange = { inside = it })
                VehiclePhotos(model, inside)

                if (selectionMade) {
                    val location = if (seat != null) seat.label else door.label
                    val state = if (seat != null) details.seatLabel(seat) else details.doorLabel(door)
                    Text("$location · $state", Modifier.fillMaxWidth().testTag("explorer_focus")
                        .clip(RoundedCornerShape(12.dp)).background(C.ice).padding(12.dp),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.title)
                }

                if (inside) Row(Modifier.fillMaxWidth().testTag("explorer_to_control")
                    .clip(RoundedCornerShape(16.dp)).background(C.card).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Thermostat, null, Modifier.size(26.dp), tint = C.title)
                    Column(Modifier.weight(1f)) {
                        Text("온도·열선 조절", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Text("제어 화면에서 바꿀 수 있어요", fontSize = 13.sp, color = C.sub)
                    }
                    androidx.compose.material3.TextButton({ go("CONTROL") }) {
                        Text("열기", color = C.title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).selectableGroup()) {
                    listOf(false to "문과 잠금", true to "좌석과 안전벨트").forEach { (seats, label) ->
                        val selectedTab = showSeats == seats
                        Column(Modifier.weight(1f).fillMaxHeight()
                            .testTag(if (seats) "explorer_tab_seats" else "explorer_tab_doors")
                            .selectable(selected = selectedTab, role = androidx.compose.ui.semantics.Role.Tab) { showSeats = seats },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                            Text(label, Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                                fontSize = 18.sp, fontWeight = if (selectedTab) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab) C.title else C.sub, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Box(Modifier.fillMaxWidth().height(if (selectedTab) 3.dp else 1.dp).background(if (selectedTab) C.title else C.line))
                        }
                    }
                }

                if (!showSeats) Column(Modifier.fillMaxWidth().testTag("explorer_doors"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (example) "예시 차량 상태" else if (u.carConnected) "차량에서 받은 위치별 신호" else "차량 미연결 · 상태 확인 불가",
                        fontSize = 13.sp, color = C.sub, modifier = Modifier.padding(bottom = 4.dp))
                    DoorPart.entries.forEach { part ->
                        val open = details.doorOpen[part]?.value
                        val locked = details.doorLocked[part]?.value
                        val attention = open == true || locked == false
                        val known = open != null && locked != null
                        val tone = when { attention -> C.warn; known -> C.title; else -> C.sub }
                        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(when { !known -> Icons.Outlined.HelpOutline; locked == true -> Icons.Outlined.Lock; else -> Icons.Outlined.LockOpen },
                                null, Modifier.size(26.dp), tint = tone)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(part.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                                Text(details.doorLabel(part), fontSize = 15.sp, color = if (attention) C.warn else C.sub)
                            }
                            androidx.compose.material3.Button({ chooseDoor(part) },
                                Modifier.heightIn(min = 44.dp).testTag("explorer_door_${part.name}"),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = C.navy, contentColor = androidx.compose.ui.graphics.Color.White),
                                shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                                Text("보기", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (part != DoorPart.entries.last()) androidx.compose.material3.HorizontalDivider(color = C.line.copy(alpha = 0.65f))
                    }
                    if (door != DoorPart.TRUNK) androidx.compose.material3.TextButton({ inside = true }, Modifier.testTag("explorer_enter")) {
                        Text("${door.label} · 실내 사진 보기", color = C.title, fontSize = 15.sp)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = C.title)
                    }
                    if (!example) {
                        val latest = listOfNotNull(details.doorOpen[door], details.doorLocked[door]).maxOfOrNull { it.atMs }
                        latest?.let { Text("마지막 수신 ${VerdictEngine.hhmmss(it)}", color = C.sub, fontSize = 13.sp) }
                    }
                }

                if (showSeats) Column(Modifier.fillMaxWidth().testTag("explorer_seats"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SeatPart.entries.forEach { position ->
                        val warning = details.occupied[position]?.value == true && details.belted[position]?.value == false
                        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.AirlineSeatReclineNormal, null, Modifier.size(28.dp), tint = if (warning) C.danger else C.sub)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(position.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                                Text(details.seatLabel(position), Modifier.testTag("explorer_seat_${position.name}"),
                                    fontSize = 15.sp, color = if (warning) C.danger else C.sub)
                                if (!example) {
                                    val at = listOfNotNull(details.occupied[position], details.belted[position]).maxOfOrNull { it.atMs }
                                    at?.let { Text("마지막 수신 ${VerdictEngine.hhmmss(it)}", fontSize = 13.sp, color = C.sub) }
                                }
                            }
                            if (warning) Icon(Icons.Outlined.WarningAmber, null, Modifier.size(22.dp), tint = C.danger)
                        }
                        if (position != SeatPart.entries.last()) androidx.compose.material3.HorizontalDivider(color = C.line.copy(alpha = 0.65f))
                    }
                    Text("좌석이 없거나 차량에서 상태를 보내지 않으면 확인 불가로 표시돼요.", color = C.sub, fontSize = 13.sp)
                }

                if (example) Card(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Text("차량 상태 체험", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Wrap {
                        Chip("뒷좌석 벨트 미체결 체험", null, Modifier.testTag("explorer_example_belt")) {
                            vm.setExampleDetails { it.copy(
                                occupied = it.occupied + (SeatPart.REAR_RIGHT to AreaValue(true, u.nowMs)),
                                belted = it.belted + (SeatPart.REAR_RIGHT to AreaValue(false, u.nowMs))) }
                        }
                        Chip("조수석 잠금 해제 체험", null, Modifier.testTag("explorer_example_lock")) {
                            vm.setExampleDetails { it.copy(doorLocked = it.doorLocked +
                                (DoorPart.FRONT_RIGHT to AreaValue(false, u.nowMs))) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExplorerImageTabs(inside: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.chip).padding(4.dp).selectableGroup()) {
        listOf(false to "외관", true to "실내").forEach { (value, label) ->
            val selected = inside == value
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .testTag(if (value) "explorer_interior" else "explorer_exterior")
                .then(if (selected) Modifier.lift(RoundedCornerShape(20.dp), 4.dp) else Modifier)
                .clip(RoundedCornerShape(20.dp)).background(if (selected) C.card else androidx.compose.ui.graphics.Color.Transparent)
                .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab) { onChange(value) }
                .padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 19.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) C.title else C.sub)
            }
        }
    }
}
