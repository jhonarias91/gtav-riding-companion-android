package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.ActivityPage
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.BridgeInfo
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.GameOptionsDoc
import com.fariddroid.gtbikecompanion.core.model.RidersBoard
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.FitFile
import com.fariddroid.gtbikecompanion.core.model.WorkoutDetail
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlans
import com.fariddroid.gtbikecompanion.core.model.WorkoutProgress
import com.fariddroid.gtbikecompanion.core.model.WorkoutSummary
import com.fariddroid.gtbikecompanion.core.model.BindingLayer
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.ChannelState
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.GameControl
import com.fariddroid.gtbikecompanion.core.model.Layers
import com.fariddroid.gtbikecompanion.core.model.Metrics
import com.fariddroid.gtbikecompanion.core.model.ModLayer
import com.fariddroid.gtbikecompanion.core.model.Operation
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PcSettings
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.model.RouteDetail
import com.fariddroid.gtbikecompanion.core.model.RoutePage
import com.fariddroid.gtbikecompanion.core.model.RoutePreview
import com.fariddroid.gtbikecompanion.core.model.Sport
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.model.TelemetrySnapshot
import com.fariddroid.gtbikecompanion.core.model.WorkoutGuard
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * One shared polling loop per paired PC (plan §14): GET state every second while collected, capabilities when
 * the semantic revision changes or every 5 s. No overlapping requests. Read failures back off 1/2/4/8 s up to
 * 15 s with jitter; commands are never retried here. Collection stops when no screen is subscribed.
 */
class BridgeFeed(
    private val client: BridgeClient,
    private val periodMs: Long = 1_000,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val random: Random = Random.Default,
) {
    val feed: Flow<TelemetryFeed> = flow {
        var capabilities = emptyList<Capability>()
        var capabilitiesAt = Long.MIN_VALUE / 2
        var capabilitiesRevision: String? = null
        var last: TelemetrySnapshot? = null
        var failures = 0
        while (true) {
            when (val result = client.state()) {
                is Outcome.Success -> {
                    failures = 0
                    val snapshot = result.value
                    val now = clockMs()
                    if (snapshot.stateRevision != capabilitiesRevision || now - capabilitiesAt >= CAPABILITIES_MS) {
                        (client.capabilities() as? Outcome.Success)?.let {
                            capabilities = it.value
                            capabilitiesAt = now
                            capabilitiesRevision = snapshot.stateRevision
                        }
                    }
                    last = snapshot
                    emit(TelemetryFeed(DataOrigin.BRIDGE, channelFor(snapshot), snapshot, capabilities))
                    delay(periodMs)
                }
                is Outcome.Failure -> {
                    if (result.code == "AUTH_REQUIRED") {
                        emit(TelemetryFeed(DataOrigin.BRIDGE, ChannelState.UNPAIRED, staleOrAbsent(last, "AUTH_REQUIRED"), emptyList()))
                        delay(MAX_BACKOFF_MS)
                    } else {
                        failures++
                        emit(TelemetryFeed(DataOrigin.BRIDGE, ChannelState.RECONNECTING, staleOrAbsent(last, result.code), capabilities))
                        delay(backoffMs(failures))
                    }
                }
            }
        }
    }

    internal fun backoffMs(failures: Int): Long {
        val base = (1_000L shl (failures - 1).coerceIn(0, 3)).coerceAtMost(MAX_BACKOFF_MS)
        return base + random.nextLong(0, 250)
    }

    companion object {
        const val CAPABILITIES_MS = 5_000L
        const val MAX_BACKOFF_MS = 15_000L

        fun channelFor(snapshot: TelemetrySnapshot): ChannelState = when {
            snapshot.layers.adapter == AdapterLayer.ABSENT -> ChannelState.WAITING_FOR_ADAPTER
            snapshot.layers.binding == BindingLayer.INCOMPATIBLE -> ChannelState.INCOMPATIBLE
            snapshot.layers.adapter == AdapterLayer.STALE -> ChannelState.STALE
            else -> ChannelState.READY
        }

        /** Last known values kept only as stale history; no last snapshot means nothing to show. */
        fun staleOrAbsent(last: TelemetrySnapshot?, reason: String): TelemetrySnapshot =
            last?.let {
                it.copy(
                    layers = it.layers.copy(adapter = AdapterLayer.STALE, gameControl = GameControl.UNKNOWN),
                    metrics = it.metrics.map { m -> m.asStale() },
                )
            } ?: absent(reason)

        fun absent(reason: String) = TelemetrySnapshot(
            adapterInstanceId = null, sequence = null, stateRevision = null, snapshotAgeMs = null, sessionGeneration = null,
            modVersion = null, bindingProfile = null,
            layers = Layers(AdapterLayer.ABSENT, BindingLayer.UNKNOWN, ModLayer.UNKNOWN, ReceiverState.UNKNOWN, ReceiverState.UNKNOWN, GameControl.UNKNOWN),
            sport = Sport.UNKNOWN, metrics = Metrics.allUnavailable(reason), sensors = emptyList(),
            workout = WorkoutGuard(null, null), activeRoute = null,
        )
    }
}

/** ControlRepository over the bridge. */
class BridgeControlRepository(private val client: BridgeClient) : ControlRepository {
    override suspend fun routes(query: String, offset: Int, limit: Int): Outcome<RoutePage> = client.routes(query, offset, limit)
    override suspend fun route(id: String): Outcome<RouteDetail> = client.route(id)
    override suspend fun routePreview(): Outcome<RoutePreview> = client.routePreview()
    override suspend fun settings(): Outcome<PcSettings> = client.settings()
    override suspend fun deviceRoles(): Outcome<DeviceRoles> = client.deviceRoles()
    override suspend fun workoutPlans(): Outcome<WorkoutPlans> = client.workoutPlans()
    override suspend fun workouts(planId: String): Outcome<List<WorkoutSummary>> = client.workouts(planId)
    override suspend fun workout(id: String): Outcome<WorkoutDetail> = client.workout(id)
    override suspend fun workoutProgress(): Outcome<WorkoutProgress> = client.workoutProgress()
    override suspend fun activities(offset: Int, limit: Int): Outcome<ActivityPage> = client.activities(offset, limit)
    override suspend fun activity(id: String): Outcome<ActivityDetail> = client.activity(id)

    override suspend fun gameMap(): Outcome<GameMap> = client.gameMap()
    override suspend fun mapImage(assetId: String): Outcome<ByteArray> = client.mapImage(assetId)
    override suspend fun riders(): Outcome<RidersBoard> = client.riders()
    override suspend fun bridgeInfo(): Outcome<BridgeInfo> = client.bridgeInfo()
    override suspend fun gameOptions(): Outcome<GameOptionsDoc> = client.gameOptions()
    override suspend fun saveGameOptions(revision: String, changes: Map<String, Any>): Outcome<GameOptionsDoc> = client.saveGameOptions(revision, changes)

    override suspend fun downloadFit(id: String): Outcome<FitFile> = when (val detail = client.activity(id)) {
        is Outcome.Failure -> detail
        is Outcome.Success -> client.downloadFit(id, detail.value.fileName)
    }
    override suspend fun submit(command: Command, adapterInstanceId: String): Outcome<Operation> = client.submit(command, adapterInstanceId)
    override suspend fun operation(requestId: String): Outcome<Operation> = client.operation(requestId)
}
