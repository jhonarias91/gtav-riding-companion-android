package com.fariddroid.gtbikecompanion.core.model

/**
 * Domain mirror of contract v1 for P5 (device roles), P6 (workouts, read only) and P7 (activities).
 */

// ---- P5 devices ----

/** [name]: what a BLE sensor advertises (P12), shown instead of the address; null for ANT and unnamed sensors. */
data class PcDevice(val deviceId: String, val source: SensorSource, val sensorType: String, val name: String? = null) {
    init {
        require(deviceId.matches(Regex("^(0|[1-9][0-9]{0,19})$"))) { "deviceId must be a decimal ulong" }
        require(source != SensorSource.UNKNOWN) { "a configured device has a known technology" }
    }
}

enum class DevicePolicy { AUTO, LOCKED, FILTERED }

enum class DeviceAction(val wire: String) { AUTO("auto"), LOCK("lock"), SKIP("skip") }

data class DeviceEntry(val device: PcDevice, val excluded: Boolean)

data class DeviceRole(
    val role: SensorRoleType,
    val visible: Boolean,
    val current: PcDevice?,
    val policy: DevicePolicy,
    val entries: List<DeviceEntry>,
    val canAuto: Boolean,
    val canLock: Boolean,
    val canSkip: Boolean,
) {
    init {
        require(!canSkip || current != null) { "skip needs a current device" }
    }

    fun allows(action: DeviceAction): Boolean = when (action) {
        DeviceAction.AUTO -> canAuto
        DeviceAction.LOCK -> canLock
        DeviceAction.SKIP -> canSkip
    }
}

data class DeviceRoles(val revision: String?, val reasonCode: String?, val roles: List<DeviceRole>)

// ---- P6 workouts (read only) ----

enum class DurationType { TIME, DISTANCE }

enum class SegmentType { WARMUP, COOLDOWN, INTERVAL, STEADY, RAMP, FREE_RIDE, UNKNOWN }

data class WorkoutPlan(val id: String, val name: String, val description: String?, val workoutCount: Int)

data class WorkoutPlans(val catalogRevision: String?, val ready: Boolean, val ftpReferenceW: Double?, val plans: List<WorkoutPlan>)

data class WorkoutSummary(
    val id: String,
    val planId: String,
    val name: String,
    val author: String?,
    val durationType: DurationType,
    /** Seconds for TIME, metres for DISTANCE. */
    val total: Double,
    val segmentCount: Int,
)

/** Reference watts are what the mod computed from [WorkoutDetail.ftpReferenceW]; information, never a target to set. */
data class WorkoutSegmentInfo(
    val index: Int,
    val type: SegmentType,
    val start: Double,
    val end: Double,
    val startPowerW: Int?,
    val endPowerW: Int?,
    val cadenceRpm: Int?,
)

data class WorkoutDetail(val summary: WorkoutSummary, val description: String?, val segments: List<WorkoutSegmentInfo>, val ftpReferenceW: Double?)

data class WorkoutProgress(
    val present: Boolean,
    val name: String?,
    val started: Boolean?,
    val inCountdown: Boolean?,
    val finished: Boolean?,
    val durationType: DurationType?,
    val elapsed: Double?,
    val total: Double?,
    val segmentCount: Int?,
    val segment: WorkoutSegmentInfo?,
) {
    /** Remaining time or distance, only when both ends are known. */
    val remaining: Double? get() = if (elapsed != null && total != null) (total - elapsed).coerceAtLeast(0.0) else null

    companion object {
        val NONE = WorkoutProgress(false, null, null, null, null, null, null, null, null, null)
    }
}

// ---- P7 activities ----

data class ActivityItem(val id: String, val fileName: String, val modifiedUtc: String, val sizeBytes: Long)

data class ActivityPage(val available: Boolean, val total: Int, val offset: Int, val items: List<ActivityItem>)

/** Power bucket averages; null entries are real gaps in the file. */
data class PowerSeries(val bucketS: Double, val values: List<Double?>)

/** Values read from the FIT session message. A missing value stays null; nothing is recomputed. */
data class ActivityDetail(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val sport: Sport,
    val startUtc: String?,
    val elapsedS: Double?,
    val movingS: Double?,
    val distanceM: Double?,
    val ascentM: Double?,
    val avgPowerW: Double?,
    val maxPowerW: Double?,
    val avgHeartRateBpm: Double?,
    val maxHeartRateBpm: Double?,
    val avgCadenceRpm: Double?,
    val calories: Double?,
    val power: PowerSeries?,
)

/** A downloaded FIT whose bytes matched the checksum sent by the PC. */
class FitFile(val fileName: String, val bytes: ByteArray)
