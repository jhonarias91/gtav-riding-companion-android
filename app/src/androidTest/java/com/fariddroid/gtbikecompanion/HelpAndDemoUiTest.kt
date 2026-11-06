package com.fariddroid.gtbikecompanion

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fariddroid.gtbikecompanion.core.demo.DemoScenario
import com.fariddroid.gtbikecompanion.core.demo.DemoScenarios
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.feature.ride.RideScreen
import com.fariddroid.gtbikecompanion.feature.ride.RideUiState
import com.fariddroid.gtbikecompanion.ui.theme.CompanionTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compose checks from P1 acceptance. Runs on the phone: gradlew connectedDebugAndroidTest. */
@RunWith(AndroidJUnit4::class)
class HelpAndDemoUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun showRide(scenario: DemoScenario) {
        rule.setContent {
            CompanionTheme {
                RideScreen(RideUiState(DemoScenarios.snapshotFor(scenario, 0), UnitSystem.METRIC, scenario))
            }
        }
    }

    @Test fun demoBannerIsAlwaysShown() {
        showRide(DemoScenario.LIVE_CYCLING)
        rule.onNodeWithTag("demoBanner").assertIsDisplayed()
    }

    @Test fun disabledModSwitchStillHasWorkingHelp() {
        showRide(DemoScenario.LIVE_CYCLING)
        rule.onNodeWithTag("modSwitch").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("help:mod.deactivate").performScrollTo().performClick()
        rule.onNodeWithTag("helpSheet").assertIsDisplayed()
        rule.onNodeWithTag("helpClose").performClick()
        rule.waitForIdle()
        // Closing help changes nothing: the switch is still there and still disabled.
        rule.onNodeWithTag("modSwitch").assertIsNotEnabled()
    }

    @Test fun staleAndAbsentScenariosRender() {
        showRide(DemoScenario.STALE_ADAPTER)
        rule.onNodeWithTag("help:connection.stale").assertIsDisplayed()
    }
}
