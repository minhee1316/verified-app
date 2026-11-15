package com.verified.incar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.outlined.*
import com.verified.incar.UiState
import com.verified.incar.R
import com.verified.incar.core.EscortPhase
import com.verified.incar.core.Level
import com.verified.incar.core.ValetPhase
import com.verified.incar.core.CarModel

/** 사진 위의 밝은 상태 말풍선. 값이 없으면 초록 확인 표시로 바꾸지 않는다. */
@Composable
private fun ExitStatusBubble(item: ExitItem, modifier: Modifier = Modifier) {
    val colors = when { item.unread -> listOf(Color(0xFFE9EDF2), Color(0xFFCED6DF)); item.bad -> listOf(Color(0xFFFFE5BA), Color(0xFFFFBC70)); else -> listOf(Color(0xFFCBFFE1), Color(0xFF8AE6AE)) }
    val ink = when { item.unread -> Color(0xFF415266); item.bad -> Color(0xFF713800); else -> Color(0xFF06432E) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text((if (item.unread) "? " else if (item.bad) "! " else "✓ ") + item.label,
            Modifier.shadow(10.dp, shape, ambientColor = colors.last(), spotColor = colors.last())
                .clip(shape).background(Brush.verticalGradient(colors)).border(1.dp, Color.White.copy(alpha = 0.8f), shape)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            color = ink, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.size(18.dp, 10.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close() }, colors.last())
        }
    }
}

@Composable
internal fun ExitPhotoStage(model: CarModel, checks: List<ExitItem>, modifier: Modifier = Modifier) {
    val shown = checks.filter { it.label in listOf("창문", "전조등", "트렁크") }
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    Column(modifier.testTag("exit_photo")) {
        if (largeText) Wrap { shown.forEach { ExitStatusBubble(it) } }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val photoHeight = if (isCompact()) (maxWidth * 0.64f).coerceIn(200.dp, 230.dp)
                else (maxWidth * 0.80f).coerceIn(220.dp, 320.dp)
            Box(Modifier.fillMaxWidth().height(photoHeight).clip(RoundedCornerShape(16.dp))) {
                Image(painterResource(R.drawable.reference_sedan_hero), "${model.label} 하차 점검 안내용 차량 이미지",
                    Modifier.fillMaxSize().padding(top = if (largeText) 0.dp else 8.dp, bottom = 14.dp), contentScale = ContentScale.Fit)
                if (!largeText) shown.forEach { item ->
                    val point = when (item.label) {
                        "창문" -> androidx.compose.ui.BiasAlignment(-0.55f, -0.42f)
                        "전조등" -> androidx.compose.ui.BiasAlignment(-0.95f, 0.78f)
                        else -> androidx.compose.ui.BiasAlignment(0.95f, -0.15f)
                    }
                    ExitStatusBubble(item, Modifier.align(point))
                }
            }
        }
    }
}

