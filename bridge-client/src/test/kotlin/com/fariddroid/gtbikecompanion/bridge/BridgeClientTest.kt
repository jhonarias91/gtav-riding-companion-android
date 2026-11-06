package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.ChannelState
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.OperationStatus
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.Quality
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import java.io.File
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BridgeClientTest {
    private val fixtures = File(System.getProperty("fixturesDir") ?: "../../gtav-riding-companion-bridge/bridge/fixtures/v1")
    private fun fixture(name: String) = File(fixtures, name).readText()
    private lateinit var server: MockWebServer
    private lateinit var client: BridgeClient

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = BridgeClient(PcEndpoint("127.0.0.1", server.port, PcEndpoint.Transport.HTTP_DEV), token = { "t".repeat(43) })
    }

    @After fun tearDown() = server.close()

    private fun ok(body: String) = server.enqueue(MockResponse.Builder().code(200).body(body).build())
    private fun error(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** The bridge refuses a body with both or neither of pin/pairingKey, so the QR request must carry no pin at all. */
    @Test fun qrPairingSendsOnlyTheKeyAndNoAuthorization() = runBlocking {
        ok("""{"token":"${"k".repeat(43)}","expiresAtUtc":"2026-12-05T10:00:00.000Z","bridgeId":"bridge-1"}""")
        val result = client.pairWithKey("Qx_-".repeat(5) + "zz", "Pixel 8")
        assertEquals("2026-12-05T10:00:00.000Z", (result as Outcome.Success).value.expiresAtUtc)
        val request = server.takeRequest()
        assertEquals("/api/v1/pair", request.url.encodedPath)
        assertNull(request.headers["Authorization"])
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(setOf("pairingKey", "clientName"), body.keys)
    }

    @Test fun liveStateMapsUnitsSensorsAndRoute() = runBlocking {
        ok(fixture("valid/state_live_cycling.json"))
        val snapshot = (client.state() as Outcome.Success).value
        assertEquals(AdapterLayer.FRESH, snapshot.layers.adapter)
        assertEquals(185.0, snapshot.metrics.powerW.value!!, 0.0)
        assertEquals("18446744073709551615", snapshot.sensor(SensorRoleType.HEART_RATE)?.deviceId)
        assertEquals("35", snapshot.activeRoute?.id)
        val request = server.takeRequest()
        assertEquals("/api/v1/state", request.url.encodedPath)
        assertEquals("Bearer " + "t".repeat(43), request.headers["Authorization"])
    }

    @Test fun staleAndAbsentStatesNeverCarryLiveValues() = runBlocking {
        for (name in listOf("valid/state_stale_adapter.json", "valid/state_adapter_absent.json", "valid/state_ready_no_sensors.json", "valid/state_unsupported_mod.json")) {
            ok(fixture(name))
            val snapshot = (client.state() as Outcome.Success).value
            if (snapshot.layers.adapter != AdapterLayer.FRESH) assertTrue(name, snapshot.metrics.all().none { it.isLive })
        }
    }

    @Test fun routesSettingsAndPreviewMap() = runBlocking {
        ok(fixture("valid/routes_page.json"))
        val page = (client.routes("", 0, 50) as Outcome.Success).value
        assertEquals("catalog-3f2a", page.catalogRevision)
        assertFalse(page.items[1].reverseAllowed)
        ok(fixture("valid/route_detail.json"))
        assertEquals("Synthetic description for contract tests.", (client.route("35") as Outcome.Success).value.description)
        ok(fixture("valid/route_preview_open.json"))
        assertEquals("35", (client.routePreview() as Outcome.Success).value.routeId)
        ok(fixture("valid/settings_active_mod.json"))
        val settings = (client.settings() as Outcome.Success).value
        assertEquals("AUTOPILOT_ON", settings.avoidObstacles.reasonCode)
        assertEquals(50, settings.difficulty.value)
        assertEquals(Outcome.Failure("NOT_FOUND"), client.route("../state"))
    }

    @Test fun commandsEncodeExactlyLikeTheContractFixtures() {
        val cases = mapOf(
            "valid/command_routes_apply.json" to Command.ApplyRoute("35", loop = true, reverse = false, catalogRevision = "catalog-3f2a"),
            "valid/command_routes_free_ride.json" to Command.ApplyRoute(null, loop = false, reverse = false, catalogRevision = "catalog-3f2a"),
            "valid/command_profile_update.json" to Command.UpdateProfile(weightKg = 71, ftpW = 240, settingsRevision = "settings-9"),
            "valid/command_ride_settings.json" to Command.UpdateRide(difficulty = 75, settingsRevision = "settings-9"),
            "valid/command_appearance_bike.json" to Command.SelectBike("Example Gravel", "settings-9"),
            "valid/command_open_routes_panel.json" to Command.OpenRoutesPanel,
            "valid/command_mod_set_active.json" to null,
        )
        for ((file, command) in cases) {
            val expected = Json.parseToJsonElement(fixture(file)).jsonObject
            if (command == null) continue
            val encoded = BridgeClient.json.encodeToJsonElement(
                CommandDto.serializer(),
                Mapping.command(command, expected["requestId"]!!.toString().trim('"'), expected["adapterInstanceId"]!!.toString().trim('"')),
            ) as JsonObject
            assertEquals(file, expected, encoded)
        }
    }

    @Test fun submitReturnsOperationAndErrorsKeepTheirCode() = runBlocking {
        server.enqueue(MockResponse.Builder().code(202).body(fixture("valid/operation_routes_apply_applied.json")).build())
        val op = (client.submit(Command.ApplyRoute("35", true, false, "catalog-3f2a"), "adapter-5b1d") as Outcome.Success).value
        assertEquals(OperationStatus.APPLIED, op.status)
        val sent = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("\"routes.apply\"", sent["type"].toString())
        error(409, fixture("valid/error_workout_pc_control.json"))
        val failure = client.submit(Command.ApplyRoute(null, false, false, "catalog-3f2a"), "adapter-5b1d") as Outcome.Failure
        assertEquals("WORKOUT_PC_CONTROL_REQUIRED", failure.code)
        error(401, fixture("valid/error_auth_required.json"))
        assertEquals("AUTH_REQUIRED", (client.state() as Outcome.Failure).code)
        ok("{not json")
        assertEquals("INVALID_RESPONSE", (client.state() as Outcome.Failure).code)
    }

    @Test fun pairingSendsNoTokenAndUnreachablePcIsReported() = runBlocking {
        ok("""{"token":"${"a".repeat(43)}","expiresAtUtc":"2026-11-05T00:00:00.000Z","bridgeId":"bridge-0123456789ab"}""")
        assertTrue(client.pair("123456", "Phone") is Outcome.Success)
        assertNull(server.takeRequest().headers["Authorization"])
        server.close()
        assertEquals("PC_UNREACHABLE", (client.state() as Outcome.Failure).code)
    }

    @Test fun feedTurnsStaleWhenThePcStopsAnswering() = runBlocking {
        ok(fixture("valid/state_live_cycling.json"))
        ok(fixture("valid/capabilities_ready.json"))
        val feed = BridgeFeed(client, periodMs = 10)
        val first = feed.feed.take(1).toList().single()
        assertEquals(ChannelState.READY, first.channel)
        assertTrue(first.capabilities.isNotEmpty())
        server.close()
        val emissions = BridgeFeed(client, periodMs = 10).feed.take(1).toList()
        assertEquals(ChannelState.RECONNECTING, emissions.single().channel)
        assertTrue(emissions.single().snapshot.metrics.all().none { it.quality == Quality.VALID })
    }

    @Test fun backoffGrowsAndIsCapped() {
        val feed = BridgeFeed(client, random = kotlin.random.Random(1))
        assertTrue(feed.backoffMs(1) in 1_000L..1_250L)
        assertTrue(feed.backoffMs(4) in 8_000L..8_250L)
        assertTrue(feed.backoffMs(20) in 8_000L..8_250L)
    }

    @Test fun endpointRejectsHostNames() {
        assertTrue(runCatching { PcEndpoint("evil.example", 8788, PcEndpoint.Transport.HTTP_DEV) }.isFailure)
        assertTrue(runCatching { PcEndpoint("192.168.1.20", 80, PcEndpoint.Transport.HTTP_DEV) }.isFailure)
        assertTrue(runCatching { PcEndpoint("192.168.1.20", 8788, PcEndpoint.Transport.HTTP_DEV) }.isSuccess)
    }
}
