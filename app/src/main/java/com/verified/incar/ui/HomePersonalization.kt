package com.verified.incar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.UiState
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.HomeFavorite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomePersonalization(vm: VerifiedRuntime, u: UiState, go: (String) -> Unit, editState: MutableState<Boolean>) {
    var editing by editState
    val selected = u.settings.homeFavorites
    Card(Modifier.fillMaxWidth(), padding = 14.dp) {
        Text("자주 쓰는 기능", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        BigButton(if (editing) "고정 편집 완료" else "홈 고정 편집", Modifier.fillMaxWidth().testTag("favorites_edit"), BtnStyle.OUTLINE) { editing = !editing }
        if (editing) {
            Text("선택한 순서대로 홈에 표시해요.", color = C.sub, fontSize = 15.sp)
            HomeFavorite.entries.forEach { feature ->
                val checked = feature.name in selected
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("favorite_pick_${feature.name}")
                    .semantics { stateDescription = if (checked) "고정됨" else "고정 안 됨" }
                    .clickable(role = Role.Checkbox) {
                        vm.settings { it.copy(homeFavorites = if (checked) it.homeFavorites - feature.name else (it.homeFavorites + feature.name).distinct()) }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, onCheckedChange = null)
                    Text(feature.label, Modifier.weight(1f), fontSize = 18.sp)
                }
            }
        } else {
            if (selected.isEmpty()) Text("자주 찾는 기능을 골라 홈에 고정해 보세요.", color = C.sub, fontSize = 16.sp)
            selected.mapNotNull { name -> HomeFavorite.entries.find { it.name == name } }.forEach { feature ->
                BigButton(feature.label, Modifier.fillMaxWidth().testTag("favorite_open_${feature.name}"), BtnStyle.OUTLINE) { go(feature.route) }
            }
        }
        val last = u.engine.controlLog.firstOrNull()
        Text(last?.let { "최근 제어 · ${it.kind.label} · ${it.result.label}" } ?: "아직 제어 이력이 없어요.", color = C.sub, fontSize = 15.sp)
        BigButton("최근 제어 결과 전체 보기", Modifier.fillMaxWidth().testTag("home_control_history"), BtnStyle.OUTLINE) { go("CONTROL_HISTORY") }
    }
}

@Composable
fun ControlHistory(u: UiState) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val formatter = remember { DateTimeFormatter.ofPattern("MM.dd HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("control_history"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (u.trial) "시연 제어 이력 · 최근 30건" else "제어 이력 · 최근 30건", color = C.sub, fontSize = 16.sp)
        if (u.engine.controlLog.isEmpty()) Card(Modifier.fillMaxWidth()) {
            Text("아직 제어 이력이 없어요", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("홈이나 차량 제어에서 조작하면 요청 시각과 확인 결과를 여기에 남겨요.", color = C.sub, fontSize = 17.sp)
        }
        u.engine.controlLog.forEachIndexed { index, ev ->
            val key = "${ev.kind}_${ev.sentAtMs}"
            Card(Modifier.fillMaxWidth().testTag("control_record_$index"), padding = 16.dp) {
                Text(ev.kind.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(formatter.format(Instant.ofEpochMilli(ev.sentAtMs)), color = C.sub, fontSize = 16.sp)
                Text(ev.result.label, color = levelColor(ev.result.level), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                BigButton(if (expanded == key) "확인 근거 접기" else "확인 근거 보기", Modifier.fillMaxWidth().testTag("control_detail_$index"), BtnStyle.OUTLINE) { expanded = if (expanded == key) null else key }
                if (expanded == key) Column(Modifier.testTag("control_steps_$index"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ev.steps.forEach { (at, line) -> Text("${formatter.format(Instant.ofEpochMilli(at))}\n$line", color = C.sub, fontSize = 16.sp) }
                }
            }
        }
    }
}
