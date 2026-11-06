package com.fariddroid.gtbikecompanion.data

import com.fariddroid.gtbikecompanion.BuildConfig
import com.fariddroid.gtbikecompanion.bridge.BridgeClient
import com.fariddroid.gtbikecompanion.bridge.BridgeControlRepository
import com.fariddroid.gtbikecompanion.bridge.BridgeFeed
import com.fariddroid.gtbikecompanion.bridge.HelloDto
import com.fariddroid.gtbikecompanion.bridge.PairResponseDto
import com.fariddroid.gtbikecompanion.bridge.PairingQr
import com.fariddroid.gtbikecompanion.bridge.PcEndpoint
import com.fariddroid.gtbikecompanion.core.demo.DemoControlRepository
import com.fariddroid.gtbikecompanion.core.demo.DemoTelemetryRepository
import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.BridgeInfo
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.GameOptionsDoc
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
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

sealed interface ConnectionMode {
    /** Preferences not read yet. Screens show nothing PC-related until this resolves. */
    data object Loading : ConnectionMode
    data object Demo : ConnectionMode
    data class Paired(val pc: PairedPc, val client: BridgeClient, val feed: BridgeFeed, val control: ControlRepository) : ConnectionMode
}

/** Hello result shown to the user for identity verification before any PIN is sent. */
data class BridgeIdentity(val endpoint: PcEndpoint, val bridgeId: String, val bridgeVersion: String, val apiMinor: Int)

/**
 * Owns the single paired PC (plan §6: one connection coordinator). Demo and bridge never mix: the mode decides
 * which repositories every screen reads.
 */
@Singleton
class ConnectionManager @Inject constructor(
    private val store: PairingStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<ConnectionMode>(ConnectionMode.Loading)
    val mode: StateFlow<ConnectionMode> = state.asStateFlow()

    init {
        scope.launch { state.value = store.load()?.let(::paired) ?: ConnectionMode.Demo }
    }

    private fun paired(pc: PairedPc): ConnectionMode.Paired {
        val client = BridgeClient(pc.endpoint, token = { pc.token })
        return ConnectionMode.Paired(pc, client, BridgeFeed(client), BridgeControlRepository(client))
    }

    /** Step 1: read the bridge identity. Nothing secret is sent. */
    suspend fun probe(host: String, port: Int, fingerprint: String = ""): Outcome<BridgeIdentity> {
        val pin = fingerprint.trim().lowercase().takeIf(String::isNotEmpty)
        if (pin == null && !BuildConfig.DEBUG) return Outcome.Failure("TLS_REQUIRED")
        if (pin != null && !pin.matches(Regex("[a-f0-9]{64}"))) return Outcome.Failure("TLS_FINGERPRINT_INVALID")
        val transport = if (pin == null) PcEndpoint.Transport.HTTP_DEV else PcEndpoint.Transport.HTTPS
        val endpoint = runCatching { PcEndpoint(host.trim(), port, transport, pin) }.getOrNull()
            ?: return Outcome.Failure("ADDRESS_INVALID")
        return when (val hello = BridgeClient(endpoint, token = { null }).hello()) {
            is Outcome.Failure -> hello
            is Outcome.Success -> check(endpoint, hello.value)
        }
    }

    private fun check(endpoint: PcEndpoint, hello: HelloDto): Outcome<BridgeIdentity> = when {
        hello.service != "gtav-riding-companion-bridge" || hello.apiMajor != 1 -> Outcome.Failure("UNSUPPORTED_VERSION")
        !hello.nativePairing -> Outcome.Failure("UNSUPPORTED_VERSION")
        hello.transport == "http-dev" && !BuildConfig.DEBUG -> Outcome.Failure("TLS_REQUIRED")
        hello.transport != if (endpoint.transport == PcEndpoint.Transport.HTTPS) "https" else "http-dev" -> Outcome.Failure("TLS_VERIFICATION_FAILED")
        else -> Outcome.Success(BridgeIdentity(endpoint, hello.bridgeId, hello.bridgeVersion, hello.apiMinor))
    }

    /** Step 2: after the user confirmed [identity] matches the PC console, send the PIN. */
    suspend fun pair(identity: BridgeIdentity, pin: String, clientName: String): Outcome<Unit> =
        complete(identity) { client, id -> client.pair(pin, clientName, id) }

    /**
     * Pairing from the console QR, with nothing typed: tries each address the PC listed, checks that the bridge that
     * answers is the one in the QR (and [expectedBridgeId] when renewing), then pairs with the QR's one-time key.
     */
    suspend fun pairFromQr(qr: PairingQr, clientName: String, expectedBridgeId: String? = null): Outcome<Unit> {
        if (expectedBridgeId != null && qr.bridgeId != expectedBridgeId) return Outcome.Failure("BRIDGE_CHANGED")
        var unreachable: Outcome.Failure? = null
        for (endpoint in qr.endpoints()) {
            val identity = when (val hello = BridgeClient(endpoint, token = { null }).hello()) {
                is Outcome.Failure -> { unreachable = hello; continue }
                is Outcome.Success -> when (val checked = check(endpoint, hello.value)) {
                    is Outcome.Failure -> return checked
                    is Outcome.Success -> checked.value
                }
            }
            if (identity.bridgeId != qr.bridgeId) return Outcome.Failure("BRIDGE_CHANGED")
            val result = complete(identity) { client, id -> client.pairWithKey(qr.pairingKey, clientName, id) }
            // The key changes after every pairing: an old QR (photo, scrolled console) is simply used up.
            return if (result is Outcome.Failure && result.code == "PAIRING_PIN_INVALID") Outcome.Failure("QR_USED") else result
        }
        return unreachable ?: Outcome.Failure("ADDRESS_INVALID")
    }

    private suspend fun complete(identity: BridgeIdentity, request: suspend (BridgeClient, String?) -> Outcome<PairResponseDto>): Outcome<Unit> {
        val client = BridgeClient(identity.endpoint, token = { null })
        // Same bridge as the one the user verified; another process on that address is refused.
        when (val again = client.hello()) {
            is Outcome.Failure -> return again
            is Outcome.Success -> if (again.value.bridgeId != identity.bridgeId) return Outcome.Failure("BRIDGE_CHANGED")
        }
        // API minor 11 accepts a stable random app ID. Older bridges keep the original request shape.
        val clientId = if (identity.apiMinor >= 11) store.clientId() else null
        return when (val result = request(client, clientId)) {
            is Outcome.Failure -> result
            is Outcome.Success -> {
                if (result.value.bridgeId != identity.bridgeId) return Outcome.Failure("BRIDGE_CHANGED")
                val expiresAt = runCatching { Instant.parse(result.value.expiresAtUtc) }.getOrNull()
                val pc = PairedPc(identity.bridgeId, identity.endpoint, result.value.token, expiresAt)
                store.save(pc)
                state.value = paired(pc)
                Outcome.Success(Unit)
            }
        }
    }

    /**
     * Pairs again with the saved PC after it stopped accepting the token (it expires every 30 days). The user already
     * verified this bridge's identity, so only the PIN is asked; if another bridge answers, the full flow is needed.
     */
    suspend fun renew(pin: String, clientName: String): Outcome<Unit> {
        val pc = (state.value as? ConnectionMode.Paired)?.pc ?: return Outcome.Failure("AUTH_REQUIRED")
        val identity = when (val hello = BridgeClient(pc.endpoint, token = { null }).hello()) {
            is Outcome.Failure -> return hello
            is Outcome.Success -> when (val checked = check(pc.endpoint, hello.value)) {
                is Outcome.Failure -> return checked
                is Outcome.Success -> checked.value
            }
        }
        if (identity.bridgeId != pc.bridgeId) return Outcome.Failure("BRIDGE_CHANGED")
        return pair(identity, pin, clientName)
    }

    fun useDemo() {
        if (state.value !is ConnectionMode.Paired) state.value = ConnectionMode.Demo
    }

    /** Revokes the token on the PC when reachable, then forgets it locally either way. Never stops the mod. */
    suspend fun forget() {
        (state.value as? ConnectionMode.Paired)?.client?.unpair()
        store.clear()
        state.value = ConnectionMode.Demo
    }
}

