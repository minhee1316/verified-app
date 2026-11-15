package com.verified.incar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.RecordFiles
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.Level
import com.verified.incar.core.VerdictEngine

/** 판정마다 필요한 차 신호. 하나라도 받지 못하면 그 판정은 기다린다. */
internal val VERDICT_NEEDS = listOf(
    "대리 주차 완료" to listOf("gear", "speed", "gps", "doors"),
    "정비·빌려주기 리포트" to listOf("odometer", "soc", "speed", "ignition", "doors"),
    "하차 3초 점검" to listOf("window", "lowBeam", "interiorLight", "doors"),
    "부축 동행" to listOf("doors", "row2Occupancy", "belt"),
    "업무 운행기록부" to listOf("odometer", "gps"),
)

/** 받지 못한 신호를 사람 말로. */
internal fun UiState.missingFor(keys: List<String>) = keys.filter { unread(it) }.map { SIGNAL_LABELS[it] ?: it }

/**
 * 차량 연결·신호 상태: 연결 여부, 위치, 판정별로 기다리는 신호, 신호마다 마지막으로 받은 시각.
 * "왜 판정이 안 되지?"를 화면에서 바로 알 수 있게 한다.
 */
@Composable
fun SignalStatusScreen(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit) {
    val example = u.simAllowed && !u.carConnected
    TwoUp(36.dp, 28.dp, 40.dp, 18.dp, left = {
        Card(Modifier.fillMaxWidth().testTag("signal_connection"), padding = 22.dp) {
            CardHeader(null, "연결 상태")
            StatusLine(Icons.Outlined.DirectionsCar, "차 신호",
                when { u.carConnected -> "연결됨 · 받는 신호 ${u.liveSignals.size}개"; example -> "차에 연결되지 않음 · 예시 차량 값으로 보여 주는 중"; else -> "차에 연결되지 않음" },
                when { u.carConnected -> Level.OK; example -> Level.INFO; else -> Level.WARN })
            StatusLine(Icons.Outlined.Place, "위치",
                when { example -> "예시 위치"; u.locationOn -> "받는 중 · 장소 판정 가능"; else -> "받지 못함 · 위치 권한과 집 등록을 확인해 주세요" },
                when { example -> Level.INFO; u.locationOn -> Level.OK; else -> Level.WARN })
            StatusLine(Icons.Outlined.PhoneIphone, "차주 휴대폰 알림", if (u.phoneTopic != null) "연결됨" else "연결 안 함 · 차 안에만 기록", if (u.phoneTopic != null) Level.OK else Level.INFO)
            if (u.phoneTopic == null) TextRow("휴대폰 알림 연결하기", tag = "signal_phone_link") { go("PHONE_LINK") }
        }
        Card(Modifier.fillMaxWidth().testTag("signal_verdicts"), padding = 22.dp) {
            CardHeader(null, "판정별 상태", "판정에 필요한 신호를 모두 받아야 자동으로 판정해요.")
            VERDICT_NEEDS.forEach { (name, keys) ->
                val missing = u.missingFor(keys)
                StatusLine(if (missing.isEmpty()) Icons.Outlined.CheckCircle else Icons.Outlined.HourglassEmpty, name,
                    if (missing.isEmpty()) "판정 가능" else "기다리는 신호: ${missing.joinToString(", ")}",
                    if (missing.isEmpty()) Level.OK else Level.WARN)
            }
            if (VERDICT_NEEDS.any { u.missingFor(it.second).isNotEmpty() })
                Text("받지 못한 신호가 있으면 그 칸은 추정하지 않고 '확인 불가'로 남겨요. 차를 직접 돌려받았다면 차주 확인으로 기록을 마칠 수 있어요.", fontSize = 15.sp, color = C.sub)
        }
    }, right = {
        Card(Modifier.fillMaxWidth().testTag("signal_list"), padding = 22.dp) {
            CardHeader(null, "신호별 마지막 수신", if (example) "예시 차량은 모든 값이 예시 값이에요." else "차가 보낸 시각 기준")
            SIGNAL_LABELS.entries.forEachIndexed { i, (key, label) ->
                if (i > 0) HorizontalDivider(color = C.line)
                val at = u.signals.seenAt[key]
                val value = when {
                    example -> "예시 값"
                    u.unread(key) -> "받지 못함"
                    at != null -> ((u.nowMs - at) / 1000).coerceAtLeast(0).let { s -> if (s < 60) "${s}초 전" else if (s < 3600) "${s / 60}분 전" else VerdictEngine.hhmm(at) }
                    else -> "받는 중"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (value == "받지 못함") Tone.orange else if (example) C.sub else Tone.mint))
                    Text(label, Modifier.weight(1f).padding(start = 10.dp), fontSize = 16.sp, color = C.ink)
                    Text(value, fontSize = 15.sp, color = if (value == "받지 못함") Tone.orange else C.sub)
                }
            }
        }
    })
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, level: Level) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconBubble(icon, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            Text(value, fontSize = 15.sp, color = when (level) { Level.WARN, Level.DANGER -> Tone.orange; Level.OK -> C.ok; else -> C.sub })
        }
    }
}

