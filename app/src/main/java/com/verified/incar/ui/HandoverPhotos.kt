package com.verified.incar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.verified.incar.VerifiedRuntime
import com.verified.incar.RecordFiles
import com.verified.incar.core.HandKind
import com.verified.incar.core.VerdictEngine
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 맡길 때·돌려받을 때 같은 각도 사진(앞·뒤·왼쪽·오른쪽·계기판). 앱 전용 저장소에만 두고 밖으로 보내지 않는다.
 * 맡기기 전에 찍은 사진은 pending_<종류>에 두었다가, 맡김이 시작되면 rec_<시작 시각>으로 옮겨 이력서와 이어 붙인다.
 * 사진은 카메라 앱이 찍은 원본 해상도로 저장한다(흠집을 확대해 비교할 수 있게). 목록에는 줄인 그림만 읽는다.
 */
object HandoverPhotos {
    val ANGLES = listOf("FRONT" to "앞", "REAR" to "뒤", "LEFT" to "왼쪽", "RIGHT" to "오른쪽", "DASH" to "계기판")
    const val BEFORE = "before"
    const val AFTER = "after"

    fun pendingDir(context: Context, kind: HandKind, demo: Boolean = false) = File(RecordFiles.photoRoot(context, demo), "pending_${kind.name}")
    fun recordDir(context: Context, startMs: Long, demo: Boolean = false) = File(RecordFiles.photoRoot(context, demo), "rec_$startMs")
    fun file(dir: File, phase: String, angle: String) = File(dir, "${phase}_$angle.jpg")

    fun save(dir: File, phase: String, angle: String, bitmap: Bitmap): File {
        val f = file(dir, phase, angle)
        return RecordFiles.writeAtomically(f) { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) throw IOException("사진을 인코딩하지 못했어요")
        }
    }

    internal fun replace(shot: File, target: File): File? {
        if (!shot.isFile || shot.length() == 0L) return null
        if (shot.canonicalFile == target.canonicalFile) return target
        val takenAt = shot.lastModified()
        val saved = RecordFiles.writeAtomically(target) { out -> shot.inputStream().use { it.copyTo(out) } }
        saved.setLastModified(takenAt)
        shot.delete()
        return saved
    }

    /** 카메라가 열린 동안 시연/실차·맡김 대상이 바뀌면 새 대상에 사진을 넣지 않는다. */
    internal fun keepCaptured(shot: File, requestedPath: String?, currentTarget: File): File? {
        if (requestedPath == null || File(requestedPath).canonicalFile != currentTarget.canonicalFile) return null
        return replace(shot, currentTarget)
    }

    /** 카메라 앱이 쓴 원본 파일을 그 칸으로 옮긴다(다시 찍으면 덮어쓴다). */
    fun keep(shot: File, dir: File, phase: String, angle: String): File? {
        return replace(shot, file(dir, phase, angle))
    }

    /** 맡김이 시작되면 맡기기 전 사진을 그 맡김 기록으로 옮긴다. 옮긴 장 수를 돌려준다. */
    fun attach(context: Context, kind: HandKind, startMs: Long, demo: Boolean = false): Int {
        val from = pendingDir(context, kind, demo)
        val files = ANGLES.map { file(from, BEFORE, it.first) }.filter { it.isFile }
        if (files.isEmpty()) return 0
        val to = recordDir(context, startMs, demo)
        files.forEach { f -> if (replace(f, File(to, f.name)) == null) throw IOException("맡길 때 사진을 옮기지 못했어요") }
        from.delete()
        return files.size
    }

    fun count(dir: File, phase: String) = ANGLES.count { file(dir, phase, it.first).exists() }

    /** 원본을 다 읽지 않고 [maxPx] 근처 크기로 줄여 읽는다(원본 여러 장을 한 화면에 그려도 메모리가 넘치지 않게). */
    fun decode(f: File, maxPx: Int): Bitmap? {
        if (!f.isFile || maxPx < 1) return null
        var bitmap: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            if (bounds.outWidth < 1 || bounds.outHeight < 1) return null
            var sample = 1
            while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
            val decoded = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            bitmap = decoded
            val orientation = runCatching { ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } catch (_: Exception) { bitmap?.recycle(); null }
        catch (_: OutOfMemoryError) { bitmap?.recycle(); null }
    }
}

