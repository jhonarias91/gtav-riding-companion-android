package com.fariddroid.gtbikecompanion.ui.format

import androidx.annotation.StringRes
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.demo.DemoScenario
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.BindingLayer
import com.fariddroid.gtbikecompanion.core.model.ModLayer
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.ui.components.StatusTone

/** Enum -> localized label. Server codes are never shown raw. */
object Labels {
    @StringRes fun scenario(s: DemoScenario): Int = when (s) {
        DemoScenario.LIVE_CYCLING -> R.string.scenario_live_cycling
        DemoScenario.READY_NO_SENSORS -> R.string.scenario_ready_no_sensors
        DemoScenario.STALE_ADAPTER -> R.string.scenario_stale_adapter
        DemoScenario.ADAPTER_ABSENT -> R.string.scenario_adapter_absent
        DemoScenario.UNSUPPORTED_MOD -> R.string.scenario_unsupported_mod
    }

    fun adapter(adapter: AdapterLayer, binding: BindingLayer): Pair<Int, StatusTone> = when {
        adapter == AdapterLayer.ABSENT -> R.string.state_absent to StatusTone.BAD
        binding == BindingLayer.INCOMPATIBLE -> R.string.state_incompatible to StatusTone.BAD
        adapter == AdapterLayer.STALE -> R.string.state_stale to StatusTone.WARN
        else -> R.string.state_fresh to StatusTone.GOOD
    }

    fun mod(mod: ModLayer): Pair<Int, StatusTone> = when (mod) {
        ModLayer.INACTIVE -> R.string.state_mod_inactive to StatusTone.NEUTRAL
        ModLayer.INITIALIZING -> R.string.state_mod_initializing to StatusTone.WARN
        ModLayer.DATA_READY -> R.string.state_mod_ready to StatusTone.GOOD
        ModLayer.STOPPING -> R.string.state_mod_stopping to StatusTone.WARN
        ModLayer.ERROR -> R.string.state_error to StatusTone.BAD
        ModLayer.UNKNOWN -> R.string.state_unknown to StatusTone.NEUTRAL
    }

    fun receiver(state: ReceiverState): Pair<Int, StatusTone> = when (state) {
        ReceiverState.UNINITIALIZED -> R.string.state_receiver_uninitialized to StatusTone.NEUTRAL
        ReceiverState.INITIALIZED, ReceiverState.STARTED, ReceiverState.COMPLETE -> R.string.state_receiver_starting to StatusTone.WARN
        ReceiverState.READY -> R.string.state_receiver_ready to StatusTone.GOOD
        ReceiverState.STOPPING -> R.string.state_receiver_stopping to StatusTone.WARN
        ReceiverState.STOPPED -> R.string.state_receiver_stopped to StatusTone.NEUTRAL
        ReceiverState.ERROR -> R.string.state_error to StatusTone.BAD
        ReceiverState.UNKNOWN -> R.string.state_unknown to StatusTone.NEUTRAL
    }

    @StringRes fun sensorRole(role: SensorRoleType): Int = when (role) {
        SensorRoleType.POWER -> R.string.sensor_power
        SensorRoleType.RESISTANCE -> R.string.sensor_resistance
        SensorRoleType.CADENCE -> R.string.sensor_cadence
        SensorRoleType.HEART_RATE -> R.string.sensor_heart_rate
        SensorRoleType.STEERING -> R.string.sensor_steering
        SensorRoleType.CONTROLS -> R.string.sensor_controls
        SensorRoleType.RUNNING_SPEED -> R.string.sensor_running_speed
    }

    /** Shortened device id for display (full id stays in the model). */
    fun shortDeviceId(id: String): String = if (id.length <= 6) id else "…" + id.takeLast(6)
}
