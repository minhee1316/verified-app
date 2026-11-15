package com.verified.incar.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.HandKind
import com.verified.incar.core.HandoverRecord
import com.verified.incar.core.Level
import com.verified.incar.core.VerdictEngine

/** 이력서 화면에서 쓰는 종류 이름. 저장 값 "맡김"은 화면에서 "정비 맡김"이다. */
internal fun HandoverRecord.kindLabel() = if (kind == "맡김") "정비 맡김" else kind

/**
 * 맡김 기록 검색·필터. [kind]: null이면 전체, [days]: null이면 전체 기간. [query]는 메모·바뀐 것·근거·종류에서 찾는다.
 */
internal fun filterHistory(h: List<HandoverRecord>, kind: String?, needCheck: Boolean, days: Int?, query: String, nowMs: Long): List<HandoverRecord> {
    val q = query.trim()
    return h.filter { r ->
        (kind == null || r.kind == kind) &&
            (!needCheck || !r.ok) &&
            (days == null || nowMs - r.endMs <= days * 86_400_000L) &&
            (q.isEmpty() || listOf(r.memo, r.kindLabel(), r.changes.joinToString(" "), r.proof.joinToString(" ") { it.what }).any { q in it })
    }
}

private val inputColors @Composable get() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.title, unfocusedBorderColor = C.sub, focusedLabelColor = C.title, unfocusedLabelColor = C.sub, cursorColor = C.title,
)

/** 기록 탭 오른쪽 위: 검색어 · 종류 · 확인 필요 · 기간. */
@Composable
internal fun HistoryFilterBar(query: String, onQuery: (String) -> Unit, kind: String?, onKind: (String?) -> Unit,
                              needCheck: Boolean, onNeedCheck: (Boolean) -> Unit, days: Int?, onDays: (Int?) -> Unit, shown: Int, total: Int) {
    Card(Modifier.fillMaxWidth().testTag("history_filter"), padding = 18.dp) {
        OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().testTag("history_search"), singleLine = true,
            placeholder = { Text("메모·바뀐 것 검색 (예: 타이어, 흠집)", color = C.sub) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = C.sub) }, shape = RoundedCornerShape(16.dp), colors = inputColors,
            textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 17.sp))
        Wrap {
            listOf(null to "전체", "대리" to "대리", "맡김" to "정비", "빌려주기" to "빌려주기").forEach { (k, label) ->
                Chip(label, kind == k, Modifier.testTag("history_kind_${k ?: "ALL"}")) { onKind(k) }
            }
            Chip("확인 필요만", needCheck, Modifier.testTag("history_need_check")) { onNeedCheck(!needCheck) }
        }
        Wrap {
            listOf(null to "전체 기간", 7 to "최근 7일", 30 to "최근 30일").forEach { (d, label) -> Chip(label, days == d) { onDays(d) } }
        }
        Text(if (shown == total) "${total}건" else "${total}건 중 ${shown}건", fontSize = 15.sp, color = C.sub)
    }
}

/**
 * 맡김 한 건 상세: 기본 정보 · 숫자 · 메모 → 시간순 기록 · 바뀐 것 → 같은 각도 사진, PDF로 저장·보내기.
 */
