package com.verified.incar

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.verified.incar.core.Module
import com.verified.incar.ui.MyPageMH
import com.verified.incar.ui.SituationDirectory
import com.verified.incar.ui.VehicleExplorer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-mdpi")
class DemoDiscoveryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun walkthrough_withoutRegistrationLinksToControlValetAndRecords() {
        val routes = mutableListOf<String>()
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SituationDirectory(UiState(simAllowed = true), Module.entries) { routes.add(it) }
                }
            }
        }
        rule.onNodeWithTag("demo_walkthrough").assertExists()
        listOf("CONTROL", "VALET", "HISTORY").forEach { route ->
            rule.onNodeWithTag("demo_step_$route").performScrollTo().performClick()
        }
        rule.onNodeWithTag("demo_models").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("CONTROL", "VALET", "HISTORY", "TRIAL"), routes) }
    }

    @Test fun exampleProfile_opensExampleChooserWithoutRegistration() {
        var route: String? = null
        rule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MyPageMH(UiState(simAllowed = true), Module.entries) { route = it }
                }
            }
        }
        rule.onNodeWithText("등록 없이 둘러보세요").assertExists()
        rule.onNodeWithTag("more_profile").performClick()
        rule.runOnIdle { assertEquals("TRIAL", route) }
    }

    @Test fun explorer_withoutRegisteredModelStillShowsExampleVehicle() {
        val vm = VerifiedRuntime(RuntimeEnvironment.getApplication(), simTicker = false)
        try {
            rule.setContent {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        VehicleExplorer(vm, UiState(simAllowed = true), {})
                    }
                }
            }
            rule.onNodeWithTag("vehicle_photo").assertExists()
            rule.onNodeWithTag("explorer_register").assertDoesNotExist()
            listOf("GRANDEUR", "AVANTE", "TUCSON", "IONIQ3").forEach {
                rule.onNodeWithTag("explorer_model_$it").assertExists()
            }
        } finally { vm.close() }
    }
}
