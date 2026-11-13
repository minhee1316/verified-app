package com.verified.incar.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.Half
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.IBLPrefilterContext
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.google.android.filament.utils.rotation
import com.google.android.filament.utils.scale
import com.google.android.filament.utils.translation
import com.verified.incar.UiState
import com.verified.incar.core.CarModel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** 예시 차종과 비슷한 3D 모델과 출처. 비상업 라이선스 모델이 있어 파일은 demo 빌드 assets에만 넣는다. */
internal data class Car3D(val asset: String, val title: String, val credit: String, val yaw: Float)

internal fun car3d(model: CarModel): Car3D? = when (model) {
    CarModel.GRANDEUR -> Car3D("models/grandeur.glb", "2012 현대 아제라(그랜저 HG 수출형)", "제작자·라이선스 확인 필요", 215f) // 이 모델만 앞이 -Z를 본다
    CarModel.AVANTE -> Car3D("models/avante.glb", "2024 Hyundai Elantra N(아반떼 N 수출형)",
        "Ddiaz Design · CC BY-NC-SA 4.0 · sketchfab.com/3d-models/2024-hyundai-elantra-n-4ba1b1b0eb844e318cc708ada1f2f51f", 35f)
    CarModel.TUCSON -> Car3D("models/tucson.glb", "2022 Hyundai Tucson L 270T(중국형 투싼)",
        "Ddiaz Design · CC BY-NC-SA 4.0 · sketchfab.com/3d-models/2022-hyundai-tucson-l-270t-6e69767042b2453ea91d533076821636", 35f)
    CarModel.IONIQ3 -> Car3D("models/ioniq3.glb", "Hyundai Ioniq 5 Lowpoly(아이오닉 3 대신 아이오닉 5)",
        "andikapratamaw · CC BY 4.0 · sketchfab.com/3d-models/hyundai-ioniq-5-lowpoly-675e78311e8440d88714bd212cb7a8fb", 35f)
    CarModel.OTHER -> null
}

/** demo 빌드에만 모델 파일이 있다. */
internal fun hasCar3D(context: Context) = runCatching { context.assets.open("models/grandeur.glb").close(); true }.getOrDefault(false)

private const val SIDE_PITCH = 14f
private const val TOP_PITCH = 88f

@Composable
internal fun Car3DScreen(u: UiState) {
    val context = LocalContext.current
    val available = remember { hasCar3D(context) }
    var model by rememberSaveable { mutableStateOf(u.settings.carModel?.takeIf { it in TRIAL_MODELS } ?: CarModel.GRANDEUR) }
    var top by rememberSaveable { mutableStateOf(false) }
    var spin by rememberSaveable { mutableStateOf(true) }
    var failed by remember(model) { mutableStateOf(false) }
    val info = car3d(model) ?: return
    val compact = isCompact()
    val height = (LocalConfiguration.current.screenHeightDp * if (compact) 0.42f else 0.55f).coerceIn(220f, 460f).dp
    val shown = available && !failed
    Column(Modifier.fillMaxWidth().padding(hPad(28.dp)).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            Wrap { TRIAL_MODELS.forEach { m -> Chip(m.label, m == model, Modifier.testTag("car3d_${m.name}")) { model = m } } }
            val box = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp)).background(C.chip)
            if (shown) key(model) {
                CarViewer(info, top, spin, box.testTag("car3d_view").semantics { contentDescription = "${model.label}와 비슷한 3D 모델" }) { failed = true }
            } else Box(box, contentAlignment = Alignment.Center) { CarPhoto(model, Modifier.fillMaxWidth(0.8f)) }
            Text(if (shown) "한 손가락으로 밀면 돌아가고, 두 손가락으로 벌리면 커져요." else "이 기기에서는 3D를 열 수 없어 사진으로 보여 드려요.", fontSize = 16.sp, color = C.sub)
            if (shown) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton(if (top) "옆에서 보기" else "위에서 보기", Modifier.weight(1f).testTag("car3d_top"), BtnStyle.TONAL, fontSize = 18.sp) { top = !top }
                BigButton(if (spin) "회전 멈추기" else "자동 회전", Modifier.weight(1f).testTag("car3d_spin"), BtnStyle.OUTLINE, fontSize = 18.sp) { spin = !spin }
            }
        }
        Card(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text("비슷한 모델로 보여 드려요", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
            Text("${info.title} 모델이에요. 실제 ${model.label}과 세대·모양이 다를 수 있어요.", fontSize = 16.sp, color = C.sub)
            Text("3D 모델: ${info.credit}", fontSize = 14.sp, color = C.sub)
        }
    }
}