@Composable
fun RecordDetailScreen(vm: VerifiedRuntime, u: UiState, startMs: Long, go: (String) -> Unit) {
    val r = u.engine.history.firstOrNull { it.startMs == startMs }
    if (r == null) {
        Column(Modifier.padding(hPad(36.dp), 28.dp)) { Note("이 기록을 찾지 못했어요. 기록 탭에서 다시 골라 주세요.") }
        return
    }
    val context = LocalContext.current
    val demoData = r.proof.any { it.src == "시뮬레이터" }
    TwoUp(36.dp, 28.dp, 40.dp, 18.dp, left = {
        Card(Modifier.fillMaxWidth().testTag("record_head"), padding = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBubble(if (r.kind == "대리") Icons.Outlined.LocalParking else if (r.kind == "맡김") Icons.Outlined.Build else Icons.Outlined.Key, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.kindLabel(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text("${VerdictEngine.date(r.startMs)} ${VerdictEngine.hhmm(r.startMs)} ~ " +
                        (if (VerdictEngine.date(r.endMs) != VerdictEngine.date(r.startMs)) VerdictEngine.date(r.endMs) + " " else "") + VerdictEngine.hhmm(r.endMs),
                        fontSize = 16.sp, color = C.sub)
                }
                Pill(if (r.ok) "문제 없이 돌려받음" else "확인 필요", if (r.ok) Level.OK else Level.WARN)
            }
            if (demoData) Text("예시 값으로 만든 기록", fontSize = 15.sp, color = C.sub)
            RecordMetricStrip(listOf(
                "남이 운전" to (r.km?.let { "%.1fkm".format(java.util.Locale.ROOT, it) } ?: "확인 불가"),
                "최고 속도" to (if (!r.recordsDrivingStats) "기록 안 함" else if (r.speedKnown) "%.0fkm/h".format(java.util.Locale.ROOT, r.maxSpeed) else "확인 불가"),
                "급감속" to (if (!r.recordsDrivingStats) "기록 안 함" else if (r.speedKnown) "${r.brakes}회" else "확인 불가"),
            ))
            r.dueMs?.let { due ->
                // 예정보다 조금이라도 늦으면 1분으로 올려 센다(반납 예정 지남 알림·근거와 같게)
                val late = -Math.floorDiv(due - r.endMs, 60_000L)
                Note("반납 예정 ${VerdictEngine.hhmm(due)} · " + if (late > 0) "예정보다 ${late}분 늦게 돌려받았어요" else "예정 안에 돌려받았어요", if (late > 0) Level.WARN else Level.OK)
            }
        }
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            CardHeader(null, "차주 메모", "차가 읽은 기록과 따로, 차주가 적은 내용이에요.")
            var memo by rememberSaveable(r.startMs) { mutableStateOf(r.memo) }
            OutlinedTextField(memo, { memo = it.take(VerdictEngine.MEMO_MAX) }, Modifier.fillMaxWidth().testTag("record_memo"), minLines = 2,
                placeholder = { Text("예: 타이어 교체, 기존 오른쪽 문 흠집", color = C.sub) }, shape = RoundedCornerShape(16.dp), colors = inputColors,
                textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 17.sp))
            if (memo.trim() != r.memo) BigButton("메모 저장", Modifier.fillMaxWidth().testTag("record_memo_save"), BtnStyle.TONAL, fontSize = 18.sp) {
                vm.act { _, _ -> setRecordMemo(r.startMs, memo); emptyList() }
            }
        }
        BigButton("PDF로 저장·보내기", Modifier.fillMaxWidth().testTag("record_pdf"), fontSize = 19.sp) {
            vm.exportRecordPdf(r.startMs)?.let { uri ->
                val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { context.startActivity(Intent.createChooser(send, "맡김 기록 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
            }
        }
        Text("PDF에는 기본 정보, 차가 읽은 값, 메모, 시간순 기록, 바뀐 것, 같은 각도 사진이 들어가요.", fontSize = 15.sp, color = C.sub)
    }, right = {
        Card(Modifier.fillMaxWidth().testTag("record_timeline"), padding = 22.dp) {
            CardHeader(null, "시간순 기록", "시각 · 일어난 일 · 읽은 곳")
            val steps = r.proof.ifEmpty {
                listOf(com.verified.incar.core.Proof(r.startMs, "맡김 시작", "기록"), com.verified.incar.core.Proof(r.endMs, "돌려받음", "기록"))
            }
            steps.forEachIndexed { i, p ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val warn = p.what.startsWith("급감속") || "지남" in p.what || "과속" in p.what || "km/h 주행" in p.what
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (warn) Tone.orange else C.title))
                        if (i < steps.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(C.line))
                    }
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text("${VerdictEngine.hhmm(p.atMs)}  ${p.what}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                        Text(p.src, fontSize = 14.sp, color = C.sub)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), padding = 22.dp) {
            CardHeader(null, "바뀐 것", "맡길 때와 돌려받을 때 차 상태 비교")
            if (r.changes.isEmpty()) Text("달라진 것이 없어요", fontSize = 17.sp, color = C.sub)
            r.changes.forEach { Text("· $it", fontSize = 17.sp, color = if (it.startsWith("확인 불가")) C.warn else C.ink) }
        }
        HandoverPhotoPanel(vm, if (r.kind == "맡김") HandKind.SERVICE else HandKind.LEND, r.startMs, active = false, returned = true, demo = u.trial)
    })
}

