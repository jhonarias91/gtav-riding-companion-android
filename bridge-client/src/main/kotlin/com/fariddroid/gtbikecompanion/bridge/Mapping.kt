package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.ActiveRoute
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.BindingLayer
import com.fariddroid.gtbikecompanion.core.model.BoolSetting
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.ChoiceOption
import com.fariddroid.gtbikecompanion.core.model.ChoiceSetting
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DifficultySetting
import com.fariddroid.gtbikecompanion.core.model.GameControl
import com.fariddroid.gtbikecompanion.core.model.Layers
import com.fariddroid.gtbikecompanion.core.model.Metric
import com.fariddroid.gtbikecompanion.core.model.MetricSource
import com.fariddroid.gtbikecompanion.core.model.Metrics
import com.fariddroid.gtbikecompanion.core.model.ModLayer
import com.fariddroid.gtbikecompanion.core.model.NumberSetting
import com.fariddroid.gtbikecompanion.core.model.Operation
import com.fariddroid.gtbikecompanion.core.model.OperationStatus
import com.fariddroid.gtbikecompanion.core.model.PcSettings
import com.fariddroid.gtbikecompanion.core.model.Quality
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.model.RouteDetail
import com.fariddroid.gtbikecompanion.core.model.RoutePage
import com.fariddroid.gtbikecompanion.core.model.RoutePreview
import com.fariddroid.gtbikecompanion.core.model.RouteSummary
import com.fariddroid.gtbikecompanion.core.model.SensorRole
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import com.fariddroid.gtbikecompanion.core.model.Sport
import com.fariddroid.gtbikecompanion.core.model.TelemetrySnapshot
import com.fariddroid.gtbikecompanion.core.model.WorkoutGuard
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** DTO -> domain. Defensive: a malformed value becomes unknown/unavailable instead of crashing or enabling actions. */
object Mapping {
    private val DEVICE_ID = Regex("^(0|[1-9][0-9]{0,19})$")

    fun metric(dto: MetricDto?): Metric {
        if (dto == null) return Metric.unavailable("NOT_CAPTURED")
        val value = dto.value?.takeIf { it.isFinite() }
        val source = when (dto.source) {
            "ANT" -> MetricSource.ANT
            "BLE" -> MetricSource.BLE
            "virtual" -> MetricSource.VIRTUAL
            "game" -> MetricSource.GAME
            "derived" -> MetricSource.DERIVED
            else -> MetricSource.UNKNOWN
        }
        return when {
            dto.quality == "valid" && value != null -> Metric.valid(value, source)
            dto.quality == "stale" -> Metric(value, Quality.STALE, source, dto.reasonCode)
            else -> Metric.unavailable(dto.reasonCode?.takeIf { it.isNotBlank() } ?: "NOT_CAPTURED")
        }
    }

    private fun receiver(name: String): ReceiverState =
        ReceiverState.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ReceiverState.UNKNOWN

    fun layers(dto: LayersDto) = Layers(
        adapter = when (dto.adapter) { "fresh" -> AdapterLayer.FRESH; "stale" -> AdapterLayer.STALE; else -> AdapterLayer.ABSENT },
        binding = when (dto.binding) {
            "compatible" -> BindingLayer.COMPATIBLE
            "limited" -> BindingLayer.LIMITED
            "incompatible" -> BindingLayer.INCOMPATIBLE
            else -> BindingLayer.UNKNOWN
        },
        mod = when (dto.mod) {
            "inactive" -> ModLayer.INACTIVE
            "initializing" -> ModLayer.INITIALIZING
            "dataReady" -> ModLayer.DATA_READY
            "stopping" -> ModLayer.STOPPING
            "error" -> ModLayer.ERROR
            else -> ModLayer.UNKNOWN
        },
        antReceiver = receiver(dto.antReceiver),
        bleReceiver = receiver(dto.bleReceiver),
        gameControl = when (dto.gameControl) {
            "inControl" -> GameControl.IN_CONTROL
            "paused" -> GameControl.PAUSED
            "loading" -> GameControl.LOADING
            "notInControl" -> GameControl.NOT_IN_CONTROL
            else -> GameControl.UNKNOWN
        },
    )

    private fun role(name: String): SensorRoleType? = when (name) {
        "power" -> SensorRoleType.POWER
        "resistance" -> SensorRoleType.RESISTANCE
        "cadence" -> SensorRoleType.CADENCE
        "heartRate" -> SensorRoleType.HEART_RATE
        "steering" -> SensorRoleType.STEERING
        "controls" -> SensorRoleType.CONTROLS
        "runningSpeed" -> SensorRoleType.RUNNING_SPEED
        else -> null
    }

