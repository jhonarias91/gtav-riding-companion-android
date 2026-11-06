package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.BotType
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.TrafficStyle
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameOptionsClientTest {
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

    @Test fun readsTheBridgeContractFixture() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(fixture("valid/mod_config.json")).build())
        val doc = (client.gameOptions() as Outcome.Success).value
        assertTrue(doc.editable)
        assertEquals("modcfg-0123456789abcdef01234567", doc.revision)
        assertEquals(listOf(BotType.CLIMBER, BotType.TIME_TRIALER, BotType.RANDOM, BotType.RANDOM, BotType.SPRINTER), doc.options!!.bots)
        assertEquals(TrafficStyle.NORMAL, doc.options!!.trafficStyle)
        assertEquals("/api/v1/mod-config", server.takeRequest().url.encodedPath)
    }

    @Test fun aRunningGameIsNeverEditableEvenIfTheFlagSaysSo() = runBlocking {
        val running = fixture("valid/mod_config.json").replace("\"gameRunning\": false", "\"gameRunning\": true")
        server.enqueue(MockResponse.Builder().code(200).body(running).build())
        assertFalse((client.gameOptions() as Outcome.Success).value.editable)
    }

    @Test fun savePostsOnlyTheChangesAndMapsErrors() = runBlocking {
        server.enqueue(MockResponse.Builder().code(200).body(fixture("valid/mod_config.json")).build())
        client.saveGameOptions("modcfg-0123456789abcdef01234567", mapOf("bots" to listOf(1, 3), "multiplayer" to false, "fineSteering" to 0.45))
        val sent = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("\"modcfg-0123456789abcdef01234567\"", sent["expectedRevision"].toString())
        assertEquals("""{"bots":[1,3],"multiplayer":false,"fineSteering":0.45}""", sent["options"].toString())
        server.enqueue(MockResponse.Builder().code(409).body("""{"error":{"code":"GAME_RUNNING","helpId":"modConfig.options","retryable":false,"operationId":null,"details":{}}}""").build())
        assertEquals(Outcome.Failure("GAME_RUNNING"), client.saveGameOptions("modcfg-0123456789abcdef01234567", mapOf("logLevel" to 4)))
        assertEquals(Outcome.Failure("INVALID_REQUEST"), client.saveGameOptions("modcfg-0123456789abcdef01234567", emptyMap()))
    }

    @Test fun anOlderBridgeWithoutTheEndpointAnswersNotFound() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).body("""{"error":{"code":"NOT_FOUND","helpId":null,"retryable":false,"operationId":null,"details":{}}}""").build())
        assertEquals(Outcome.Failure("NOT_FOUND"), client.gameOptions())
    }
}