/** 참고 이미지처럼 실내를 크게 보여 주되 열선 빛과 단계는 저장된 설정에 맞춰 그린다. */
@Composable
internal fun ComfortPerspectiveArtwork(driver: Int, passenger: Int, rear: Int, modifier: Modifier = Modifier,
    onDriver: () -> Unit, onPassenger: () -> Unit, onRear: () -> Unit) {
    BoxWithConstraints(modifier.aspectRatio(if (isCompact()) 1.22f else 1f).clip(RoundedCornerShape(22.dp)).testTag("comfort_seats")) {
        val width = maxWidth
        val widthPx = with(LocalDensity.current) { width.toPx() }
        // 사진과 오버레이는 같은 정사각 좌표를 쓰고, 폰에서는 위아래 여백만 잘라 조작부를 함께 보여 준다.
        Box(Modifier.requiredSize(width).align(Alignment.Center).graphicsLayer {
            scaleX = 1.35f; scaleY = 1.35f
            translationX = -widthPx * 0.15f; translationY = widthPx * 0.07f
        }) {
            Image(painterResource(R.drawable.reference_cabin_perspective), "출발 준비 실내", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                listOf(Triple(0.44f, 0.39f, driver), Triple(0.60f, 0.50f, passenger),
                    Triple(0.63f, 0.26f, rear), Triple(0.71f, 0.30f, rear), Triple(0.78f, 0.36f, rear)).forEach { (x, y, heat) ->
                    if (heat > 0) {
                        val at = Offset(w * x, h * y)
                        rotate(24f, at) {
                            drawOval(Brush.radialGradient(listOf(Color(0xFFFFA04A).copy(alpha = 0.48f + heat * 0.08f), Color(0x00FF8A25)), at, w * 0.11f),
                                Offset(at.x - w * 0.065f, at.y - h * 0.13f), androidx.compose.ui.geometry.Size(w * 0.13f, h * 0.26f))
                            drawOval(Brush.radialGradient(listOf(Color(0xFFFFB06B).copy(alpha = 0.65f), Color(0x00FF701C)), at, w * 0.065f),
                                Offset(at.x - w * 0.04f, at.y - h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.08f, h * 0.16f))
                        }
                    }
                }
            }
            @Composable fun HeatMarker(x: Float, y: Float, name: String, level: Int, tag: String, onClick: () -> Unit) {
                Column(Modifier.offset(width * x - 22.dp, width * y - 25.dp).size(44.dp, 50.dp)
                    .clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = "$name 열선 " + if (level > 0) "${level}단" else "끔" }.testTag(tag),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (level > 0) Canvas(Modifier.size(20.dp, 14.dp)) {
                        repeat(3) { i ->
                            val x0 = size.width * (0.22f + i * 0.28f)
                            drawPath(Path().apply { moveTo(x0, size.height); cubicTo(x0 + size.width * 0.24f, size.height * 0.62f, x0 - size.width * 0.20f, size.height * 0.37f, x0, 0f) },
                                Color.White, style = Stroke(size.width * 0.085f, cap = StrokeCap.Round))
                        }
                    }
                    Text(if (level > 0) "${level}단" else "끔", Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (level > 0) Color(0xB0DC6721) else Color(0xAA203344)).padding(horizontal = 5.dp, vertical = 2.dp),
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            HeatMarker(0.44f, 0.43f, "운전석", driver, "comfort_seat_driver", onDriver)
            HeatMarker(0.60f, 0.55f, "조수석", passenger, "comfort_seat_passenger", onPassenger)
            HeatMarker(0.73f, 0.32f, "뒷좌석", rear, "comfort_seat_rear", onRear)
        }
    }
}

@Composable
internal fun RecordOverviewRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = C.ink, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(value, Modifier.widthIn(max = if (isCompact()) 150.dp else 240.dp), color = C.title, fontSize = 24.sp,
            lineHeight = 31.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
internal fun HandoverMetricSummary(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = listOf(if (C.dark) Color(0xFFC4CCD7) else Color(0xFF8F9BAC), if (C.dark) Color(0xFF76D9AE) else Color(0xFF129F70), if (C.dark) Color(0xFFFFBE82) else Color(0xFFDB7627))
    val icons = listOf(Icons.Outlined.Schedule, Icons.Outlined.DirectionsCar, Icons.AutoMirrored.Outlined.TrendingDown)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 270.dp || LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEachIndexed { index, (label, value) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icons[index], null, Modifier.size(28.dp), tint = colors[index])
                        Text(label, Modifier.weight(1f), fontSize = 18.sp, color = C.ink, fontWeight = FontWeight.SemiBold)
                        Text(value, fontSize = 23.sp, color = colors[index], fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            items.forEachIndexed { index, (label, value) ->
                Column(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(icons[index], null, Modifier.size(32.dp), tint = colors[index])
                    Text(label, fontSize = 18.sp, lineHeight = 24.sp, color = C.ink, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(value, fontSize = 23.sp, lineHeight = 30.sp, color = if (index == 0) C.ink else colors[index], fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                if (index != items.lastIndex) Box(Modifier.padding(vertical = 8.dp).width(1.dp).fillMaxHeight().background(C.line))
            }
        }
    }
}

/** 차량과 레이더는 가용 공간에 비례하고, 상태 문구는 그림 밖에서 줄바꿈된다. */
@Composable
internal fun ValetStatusArtwork(u: UiState, label: String, level: Level?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    val accent = if (level == Level.OK) Color(0xFF57D7A3) else if (level == Level.DANGER) C.danger else C.title
    val studio = if (C.dark) listOf(Color(0xFF263446), Color(0xFF182331))
        else listOf(Color(0xFFF4F7FB), Color(0xFFE6EDF5))
    Column(
        modifier.shadow(9.dp, shape).clip(shape).background(Brush.verticalGradient(studio))
            .border(1.dp, C.line.copy(alpha = 0.6f), shape).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Pill(label, level, big = true)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val stageHeight = (maxWidth * 1.05f).coerceIn(230.dp, if (isCarScreen()) 440.dp else 340.dp)
            Box(Modifier.fillMaxWidth().height(stageHeight), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * 0.47f
                    drawCircle(
                        Brush.radialGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent), center, radius),
                        radius, center,
                    )
                    listOf(0.70f, 0.85f, 1f).forEachIndexed { index, factor ->
                        drawCircle(accent.copy(alpha = 0.18f - index * 0.035f), radius * factor, center,
                            style = Stroke(size.minDimension * 0.025f))
                        drawCircle(Color.White.copy(alpha = if (C.dark) 0.07f else 0.4f), radius * factor, center,
                            style = Stroke(size.minDimension * 0.002f))
                    }
                }
                TopCarPicture(u, Modifier.fillMaxHeight(0.94f).aspectRatio(0.48f, matchHeightConstraintsFirst = true))
            }
        }
        Text(
            if (u.simAllowed && !u.carConnected) "차량 연결 전 · 예시 값 기준" else "차량 신호로 주차 상태 확인",
            color = C.sub, fontSize = 16.sp,
        )
    }
}

/**
 * 방향 안내와 안전 확인은 다르다. 신호 미수신/대기/순정 경고에서는 초록 유도선을 표시하지 않는다.
 * 그림과 문구를 분리해 큰 글자에서도 글자가 차체나 유도선에 겹치지 않게 한다.
 */
