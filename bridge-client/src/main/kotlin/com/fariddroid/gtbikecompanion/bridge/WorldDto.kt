package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.CourseRef
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.MapPoint
import com.fariddroid.gtbikecompanion.core.model.MapRoute
import com.fariddroid.gtbikecompanion.core.model.RiderKind
import com.fariddroid.gtbikecompanion.core.model.RiderRow
import com.fariddroid.gtbikecompanion.core.model.RidersBoard
import kotlinx.serialization.Serializable

// Wire DTOs for map.schema.json and riders.schema.json (P8).

@Serializable
data class MapPointDto(val u: Double, val v: Double)

@Serializable
data class MapRouteDto(val id: String, val reverse: Boolean, val waypoints: List<MapPointDto?>)

@Serializable
data class MapDto(
    val assetId: String,
    val calibrationRevision: String,
    val width: Int,
    val height: Int,
    val sequence: Long,
    val position: MapPointDto? = null,
    val route: MapRouteDto? = null,
)

@Serializable
data class CourseDto(val id: String, val reverse: Boolean)

@Serializable
data class RiderDto(val rank: Int, val kind: String, val name: String? = null, val wattsPerKg: Double? = null, val speedMps: Double? = null)

@Serializable
data class RidersDto(val connected: Boolean, val course: CourseDto? = null, val busiestRoute: CourseDto? = null, val riders: List<RiderDto>)

object WorldMapping {
    /** Points outside [0,1] are treated as "no point" rather than clamped onto the edge. */
    private fun point(p: MapPointDto?): MapPoint? = p?.takeIf { it.u in 0.0..1.0 && it.v in 0.0..1.0 }?.let { MapPoint(it.u, it.v) }

    fun map(dto: MapDto) = GameMap(
        assetId = dto.assetId,
        calibrationRevision = dto.calibrationRevision,
        width = dto.width,
        height = dto.height,
        sequence = dto.sequence,
        position = point(dto.position),
        route = dto.route?.let { MapRoute(CourseRef(it.id, it.reverse), it.waypoints.map(::point)) },
    )

    fun riders(dto: RidersDto) = RidersBoard(
        connected = dto.connected,
        course = dto.course?.let { CourseRef(it.id, it.reverse) },
        busiestRoute = if (dto.connected) dto.busiestRoute?.let { CourseRef(it.id, it.reverse) } else null,
        riders = dto.riders.map {
            RiderRow(
                rank = it.rank,
                kind = when (it.kind) { "local" -> RiderKind.LOCAL; "human" -> RiderKind.HUMAN; "bot" -> RiderKind.BOT; else -> RiderKind.UNKNOWN },
                name = it.name,
                wattsPerKg = it.wattsPerKg,
                speedMps = it.speedMps,
            )
        }.sortedBy { it.rank },
    )
}
