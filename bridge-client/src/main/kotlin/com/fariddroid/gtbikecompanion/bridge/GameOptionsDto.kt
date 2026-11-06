package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.BotType
import com.fariddroid.gtbikecompanion.core.model.GameOptions
import com.fariddroid.gtbikecompanion.core.model.GameOptionsDoc
import com.fariddroid.gtbikecompanion.core.model.TrafficStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** GET/POST /api/v1/mod-config (P15, contracts/v1/mod-config.schema.json). */
@Serializable
data class GameOptionsDocDto(
    val available: Boolean,
    val reasonCode: String? = null,
    val revision: String? = null,
    val gameRunning: Boolean,
    val editable: Boolean,
    val options: GameOptionsDto? = null,
    val defaults: GameOptionsDto,
)

@Serializable
data class GameOptionsDto(
    val trafficBikesPercent: Int? = null,
    val trafficStyle: String? = null,
    val crowdsEnabled: Boolean? = null,
    val crowdsSlopePercent: Int? = null,
    val crowdsAmount: Int? = null,
    val bots: List<Int>? = null,
    val defensiveLevel: Int? = null,
    val waypointColor: String? = null,
    val waypointRadius: Int? = null,
    val fineSteering: Double? = null,
    val screenshotIntervalKm: Int? = null,
    val screenshotXOffset: Int? = null,
    val menuAutoOpen: Boolean? = null,
    val showClock: Boolean? = null,
    val debugWindow: Boolean? = null,
    val logLevel: Int? = null,
    val gpsOriginLat: Double? = null,
    val gpsOriginLon: Double? = null,
    val telemetry: Boolean? = null,
    val multiplayer: Boolean? = null,
)

object GameOptionsMapping {
    fun doc(dto: GameOptionsDocDto) = GameOptionsDoc(
        available = dto.available,
        reasonCode = dto.reasonCode,
        revision = dto.revision?.takeIf { REVISION.matches(it) },
        gameRunning = dto.gameRunning,
        editable = dto.editable && dto.available && !dto.gameRunning,
        options = dto.options?.let(::options),
        defaults = options(dto.defaults),
    )

    fun options(d: GameOptionsDto) = GameOptions(
        trafficBikesPercent = d.trafficBikesPercent,
        trafficStyle = TrafficStyle.fromWire(d.trafficStyle),
        crowdsEnabled = d.crowdsEnabled,
        crowdsSlopePercent = d.crowdsSlopePercent,
        crowdsAmount = d.crowdsAmount,
        bots = d.bots?.mapNotNull(BotType::fromWire),
        defensiveLevel = d.defensiveLevel,
        waypointColor = d.waypointColor,
        waypointRadius = d.waypointRadius,
        fineSteering = d.fineSteering,
        screenshotIntervalKm = d.screenshotIntervalKm,
        screenshotXOffset = d.screenshotXOffset,
        menuAutoOpen = d.menuAutoOpen,
        showClock = d.showClock,
        debugWindow = d.debugWindow,
        logLevel = d.logLevel,
        gpsOriginLat = d.gpsOriginLat,
        gpsOriginLon = d.gpsOriginLon,
        telemetry = d.telemetry,
        multiplayer = d.multiplayer,
    )

    /** {expectedRevision, options:{...}} with only the changed fields (the bridge refuses nulls and unknown names). */
    fun request(revision: String, changes: Map<String, Any>): JsonObject = buildJsonObject {
        put("expectedRevision", JsonPrimitive(revision))
        put("options", buildJsonObject {
            for ((name, value) in changes) {
                put(name, when (value) {
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is String -> JsonPrimitive(value)
                    is List<*> -> JsonArray(value.map { JsonPrimitive(it as Number) })
                    else -> throw IllegalArgumentException("Unsupported option value for $name")
                })
            }
        })
    }

    private val REVISION = Regex("^modcfg-[a-f0-9]{24}$")
}
