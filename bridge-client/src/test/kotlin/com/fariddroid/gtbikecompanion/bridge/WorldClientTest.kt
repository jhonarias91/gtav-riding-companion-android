package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.RiderKind
import java.io.File
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorldClientTest {
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

    @Test fun mapKeepsBreaksAndOutsideCoverage() = runBlocking {
        ok(fixture("valid/map_with_route.json"))
        val map = (client.gameMap() as Outcome.Success).value
        assertEquals(0.41, map.position!!.u, 0.0)
        assertNull(map.route!!.waypoints[2])
        ok(fixture("valid/map_outside_coverage.json"))
        val outside = (client.gameMap() as Outcome.Success).value
        assertNull(outside.position)
        assertNull(outside.route)
    }

    @Test fun ridersSeparateHumansBotsAndYou() = runBlocking {
        ok(fixture("valid/riders_online.json"))
        val board = (client.riders() as Outcome.Success).value
        assertEquals(listOf(RiderKind.HUMAN, RiderKind.LOCAL, RiderKind.BOT), board.riders.map { it.kind })
        assertNull(board.riders[2].name)
        ok(fixture("valid/riders_free_ride_suggestion.json"))
        val free = (client.riders() as Outcome.Success).value
        assertTrue(free.busiestRoute!!.reverse)
    }

    @Test fun mapImageMustBeABoundedPng() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(png)).build())
        assertTrue(client.mapImage("map-" + "a".repeat(64)) is Outcome.Success)
        server.enqueue(MockResponse.Builder().code(200).body("<html>not a png</html>").build())
        assertEquals(Outcome.Failure("ASSET_UNAVAILABLE"), client.mapImage("map-" + "a".repeat(64)))
        assertEquals(Outcome.Failure("NOT_FOUND"), client.mapImage("../state"))
    }
}
