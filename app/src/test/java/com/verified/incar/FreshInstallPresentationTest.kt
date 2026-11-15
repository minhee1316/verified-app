package com.verified.incar

import android.app.Application
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 설정을 사전에 넣지 않고 설치용 MainActivity의 첫 화면과 제어 진입을 렌더링한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw411dp-w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FreshInstallPresentationTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun releaseLaunch_opensWorkingDemoAndRendersLauncherIcon() {
        val app = RuntimeEnvironment.getApplication()
        val flags = app.applicationInfo.flags
        app.applicationInfo.flags = flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        try {
            controller.setup()
            rule.waitForIdle()
            val vm = (app as VerifiedApp).runtime
            assertTrue(vm.ui.value.trial)
            assertFalse(vm.ui.value.carConnected)
            rule.onNodeWithTag("quick_lock").assertIsDisplayed()
            val root = controller.get().window.decorView
            fun shot(name: String) {
                rule.waitForIdle()
                val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(image))
                save(name, image)
            }
            shot("release_home")
            rule.onNodeWithTag("nav_CONTROL").performClick()
            rule.onNodeWithTag("control_exterior").assertIsDisplayed()
            shot("release_control")
            rule.onNodeWithTag("control_interior").performClick()
            rule.onNodeWithTag("cabin_temp_up").performScrollTo().performClick()
            shot("release_interior")
            vm.navigate.value = "VALET"
            rule.waitForIdle()
            // 일반 동의 흐름을 먼저 골라도 자동 시연으로 다시 진행할 수 있어야 한다.
            vm.act { _, _ -> startValet() }
            vm.act { s, now -> confirmValetConsent(s, now) }
            rule.waitForIdle()
            rule.onNodeWithText("전체 과정 다시 시연").assertExists()
            rule.onNodeWithTag("valet_demo_start").performScrollTo().performClick()
            repeat(20) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
                rule.waitForIdle()
            }
            assertTrue(vm.ui.value.engine.valet.handover != null)
            assertTrue(vm.ui.value.engine.valet.proof.isNotEmpty())
            shot("release_valet_result")
            vm.runScenario("ev_trip")
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
            rule.waitForIdle()
            rule.onNodeWithTag("demo_stop").assertIsDisplayed()
            shot("release_driving")
            rule.onNodeWithTag("demo_stop").performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("nav_HOME").assertIsDisplayed()
            assertFalse(vm.ui.value.signals.uxRestricted)

            // 실행 아이콘을 Android 리소스 경로 그대로 로드해 adaptive mask 적용 결과를 확인한다.
            val icon = checkNotNull(controller.get().getDrawable(R.mipmap.ic_launcher))
            val image = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, 256, 256)
            icon.draw(Canvas(image))
            save("launcher_icon", image)
        } finally {
            controller.pause().stop().destroy()
            (app as VerifiedApp).runtime.close()
            app.applicationInfo.flags = flags
        }
    }

    private fun save(name: String, image: Bitmap) {
        File(File("../screenshots/branding").apply { mkdirs() }, "$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }
}
