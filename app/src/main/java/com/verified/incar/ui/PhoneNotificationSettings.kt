package com.verified.incar.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.verified.incar.PhoneDelivery
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime

internal fun PhoneDelivery?.displayText() = when (this) {
    PhoneDelivery.QUEUED -> "휴대폰 전송 대기"
    PhoneDelivery.ACCEPTED -> "알림 서버 접수"
    PhoneDelivery.FAILED -> "휴대폰 전송 실패"
    null -> ""
}

@Composable
internal fun PhoneNotificationSettings(vm: VerifiedRuntime, u: UiState) {
    val topic = u.phoneTopic
    var showCode by remember(topic) { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(hPad(28.dp)), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Text("차주 휴대폰 알림", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = C.title)
            if (topic == null) {
                Text("차를 맡기거나 돌려받을 때 내 휴대폰에서 알림을 받을 수 있어요. 휴대폰에 ntfy 앱을 설치하고 알림을 허용해 주세요.", color = C.ink, fontSize = 18.sp)
                Text("연결하면 ntfy.sh에 사건 종류를 보내요. 위치, 운전자 이름, 상세한 차량 기록은 차 안에 남아요.", color = C.sub, fontSize = 16.sp)
                BigButton("동의하고 연결 시작", Modifier.fillMaxWidth()) { vm.enablePhoneNotifications() }
            } else {
                Text("① 휴대폰에 ntfy 설치 → ② 같은 주제 구독 → ③ 시험 알림 확인", color = C.ink, fontSize = 18.sp)
                BigButton(if (showCode) "연결 코드 숨기기" else "연결 QR·주제 보기", Modifier.fillMaxWidth(), BtnStyle.OUTLINE) { showCode = !showCode }
                if (showCode) {
                    val link = "ntfy://ntfy.sh/$topic"
                    Text("Android 휴대폰으로 QR을 읽으세요. 직접 연결하려면 ntfy 앱의 + 버튼을 눌러 아래 주제 이름을 입력하세요.", color = C.ink, fontSize = 17.sp)
                    TopicQr(link, Modifier.align(Alignment.CenterHorizontally))
                    SelectionContainer { Text(topic, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    if (isCompact()) Chip("이 휴대폰의 ntfy에서 열기", null) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage("io.heckel.ntfy"))
                        } catch (_: ActivityNotFoundException) {
                            vm.toast("ntfy 앱을 설치한 뒤 주제를 구독해 주세요.")
                        }
                    }
                    Text("이 코드를 아는 사람은 알림을 읽거나 보낼 수 있어요. 다시 연결하면 새 코드가 생겨요.", color = C.sub, fontSize = 16.sp)
                }
                BigButton("시험 알림 보내기", Modifier.fillMaxWidth()) { vm.sendPhoneTest() }
                u.notices.firstOrNull { it.phoneStatus != null }?.let {
                    Text("최근 전송 · ${it.phoneStatus.displayText()}", color = C.ink, fontSize = 17.sp)
                }
                Text("휴대폰 알림창에서 ‘Hyundai CarGuard’ 시험 알림을 확인해 주세요. 알림이 없으면 ntfy의 주제 이름과 휴대폰 알림 권한을 확인하세요.", color = C.sub, fontSize = 16.sp)
                Chip("휴대폰 연결 해제", null) { vm.disablePhoneNotifications() }
            }
        }
    }
}

@Composable
private fun TopicQr(link: String, modifier: Modifier = Modifier) {
    val matrix = remember(link) { QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 1, 1) }
    Canvas(modifier.size(184.dp).semantics { contentDescription = "휴대폰 알림 구독 QR 코드" }) {
        drawRect(Color.White)
        val cell = (size.minDimension / matrix.width).toInt().toFloat()
        val inset = ((size.minDimension - cell * matrix.width) / 2).toInt().toFloat()
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(inset + x * cell, inset + y * cell), Size(cell, cell))
        }
    }
}
