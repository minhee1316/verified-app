package com.verified.incar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.verified.incar.core.Module
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 정적 화면별 실행이 아니라, 같은 Activity에서 모든 화면을 회전 왕복한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RotationScreenAuditTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun tour(width: Int, height: Int, night: Boolean, prefix: String) {
        RotationHarness(rule).use { app ->
            app.start()
            val routes = listOf("HOME", "CONTROL", "HISTORY", "MYPAGE", "VERIFIED", "SEARCH", "CARLIFE", "SERVICE", "SHOP", "WALLET", "KEY", "INSIGHT", "NOTICE", "SETTINGS", "CAR_SETUP", "VEHICLE_VIEW", "HAND_SERVICE", "HAND_LEND", "PHONE_LINK", "TRIAL") + Module.entries.map { it.name }
            for (route in routes.distinct()) {
                app.go(route)
                app.rotate(height, width, night, width)
                rule.onNodeWithTag("nav_HOME").assertIsDisplayed()
                rule.onNodeWithTag("nav_CONTROL").assertIsDisplayed()
                app.shot("${prefix}_${route}_landscape")
                app.rotate(width, height, night, width)
                rule.onNodeWithTag("nav_MYPAGE").assertIsDisplayed()
                app.shot("${prefix}_${route}_portrait")
            }
        }
    }

    @Test fun allScreens_rotateAtNormalPhoneSize() = tour(360, 800, false, "normal")

    @Test @Config(qualifiers = "sw320dp-w320dp-h640dp-port-night-mdpi")
    fun allScreens_rotateOnSmallPhoneWithLargeTextAndNightTheme() {
        RuntimeEnvironment.setFontScale(1.5f)
        tour(320, 640, true, "small_night_large")
    }
}
