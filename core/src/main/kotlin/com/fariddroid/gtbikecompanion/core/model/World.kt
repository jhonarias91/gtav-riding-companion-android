package com.fariddroid.gtbikecompanion.core.model

/**
 * P8 (optional extensions) and P9 (diagnostics). Map and riders exist only when the PC operator enabled them on
 * the bridge; their capabilities say so. Join busiest route and advanced bot/traffic edits stay unavailable.
 */

/** Position normalized to the calibrated PNG, from its top-left corner. */
data class MapPoint(val u: Double, val v: Double) {
    init {
        require(u in 0.0..1.0 && v in 0.0..1.0) { "map point outside the image" }
    }
}

data class CourseRef(val id: String, val reverse: Boolean)

/** Native course waypoints in riding order; a null point breaks the line (outside coverage). */
data class MapRoute(val course: CourseRef, val waypoints: List<MapPoint?>)

data class GameMap(
    val assetId: String,
    val calibrationRevision: String,
    val width: Int,
    val height: Int,
    val sequence: Long,
    /** null: outside the calibrated image or unavailable. */
    val position: MapPoint?,
    val route: MapRoute?,
)

enum class RiderKind { LOCAL, HUMAN, BOT, UNKNOWN }

data class RiderRow(val rank: Int, val kind: RiderKind, val name: String?, val wattsPerKg: Double?, val speedMps: Double?)

data class RidersBoard(
    /** PC multiplayer connection, independent of the phone-PC link. */
    val connected: Boolean,
    val course: CourseRef?,
    /** Suggestion only; joining is done from the PC menu. */
    val busiestRoute: CourseRef?,
    val riders: List<RiderRow>,
)

/** Bridge identity for diagnostics. Never includes tokens, PINs or LAN addresses. */
data class BridgeInfo(val bridgeId: String, val bridgeVersion: String, val apiMinor: Int, val transport: String)