@Composable
private fun CarViewer(info: Car3D, top: Boolean, spin: Boolean, modifier: Modifier, onFail: () -> Unit) {
    var scene by remember { mutableStateOf<CarScene?>(null) }
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { tv ->
                scene = runCatching { CarScene(tv, ctx.assets.open(info.asset).use { it.readBytes() }, info.yaw) }
                    .onFailure { onFail() }.getOrNull()
            }
        },
        modifier = modifier,
        update = { scene?.let { it.top = top; it.spin = spin } },
        onRelease = { scene?.stop() },
    )
}

/**
 * Filament로 glb 한 대를 그린다. 카메라는 고정하고 차를 돌리고 기울인다(위에서 보기 = 88도 기울이기).
 * ModelViewer는 TextureView가 창에서 떨어질 때 엔진까지 스스로 정리한다.
 */
@SuppressLint("ClickableViewAccessibility")
private class CarScene(private val tv: TextureView, glb: ByteArray, startYaw: Float) : Choreographer.FrameCallback {
    var top = false
    var spin = true
    private var running = true
    private var yaw = startYaw
    private var pitch = SIDE_PITCH
    private var tilt = SIDE_PITCH
    private var zoom = 1f
    private var lastFrame = 0L
    private var touching = false
    private var lastX = 0f
    private var lastY = 0f
    private var regrab = false
    private val viewer: ModelViewer

