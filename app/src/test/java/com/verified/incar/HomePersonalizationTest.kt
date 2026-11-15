package com.verified.incar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.verified.incar.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePersonalizationTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun exercise(small: Boolean) {
        if (small) {
            RuntimeEnvironment.setQualifiers("sw320dp-w320dp-h640dp-port-night-mdpi")
            RuntimeEnvironment.setFontScale(1.5f)
        }
        RotationHarness(rule).use { app ->
            app.start()
            rule.onNodeWithTag("favorites_edit").performScrollTo().performClick()
            rule.onNodeWithTag("favorite_pick_CONTROL_HISTORY").performScrollTo().performClick()
            rule.onNodeWithTag("favorite_pick_TRUNK").performScrollTo().performClick()
            val expected = listOf("CONTROL_HISTORY", "TRUNK")
            app.rotate(if (small) 640 else 800, if (small) 320 else 360, night = small, smallestWidth = if (small) 320 else 360)
            rule.onNodeWithText("고정 편집 완료").assertExists()
            app.recreate()
            rule.onNodeWithText("고정 편집 완료").assertExists()
            rule.onNodeWithTag("favorites_edit").performScrollTo().performClick()
            assertEquals(expected, app.vm.ui.value.settings.homeFavorites)
            app.vm.act { s, now -> control(ControlKind.HORN, s, now) }
            rule.waitForIdle()
            rule.onNodeWithTag("favorite_open_CONTROL_HISTORY").performScrollTo().performClick()
            rule.onNodeWithTag("control_detail_0").performScrollTo().performClick()
            rule.onNodeWithTag("control_steps_0").performScrollTo().assertIsDisplayed()
            app.shot(if (small) "favorites_history_small_landscape" else "favorites_history_landscape")
            app.rotate(if (small) 320 else 360, if (small) 640 else 800, night = small, smallestWidth = if (small) 320 else 360)
            rule.onNodeWithTag("control_steps_0").performScrollTo().assertIsDisplayed()
            app.recreate()
            rule.onNodeWithTag("control_steps_0").performScrollTo().assertIsDisplayed()
            app.shot(if (small) "favorites_history_small_night" else "favorites_history_portrait")
            app.go("HOME")
            rule.onNodeWithTag("favorite_open_TRUNK").performScrollTo().assertIsDisplayed()
            app.shot(if (small) "favorites_home_small_night" else "favorites_home_portrait")
            rule.onNodeWithTag("favorites_edit").performScrollTo().performClick()
            rule.onNodeWithTag("favorite_pick_TRUNK").performScrollTo().performClick()
            rule.onNodeWithTag("favorites_edit").performScrollTo().performClick()
            rule.onNodeWithTag("favorite_open_TRUNK").assertDoesNotExist()
        }
    }

    @Test fun favoritesAndExpandedResult_surviveRotationAndRecreation() = exercise(false)
    @Test fun smallLandscapeNightLargeText_canEditAndReadResults() = exercise(true)

    @Test fun runtimeRestart_restoresFavoritesAndResults_withoutMixingStores() {
        val app = RuntimeEnvironment.getApplication()
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(carModel = CarModel.GRANDEUR, homeFavorites = listOf("TRUNK", "CONTROL_HISTORY")) }
        vm.act { s, now -> control(ControlKind.HORN, s, now) }
        vm.close()
        val restored = VerifiedRuntime(app, simTicker = false)
        try {
            assertEquals(listOf("TRUNK", "CONTROL_HISTORY"), restored.ui.value.settings.homeFavorites)
            assertEquals(ControlKind.HORN, restored.ui.value.engine.controlLog.first().kind)
            assertTrue(Store(app, ns = Store.TRIAL_NS).loadControls().isEmpty())
        } finally { restored.close() }
    }
}

