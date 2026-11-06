package com.fariddroid.gtbikecompanion.core.demo

import com.fariddroid.gtbikecompanion.core.model.ActiveRoute
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.BindingLayer
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.ChannelState
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.GameControl
import com.fariddroid.gtbikecompanion.core.model.Layers
import com.fariddroid.gtbikecompanion.core.model.Metric
import com.fariddroid.gtbikecompanion.core.model.MetricSource
import com.fariddroid.gtbikecompanion.core.model.Metrics
import com.fariddroid.gtbikecompanion.core.model.ModLayer
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.model.SensorRole
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import com.fariddroid.gtbikecompanion.core.model.Sport
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.model.TelemetrySnapshot
import com.fariddroid.gtbikecompanion.core.model.WorkoutGuard
import kotlin.math.sin

/**
 * Synthetic scenarios named after the contract fixtures (gtav-riding-companion-bridge/bridge/fixtures/v1).
 * Always emitted with DataOrigin.DEMO. Not captured from a real GTBike V session.
 */
enum class DemoScenario(val wireName: String) {
    LIVE_CYCLING("live_cycling"),
    READY_NO_SENSORS("ready_no_sensors"),
    STALE_ADAPTER("stale_adapter"),
    ADAPTER_ABSENT("adapter_absent"),
    UNSUPPORTED_MOD("unsupported_mod"),
}

object DemoScenarios {
    /** Demo-only FTP so the gauge can show zones. Real FTP comes from profile.read (P4). */
    const val DEMO_FTP_W = 250

    private val demoCapabilities = listOf(
        Capability(CapabilityId.METRICS_READ, true, null, "connection.status"),
        Capability(CapabilityId.SENSORS_READ, true, null, "devices.policy"),
        Capability(CapabilityId.ACTIVITY_READ, true, null, "metrics.movingTime"),
        Capability(CapabilityId.MOD_SET_ACTIVE, false, "DEMO_MODE", "mod.activate"),
    )

    private fun noSensor(role: SensorRoleType) = SensorRole(role, false, SensorSource.UNKNOWN, null, "unknown")

    private val liveSensors = listOf(
        SensorRole(SensorRoleType.POWER, true, SensorSource.ANT, "54321", "FEC"),
        SensorRole(SensorRoleType.RESISTANCE, true, SensorSource.ANT, "54321", "FEC"),
        SensorRole(SensorRoleType.CADENCE, true, SensorSource.ANT, "54321", "FEC"),
        SensorRole(SensorRoleType.HEART_RATE, true, SensorSource.BLE, "18446744073709551615", "HR"),
        noSensor(SensorRoleType.STEERING),
        noSensor(SensorRoleType.CONTROLS),
        noSensor(SensorRoleType.RUNNING_SPEED),
    )

    /** Live ride at [tick] seconds; deterministic so tests and screenshots are reproducible. */
    fun liveCycling(tick: Long): TelemetryFeed {
        val t = tick.toDouble()
        val power = (185 + 35 * sin(t / 6.0) + 12 * sin(t / 1.7)).coerceAtLeast(0.0)
        val speed = 8.5 + 0.8 * sin(t / 9.0)
        val distance = 1050.4 + 8.5 * t
        val riding = 180_000L + tick * 1000
        val metrics = Metrics(
            powerW = Metric.valid(power.roundTo(0), MetricSource.ANT),
            speedMps = Metric.valid(speed.roundTo(2), MetricSource.VIRTUAL),
            cadenceRpm = Metric.valid((88 + 4 * sin(t / 4.0)).roundTo(0), MetricSource.ANT),
            heartRateBpm = Metric.valid((134 + 6 * sin(t / 20.0)).roundTo(0), MetricSource.BLE),
            distanceM = Metric.valid(distance.roundTo(1), MetricSource.GAME),
            ridingTimeMs = Metric.valid(riding.toDouble(), MetricSource.GAME),
            movingTimeMs = Metric.valid((riding - 4_000).toDouble(), MetricSource.GAME),
            elevationGainM = Metric.valid((12.4 + 0.05 * t).roundTo(1), MetricSource.GAME),
            elevationLossM = Metric.valid(3.1, MetricSource.GAME),
            slopeFraction = Metric.valid((0.021 + 0.015 * sin(t / 15.0)).roundTo(3), MetricSource.GAME),
            windSpeedMps = Metric.valid(1.4, MetricSource.GAME),
            windDirRad = Metric.valid(1.2, MetricSource.GAME),
        )
        return feed(
            TelemetrySnapshot(
                adapterInstanceId = "demo-adapter", sequence = 4217 + tick, stateRevision = "demo-state-12",
                snapshotAgeMs = 180, sessionGeneration = "demo-session-3", modVersion = "0.8.0.4",
                bindingProfile = "gtbikev-0804",
                layers = Layers(AdapterLayer.FRESH, BindingLayer.COMPATIBLE, ModLayer.DATA_READY,
                    ReceiverState.READY, ReceiverState.READY, GameControl.IN_CONTROL),
                sport = Sport.CYCLING, metrics = metrics, sensors = liveSensors,
                workout = WorkoutGuard(present = false, finished = null),
                activeRoute = ActiveRoute("35", loop = true, reverse = false),
            ),
        )
    }

