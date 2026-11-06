package com.fariddroid.gtbikecompanion

import com.fariddroid.gtbikecompanion.core.demo.DemoScenarios
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.ChannelState
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.feature.training.TrainingData
import com.fariddroid.gtbikecompanion.feature.training.TrainingUiState
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.feature.activities.ActivitiesData
import com.fariddroid.gtbikecompanion.feature.activities.ActivitiesUiState
import com.fariddroid.gtbikecompanion.feature.common.OperationUi
import com.fariddroid.gtbikecompanion.feature.devices.DevicesUiState
import com.fariddroid.gtbikecompanion.feature.routes.RoutesData
import com.fariddroid.gtbikecompanion.feature.routes.RoutesUiState
import com.fariddroid.gtbikecompanion.feature.settings.PcSettingsUiState
import com.fariddroid.gtbikecompanion.feature.settings.SettingsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression (6 Nov 2026, blocks 6 and 7 of PRUEBAS_COMPLETAS): with the PC ready and every capability available,
 * Routes and Profile showed "The game adapter is not running on the PC" on every control, because the screens turned
 * an allowed action (null) into ADAPTER_ABSENT. Each screen state must pass an allowed action through.
 */
class ControlScreensStateTest {
    private val ready = DemoScenarios.liveCycling(0).copy(
        origin = DataOrigin.BRIDGE,
        channel = ChannelState.READY,
        capabilities = CapabilityId.entries.map { Capability(it, true, null, "help") },
    )

    @Test fun routesControlsAreEnabledWhenThePcAllowsThem() {
        val state = RoutesUiState(RoutesData(), ready, UnitSystem.METRIC, null)
        assertNull(state.applyBlock)
        assertNull(state.openPanelBlock)
        assertNull(state.previewBlock)
    }

    @Test fun profileAndRideControlsAreEnabledWhenThePcAllowsThem() {
        val state = PcSettingsUiState(SettingsData(), ready, UnitSystem.METRIC, null)
        assertNull(state.profileBlock)
        assertNull(state.rideBlock)
        assertNull(state.appearanceBlock)
    }

    @Test fun devicesAndSaveAreEnabledWhenThePcAllowsThem() {
        assertNull(DevicesUiState(null, null, ready, null).block)
        assertNull(ActivitiesUiState(ActivitiesData(), ready, UnitSystem.METRIC, null).saveBlock)
    }

    @Test fun withoutAFeedEveryControlSaysTheAdapterIsAbsent() {
        assertEquals("ADAPTER_ABSENT", RoutesUiState(RoutesData(), null, UnitSystem.METRIC, null).applyBlock)
        assertEquals("ADAPTER_ABSENT", PcSettingsUiState(SettingsData(), null, UnitSystem.METRIC, null).profileBlock)
        assertEquals("ADAPTER_ABSENT", DevicesUiState(null, null, null, null).block)
    }

    @Test fun theBridgeReasonIsShownWhenACapabilityIsUnavailable() {
        val panelClosed = ready.copy(capabilities = ready.capabilities.map {
            if (it.id == CapabilityId.ROUTES_PREVIEW) Capability(it.id, false, "PANEL_CLOSED", it.helpId) else it
        })
        val state = RoutesUiState(RoutesData(), panelClosed, UnitSystem.METRIC, null)
        assertEquals("PANEL_CLOSED", state.previewBlock)
        assertNull(state.applyBlock)
    }

    @Test fun workoutSelectFollowsThePcCapability() {
        val progressNone = com.fariddroid.gtbikecompanion.core.model.WorkoutProgress.NONE
        assertNull(TrainingUiState(TrainingData(), progressNone, ready, UnitSystem.METRIC).selectBlock)
        val running = ready.copy(capabilities = ready.capabilities.map {
            if (it.id == CapabilityId.WORKOUTS_SELECT) Capability(it.id, false, "WORKOUT_PC_CONTROL_REQUIRED", it.helpId) else it
        })
        assertEquals("WORKOUT_PC_CONTROL_REQUIRED", TrainingUiState(TrainingData(), progressNone, running, UnitSystem.METRIC).selectBlock)
        assertEquals("ADAPTER_ABSENT", TrainingUiState(TrainingData(), progressNone, null, UnitSystem.METRIC).selectBlock)
    }

    @Test fun aRunningWorkoutIsNamedSoTheDialogCanWarn() {
        val running = com.fariddroid.gtbikecompanion.core.model.WorkoutProgress(true, "Sweet Spot", true, false, false, null, 600.0, 1200.0, 2, null)
        assertEquals("Sweet Spot", TrainingUiState(TrainingData(), running, ready, UnitSystem.METRIC).runningWorkoutName)
        assertNull(TrainingUiState(TrainingData(), running.copy(finished = true), ready, UnitSystem.METRIC).runningWorkoutName)
        assertNull(TrainingUiState(TrainingData(), com.fariddroid.gtbikecompanion.core.model.WorkoutProgress.NONE, ready, UnitSystem.METRIC).runningWorkoutName)
        assertNull("still selectable while it runs", TrainingUiState(TrainingData(), running, ready, UnitSystem.METRIC).selectBlock)
    }

    @Test fun applyRouteStaysEnabledDuringAWorkoutAndWarns() {
        val snapshot = ready.snapshot.copy(workout = com.fariddroid.gtbikecompanion.core.model.WorkoutGuard(present = true, finished = false))
        val state = RoutesUiState(RoutesData(), ready.copy(snapshot = snapshot), UnitSystem.METRIC, null)
        assertNull(state.applyBlock)
        assertEquals(true, state.workoutRunning)
        assertEquals(false, RoutesUiState(RoutesData(), ready, UnitSystem.METRIC, null).workoutRunning)
    }

    @Test fun selectWorkoutOnlyCarriesACatalogId() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { Command.SelectWorkout("../plan", "workouts-1") }
        assertEquals("workouts.select", Command.SelectWorkout("w-0123456789abcdef", "workouts-1").wireType)
    }

    @Test fun anOperationInFlightBlocksTheOtherSettings() {
        val state = PcSettingsUiState(SettingsData(), ready, UnitSystem.METRIC, OperationUi.Waiting)
        assertEquals("OPERATION_IN_PROGRESS", state.rideBlock)
    }
}