/**
 * 원본 해상도 촬영: 카메라 앱에 캐시의 임시 파일을 건네고, 찍으면 [onShot]으로 그 파일을 넘긴다.
 * 카메라가 없는 기기(차 디스플레이)에서는 안내만 한다.
 */
@Composable
fun rememberOriginalCamera(vm: VerifiedRuntime, onShot: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    val shotName = rememberSaveable { "camera/shot_${UUID.randomUUID()}.jpg" }
    val shot = remember(context, shotName) { File(context.cacheDir, shotName) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) runCatching {
            if (!shot.isFile || shot.length() == 0L) throw IOException("촬영한 사진이 없어요")
            onShot(shot)
        }.onFailure { shot.delete(); vm.toast("사진을 저장하지 못했어요. 기존 사진은 유지돼요. 저장 공간을 확인하고 다시 찍어 주세요.") }
        else shot.delete()
    }
    return {
        runCatching {
            shot.parentFile?.mkdirs()
            shot.delete()
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", shot)
            camera.launch(uri)
        }.onFailure {
            vm.toast(if (it is ActivityNotFoundException) "이 기기에는 카메라가 없어요. 휴대폰 앱에서 찍어 주세요." else "카메라를 열지 못했어요.")
        }
    }
}

/**
 * 정비 맡기기·빌려주기 카드 안의 사진 칸.
 * 맡기기 전: 맡길 때 사진 5칸 / 맡긴 중: 맡길 때 사진 + 돌려받을 때 사진 5칸 / 돌려받음: 같은 각도끼리 나란히 비교.
 * 사진을 누르면 크게 열어 확대하고, 맡길 때 ↔ 돌려받을 때를 같은 배율로 바꿔 본다.
 */
