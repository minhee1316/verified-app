package com.verified.incar.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    if (photos.isEmpty()) {
        Text("이 차종의 공식 사진은 아직 준비되지 않았어요.", color = C.sub, fontSize = 17.sp)
        return
    }
    val photo = photos[index.coerceIn(photos.indices)]
    Column(Modifier.fillMaxWidth().testTag("vehicle_scene"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
            .background(C.bg).pointerInput(model, inside) {
                var drag = 0f
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                    if (drag < -60f && index < photos.lastIndex) index++
                    if (drag > 60f && index > 0) index--
                }, onHorizontalDrag = { change, amount -> change.consume(); drag += amount })
            }, contentAlignment = Alignment.Center) {
            Image(painterResource(photo.resource), "${model.label} 공식 사진 · ${photo.caption}",
                Modifier.fillMaxWidth().testTag("vehicle_photo"), contentScale = ContentScale.Fit)
        }
        Text("${photo.caption} · ${index + 1}/${photos.size}", Modifier.testTag("vehicle_photo_caption"),
            color = C.ink, fontSize = 16.sp)
        if (photos.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("이전 사진", null, Modifier.weight(1f).testTag("vehicle_photo_previous"), enabled = index > 0) { index-- }
            Chip("다음 사진", null, Modifier.weight(1f).testTag("vehicle_photo_next"), enabled = index < photos.lastIndex) { index++ }
        }
        Text("현대자동차 공식 사진 · 연식·색상·트림은 실제 차량과 다를 수 있어요.",
            color = C.sub, fontSize = 14.sp)
        TextRow("사진 출처 보기") {
            runCatching { uri.openUri(photo.source ?: photoSource(model)) }.onFailure {
                android.widget.Toast.makeText(context, "출처를 열 웹 브라우저가 필요해요.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
