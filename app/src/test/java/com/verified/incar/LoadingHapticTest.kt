package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.verified.incar.core.CarModel
import com.verified.incar.core.Persona
import com.verified.incar.core.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoadingHapticTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication()

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("../screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun controlWaiting_showsRingAroundIcon_thenStops() {
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.onNodeWithTag("nav_CONTROL").performClick()
        rule.waitForIdle()
        // 시뮬레이터가 명령을 반영하지 않게 해, 잠금 명령이 차 신호를 기다리는 상태로 남게 한다
        vm.setSimControlFail(true)
        vm.act { sig, now -> control(if (sig.doorsLocked) com.verified.incar.core.ControlKind.UNLOCK else com.verified.incar.core.ControlKind.LOCK, sig, now) }
        rule.waitForIdle()
        rule.onNodeWithTag("control_busy", useUnmergedTree = true).assertExists()
        shot("78_제어_결과대기_링")
    }

    @Test fun startHaptic_evSofterThanEngine_andSettingSurvivesRestart() {
        val ev = StartHaptic.pattern(ev = true)
        val ice = StartHaptic.pattern(ev = false)
        assertTrue(ev.maxOf { it.second } < ice.maxOf { it.second })
        assertTrue((ev + ice).all { it.second in 0..255 })
        assertTrue(Settings().startHaptic)
        Store(app).saveSettings(Settings(startHaptic = false))
        assertEquals(false, Store(app).loadSettings().startHaptic)
    }
}
