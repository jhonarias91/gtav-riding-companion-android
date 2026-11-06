package com.fariddroid.gtbikecompanion.core

import com.fariddroid.gtbikecompanion.core.model.AssignmentState
import com.fariddroid.gtbikecompanion.core.model.BleAddress
import com.fariddroid.gtbikecompanion.core.model.BleAdvertisement
import com.fariddroid.gtbikecompanion.core.model.BleSensorClassifier
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DeviceEntry
import com.fariddroid.gtbikecompanion.core.model.DevicePolicy
import com.fariddroid.gtbikecompanion.core.model.DeviceRole
import com.fariddroid.gtbikecompanion.core.model.PcDevice
import com.fariddroid.gtbikecompanion.core.model.PhoneAssignment
import com.fariddroid.gtbikecompanion.core.model.PhoneSensorType
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** P11. Advertisements shaped like the ones nRF Connect shows for these sensors; addresses and names are made up. */
class PhoneSensorsTest {
    private fun ad(address: String, name: String?, vararg uuids: String, mfg: Map<Int, ByteArray> = emptyMap()) =
        BleAdvertisement(address, name, uuids.toSet(), mfg, -60)

    private val kickr = ad("C0:FF:EE:00:00:01", "Wahoo KICKR 0001", BleSensorClassifier.CPS, BleSensorClassifier.FTMS)
    private val strap = ad("C0:FF:EE:00:00:02", "HRM 0002", BleSensorClassifier.HRS)
    private val sterzo = ad("C0:FF:EE:00:00:03", "STERZO", BleSensorClassifier.STERZO)
    // Xiaomi Band 9 Active: only service data (FE95, FDAB), no service UUID list -> the PC ignores it.
    private val band = ad("C0:FF:EE:00:00:04", "Xiaomi Band 9 Active 0004")

