package com.verified.incar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.verified.incar.core.Consent
import com.verified.incar.core.Flag
import com.verified.incar.core.Gear
import com.verified.incar.core.Module
import com.verified.incar.core.Place
import com.verified.incar.core.ValetPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 실행 중 회전과 Activity 재생성에서도 모듈의 선택·안내·주행 잠금을 유지한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModuleRotationTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun roundTrip(app: RotationHarness, check: () -> Unit) {
        app.rotate(800, 360)
        check()
        app.rotate(360, 800)
        check()
        app.recreate()
        check()
    }

    private fun assertWholeBounds(app: RotationHarness, tag: String): SemanticsNodeInteraction {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
        val semantics = node.fetchSemanticsNode()
        val bounds = semantics.boundsInRoot
        val window = app.activity.window.decorView
        assertTrue("$tag 가로 경계: $bounds", bounds.left >= 0f && bounds.right <= window.width + 1f)
        assertTrue("$tag 세로 경계: $bounds", bounds.top >= 0f && bounds.bottom <= window.height + 1f)
        assertTrue("$tag 부모에 의해 폭 잘림: 원래=${semantics.size.width}, 노출=${bounds.width}", bounds.width + 1f >= semantics.size.width)
        assertTrue("$tag 부모에 의해 높이 잘림: 원래=${semantics.size.height}, 노출=${bounds.height}", bounds.height + 1f >= semantics.size.height)
        return node
    }

    /** assertIsDisplayed만으로는 일부만 보이는 글자를 통과시키므로 전체 경계와 실제 글줄도 검사한다. */
    private fun assertWholeText(app: RotationHarness, tag: String) {
        val node = assertWholeBounds(app, tag)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("$tag 글줄 결과 없음", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("$tag 글자 높이 잘림", layout.didOverflowHeight)
            assertFalse("$tag 글자 폭 잘림: 측정=${layout.size.width}, 문단=${layout.multiParagraph.width}", layout.didOverflowWidth)
        }
    }

    @Test fun drivingLock_keepsSpeedStateAndCompleteUnlockHintVisible() {
        RotationHarness(rule).use { app ->
            app.start()
            app.vm.settings { it.copy(flags = Flag.entries.toSet(), consents = Consent.entries.toSet()) }
            app.vm.act { _, _ -> startValet(); emptyList() }
            app.vm.act { signals, now -> confirmValetConsent(signals, now) }
            app.vm.sim { it.copy(gear = Gear.D, speedKmh = 35f, place = Place.OTHER) }
            rule.waitForIdle()
            fun check() {
                rule.onNodeWithTag("restricted").assertIsDisplayed()
                rule.onNodeWithTag("restricted_speed").assertTextEquals("35")
                rule.onNodeWithText("조작 잠김", substring = true).assertIsDisplayed()
                assertWholeText(app, "restricted_speed")
                assertWholeText(app, "restricted_unlock_hint")
                app.vm.ui.value.drivingLines.take(3).indices.forEach { index ->
                    assertWholeBounds(app, "restricted_status_$index")
                }
                listOf("HOME", "CONTROL", "HISTORY", "MYPAGE").forEach { route ->
                    rule.onNodeWithTag("nav_$route").assertDoesNotExist()
                }
                rule.runOnIdle {
                    assertTrue(app.vm.ui.value.signals.uxRestricted)
                    assertEquals(ValetPhase.DRIVING, app.vm.ui.value.engine.valet.phase)
                    assertEquals(35f, app.vm.ui.value.signals.speedKmh, 0.01f)
                    assertTrue(app.vm.ui.value.drivingLines.any { "대리" in it })
                }
            }
            app.shot("modules_driving_portrait_before")
            check()
            app.rotate(800, 360)
            check()
            app.shot("modules_driving_landscape")
            app.rotate(360, 800)
            check()
            app.recreate()
            check()
            app.shot("modules_driving_portrait_restored")
        }
    }

    @Test fun valetConsent_keepsExpandedGuideAcrossDifferentPhoneAndCarLayouts() {
        RotationHarness(rule).use { app ->
            app.start()
            app.vm.act { _, _ -> startValet(); emptyList() }
            app.go("VALET")
            rule.onNodeWithTag("valet_guide_more").performScrollTo().performClick()
            roundTrip(app) {
                rule.onNodeWithTag("valet_guide_more").assertTextContains("동의·인수·도착 예정·실제 도착 시각", substring = true)
                rule.runOnIdle { assertEquals(ValetPhase.CONSENT, app.vm.ui.value.engine.valet.phase) }
            }
            rule.onNodeWithTag("valet_guide_more").performScrollTo().performClick()
            rule.onNodeWithTag("valet_guide_more").assertTextEquals("기록하는 항목 자세히 보기 ›")
        }
    }

    @Test fun driver_keepsSelectedModePaneAfterRoundTripAndRecreation() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("DRIVER")
            rule.onNodeWithTag("pane_1").performClick()
            rule.onNodeWithText("운전자 모드 설정").assertExists()
            app.rotate(800, 360)
            rule.onNodeWithText("운전자 모드 설정").assertExists()
            app.rotate(360, 800)
            rule.onNodeWithTag("pane_1").assertIsSelected()
            app.recreate()
            rule.onNodeWithTag("pane_1").assertIsSelected()
            rule.onNodeWithText("운전자 모드 설정").assertExists()
        }
    }

    @Test fun comfort_keepsSpouseSelectionAndEditsOnlySpousePreset() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go("COMFORT")
            val ownerTemp = app.vm.ui.value.settings.comfort.getValue("차주").tempC
            val spouseTemp = app.vm.ui.value.settings.comfort.getValue("배우자").tempC
            rule.onNodeWithText("배우자 설정").performScrollTo().performClick()
            roundTrip(app) {
                rule.onNodeWithText("배우자 설정").assertIsSelected()
            }
            rule.onNodeWithContentDescription("온도 1도 높이기").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(spouseTemp + 1f, app.vm.ui.value.settings.comfort.getValue("배우자").tempC, 0.01f)
                assertEquals(ownerTemp, app.vm.ui.value.settings.comfort.getValue("차주").tempC, 0.01f)
            }
            app.rotate(800, 360)
            app.rotate(360, 800)
            rule.onNodeWithText("배우자 설정").assertIsSelected()
        }
    }

    /** 하차 점검의 두 칸이 세로(위아래)↔가로(좌우)로 바뀌어도 저장 전 주차 메모가 남는다. */
    @Test fun trunkParkingNote_keepsUnsavedDraftAcrossLayoutChange() {
        RotationHarness(rule).use { app ->
            app.start()
            app.go(Module.TRUNK.name)
            rule.onNodeWithTag("parking_input").performScrollTo().performTextInput("B2 C구역 32번")
            roundTrip(app) {
                rule.onNodeWithTag("parking_input").performScrollTo().assertTextContains("B2 C구역 32번")
            }
            assertEquals("저장 버튼을 누르기 전에는 설정에 쓰지 않는다", "", app.vm.ui.value.settings.parkingNote)
        }
    }

    /** 가로에서 PIN을 정한 뒤 세로로 돌려 보안 칸이 탭 뒤로 숨어도 한 번뿐인 복구 코드는 닫을 때까지 보인다. */
    @Test fun pinRecoveryCode_staysUntilDismissedAcrossLayoutChangeAndRecreation() {
        RotationHarness(rule).use { app ->
            app.start()
            app.rotate(800, 360)
            app.go("SETTINGS")
            // 가로 폰(높이 360dp)에서는 숫자 패드가 창보다 크다. 패드를 스크롤해 아래 줄까지 실제로 눌러 본다
            fun press(tag: String) = rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.onNodeWithText("PIN 정하기").performScrollTo().performClick()
            repeat(2) {
                "2468".forEach { d -> press("pin_$d") }
                press("pin_확인")
                rule.waitForIdle()
            }
            assertTrue(app.vm.ui.value.ownerPinSet)
            rule.onNodeWithTag("recovery_code").assertExists()
            app.rotate(360, 800)
            rule.onNodeWithTag("pane_0").assertIsSelected()
            rule.onNodeWithTag("recovery_code").assertExists()
            app.recreate()
            rule.onNodeWithTag("recovery_code").assertExists()
            rule.onNodeWithText("적어 두었어요").performSemanticsAction(SemanticsActions.OnClick)
            rule.waitForIdle()
            rule.onNodeWithTag("recovery_code").assertDoesNotExist()
        }
    }

    /** 차주 잠금으로 닫힌 차주 화면은 PIN을 넣고 다시 열면 펼쳐 둔 내용을 이어 보여 준다(사진 결과·입력을 잃지 않게). */
    @Test fun ownerLock_keepsClosedScreenStateUntilOwnerReopensIt() {
        RotationHarness(rule).use { app ->
            app.start()
            app.vm.setOwnerPin("2468")
            app.go("SETTINGS")
            val label = com.verified.incar.core.Persona.entries.first().label
            rule.onNodeWithTag("set_persona").performScrollTo().performClick()
            rule.onNodeWithText(label).assertExists()
            app.vm.lockOwner()
            rule.waitForIdle()
            rule.onNodeWithTag("set_persona").assertDoesNotExist()
            app.go("SETTINGS")
            assertTrue("잠긴 화면은 PIN을 거친다", app.vm.ownerRequest.value != null)
            assertEquals(null, app.vm.unlockOwner("2468"))
            rule.waitForIdle()
            rule.onNodeWithTag("set_persona").assertExists()
            rule.onNodeWithText(label).assertExists()
        }
    }
}
