package com.verified.incar.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.core.EvRoute
import com.verified.incar.core.Fence
import com.verified.incar.core.Level
import com.verified.incar.core.MyHyundai
import com.verified.incar.core.fenceVerdict
import kotlin.math.roundToInt

private const val EXAMPLE_NOTE = "체험용 예시 화면이에요. 실제 차와 지도의 값이 아니에요."

@Composable
private fun DemoStates(options: List<String>, selected: String, tag: String, onPick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), padding = 16.dp) {
        Text("시연 상태", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
        Wrap { options.forEach { Chip(it, it == selected, Modifier.testTag("${tag}_$it")) { onPick(it) } } }
    }
}

@Composable
private fun MapBox(description: String, draw: DrawScope.() -> Unit) {
    val height = if (isCompact()) 280.dp else 380.dp
    val shape = RoundedCornerShape(16.dp)
    Canvas(Modifier.fillMaxWidth().height(height).clip(shape).background(C.chip).semantics { contentDescription = description }, onDraw = draw)
}

/** 도로 격자. 실제 지도가 아니라 코드로 그린 배경이다. */
private fun DrawScope.roads(color: Color) {
    val w = size.width; val h = size.height
    listOf(0.18f, 0.46f, 0.74f).forEach { drawLine(color, Offset(0f, h * it), Offset(w, h * (it + 0.08f)), 3f) }
    listOf(0.14f, 0.38f, 0.66f, 0.88f).forEach { drawLine(color, Offset(w * it, 0f), Offset(w * (it - 0.05f), h), 3f) }
}

private fun levelTint(level: Level) = when (level) {
    Level.OK, Level.INFO -> C.ok
    Level.WARN -> C.warn
    Level.DANGER -> C.danger
}

// ------------------------------------------------------------------ 전기차 경로
private val ROUTE = listOf(0.08f to 0.86f, 0.16f to 0.74f, 0.24f to 0.62f, 0.33f to 0.55f, 0.40f to 0.42f, 0.48f to 0.38f, 0.56f to 0.30f, 0.66f to 0.33f, 0.74f to 0.26f, 0.80f to 0.24f, 0.88f to 0.16f, 0.93f to 0.10f)

private fun routePoint(t: Float): Offset {
    val pos = t * (ROUTE.size - 1)
    val i = pos.toInt().coerceAtMost(ROUTE.size - 2)
    val f = pos - i
    return Offset(ROUTE[i].first + (ROUTE[i + 1].first - ROUTE[i].first) * f, ROUTE[i].second + (ROUTE[i + 1].second - ROUTE[i].second) * f)
}

