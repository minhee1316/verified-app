package com.verified.incar.ui

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.core.HandoverRecord
import com.verified.incar.core.MyHyundai
import com.verified.incar.core.VerdictEngine

/** 과태료·범칙금 고지서의 단속 일시가 맡긴 시간 안인지 맡김 이력서에서 찾아 준다. */
@Composable
internal fun FineCheckCard(history: List<HandoverRecord>) {
    var text by rememberSaveable { mutableStateOf("") }
    val at = MyHyundai.parseDateTime(text)
    val hit = at?.let { MyHyundai.handoverAt(history, it) }
    Card(Modifier.fillMaxWidth().testTag("fine_check"), padding = 24.dp) {
        CardHeader(null, "과태료·범칙금 날짜 대조")
        OutlinedTextField(
            value = text, onValueChange = { text = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("fine_input"),
            label = { Text("단속 일시 (예: 2026-11-10 14:30)") },
            shape = RoundedCornerShape(16.dp),
            trailingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(24.dp), tint = C.title) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.title, unfocusedBorderColor = C.sub,
                focusedLabelColor = C.title, unfocusedLabelColor = C.sub, cursorColor = C.title,
            ),
            textStyle = LocalTextStyle.current.copy(color = C.ink, fontSize = 18.sp),
        )
        when {
            text.isBlank() -> Text("단속 시각에 차를 맡긴 상태였는지 찾아 줘요.", fontSize = 16.sp, color = C.sub)
            at == null -> Text("연도·월·일·시·분을 모두 넣어 주세요.", fontSize = 17.sp, color = C.warn)
            hit == null -> Text("이 시각에 맡긴 기록이 없어요.", fontSize = 18.sp, color = C.ink)
            else -> Column(Modifier.fillMaxWidth().testTag("fine_proof").clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(if (C.dark) C.card else Tone.greenSoft, if (C.dark) C.chip else C.card)))
                .border(1.dp, C.ok.copy(alpha = 0.45f), RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(C.ok.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(24.dp), tint = C.ok)
                    }
                    Text("이 시각은 차를 맡긴 시간이에요", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.ink)
                }
                Text("${if (hit.kind == "맡김") "정비 맡김" else hit.kind} · ${VerdictEngine.date(hit.startMs)} ${VerdictEngine.hhmm(hit.startMs)} ~ " +
                    (if (VerdictEngine.date(hit.endMs) != VerdictEngine.date(hit.startMs)) VerdictEngine.date(hit.endMs) + " " else "") + VerdictEngine.hhmm(hit.endMs), fontSize = 16.sp, color = C.sub)
                HandoverTimeRange(hit.startMs, hit.endMs, at)
            }
        }
    }
}

/** 조회 시각을 실제 맡김 기간 비율로 배치한다. 양 끝의 핀도 카드 경계에서 잘리지 않는다. */
@Composable
private fun HandoverTimeRange(startMs: Long, endMs: Long, queryMs: Long) {
    val fraction = if (endMs <= startMs) 0.5f else
        ((queryMs - startMs).toDouble() / (endMs - startMs).toDouble()).toFloat().coerceIn(0f, 1f)
    val span = C.ok
    val marker = C.title
    val surface = C.card
    val start = VerdictEngine.hhmm(startMs)
    val end = VerdictEngine.hhmm(endMs)
    val query = VerdictEngine.hhmm(queryMs)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.ice).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = marker)
            Text("단속 $query", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = marker)
        }
        Canvas(Modifier.fillMaxWidth().height(40.dp).testTag("fine_timeline").semantics {
            contentDescription = "맡김 $start, 단속 $query, 반납 $end"
        }) {
            val inset = 10.dp.toPx().coerceAtMost(size.width / 2)
            val y = size.height / 2
            val x0 = inset
            val x1 = size.width - inset
            drawLine(span.copy(alpha = 0.2f), Offset(x0, y), Offset(x1, y),
                strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
            drawLine(span, Offset(x0, y), Offset(x1, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(span, 3.dp.toPx(), Offset(x0, y))
            drawCircle(span, 3.dp.toPx(), Offset(x1, y))
            val x = x0 + (x1 - x0) * fraction
            drawLine(marker, Offset(x, 7.dp.toPx()), Offset(x, size.height - 7.dp.toPx()),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(surface, 8.dp.toPx(), Offset(x, y))
            drawCircle(marker, 6.dp.toPx(), Offset(x, y))
            drawCircle(surface, 2.dp.toPx(), Offset(x, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("맡김\n$start", Modifier.weight(1f), fontSize = 15.sp, color = C.sub)
            Text("반납\n$end", Modifier.weight(1f), fontSize = 15.sp, color = C.sub, textAlign = TextAlign.End)
        }
    }
}

/** ⑧ 도우미의 무선 프로젝션 안내. 일반 앱은 Wi-Fi를 끄고 켤 수 없어 점검 순서만 보여 준다. */
@Composable
internal fun ProjectionHelpCard() {
    var symptom by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().testTag("projection_help"), padding = 22.dp) {
        CardHeader(null, "카플레이·안드로이드 오토가 이상해요")
        Wrap {
            MyHyundai.PROJECTION_HELP.forEach { help ->
                Chip(help.symptom, help.symptom == symptom) { symptom = help.symptom.takeIf { it != symptom } }
            }
        }
        MyHyundai.PROJECTION_HELP.firstOrNull { it.symptom == symptom }?.steps?.forEachIndexed { i, step ->
            Text("${i + 1}. $step", fontSize = 18.sp, lineHeight = 26.sp, color = C.ink)
        }
        if (isCompact()) Chip("이 폰의 Wi-Fi 설정 열기", null) {
            try { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) } catch (_: ActivityNotFoundException) {}
        }
        Text("주행 중에는 조작하지 마세요.", fontSize = 16.sp, color = C.sub)
    }
}
