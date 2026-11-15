package com.verified.incar

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.verified.incar.core.CarModel
import com.verified.incar.core.Persona
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** 제출용 앱은 등록 없이 시연으로 시작하고 예시 기록은 별도 저장소에만 쌓인다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-mdpi")
class TrialModeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private var flags = 0

    @Before fun release() {
        flags = app.applicationInfo.flags
        app.applicationInfo.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
    }

    @After fun restore() { app.applicationInfo.flags = flags }

    private fun runtime() = VerifiedRuntime(app, simTicker = false)

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File(File("../screenshots").apply { mkdirs() }, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun trialKeepsExampleRecordsApartAndEndsCleanly() {
        Store(app).saveSettings(com.verified.incar.core.Settings(personas = setOf(Persona.NIGHT_OUT), carModel = CarModel.AVANTE))
        val real = runtime()
        assertTrue(real.ui.value.trial)
        assertTrue(real.ui.value.simAllowed)

        assertTrue(real.startTrial(CarModel.TUCSON))
        assertTrue(TrialMode.isOn(app))

        val trial = runtime()
        assertTrue(trial.ui.value.trial)
        assertTrue(trial.ui.value.simAllowed)
        assertFalse(trial.ui.value.trialAvailable)
        assertEquals(CarModel.TUCSON, trial.ui.value.settings.carModel)
        // 시연은 전체 기능을 볼 수 있고 실제 저장소의 차종·생활 유형은 그대로다
        assertEquals(Persona.entries.toSet(), trial.ui.value.settings.personas)
        assertEquals(CarModel.AVANTE, Store(app).loadSettings().carModel)

        trial.act { s, now -> startLend(s, now, com.verified.incar.core.HandKind.SERVICE) }
        trial.act { s, now -> endLend(s, now) }
        assertEquals(1, trial.ui.value.engine.history.size)
        assertTrue(Store(app).loadHistory().isEmpty())

        assertTrue(trial.endTrial())
        assertFalse(TrialMode.isOn(app))
        assertNull(Store(app, ns = Store.TRIAL_NS).loadSettings().carModel)
        val back = runtime()
        assertTrue(back.ui.value.trial)
        assertTrue(back.ui.value.simAllowed)
        assertEquals(CarModel.GRANDEUR, back.ui.value.settings.carModel)
        assertTrue(back.ui.value.engine.history.isEmpty())
        assertEquals(CarModel.AVANTE, Store(app).loadSettings().carModel)
        real.close(); trial.close(); back.close()
    }

    @Test fun releaseHomeStartsDemoAndAllowsChangingExampleCar() {
        val vm = runtime()
        vm.settings { it.copy(personas = Persona.entries.toSet()) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        rule.onNodeWithTag("home_hero").assertExists()
        rule.onNodeWithTag("trial_panel").assertExists()
        rule.onNodeWithTag("nav_SHOP").assertDoesNotExist()
        vm.navigate.value = "TRIAL"
        rule.waitForIdle()
        rule.onNodeWithTag("trial_model_TUCSON").performClick()
        rule.waitForIdle()
        shot("60_폰_체험_차종선택")
        assertTrue(vm.ui.value.trial)
        assertEquals(CarModel.TUCSON, Store(app, ns = Store.TRIAL_NS).loadSettings().carModel)
        vm.close()
    }

    @Test fun trialHomePlaysServiceScenario() {
        runtime().apply { settings { it.copy(personas = Persona.entries.toSet()) } }.startTrial(CarModel.GRANDEUR)
        val vm = runtime()
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trial_panel").assertExists()
        rule.onNodeWithTag("nav_SHOP").assertDoesNotExist()
        shot("61_폰_체험_홈")
        rule.onNodeWithTag("trial_service").performScrollTo().performClick()
        repeat(40) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)); rule.waitForIdle() }
        val h = vm.ui.value.engine.history
        assertEquals(listOf("맡김"), h.map { it.kind })
        assertTrue("예시 값으로 이동 거리가 남는다: ${h.first().km}", (h.first().km ?: 0.0) > 0.0)
        shot("62_폰_체험_정비반납")
    }
}
