package com.verified.incar.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 휴대폰 실내 참고안: 밝은 바탕에 얇은 차량·시트 윤곽, 켜진 좌석만 따뜻하게 강조한다. */
internal fun DrawScope.drawPhoneCabin(dark: Boolean, heats: List<Int>) {
    val w = size.width
    val h = size.height
    val edge = if (dark) Color(0xFF7F8B9C) else Color(0xFFA6ABB4)
    val light = if (dark) Color(0xFF202A3B) else Color(0xFFF0F1F4)
    val detail = if (dark) Color(0xFF69778B) else Color(0xFFBDC1C9)
    val line = Stroke(width = 1.35f.dp.toPx())
    fun pt(x: Float, y: Float) = Offset(w * x, h * y)
    fun rect(x: Float, y: Float, width: Float, height: Float, radius: Float, fill: Color = light, outline: Color = edge) {
        drawRoundRect(fill, pt(x, y), Size(w * width, h * height), CornerRadius(w * radius))
        drawRoundRect(outline, pt(x, y), Size(w * width, h * height), CornerRadius(w * radius), style = line)
    }
    val outline = Path().apply {
        moveTo(w * 0.50f, h * 0.018f)
        cubicTo(w * 0.29f, h * 0.018f, w * 0.12f, h * 0.034f, w * 0.085f, h * 0.105f)
        cubicTo(w * 0.015f, h * 0.24f, w * 0.032f, h * 0.76f, w * 0.08f, h * 0.925f)
        cubicTo(w * 0.105f, h * 0.97f, w * 0.29f, h * 0.982f, w * 0.50f, h * 0.982f)
        cubicTo(w * 0.71f, h * 0.982f, w * 0.895f, h * 0.97f, w * 0.92f, h * 0.925f)
        cubicTo(w * 0.968f, h * 0.76f, w * 0.985f, h * 0.24f, w * 0.915f, h * 0.105f)
        cubicTo(w * 0.88f, h * 0.034f, w * 0.71f, h * 0.018f, w * 0.50f, h * 0.018f)
        close()
    }
    drawPath(outline, light.copy(alpha = 0.25f))
    drawPath(outline, edge, style = Stroke(1.6f.dp.toPx()))
    // 앞 유리와 대시보드. 실제 표시 글자는 Canvas 밖의 Compose Text로 유지한다.
    val dash = Path().apply {
        moveTo(w * 0.045f, h * 0.19f)
        lineTo(w * 0.15f, h * 0.19f)
        cubicTo(w * 0.22f, h * 0.16f, w * 0.32f, h * 0.16f, w * 0.39f, h * 0.19f)
        lineTo(w * 0.955f, h * 0.19f)
    }
    drawPath(dash, edge, style = line)
    val dashboard = Path().apply {
        moveTo(w * 0.05f, h * 0.225f)
        lineTo(w * 0.13f, h * 0.225f)
        cubicTo(w * 0.21f, h * 0.185f, w * 0.34f, h * 0.19f, w * 0.41f, h * 0.23f)
        lineTo(w * 0.44f, h * 0.32f)
        lineTo(w * 0.56f, h * 0.32f)
        lineTo(w * 0.59f, h * 0.23f)
        lineTo(w * 0.94f, h * 0.23f)
    }
    drawPath(dashboard, detail, style = line)
    drawCircle(edge, w * 0.106f, pt(0.25f, 0.255f), style = Stroke(1.5f.dp.toPx()))
    drawCircle(detail, w * 0.077f, pt(0.25f, 0.255f), style = line)
    drawLine(edge, pt(0.178f, 0.25f), pt(0.25f, 0.263f), 1.5f.dp.toPx(), StrokeCap.Round)
    drawLine(edge, pt(0.322f, 0.25f), pt(0.25f, 0.263f), 1.5f.dp.toPx(), StrokeCap.Round)
    drawLine(edge, pt(0.25f, 0.263f), pt(0.25f, 0.295f), 1.5f.dp.toPx(), StrokeCap.Round)
    rect(0.435f, 0.20f, 0.13f, 0.046f, 0.008f, light, detail)
    rect(0.455f, 0.35f, 0.09f, 0.19f, 0.028f, light, detail)
    // 앞 2석과 뒤 3석 모두 독립된 조작 영역을 갖는다.
    drawOutlineSeat(pt(0.075f, 0.315f), Size(w * 0.35f, h * 0.25f), light, edge, heats.getOrElse(0) { 0 })
    drawOutlineSeat(pt(0.575f, 0.315f), Size(w * 0.35f, h * 0.25f), light, edge, heats.getOrElse(1) { 0 })
    listOf(0.075f, 0.385f, 0.695f).forEachIndexed { i, x ->
        drawOutlineSeat(pt(x, 0.64f), Size(w * 0.23f, h * 0.235f), light, edge, heats.getOrElse(i + 2) { 0 })
    }
}