@Composable
internal fun EvRouteScreen() {
    val normal = "정상 주행"; val low = "배터리 부족"
    var state by rememberSaveable { mutableStateOf(normal) }
    var driving by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf(true) }
    val cold = state == low
    val start = if (cold) 55.0 else 80.0
    val along = remember(state) { EvRoute.batteryAlong(start, cold) }
    val arrival = along.last().coerceAtLeast(0.0).roundToInt()
    val extra = EvRoute.extraUse(start, cold).roundToInt()
    val stop = EvRoute.suggestedStop(along)
    val short = arrival < MyHyundai.TRIP_LOW_PCT
    val measurer = rememberTextMeasurer()
    val ink = C.ink; val line = C.line; val card = C.card; val ok = C.ok; val warn = C.warn; val danger = C.danger
    TwoUp(28.dp, 20.dp, 20.dp, 12.dp, leftWeight = 1.2f, left = {
        Note(
            (if (cold) "외기 -5℃와 오르막으로" else "오르막 구간으로") + " 배터리를 약 $extra% 더 써요" +
                if (short && stop != null) " · ${stop.km.roundToInt()}km 충전소 경유를 권장해요" else "",
            if (short) Level.DANGER else Level.INFO,
        )
        MapBox("예시 지도: 경로와 충전소") {
            roads(line)
            val steps = 60
            for (i in 0 until steps) {
                val a = routePoint(i / steps.toFloat()); val b = routePoint((i + 1) / steps.toFloat())
                val pct = EvRoute.batteryAt(along, EvRoute.TOTAL_KM * i / steps)
                val level = EvRoute.level(pct)
                val color = when (level) { Level.DANGER -> danger; Level.WARN -> warn; else -> ok }
                // 색만으로 구분하지 않도록 구간마다 굵기도 다르게 그린다
                val width = when (level) { Level.DANGER -> 5f; Level.WARN -> 9f; else -> 14f }
                drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), width, StrokeCap.Round)
            }
            EvRoute.CHARGERS.forEach { c ->
                val p = Offset(c.x * size.width, (c.y - 0.1f) * size.height)
                val full = c.free == 0
                val r = 26f
                // 빈자리가 있으면 원, 만차면 네모
                if (full) {
                    drawRoundRect(card, Offset(p.x - r, p.y - r), Size(r * 2, r * 2), CornerRadius(6f))
                    drawRoundRect(danger, Offset(p.x - r, p.y - r), Size(r * 2, r * 2), CornerRadius(6f), Stroke(4f))
                } else {
                    drawCircle(card, r, p)
                    drawCircle(ok, r, p, style = Stroke(4f))
                }
                val text = measurer.measure("⚡".repeat(c.bolts) + "\n빈 ${c.free}", TextStyle(fontSize = 9.sp, color = ink, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                drawText(text, topLeft = Offset(p.x - text.size.width / 2, p.y - text.size.height / 2))
            }
        }
        Text("경로 굵기: 굵음 50% 이상 · 중간 20~50% · 가늘음 20% 미만   핀: 원 빈자리 있음 · 네모 만차", fontSize = 15.sp, color = C.sub)
    }, right = {
        Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("주행 모드 (시속 10km 이상)", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                Box(Modifier.testTag("ev_drive")) { NavySwitch(driving, "주행 모드") { driving = it } }
            }
            if (driving) Column(Modifier.testTag("ev_simple"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("도착 시 예상 배터리", fontSize = 18.sp, color = C.sub)
                Text("$arrival%", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = if (short) C.danger else C.ink)
                Text("다음 충전소까지", fontSize = 18.sp, color = C.sub)
                Text("${EvRoute.nextCharger().km.roundToInt()}km", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = C.ink)
            } else {
                var drag by remember { mutableStateOf(0f) }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("ev_sheet_handle")
                        .draggable(rememberDraggableState { drag += it }, Orientation.Vertical, onDragStopped = {
                            if (drag > 40f) open = false else if (drag < -40f) open = true
                            drag = 0f
                        })
                        .clickable(role = Role.Button, onClickLabel = if (open) "그래프 접기" else "그래프 펼치기") { open = !open },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.width(48.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(C.sub)) }
                if (open) {
                    Row { Text("고도(m)", Modifier.weight(1f), fontSize = 15.sp, color = C.sub); Text("예상 배터리(%)", fontSize = 15.sp, color = C.sub) }
                    val area = C.ice; val title = C.title
                    Canvas(Modifier.fillMaxWidth().height(170.dp).testTag("ev_chart").semantics { contentDescription = "고도와 예상 배터리 그래프. 도착 시 $arrival%" }) {
                        val w = size.width; val h = size.height
                        val n = EvRoute.ELEVATION.size - 1
                        val top = 900f
                        val fill = Path().apply {
                            moveTo(0f, h)
                            EvRoute.ELEVATION.forEachIndexed { i, e -> lineTo(w * i / n, h - h * e / top) }
                            lineTo(w, h); close()
                        }
                        drawPath(fill, area)
                        val battery = Path().apply {
                            along.forEachIndexed { i, p ->
                                val y = h - h * (p.coerceIn(0.0, 100.0) / 100).toFloat()
                                if (i == 0) moveTo(0f, y) else lineTo(w * i / n, y)
                            }
                        }
                        drawPath(battery, title, style = Stroke(6f, cap = StrokeCap.Round))
                    }
                    Row {
                        Text("${start.roundToInt()}%", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.ink)
                        Text("$arrival%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (short) C.danger else C.ink)
                    }
                    Row { Text("0km", Modifier.weight(1f), fontSize = 15.sp, color = C.sub); Text("${EvRoute.TOTAL_KM.roundToInt()}km", fontSize = 15.sp, color = C.sub) }
                }
                HorizontalDivider(color = C.line)
                Row {
                    Text("다음 충전소 ${EvRoute.nextCharger().km.roundToInt()}km", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
                    Text("도착 $arrival%", fontSize = 19.sp, color = if (short) C.danger else C.ink)
                }
            }
        }
        DemoStates(listOf(normal, low), state, "ev") { state = it }
        Text(EXAMPLE_NOTE, fontSize = 15.sp, color = C.sub)
    })
}

