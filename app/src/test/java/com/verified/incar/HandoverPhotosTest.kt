package com.verified.incar

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.verified.incar.core.CarModel
import com.verified.incar.core.HandKind
import com.verified.incar.core.Persona
import com.verified.incar.ui.HandoverPhotos
import com.verified.incar.ui.handoverPhotoCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-mdpi")
class HandoverPhotosTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = RuntimeEnvironment.getApplication()
    private fun bmp() = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

    @Test fun beforePhotos_moveToRecordWhenHandoverStarts() {
        val pending = HandoverPhotos.pendingDir(app, HandKind.SERVICE)
        HandoverPhotos.save(pending, HandoverPhotos.BEFORE, "FRONT", bmp())
        HandoverPhotos.save(pending, HandoverPhotos.BEFORE, "DASH", bmp())
        assertEquals(2, HandoverPhotos.attach(app, HandKind.SERVICE, 1_000L))
        assertFalse(pending.exists())
        val rec = HandoverPhotos.recordDir(app, 1_000L)
        HandoverPhotos.save(rec, HandoverPhotos.AFTER, "FRONT", bmp())
        assertEquals(3, handoverPhotoCount(app, 1_000L))
        // 다른 맡김 기록과 섞이지 않는다
        assertEquals(0, handoverPhotoCount(app, 2_000L))
    }

    @Test fun serviceCard_showsBeforeSlotsThenAfterSlots() {
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        vm.navigate.value = "HAND_SERVICE"
        rule.waitForIdle()
        rule.onNodeWithTag("photo_before_FRONT").assertExists()
        HandoverPhotos.save(HandoverPhotos.pendingDir(app, HandKind.SERVICE), HandoverPhotos.BEFORE, "FRONT", bmp())
        vm.act { s, now -> startLend(s, now, HandKind.SERVICE) }
        rule.waitForIdle()
        rule.onNodeWithTag("photo_after_FRONT").assertExists()
        val start = vm.ui.value.engine.lend.start!!.timeMs
        assertTrue("맡기기 전 사진이 이번 맡김으로 옮겨짐", HandoverPhotos.file(HandoverPhotos.recordDir(app, start), HandoverPhotos.BEFORE, "FRONT").exists())
        vm.close()
    }

    /** PIN 재잠금으로 맡김 화면이 시작과 동시에 닫혀도 맡기기 전 사진은 그 맡김 기록에 붙는다. */
    @Test fun beforePhotos_attachEvenWhenOwnerLockClosesTheHandoverScreen() {
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        vm.setOwnerPin("2468")
        rule.setContent { MaterialTheme { App(vm) } }
        vm.navigate.value = "HAND_LEND"
        rule.waitForIdle()
        HandoverPhotos.save(HandoverPhotos.pendingDir(app, HandKind.LEND), HandoverPhotos.BEFORE, "FRONT", bmp())
        vm.act { s, now -> startLend(s, now) }
        rule.waitForIdle()
        assertFalse("맡김을 시작하면 차주 잠금이 다시 걸린다", vm.ui.value.ownerUnlocked)
        rule.onNodeWithTag("handover_photos").assertDoesNotExist()
        val start = vm.ui.value.engine.lend.start!!.timeMs
        assertTrue(HandoverPhotos.file(HandoverPhotos.recordDir(app, start), HandoverPhotos.BEFORE, "FRONT").exists())
        assertFalse(HandoverPhotos.pendingDir(app, HandKind.LEND).exists())
        vm.close()
    }

    /** 같은 종류를 다시 맡길 때도 맡기기 전 화면(사진·메모·반납 예정)을 거쳐 시작한다. */
    @Test fun repeatHandover_reopensBeforeStateForNewPhotos() {
        val vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        vm.navigate.value = "HAND_SERVICE"
        rule.waitForIdle()
        vm.act { s, now -> startLend(s, now, HandKind.SERVICE) }
        vm.skipSeconds(600)
        vm.act { s, now -> endLend(s, now) }
        rule.waitForIdle()
        val first = vm.ui.value.engine.history.single().startMs
        rule.onNodeWithTag("lend_plan").assertDoesNotExist()
        // 돌려받은 화면의 '다시 맡기기'는 바로 시작하지 않고 맡기기 전 화면을 연다
        rule.onNodeWithTag("service_start").performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(vm.ui.value.engine.lend.active)
        rule.onNodeWithTag("lend_plan").assertExists()
        rule.onNodeWithText("맡기기 전").assertExists()
        HandoverPhotos.save(HandoverPhotos.pendingDir(app, HandKind.SERVICE), HandoverPhotos.BEFORE, "REAR", bmp())
        vm.skipSeconds(60)
        rule.onNodeWithTag("service_start").performScrollTo().performClick()
        rule.waitForIdle()
        val lend = vm.ui.value.engine.lend
        assertTrue(lend.active)
        val second = lend.start!!.timeMs
        assertTrue(second != first)
        assertTrue(HandoverPhotos.file(HandoverPhotos.recordDir(app, second), HandoverPhotos.BEFORE, "REAR").exists())
        assertFalse(HandoverPhotos.file(HandoverPhotos.recordDir(app, first), HandoverPhotos.BEFORE, "REAR").exists())
        vm.close()
    }
}
