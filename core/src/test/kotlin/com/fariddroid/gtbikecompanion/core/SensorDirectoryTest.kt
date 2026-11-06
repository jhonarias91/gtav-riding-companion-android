package com.fariddroid.gtbikecompanion.core

import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DeviceEntry
import com.fariddroid.gtbikecompanion.core.model.DevicePolicy
import com.fariddroid.gtbikecompanion.core.model.DeviceRole
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.PcDevice
import com.fariddroid.gtbikecompanion.core.model.PhoneSensor
import com.fariddroid.gtbikecompanion.core.model.PhoneSensorType
import com.fariddroid.gtbikecompanion.core.model.SensorDirectory
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** P12: one list with the sensors the PC uses (even when they stop advertising) and those the phone hears. */
class SensorDirectoryTest {
    private val kickrId = "212205442170881" // C0:FF:EE:00:00:01 (made up)
    private val strapId = "212205442170882" // C0:FF:EE:00:00:02
    private val kickr = PcDevice(kickrId, SensorSource.BLE, "FEC", "Wahoo KICKR 0001")
    private val antHr = PcDevice("54321", SensorSource.ANT, "HR")

    private fun role(r: SensorRoleType, current: PcDevice?, visible: Boolean = true, policy: DevicePolicy = DevicePolicy.AUTO, entries: List<DeviceEntry> = emptyList()) =
        DeviceRole(r, visible, current, policy, entries, canAuto = policy != DevicePolicy.AUTO, canLock = current != null, canSkip = current != null)

    @Test fun pcDevicesAreListedOnceWithAllTheirRolesAndConnectedFirst() {
        val roles = DeviceRoles("devices-1", null, listOf(
            role(SensorRoleType.POWER, kickr), role(SensorRoleType.RESISTANCE, kickr), role(SensorRoleType.HEART_RATE, antHr),
            role(SensorRoleType.RUNNING_SPEED, kickr, visible = false),
        ))
        val phone = listOf(PhoneSensor("C0:FF:EE:00:00:02", "HRM 0002", PhoneSensorType.HEART_RATE, -55))
        val list = SensorDirectory.merge(phone, roles)
        assertEquals(3, list.size)
        val first = list[0]
        assertEquals(kickrId, first.deviceId)
        assertEquals("Wahoo KICKR 0001", first.name)
        assertEquals(PhoneSensorType.TRAINER, first.type)
        assertEquals(listOf(SensorRoleType.POWER, SensorRoleType.RESISTANCE), first.connectedRoles)
        assertNull("only the PC knows it", first.rssi)
        assertNull("ANT sensors cannot be acted on from the phone", list[1].type)
        assertEquals(strapId, list[2].deviceId)
        assertEquals(false, list[2].connected)
        assertEquals("01:01".length, list[2].shortAddress!!.length)
        assertEquals("00:02", list[2].shortAddress)
    }

    @Test fun phoneSignalAndNameFillTheGapsOfAConnectedSensor() {
        val unnamed = kickr.copy(name = null)
        val roles = DeviceRoles("devices-1", null, listOf(role(SensorRoleType.RESISTANCE, unnamed)))
        val list = SensorDirectory.merge(listOf(PhoneSensor("C0:FF:EE:00:00:01", "KICKR 0001", PhoneSensorType.TRAINER, -48)), roles)
        assertEquals(1, list.size)
        assertEquals("KICKR 0001", list.single().name)
        assertEquals(-48, list.single().rssi)
        assertEquals(true, list.single().connected)
    }

    @Test fun aLockedRuleWithoutConnectionIsWaiting() {
        val entry = DeviceEntry(PcDevice(strapId, SensorSource.BLE, "HR"), excluded = false)
        val roles = DeviceRoles("devices-1", null, listOf(role(SensorRoleType.HEART_RATE, null, policy = DevicePolicy.LOCKED, entries = listOf(entry))))
        val list = SensorDirectory.merge(listOf(PhoneSensor("C0:FF:EE:00:00:02", null, PhoneSensorType.HEART_RATE, -70)), roles)
        assertEquals(listOf(SensorRoleType.HEART_RATE), list.single().waitingRoles)
        assertEquals(false, list.single().connected)
    }

    @Test fun connectUsesTheFirstFunctionThePcShows() {
        val cycling = DeviceRoles("devices-1", null, listOf(role(SensorRoleType.HEART_RATE, null), role(SensorRoleType.RUNNING_SPEED, null, visible = false)))
        assertEquals(SensorRoleType.HEART_RATE, SensorDirectory.connectRole(PhoneSensorType.HEART_RATE, cycling))
        assertNull(SensorDirectory.connectRole(PhoneSensorType.STEERING, cycling))
        assertNull(SensorDirectory.connectRole(PhoneSensorType.HEART_RATE, null))
    }

    @Test fun disconnectNamesABleAddressOnly() {
        assertEquals("devices.disconnect", Command.DisconnectDevice(PhoneSensorType.HEART_RATE, strapId, "devices-1").wireType)
        assertThrows(IllegalArgumentException::class.java) { Command.DisconnectDevice(PhoneSensorType.HEART_RATE, "0", "devices-1") }
        assertEquals(PhoneSensorType.STEERING, PhoneSensorType.fromWire("BTS"))
        assertNull(PhoneSensorType.fromWire("STZ"))
    }
}
