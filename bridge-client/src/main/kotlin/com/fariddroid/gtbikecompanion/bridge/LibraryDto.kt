package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.ActivityItem
import com.fariddroid.gtbikecompanion.core.model.ActivityPage
import com.fariddroid.gtbikecompanion.core.model.DeviceEntry
import com.fariddroid.gtbikecompanion.core.model.DevicePolicy
import com.fariddroid.gtbikecompanion.core.model.DeviceRole
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.DurationType
import com.fariddroid.gtbikecompanion.core.model.PcDevice
import com.fariddroid.gtbikecompanion.core.model.PowerSeries
import com.fariddroid.gtbikecompanion.core.model.SegmentType
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import com.fariddroid.gtbikecompanion.core.model.Sport
import com.fariddroid.gtbikecompanion.core.model.WorkoutDetail
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlan
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlans
import com.fariddroid.gtbikecompanion.core.model.WorkoutProgress
import com.fariddroid.gtbikecompanion.core.model.WorkoutSegmentInfo
import com.fariddroid.gtbikecompanion.core.model.WorkoutSummary
import kotlinx.serialization.Serializable

// ---- wire DTOs (devices.schema.json, workouts.schema.json, activities.schema.json) ----

@Serializable
data class DeviceDto(val deviceId: String, val source: String, val sensorType: String, val name: String? = null)

@Serializable
data class DeviceEntryDto(val deviceId: String, val source: String, val sensorType: String, val excluded: Boolean)

@Serializable
data class DeviceActionsDto(val auto: Boolean, val lock: Boolean, val skip: Boolean)

@Serializable
data class DeviceRoleDto(
    val role: String,
    val visible: Boolean,
    val current: DeviceDto? = null,
    val policy: String,
    val entries: List<DeviceEntryDto> = emptyList(),
    val actions: DeviceActionsDto,
)

@Serializable
data class DeviceRolesDto(val devicesRevision: String? = null, val reasonCode: String? = null, val roles: List<DeviceRoleDto>)

@Serializable
data class WorkoutPlanDto(val id: String, val name: String, val description: String? = null, val workoutCount: Int)

@Serializable
data class WorkoutPlansDto(val catalogRevision: String? = null, val ready: Boolean, val ftpReferenceW: Double? = null, val plans: List<WorkoutPlanDto>)

@Serializable
data class WorkoutSummaryDto(
    val id: String,
    val planId: String,
    val name: String,
    val author: String? = null,
    val durationType: String,
    val total: Double,
    val segmentCount: Int,
    val description: String? = null,
    val segments: List<WorkoutSegmentDto> = emptyList(),
)

@Serializable
data class WorkoutListDto(val catalogRevision: String, val planId: String, val workouts: List<WorkoutSummaryDto>)

@Serializable
data class WorkoutSegmentDto(
    val index: Int,
    val type: String,
    val start: Double,
    val end: Double,
    val startPowerW: Int? = null,
    val endPowerW: Int? = null,
    val cadenceRpm: Int? = null,
)

@Serializable
data class WorkoutDetailDto(val catalogRevision: String, val ftpReferenceW: Double? = null, val workout: WorkoutSummaryDto)

@Serializable
data class WorkoutProgressDto(
    val present: Boolean,
    val name: String? = null,
    val started: Boolean? = null,
    val inCountdown: Boolean? = null,
    val finished: Boolean? = null,
    val durationType: String? = null,
    val elapsed: Double? = null,
    val total: Double? = null,
    val segmentCount: Int? = null,
    val segment: WorkoutSegmentDto? = null,
)

@Serializable
data class ActivityItemDto(val id: String, val fileName: String, val modifiedUtc: String, val sizeBytes: Long)

@Serializable
data class ActivityPageDto(val available: Boolean, val total: Int, val offset: Int, val items: List<ActivityItemDto>)

@Serializable
data class PowerSeriesDto(val bucketS: Double, val values: List<Double?>)

@Serializable
data class ActivityDto(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val source: String,
    val sport: String,
    val startUtc: String? = null,
    val elapsedS: Double? = null,
    val movingS: Double? = null,
    val distanceM: Double? = null,
    val ascentM: Double? = null,
    val avgPowerW: Double? = null,
    val maxPowerW: Double? = null,
    val avgHeartRateBpm: Double? = null,
    val maxHeartRateBpm: Double? = null,
    val avgCadenceRpm: Double? = null,
    val calories: Double? = null,
    val power: PowerSeriesDto? = null,
)

@Serializable
data class ActivityDetailDto(val activity: ActivityDto)

// ---- mapping ----

