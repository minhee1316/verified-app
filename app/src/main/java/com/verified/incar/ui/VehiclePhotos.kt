package com.verified.incar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.R
import com.verified.incar.core.CarModel

/** Manufacturer photographs, bundled at a bounded resolution for offline use. */
internal data class VehiclePhoto(@param:DrawableRes val resource: Int, val caption: String, val source: String? = null)
internal fun vehiclePhotos(model: CarModel, inside: Boolean): List<VehiclePhoto> = when (model) {
    CarModel.GRANDEUR -> if (inside) listOf(
        VehiclePhoto(R.drawable.hy_grandeur_cabin, "운전석과 앞좌석")
    ) else listOf(
        VehiclePhoto(R.drawable.hy_grandeur_profile, "전체 외관",
            "https://www.hyundai.com/kr/ko/e/vehicles/the-new-grandeur/intro"),
        VehiclePhoto(R.drawable.hy_grandeur_front, "앞부분 디테일"),
        VehiclePhoto(R.drawable.hy_grandeur_rear, "뒷부분 디테일")
    )
    CarModel.AVANTE -> if (inside) listOf(
        VehiclePhoto(R.drawable.hy_avante_cabin, "운전석과 대시보드"),
        VehiclePhoto(R.drawable.hy_avante_seats, "앞좌석")
    ) else listOf(
        VehiclePhoto(R.drawable.hy_avante_front, "앞쪽 외관"),
        VehiclePhoto(R.drawable.hy_avante_rear, "뒤쪽 외관")
    )
    CarModel.TUCSON -> if (inside) listOf(
        VehiclePhoto(R.drawable.hy_tucson_cabin, "운전석과 앞좌석"),
        VehiclePhoto(R.drawable.hy_tucson_seats, "운전석과 대시보드")
    ) else listOf(
        VehiclePhoto(R.drawable.hy_tucson_front, "측면 외관"),
        VehiclePhoto(R.drawable.hy_tucson_rear, "뒤쪽 외관"),
        VehiclePhoto(R.drawable.hy_tucson_side, "앞쪽 외관 · 일반형과 XRT")
    )
    CarModel.IONIQ3 -> if (inside) listOf(
        VehiclePhoto(R.drawable.hy_ioniq3_cabin, "운전석과 앞좌석 · N Line"),
        VehiclePhoto(R.drawable.hy_ioniq3_seats, "대시보드 디테일")
    ) else listOf(
        VehiclePhoto(R.drawable.hy_ioniq3_rear, "앞쪽 외관 · N Line"),
        VehiclePhoto(R.drawable.hy_ioniq3_front, "외관 · 두 가지 색상")
    )
    CarModel.OTHER -> emptyList()
}

private fun photoSource(model: CarModel): String = when (model) {
    CarModel.GRANDEUR -> "https://www.hyundaimotorgroup.com/ko/news/hyundai-new-grandeur-design-reveal"
    CarModel.AVANTE -> "https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-all-new-avante"
    CarModel.TUCSON -> "https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-company-the-all-new-tucson-design-reveal"
    CarModel.IONIQ3 -> "https://www.hyundai.news/eu/models/electrified/ioniq-3/images.html"
    CarModel.OTHER -> ""
}

