package com.verified.incar.ui

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
    val model = u.settings.carModel
    val example = u.simAllowed && !u.carConnected
    val details = when {
        u.carConnected -> u.signals.details
        example -> VehicleDetails.example(u.signals, u.nowMs)
        else -> VehicleDetails()
    }
    var inside by rememberSaveable(model, focus) { mutableStateOf(focus.seat != null) }
    var selected by rememberSaveable(model, focus) { mutableStateOf(focus.entryDoor.name) }
    var selectedSeat by rememberSaveable(model, focus) { mutableStateOf(focus.seat?.name) }
    var selectionMade by rememberSaveable(model, focus) { mutableStateOf(focus.door != null || focus.seat != null) }
    val door = DoorPart.valueOf(selected)
    val seat = selectedSeat?.let(SeatPart::valueOf)
    fun chooseDoor(part: DoorPart) {
        selected = part.name
        selectedSeat = null
        selectionMade = true
        inside = part != DoorPart.TRUNK
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = hPad(24.dp), vertical = 16.dp), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text(model?.label ?: "내 차 둘러보기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.title)
                if (selectionMade) {
                    val location = if (seat != null) seat.label else door.label
                    val state = if (seat != null) details.seatLabel(seat) else details.doorLabel(door)
                    Text("$location · $state", Modifier.testTag("explorer_focus"),
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.warn)
                }
                if (model == null) {
                    Text("차종을 등록하면 해당 차량의 공식 사진을 볼 수 있어요.", color = C.sub, fontSize = 17.sp)
                    BigButton("차량 등록", Modifier.testTag("explorer_register")) { go("CAR_SETUP") }
                } else {
                    if (example) Wrap {
                        CarModel.entries.filter { it != CarModel.OTHER }.forEach { candidate ->
                            Chip(candidate.label, candidate == model, Modifier.testTag("explorer_model_${candidate.name}")) {
                                vm.ownerOnly { vm.settings { it.copy(carModel = candidate) } }
                            }
                        }
                    }
                    Wrap {
                        Chip("외관", !inside, Modifier.testTag("explorer_exterior")) { inside = false }
                        Chip("실내", inside, Modifier.testTag("explorer_interior")) { inside = true }
                    }
                    VehiclePhotos(model, inside)
                }
            }
            if (model != null) {
                if (inside) CabinControls(vm, u)
                Card(Modifier.fillMaxWidth(), padding = 18.dp) {
                    Text("문과 잠금", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text(if (example) "예시 차량 신호" else if (u.carConnected) "차량에서 받은 위치별 신호" else "차량 미연결 · 상태 확인 불가",
                        fontSize = 16.sp, color = C.sub)
                    DoorPart.entries.forEach { part ->
                        val attention = details.doorOpen[part]?.value == true || details.doorLocked[part]?.value == false
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (details.doorLocked[part]?.value == true) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                                null, tint = if (attention) C.warn else C.sub)
                            Column(Modifier.weight(1f)) {
                                Text(part.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                                Text(details.doorLabel(part), fontSize = 16.sp, color = if (attention) C.warn else C.sub)
                            }
                            Chip("보기", part == door, Modifier.testTag("explorer_door_${part.name}")) { chooseDoor(part) }
                        }
                    }
                    if (door != DoorPart.TRUNK) {
                        Text("${door.label} 선택됨 · 위에서 상태를 확인하고 실내 사진을 볼 수 있어요.", fontSize = 16.sp, color = C.sub)
                        Wrap {
                            Chip("실내 사진 보기", null, Modifier.testTag("explorer_enter")) { inside = true }
                        }
                    }
                    if (!example) {
                        val latest = listOfNotNull(details.doorOpen[door], details.doorLocked[door]).maxOfOrNull { it.atMs }
                        latest?.let { Text("마지막 수신 ${VerdictEngine.hhmmss(it)}", color = C.sub, fontSize = 15.sp) }
                    }
                }
                Card(Modifier.fillMaxWidth().testTag("explorer_seats"), padding = 18.dp) {
                    Text("좌석과 안전벨트", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    SeatPart.entries.forEach { seat ->
                        val warning = details.occupied[seat]?.value == true && details.belted[seat]?.value == false
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.AirlineSeatReclineNormal, null, tint = if (warning) C.danger else C.sub)
                            Column(Modifier.weight(1f)) {
                                Text(seat.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                                Text(details.seatLabel(seat), Modifier.testTag("explorer_seat_${seat.name}"),
                                    fontSize = 16.sp, color = if (warning) C.danger else C.sub)
                                if (!example) {
                                    val at = listOfNotNull(details.occupied[seat], details.belted[seat]).maxOfOrNull { it.atMs }
                                    at?.let { Text("마지막 수신 ${VerdictEngine.hhmmss(it)}", fontSize = 14.sp, color = C.sub) }
                                }
                            }
                        }
                    }
                    Text("좌석이 없거나 차량에서 상태를 보내지 않으면 확인 불가로 표시돼요.", color = C.sub, fontSize = 15.sp)
                    if (example) Wrap {
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
private fun CabinControls(vm: VerifiedRuntime, u: UiState) {
    val s = u.signals
    val canChange = u.simAllowed && !u.carConnected && !s.uxRestricted && u.settings.carModel?.controllable == true
    val example = u.simAllowed && !u.carConnected
    val setting = CabinSetting.from(s)
    fun value(signal: String, text: String) = if (example || (u.carConnected && signal in u.liveSignals && signal in s.seenAt)) text else "확인 불가"
    Card(Modifier.fillMaxWidth().testTag("explorer_climate"), padding = 18.dp) {
        Text("실내 공조", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Text(if (example) "체험 차량에 반영돼요." else "실차 공조 조작은 연결 준비 중이에요.", fontSize = 16.sp, color = C.sub)
        KeyValue("공조 전원", value("hvacPower", if (s.hvacPowerOn) "켜짐" else "꺼짐"))
        KeyValue("설정 온도", value("hvacTemp", "${s.hvacTempC.toInt()}℃"))
        Wrap {
            Chip("시원하게", null, Modifier.testTag("explorer_cool"), enabled = canChange) {
                vm.setCabin(setting.copy(power = true, cooling = true, temperatureC = 20f, fanSpeed = 3))
            }
            Chip("따뜻하게", null, Modifier.testTag("explorer_heat"), enabled = canChange) {
                vm.setCabin(setting.copy(power = true, cooling = false, temperatureC = 25f, fanSpeed = 3))
            }
            Chip("공조 끄기", null, enabled = canChange) { vm.setCabin(setting.copy(power = false, fanSpeed = 0)) }
        }
        Wrap {
            Chip("온도 −", null, Modifier.testTag("explorer_temp_down"), enabled = canChange) { vm.setCabin(setting.copy(temperatureC = s.hvacTempC - 1)) }
            Chip("온도 ＋", null, Modifier.testTag("explorer_temp_up"), enabled = canChange) { vm.setCabin(setting.copy(temperatureC = s.hvacTempC + 1)) }
        }
        KeyValue("바람 세기", value("fanSpeed", if (s.fanSpeed == 0) "꺼짐" else "${s.fanSpeed}단"))
        Wrap {
            Chip("바람 −", null, enabled = canChange) { vm.setCabin(setting.copy(fanSpeed = s.fanSpeed - 1)) }
            Chip("바람 ＋", null, Modifier.testTag("explorer_fan_up"), enabled = canChange) { vm.setCabin(setting.copy(fanSpeed = s.fanSpeed + 1)) }
        }
        Wrap {
            Chip("에어컨 A/C · ${value("ac", if (s.acOn) "켜짐" else "꺼짐")}", example && s.acOn, enabled = canChange) {
                vm.setCabin(setting.copy(cooling = !s.acOn))
            }
            Chip("내기순환 · ${value("recirc", if (s.recircOn) "켜짐" else "꺼짐")}", example && s.recircOn, enabled = canChange) {
                vm.setCabin(setting.copy(recirculate = !s.recircOn))
            }
            Chip("앞유리 성에 제거", example && s.defrosterOn, enabled = canChange) {
                vm.setCabin(setting.copy(defrost = !s.defrosterOn))
            }
            Chip("운전석 열선 · ${value("seatHeat", "${s.seatHeatDriver}단")}", example && s.seatHeatDriver > 0, enabled = canChange) {
                vm.setCabin(setting.copy(driverHeat = (s.seatHeatDriver + 1) % 4))
            }
        }
    }
}
