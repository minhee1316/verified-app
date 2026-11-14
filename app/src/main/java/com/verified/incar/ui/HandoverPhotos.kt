package com.verified.incar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verified.incar.VerifiedRuntime
import com.verified.incar.core.HandKind
import com.verified.incar.core.VerdictEngine
import java.io.File

/**
 * 맡길 때·돌려받을 때 같은 각도 사진(앞·뒤·왼쪽·오른쪽·계기판). 앱 전용 저장소에만 두고 밖으로 보내지 않는다.
 * 맡기기 전에 찍은 사진은 pending_<종류>에 두었다가, 맡김이 시작되면 rec_<시작 시각>으로 옮겨 이력서와 이어 붙인다.
 */
object HandoverPhotos {
    val ANGLES = listOf("FRONT" to "앞", "REAR" to "뒤", "LEFT" to "왼쪽", "RIGHT" to "오른쪽", "DASH" to "계기판")
    const val BEFORE = "before"
    const val AFTER = "after"

    private fun root(context: Context) = File(context.filesDir, "handover_photos")
    fun pendingDir(context: Context, kind: HandKind) = File(root(context), "pending_${kind.name}")
    fun recordDir(context: Context, startMs: Long) = File(root(context), "rec_$startMs")
    fun file(dir: File, phase: String, angle: String) = File(dir, "${phase}_$angle.jpg")

    fun save(dir: File, phase: String, angle: String, bitmap: Bitmap): File {
        dir.mkdirs()
        val f = file(dir, phase, angle)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return f
    }

    /** 맡김이 시작되면 맡기기 전 사진을 그 맡김 기록으로 옮긴다. 옮긴 장 수를 돌려준다. */
    fun attach(context: Context, kind: HandKind, startMs: Long): Int {
        val from = pendingDir(context, kind)
        val files = from.listFiles()?.filter { it.isFile } ?: return 0
        if (files.isEmpty()) return 0
        val to = recordDir(context, startMs).apply { mkdirs() }
        files.forEach { f -> if (!f.renameTo(File(to, f.name))) { f.copyTo(File(to, f.name), overwrite = true); f.delete() } }
        from.delete()
        return files.size
    }

    fun count(dir: File, phase: String) = ANGLES.count { file(dir, phase, it.first).exists() }
}

/**
 * 정비 맡기기·빌려주기 카드 안의 사진 칸.
 * 맡기기 전: 맡길 때 사진 5칸 / 맡긴 중: 맡길 때 사진 + 돌려받을 때 사진 5칸 / 돌려받음: 같은 각도끼리 나란히 비교.
 */
@Composable
fun HandoverPhotoPanel(vm: VerifiedRuntime, kind: HandKind, startMs: Long?, active: Boolean, returned: Boolean) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(active, startMs) {
        if (active && startMs != null && HandoverPhotos.attach(context, kind, startMs) > 0) version++
    }
    val dir = if (startMs != null && (active || returned)) HandoverPhotos.recordDir(context, startMs) else HandoverPhotos.pendingDir(context, kind)
    var target by remember { mutableStateOf<Pair<String, String>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        val t = target
        if (bmp != null && t != null) { HandoverPhotos.save(dir, t.first, t.second, bmp); version++ }
    }
    fun shoot(phase: String, angle: String) {
        target = phase to angle
        try { camera.launch(null) } catch (_: ActivityNotFoundException) { vm.toast("이 기기에는 카메라가 없어요. 휴대폰 앱에서 찍어 주세요.") }
    }
    Card(Modifier.fillMaxWidth().testTag("handover_photos"), padding = 16.dp) {
        val v = version // 사진을 찍으면 다시 그린다
        when {
            returned -> {
                Text("같은 각도 비교", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.width(56.dp))
                    Text("맡길 때", Modifier.weight(1f), fontSize = 14.sp, color = C.sub, textAlign = TextAlign.Center)
                    Text("돌려받을 때", Modifier.weight(1f), fontSize = 14.sp, color = C.sub, textAlign = TextAlign.Center)
                }
                HandoverPhotos.ANGLES.forEach { (angle, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, Modifier.width(48.dp), fontSize = 15.sp, color = C.ink)
                        PhotoSlot(dir, HandoverPhotos.BEFORE, angle, label, v, Modifier.weight(1f), null)
                        PhotoSlot(dir, HandoverPhotos.AFTER, angle, label, v, Modifier.weight(1f), null)
                    }
                }
                if (HandoverPhotos.count(dir, HandoverPhotos.BEFORE) + HandoverPhotos.count(dir, HandoverPhotos.AFTER) == 0)
                    Text("이번 맡김에는 찍은 사진이 없어요.", fontSize = 15.sp, color = C.sub)
            }
            active -> {
                Text("돌려받을 때 사진", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("맡길 때와 같은 각도로 찍으면 돌려받은 뒤 나란히 비교해요.", fontSize = 15.sp, color = C.sub)
                PhotoRow(dir, HandoverPhotos.AFTER, v) { shoot(HandoverPhotos.AFTER, it) }
                Text("맡길 때 사진 ${HandoverPhotos.count(dir, HandoverPhotos.BEFORE)}장", fontSize = 15.sp, color = C.sub)
                PhotoRow(dir, HandoverPhotos.BEFORE, v, null)
            }
            else -> {
                Text("맡기기 전 사진", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("앞·뒤·양옆·계기판을 찍어 두면 돌려받을 때 같은 각도로 비교해요. 사진은 이 기기에만 저장돼요.", fontSize = 15.sp, color = C.sub)
                PhotoRow(dir, HandoverPhotos.BEFORE, v) { shoot(HandoverPhotos.BEFORE, it) }
            }
        }
    }
}

@Composable
private fun PhotoRow(dir: File, phase: String, version: Int, onShoot: ((String) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HandoverPhotos.ANGLES.forEach { (angle, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PhotoSlot(dir, phase, angle, label, version, Modifier.fillMaxWidth(), onShoot?.let { { it(angle) } })
                Text(label, fontSize = 13.sp, color = C.sub)
            }
        }
    }
}

/** 사진 한 칸. 없으면 카메라 아이콘(누르면 찍기), 있으면 사진과 찍은 시각. */
@Composable
private fun PhotoSlot(dir: File, phase: String, angle: String, label: String, version: Int, modifier: Modifier, onShoot: (() -> Unit)?) {
    val f = HandoverPhotos.file(dir, phase, angle)
    val bmp = remember(f.path, version) { if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.aspectRatio(1f).clip(shape).background(C.chip).border(1.dp, C.line, shape)
        .then(if (onShoot != null) Modifier.clickable(role = Role.Button, onClickLabel = "$label 사진 찍기", onClick = onShoot) else Modifier)
        .testTag("photo_${phase}_$angle"), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, "$label 사진", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Text(VerdictEngine.hhmm(f.lastModified()), Modifier.align(Alignment.BottomCenter).background(C.ink.copy(alpha = 0.55f)),
                color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp)
        } else Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(22.dp), tint = if (onShoot != null) C.title else C.line)
    }
}

/** 이력서 기록 한 건에 붙은 사진이 있으면 장 수를 보여 준다. */
fun handoverPhotoCount(context: Context, startMs: Long): Int {
    val d = HandoverPhotos.recordDir(context, startMs)
    return HandoverPhotos.count(d, HandoverPhotos.BEFORE) + HandoverPhotos.count(d, HandoverPhotos.AFTER)
}
