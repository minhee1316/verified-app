package com.verified.incar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** 인수·반납의 실제 기록값을 비교하며, 좁은 폭·큰 글자에서는 이름과 값의 행으로 바꾼다. */
@Composable
internal fun RecordMetricStrip(values: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val fontScale = LocalDensity.current.fontScale
        val minimumCell = if (values.any { it.second.length > 6 }) 90.dp else 82.dp
        // 값이 긴 반납 기록과 큰 글자에서는 축소·잘림 대신 이름과 값을 한 줄씩 배치한다.
        if (maxWidth < minimumCell * values.size * fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                values.forEachIndexed { index, (label, value) ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.line.copy(alpha = 0.7f)))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(label, Modifier.weight(0.9f), color = C.sub, fontSize = 16.sp)
                        Text(value, Modifier.weight(1.1f), color = C.ink, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                values.forEachIndexed { index, (label, value) ->
                    if (index > 0) Box(Modifier.width(1.dp).height(48.dp).background(C.line))
                    Column(Modifier.weight(1f).padding(horizontal = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, color = C.sub, fontSize = 16.sp, textAlign = TextAlign.Center)
                        Text(value, color = C.ink, fontSize = if (value.length > 6) 21.sp else 27.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** 막대와 평균선이 같은 좌표계를 쓰며, 미기록과 실제 0km를 구분하는 네이티브 차트. */
@Composable
internal fun DrivingDistanceChart(
    dates: List<LocalDate>,
    distances: Map<String, Double>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val valid = dates.mapNotNull { distances[it.toString()]?.takeIf { km -> km.isFinite() && km >= 0.0 } }
    val longest = valid.maxOrNull() ?: 0.0
    val average = valid.takeIf { it.isNotEmpty() }?.average()
    val selectedKm = distances[selected.toString()]?.takeIf { it.isFinite() && it >= 0.0 }
    val titleColor = C.title
    val lineColor = C.line
    val guideColor = C.sub
    val selectedColors = if (C.dark) listOf(Color(0xFF7FCBEE), Color(0xFF4888D2))
        else listOf(Color(0xFF39B5D8), Color(0xFF002C5F))
    val normalColors = if (C.dark) listOf(Color(0xFF4DCBF1), Color(0xFF3678D3))
        else listOf(Color(0xFF4CC9ED), Color(0xFF5A7AD5))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val plotWidth = maxWidth
            val gap = 6.dp
            val cellWidth = ((maxWidth - gap * (dates.size - 1).coerceAtLeast(0)) / dates.size.coerceAtLeast(1))
                .coerceAtLeast(if (density.fontScale <= 1.15f) 36.dp else 48.dp * density.fontScale)
            val scroll = rememberScrollState()
            val plotHeight = if (valid.isEmpty()) 72.dp else 256.dp
            val needsScroll = cellWidth * dates.size + gap * (dates.size - 1).coerceAtLeast(0) > maxWidth + 1.dp
            LaunchedEffect(selected, cellWidth, maxWidth, scroll.maxValue) {
                val index = dates.indexOf(selected).coerceAtLeast(0)
                val target = with(density) { ((cellWidth + gap) * index - (maxWidth - cellWidth) / 2).roundToPx() }
                scroll.scrollTo(target.coerceAtLeast(0))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth()) {
            Row(Modifier.horizontalScroll(scroll).selectableGroup().testTag("insight_chart")
                .drawBehind {
                    val bottom = plotHeight.toPx() - 10.dp.toPx()
                    val chartHeight = plotHeight.toPx() - 58.dp.toPx()
                    drawLine(lineColor, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
                    if (average != null && longest > 0.0 && average > 0.0) {
                        val y = bottom - chartHeight * (average / longest).toFloat()
                        drawLine(guideColor.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                    }
                }, horizontalArrangement = Arrangement.spacedBy(gap)) {
                dates.forEach { date ->
                    val km = distances[date.toString()]?.takeIf { it.isFinite() && it >= 0.0 }
                    val current = selected == date
                    val weekday = "월화수목금토일"[date.dayOfWeek.value - 1]
                    val description = "${date.monthValue}월 ${date.dayOfMonth}일 ${weekday}요일, " +
                        (km?.let { "%,.1f km".format(it) } ?: "기록 없음")
                    Column(Modifier.width(cellWidth).clip(RoundedCornerShape(10.dp))
                        .testTag("insight_day_$date")
                        .selectable(current, role = Role.RadioButton) { onSelect(date) }
                        .semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(plotHeight), contentAlignment = Alignment.BottomCenter) {
                            Canvas(Modifier.fillMaxSize()) {
                                val bottom = size.height - 10.dp.toPx()
                                val chartHeight = size.height - 58.dp.toPx()
                                val width = size.width * 0.66f
                                if (km != null && km > 0.0 && longest > 0.0) {
                                    val height = chartHeight * (km / longest).toFloat().coerceIn(0f, 1f)
                                    drawRoundRect(Brush.verticalGradient(if (current) selectedColors else normalColors),
                                        Offset((size.width - width) / 2, bottom - height), Size(width, height),
                                        CornerRadius(minOf(5.dp.toPx(), height / 2)))
                                } else if (km == 0.0) {
                                    // 0은 수신된 기록이다. 미기록처럼 막대 높이를 임의로 만들어 보여 주지 않는다.
                                    drawCircle(if (current) titleColor else guideColor, 3.dp.toPx(), Offset(size.width / 2, bottom))
                                }
                            }
                            if (km == null || km == 0.0) Text(if (km == null) "—" else "0", Modifier.padding(bottom = 20.dp),
                                color = guideColor, fontSize = 14.sp)
                        }
                        Text("${date.monthValue}/${date.dayOfMonth}", Modifier.fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 8.dp), fontSize = 14.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                            color = if (current) titleColor else guideColor, textAlign = TextAlign.Center)
                    }
                }
            }
            if (selectedKm != null) {
                val center = (cellWidth + gap) * dates.indexOf(selected).coerceAtLeast(0) + cellWidth / 2 - with(density) { scroll.value.toDp() }
                if (center >= 0.dp && center <= plotWidth) {
                    val width = (92.dp * density.fontScale).coerceAtMost(plotWidth)
                    val x = (center - width / 2).coerceIn(0.dp, (plotWidth - width).coerceAtLeast(0.dp))
                    val fraction = if (longest > 0.0) (selectedKm / longest).toFloat().coerceIn(0f, 1f) else 0f
                    val y = (plotHeight - 10.dp - (plotHeight - 58.dp) * fraction - 42.dp).coerceAtLeast(0.dp)
                    Column(Modifier.offset(x, y).width(width), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xFF003767))
                            .padding(horizontal = 5.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text("%,.1f km".format(selectedKm), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp)) {
                            Canvas(Modifier.offset(x = (center - x - 6.dp).coerceIn(0.dp, (width - 12.dp).coerceAtLeast(0.dp))).width(12.dp).height(6.dp)) {
                                drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, Color(0xFF003767))
                            }
                        }
                    }
                }
            }
            }
            Text(if (needsScroll) "좌우로 밀어 날짜를 선택하세요." else "날짜를 누르면 상세 기록을 볼 수 있어요.",
                fontSize = 14.sp, color = guideColor)
            }
        }
        if (average != null) Text((if (longest > 0.0) "점선 · " else "") + "기록 있는 날 평균 %,.1f km".format(average),
            fontSize = 14.sp, color = guideColor)
    }
}