/** Telemetry for whichever mode is active. */
@Singleton
class ActiveTelemetryRepository @Inject constructor(
    private val connection: ConnectionManager,
    private val demo: DemoTelemetryRepository,
    @ApplicationScope scope: CoroutineScope,
) : TelemetryRepository {
    /** One polling loop shared by every screen; it stops 5 s after the last screen stops collecting. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val feed: Flow<TelemetryFeed> = connection.mode.flatMapLatest { mode ->
        when (mode) {
            is ConnectionMode.Paired -> mode.feed.feed
            else -> demo.feed
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 1)
}

/** Reads/commands for whichever mode is active at call time. */
@Singleton
class ActiveControlRepository @Inject constructor(
    private val connection: ConnectionManager,
    private val demo: DemoControlRepository,
) : ControlRepository {
    private val current: ControlRepository
        get() = (connection.mode.value as? ConnectionMode.Paired)?.control ?: demo

    override suspend fun routes(query: String, offset: Int, limit: Int): Outcome<RoutePage> = current.routes(query, offset, limit)
    override suspend fun route(id: String): Outcome<RouteDetail> = current.route(id)
    override suspend fun routePreview(): Outcome<RoutePreview> = current.routePreview()
    override suspend fun settings(): Outcome<PcSettings> = current.settings()
    override suspend fun deviceRoles(): Outcome<DeviceRoles> = current.deviceRoles()
    override suspend fun workoutPlans(): Outcome<WorkoutPlans> = current.workoutPlans()
    override suspend fun workouts(planId: String): Outcome<List<WorkoutSummary>> = current.workouts(planId)
    override suspend fun workout(id: String): Outcome<WorkoutDetail> = current.workout(id)
    override suspend fun workoutProgress(): Outcome<WorkoutProgress> = current.workoutProgress()
    override suspend fun activities(offset: Int, limit: Int): Outcome<ActivityPage> = current.activities(offset, limit)
    override suspend fun activity(id: String): Outcome<ActivityDetail> = current.activity(id)
    override suspend fun downloadFit(id: String): Outcome<FitFile> = current.downloadFit(id)
    override suspend fun gameMap(): Outcome<GameMap> = current.gameMap()
    override suspend fun mapImage(assetId: String): Outcome<ByteArray> = current.mapImage(assetId)
    override suspend fun riders(): Outcome<RidersBoard> = current.riders()
    override suspend fun bridgeInfo(): Outcome<BridgeInfo> = current.bridgeInfo()
    override suspend fun gameOptions(): Outcome<GameOptionsDoc> = current.gameOptions()
    override suspend fun saveGameOptions(revision: String, changes: Map<String, Any>): Outcome<GameOptionsDoc> = current.saveGameOptions(revision, changes)
    override suspend fun submit(command: Command, adapterInstanceId: String): Outcome<Operation> = current.submit(command, adapterInstanceId)
    override suspend fun operation(requestId: String): Outcome<Operation> = current.operation(requestId)
}
