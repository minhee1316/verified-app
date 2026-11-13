package com.verified.incar.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.CarModel

/** 체험할 수 있는 차종(그 밖의 차량은 제어 기능이 없어 체험에서 뺀다). */
internal val TRIAL_MODELS = listOf(CarModel.GRANDEUR, CarModel.AVANTE, CarModel.TUCSON, CarModel.IONIQ3)

/** 체험 시작(차종 고르기)과 체험 끝내기. 저장소가 바뀌므로 둘 다 앱을 다시 연다. */
@Composable
internal fun TrialScreen(vm: VerifiedRuntime, u: UiState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(hPad(28.dp)).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            u.trial -> Card(Modifier.fillMaxWidth(), padding = 20.dp) {
                Text("체험 모드", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("지금 보이는 차량 값과 기록은 모두 예시예요.", fontSize = 18.sp, color = C.ink)
                Text("다른 차종으로 바꾸기", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                ModelChoices(u.settings.carModel) { model -> vm.settings { it.copy(carModel = model) } }
                Text("체험을 끝내면 예시 맡김 기록과 알림을 지우고 실제 모드로 돌아가요. 앱이 다시 열려요.", fontSize = 17.sp, color = C.sub)
                BigButton("체험 끝내기", Modifier.fillMaxWidth().testTag("trial_finish"), BtnStyle.OUTLINE) {
                    if (vm.endTrial()) restartApp(context)
                }
            }
            u.trialAvailable -> {
                var pick by rememberSaveable { mutableStateOf(u.settings.carModel?.takeIf { it in TRIAL_MODELS } ?: CarModel.GRANDEUR) }
                Card(Modifier.fillMaxWidth(), padding = 20.dp) {
                    Text("예시 차량 고르기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text("고른 차종의 예시 값으로 대리·정비·빌려주기 과정이 자동으로 진행돼요. 실제 차 상태가 아니에요.", fontSize = 17.sp, color = C.sub)
                    ModelChoices(pick) { pick = it }
                    Text("체험 기록은 실제 기록과 따로 저장되고, 체험을 끝내면 지워져요. 시작하면 앱이 다시 열려요.", fontSize = 16.sp, color = C.sub)
                    BigButton("${pick.label}로 체험 시작", Modifier.fillMaxWidth().testTag("trial_start")) {
                        if (vm.startTrial(pick)) restartApp(context)
                    }
                }
            }
            else -> Card(Modifier.fillMaxWidth(), padding = 20.dp) {
                Text("지금은 체험을 시작할 수 없어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("차에 연결되어 있거나 진행 중인 맡김이 있으면 체험 모드를 열 수 없어요.", fontSize = 17.sp, color = C.sub)
            }
        }
    }
}

@Composable
private fun ModelChoices(selected: CarModel?, onPick: (CarModel) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TRIAL_MODELS.forEach { model ->
            val on = model == selected
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(if (on) C.chip else C.card)
                    .border(if (on) 2.dp else 1.dp, if (on) C.title else C.line, shape)
                    .selectable(selected = on, role = Role.RadioButton) { onPick(model) }
                    .heightIn(min = 72.dp).padding(horizontal = 14.dp, vertical = 8.dp).testTag("trial_model_${model.name}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CarPhoto(model, Modifier.width(110.dp).heightIn(max = 48.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.label, fontSize = 19.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = C.ink)
                    Text(when { model.ev -> "전기차 · 공식 사진"; model == CarModel.TUCSON -> "SUV · 공식 사진"; else -> "세단 · 공식 사진" }, fontSize = 15.sp, color = C.sub)
                }
            }
        }
    }
}

/** 체험 모드를 켜고 끄면 저장소가 바뀌므로 앱을 처음부터 다시 연다. */
internal fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    android.os.Process.killProcess(android.os.Process.myPid())
}