@Composable
fun HandoverPhotoPanel(vm: VerifiedRuntime, kind: HandKind, startMs: Long?, active: Boolean, returned: Boolean, demo: Boolean = false) {
    val context = LocalContext.current
    var version by remember(demo) { mutableIntStateOf(0) }
    LaunchedEffect(demo, active, startMs) {
        if (active && startMs != null) {
            runCatching { HandoverPhotos.attach(context, kind, startMs, demo) }
                .onFailure { vm.toast("맡길 때 사진 일부를 옮기지 못했어요. 원본은 이 기기에 남아 있어요.") }
            version++
        }
    }
    val dir = if (startMs != null && (active || returned)) HandoverPhotos.recordDir(context, startMs, demo) else HandoverPhotos.pendingDir(context, kind, demo)
    // 카메라가 열린 동안 실차 연결·회전이 일어나도 촬영을 시작한 저장소에만 저장한다.
    var targetPath by rememberSaveable { mutableStateOf<String?>(null) }
    var targetPhase by rememberSaveable { mutableStateOf<String?>(null) }
    var targetAngle by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberOriginalCamera(vm) { shot ->
        val path = targetPath
        val phase = targetPhase
        val angle = targetAngle
        try {
            if (path == null || phase == null || angle == null || returned || (active && phase != HandoverPhotos.AFTER) || (!active && phase != HandoverPhotos.BEFORE)) {
                shot.delete()
                vm.toast("촬영 중 맡김 상태가 바뀌었어요. 현재 기록을 확인하고 다시 찍어 주세요.")
            } else if (HandoverPhotos.keepCaptured(shot, HandoverPhotos.file(File(path), phase, angle).absolutePath, HandoverPhotos.file(dir, phase, angle)) != null) version++
            else { shot.delete(); vm.toast("촬영 중 차량이나 맡김 대상이 바뀌었어요. 현재 기록을 확인하고 다시 찍어 주세요.") }
        } finally { targetPath = null; targetPhase = null; targetAngle = null }
    }
    fun shoot(phase: String, angle: String) {
        targetPath = dir.absolutePath
        targetPhase = phase
        targetAngle = angle
        camera()
    }
    // 증거를 지키기 위해: 맡기기 전에는 맡길 때 사진을, 맡긴 중에는 돌려받을 때 사진만 다시 찍을 수 있다. 돌려받은 뒤에는 고치지 않는다
    fun retakable(phase: String) = !returned && (if (active) phase == HandoverPhotos.AFTER else phase == HandoverPhotos.BEFORE)
    var viewing by rememberSaveable { mutableStateOf<String?>(null) } // "phase:angle"
    viewing?.split(":")?.let { (phase, angle) ->
        PhotoViewer(dir, phase, angle, version, ::retakable, onRetake = { p -> viewing = null; shoot(p, angle) }) { viewing = null }
    }
    val open: (String, String) -> Unit = { phase, angle -> viewing = "$phase:$angle" }
    Card(Modifier.fillMaxWidth().testTag("handover_photos"), padding = 16.dp) {
        val v = version // 사진을 찍으면 다시 그린다
        when {
            returned && HandoverPhotos.count(dir, HandoverPhotos.BEFORE) + HandoverPhotos.count(dir, HandoverPhotos.AFTER) == 0 -> {
                Text("같은 각도 비교", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("이번 맡김에는 찍은 사진이 없어요.", fontSize = 15.sp, color = C.sub)
            }
            returned -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("같은 각도 비교", Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                    Icon(Icons.Outlined.ZoomIn, null, Modifier.size(18.dp), tint = C.sub)
                    Text(" 눌러서 확대", fontSize = 14.sp, color = C.sub)
                }
                Row(Modifier.fillMaxWidth()) {
                    Text("", Modifier.width(56.dp))
                    Text("맡길 때", Modifier.weight(1f), fontSize = 14.sp, color = C.sub, textAlign = TextAlign.Center)
                    Text("돌려받을 때", Modifier.weight(1f), fontSize = 14.sp, color = C.sub, textAlign = TextAlign.Center)
                }
                HandoverPhotos.ANGLES.forEach { (angle, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(label, Modifier.width(48.dp), fontSize = 15.sp, color = C.ink)
                        PhotoSlot(dir, HandoverPhotos.BEFORE, angle, label, v, Modifier.weight(1f), null, open, comparison = true)
                        PhotoSlot(dir, HandoverPhotos.AFTER, angle, label, v, Modifier.weight(1f), null, open, comparison = true)
                    }
                }
            }
            active -> {
                Text("돌려받을 때 사진", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("맡길 때와 같은 각도로 찍으면 돌려받은 뒤 나란히 비교해요. 찍은 사진을 누르면 크게 보고 다시 찍을 수 있어요.", fontSize = 15.sp, color = C.sub)
                PhotoRow(dir, HandoverPhotos.AFTER, v, open) { shoot(HandoverPhotos.AFTER, it) }
                Text("맡길 때 사진 ${HandoverPhotos.count(dir, HandoverPhotos.BEFORE)}장", fontSize = 15.sp, color = C.sub)
                PhotoRow(dir, HandoverPhotos.BEFORE, v, open, null)
            }
            else -> {
                Text("맡기기 전 사진", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = C.ink)
                Text("앞·뒤·양옆·계기판을 찍어 두면 돌려받을 때 같은 각도로 비교해요. 원본 그대로 이 기기에만 저장돼요.", fontSize = 15.sp, color = C.sub)
                PhotoRow(dir, HandoverPhotos.BEFORE, v, open) { shoot(HandoverPhotos.BEFORE, it) }
            }
        }
    }
}

@Composable
private fun PhotoRow(dir: File, phase: String, version: Int, open: (String, String) -> Unit, onShoot: ((String) -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        HandoverPhotos.ANGLES.forEach { (angle, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                PhotoSlot(dir, phase, angle, label, version, Modifier.fillMaxWidth(), onShoot?.let { { it(angle) } }, open)
                Text(label, fontSize = 13.sp, color = C.sub)
            }
        }
    }
}

/** 사진 한 칸. 없으면 카메라 아이콘(누르면 찍기), 있으면 사진과 찍은 시각(누르면 크게 보기). */
@Composable
private fun PhotoSlot(dir: File, phase: String, angle: String, label: String, version: Int, modifier: Modifier, onShoot: (() -> Unit)?,
                      open: (String, String) -> Unit, comparison: Boolean = false) {
    val f = HandoverPhotos.file(dir, phase, angle)
    val bmp = remember(f.path, version) { HandoverPhotos.decode(f, 480)?.asImageBitmap() }
    val shape = RoundedCornerShape(8.dp)
    val click: (() -> Unit)? = if (bmp != null) ({ open(phase, angle) }) else onShoot
    Box(modifier.aspectRatio(if (comparison) 16f / 9f else 1f).clip(shape).background(C.chip).border(1.dp, C.line, shape)
        .then(if (click != null) Modifier.clickable(role = Role.Button, onClickLabel = if (bmp != null) "$label 사진 크게 보기" else "$label 사진 찍기", onClick = click) else Modifier)
        .testTag("photo_${phase}_$angle"), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, "$label 사진", Modifier.fillMaxSize(), contentScale = if (comparison) ContentScale.Fit else ContentScale.Crop)
            // 시각은 사진 위가 아니라 모서리 작은 띠로(사진을 가리지 않게)
            Text(VerdictEngine.hhmm(f.lastModified()), Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(bottomStart = 6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp),
                color = Color.White, fontSize = 10.sp)
        } else Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(22.dp), tint = if (onShoot != null) C.title else C.line)
    }
}