object LibraryMapping {
    private val ROLE = mapOf(
        "power" to SensorRoleType.POWER, "resistance" to SensorRoleType.RESISTANCE, "cadence" to SensorRoleType.CADENCE,
        "heartRate" to SensorRoleType.HEART_RATE, "steering" to SensorRoleType.STEERING, "controls" to SensorRoleType.CONTROLS,
        "runningSpeed" to SensorRoleType.RUNNING_SPEED,
    )
    val ROLE_WIRE: Map<SensorRoleType, String> = ROLE.entries.associate { (k, v) -> v to k }
    private val DEVICE_ID = Regex("^(0|[1-9][0-9]{0,19})$")

    private fun source(s: String) = when (s) { "ANT" -> SensorSource.ANT; "BLE" -> SensorSource.BLE; else -> SensorSource.UNKNOWN }

    private fun device(id: String, src: String, type: String, name: String? = null): PcDevice? {
        val source = source(src)
        val label = name?.filterNot { it.isISOControl() }?.trim()?.take(64)?.takeIf { it.isNotEmpty() && source == SensorSource.BLE }
        return if (DEVICE_ID.matches(id) && source != SensorSource.UNKNOWN) PcDevice(id, source, type, label) else null
    }

    fun roles(dto: DeviceRolesDto) = DeviceRoles(
        dto.devicesRevision,
        dto.reasonCode,
        dto.roles.mapNotNull { r ->
            val role = ROLE[r.role] ?: return@mapNotNull null
            val current = r.current?.let { device(it.deviceId, it.source, it.sensorType, it.name) }
            DeviceRole(
                role = role,
                visible = r.visible,
                current = current,
                policy = when (r.policy) { "locked" -> DevicePolicy.LOCKED; "filtered" -> DevicePolicy.FILTERED; else -> DevicePolicy.AUTO },
                entries = r.entries.mapNotNull { e -> device(e.deviceId, e.source, e.sensorType)?.let { DeviceEntry(it, e.excluded) } },
                canAuto = r.actions.auto,
                canLock = r.actions.lock && current != null,
                canSkip = r.actions.skip && current != null,
            )
        }.distinctBy { it.role },
    )

    private fun duration(s: String?) = when (s) { "time" -> DurationType.TIME; "distance" -> DurationType.DISTANCE; else -> null }

    private fun segmentType(s: String) = when (s) {
        "warmup" -> SegmentType.WARMUP
        "cooldown" -> SegmentType.COOLDOWN
        "interval" -> SegmentType.INTERVAL
        "steady" -> SegmentType.STEADY
        "ramp" -> SegmentType.RAMP
        "freeRide" -> SegmentType.FREE_RIDE
        else -> SegmentType.UNKNOWN
    }

    fun segment(d: WorkoutSegmentDto) = WorkoutSegmentInfo(d.index, segmentType(d.type), d.start, d.end, d.startPowerW, d.endPowerW, d.cadenceRpm)

    fun plans(dto: WorkoutPlansDto) = WorkoutPlans(dto.catalogRevision, dto.ready, dto.ftpReferenceW, dto.plans.map { WorkoutPlan(it.id, it.name, it.description, it.workoutCount) })

    fun summary(d: WorkoutSummaryDto) = WorkoutSummary(d.id, d.planId, d.name, d.author, duration(d.durationType) ?: DurationType.TIME, d.total, d.segmentCount)

    fun detail(dto: WorkoutDetailDto) = WorkoutDetail(summary(dto.workout), dto.workout.description, dto.workout.segments.map(::segment), dto.ftpReferenceW)

    fun progress(d: WorkoutProgressDto) = if (!d.present) WorkoutProgress.NONE else WorkoutProgress(
        true, d.name, d.started, d.inCountdown, d.finished, duration(d.durationType), d.elapsed, d.total, d.segmentCount, d.segment?.let(::segment),
    )

    fun page(dto: ActivityPageDto) = ActivityPage(dto.available, dto.total, dto.offset, dto.items.map { ActivityItem(it.id, it.fileName, it.modifiedUtc, it.sizeBytes) })

    fun activity(dto: ActivityDetailDto): ActivityDetail = with(dto.activity) {
        ActivityDetail(
            id, fileName, sizeBytes,
            when (sport) { "cycling" -> Sport.CYCLING; "running" -> Sport.RUNNING; "rowing" -> Sport.ROWING; else -> Sport.UNKNOWN },
            startUtc, elapsedS, movingS, distanceM, ascentM, avgPowerW, maxPowerW, avgHeartRateBpm, maxHeartRateBpm, avgCadenceRpm, calories,
            power?.let { PowerSeries(it.bucketS, it.values) },
        )
    }
}