/** 반납 예정 고르기: 맡길 때(시작 전)와 맡기는 중에 쓴다. 결과는 맡긴 시각 기준 밀리초 또는 null. */
private val DUE_CHOICES: List<Pair<String, (Long) -> Long?>> = listOf(
    "정하지 않음" to { _ -> null },
    "1시간 뒤" to { now -> now + 3_600_000L },
    "2시간 뒤" to { now -> now + 7_200_000L },
    "오늘 18:00" to { now -> atHour(now, 18, 0) },
    "내일 09:00" to { now -> atHour(now, 9, 1) },
)

private fun atHour(now: Long, hour: Int, plusDays: Int): Long? {
    val z = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(now).atZone(z).toLocalDate().plusDays(plusDays.toLong()).atTime(hour, 0).atZone(z).toInstant().toEpochMilli()
    return t.takeIf { it > now }
}

/**
 * 맡김 메모와 반납 예정 시각. [nowMs] 기준으로 예정을 계산한다. 맡기는 중이면 지금 값([memo0]·[due0])에서 시작한다.
 */
@Composable
internal fun LendPlanEditor(nowMs: Long, memo0: String, due0: Long?, saveLabel: String?, onChange: (String, Long?) -> Unit) {
    var memo by rememberSaveable(memo0) { mutableStateOf(memo0) }
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    val due = choice?.let { c -> DUE_CHOICES.firstOrNull { it.first == c }?.second?.invoke(nowMs) } ?: if (choice == null) due0 else null
    // 맡기기 전 편집기: 화면에 보이는 예정 시각(시간이 흐르면 함께 바뀜)과 시작할 때 저장할 값을 같게 둔다
    if (saveLabel == null) LaunchedEffect(due) { onChange(memo, due) }
    Column(Modifier.fillMaxWidth().testTag("lend_plan"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("반납 예정", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.ink)
        Wrap {
            DUE_CHOICES.forEach { (label, f) ->
                val at = f(nowMs)
                if (label == "정하지 않음" || at != null) Chip(label, if (choice == null) (label == "정하지 않음" && due0 == null) else choice == label, Modifier.testTag("due_$label")) {
                    choice = label
                    if (saveLabel == null) onChange(memo, DUE_CHOICES.first { it.first == label }.second(nowMs))
                }
            }
        }
        due?.let { Text("예정 ${VerdictEngine.hhmm(it)} · 지나도 반납 전이면 휴대폰으로 알려요", fontSize = 15.sp, color = C.sub) }
        OutlinedTextField(memo, { memo = it.take(VerdictEngine.MEMO_MAX); if (saveLabel == null) onChange(memo, due) }, Modifier.fillMaxWidth().testTag("lend_memo"), minLines = 2,
            placeholder = { Text("메모 (예: 타이어 교체, 기존 오른쪽 문 흠집)", color = C.sub) }, shape = RoundedCornerShape(16.dp), colors = inputColors,
            textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 17.sp))
        if (saveLabel != null) BigButton(saveLabel, Modifier.fillMaxWidth().testTag("lend_plan_save"), BtnStyle.TONAL, fontSize = 18.sp) { onChange(memo, due) }
    }
}
