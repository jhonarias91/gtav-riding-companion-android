package com.fariddroid.gtbikecompanion.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Wire DTOs for contract v1 (gtav-riding-companion-bridge/bridge/contracts/v1). Unknown fields are ignored by the Json
 * configuration; unknown enum strings stay strings here and map to UNKNOWN in the domain, never enabling actions.
 */

@Serializable
data class HelloDto(
    val service: String,
    val apiMajor: Int,
    val apiMinor: Int,
    val bridgeId: String,
    val bridgeVersion: String,
    val authRequired: Boolean,
    val transport: String,
    val nativePairing: Boolean,
)

@Serializable
data class PairRequestDto(val pin: String, val clientName: String)

@Serializable
data class PairIdRequestDto(val pin: String, val clientName: String, val clientId: String)

/** Pairing from the console QR: the bridge's one-time key instead of the typed PIN. */
@Serializable
data class PairKeyRequestDto(val pairingKey: String, val clientName: String)

@Serializable
data class PairKeyIdRequestDto(val pairingKey: String, val clientName: String, val clientId: String)

@Serializable
data class PairResponseDto(val token: String, val expiresAtUtc: String, val bridgeId: String)

@Serializable
data class MetricDto(val value: Double? = null, val quality: String, val source: String? = null, val reasonCode: String? = null)

@Serializable
data class LayersDto(
    val adapter: String,
    val binding: String,
    val mod: String,
    val antReceiver: String,
    val bleReceiver: String,
    val gameControl: String,
)

@Serializable
data class SensorDto(val role: String, val present: Boolean? = null, val source: String, val deviceId: String? = null, val sensorType: String, val name: String? = null)

@Serializable
data class WorkoutDto(val present: Boolean? = null, val finished: Boolean? = null)

@Serializable
data class ActiveRouteDto(val id: String, val loop: Boolean, val reverse: Boolean)

@Serializable
data class StateDto(
    val adapterInstanceId: String? = null,
    val sequence: Long? = null,
    val stateRevision: String? = null,
    val capturedAtUtc: String? = null,
    val snapshotAgeMs: Long? = null,
    val sessionGeneration: String? = null,
    val modVersion: String? = null,
    val bindingProfile: String? = null,
    val layers: LayersDto,
    val sport: String,
    val metrics: Map<String, MetricDto>,
    val sensors: List<SensorDto>,
    val workout: WorkoutDto,
    val activeRoute: ActiveRouteDto? = null,
)

@Serializable
data class CapabilityDto(val id: String, val available: Boolean, val reasonCode: String? = null, val helpId: String, val applyMode: String? = null)

@Serializable
data class CapabilitiesDto(
    val modVersion: String? = null,
    val bindingProfile: String? = null,
    val adapterInstanceId: String? = null,
    val capabilities: List<CapabilityDto>,
)

@Serializable
data class ErrorBodyDto(val code: String, val helpId: String? = null, val retryable: Boolean = false, val operationId: String? = null)

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto)

@Serializable
data class RouteDto(
    val id: String,
    val name: String,
    val author: String? = null,
    val distanceM: Double? = null,
    val elevationGainM: Double? = null,
    val elevationLossM: Double? = null,
    val maxElevationM: Double? = null,
    val categories: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val loopAllowed: Boolean,
    val reverseAllowed: Boolean,
    val version: String? = null,
    val description: String? = null,
    val notes: String? = null,
)

@Serializable
data class RoutePageDto(val catalogRevision: String? = null, val total: Int, val offset: Int, val items: List<RouteDto>)

@Serializable
data class RouteDetailDto(val catalogRevision: String, val route: RouteDto)

@Serializable
data class RoutePreviewDto(
    val catalogRevision: String? = null,
    val panelOpen: Boolean,
    val routeId: String? = null,
    val loop: Boolean? = null,
    val reverse: Boolean? = null,
    val previewRevision: String? = null,
)

@Serializable
data class NumberSettingDto(val value: Double? = null, val min: Double, val max: Double, val editable: Boolean, val reasonCode: String? = null)

@Serializable
data class BoolSettingDto(val value: Boolean? = null, val editable: Boolean, val reasonCode: String? = null)

@Serializable
data class DifficultySettingDto(val value: Int? = null, val editable: Boolean, val reasonCode: String? = null, val allowed: List<Int>)

@Serializable
data class ChoiceOptionDto(val id: String, val label: String)

@Serializable
data class ChoiceSettingDto(val selectedId: String? = null, val options: List<ChoiceOptionDto>, val editable: Boolean, val reasonCode: String? = null)

@Serializable
data class ProfileSettingsDto(val weightKg: NumberSettingDto, val ftpW: NumberSettingDto, val hrMaxBpm: NumberSettingDto)

@Serializable
data class RideSettingsDto(val autopilot: BoolSettingDto, val avoidObstacles: BoolSettingDto, val difficulty: DifficultySettingDto, val roadFeel: BoolSettingDto)

@Serializable
data class AppearanceSettingsDto(val bike: ChoiceSettingDto, val character: ChoiceSettingDto)

@Serializable
data class SettingsDto(
    val settingsRevision: String? = null,
    val profile: ProfileSettingsDto,
    val ride: RideSettingsDto,
    val appearance: AppearanceSettingsDto,
)

@Serializable
data class CommandDto(
    val requestId: String,
    val adapterInstanceId: String,
    val type: String,
    val expectedRevision: String?,
    val args: JsonObject,
)

@Serializable
data class OperationDto(
    val operationId: String,
    val requestId: String,
    val type: String,
    val status: String,
    val createdAtUtc: String,
    val updatedAtUtc: String,
    val error: ErrorBodyDto? = null,
    val observed: JsonElement? = null,
)
