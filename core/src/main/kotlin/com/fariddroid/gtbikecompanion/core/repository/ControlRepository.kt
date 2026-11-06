package com.fariddroid.gtbikecompanion.core.repository

import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.BridgeInfo
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.RidersBoard
import com.fariddroid.gtbikecompanion.core.model.ActivityPage
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.FitFile
import com.fariddroid.gtbikecompanion.core.model.WorkoutDetail
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlans
import com.fariddroid.gtbikecompanion.core.model.WorkoutProgress
import com.fariddroid.gtbikecompanion.core.model.WorkoutSummary
import com.fariddroid.gtbikecompanion.core.model.Operation
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PcSettings
import com.fariddroid.gtbikecompanion.core.model.RouteDetail
import com.fariddroid.gtbikecompanion.core.model.RoutePage
import com.fariddroid.gtbikecompanion.core.model.RoutePreview

/**
 * Reads and commands that go to the PC. Reads never change anything on the PC. A submitted command returns
 * an operation; callers poll [operation] and never resubmit automatically (CONTRATO_PUENTE_V1.md §8).
 */
interface ControlRepository {
    suspend fun routes(query: String, offset: Int, limit: Int = 50): Outcome<RoutePage>
    suspend fun route(id: String): Outcome<RouteDetail>
    suspend fun routePreview(): Outcome<RoutePreview>
    suspend fun settings(): Outcome<PcSettings>
    suspend fun deviceRoles(): Outcome<DeviceRoles>
    suspend fun workoutPlans(): Outcome<WorkoutPlans>
    suspend fun workouts(planId: String): Outcome<List<WorkoutSummary>>
    suspend fun workout(id: String): Outcome<WorkoutDetail>
    suspend fun workoutProgress(): Outcome<WorkoutProgress>
    suspend fun activities(offset: Int, limit: Int = 50): Outcome<ActivityPage>
    suspend fun activity(id: String): Outcome<ActivityDetail>

    /** Whole FIT, verified against the PC checksum. */
    suspend fun downloadFit(id: String): Outcome<FitFile>

    /** P8a: position and route on the PC operator's calibrated image. */
    suspend fun gameMap(): Outcome<GameMap>

    /** P8a: the calibrated PNG itself (bounded size). */
    suspend fun mapImage(assetId: String): Outcome<ByteArray>

    /** P8b: ranking of the current or busiest course; read only. */
    suspend fun riders(): Outcome<RidersBoard>

    /** P9: identity and versions for diagnostics. */
    suspend fun bridgeInfo(): Outcome<BridgeInfo>

    /** [adapterInstanceId] comes from the snapshot the user acted on; a restarted adapter rejects it. */
    suspend fun submit(command: Command, adapterInstanceId: String): Outcome<Operation>
    suspend fun operation(requestId: String): Outcome<Operation>
}