    private fun sensor(dto: SensorDto): SensorRole? {
        val type = role(dto.role) ?: return null
        val source = when (dto.source) { "ANT" -> SensorSource.ANT; "BLE" -> SensorSource.BLE; else -> SensorSource.UNKNOWN }
        val id = dto.deviceId?.takeIf { DEVICE_ID.matches(it) }
        val present = when {
            dto.present == true && id != null && source != SensorSource.UNKNOWN -> true
            dto.present == null -> null
            else -> false
        }
        val name = dto.name?.filterNot { it.isISOControl() }?.trim()?.take(64)?.takeIf { it.isNotEmpty() && present == true && source == SensorSource.BLE }
        return SensorRole(type, present, source, if (present == true) id else null, dto.sensorType, name)
    }

    fun snapshot(dto: StateDto): TelemetrySnapshot {
        val layers = layers(dto.layers)
        var metrics = Metrics(
            powerW = metric(dto.metrics["powerW"]),
            speedMps = metric(dto.metrics["speedMps"]),
            cadenceRpm = metric(dto.metrics["cadenceRpm"]),
            heartRateBpm = metric(dto.metrics["heartRateBpm"]),
            distanceM = metric(dto.metrics["distanceM"]),
            ridingTimeMs = metric(dto.metrics["ridingTimeMs"]),
            movingTimeMs = metric(dto.metrics["movingTimeMs"]),
            elevationGainM = metric(dto.metrics["elevationGainM"]),
            elevationLossM = metric(dto.metrics["elevationLossM"]),
            slopeFraction = metric(dto.metrics["slopeFraction"]),
            windSpeedMps = metric(dto.metrics["windSpeedMps"]),
            windDirRad = metric(dto.metrics["windDirRad"]),
        )
        // The bridge already guarantees this; the client enforces it again (never live values without a fresh adapter).
        if (layers.adapter != AdapterLayer.FRESH) metrics = metrics.map { it.asStale() }
        val absent = layers.adapter == AdapterLayer.ABSENT
        return TelemetrySnapshot(
            adapterInstanceId = if (absent) null else dto.adapterInstanceId,
            sequence = if (absent) null else dto.sequence,
            stateRevision = dto.stateRevision,
            snapshotAgeMs = dto.snapshotAgeMs,
            sessionGeneration = dto.sessionGeneration,
            modVersion = dto.modVersion,
            bindingProfile = dto.bindingProfile,
            layers = layers,
            sport = when (dto.sport) { "cycling" -> Sport.CYCLING; "running" -> Sport.RUNNING; "rowing" -> Sport.ROWING; else -> Sport.UNKNOWN },
            metrics = metrics,
            sensors = dto.sensors.mapNotNull(::sensor).distinctBy { it.role },
            workout = WorkoutGuard(dto.workout.present, dto.workout.finished),
            activeRoute = dto.activeRoute?.let { ActiveRoute(it.id, it.loop, it.reverse) },
        )
    }

    fun capabilities(dto: CapabilitiesDto): List<Capability> = dto.capabilities.mapNotNull { c ->
        val id = CapabilityId.fromWire(c.id) ?: return@mapNotNull null
        val reason = if (c.available) null else c.reasonCode?.takeIf { it.isNotBlank() } ?: "CAPABILITY_UNAVAILABLE"
        Capability(id, c.available, reason, c.helpId)
    }

    fun route(dto: RouteDto) = RouteSummary(
        id = dto.id,
        name = dto.name,
        author = dto.author,
        distanceM = dto.distanceM,
        elevationGainM = dto.elevationGainM,
        elevationLossM = dto.elevationLossM,
        maxElevationM = dto.maxElevationM,
        categories = dto.categories,
        loopAllowed = dto.loopAllowed,
        reverseAllowed = dto.loopAllowed && dto.reverseAllowed,
    )

    fun page(dto: RoutePageDto) = RoutePage(dto.catalogRevision, dto.total, dto.offset, dto.items.map(::route))
    fun detail(dto: RouteDetailDto) = RouteDetail(route(dto.route), dto.route.description, dto.route.notes)
    fun preview(dto: RoutePreviewDto) = RoutePreview(dto.catalogRevision, dto.panelOpen, dto.routeId, dto.loop, dto.reverse)