@Composable
internal fun EscortLightArtwork(u: UiState, modifier: Modifier = Modifier) {
    val phase = u.engine.escort.phase
    val seaWarning = phase != EscortPhase.OFF && u.signals.seaWarning
    val unknown = phase != EscortPhase.OFF && listOf("gear", "speed", "doors").any { u.unread(it) }
    val guiding = !seaWarning && !unknown && (phase == EscortPhase.ALIGHT_GUIDE || phase == EscortPhase.WRONG_SIDE)
    val lights = guiding && u.engine.ambient?.right == Level.OK
    val muted = Color(0xFFADBCCD)
    val red = Color(0xFFFF747D)
    val green = Color(0xFF4CE39D)
    val blue = Color(0xFF8ECFFF)
    val tint = when { seaWarning -> Color(0xFFFFC078); guiding -> if (lights) green else blue; else -> muted }
    val status = when {
        seaWarning -> "순정 안전하차보조 경고"
        unknown -> "차량 신호 확인 중"
        guiding -> if (lights) "하차 조명 안내 중" else "화면으로 하차 방향 안내"
        phase == EscortPhase.OFF -> "동행 시작 전"
        phase == EscortPhase.ALIGHTED -> "동행을 마쳤어요"
        else -> "도착 후 하차 안내"
    }
    val detail = when {
        seaWarning -> "경고가 끝날 때까지 하차를 기다려 주세요."
        unknown -> "기어·속도·문 신호를 받은 뒤 안내해요."
        guiding -> "주변을 확인한 뒤 보도 쪽으로 내려 주세요."
        phase == EscortPhase.OFF -> "동행을 시작하면 도착 후 하차 방향을 안내해요."
        phase == EscortPhase.ALIGHTED -> "하차 시각과 확인 과정은 동행 기록에 남아 있어요."
        else -> "정차하고 P단을 확인하면 안내를 시작해요."
    }
    val leftLabel = if (guiding && phase == EscortPhase.WRONG_SIDE) "차도 쪽 · 문 열림" else "차도 쪽 · 주의"
    val rightLabel = when { seaWarning -> "하차 대기"; guiding -> "보도 쪽 · 방향 안내"; else -> "보도 쪽" }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.shadow(10.dp, shape).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF0B2037), Color(0xFF06283D), Color(0xFF112139))))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape).padding(18.dp)
            .testTag("escort_artwork"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (seaWarning) Icons.Outlined.ErrorOutline else Icons.Outlined.LightMode, null, Modifier.size(24.dp), tint = tint)
            Text(status, Modifier.weight(1f).testTag("escort_artwork_status"), color = Color.White,
                fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val height = (maxWidth * 1.3f).coerceIn(240.dp, 480.dp)
            Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription = when {
                        seaWarning -> "순정 경고 중: 하차 유도 조명 꺼짐"
                        unknown -> "신호 미수신: 하차 유도 대기"
                        lights -> "오른쪽 보도 방향 조명 안내, 왼쪽 차도 주의"
                        guiding -> "조명 제어 없이 화면으로 보도 방향 안내"
                        else -> "하차 방향 안내 대기"
                    }
                }) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)
                    drawOval(Color.White.copy(alpha = 0.035f), Offset(w * 0.18f, h * 0.04f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.92f))
                    // 차폭과 무관한 고정 dp 좌표 대신 그림의 비율로 유도선을 배치한다.
                    listOf(false, true).forEach { right ->
                        val color = if (!guiding) muted.copy(alpha = 0.22f) else if (right) tint else red
                        val side = if (right) 1f else -1f
                        repeat(3) { i ->
                            val startX = center.x + side * w * 0.16f
                            val outerX = center.x + side * w * (0.32f + i * 0.043f)
                            val startY = h * if (right) 0.57f else 0.48f
                            val endY = h * if (right) 0.08f else 0.92f
                            val path = Path().apply {
                                moveTo(startX, startY)
                                cubicTo(outerX, startY, outerX, startY, outerX, endY)
                            }
                            if (lights) {
                                drawPath(path, color.copy(alpha = 0.07f), style = Stroke(w * 0.065f, cap = StrokeCap.Round))
                                drawPath(path, color.copy(alpha = 0.20f), style = Stroke(w * 0.028f, cap = StrokeCap.Round))
                            }
                            drawPath(path, color, style = Stroke(w * 0.008f, cap = StrokeCap.Round))
                        }
                    }
                }
                AlightLightCar(Modifier.fillMaxHeight(0.94f).aspectRatio(0.48f, matchHeightConstraintsFirst = true), showLights = false)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(leftLabel, Modifier.weight(1f), color = if (guiding) red else muted, fontSize = 16.sp,
                lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(rightLabel, Modifier.weight(1f).testTag("escort_safe_label"), color = tint, fontSize = 16.sp,
                lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Text(detail, color = muted, fontSize = 15.sp, lineHeight = 22.sp)
    }
}
