package com.verified.incar.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
            else -> Text(
                "이 시각은 ${hit.kind}로 맡긴 시간이에요 · ${VerdictEngine.date(hit.startMs)} ${VerdictEngine.hhmm(hit.startMs)} ~ ${VerdictEngine.date(hit.endMs)} ${VerdictEngine.hhmm(hit.endMs)}",
                fontSize = 18.sp, color = C.ink,
            )
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
        Text("이 앱이 연결을 직접 고치지는 못해요. 주행 중에는 조작하지 마세요.", fontSize = 16.sp, color = C.sub)
    }
}
