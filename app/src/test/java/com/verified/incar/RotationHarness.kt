package com.verified.incar

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.DisplayMetrics
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.junit.Assert.assertEquals
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import java.io.File

/** Manifest의 configChanges를 포함한 실제 MainActivity에 실행 중 구성 변경을 전달한다. */
class RotationHarness(private val rule: ComposeTestRule) : AutoCloseable {
    val vm = (RuntimeEnvironment.getApplication() as VerifiedApp).runtime
    private val controller = Robolectric.buildActivity(MainActivity::class.java)
    val activity get() = controller.get()

    fun start() {
        vm.settings { it.copy(personas = com.verified.incar.core.Persona.entries.toSet(), carModel = com.verified.incar.core.CarModel.GRANDEUR) }
        controller.setup()
        rule.waitForIdle()
    }

    fun rotate(width: Int, height: Int, night: Boolean = false, smallestWidth: Int = 360) {
        rule.waitForIdle()
        val old = Configuration(activity.resources.configuration)
        RuntimeEnvironment.setQualifiers("sw${smallestWidth}dp-w${width}dp-h${height}dp-${if (width > height) "land" else "port"}-${if (night) "night" else "notnight"}-mdpi")
        RuntimeEnvironment.setFontScale(old.fontScale)
        val resources = RuntimeEnvironment.getApplication().resources
        val next = Configuration(resources.configuration)
        val metrics = DisplayMetrics().apply { setTo(resources.displayMetrics) }
        controller.configurationChange(next, metrics, old.diff(next))
        rule.waitForIdle()
        assertEquals("회전 후 실제 창 너비", width, activity.window.decorView.width)
        assertEquals("회전 후 실제 창 높이", height, activity.window.decorView.height)
    }

    fun recreate() {
        controller.recreate()
        rule.waitForIdle()
    }

    fun go(route: String) {
        vm.navigate.value = route
        rule.waitForIdle()
    }

    fun shot(name: String) {
        rule.waitForIdle()
        val view = activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        File("../screenshots/rotation").apply { mkdirs() }.resolve("$name.png").outputStream().use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bmp.recycle()
    }

    override fun close() {
        controller.pause().stop().destroy()
        vm.close()
        com.verified.incar.ui.C.forceDark = null
    }
}
