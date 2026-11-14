package com.verified.incar.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 기다리는 동안의 표시: 가운데 [content](아이콘·로고)는 그대로 두고, 바깥의 한쪽이 열린 얇은 링만 돈다.
 * 앱 시작에는 쓰지 않는다(시작은 시스템 시작 화면의 정적인 앱 아이콘만). 원격 제어 결과를 기다릴 때처럼
 * 실제로 처리를 기다리는 곳에서만 쓴다.
 */
@Composable
fun SpinnerRing(size: Dp, modifier: Modifier = Modifier, stroke: Dp = 2.5.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val turn by rememberInfiniteTransition(label = "ring").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ring_angle",
    )
    val ring = C.navy
    val track = C.line
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            drawCircle(track, radius = this.size.minDimension / 2 - w / 2, style = Stroke(w))
        }
        Canvas(Modifier.size(size).graphicsLayer { rotationZ = turn }) {
            val w = stroke.toPx()
            drawArc(ring, -90f, 270f, false, topLeft = Offset(w / 2, w / 2),
                size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}