/**
 * 주차 위치 직접 보완: 자동 층 추정에 "B2 C구역 32번 · 엘리베이터 옆" 같은 메모와 사진 한 장을 붙인다.
 * 지하에서 GPS가 없어도 찾아갈 단서. 사진은 원본 그대로 이 기기 안에만 둔다.
 */
@Composable
internal fun ParkingNoteCard(vm: VerifiedRuntime, u: UiState) {
    val context = LocalContext.current
    val photo = RecordFiles.parkingPhoto(context, u.trial)
    var version by remember { mutableIntStateOf(0) }
    var requestedPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberOriginalCamera(vm) { shot ->
        try {
            if (HandoverPhotos.keepCaptured(shot, requestedPhotoPath, photo) != null) version++
            else { shot.delete(); vm.toast("촬영 중 차량 모드가 바뀌었어요. 현재 주차 위치에서 다시 찍어 주세요.") }
        } finally { requestedPhotoPath = null }
    }
    Column(Modifier.fillMaxWidth().testTag("parking_note"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = C.line)
        Text("주차 위치 메모", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
        var note by rememberSaveable(u.settings.parkingNote) { mutableStateOf(u.settings.parkingNote) }
        OutlinedTextField(note, { note = it.take(80) }, Modifier.fillMaxWidth().testTag("parking_input"), singleLine = true,
            placeholder = { Text("예: B2 C구역 32번, 엘리베이터 옆", color = C.sub) }, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.title, unfocusedBorderColor = C.sub, cursorColor = C.title),
            textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 17.sp))
        if (u.settings.parkingNoteAtMs > 0 && note == u.settings.parkingNote && note.isNotBlank())
            Text("${VerdictEngine.date(u.settings.parkingNoteAtMs)} ${VerdictEngine.hhmm(u.settings.parkingNoteAtMs)}에 적음", fontSize = 14.sp, color = C.sub)
        val bmp = remember(photo.path, version) { HandoverPhotos.decode(photo, 640)?.asImageBitmap() }
        bmp?.let { Image(it, "주차 위치 사진", Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (note.trim() != u.settings.parkingNote) BigButton("메모 저장", Modifier.weight(1f).testTag("parking_save"), BtnStyle.TONAL, fontSize = 17.sp) {
                val text = note.trim()
                vm.settings { it.copy(parkingNote = text, parkingNoteAtMs = if (text.isEmpty()) 0 else u.nowMs) }
            }
            BigButton(if (bmp == null) "사진 찍기" else "사진 다시 찍기", Modifier.weight(1f).testTag("parking_photo"), BtnStyle.OUTLINE, fontSize = 17.sp) {
                requestedPhotoPath = photo.absolutePath
                camera()
            }
        }
    }
}

/** 승차감 화면 아래: 최근 7일 주행 습관 한 줄(인사이트 통계에서). 통계가 없으면 그리지 않는다. */
@Composable
internal fun DrivingHabitTip(u: UiState) {
    val week = u.thisWeek ?: return
    val last = u.lastWeek
    Card(Modifier.fillMaxWidth().testTag("ride_tip"), padding = 20.dp) {
        CardHeader(null, "이번 주 운전 습관", "드라이빙 인사이트의 이번 주 기록")
        RecordMetricStrip(listOf("주행" to "%.1fkm".format(java.util.Locale.ROOT, week.km), "급감속" to "${week.brakes}회", "급가속" to "${week.accels}회"))
        last?.let {
            val diff = week.brakes - it.brakes
            Text(when { diff < 0 -> "지난주보다 급감속이 ${-diff}회 줄었어요."; diff > 0 -> "지난주보다 급감속이 ${diff}회 늘었어요. 앞차와 거리를 넉넉히 두면 뒷좌석이 편해요."; else -> "지난주와 급감속 횟수가 같아요." },
                fontSize = 16.sp, color = C.ink)
        } ?: Text("뒷좌석 손님이 있을 때는 출발과 정지를 천천히 하면 흔들림이 줄어요.", fontSize = 16.sp, color = C.sub)
    }
}
