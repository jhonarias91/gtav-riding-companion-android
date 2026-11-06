package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.ActivityPage
import com.fariddroid.gtbikecompanion.core.model.BridgeInfo
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.RidersBoard
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.FitFile
import com.fariddroid.gtbikecompanion.core.model.WorkoutDetail
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlans
import com.fariddroid.gtbikecompanion.core.model.WorkoutProgress
import com.fariddroid.gtbikecompanion.core.model.WorkoutSummary
import java.security.MessageDigest
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.Operation
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PcSettings
import com.fariddroid.gtbikecompanion.core.model.RouteDetail
import com.fariddroid.gtbikecompanion.core.model.RoutePage
import com.fariddroid.gtbikecompanion.core.model.RoutePreview
import com.fariddroid.gtbikecompanion.core.model.TelemetrySnapshot
import com.fariddroid.gtbikecompanion.core.model.map
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Where a paired PC lives. The address is not its identity: [bridgeId] is (plan §12). */
data class PcEndpoint(val host: String, val port: Int, val transport: Transport, val keyFingerprint: String? = null) {
    enum class Transport { HTTP_DEV, HTTPS }

    init {
        require(port in 1024..65535) { "port out of range" }
        require(HOST.matches(host)) { "host must be an IPv4 literal or localhost" }
        require(if (transport == Transport.HTTPS) keyFingerprint?.matches(Regex("[a-f0-9]{64}")) == true else keyFingerprint == null)
    }

    companion object {
        /** IPv4 literal only: no DNS names, so a rebinding host name cannot impersonate the PC. */
        val HOST = Regex("^(localhost|((25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1?[0-9]?[0-9]))$")
    }
}

/**
 * HTTP transport for contract v1. Every call returns an [Outcome]; failures are stable codes:
 * contract codes from the error envelope, or client-only PC_UNREACHABLE / INVALID_RESPONSE.
 * HTTPS pins the key provided during pairing. Existing debug HTTP pairings use the development transport.
 */
