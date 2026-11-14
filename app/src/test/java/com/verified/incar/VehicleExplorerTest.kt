package com.verified.incar

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.verified.incar.core.CabinSetting
import com.verified.incar.core.CarModel
import com.verified.incar.core.Persona
import com.verified.incar.core.SeatPart
import com.verified.incar.core.DoorPart
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VehicleExplorerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: VerifiedRuntime

    private fun start(release: Boolean = false) {
        val app = RuntimeEnvironment.getApplication()
        if (release) app.applicationInfo.flags = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
        vm = VerifiedRuntime(app, simTicker = false)
        vm.settings { it.copy(personas = Persona.entries.toSet(), carModel = CarModel.GRANDEUR) }
        rule.setContent { MaterialTheme { App(vm) } }
        rule.waitForIdle()
        rule.onNodeWithTag("nav_CONTROL").performClick()
        rule.waitForIdle()
        tap("control_vehicle_view")
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String, tag: String) {
        rule.onNodeWithTag(tag).performScrollTo()
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(File("../screenshots").apply { mkdirs() }, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun officialPhotosDoorAndInteriorClimateWorkTogether() {
        start()
        rule.onNodeWithTag("vehicle_scene").assertExists()
        shot("69_폰_차량360_외관", "vehicle_scene")
        tap("vehicle_photo_next")
        rule.onNodeWithTag("vehicle_photo_caption").assertTextContains("앞부분 디테일", substring = true)
        tap("explorer_door_FRONT_RIGHT")
        // 실내 온도·열선은 제어 화면 한 곳에서만 바꾼다
        rule.onNodeWithTag("explorer_to_control").assertExists()
        shot("70_폰_차량360_실내", "vehicle_scene")
        tap("explorer_example_belt")
        tap("explorer_example_lock")
        assertEquals(false, vm.ui.value.signals.details.belted[SeatPart.REAR_RIGHT]?.value)
        assertEquals(false, vm.ui.value.signals.details.doorLocked[DoorPart.FRONT_RIGHT]?.value)
        assertTrue(vm.ui.value.signals.row2Seated)
        assertFalse(vm.ui.value.signals.row2Belt)
        assertFalse(vm.ui.value.signals.doorsLocked)
        shot("72_폰_차량360_좌석잠금", "explorer_seats")
        tap("explorer_model_TUCSON")
        assertEquals(CarModel.TUCSON, vm.ui.value.settings.carModel)
        shot("73_폰_차량360_SUV", "vehicle_scene")
        vm.close()
    }

    @Test fun unconnectedReleaseDoesNotInventLocationsOrApplyClimate() {
        start(release = true)
        rule.onNodeWithTag("explorer_seat_DRIVER").assertExists()
        assertEquals(null, vm.ui.value.signals.details.occupied[SeatPart.DRIVER])
        tap("explorer_interior")
        rule.onNodeWithTag("explorer_to_control").assertExists()
        val before = vm.ui.value.signals
        vm.setCabin(CabinSetting.from(before).copy(temperatureC = 30f))
        assertEquals(before, vm.ui.value.signals)
        vm.close()
    }

    @Test fun homeDoorWarningOpensTheMatchingDoor() {
        start()
        vm.sim { it.copy(ignitionOn = false, driverSeated = false, doorsLocked = true,
            details = com.verified.incar.core.VehicleDetails(
                doorOpen = mapOf(DoorPart.FRONT_RIGHT to com.verified.incar.core.AreaValue(false, 1_000)),
                doorLocked = mapOf(DoorPart.FRONT_RIGHT to com.verified.incar.core.AreaValue(false, 1_000)))) }
        rule.waitForIdle()
        rule.onNodeWithTag("nav_HOME").performClick()
        rule.waitForIdle()
        tap("home_alert_door_FRONT_RIGHT")
        rule.onNodeWithTag("explorer_focus").assertTextContains("조수석 문", substring = true)
        rule.onNodeWithTag("explorer_focus").assertTextContains("잠금 해제", substring = true)
        shot("75_폰_홈경고_조수석문", "explorer_focus")
        vm.close()
    }

    @Test fun homeSeatWarningKeepsItsTargetThroughOwnerPinAuthentication() {
        start()
        vm.sim { it.copy(details = com.verified.incar.core.VehicleDetails(
            occupied = mapOf(SeatPart.REAR_RIGHT to com.verified.incar.core.AreaValue(true, 1_000)),
            belted = mapOf(SeatPart.REAR_RIGHT to com.verified.incar.core.AreaValue(false, 1_000)))) }
        vm.setOwnerPin("2468")
        vm.act { s, now -> startLend(s, now) }
        rule.waitForIdle()
        rule.onNodeWithTag("nav_HOME").performClick()
        rule.waitForIdle()
        tap("home_alert_seat_REAR_RIGHT")
        assertNotNull(vm.ownerRequest.value)
        assertNull(vm.unlockOwner("2468"))
        rule.waitForIdle()
        rule.onNodeWithTag("explorer_focus").assertTextContains("뒷좌석 오른쪽", substring = true)
        rule.onNodeWithTag("explorer_focus").assertTextContains("벨트 미체결", substring = true)
        rule.onNodeWithTag("explorer_to_control").assertExists()
        shot("76_폰_홈경고_뒷좌석벨트", "explorer_focus")
        vm.close()
    }

    @Config(qualifiers = "w1408dp-h792dp-land-mdpi")
    @Test fun carScreenRendersAllModelsAndDrivingBlocksCabinChanges() {
        start()
        for (model in listOf(CarModel.GRANDEUR, CarModel.AVANTE, CarModel.TUCSON, CarModel.IONIQ3)) {
            tap("explorer_model_${model.name}")
            rule.onNodeWithTag("vehicle_photo").assertContentDescriptionContains("${model.label} 공식 사진", substring = true)
            tap("explorer_interior")
            rule.onNodeWithTag("vehicle_photo").assertContentDescriptionContains("${model.label} 공식 사진", substring = true)
            tap("explorer_exterior")
        }
        shot("74_차량360_가로", "vehicle_scene")
        vm.sim { it.copy(gear = com.verified.incar.core.Gear.D, speedKmh = 25f) }
        rule.waitForIdle()
        val before = vm.ui.value.signals
        vm.setCabin(CabinSetting.from(before).copy(temperatureC = 30f))
        assertEquals(before.hvacTempC, vm.ui.value.signals.hvacTempC)
        vm.close()
    }
}