/**
 * 사진 크게 보기: 두 손가락으로 확대·이동, 두 번 누르면 2배/원래 크기. 맡길 때 ↔ 돌려받을 때를 같은 배율·위치로 바꿔 흠집을 비교한다.
 */
@Composable
private fun PhotoViewer(dir: File, startPhase: String, angle: String, version: Int, retakable: (String) -> Boolean,
                        onRetake: (String) -> Unit, onDismiss: () -> Unit) {
    val label = HandoverPhotos.ANGLES.firstOrNull { it.first == angle }?.second ?: angle
    var phase by remember { mutableStateOf(startPhase) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0D121B)).padding(16.dp).testTag("photo_viewer"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val f = HandoverPhotos.file(dir, phase, angle)
            Text("$label · ${if (phase == HandoverPhotos.BEFORE) "맡길 때" else "돌려받을 때"}" + (if (f.exists()) " · ${VerdictEngine.date(f.lastModified())} ${VerdictEngine.hhmm(f.lastModified())}" else ""),
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HandoverPhotos.BEFORE to "맡길 때", HandoverPhotos.AFTER to "돌려받을 때").forEach { (p, name) ->
                    val has = HandoverPhotos.file(dir, p, angle).exists()
                    Text(name, Modifier.clip(RoundedCornerShape(50)).background(if (p == phase) Color.White else Color.White.copy(alpha = 0.12f))
                        .then(if (has) Modifier.clickable(role = Role.Tab) { phase = p } else Modifier).padding(horizontal = 16.dp, vertical = 8.dp).testTag("viewer_$p"),
                        color = if (p == phase) Color(0xFF0D121B) else if (has) Color.White else Color.White.copy(alpha = 0.35f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                listOf(1f, 2f, 4f).forEach { z ->
                    Text("${z.toInt()}배", Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (scale == z) 0.3f else 0.12f))
                        .clickable(role = Role.Button) { scale = z; if (z == 1f) offset = Offset.Zero }.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = Color.White, fontSize = 15.sp)
                }
            }
            val bmp = remember(f.path, version) { HandoverPhotos.decode(f, 2048)?.asImageBitmap() }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        offset = if (scale == 1f) Offset.Zero else offset + pan
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2f }) },
                contentAlignment = Alignment.Center) {
                if (bmp != null) Image(bmp, "$label 사진", Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
                else Text("이 각도의 사진이 없어요", color = Color.White.copy(alpha = 0.7f), fontSize = 17.sp)
            }
            Text("두 손가락으로 확대하고, 위에서 맡길 때 · 돌려받을 때를 바꾸면 같은 배율로 비교해요.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (retakable(phase)) BigButton("다시 찍기", Modifier.weight(1f).testTag("viewer_retake"), BtnStyle.OUTLINE) { onRetake(phase) }
                BigButton("닫기", Modifier.weight(1f).testTag("viewer_close")) { onDismiss() }
            }
        }
    }
}

/** 이력서 기록 한 건에 붙은 사진이 있으면 장 수를 보여 준다. */
fun handoverPhotoCount(context: Context, startMs: Long, demo: Boolean = false): Int {
    val d = HandoverPhotos.recordDir(context, startMs, demo)
    return HandoverPhotos.count(d, HandoverPhotos.BEFORE) + HandoverPhotos.count(d, HandoverPhotos.AFTER)
}