    fun readyNoSensors(): TelemetryFeed {
        val missing = Metric.unavailable("SENSOR_MISSING")
        val zero = { s: MetricSource -> Metric.valid(0.0, s) }
        val metrics = Metrics(
            powerW = missing, speedMps = zero(MetricSource.VIRTUAL), cadenceRpm = missing, heartRateBpm = missing,
            distanceM = zero(MetricSource.GAME), ridingTimeMs = zero(MetricSource.GAME), movingTimeMs = zero(MetricSource.GAME),
            elevationGainM = zero(MetricSource.GAME), elevationLossM = zero(MetricSource.GAME), slopeFraction = zero(MetricSource.GAME),
            windSpeedMps = Metric.unavailable("NOT_CAPTURED"), windDirRad = Metric.unavailable("NOT_CAPTURED"),
        )
        return feed(
            TelemetrySnapshot(
                adapterInstanceId = "demo-adapter", sequence = 12, stateRevision = "demo-state-2", snapshotAgeMs = 240,
                sessionGeneration = "demo-session-1", modVersion = "0.8.0.4", bindingProfile = "gtbikev-0804",
                layers = Layers(AdapterLayer.FRESH, BindingLayer.COMPATIBLE, ModLayer.DATA_READY,
                    ReceiverState.READY, ReceiverState.STARTED, GameControl.IN_CONTROL),
                sport = Sport.UNKNOWN, metrics = metrics,
                sensors = SensorRoleType.entries.map(::noSensor),
                workout = WorkoutGuard(present = false, finished = null), activeRoute = null,
            ),
        )
    }

    fun staleAdapter(): TelemetryFeed {
        val last = liveCycling(0).snapshot
        return feed(
            last.copy(
                snapshotAgeMs = 7_400,
                layers = Layers(AdapterLayer.STALE, BindingLayer.COMPATIBLE, ModLayer.UNKNOWN,
                    ReceiverState.UNKNOWN, ReceiverState.UNKNOWN, GameControl.UNKNOWN),
                metrics = last.metrics.map { it.asStale() },
                sensors = emptyList(), workout = WorkoutGuard(null, null), activeRoute = null,
            ),
            channel = ChannelState.STALE,
        )
    }

    fun adapterAbsent(): TelemetryFeed = feed(
        TelemetrySnapshot(
            adapterInstanceId = null, sequence = null, stateRevision = null, snapshotAgeMs = null,
            sessionGeneration = null, modVersion = null, bindingProfile = null,
            layers = Layers(AdapterLayer.ABSENT, BindingLayer.UNKNOWN, ModLayer.UNKNOWN,
                ReceiverState.UNKNOWN, ReceiverState.UNKNOWN, GameControl.UNKNOWN),
            sport = Sport.UNKNOWN, metrics = Metrics.allUnavailable("ADAPTER_ABSENT"), sensors = emptyList(),
            workout = WorkoutGuard(null, null), activeRoute = null,
        ),
        channel = ChannelState.WAITING_FOR_ADAPTER,
    )

    fun unsupportedMod(): TelemetryFeed = feed(
        TelemetrySnapshot(
            adapterInstanceId = "demo-adapter-9e02", sequence = 3, stateRevision = "demo-state-1", snapshotAgeMs = 200,
            sessionGeneration = null, modVersion = "0.8.1.0", bindingProfile = null,
            layers = Layers(AdapterLayer.FRESH, BindingLayer.INCOMPATIBLE, ModLayer.UNKNOWN,
                ReceiverState.UNKNOWN, ReceiverState.UNKNOWN, GameControl.UNKNOWN),
            sport = Sport.UNKNOWN, metrics = Metrics.allUnavailable("UNSUPPORTED_VERSION"), sensors = emptyList(),
            workout = WorkoutGuard(null, null), activeRoute = null,
        ),
        channel = ChannelState.INCOMPATIBLE,
        capabilities = listOf(
            Capability(CapabilityId.METRICS_READ, false, "UNSUPPORTED_VERSION", "connection.compatibility"),
            Capability(CapabilityId.MOD_SET_ACTIVE, false, "UNSUPPORTED_VERSION", "connection.compatibility"),
        ),
    )

    fun snapshotFor(scenario: DemoScenario, tick: Long): TelemetryFeed = when (scenario) {
        DemoScenario.LIVE_CYCLING -> liveCycling(tick)
        DemoScenario.READY_NO_SENSORS -> readyNoSensors()
        DemoScenario.STALE_ADAPTER -> staleAdapter()
        DemoScenario.ADAPTER_ABSENT -> adapterAbsent()
        DemoScenario.UNSUPPORTED_MOD -> unsupportedMod()
    }

    private fun feed(
        snapshot: TelemetrySnapshot,
        channel: ChannelState = ChannelState.DEMO,
        capabilities: List<Capability> = demoCapabilities,
    ) = TelemetryFeed(DataOrigin.DEMO, channel, snapshot, capabilities)

    private fun Double.roundTo(decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        return kotlin.math.round(this * factor) / factor
    }
}
