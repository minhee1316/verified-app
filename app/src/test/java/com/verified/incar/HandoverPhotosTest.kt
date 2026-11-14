package com.verified.incar

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
