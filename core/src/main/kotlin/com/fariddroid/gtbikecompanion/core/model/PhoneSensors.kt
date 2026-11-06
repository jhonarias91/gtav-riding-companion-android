package com.fariddroid.gtbikecompanion.core.model

import java.util.Locale

/**
 * P11: Bluetooth sensors found by the phone and handed to the PC. The phone only listens to advertisements and
 * never connects (a sensor usually accepts a single connection, which must stay free for the PC). The PC then
 * locks the sensor by address and connects to it directly, so riding data never passes through the phone.
 */

/** One advertisement as the phone heard it. Service UUIDs are lowercase 128-bit strings. */
data class BleAdvertisement(
    val address: String,
    val name: String?,
    val serviceUuids: Set<String>,
    val manufacturerData: Map<Int, ByteArray>,
    val rssi: Int,
)

/**
 * Sensor types GTBike V 0.8.0.4 derives from an advertisement (BLE_Receiver DeviceFactory), named as the mod's
 * SensorDeviceType. [roles] lists the functions the type can serve (BLE DeviceFactory.DevicePreference); the first
 * one is what the phone suggests.
 */
enum class PhoneSensorType(val wire: String, val roles: List<SensorRoleType>) {
    SMART_BIKE("KICKR", listOf(SensorRoleType.RESISTANCE, SensorRoleType.POWER, SensorRoleType.CADENCE, SensorRoleType.STEERING, SensorRoleType.CONTROLS)),
    TRAINER("FEC", listOf(SensorRoleType.RESISTANCE, SensorRoleType.POWER, SensorRoleType.CADENCE, SensorRoleType.HEART_RATE, SensorRoleType.RUNNING_SPEED)),
    POWER_METER("CPS", listOf(SensorRoleType.POWER, SensorRoleType.CADENCE)),
    SPEED_CADENCE("CAD", listOf(SensorRoleType.CADENCE, SensorRoleType.POWER)),
    RUNNING_POD("FPOD", listOf(SensorRoleType.RUNNING_SPEED, SensorRoleType.HEART_RATE)),
    STEERING("BTS", listOf(SensorRoleType.STEERING)),
    HEART_RATE("HR", listOf(SensorRoleType.HEART_RATE, SensorRoleType.RUNNING_SPEED)),
    CONTROLLER("CTRLS", listOf(SensorRoleType.CONTROLS, SensorRoleType.STEERING)),
    ;

    val suggestedRole: SensorRoleType get() = roles.first()

    companion object {
        fun fromWire(wire: String?): PhoneSensorType? = entries.firstOrNull { it.wire == wire }
    }
}

/** A sensor the PC would recognise, with the id GTBike V will store for it. */
data class PhoneSensor(
    val address: String,
    val name: String?,
    val type: PhoneSensorType,
    val rssi: Int,
) {
    /** Windows' BluetoothAddress, which GTBike V uses as DeviceId: the MAC read as one 48-bit number. */
    val deviceId: String get() = BleAddress.toDeviceId(address)
}

object BleAddress {
    private val MAC = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    fun isValid(address: String): Boolean = MAC.matches(address) && address != "00:00:00:00:00:00"

    /** "C0:FF:EE:00:00:01" -> "212205442170881" (0xC0FFEE000001). */
    fun toDeviceId(address: String): String {
        require(isValid(address)) { "not a BLE MAC address: $address" }
        return address.replace(":", "").toLong(16).toString()
    }

    /** Inverse of [toDeviceId], for showing the PC's id like the phone shows the address. */
    fun fromDeviceId(deviceId: String): String? {
        val value = deviceId.toULongOrNull() ?: return null
        if (value == 0UL || value > 0xFFFFFFFFFFFFUL) return null
        return value.toString(16).padStart(12, '0').uppercase(Locale.ROOT).chunked(2).joinToString(":")
    }
}