@Composable
internal fun CarPhoto(model: CarModel, modifier: Modifier = Modifier) {
    val photo = vehiclePhotos(model, false).firstOrNull()
    if (photo != null) Image(painterResource(photo.resource), "${model.label} 공식 외관 사진",
        modifier.clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
    else Icon(Icons.Outlined.DirectionsCar, "등록 차량", modifier, tint = C.sub)
}

@Composable
internal fun VehiclePhotos(model: CarModel, inside: Boolean) {
    val photos = vehiclePhotos(model, inside)
    var index by rememberSaveable(model, inside) { mutableIntStateOf(0) }
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val dragThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    if (photos.isEmpty()) {
        Text("이 차종의 공식 사진은 아직 준비되지 않았어요.", color = C.sub, fontSize = 17.sp)
        return
    }
    val photo = photos[index.coerceIn(photos.indices)]
    Column(Modifier.fillMaxWidth().testTag("vehicle_scene"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactButtons = maxWidth < 350.dp || fontScale > 1.2f
            Box(Modifier.fillMaxWidth().aspectRatio(if (isCarScreen()) 1.8f else 1.30f)
                .lift(RoundedCornerShape(18.dp), 5.dp).clip(RoundedCornerShape(18.dp))
                .background(if (C.dark) Color(0xFF41444E) else Color(0xFFE0E1E4))
                .pointerInput(model, inside, dragThreshold) {
                    var drag = 0f
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                        if (drag < -dragThreshold && index < photos.lastIndex) index++
                        if (drag > dragThreshold && index > 0) index--
                    }, onHorizontalDrag = { change, amount -> change.consume(); drag += amount })
                }, contentAlignment = Alignment.Center) {
                val description = "${model.label} 공식 사진 · ${photo.caption}"
                val pictureModifier = Modifier.fillMaxSize().padding(bottom = 18.dp).testTag("vehicle_photo")
                if (photo.resource == R.drawable.hy_grandeur_profile) GrandeurGalleryPhoto(description, pictureModifier)
                else Image(painterResource(photo.resource), description, pictureModifier, contentScale = ContentScale.Fit)
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)))))
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (photos.size > 1) PhotoStepButton("이전 사진", "vehicle_photo_previous", compactButtons, previous = true, enabled = index > 0) { index-- }
                    Text("${photo.caption} · ${index + 1}/${photos.size}", Modifier.weight(1f)
                        .testTag("vehicle_photo_caption").clip(RoundedCornerShape(20.dp)).background(Color(0xB5444851))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (photos.size > 1) PhotoStepButton("다음 사진", "vehicle_photo_next", compactButtons, previous = false, enabled = index < photos.lastIndex) { index++ }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("공식 사진 · 연식·색상·트림은 다를 수 있어요", Modifier.weight(1f), color = C.sub, fontSize = 12.sp)
            androidx.compose.material3.TextButton({
                runCatching { uri.openUri(photo.source ?: photoSource(model)) }.onFailure {
                    android.widget.Toast.makeText(context, "출처를 열 웹 브라우저가 필요해요.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }, Modifier.testTag("vehicle_photo_source")) {
                Text("출처", fontSize = 12.sp, color = C.sub)
                Icon(Icons.Outlined.OpenInNew, "사진 출처 보기", Modifier.size(14.dp).padding(start = 3.dp), tint = C.sub)
            }
        }
    }
}

/** 원본 공식 PNG의 투명 상단·좌우 여백을 제외해 차체 자체를 카드 중앙에 맞춘다. */
@Composable
private fun GrandeurGalleryPhoto(description: String, modifier: Modifier) {
    val bitmap = androidx.compose.ui.graphics.ImageBitmap.imageResource(R.drawable.hy_grandeur_profile)
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = description }) {
        // bundled 400 × 214 PNG의 실제 알파 영역(원본 파일은 보존).
        val source = androidx.compose.ui.unit.IntSize(295, 131)
        val scale = minOf(size.width * 0.94f / source.width, size.height * 0.78f / source.height)
        val width = (source.width * scale).toInt()
        val height = (source.height * scale).toInt()
        drawImage(bitmap, srcOffset = androidx.compose.ui.unit.IntOffset(56, 76), srcSize = source,
            dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - width) / 2f).toInt(), (size.height * 0.46f - height / 2f).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(width, height), filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    }
}

@Composable
private fun PhotoStepButton(label: String, tag: String, compact: Boolean, previous: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).testTag(tag)
        .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = if (enabled) 0.94f else 0.58f))
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .semantics { contentDescription = label }
        .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        if (compact) Icon(if (previous) Icons.AutoMirrored.Outlined.KeyboardArrowLeft else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            null, Modifier.size(22.dp), tint = Color(0xFF263746).copy(alpha = if (enabled) 1f else 0.45f))
        else Text(label, color = Color(0xFF263746).copy(alpha = if (enabled) 1f else 0.45f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
