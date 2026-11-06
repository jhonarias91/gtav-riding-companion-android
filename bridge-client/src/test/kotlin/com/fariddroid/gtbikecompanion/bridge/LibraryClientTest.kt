package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DeviceAction
import com.fariddroid.gtbikecompanion.core.model.DevicePolicy
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PcDevice
import com.fariddroid.gtbikecompanion.core.model.PhoneSensorType
import com.fariddroid.gtbikecompanion.core.model.SegmentType
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryClientTest {
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

    @Test fun deviceRolesMapActionsAndPolicy() = runBlocking {
        ok(fixture("valid/device_roles.json"))
        val roles = (client.deviceRoles() as Outcome.Success).value
        val hr = roles.roles.first { it.role == SensorRoleType.HEART_RATE }
        assertEquals(DevicePolicy.FILTERED, hr.policy)
        assertEquals("18446744073709551615", hr.current?.deviceId)
        assertEquals("Polar H10 A1B2C3D4", hr.current?.name)
        assertEquals(null, roles.roles.first { it.role == SensorRoleType.POWER }.current?.name)
        assertTrue(hr.entries.single().excluded)
        val resistance = roles.roles.first { it.role == SensorRoleType.RESISTANCE }
        assertEquals(DevicePolicy.LOCKED, resistance.policy)
        assertFalse(resistance.canLock)
        assertFalse(roles.roles.first { it.role == SensorRoleType.STEERING }.canSkip)
        ok(fixture("valid/device_roles_inactive.json"))
        assertEquals("MOD_INACTIVE", (client.deviceRoles() as Outcome.Success).value.reasonCode)
    }

    @Test fun workoutsAreReadOnlyDocuments() = runBlocking {
        ok(fixture("valid/workout_plans.json"))
        val plans = (client.workoutPlans() as Outcome.Success).value
        assertEquals(230.0, plans.ftpReferenceW!!, 0.0)
        ok(fixture("valid/workouts_list.json"))
        assertEquals("Example Sweet Spot", (client.workouts("p-0123456789abcdef") as Outcome.Success).value.single().name)
        ok(fixture("valid/workout_detail.json"))
        val detail = (client.workout("w-0123456789abcdef") as Outcome.Success).value
        assertEquals(listOf(SegmentType.WARMUP, SegmentType.STEADY, SegmentType.COOLDOWN), detail.segments.map { it.type })
        ok(fixture("valid/workout_progress_active.json"))
        val progress = (client.workoutProgress() as Outcome.Success).value
        assertEquals(1888.0, progress.remaining!!, 0.0)
        ok(fixture("valid/workout_progress_none.json"))
        assertFalse((client.workoutProgress() as Outcome.Success).value.present)
        assertEquals(Outcome.Failure("NOT_FOUND"), client.workout("../state"))
    }

    @Test fun activitiesKeepNullsAndGaps() = runBlocking {
        ok(fixture("valid/activities_page.json"))
        assertEquals("GTBikeV_20261104_120000.fit", (client.activities(0, 50) as Outcome.Success).value.items.single().fileName)
        ok(fixture("valid/activity_detail.json"))
        val a = (client.activity("a-0123456789abcdef") as Outcome.Success).value
        assertNull(a.power!!.values[2])
        assertEquals(185.0, a.avgPowerW!!, 0.0)
    }

    @Test fun fitDownloadIsVerified() = runBlocking {
        val bytes = ByteArray(1000) { (it % 251).toByte() }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).setHeader("X-Content-SHA256", sha).build())
        val file = (client.downloadFit("a-0123456789abcdef", "ride.fit") as Outcome.Success).value
        assertArrayEquals(bytes, file.bytes)
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).setHeader("X-Content-SHA256", "0".repeat(64)).build())
        assertEquals(Outcome.Failure("FIT_INVALID"), client.downloadFit("a-0123456789abcdef", "ride.fit"))
    }

    @Test fun newCommandsEncodeLikeTheFixtures() {
        val cases = mapOf(
            "valid/command_devices_lock.json" to Command.ConfigureDevice(SensorRoleType.HEART_RATE, DeviceAction.LOCK, PcDevice("18446744073709551615", SensorSource.BLE, "HR"), "devices-1a2b"),
            "valid/command_devices_auto.json" to Command.ConfigureDevice(SensorRoleType.RESISTANCE, DeviceAction.AUTO, null, "devices-1a2b"),
            "valid/command_activities_finish.json" to Command.FinishActivity("session-3"),
            // P11: example trainer at the made-up address C0:FF:EE:00:00:01.
            "valid/command_devices_assign.json" to Command.AssignDevice(SensorRoleType.RESISTANCE, PhoneSensorType.TRAINER, "212205442170881", "devices-1a2b"),
            // P12: example chest strap at the made-up address C0:FF:EE:00:00:02.
            "valid/command_devices_disconnect.json" to Command.DisconnectDevice(PhoneSensorType.HEART_RATE, "212205442170882", "devices-1a2b"),
        )
        for ((file, command) in cases) {
            val expected = Json.parseToJsonElement(fixture(file)).jsonObject
            val encoded = BridgeClient.json.encodeToJsonElement(
                CommandDto.serializer(),
                Mapping.command(command, expected["requestId"]!!.toString().trim('"'), expected["adapterInstanceId"]!!.toString().trim('"')),
            ) as JsonObject
            assertEquals(file, expected, encoded)
        }
    }
}