/**
 * Port of BLE_Receiver.dll DeviceFactory.GetDeviceForAdvertisement (0.8.0.4), in the same order, so the phone
 * offers exactly what the PC's receiver would accept. Returns null for anything the PC would ignore, including
 * advertisements without any service UUID (the PC's watcher drops those).
 */
object BleSensorClassifier {
    private fun uuid16(short: String) = "0000$short-0000-1000-8000-00805f9b34fb"

    val FTMS = uuid16("1826")
    val CPS = uuid16("1818")
    val CSCS = uuid16("1816")
    val RSCS = uuid16("1814")
    val HRS = uuid16("180d")
    val ZWIFT_16 = uuid16("fc82")
    val GARMIN_16 = uuid16("fe1f")
    const val WAHOO = "a026ee0d-0a7d-4ab3-97fa-f1500f9feb8b"
    const val STERZO = "347b0001-7635-408b-8918-8ff3949ce592"
    const val ZWIFT = "00000001-19ca-4651-86e5-fa29dcdd09d1"
    const val TACX_FEC = "6e40fec1-b5a3-f393-e0a9-e50e24dcca9e"
    private const val GARMIN_ID = 135
    private const val ZWIFT_ID = 2378

    fun classify(ad: BleAdvertisement): PhoneSensorType? {
        val uuids = ad.serviceUuids.map { it.lowercase(Locale.ROOT) }.toSet()
        if (uuids.isEmpty()) return null
        val name = ad.name.orEmpty()
        if (name.contains("KICKR BIKE") || (FTMS in uuids && WAHOO in uuids && CPS in uuids)) return PhoneSensorType.SMART_BIKE
        if (FTMS in uuids) return if (name.contains("Schwinn")) PhoneSensorType.SMART_BIKE else PhoneSensorType.TRAINER
        if (name.contains("Tacx Neo") || (TACX_FEC in uuids && CPS in uuids)) return PhoneSensorType.TRAINER
        if (CPS in uuids) return PhoneSensorType.POWER_METER
        if (CSCS in uuids) return PhoneSensorType.SPEED_CADENCE
        if (RSCS in uuids) return PhoneSensorType.RUNNING_POD
        if (STERZO in uuids) return PhoneSensorType.STEERING
        val garmin = ad.manufacturerData[GARMIN_ID]
        if (GARMIN_16 in uuids && garmin != null && garmin.size >= 3 && garmin[0] == 17.toByte() && garmin[1] == 209.toByte() && garmin[2] == 1.toByte()) {
            return PhoneSensorType.STEERING // Tacx Alpine
        }
        if (HRS in uuids) return PhoneSensorType.HEART_RATE
        if ((ZWIFT in uuids || ZWIFT_16 in uuids) && ad.manufacturerData.containsKey(ZWIFT_ID)) return PhoneSensorType.CONTROLLER
        return null
    }

    fun sensor(ad: BleAdvertisement): PhoneSensor? {
        if (!BleAddress.isValid(ad.address)) return null
        val type = classify(ad) ?: return null
        return PhoneSensor(ad.address.uppercase(Locale.ROOT), ad.name?.takeIf { it.isNotBlank() }, type, ad.rssi)
    }
}

/** Where a phone-found sensor stands for one function on the PC (read from device-roles). */
enum class AssignmentState {
    /** The PC is already receiving this function from this sensor. */
    CONNECTED,

    /** The PC rule names this sensor, but the PC has not connected to it yet. */
    WAITING,

    /** The PC rule names this sensor, but the PC still uses another device for the function. */
    OTHER_DEVICE,

    /** The PC rule does not name this sensor. */
    NOT_ASSIGNED,
}

object PhoneAssignment {
    fun state(role: DeviceRole?, deviceId: String): AssignmentState {
        if (role == null) return AssignmentState.NOT_ASSIGNED
        val current = role.current
        if (current != null && current.source == SensorSource.BLE && current.deviceId == deviceId) return AssignmentState.CONNECTED
        val locked = role.policy == DevicePolicy.LOCKED && role.entries.any { !it.excluded && it.device.source == SensorSource.BLE && it.device.deviceId == deviceId }
        if (!locked) return AssignmentState.NOT_ASSIGNED
        return if (current == null) AssignmentState.WAITING else AssignmentState.OTHER_DEVICE
    }
}

