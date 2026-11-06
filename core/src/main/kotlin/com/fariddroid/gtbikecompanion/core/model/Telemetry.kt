package com.fariddroid.gtbikecompanion.core.model

/**
 * Domain mirror of contract v1 `state.schema.json`. Network DTOs (P2, bridge-client) map into these types;
 * the UI never sees DTOs, CLR types or disk paths.
 */

enum class Quality { VALID, UNAVAILABLE, STALE }

enum class MetricSource { ANT, BLE, VIRTUAL, GAME, DERIVED, UNKNOWN }

/**
 * A single measurement. `0.0` is a real value; absence is `value == null` with a reason.
 * Invariants match common.schema.json#/$defs/metric.
 */
data class Metric(
    val value: Double?,
    val quality: Quality,
    val source: MetricSource = MetricSource.UNKNOWN,
    val reasonCode: String? = null,
) {
    init {
        when (quality) {
            Quality.VALID -> require(value != null && value.isFinite()) { "valid metric needs a finite value" }
            Quality.UNAVAILABLE -> require(value == null && !reasonCode.isNullOrBlank()) { "unavailable metric needs null value and reason" }
            Quality.STALE -> require(value == null || value.isFinite()) { "stale value must be finite" }
        }
    }

    /** True only for data that may be presented as current. */
    val isLive: Boolean get() = quality == Quality.VALID

    fun asStale(): Metric = when (quality) {
        Quality.VALID -> copy(quality = Quality.STALE)
        else -> this
    }

    companion object {
        fun valid(value: Double, source: MetricSource = MetricSource.UNKNOWN) = Metric(value, Quality.VALID, source)
        fun unavailable(reasonCode: String) = Metric(null, Quality.UNAVAILABLE, reasonCode = reasonCode)
    }
}

data class Metrics(
    val powerW: Metric,
    val speedMps: Metric,
    val cadenceRpm: Metric,
    val heartRateBpm: Metric,
    val distanceM: Metric,
    val ridingTimeMs: Metric,
    val movingTimeMs: Metric,
    val elevationGainM: Metric,
    val elevationLossM: Metric,
    val slopeFraction: Metric,
    val windSpeedMps: Metric,
    val windDirRad: Metric,
) {
    fun all(): List<Metric> = listOf(
        powerW, speedMps, cadenceRpm, heartRateBpm, distanceM, ridingTimeMs,
        movingTimeMs, elevationGainM, elevationLossM, slopeFraction, windSpeedMps, windDirRad,
    )

    fun map(transform: (Metric) -> Metric) = Metrics(
        transform(powerW), transform(speedMps), transform(cadenceRpm), transform(heartRateBpm),
        transform(distanceM), transform(ridingTimeMs), transform(movingTimeMs), transform(elevationGainM),
        transform(elevationLossM), transform(slopeFraction), transform(windSpeedMps), transform(windDirRad),
    )

    companion object {
        fun allUnavailable(reasonCode: String): Metrics {
            val m = Metric.unavailable(reasonCode)
            return Metrics(m, m, m, m, m, m, m, m, m, m, m, m)
        }
    }
}

enum class AdapterLayer { FRESH, STALE, ABSENT }
enum class BindingLayer { COMPATIBLE, LIMITED, INCOMPATIBLE, UNKNOWN }
enum class ModLayer { INACTIVE, INITIALIZING, DATA_READY, STOPPING, ERROR, UNKNOWN }

/** Mirror of private DataTransceiver.ReceiverState (DataTransceiver.cs:1662) plus UNKNOWN. */
enum class ReceiverState {
    UNINITIALIZED, INITIALIZED, STARTED, COMPLETE, READY, STOPPING, STOPPED, ERROR, UNKNOWN;

    val isTransitional: Boolean get() = this == INITIALIZED || this == STARTED || this == COMPLETE || this == STOPPING

    /** Same membership as DataTransceiver.IsANTOK / IsBLEOK (READY or STOPPING). */
    val countsAsOk: Boolean get() = this == READY || this == STOPPING

    companion object {
        /**
         * Whether Ride lists a receiver. With no ANT+ stick (or no Bluetooth) the mod keeps that receiver in ERROR and
         * retries it in a loop while the other one works; that is not a problem for the rider, so it is hidden. It shows
         * again as soon as it is ready, or whenever the other receiver is not working either.
         */
        fun shown(receiver: ReceiverState, other: ReceiverState): Boolean = receiver.countsAsOk || !other.countsAsOk
    }
}

enum class GameControl { IN_CONTROL, PAUSED, LOADING, NOT_IN_CONTROL, UNKNOWN }

data class Layers(
    val adapter: AdapterLayer,
    val binding: BindingLayer,
    val mod: ModLayer,
    val antReceiver: ReceiverState,
    val bleReceiver: ReceiverState,
    val gameControl: GameControl,
)

enum class Sport { CYCLING, RUNNING, ROWING, UNKNOWN }

/** Mirror of Devices/DeviceFunctionType.cs. */
enum class SensorRoleType { POWER, RESISTANCE, CADENCE, HEART_RATE, STEERING, CONTROLS, RUNNING_SPEED }

enum class SensorSource { ANT, BLE, UNKNOWN }

data class SensorRole(
    val role: SensorRoleType,
    val present: Boolean?,
    val source: SensorSource,
    /** C# ulong as decimal string. */
    val deviceId: String?,
    val sensorType: String,
    /** P12: name a present BLE sensor advertises; shown instead of the ID. */
    val name: String? = null,
) {
    init {
        if (present == true) require(deviceId != null && source != SensorSource.UNKNOWN) { "present sensor needs device and source" }
        deviceId?.let { require(it.matches(Regex("^(0|[1-9][0-9]{0,19})$"))) { "deviceId must be a decimal ulong" } }
    }
}

data class WorkoutGuard(val present: Boolean?, val finished: Boolean?) {
    /** An unfinished PC workout blocks route/save/join (policy.json indirectGuards). */
    val blocksSessionChanges: Boolean get() = present != false && finished != true
}

data class ActiveRoute(val id: String, val loop: Boolean, val reverse: Boolean)

data class TelemetrySnapshot(
    val adapterInstanceId: String?,
    val sequence: Long?,
    val stateRevision: String?,
    val snapshotAgeMs: Long?,
    val sessionGeneration: String?,
    val modVersion: String?,
    val bindingProfile: String?,
    val layers: Layers,
    val sport: Sport,
    val metrics: Metrics,
    val sensors: List<SensorRole>,
    val workout: WorkoutGuard,
    val activeRoute: ActiveRoute?,
) {
    init {
        require(sensors.map { it.role }.toSet().size == sensors.size) { "sensor roles must be unique" }
        if (layers.adapter != AdapterLayer.FRESH) {
            require(metrics.all().none { it.isLive }) { "stale/absent adapter cannot publish live metrics" }
        }
        if (layers.adapter == AdapterLayer.ABSENT) {
            require(adapterInstanceId == null && sequence == null) { "absent adapter has no instance" }
        }
    }

    fun sensor(role: SensorRoleType): SensorRole? = sensors.firstOrNull { it.role == role }
}