class BridgeClient(
    private val endpoint: PcEndpoint,
    private val token: () -> String?,
    httpClient: OkHttpClient = bridgeHttpClient(endpoint),
) {
    private val http = httpClient

    /** FIT files can take longer than a JSON call on Wi-Fi. */
    private val downloadClient = httpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS).build()

    private val base: HttpUrl = HttpUrl.Builder()
        .scheme(if (endpoint.transport == PcEndpoint.Transport.HTTPS) "https" else "http")
        .host(endpoint.host)
        .port(endpoint.port)
        .build()

    suspend fun hello(): Outcome<HelloDto> = get("hello", HelloDto.serializer(), auth = false)

    suspend fun pair(pin: String, clientName: String): Outcome<PairResponseDto> =
        post("pair", json.encodeToString(PairRequestDto.serializer(), PairRequestDto(pin, clientName)), PairResponseDto.serializer(), auth = false)

    suspend fun pairWithKey(pairingKey: String, clientName: String): Outcome<PairResponseDto> =
        post("pair", json.encodeToString(PairKeyRequestDto.serializer(), PairKeyRequestDto(pairingKey, clientName)), PairResponseDto.serializer(), auth = false)

    suspend fun unpair(): Outcome<Unit> = send(request("unpair", auth = true).post(EMPTY_BODY).build()) { }

    suspend fun state(): Outcome<TelemetrySnapshot> = get("state", StateDto.serializer()).map(Mapping::snapshot)
    suspend fun capabilities(): Outcome<List<Capability>> = get("capabilities", CapabilitiesDto.serializer()).map(Mapping::capabilities)

    suspend fun routes(query: String, offset: Int, limit: Int): Outcome<RoutePage> {
        val url = base.newBuilder().addPathSegments("api/v1/routes")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", limit.toString())
            .apply { if (query.isNotBlank()) addQueryParameter("q", query.trim().take(60)) }
            .build()
        return send(authorized(Request.Builder().url(url)).build()) { json.decodeFromString(RoutePageDto.serializer(), it) }.map(Mapping::page)
    }

    suspend fun route(id: String): Outcome<RouteDetail> {
        if (!ROUTE_ID.matches(id)) return Outcome.Failure("NOT_FOUND")
        return get("routes/$id", RouteDetailDto.serializer()).map(Mapping::detail)
    }

    suspend fun routePreview(): Outcome<RoutePreview> = get("route-preview", RoutePreviewDto.serializer()).map(Mapping::preview)
    suspend fun settings(): Outcome<PcSettings> = get("settings", SettingsDto.serializer()).map(Mapping::settings)

    suspend fun deviceRoles(): Outcome<DeviceRoles> = get("device-roles", DeviceRolesDto.serializer()).map(LibraryMapping::roles)
    suspend fun workoutPlans(): Outcome<WorkoutPlans> = get("workout-plans", WorkoutPlansDto.serializer()).map(LibraryMapping::plans)

    suspend fun workouts(planId: String): Outcome<List<WorkoutSummary>> {
        if (!PLAN_ID.matches(planId)) return Outcome.Failure("NOT_FOUND")
        val url = base.newBuilder().addPathSegments("api/v1/workouts").addQueryParameter("planId", planId).build()
        return send(authorized(Request.Builder().url(url)).build()) { json.decodeFromString(WorkoutListDto.serializer(), it) }
            .map { list -> list.workouts.map(LibraryMapping::summary) }
    }

    suspend fun workout(id: String): Outcome<WorkoutDetail> {
        if (!WORKOUT_ID.matches(id)) return Outcome.Failure("NOT_FOUND")
        return get("workouts/$id", WorkoutDetailDto.serializer()).map(LibraryMapping::detail)
    }

    suspend fun workoutProgress(): Outcome<WorkoutProgress> = get("workout-progress", WorkoutProgressDto.serializer()).map(LibraryMapping::progress)

    suspend fun activities(offset: Int, limit: Int): Outcome<ActivityPage> {
        val url = base.newBuilder().addPathSegments("api/v1/activities")
            .addQueryParameter("offset", offset.toString()).addQueryParameter("limit", limit.toString()).build()
        return send(authorized(Request.Builder().url(url)).build()) { json.decodeFromString(ActivityPageDto.serializer(), it) }.map(LibraryMapping::page)
    }

    suspend fun activity(id: String): Outcome<ActivityDetail> {
        if (!ACTIVITY_ID.matches(id)) return Outcome.Failure("NOT_FOUND")
        return get("activities/$id", ActivityDetailDto.serializer()).map(LibraryMapping::activity)
    }

    suspend fun gameMap(): Outcome<GameMap> = get("map", MapDto.serializer()).map(WorldMapping::map)
    suspend fun riders(): Outcome<RidersBoard> = get("riders", RidersDto.serializer()).map(WorldMapping::riders)
    suspend fun bridgeInfo(): Outcome<BridgeInfo> = hello().map { BridgeInfo(it.bridgeId, it.bridgeVersion, it.apiMinor, it.transport) }

    /** Calibrated PNG for [GameMap.assetId]; size-bounded and checked for the PNG signature. */
    suspend fun mapImage(assetId: String): Outcome<ByteArray> = withContext(Dispatchers.IO) {
        if (!MAP_ASSET.matches(assetId)) return@withContext Outcome.Failure("NOT_FOUND")
        try {
            downloadClient.newCall(request("assets/$assetId", auth = true).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use Outcome.Failure(if (response.code == 401) "AUTH_REQUIRED" else "ASSET_UNAVAILABLE")
                val declared = response.header("Content-Length")?.toLongOrNull()
                if (declared == null || declared > MAX_MAP_BYTES) return@use Outcome.Failure("ASSET_UNAVAILABLE")
                val bytes = response.body.bytes()
                val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)
                if (bytes.size.toLong() != declared || !png) Outcome.Failure("ASSET_UNAVAILABLE") else Outcome.Success(bytes)
            }
        } catch (e: IOException) {
            Outcome.Failure("PC_UNREACHABLE")
        }
    }

    /** Binary FIT. Rejected unless size and SHA-256 match what the PC declared (no partial files). */
    suspend fun downloadFit(id: String, fileName: String): Outcome<FitFile> = withContext(Dispatchers.IO) {
        if (!ACTIVITY_ID.matches(id)) return@withContext Outcome.Failure("NOT_FOUND")
        try {
            downloadClient.newCall(request("activities/$id/fit", auth = true).get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), response.body.string()).error }.getOrNull()
                    return@use Outcome.Failure(error?.code ?: if (response.code == 401) "AUTH_REQUIRED" else "HTTP_${response.code}")
                }
                val declared = response.header("Content-Length")?.toLongOrNull()
                if (declared == null || declared > MAX_FIT_BYTES) return@use Outcome.Failure("FIT_INVALID")
                val bytes = response.body.bytes()
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                if (bytes.size.toLong() != declared || sha != response.header("X-Content-SHA256")) Outcome.Failure("FIT_INVALID")
                else Outcome.Success(FitFile(fileName, bytes))
            }
        } catch (e: IOException) {
            Outcome.Failure("PC_UNREACHABLE")
        }
    }

    /** A fresh UUID per user action; callers never resend the same action automatically. */
    suspend fun submit(command: Command, adapterInstanceId: String, requestId: String = UUID.randomUUID().toString()): Outcome<Operation> {
        val body = json.encodeToString(CommandDto.serializer(), Mapping.command(command, requestId, adapterInstanceId))
        return post("commands", body, OperationDto.serializer(), auth = true).map(Mapping::operation)
    }

    suspend fun operation(requestId: String): Outcome<Operation> {
        if (!REQUEST_ID.matches(requestId)) return Outcome.Failure("NOT_FOUND")
        return get("operations/$requestId", OperationDto.serializer()).map(Mapping::operation)
    }

    // ---- transport ----

    private fun request(path: String, auth: Boolean): Request.Builder {
        val builder = Request.Builder().url(base.newBuilder().addPathSegments("api/v1/$path").build())
        return if (auth) authorized(builder) else builder
    }

    private fun authorized(builder: Request.Builder): Request.Builder {
        val t = token()
        return if (t != null) builder.header("Authorization", "Bearer $t") else builder
    }

    private suspend fun <T> get(path: String, serializer: KSerializer<T>, auth: Boolean = true): Outcome<T> =
        send(request(path, auth).get().build()) { json.decodeFromString(serializer, it) }

    private suspend fun <T> post(path: String, body: String, serializer: KSerializer<T>, auth: Boolean): Outcome<T> =
        send(request(path, auth).post(body.toRequestBody(JSON_TYPE)).build()) { json.decodeFromString(serializer, it) }

    private suspend fun <T> send(request: Request, client: OkHttpClient = http, parse: (String) -> T): Outcome<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) {
                    Outcome.Success(parse(text))
                } else {
                    val error = runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), text).error }.getOrNull()
                    Outcome.Failure(error?.code ?: if (response.code == 401) "AUTH_REQUIRED" else "HTTP_${response.code}", error?.operationId)
                }
            }
        } catch (e: SSLException) {
            Outcome.Failure("TLS_VERIFICATION_FAILED")
        } catch (e: IOException) {
            Outcome.Failure("PC_UNREACHABLE")
        } catch (e: SerializationException) {
            Outcome.Failure("INVALID_RESPONSE")
        } catch (e: IllegalArgumentException) {
            Outcome.Failure("INVALID_RESPONSE") // Domain invariants rejected the data.
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = true; encodeDefaults = true }
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)
        private val ROUTE_ID = Regex("^(0|[1-9][0-9]{0,4})$")
        private val MAP_ASSET = Regex("^map-[a-f0-9]{64}$")
        const val MAX_MAP_BYTES = 12L * 1024 * 1024
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val PLAN_ID = Regex("^p-[a-f0-9]{16}$")
        private val WORKOUT_ID = Regex("^w-[a-f0-9]{16}$")
        private val ACTIVITY_ID = Regex("^a-[a-f0-9]{16}$")
        const val MAX_FIT_BYTES = 20L * 1024 * 1024
        private val REQUEST_ID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS) // A command waits up to ~3.5 s for the adapter ack.
            .callTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false) // Never let the transport replay a command.
            .followRedirects(false)
            .build()
    }
}