/**
 * P12: one row of the "Search sensors" list. It merges what the PC uses now (device-roles, so a sensor already
 * connected to the PC is listed even if it no longer advertises) with what the phone hears.
 */
data class SensorListItem(
    val deviceId: String,
    val source: SensorSource,
    /** Name advertised by the sensor: the PC's when it uses it, otherwise the one the phone heard. */
    val name: String?,
    /** Type as the PC's BLE receiver classifies it; null for ANT or a type the phone cannot act on. */
    val type: PhoneSensorType?,
    /** Raw sensor type the PC reports (FEC, HR, STZ...), for ANT sensors and unknown types. */
    val pcSensorType: String?,
    /** Functions the PC is receiving from this sensor now. */
    val connectedRoles: List<SensorRoleType>,
    /** Functions whose PC rule names this sensor while the PC is not connected to it yet. */
    val waitingRoles: List<SensorRoleType>,
    /** Signal on the phone; null when only the PC knows the sensor. */
    val rssi: Int?,
) {
    val connected: Boolean get() = connectedRoles.isNotEmpty()

    /** Last two address bytes, only to tell apart two unnamed sensors of the same type. */
    val shortAddress: String? get() = if (source == SensorSource.BLE) BleAddress.fromDeviceId(deviceId)?.takeLast(5) else null
}

object SensorDirectory {
    fun merge(phone: List<PhoneSensor>, roles: DeviceRoles?): List<SensorListItem> {
        val visible = roles?.roles?.filter { it.visible }.orEmpty()
        val items = LinkedHashMap<String, SensorListItem>()
        for (role in visible) {
            val current = role.current ?: continue
            val key = current.source.name + ":" + current.deviceId
            val known = items[key]
            items[key] = known?.copy(connectedRoles = known.connectedRoles + role.role, name = known.name ?: current.name)
                ?: SensorListItem(
                    deviceId = current.deviceId,
                    source = current.source,
                    name = current.name,
                    type = if (current.source == SensorSource.BLE) PhoneSensorType.fromWire(current.sensorType) else null,
                    pcSensorType = current.sensorType,
                    connectedRoles = listOf(role.role),
                    waitingRoles = emptyList(),
                    rssi = null,
                )
        }
        for (sensor in phone) {
            val key = SensorSource.BLE.name + ":" + sensor.deviceId
            val known = items[key]
            items[key] = known?.copy(name = known.name ?: sensor.name, type = known.type ?: sensor.type, rssi = sensor.rssi)
                ?: SensorListItem(sensor.deviceId, SensorSource.BLE, sensor.name, sensor.type, sensor.type.wire, emptyList(), emptyList(), sensor.rssi)
        }
        // Rules that name a BLE sensor the PC has not connected to yet.
        for (role in visible) {
            if (role.policy != DevicePolicy.LOCKED) continue
            for (entry in role.entries) {
                if (entry.excluded || entry.device.source != SensorSource.BLE) continue
                val key = SensorSource.BLE.name + ":" + entry.device.deviceId
                val known = items[key] ?: continue
                if (role.role !in known.connectedRoles && role.role !in known.waitingRoles) items[key] = known.copy(waitingRoles = known.waitingRoles + role.role)
            }
        }
        return items.values.sortedWith(compareByDescending<SensorListItem> { it.connected }.thenByDescending { it.waitingRoles.isNotEmpty() }.thenByDescending { it.rssi ?: Int.MIN_VALUE })
    }

    /** Function a "Connect" tap asks the PC to use the sensor for: the first one the PC currently shows. */
    fun connectRole(type: PhoneSensorType, roles: DeviceRoles?): SensorRoleType? {
        val shown = roles?.roles?.filter { it.visible }?.map { it.role }?.toSet() ?: return null
        return type.roles.firstOrNull { it in shown }
    }
}