@Composable
internal fun DrivingEventTile(label: String, value: String, warm: Boolean, modifier: Modifier = Modifier) {
    val colors = when {
        C.dark && warm -> listOf(Color(0xFF754B34), Color(0xFF463223))
        C.dark -> listOf(Color(0xFF28576D), Color(0xFF233A55))
        warm -> listOf(Color(0xFFFFE8D1), Color(0xFFFFCDA8))
        else -> listOf(Color(0xFFC1EDF5), Color(0xFF9DDFF0))
    }
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors))
        .padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = C.ink)
    }
}

/** 이미지 안의 입체 아이콘과 네이티브 이름·터치 영역을 분리한다. */
@Composable
internal fun DiscoveryIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    if (icon == Icons.Outlined.LocalParking) SculptedIconCell(5, modifier, 78.dp)
    else if (icon == Icons.Outlined.Insights) SculptedIconCell(8, modifier, 78.dp)
    else SculptedIcon(icon, modifier, 78.dp)
}

internal fun discoveryIcon(route: String): ImageVector = when (route) {
    "VEHICLE_VIEW" -> Icons.Outlined.PhotoLibrary
    "VALET" -> Icons.Outlined.LocalParking
    "HAND_LEND", "KEY" -> Icons.Outlined.Key
    "HAND_SERVICE", "SERVICE" -> Icons.Outlined.Build
    "HISTORY", "TRIP" -> Icons.Outlined.ReceiptLong
    "NOTICE" -> Icons.Outlined.Notifications
    "SETTINGS" -> Icons.Outlined.Settings
    "INSIGHT" -> Icons.Outlined.Insights
    "ESCORT" -> Icons.Outlined.Group
    "SCHOOL" -> Icons.Outlined.School
    "TRUNK" -> Icons.Outlined.Inventory2
    else -> Icons.Outlined.DirectionsCar
}
