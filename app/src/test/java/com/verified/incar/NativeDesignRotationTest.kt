package com.verified.incar

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.verified.incar.core.DailyStat
import com.verified.incar.core.VerdictEngine
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 글자를 그림과 분리한 컴포넌트가 150% 글씨·320dp·회전에서도 실제로 잘리지 않는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw320dp-w320dp-h640dp-port-night-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeDesignRotationTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun wholeText(node: SemanticsNodeInteraction) {
        node.performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("글자 가로 잘림: ${it.layoutInput.text}", it.didOverflowWidth)
            assertFalse("글자 세로 잘림: ${it.layoutInput.text}", it.didOverflowHeight)
        }
    }

    @Test fun instrumentsAndChart_keepTextAndSelectionAcrossRotation() {
        RuntimeEnvironment.setFontScale(1.5f)
        RotationHarness(rule).use { app ->
            app.start()
            app.go("SCHOOL")
            wholeText(rule.onNodeWithText("재출발 최고 속도"))
            app.shot("native_school_large_portrait")
            app.rotate(640, 320, night = true, smallestWidth = 320)
            wholeText(rule.onNodeWithText("재출발 최고 속도"))
            app.shot("native_school_large_landscape")

            app.go("KEY")
            wholeText(rule.onNodeWithTag("key_car_name"))
            app.rotate(320, 640, night = true, smallestWidth = 320)
            wholeText(rule.onNodeWithTag("key_car_name"))
            app.shot("native_key_large_portrait")

            val today = LocalDate.parse(VerdictEngine.date(app.vm.ui.value.nowMs))
            val chosen = today.minusDays(6)
            app.vm.act { _, _ ->
                loadStats(listOf(DailyStat(chosen.toString(), km = 37.8), DailyStat(today.toString(), km = 0.0)))
                emptyList()
            }
            app.go("INSIGHT")
            val day = "insight_day_$chosen"
            rule.onNodeWithTag(day).performScrollTo().performClick().assertIsSelected()
            val bounds = rule.onNodeWithTag(day).fetchSemanticsNode().boundsInRoot
            assertTrue("날짜 누를 영역이 너무 작음", bounds.width >= 48f)
            app.shot("native_insight_large_portrait")
            app.rotate(640, 320, night = true, smallestWidth = 320)
            rule.onNodeWithTag(day).performScrollTo().assertIsSelected()
            app.rotate(320, 640, night = true, smallestWidth = 320)
            app.recreate()
            rule.onNodeWithTag(day).performScrollTo().assertIsSelected()
            rule.onAllNodesWithText("%,.1f km".format(37.8)).onFirst().assertExists()
        }
    }
}
