package com.verified.incar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reference palette: navy actions, luminous blue accents and sculpted neutral surfaces. */
object Tone {
    val orange = Color(0xFFE8711A)
    val mint = Color(0xFF12B886)
    val mintSoft = Color(0xFFD9F7EC)
    val orangeSoft = Color(0xFFFFEBD9)
    val greenSoft = Color(0xFFE3F6EA)
    val blueSoft = Color(0xFFE3EDFF)
    val purpleSoft = Color(0xFFECE6FF)
    val navy get() = Brush.linearGradient(listOf(Color(0xFF00366D), Color(0xFF001E42)))
    val navyBlue get() = Brush.linearGradient(listOf(Color(0xFF1B4D8E), Color(0xFF2894AD)))
    val bluePurple get() = Brush.linearGradient(listOf(Color(0xFF3459A3), Color(0xFFAC77C4)))
    val blueMint get() = Brush.linearGradient(listOf(Color(0xFF179DE5), Color(0xFF22CC9A)))
    val hero get() = C.backdrop
    val studio get() = Brush.verticalGradient(if (C.dark) listOf(Color(0xFF253344), Color(0xFF182330))
        else listOf(Color(0xFFEDF2F7), Color(0xFFE4EBF3)))
}

/** Soft elevation and a fine highlight give the reference cards their physical depth. */
fun Modifier.lift(shape: Shape = RoundedCornerShape(16.dp), elevation: Dp = 8.dp) =
    referenceShadow(elevation)
        .border(1.dp, if (C.dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f), shape)

/** Drawn soft penumbra also renders in software previews and low-power vehicle compositors. */
fun Modifier.referenceShadow(elevation: Dp = 10.dp, radius: Dp = 20.dp) = drawBehind {
    val depth = elevation.toPx()
    if (depth > 0f) for (layer in 14 downTo 1) {
        val spread = depth * layer / 14f
        drawRoundRect(Color(0xFF142033).copy(alpha = if (C.dark) 0.013f else 0.008f),
            Offset(-spread * 0.55f, depth * 0.55f - spread * 0.45f),
            Size(size.width + spread * 1.1f, size.height + spread * 0.9f),
            CornerRadius(radius.toPx() + spread * 0.5f))
    }
}

/** 파스텔 원 안의 아이콘. */
@Composable
fun IconBubble(icon: ImageVector, size: Dp = 44.dp, tint: Color = C.title, bg: Color = C.ice, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = tint)
    }
}

/** 숫자 요약 타일: 작은 이름 + 큰 값. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = C.ink, icon: ImageVector? = null, iconBg: Color = C.ice, iconTint: Color = C.title) {
    Column(modifier.lift(RoundedCornerShape(18.dp), 3.dp).clip(RoundedCornerShape(18.dp)).background(C.surface).padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) IconBubble(icon, 34.dp, iconTint, iconBg)
        Text(label, fontSize = 14.sp, color = C.sub)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, softWrap = false)
    }
}

/** 확인 항목 타일(2x2 배치용): 상태 표시 + 이름 + 값. ok가 null이면 확인 불가. */
@Composable
fun CheckTile(ok: Boolean?, label: String, value: String?, modifier: Modifier = Modifier) {
    val color = when (ok) { true -> C.ok; false -> Tone.orange; null -> C.sub }
    Row(modifier.lift(RoundedCornerShape(18.dp), 2.dp).clip(RoundedCornerShape(18.dp)).background(C.card).border(1.dp, color.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
        .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            if (ok == null) Text("?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            else Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.PriorityHigh, null, Modifier.size(16.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            if (value != null) Text(value, fontSize = 14.sp, color = C.sub)
        }
    }
}

/** 계기판처럼 270도 호를 그리고 가운데에 내용을 둔다. */
@Composable
fun ArcGauge(fraction: Float, size: Dp, modifier: Modifier = Modifier, stroke: Dp = 12.dp, color: Brush = Tone.blueMint, track: Color = C.line, ticks: Int = 0,
             marker: Float? = null, content: @Composable () -> Unit) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        GaugeTrack(fraction, Modifier.fillMaxSize(), stroke, color, track, ticks, marker)
        content()
    }
}

/**
 * 위에서 본 좌석 하나(차 앞이 위쪽): 방석 + 양옆 볼스터 + 등받이 + 머리받침.
 * 열선이 켜지면 주황빛으로 채우고 둘레에 빛이 번진다.
 */
fun DrawScope.drawSeat(topLeft: Offset, size: Size, base: Color, heat: Int) {
    val w = size.width
    val h = size.height
    val hot = heat > 0
    if (hot) drawRoundRect(Brush.radialGradient(listOf(Color(0xAAFF8A3C), Color(0x00FF8A3C)), center = Offset(topLeft.x + w / 2, topLeft.y + h / 2), radius = maxOf(w, h) * 0.85f),
        Offset(topLeft.x - w * 0.3f, topLeft.y - h * 0.25f), Size(w * 1.6f, h * 1.5f), CornerRadius(w * 0.4f))
    val fill: Brush = if (hot) Brush.verticalGradient(listOf(Color(0xFFFFB067), Color(0xFFF2742B)), topLeft.y, topLeft.y + h)
    else Brush.verticalGradient(listOf(base, base.copy(alpha = 0.82f)), topLeft.y, topLeft.y + h)
    val edge = if (hot) Color(0xFFD9591A) else Color(0xFF8C96A5)
    val r = CornerRadius(w * 0.2f)
    // 볼스터(양옆)
    drawRoundRect(fill, Offset(topLeft.x, topLeft.y + h * 0.06f), Size(w * 0.16f, h * 0.56f), r)
    drawRoundRect(fill, Offset(topLeft.x + w * 0.84f, topLeft.y + h * 0.06f), Size(w * 0.16f, h * 0.56f), r)
    // 방석
    drawRoundRect(fill, Offset(topLeft.x + w * 0.1f, topLeft.y), Size(w * 0.8f, h * 0.66f), r)
    drawRoundRect(edge, Offset(topLeft.x + w * 0.1f, topLeft.y), Size(w * 0.8f, h * 0.66f), r, style = Stroke(w * 0.025f))
    // 등받이
    drawRoundRect(fill, Offset(topLeft.x + w * 0.06f, topLeft.y + h * 0.6f), Size(w * 0.88f, h * 0.27f), r)
    drawRoundRect(edge, Offset(topLeft.x + w * 0.06f, topLeft.y + h * 0.6f), Size(w * 0.88f, h * 0.27f), r, style = Stroke(w * 0.025f))
    // 머리받침
    drawRoundRect(fill, Offset(topLeft.x + w * 0.28f, topLeft.y + h * 0.86f), Size(w * 0.44f, h * 0.14f), CornerRadius(w * 0.12f))
    drawRoundRect(edge, Offset(topLeft.x + w * 0.28f, topLeft.y + h * 0.86f), Size(w * 0.44f, h * 0.14f), CornerRadius(w * 0.12f), style = Stroke(w * 0.02f))
}
