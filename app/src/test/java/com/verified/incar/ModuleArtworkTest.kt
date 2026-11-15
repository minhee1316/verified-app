package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.verified.incar.core.*
import com.verified.incar.ui.C
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 실제 동행 신호가 네이티브 안내 그림의 대기·경고 상태까지 반영되는지 검사한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModuleArtworkTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(vm: VerifiedRuntime) {
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR,
            flags = Flag.entries.toSet(), consents = Consent.entries.toSet()) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        vm.navigate.value = "ESCORT"
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File("../screenshots/design").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun assertUnclippedText(tag: String) {
        val node = rule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$tag 글자 배치 없음", layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("$tag 가로 잘림", it.didOverflowWidth)
            assertFalse("$tag 세로 잘림", it.didOverflowHeight)
        }
    }

    @Test fun missingVehicleSignals_haveNeutralChecksAndNoGuidance() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), false, PhoneRelay::publish) { _, _ -> ConnectedWithoutReadings() }
        try {
            show(vm)
            vm.act { _, now -> startEscort(now); emptyList() }
            rule.waitForIdle()
            listOf("뒷문", "탑승", "안전벨트").forEach {
                rule.onNodeWithContentDescription("$it · 확인 불가 · 신호 미수신").assertExists()
            }
            rule.onNodeWithTag("escort_artwork_status").assertTextEquals("차량 신호 확인 중")
            rule.onNodeWithContentDescription("신호 미수신: 하차 유도 대기").assertExists()
            rule.onNodeWithContentDescription("오른쪽 보도 방향 조명 안내, 왼쪽 차도 주의").assertDoesNotExist()
            assertUnclippedText("escort_artwork_status")
            shot("native_escort_unknown")
        } finally { vm.close(); C.forceDark = null }
    }

    @Test fun seaWarning_removesActiveGuidanceAndShowsWaitMessage() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        try {
            show(vm)
            vm.act { _, now -> startEscort(now); emptyList() }
            vm.sim { it.copy(gear = Gear.P, speedKmh = 0f, row2Seated = true, row2Belt = true,
                row2LeftDoorOpen = false, row2RightDoorOpen = false) }
            rule.waitForIdle()
            vm.sim { it.copy(gear = Gear.D, speedKmh = 30f) }
            rule.waitForIdle()
            vm.sim { it.copy(gear = Gear.P, speedKmh = 0f, place = Place.HOSPITAL) }
            rule.waitForIdle()
            vm.skipSeconds(4)
            rule.waitForIdle()
            assertEquals(EscortPhase.ALIGHT_GUIDE, vm.ui.value.engine.escort.phase)
            rule.onNodeWithContentDescription("오른쪽 보도 방향 조명 안내, 왼쪽 차도 주의").assertExists()
            assertUnclippedText("escort_safe_label")
            shot("native_escort_guide")

            vm.sim { it.copy(seaWarning = true) }
            rule.waitForIdle()
            rule.onNodeWithTag("escort_artwork_status").assertTextEquals("순정 안전하차보조 경고")
            rule.onNodeWithTag("escort_safe_label").assertTextEquals("하차 대기")
            rule.onNodeWithContentDescription("순정 경고 중: 하차 유도 조명 꺼짐").assertExists()
            rule.onNodeWithContentDescription("오른쪽 보도 방향 조명 안내, 왼쪽 차도 주의").assertDoesNotExist()
            assertUnclippedText("escort_artwork_status")
            shot("native_escort_sea_warning")
        } finally { vm.close(); C.forceDark = null }
    }
}
