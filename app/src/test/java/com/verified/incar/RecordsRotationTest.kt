package com.verified.incar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.verified.incar.core.CarModel
import com.verified.incar.core.HandoverRecord
import com.verified.incar.core.MyHyundai
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 입력·선택을 바꾼 다음 실제 창을 왕복 회전하고 Activity 복원까지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordsRotationTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun roundTrip(app: RotationHarness, check: () -> Unit) {
        app.rotate(800, 360)
        check()
        app.rotate(360, 800)
        check()
        app.recreate()
        check()
    }

    @Test fun search_keepsQueryAndMatchingRoute() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("SEARCH")
            rule.onNodeWithTag("search_box").performTextInput("공기압")
            roundTrip(app) {
                rule.onNodeWithTag("search_box").assertTextContains("공기압")
                rule.onNodeWithTag("search_SERVICE").assertExists()
            }
            rule.onNodeWithTag("search_SERVICE").performScrollTo().performClick()
            rule.onNodeWithTag("service_tiles").assertExists()
        }
    }

    @Test fun fineCheck_keepsDraftAndMatchingEvidence() {
        RotationHarness(rule).use { app ->
            app.start()
            val start = MyHyundai.parseDateTime("2026-11-15 14:00")!!
            app.vm.act { _, _ ->
                loadHistory(listOf(HandoverRecord("맡김", start, start + 3_600_000L, 3.2, 30f, 0, emptyList(), true)))
                emptyList()
            }
            app.go("HISTORY")
            rule.onNodeWithTag("pane_0").performClick()
            rule.onNodeWithTag("fine_input").performScrollTo().performTextInput("2026-11-15 14:30")
            roundTrip(app) {
                rule.onNodeWithTag("fine_input").assertTextContains("2026-11-15 14:30")
                rule.onNodeWithTag("fine_proof").assertExists()
            }
        }
    }

    @Test fun service_keepsBookingDateAcrossTabChangesAndRotation() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("SERVICE")
            val date = LocalDate.now().plusDays(3).toString()
            rule.onNodeWithText("블루핸즈 정비 예약").performScrollTo().performClick()
            rule.onNodeWithText(date.substring(5)).performScrollTo().performClick()
            rule.onNodeWithTag("pane_1").performScrollTo().performClick()
            app.rotate(800, 360)
            rule.onNodeWithText(date.substring(5)).assertIsSelected()
            app.rotate(360, 800)
            rule.onNodeWithTag("pane_1").assertIsSelected()
            rule.onNodeWithTag("pane_0").performClick()
            rule.onNodeWithText(date.substring(5)).assertIsSelected()
            app.recreate()
            rule.onNodeWithText(date.substring(5)).assertIsSelected()
            rule.onNodeWithText("14:00 예약").performScrollTo().performClick()
            rule.runOnIdle { assertEquals(date, app.vm.ui.value.settings.bookings.first().date) }
        }
    }

    @Test fun digitalKey_keepsRecipientAndSharesToTheChosenPerson() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("KEY")
            rule.onNodeWithTag("key_who_부모님").performScrollTo().performClick()
            roundTrip(app) {
                rule.onNodeWithTag("key_who_부모님").assertIsSelected()
                rule.onNodeWithTag("key_share").assertTextContains("부모님 · 7일 키 공유하기")
            }
            rule.onNodeWithTag("key_share").performScrollTo().performClick()
            rule.runOnIdle { assertTrue(app.vm.ui.value.settings.sharedKeys.any { it.name == "부모님" }) }
        }
    }

    @Test fun carLife_keepsFiltersOpenWashPanelAndBookingDate() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("CARLIFE")
            rule.onNodeWithTag("charger_지하·실내").performScrollTo().performClick()
            rule.onNode(hasText("세차") and hasAnyAncestor(hasTestTag("life_tiles"))).performScrollTo().performClick()
            val date = LocalDate.now().plusDays(2).toString()
            rule.onNodeWithText(date.substring(5)).performScrollTo().performClick()
            roundTrip(app) {
                rule.onNodeWithTag("charger_지하·실내").assertIsSelected()
                rule.onNodeWithText("자동 세차 들어가기 전에").assertExists()
                rule.onNodeWithText(date.substring(5)).assertIsSelected()
            }
            rule.onNodeWithText("10:00 예약").performScrollTo().performClick()
            rule.runOnIdle {
                val booking = app.vm.ui.value.settings.bookings.first()
                assertEquals("세차", booking.kind)
                assertEquals(date, booking.date)
            }
        }
    }

    @Test fun vehicleExplorer_keepsInteriorPhotoAndSeatTab() {
        RotationHarness(rule).use { app ->
            app.start()
            // 그랜저 실내 사진은 한 장이므로, 두 장이 있는 아반떼로 페이지 보존을 검증한다.
            app.vm.settings { it.copy(carModel = CarModel.AVANTE) }
            app.go("VEHICLE_VIEW")
            rule.onNodeWithTag("explorer_interior").performScrollTo().performClick()
            rule.onNodeWithTag("vehicle_photo_next").performScrollTo().performClick()
            val caption = rule.onNodeWithTag("vehicle_photo_caption").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
            rule.onNodeWithTag("explorer_tab_seats").performScrollTo().performClick()
            roundTrip(app) {
                rule.onNodeWithTag("explorer_interior").assertIsSelected()
                rule.onNodeWithTag("explorer_tab_seats").assertIsSelected()
                rule.onNodeWithTag("vehicle_photo_caption").assertTextEquals(caption)
                rule.onNodeWithTag("explorer_seats").assertExists()
            }
        }
    }
}