    private fun reason(editable: Boolean, code: String?) = if (editable) null else code ?: "CAPABILITY_UNAVAILABLE"
    private fun number(d: NumberSettingDto) = NumberSetting(d.value, d.min, d.max, d.editable, reason(d.editable, d.reasonCode))
    private fun bool(d: BoolSettingDto) = BoolSetting(d.value, d.editable, reason(d.editable, d.reasonCode))
    private fun choice(d: ChoiceSettingDto) = ChoiceSetting(d.selectedId, d.options.map { ChoiceOption(it.id, it.label) }, d.editable, reason(d.editable, d.reasonCode))

    fun settings(dto: SettingsDto) = PcSettings(
        revision = dto.settingsRevision,
        weightKg = number(dto.profile.weightKg),
        ftpW = number(dto.profile.ftpW),
        hrMaxBpm = number(dto.profile.hrMaxBpm),
        autopilot = bool(dto.ride.autopilot),
        avoidObstacles = bool(dto.ride.avoidObstacles),
        difficulty = DifficultySetting(
            dto.ride.difficulty.value?.takeIf { it in DifficultySetting.LEVELS },
            dto.ride.difficulty.allowed.filter { it in DifficultySetting.LEVELS },
            dto.ride.difficulty.editable,
            reason(dto.ride.difficulty.editable, dto.ride.difficulty.reasonCode),
        ),
        roadFeel = bool(dto.ride.roadFeel),
        bike = choice(dto.appearance.bike),
        character = choice(dto.appearance.character),
    )

    fun operation(dto: OperationDto) = Operation(
        requestId = dto.requestId,
        type = dto.type,
        status = OperationStatus.entries.firstOrNull { it.name.equals(dto.status, ignoreCase = true) } ?: OperationStatus.UNKNOWN,
        errorCode = dto.error?.code,
    )

    /** Domain command -> wire envelope. expectedRevision is the catalog or settings revision the user saw. */
    fun command(command: Command, requestId: String, adapterInstanceId: String): CommandDto {
        val (revision: String?, args: JsonObject) = when (command) {
            is Command.SetModActive -> null to buildJsonObject { put("enabled", command.enabled) }
            is Command.ApplyRoute -> command.catalogRevision to buildJsonObject {
                put("routeId", command.routeId?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                put("loop", command.loop)
                put("reverse", command.reverse)
            }
            is Command.PreviewRoute -> command.catalogRevision to buildJsonObject {
                put("routeId", command.routeId?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            }
            Command.OpenRoutesPanel -> null to buildJsonObject { put("panel", "routes") }
            Command.CloseRoutesPanel -> null to buildJsonObject { put("panel", "routes") }
            is Command.UpdateProfile -> command.settingsRevision to buildJsonObject {
                command.weightKg?.let { put("weightKg", it) }
                command.ftpW?.let { put("ftpW", it) }
                command.hrMaxBpm?.let { put("hrMaxBpm", it) }
            }
            is Command.UpdateRide -> command.settingsRevision to buildJsonObject {
                command.autopilot?.let { put("autopilot", it) }
                command.avoidObstacles?.let { put("avoidObstacles", it) }
                command.difficulty?.let { put("difficulty", it) }
                command.roadFeel?.let { put("roadFeel", it) }
            }
            is Command.SelectBike -> command.settingsRevision to buildJsonObject { put("bikeId", command.bikeId) }
            is Command.SelectCharacter -> command.settingsRevision to buildJsonObject { put("characterId", command.characterId) }
            is Command.ConfigureDevice -> command.devicesRevision to buildJsonObject {
                put("role", LibraryMapping.ROLE_WIRE.getValue(command.role))
                put("action", command.action.wire)
                put("expectedDeviceId", command.expected?.deviceId?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
                put("expectedSource", command.expected?.source?.name?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            }
            is Command.AssignDevice -> command.devicesRevision to buildJsonObject {
                put("role", LibraryMapping.ROLE_WIRE.getValue(command.role))
                put("sensorType", command.sensorType.wire)
                put("deviceId", command.deviceId)
            }
            is Command.DisconnectDevice -> command.devicesRevision to buildJsonObject {
                put("sensorType", command.sensorType.wire)
                put("deviceId", command.deviceId)
            }
            is Command.FinishActivity -> command.sessionGeneration to buildJsonObject { }
            is Command.SelectWorkout -> command.catalogRevision to buildJsonObject { put("workoutId", command.workoutId) }
        }
        return CommandDto(requestId, adapterInstanceId, command.wireType, revision, args)
    }
}