// ------------------------------------------------------------------ 발레 감시
@Composable
internal fun ValetWatchScreen() {
    val parked = "정상 주차"; val out = "구역 이탈"; val noise = "GPS 오차"
    var state by rememberSaveable { mutableStateOf(parked) }
    val fence = when (state) {
        out -> fenceVerdict(outside = true, ignitionOn = true, movedKm = 1.8)
        noise -> fenceVerdict(outside = true, ignitionOn = false, movedKm = 0.0)
        else -> fenceVerdict(outside = false, ignitionOn = false, movedKm = 0.0)
    }
    val pulse by rememberInfiniteTransition(label = "fence").animateFloat(0.18f, 0.42f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    val line = C.line; val title = C.title; val danger = C.danger; val warn = C.warn; val sub = C.sub; val navy = C.navy
    TwoUp(28.dp, 20.dp, 20.dp, 12.dp, leftWeight = 1.1f, left = {
        when (fence) {
            Fence.BREACH -> Note("시동이 켜진 채 지정 구역을 벗어났어요", Level.DANGER)
            Fence.GPS_NOISE -> Note("지하 주차장 신호 미약으로 위치 보정 중이에요", Level.WARN)
            Fence.INSIDE -> Note("맡긴 위치 반경 안에 있어요", Level.OK)
        }
        MapBox("예시 지도: 맡긴 위치 반경") {
            roads(line)
            val center = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * 0.34f
            val color = when (fence) { Fence.BREACH -> danger; Fence.GPS_NOISE -> sub; Fence.INSIDE -> title }
            drawCircle(color, r, center, alpha = if (fence == Fence.BREACH) pulse else 0.18f)
            drawCircle(if (fence == Fence.GPS_NOISE) warn else color, r, center, style = Stroke(4f))
            val car = when (fence) {
                Fence.BREACH -> Offset(center.x + r * 1.35f, center.y - r * 0.9f)
                Fence.GPS_NOISE -> Offset(center.x + r * 1.12f, center.y + r * 0.3f)
                Fence.INSIDE -> center
            }
            if (fence == Fence.BREACH) drawLine(danger, center, car, 5f, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(4f, 18f)))
            drawRoundRect(navy, Offset(car.x - 26f, car.y - 14f), Size(52f, 28f), CornerRadius(10f))
            drawRoundRect(Color.White, Offset(car.x - 10f, car.y - 9f), Size(20f, 18f), CornerRadius(4f))
        }
        DemoStates(listOf(parked, out, noise), state, "valet") { state = it }
    }, right = {
        Card(Modifier.fillMaxWidth().testTag("valet_dash"), padding = 16.dp) {
            val breach = fence == Fence.BREACH
            val gauge = when (fence) { Fence.BREACH -> 0.95f; Fence.GPS_NOISE -> 0.5f; Fence.INSIDE -> 0.15f }
            val track = C.line; val arc = when (fence) { Fence.BREACH -> C.danger; Fence.GPS_NOISE -> C.warn; Fence.INSIDE -> C.ok }
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.size(220.dp, 120.dp)) {
                    val d = size.width - 24f
                    val tl = Offset(12f, 12f)
                    drawArc(track, 180f, 180f, false, tl, Size(d, d), style = Stroke(22f, cap = StrokeCap.Round))
                    drawArc(arc, 180f, 180f * gauge, false, tl, Size(d, d), style = Stroke(22f, cap = StrokeCap.Round))
                }
                Text(when (fence) { Fence.BREACH -> "위험"; Fence.GPS_NOISE -> "확인 중"; Fence.INSIDE -> "안전" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (breach) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null, tint = C.title)
                Text(if (breach) "문 잠금 해제" else "문 잠김", Modifier.weight(1f), fontSize = 19.sp, color = C.ink)
                Text("${if (breach) 48 else 0} km/h", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.ink)
            }
        }
        Card(Modifier.fillMaxWidth().testTag("valet_log"), padding = 16.dp) {
            CardHeader(null, "기록")
            val log = listOfNotNull(
                when (fence) {
                    Fence.BREACH -> Triple("16:45", "지정 구역 이탈 감지", Level.DANGER)
                    Fence.GPS_NOISE -> Triple("16:45", "위치 신호 불안정 · 시동 꺼짐", Level.WARN)
                    Fence.INSIDE -> null
                },
                Triple("16:30", "발레 감시 시작", Level.INFO),
                Triple("16:28", "시동 켜짐", Level.INFO),
            )
            log.forEach { (at, what, level) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (level == Level.INFO) C.sub else levelTint(level)))
                    Text(at, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Text(what, Modifier.weight(1f), fontSize = 18.sp, color = if (level == Level.INFO) C.ink else levelTint(level))
                }
            }
        }
        Text(EXAMPLE_NOTE, fontSize = 15.sp, color = C.sub)
    })
}