    @Test fun classifiesLikeThePcReceiver() {
        assertEquals(PhoneSensorType.TRAINER, BleSensorClassifier.classify(kickr)) // FTMS without Wahoo service -> generic FTMS (FEC)
        assertEquals(PhoneSensorType.HEART_RATE, BleSensorClassifier.classify(strap))
        assertEquals(PhoneSensorType.STEERING, BleSensorClassifier.classify(sterzo))
        assertNull(BleSensorClassifier.classify(band))
        assertEquals(PhoneSensorType.SMART_BIKE, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:01", "KICKR BIKE 12", BleSensorClassifier.FTMS)))
        assertEquals(PhoneSensorType.SMART_BIKE, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:02", "x", BleSensorClassifier.FTMS, BleSensorClassifier.WAHOO, BleSensorClassifier.CPS)))
        assertEquals(PhoneSensorType.TRAINER, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:03", "Tacx Neo 2T", BleSensorClassifier.TACX_FEC)))
        assertEquals(PhoneSensorType.POWER_METER, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:04", null, BleSensorClassifier.CPS, BleSensorClassifier.HRS)))
        assertEquals(PhoneSensorType.SPEED_CADENCE, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:05", null, BleSensorClassifier.CSCS)))
        // Zwift controllers need the Zwift manufacturer data, like the PC.
        assertNull(BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:06", "Zwift Click", BleSensorClassifier.ZWIFT_16)))
        assertEquals(PhoneSensorType.CONTROLLER, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:07", "Zwift Click", BleSensorClassifier.ZWIFT_16, mfg = mapOf(2378 to byteArrayOf(9)))))
        val alpine = mapOf(135 to byteArrayOf(17, 209.toByte(), 1))
        assertEquals(PhoneSensorType.STEERING, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:08", "Alpine", BleSensorClassifier.GARMIN_16, mfg = alpine)))
        assertEquals("upper-case UUIDs from the platform", PhoneSensorType.HEART_RATE, BleSensorClassifier.classify(ad("AA:BB:CC:DD:EE:09", null, BleSensorClassifier.HRS.uppercase())))
    }

    @Test fun deviceIdIsTheMacAsOneNumber() {
        // Same values Test-BindingResolve.ps1 checks against the real mod (BLE:FEC:C0FFEE000001, ...).
        assertEquals("212205442170881", BleSensorClassifier.sensor(kickr)!!.deviceId)
        assertEquals("212205442170882", BleSensorClassifier.sensor(strap)!!.deviceId)
        assertEquals("212205442170883", BleSensorClassifier.sensor(sterzo)!!.deviceId)
        assertEquals("C0:FF:EE:00:00:01", BleAddress.fromDeviceId("212205442170881"))
        assertEquals("00:00:00:00:00:01", BleAddress.fromDeviceId("1"))
        assertNull(BleAddress.fromDeviceId("0"))
        assertNull(BleAddress.fromDeviceId("281474976710656"))
        assertNull(BleSensorClassifier.sensor(ad("not-a-mac", null, BleSensorClassifier.HRS)))
    }

    /** Must stay the inverse of ROLE_SENSOR_TYPES in gtav-riding-companion-bridge/bridge/api-v1/validate.mjs and GtbikeV1More.RoleTypes. */
    @Test fun rolesMatchTheBridgeTable() {
        val bridge = mapOf(
            SensorRoleType.POWER to setOf("CPS", "KICKR", "FEC", "CAD"),
            SensorRoleType.RESISTANCE to setOf("KICKR", "FEC"),
            SensorRoleType.CADENCE to setOf("CPS", "FEC", "KICKR", "CAD"),
            SensorRoleType.HEART_RATE to setOf("HR", "FEC", "FPOD"),
            SensorRoleType.STEERING to setOf("BTS", "CTRLS", "KICKR"),
            SensorRoleType.CONTROLS to setOf("CTRLS", "KICKR"),
            SensorRoleType.RUNNING_SPEED to setOf("FPOD", "FEC", "HR"),
        )
        for (role in SensorRoleType.entries) {
            assertEquals(role.name, bridge.getValue(role), PhoneSensorType.entries.filter { role in it.roles }.map { it.wire }.toSet())
        }
        assertEquals(SensorRoleType.RESISTANCE, PhoneSensorType.TRAINER.suggestedRole)
        assertEquals(SensorRoleType.HEART_RATE, PhoneSensorType.HEART_RATE.suggestedRole)
        assertEquals(SensorRoleType.STEERING, PhoneSensorType.STEERING.suggestedRole)
    }

    @Test fun commandRejectsImpossibleAssignments() {
        Command.AssignDevice(SensorRoleType.RESISTANCE, PhoneSensorType.TRAINER, "212205442170881", "devices-1")
        assertThrows(IllegalArgumentException::class.java) { Command.AssignDevice(SensorRoleType.HEART_RATE, PhoneSensorType.STEERING, "212205442170883", "devices-1") }
        assertThrows(IllegalArgumentException::class.java) { Command.AssignDevice(SensorRoleType.HEART_RATE, PhoneSensorType.HEART_RATE, "0", "devices-1") }
    }

    @Test fun assignmentStateFollowsThePc() {
        val strapId = "212205442170882"
        val locked = listOf(DeviceEntry(PcDevice(strapId, SensorSource.BLE, "HR"), excluded = false))
        fun role(current: PcDevice?, policy: DevicePolicy, entries: List<DeviceEntry>) =
            DeviceRole(SensorRoleType.HEART_RATE, true, current, policy, entries, canAuto = policy != DevicePolicy.AUTO, canLock = false, canSkip = false)
        assertEquals(AssignmentState.NOT_ASSIGNED, PhoneAssignment.state(role(null, DevicePolicy.AUTO, emptyList()), strapId))
        assertEquals(AssignmentState.WAITING, PhoneAssignment.state(role(null, DevicePolicy.LOCKED, locked), strapId))
        assertEquals(AssignmentState.OTHER_DEVICE, PhoneAssignment.state(role(PcDevice("54321", SensorSource.ANT, "HR"), DevicePolicy.LOCKED, locked), strapId))
        assertEquals(AssignmentState.CONNECTED, PhoneAssignment.state(role(PcDevice(strapId, SensorSource.BLE, "HR"), DevicePolicy.LOCKED, locked), strapId))
        assertEquals("connected under Auto too", AssignmentState.CONNECTED, PhoneAssignment.state(role(PcDevice(strapId, SensorSource.BLE, "HR"), DevicePolicy.AUTO, emptyList()), strapId))
    }
}