    private val pinch = ScaleGestureDetector(tv.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { zoom = (zoom * d.scaleFactor).coerceIn(0.6f, 2.5f); return true }
    })

    init {
        Utils.init()
        // ModelViewer의 정리 리스너보다 먼저 프레임을 멈춘다(리스너는 붙인 순서로 불린다)
        tv.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}
            override fun onViewDetachedFromWindow(v: android.view.View) = stop()
        })
        tv.isOpaque = false
        val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }
        val engine = Engine.create()
        viewer = ModelViewer(tv, engine, ui, Manipulator.Builder().targetPosition(0f, 0f, -4f).build(Manipulator.Mode.ORBIT))
        viewer.view.blendMode = View.BlendMode.TRANSLUCENT
        viewer.renderer.clearOptions = viewer.renderer.clearOptions.apply { clear = true; clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0) }
        val reflections = studioReflections(engine)
        viewer.indirectLightCubemap = reflections
        viewer.scene.indirectLight = IndirectLight.Builder().reflections(reflections)
            .irradiance(1, floatArrayOf(0.55f, 0.55f, 0.56f)).intensity(30_000f).build(engine)
        viewer.loadModelGlb(ByteBuffer.allocateDirect(glb.size).order(ByteOrder.nativeOrder()).put(glb).flip())
        checkNotNull(viewer.asset) { "glb를 읽지 못함" }
        tv.setOnTouchListener { v, e -> touch(e); v.parent?.requestDisallowInterceptTouchEvent(e.actionMasked != MotionEvent.ACTION_UP && e.actionMasked != MotionEvent.ACTION_CANCEL); true }
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun touch(e: MotionEvent) {
        pinch.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { touching = true; lastX = e.x; lastY = e.y; regrab = false }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> regrab = true
            MotionEvent.ACTION_MOVE -> if (e.pointerCount == 1 && !pinch.isInProgress) {
                if (!regrab) {
                    yaw += (e.x - lastX) * 0.35f
                    tilt = (tilt + (e.y - lastY) * 0.25f).coerceIn(0f, TOP_PITCH)
                }
                lastX = e.x; lastY = e.y; regrab = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
        }
    }

    private var wasTop = false
    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val dt = if (lastFrame == 0L) 0f else ((frameTimeNanos - lastFrame) / 1e9f).coerceAtMost(0.1f)
        lastFrame = frameTimeNanos
        if (top != wasTop) { tilt = if (top) TOP_PITCH else SIDE_PITCH; wasTop = top }
        if (spin && !touching) yaw += 24f * dt
        pitch += (tilt - pitch) * min(1f, dt * 6f)
        place()
        viewer.render(frameTimeNanos)
    }

    /** 화면 비율에 맞춰 차 길이가 폭의 약 72%가 되게 놓는다(카메라 (0,0,1)에서 (0,0,-4)를 본다). */
    private fun place() {
        val asset = viewer.asset ?: return
        val w = tv.width.coerceAtLeast(1); val h = tv.height.coerceAtLeast(1)
        val visH = 2f * 5f * tan(atanHalfFov())
        val visW = visH * w / h
        val box = asset.boundingBox
        val c = box.center; val e = box.halfExtent
        val fit = 0.72f * min(visW, 2.2f * visH) / 2f
        val s = fit / max(e[0], max(e[1], e[2])) * zoom
        val m = translation(Float3(0f, 0f, -4f)) * rotation(Float3(1f, 0f, 0f), pitch) * rotation(Float3(0f, 1f, 0f), yaw) *
            scale(Float3(s)) * translation(Float3(-c[0], -c[1], -c[2]))
        val tm = viewer.engine.transformManager
        tm.setTransform(tm.getInstance(asset.root), m.toFloatArray())
    }

    /** ModelViewer 기본 렌즈(28mm, 35mm 필름 세로 24mm)의 세로 반화각. */
    private fun atanHalfFov() = kotlin.math.atan(12f / viewer.cameraFocalLength)
}

/** 위가 밝고 바닥이 어두운 스튜디오에 길쭉한 조명 네 개를 둔 환경. 차 표면에 반사 줄이 생기게 한다. */
private fun studioReflections(engine: Engine): Texture {
    val w = 128; val h = 64
    val buf = ByteBuffer.allocateDirect(w * h * 4 * 2).order(ByteOrder.nativeOrder())
    for (y in 0 until h) {
        val elev = (0.5f - (y + 0.5f) / h) * PI.toFloat() // 위 +90도, 아래 -90도
        val deg = elev * 180f / PI.toFloat()
        for (x in 0 until w) {
            val u = (x + 0.5f) / w
            var l = if (elev >= 0) 0.6f + 0.4f * sin(elev) else 0.15f + 0.35f * (1f + sin(elev))
            if (deg in 25f..45f && (u % 0.25f) in 0.04f..0.19f) l = 6f
            if (deg > 75f) l = 3f
            val half = Half.toHalf(l)
            buf.putShort(half).putShort(half).putShort(half).putShort(Half.toHalf(1f))
        }
    }
    buf.flip()
    val equirect = Texture.Builder().width(w).height(h).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
        .format(Texture.InternalFormat.RGBA16F).build(engine)
    equirect.setImage(engine, 0, Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.HALF))
    val ctx = IBLPrefilterContext(engine)
    val toCube = IBLPrefilterContext.EquirectangularToCubemap(ctx)
    val specular = IBLPrefilterContext.SpecularFilter(ctx)
    val cube = toCube.run(equirect)
    val reflections = specular.run(cube)
    engine.destroyTexture(equirect)
    engine.destroyTexture(cube)
    specular.destroy(); toCube.destroy(); ctx.destroy()
    return reflections
}