// ------------------------------------------------------------------ 무선 간섭 진단
@Composable
internal fun RadioDiagScreen() {
    val normal = "정상 연결"; val zone = "간섭 구역 진입"
    var state by rememberSaveable { mutableStateOf(normal) }
    var help by rememberSaveable { mutableStateOf(false) }
    val jam = state == zone
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val line = C.line; val ink = C.ink; val violet = C.violet; val blue = C.blue; val card = C.card; val sub = C.sub
    TwoUp(28.dp, 20.dp, 20.dp, 12.dp, leftWeight = 1.1f, left = {
        AnimatedVisibility(jam, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.navy).testTag("radio_banner")
                    .clickable(role = Role.Button) { help = true }.heightIn(min = 52.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("전파 간섭 구역 감지 · 눌러서 복구 안내", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
        MapBox("예시 지도: 전파 간섭 구역") {
            roads(line)
            val w = size.width; val h = size.height
            // 톨게이트
            drawLine(sub, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h), 46f)
            drawRoundRect(card, Offset(w * 0.28f, h * 0.36f), Size(w * 0.44f, 26f), CornerRadius(4f))
            drawRoundRect(sub, Offset(w * 0.28f, h * 0.36f), Size(w * 0.44f, 26f), CornerRadius(4f), Stroke(3f))
            if (jam) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                listOf(Offset(w * 0.42f, h * 0.38f) to violet, Offset(w * 0.6f, h * 0.68f) to blue).forEach { (c, color) ->
                    listOf(0.28f, 0.19f, 0.1f).forEachIndexed { i, r ->
                        drawCircle(color, size.minDimension * r, c, alpha = 0.14f + i * 0.08f)
                        drawCircle(color, size.minDimension * r, c, style = Stroke(3f, pathEffect = dash))
                    }
                    val label = measurer.measure("수신 세기 -85dBm", TextStyle(fontSize = 11.sp, color = ink, fontWeight = FontWeight.Bold))
                    drawRoundRect(card, Offset(c.x - label.size.width / 2 - 8f, c.y - label.size.height / 2 - 4f), Size(label.size.width + 16f, label.size.height + 8f), CornerRadius(8f))
                    drawText(label, topLeft = Offset(c.x - label.size.width / 2, c.y - label.size.height / 2))
                }
            }
        }
        Text("보라·파란 점선 원은 전파 간섭 구역이에요. 도로 정체 표시와 달라요.", fontSize = 15.sp, color = C.sub)
    }, right = {
        Card(Modifier.fillMaxWidth().testTag("radio_check"), padding = 16.dp) {
            CardHeader(null, "진단 체크리스트")
            listOf("Wi-Fi 5GHz 채널" to jam, "블루투스 연결" to false, "GPS 보정" to false, "스마트폰 발열" to false).forEach { (name, bad) ->
                Row(
                    Modifier.fillMaxWidth().testTag("radio_item_$name").then(if (bad) Modifier.clickable(role = Role.Button) { help = true } else Modifier).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(name, Modifier.weight(1f), fontSize = 19.sp, color = C.ink)
                    Text(if (bad) "확인 필요" else "정상", fontSize = 17.sp, color = if (bad) C.danger else C.ok)
                    Icon(if (bad) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle, null, tint = if (bad) C.danger else C.ok)
                }
            }
        }
        DemoStates(listOf(normal, zone), state, "radio") { state = it }
        Text(EXAMPLE_NOTE + " 앱은 전파 세기를 재거나 Wi-Fi를 직접 끄고 켤 수 없어요.", fontSize = 15.sp, color = C.sub)
    })
    if (help) AlertDialog(
        onDismissRequest = { help = false }, containerColor = C.card,
        title = { Text("다시 연결해 보세요", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = C.ink) },
        text = {
            Column(Modifier.testTag("radio_help"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MyHyundai.PROJECTION_HELP.first { it.symptom == "톨게이트·터널에서 멈춰요" }.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", fontSize = 18.sp, color = C.ink)
                }
            }
        },
        confirmButton = { TextButton(onClick = { help = false }) { Text("닫기", color = C.title, fontSize = 18.sp) } },
        dismissButton = if (isCompact()) ({
            TextButton(onClick = { try { context.startActivity(Intent(Settings.Panel.ACTION_WIFI)) } catch (_: ActivityNotFoundException) {} }) {
                Text("Wi-Fi 설정", color = C.title, fontSize = 18.sp)
            }
        }) else null,
    )
}
