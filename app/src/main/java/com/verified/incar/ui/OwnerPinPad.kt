package com.verified.incar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.Level
import com.verified.incar.core.OwnerPin

/** 차 화면용 숫자 패드. 누른 숫자는 점으로만 보여 옆자리에서 읽히지 않게 한다. */
@Composable
fun PinPad(
    title: String, sub: String, error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit,
    maxLen: Int = 6, valid: (String) -> Boolean = OwnerPin::valid, extra: (@Composable () -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(onClick = {}), contentAlignment = Alignment.Center) {
        // 가로 폰처럼 창이 낮으면 패드를 스크롤해 아래 줄(0·확인·취소)까지 누를 수 있게 한다. 창에 다 들어가면 지금과 같다
        Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
        Card(Modifier.width(560.dp).testTag("pin_pad"), padding = 28.dp) {
            CardHeader(null, title, sub, kicker = "차주 잠금")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(maxLen) { i -> Box(Modifier.padding(8.dp).size(18.dp).clip(CircleShape).background(if (i < pin.length) C.navy else C.line)) }
            }
            if (error != null) Text(error, Modifier.fillMaxWidth(), color = C.danger, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("지우기", "0", "확인")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { k ->
                        BigButton(
                            k, Modifier.weight(1f).testTag("pin_$k"), if (k == "확인") BtnStyle.FILLED else BtnStyle.TONAL,
                            enabled = k != "확인" || valid(pin), fontSize = 24.sp,
                        ) {
                            when (k) {
                                "지우기" -> pin = pin.dropLast(1)
                                "확인" -> { onSubmit(pin); pin = "" }
                                else -> if (pin.length < maxLen) pin += k
                            }
                        }
                    }
                }
            }
            // 높이가 늘지 않도록 취소와 보조 링크를 한 줄에 둔다
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("취소", Modifier.clickable(onClick = onCancel).padding(8.dp), color = C.sub, fontSize = 18.sp)
                if (extra != null) {
                    Text("  ·  ", color = C.line, fontSize = 18.sp)
                    extra()
                }
            }
        }
        }
    }
}

/** 차주 전용 조작을 눌렀는데 잠겨 있을 때 뜨는 PIN 패드. */
@Composable
fun OwnerPinGate(vm: VerifiedRuntime) {
    var error by remember { mutableStateOf<String?>(null) }
    var recovering by remember { mutableStateOf(false) }
    if (!recovering) PinPad(
        "차주 PIN을 입력해 주세요", "설정 변경·대리 종료 승인·기록 지우기·키 공유는 차주만 할 수 있어요.", error,
        onSubmit = { error = vm.unlockOwner(it) }, onCancel = { vm.cancelOwnerRequest() },
        extra = {
            Text(
                "PIN을 잊었어요 (복구 코드)", Modifier.testTag("pin_forgot").clickable { recovering = true; error = null }.padding(8.dp),
                color = C.teal, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
        },
    ) else PinPad(
        "복구 코드 12자리", "PIN을 정할 때 받은 복구 코드를 넣으면 PIN이 꺼져요. 설정에서 새로 정해 주세요.", error,
        onSubmit = { error = vm.recoverOwner(it) }, onCancel = { recovering = false; error = null },
        maxLen = OwnerPin.RECOVERY_LEN, valid = OwnerPin::validRecovery,
    )
}

/**
 * 설정 화면의 차주 잠금 묶음: PIN 정하기(두 번 입력) · 바꾸기 · 지금 잠그기 · 끄기.
 * 한 번만 보여 주는 복구 코드는 [onRecovery]로 넘겨, 설정 화면이 칸 배치(회전·탭)와 상관없이 띄운다.
 */
@Composable
fun OwnerPinSettings(vm: VerifiedRuntime, u: UiState, onRecovery: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var first by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Panel {
        CardHeader(
            null, "차주 잠금 (PIN)",
            if (u.ownerPinSet) "설정·대리 종료 승인·기록 지우기·키 공유·시뮬레이터는 PIN을 넣어야 해요. 대리나 차 빌려주기를 시작하면 바로 다시 잠겨요."
            else "PIN을 정하면 대리기사님이나 다른 탑승자가 설정을 바꾸거나 대리 종료를 대신 승인할 수 없어요.",
        ) { Pill(if (u.ownerPinSet) "켜짐" else "꺼짐", if (u.ownerPinSet) Level.OK else Level.WARN) }
        Wrap {
            BigButton(if (u.ownerPinSet) "PIN 바꾸기" else "PIN 정하기", fontSize = 18.sp) { step = 1; error = null }
            if (u.ownerPinSet) {
                BigButton("지금 잠그기", style = BtnStyle.TONAL, fontSize = 18.sp) { vm.lockOwner() }
                BigButton("PIN 끄기", style = BtnStyle.SOFT_DANGER, fontSize = 18.sp) { vm.setOwnerPin(null) }
            }
        }
    }
    if (step > 0) androidx.compose.ui.window.Dialog({ step = 0 }, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad(
            if (step == 1) "새 PIN (숫자 4~6자리)" else "한 번 더 입력해 주세요", "잊어버리면 PIN을 정한 뒤 보여 드리는 복구 코드로 끌 수 있어요.", error,
            onSubmit = { p ->
                when {
                    step == 1 -> { first = p; step = 2; error = null }
                    p == first -> { vm.setOwnerPin(p)?.let(onRecovery); step = 0 }
                    else -> { error = "처음 입력과 달라요. 다시 정해 주세요"; step = 1 }
                }
            },
            onCancel = { step = 0 },
        )
    }
}

/** PIN을 정한 직후 한 번만 보여 주는 복구 코드. 차주가 닫을 때까지 유지한다. */
@Composable
fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDone, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
            Card(Modifier.width(560.dp).testTag("recovery_code"), padding = 28.dp) {
                CardHeader(null, "복구 코드를 적어 두세요", "PIN을 잊었을 때 이 코드로 PIN을 끌 수 있어요. 이 화면을 닫으면 다시 볼 수 없어요.", kicker = "차주 잠금")
                Text(code.chunked(4).joinToString("  "), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 34.sp, fontWeight = FontWeight.Black, color = C.title, letterSpacing = 2.sp)
                BigButton("적어 두었어요", Modifier.fillMaxWidth(), fontSize = 20.sp) { onDone() }
            }
        }
    }
}
