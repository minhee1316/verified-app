package com.verified.incar

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.verified.incar.core.CarModel
import com.verified.incar.ui.Car3DScreen
import com.verified.incar.ui.TRIAL_MODELS
import com.verified.incar.ui.car3d
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-port-mdpi")
class Car3DTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyTrialModelHasA3dModelWithCredit() {
        TRIAL_MODELS.forEach { m -> assertNotNull(m.name, car3d(m)?.credit?.takeIf { it.isNotBlank() }) }
        assertTrue(car3d(CarModel.OTHER) == null)
    }

    /** 모델 파일이 없는 빌드(테스트·release)에서는 3D 대신 사진과 안내를 보여 준다. */
    @Test fun withoutModelFilesShowsPhotoFallback() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        rule.setContent { MaterialTheme { Car3DScreen(vm.ui.value) } }
        rule.onNodeWithText("이 기기에서는 3D를 열 수 없어 사진으로 보여 드려요.").assertIsDisplayed()
        rule.onNodeWithTag("car3d_TUCSON").performClick()
        rule.onNodeWithText("2022 Hyundai Tucson L 270T", substring = true).assertIsDisplayed()
    }
}
