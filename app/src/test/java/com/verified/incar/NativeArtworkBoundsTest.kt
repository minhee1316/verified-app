package com.verified.incar

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.verified.incar.ui.CarTopView
import com.verified.incar.ui.DigitalKeyArtwork
import com.verified.incar.ui.GaugeTrack
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise drawing as well as composition: shader errors happen when the view draws. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "sw360dp-w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeArtworkBoundsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var runtime: VerifiedRuntime? = null

    @After fun close() { runtime?.close() }

    private fun draw() {
        rule.waitForIdle()
        rule.runOnIdle {
            val root = rule.activity.window.decorView
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            try { root.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
        }
    }

    @Test fun gaugeDrawsAtTinyAndZeroSizesWithoutReversedStrokeBounds() {
        rule.setContent {
            MaterialTheme {
                Column {
                    GaugeTrack(0.5f, Modifier.size(4.dp).testTag("tiny_gauge"))
                    GaugeTrack(0.5f, Modifier.size(1.dp))
                    GaugeTrack(0.5f, Modifier.size(0.dp).testTag("zero_gauge"))
                    GaugeTrack(Float.NaN, Modifier.size(64.dp), marker = Float.POSITIVE_INFINITY)
                }
            }
        }
        rule.onNodeWithTag("tiny_gauge").assertWidthIsEqualTo(4.dp)
        rule.onNodeWithTag("zero_gauge").assertWidthIsEqualTo(0.dp)
        draw()
    }

    @Test fun digitalKeyDrawsAtZeroWidthAndAfterWidthReturns() {
        val width = mutableStateOf(0.dp)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(width.value)) { DigitalKeyArtwork("더 뉴 그랜저") }
            }
        }
        rule.onNodeWithTag("key_art").assertWidthIsEqualTo(0.dp)
        draw()
        rule.runOnIdle { width.value = 320.dp }
        rule.onNodeWithTag("key_art").assertWidthIsEqualTo(320.dp)
        rule.onNodeWithTag("key_car_name").assertExists()
        draw()
    }

    @Test fun carDiagramAvoidsNaNOffsetsAtZeroWidthAndRestoresItsControls() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        runtime = vm
        val width = mutableStateOf(0.dp)
        rule.setContent {
            MaterialTheme {
                Box(Modifier.width(width.value)) { CarTopView(vm, vm.ui.value, interior = false) }
            }
        }
        rule.onNodeWithTag("topview_exterior").assertWidthIsEqualTo(0.dp)
        rule.onNodeWithTag("topview_lock").assertDoesNotExist()
        draw()
        rule.runOnIdle { width.value = 320.dp }
        rule.onNodeWithTag("topview_exterior").assertWidthIsEqualTo(320.dp)
        rule.onNodeWithTag("topview_lock").assertExists()
        rule.onNodeWithTag("topview_trunk").assertExists()
        draw()
    }
}