private fun DrawScope.drawOutlineSeat(top: Offset, size: Size, neutral: Color, edge: Color, heat: Int) {
    val w = size.width
    val h = size.height
    val active = heat > 0
    val color = if (active) Color(0xFFD9772C) else edge
    val line = Stroke(if (active) 1.7f.dp.toPx() else 1.15f.dp.toPx())
    val fill = if (active) Brush.verticalGradient(listOf(Color(0xFFFFE7C7), Color(0xFFFFBD79)), top.y, top.y + h)
        else Brush.verticalGradient(listOf(neutral.copy(alpha = 0.20f), neutral.copy(alpha = 0.5f)), top.y, top.y + h)
    if (active) drawRoundRect(Brush.radialGradient(listOf(Color(0x33F59B48), Color.Transparent), Offset(top.x + w / 2, top.y + h / 2), h * 0.7f),
        Offset(top.x - w * 0.1f, top.y - h * 0.05f), Size(w * 1.2f, h * 1.1f), CornerRadius(w * 0.30f))
    val back = Path().apply {
        moveTo(top.x + w * 0.18f, top.y)
        cubicTo(top.x + w * 0.06f, top.y, top.x + w * 0.04f, top.y + h * 0.1f, top.x + w * 0.04f, top.y + h * 0.26f)
        lineTo(top.x + w * 0.17f, top.y + h * 0.66f)
        quadraticTo(top.x + w * 0.5f, top.y + h * 0.73f, top.x + w * 0.83f, top.y + h * 0.66f)
        lineTo(top.x + w * 0.96f, top.y + h * 0.26f)
        cubicTo(top.x + w * 0.96f, top.y + h * 0.1f, top.x + w * 0.94f, top.y, top.x + w * 0.82f, top.y)
        close()
    }
    drawPath(back, fill)
    drawPath(back, color, style = line)
    val seams = if (active) color.copy(alpha = 0.4f) else edge.copy(alpha = 0.6f)
    drawLine(seams, Offset(top.x + w * 0.20f, top.y + h * 0.035f), Offset(top.x + w * 0.28f, top.y + h * 0.60f), 0.8f.dp.toPx())
    drawLine(seams, Offset(top.x + w * 0.80f, top.y + h * 0.035f), Offset(top.x + w * 0.72f, top.y + h * 0.60f), 0.8f.dp.toPx())
    drawLine(seams, Offset(top.x + w * 0.14f, top.y + h * 0.23f), Offset(top.x + w * 0.86f, top.y + h * 0.23f), 0.8f.dp.toPx())
    listOf(0.01f, 0.77f).forEach { x ->
        drawRoundRect(fill, Offset(top.x + w * x, top.y + h * 0.57f), Size(w * 0.22f, h * 0.27f), CornerRadius(w * 0.12f))
        drawRoundRect(color, Offset(top.x + w * x, top.y + h * 0.57f), Size(w * 0.22f, h * 0.27f), CornerRadius(w * 0.12f), style = line)
    }
    drawRoundRect(fill, Offset(top.x + w * 0.16f, top.y + h * 0.70f), Size(w * 0.68f, h * 0.24f), CornerRadius(w * 0.13f))
    drawRoundRect(color, Offset(top.x + w * 0.16f, top.y + h * 0.70f), Size(w * 0.68f, h * 0.24f), CornerRadius(w * 0.13f), style = line)
    drawRoundRect(fill, Offset(top.x + w * 0.28f, top.y + h * 0.80f), Size(w * 0.44f, h * 0.18f), CornerRadius(w * 0.1f))
    drawRoundRect(color, Offset(top.x + w * 0.28f, top.y + h * 0.80f), Size(w * 0.44f, h * 0.18f), CornerRadius(w * 0.1f), style = line)
}

/** 실사 이미지의 좌석 위에 상태에 따라 색만 얹는다. 글자·버튼은 별도의 UI다. */
internal fun DrawScope.drawPhotoSeatHeat(heats: List<Int>) {
    val centers = listOf(Offset(0.33f, 0.48f), Offset(0.68f, 0.48f), Offset(0.30f, 0.70f), Offset(0.50f, 0.70f), Offset(0.70f, 0.70f))
    centers.forEachIndexed { index, position ->
        val heat = heats.getOrElse(index) { 0 }
        if (heat > 0) {
            val center = Offset(size.width * position.x, size.height * position.y)
            val radius = size.width * if (index < 2) 0.16f else 0.13f
            drawCircle(Brush.radialGradient(listOf(Color(0xDDFF7B20), Color(0x66FF9F42), Color.Transparent), center, radius), radius, center)
        }
    }
}

