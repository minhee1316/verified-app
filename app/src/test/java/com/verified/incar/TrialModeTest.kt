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

/** 제출용 빌드의 휴대폰 체험 모드: 예시 값은 체험 저장소에만 쌓이고, 끝내면 실제 모드로 돌아간다. */
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
        val real = runtime()
        real.settings { it.copy(personas = setOf(Persona.NIGHT_OUT), carModel = CarModel.AVANTE) }
        assertTrue(real.ui.value.trialAvailable)
        assertFalse(real.ui.value.simAllowed)

        assertTrue(real.startTrial(CarModel.TUCSON))
        assertTrue(TrialMode.isOn(app))

        val trial = runtime()
        assertTrue(trial.ui.value.trial)
        assertTrue(trial.ui.value.simAllowed)
        assertFalse(trial.ui.value.trialAvailable)
        assertEquals(CarModel.TUCSON, trial.ui.value.settings.carModel)
        // 생활 유형은 이어 가지만 실제 저장소의 차종은 그대로다
        assertEquals(setOf(Persona.NIGHT_OUT), trial.ui.value.settings.personas)
        assertEquals(CarModel.AVANTE, Store(app).loadSettings().carModel)

        trial.act { s, now -> startLend(s, now, com.verified.incar.core.HandKind.SERVICE) }
        trial.act { s, now -> endLend(s, now) }
        assertEquals(1, trial.ui.value.engine.history.size)
        assertTrue(Store(app).loadHistory().isEmpty())

        assertTrue(trial.endTrial())
        assertFalse(TrialMode.isOn(app))
        assertNull(Store(app, ns = Store.TRIAL_NS).loadSettings().carModel)
        val back = runtime()
        assertFalse(back.ui.value.trial)
        assertFalse(back.ui.value.simAllowed)
        assertTrue(back.ui.value.engine.history.isEmpty())
    }

    @Test fun releaseHomeOffersTrialAndHidesExampleShop() {
        val vm = runtime()
        vm.settings { it.copy(personas = Persona.entries.toSet()) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        rule.onNodeWithTag("trial_entry").assertExists()
        rule.onNodeWithTag("nav_SHOP").assertDoesNotExist()
        rule.onAllNodesWithText("차종 고르고 체험 시작").onFirst().performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trial_model_TUCSON").performClick()
        rule.waitForIdle()
        shot("60_폰_체험_차종선택")
        rule.onNodeWithTag("trial_start").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(TrialMode.isOn(app))
        assertEquals(CarModel.TUCSON, Store(app, ns = Store.TRIAL_NS).loadSettings().carModel)
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
