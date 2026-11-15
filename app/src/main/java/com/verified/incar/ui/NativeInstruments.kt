package com.verified.incar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** 장식과 수치의 위치를 분리한다. 원·눈금·마커 모두 실제 Canvas 중심을 기준으로 그린다. */
@Composable
internal fun GaugeTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    color: Brush = Tone.blueMint,
    track: Color = C.line,
    ticks: Int = 0,
    marker: Float? = null,
) {
    val markerColor = if (C.dark) Color(0xFFFFC27E) else Color(0xFFB86012)
    val tickColor = C.sub.copy(alpha = 0.5f)
    Canvas(modifier) {
        val diameter = size.minDimension
        if (diameter <= 0f) return@Canvas
        val maxStroke = diameter * 0.12f
        // A temporarily tiny layout must not create a reversed coerceIn range.
        val sw = stroke.toPx().coerceIn(minOf(1f, maxStroke), maxStroke)
        val padding = maxOf(sw * 0.9f, diameter * 0.04f)
        val radius = diameter / 2f - padding
        val center = Offset(size.width / 2f, size.height / 2f)
        val origin = center - Offset(radius, radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val start = 135f
        val sweep = 270f
        drawArc(track, start, sweep, false, origin, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        val progress = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        if (progress > 0f) drawArc(color, start, sweep * progress, false, origin, arcSize,
            style = Stroke(sw, cap = StrokeCap.Round))
        fun point(angle: Float, r: Float): Offset {
            val rad = Math.toRadians(angle.toDouble())
            return center + Offset(r * cos(rad).toFloat(), r * sin(rad).toFloat())
        }
        if (ticks > 0) for (i in 0..ticks) {
            val angle = start + sweep * i / ticks
            val outer = radius - sw * 0.8f
            drawLine(tickColor, point(angle, outer - diameter * 0.025f), point(angle, outer),
                strokeWidth = maxOf(1f, diameter * 0.005f), cap = StrokeCap.Round)
        }
        marker?.takeIf { it.isFinite() }?.let {
            val angle = start + sweep * it.coerceIn(0f, 1f)
            drawLine(markerColor, point(angle, radius - sw * 0.9f), point(angle, radius + sw * 0.65f),
                strokeWidth = diameter * 0.012f, cap = StrokeCap.Round)
        }
    }
}

/** 화면 폭·글자 배율을 따라 커지는 금속 키 카드. 글자를 이미지에 굽거나 높이로 자르지 않는다. */
@Composable
internal fun DigitalKeyArtwork(modelName: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val ink = Color(0xFF243C57)
    val sub = Color(0xFF526B83)
    val metal = listOf(Color(0xFFF2F7FA), Color(0xFFBCCBD9), Color(0xFF8C9FB4), Color(0xFFDCE3E9))
    val stage = if (C.dark) listOf(Color(0xFF364B65), Color(0xFF17283D), Color(0xFF405874))
        else listOf(Color(0xFFC5DDE6), Color(0xFFABC3D4), Color(0xFF91A6C8))
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 230.dp).testTag("key_art").clip(RoundedCornerShape(22.dp))
        .background(Brush.linearGradient(stage)).padding(vertical = 34.dp), contentAlignment = Alignment.Center) {
        val cardWidth = (maxWidth * 0.65f).coerceAtMost(320.dp)
        val cardHeight = cardWidth / 1.6f
        Canvas(Modifier.matchParentSize()) {
            if (size.minDimension <= 0f) return@Canvas
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.15f), radius = size.width * 0.7f),
                radius = size.width * 0.7f, center = Offset(size.width * 0.22f, size.height * 0.15f))
        }
        Canvas(Modifier.width(cardWidth * 1.12f).height(cardWidth * 0.35f).offset(y = cardHeight * 0.42f)) {
            if (size.minDimension <= 0f) return@Canvas
            drawOval(Brush.radialGradient(listOf(Color(0x55001B39), Color.Transparent),
                center = center, radius = size.width / 2), topLeft = Offset.Zero, size = size)
        }
        Box(Modifier.width(cardWidth)
            .shadow(20.dp, shape, clip = false, spotColor = Color(0xA0001C38))
            .clip(shape).background(Brush.linearGradient(metal))
            .border(1.5.dp, Color.White.copy(alpha = 0.8f), shape)) {
            Canvas(Modifier.matchParentSize()) {
                // 반사광만 장식으로 그린다. 글자와 의미 있는 아이콘은 Compose로 표시한다.
                repeat(4) { i ->
                    val x = size.width * (0.48f + i * 0.09f)
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(x, 0f),
                        Offset(x + size.width * 0.34f, size.height), strokeWidth = size.width * 0.09f)
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(min = cardHeight).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Key, null, Modifier.size(26.dp), tint = sub)
                    Icon(Icons.Outlined.Nfc, null, Modifier.size(20.dp), tint = sub)
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("DIGITAL KEY", color = sub, fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text(modelName, Modifier.fillMaxWidth().testTag("key_car_name"), color = ink,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
    }
    }
}
