package com.verified.incar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.Gear
import com.verified.incar.core.Place
import com.verified.incar.core.Postures
import com.verified.incar.core.Turn
import com.verified.incar.core.Wiper

/**
 * 차량 신호 시뮬레이터. 에뮬레이터·PT 시연에서 실제 신호 대신 값을 넣는다.
 * 실차에 연결되면 실차가 주는 신호는 실차 값이 덮어쓰고, 못 읽는 신호만 여기서 넣는다.
 */
@Composable
fun SimPanel(vm: VerifiedRuntime, u: UiState) {
    val s = u.signals
    fun live(name: String) = if (name in u.liveSignals) " · 실차" else ""
    Column(
        Modifier.width(if (isCompact()) androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp * 0.92f else 340.dp).fillMaxHeight().background(C.ice).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("신호 시뮬레이터", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = C.navy)
        Text(if (u.carConnected) "실차 연결됨: 실차 신호가 우선합니다" else "실차 미연결: 모든 신호를 여기서 넣습니다", fontSize = 12.sp, color = C.sub)

        Head("시나리오 자동 재생")
        Wrap {
            Chip("미리보기: 전기차 경로", false) { vm.navigate.value = "EV_ROUTE" }
            Chip("미리보기: 발레 감시", false) { vm.navigate.value = "VALET_WATCH" }
            Chip("미리보기: 무선 진단", false) { vm.navigate.value = "RADIO_DIAG" }
            Chip("① 대리 완료", false) { vm.runScenario("valet") }
            Chip("① 시동 바로 OFF", false) { vm.runScenario("valet_quick_off") }
            Chip("① 지하주차장", false) { vm.runScenario("valet_garage") }
            Chip("① 집 아닌 곳 하차", false) { vm.runScenario("valet_outside") }
            Chip("② 처음 보는 운전자", false) { vm.runScenario("stranger") }
            Chip("④ 부모님 동행", false) { vm.runScenario("escort") }
            Chip("⑤ 스쿨존", false) { vm.runScenario("school") }
            Chip("⑥ 급감속", false) { vm.runScenario("harsh") }
            Chip("⑦ 하차 점검", false) { vm.runScenario("exit") }
            Chip("⑦ 지하 2층 주차", false) { vm.runScenario("floor") }
            Chip("⑨ N단 유지", false) { vm.runScenario("nhold") }
            Chip("② 빌려준 차", false) { vm.runScenario("lend") }
            Chip("정비소 맡김", false) { vm.runScenario("service") }
            Chip("타이어 천천히 빠짐", false) { vm.runScenario("leak") }
            Chip("고장 · 비상등 대피", false) { vm.runScenario("evac") }
        }

        Head("기어 · 속도 · 시간" + live("gear"))
        Wrap { Gear.entries.forEach { g -> Chip(g.name, s.gear == g) { vm.sim { it.copy(gear = g, speedKmh = if (g == Gear.P) 0f else it.speedKmh) } } } }
        Text("속도 %.0f km/h".format(s.speedKmh) + live("speed"), fontSize = 13.sp)
        Slider(s.speedKmh, { v -> vm.sim { it.copy(speedKmh = v.toInt().toFloat(), gear = if (v > 0 && it.gear == Gear.P) Gear.D else it.gear) } }, valueRange = 0f..100f)
        Wrap {
            Chip("+10초", false) { vm.skipSeconds(10) }
            Chip("+60초", false) { vm.skipSeconds(60) }
            Chip(if (s.ignitionOn) "시동 ON" else "시동 OFF", !s.ignitionOn) { vm.sim { it.copy(ignitionOn = !it.ignitionOn) } }
        }

        Head("위치 (NaviHelper 연동 전 입력)")
        Wrap {
            Place.entries.forEach { p -> Chip(p.label, s.place == p) { vm.sim { it.copy(place = p) } } }
            Chip("GPS 끊김(지하)", s.gpsLost) { vm.sim { it.copy(gpsLost = !it.gpsLost) } }
        }

        Head("운전석" + live("posture"))
        Wrap {
            Chip("착석", s.driverSeated) { vm.sim { it.copy(driverSeated = !it.driverSeated) } }
            Chip("운전석 문 열림", s.driverDoorOpen) { vm.sim { it.copy(driverDoorOpen = !it.driverDoorOpen) } }
            Chip("차주 자세", s.posture == Postures.OWNER) { vm.sim { it.copy(posture = Postures.OWNER) } }
            Chip("배우자 자세", s.posture == Postures.SPOUSE) { vm.sim { it.copy(posture = Postures.SPOUSE) } }
            Chip("애매한 자세", s.posture == Postures.AMBIGUOUS) { vm.sim { it.copy(posture = Postures.AMBIGUOUS) } }
            Chip("처음 보는 자세", s.posture == Postures.STRANGER) { vm.sim { it.copy(posture = Postures.STRANGER) } }
        }

        Head("동승석 · 2열" + live("row2Occupancy"))
        Wrap {
            Chip("조수석 착석", s.passengerSeated) { vm.sim { it.copy(passengerSeated = !it.passengerSeated) } }
            Chip("조수석 벨트", s.passengerBelt) { vm.sim { it.copy(passengerBelt = !it.passengerBelt) } }
            Chip("2열 착석", s.row2Seated) { vm.sim { it.copy(row2Seated = !it.row2Seated) } }
            Chip("2열 벨트", s.row2Belt) { vm.sim { it.copy(row2Belt = !it.row2Belt) } }
            Chip("2열 오른문(보도)", s.row2RightDoorOpen) { vm.sim { it.copy(row2RightDoorOpen = !it.row2RightDoorOpen) } }
            Chip("2열 왼문(차도)", s.row2LeftDoorOpen) { vm.sim { it.copy(row2LeftDoorOpen = !it.row2LeftDoorOpen) } }
            Chip("2열 등받이 젖힘", s.row2Reclined) { vm.sim { it.copy(row2Reclined = !it.row2Reclined) } }
        }

        Head("하차·겨울 신호")
        Wrap {
            Chip("실내등", s.interiorLightOn) { vm.sim { it.copy(interiorLightOn = !it.interiorLightOn) } }
            Chip("타이어 저압", s.tirePressureLow) { vm.sim { it.copy(tirePressureLow = !it.tirePressureLow) } }
            Chip("외기 -5℃", s.outsideTempC <= 0f) { vm.sim { it.copy(outsideTempC = if (it.outsideTempC <= 0f) 15f else -5f) } }
            Chip("램프 회전(바퀴 30°)", s.steeringDeg >= 25f) { vm.sim { it.copy(steeringDeg = if (it.steeringDeg >= 25f) 0f else 30f) } }
        }

        Head("마이현대")
        Wrap {
            Chip("주행거리 +5,000km", false) { vm.sim { it.copy(odometerKm = it.odometerKm + 5_000) } }
            Chip("단속 구역", s.enforcementZone) { vm.sim { it.copy(enforcementZone = !it.enforcementZone) } }
            Chip("비상등 " + if (s.hazardOn) "켜짐" else "꺼짐", s.hazardOn) { vm.sim { it.copy(hazardOn = !it.hazardOn) } }
            Chip("서버 연결 끊김", u.simOffline) { vm.setSimOffline(!u.simOffline) }
            Chip("제어 무응답", u.simControlFail) { vm.setSimControlFail(!u.simControlFail) }
        }

        Head("경고등 · 내비 · 위치" + live("warnings"))
        Wrap {
            Chip("경고등 켜짐(엔진)", "엔진" in s.warnings) { vm.sim { it.copy(warnings = if ("엔진" in it.warnings) it.warnings - "엔진" else it.warnings + "엔진") } }
            Chip("엔진오일 부족", s.oilLow) { vm.sim { it.copy(oilLow = !it.oilLow) } }
            Chip("내비 안내(집 12km·18분)", s.naviDest != null) {
                vm.sim { if (it.naviDest != null) it.copy(naviDest = null, naviRemainKm = null, naviRemainMin = null) else it.copy(naviDest = "집", naviRemainKm = 12f, naviRemainMin = 18) }
            }
            // 경로 이탈 판정은 남은 거리가 최저값보다 1.5km 넘게 늘어야 한다
            Chip("내비 경로 이탈", false) { vm.sim { it.copy(naviRemainKm = (it.naviRemainKm ?: 12f) + 2f) } }
            // 위도 0.0135° ≈ 1.5km
            Chip("정비소에서 멀리 이동", false) { vm.sim { it.copy(odometerKm = it.odometerKm + 6, lat = (it.lat ?: 37.5665) + 0.0135, lon = it.lon ?: 126.978) } }
        }

        Head("화면")
        Wrap { Chip("야간 모드", C.dark) { C.forceDark = !C.dark } }

        Head("차체 · 안전")
        Wrap {
            Chip("트렁크 열림", s.trunkOpen) { vm.sim { it.copy(trunkOpen = !it.trunkOpen) } }
            Chip("창문 열림", s.windowOpen) { vm.sim { it.copy(windowOpen = !it.windowOpen) } }
            Chip("바퀴 +35°", s.steeringDeg >= 30f) { vm.sim { it.copy(steeringDeg = if (it.steeringDeg >= 30f) 0f else 35f) } }
            Chip("순정 SEA 경고", s.seaWarning) { vm.sim { it.copy(seaWarning = !it.seaWarning) } }
            Chip("순정 오디오 우선", s.audioFocusLost) { vm.sim { it.copy(audioFocusLost = !it.audioFocusLost) } }
        }

        Head("⑩ 공조·열선 (현재 %.0f℃ · 열선 ${s.seatHeatDriver}단 · 통풍 ${s.seatVentDriver}단)".format(s.hvacTempC))
        Wrap {
            Chip("문 잠금", s.doorsLocked) { vm.sim { it.copy(doorsLocked = !it.doorsLocked) } }
            Chip("내려서 잠금(시동 OFF)", !s.driverSeated && !s.ignitionOn) { vm.sim { it.copy(driverSeated = false, driverDoorOpen = false, ignitionOn = false, doorsLocked = true, gear = Gear.P, speedKmh = 0f) } }
            Chip("탑승(시동 ON)", s.driverSeated && s.ignitionOn) { vm.sim { it.copy(driverSeated = true, ignitionOn = true, doorsLocked = false) } }
            Chip("배터리 15%", s.socPct < 20) { vm.sim { it.copy(socPct = if (it.socPct < 20) 80 else 15) } }
        }

        Head("⑧ 설정 신호")
        Wrap {
            Chip("크루즈 해제", s.cruiseOverride) { vm.sim { it.copy(cruiseOverride = !it.cruiseOverride) } }
            Chip("와이퍼 AUTO", s.wiper == Wiper.AUTO) { vm.sim { it.copy(wiper = if (it.wiper == Wiper.AUTO) Wiper.OFF else Wiper.AUTO) } }
            Chip("내기순환", s.recircOn) { vm.sim { it.copy(recircOn = !it.recircOn) } }
            Chip("에어컨", s.acOn) { vm.sim { it.copy(acOn = !it.acOn) } }
            Chip("주행모드 실내등", s.ambientByDriveMode) { vm.sim { it.copy(ambientByDriveMode = !it.ambientByDriveMode) } }
            Chip("워셔액 부족", s.washerLow) { vm.sim { it.copy(washerLow = !it.washerLow) } }
        }

        Head("⑨ 조작")
        Wrap {
            Chip("와이퍼 작동", s.wiper == Wiper.LOW) { vm.sim { it.copy(wiper = if (it.wiper == Wiper.LOW) Wiper.OFF else Wiper.LOW) } }
            Chip("왼쪽 깜빡이", s.turnSignal == Turn.LEFT) { vm.sim { it.copy(turnSignal = if (it.turnSignal == Turn.LEFT) Turn.OFF else Turn.LEFT) } }
            Chip("전조등", s.lowBeamOn) { vm.sim { it.copy(lowBeamOn = !it.lowBeamOn) } }
            Chip("시트 앞으로", false) { vm.sim { it.copy(posture = listOf((it.posture[0] - 0.05f).coerceAtLeast(0f)) + it.posture.drop(1)) } }
            Chip("온도 +1", false) { vm.sim { it.copy(hvacTempC = it.hvacTempC + 1) } }
            Chip("문 잠금", s.doorsLocked) { vm.sim { it.copy(doorsLocked = !it.doorsLocked) } }
            Chip("성에 제거", s.defrosterOn) { vm.sim { it.copy(defrosterOn = !it.defrosterOn) } }
        }
        Chip("모든 신호 초기화", false) { vm.sim { com.verified.incar.core.Signals() } }
    }
}

@Composable
private fun Head(t: String) = Text(t, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = C.ink, modifier = Modifier.padding(top = 6.dp))
