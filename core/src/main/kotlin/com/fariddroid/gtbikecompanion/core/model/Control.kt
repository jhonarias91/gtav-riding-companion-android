package com.fariddroid.gtbikecompanion.core.model

/**
 * Domain mirror of contract v1 for P2-P4 actions: routes, route preview, settings, commands and operations.
 * Network DTOs map into these; screens never see JSON, CLR names or list indexes.
 */

/** Result of a call to the PC. Failures carry a stable code that the UI translates (never server text). */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val code: String, val operationId: String? = null) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

// ---- routes (P3) ----

data class RouteSummary(
    /** Base catalog id (CourseInfo.Id without the reverse bit) as string. */
    val id: String,
    val name: String,
    val author: String?,
    val distanceM: Double?,
    val elevationGainM: Double?,
    val elevationLossM: Double?,
    val maxElevationM: Double?,
    val categories: List<String>,
    val loopAllowed: Boolean,
    val reverseAllowed: Boolean,
) {
    init {
        require(!reverseAllowed || loopAllowed) { "reverse requires a loop-capable route" }
    }
}

data class RouteDetail(val summary: RouteSummary, val description: String?, val notes: String?)

data class RoutePage(val catalogRevision: String?, val total: Int, val offset: Int, val items: List<RouteSummary>)

/** What the PC selector shows. Never the active route. */
data class RoutePreview(val catalogRevision: String?, val panelOpen: Boolean, val routeId: String?, val loop: Boolean?, val reverse: Boolean?)

// ---- settings (P4) ----

data class NumberSetting(val value: Double?, val min: Double, val max: Double, val editable: Boolean, val reasonCode: String?)

data class BoolSetting(val value: Boolean?, val editable: Boolean, val reasonCode: String?)

data class DifficultySetting(val value: Int?, val allowed: List<Int>, val editable: Boolean, val reasonCode: String?) {
    companion object {
        /** Native DifficultyLevel list (Menus.cs:283); 255 = disabled, which is not 0 %. */
        val LEVELS = listOf(0, 25, 50, 75, 100, 255)
        const val DISABLED = 255
    }
}

data class ChoiceOption(val id: String, val label: String)

data class ChoiceSetting(val selectedId: String?, val options: List<ChoiceOption>, val editable: Boolean, val reasonCode: String?)

data class PcSettings(
    val revision: String?,
    val weightKg: NumberSetting,
    val ftpW: NumberSetting,
    val hrMaxBpm: NumberSetting,
    val autopilot: BoolSetting,
    val avoidObstacles: BoolSetting,
    val difficulty: DifficultySetting,
    val roadFeel: BoolSetting,
    val bike: ChoiceSetting,
    val character: ChoiceSetting,
)

// ---- commands and operations ----

/** The complete v1 allowlist. There is no ERG, target power, gear, bias or workout control type. */
sealed interface Command {
    val wireType: String

    data class SetModActive(val enabled: Boolean) : Command { override val wireType = "mod.setActive" }
    data class ApplyRoute(val routeId: String?, val loop: Boolean, val reverse: Boolean, val catalogRevision: String) : Command {
        override val wireType = "routes.apply"
        init { require(routeId != null || (!loop && !reverse)) { "free ride has no flags" } }
    }
    data class PreviewRoute(val routeId: String?, val catalogRevision: String) : Command { override val wireType = "routes.preview" }
    data object OpenRoutesPanel : Command { override val wireType = "ui.openPanel" }
    data object CloseRoutesPanel : Command { override val wireType = "ui.closePanel" }
    data class UpdateProfile(val weightKg: Int? = null, val ftpW: Int? = null, val hrMaxBpm: Int? = null, val settingsRevision: String) : Command {
        override val wireType = "profile.update"
        init { require(weightKg != null || ftpW != null || hrMaxBpm != null) { "nothing to update" } }
    }
    data class UpdateRide(
        val autopilot: Boolean? = null,
        val avoidObstacles: Boolean? = null,
        val difficulty: Int? = null,
        val roadFeel: Boolean? = null,
        val settingsRevision: String,
    ) : Command {
        override val wireType = "rideSettings.update"
        init {
            require(autopilot != null || avoidObstacles != null || difficulty != null || roadFeel != null) { "nothing to update" }
            require(difficulty == null || difficulty in DifficultySetting.LEVELS) { "difficulty must be a native level" }
        }
    }
    data class SelectBike(val bikeId: String, val settingsRevision: String) : Command { override val wireType = "appearance.select" }
    data class SelectCharacter(val characterId: String, val settingsRevision: String) : Command { override val wireType = "appearance.select" }

    /** Lock/Skip name the device the user saw; the PC answers DEVICE_CHANGED if it switched meanwhile. */
    data class ConfigureDevice(val role: SensorRoleType, val action: DeviceAction, val expected: PcDevice?, val devicesRevision: String) : Command {
        override val wireType = "devices.configure"
        init { require((action == DeviceAction.AUTO) == (expected == null)) { "auto names no device; lock/skip name one" } }
    }

    /** P11: locks a BLE sensor the phone heard for one function; the PC connects to it. Source is always BLE. */
    /** P12: the PC drops a BLE sensor it uses now; that sensor type stays free until one is connected again. */
    data class DisconnectDevice(val sensorType: PhoneSensorType, val deviceId: String, val devicesRevision: String) : Command {
        override val wireType = "devices.disconnect"
        init { require(BleAddress.fromDeviceId(deviceId) != null && deviceId.matches(Regex("^[1-9][0-9]{0,14}$"))) { "deviceId must be a 48-bit BLE address" } }
    }

    data class AssignDevice(val role: SensorRoleType, val sensorType: PhoneSensorType, val deviceId: String, val devicesRevision: String) : Command {
        override val wireType = "devices.assign"
        init {
            require(role in sensorType.roles) { "${sensorType.wire} cannot serve $role" }
            require(BleAddress.fromDeviceId(deviceId) != null && deviceId.matches(Regex("^[1-9][0-9]{0,14}$"))) { "deviceId must be a 48-bit BLE address" }
        }
    }

    /**
     * P13: starts a workout like the Select button of the mod's workout selector; the mod then engages ERG itself.
     * [catalogRevision] is the workout library revision the user browsed. There is no pause, skip, end or bias command.
     */
    data class SelectWorkout(val workoutId: String, val catalogRevision: String) : Command {
        override val wireType = "workouts.select"
        init { require(workoutId.matches(Regex("^w-[a-f0-9]{16}$"))) { "workoutId must be a catalog id" } }
    }

    /** Saves the current activity like the native Save item. There is no discard or delete command. */
    data class FinishActivity(val sessionGeneration: String) : Command { override val wireType = "activities.finish" }
}

enum class OperationStatus {
    RECEIVED, VALIDATED, DISPATCHED, PENDING, APPLIED, REJECTED, FAILED, UNKNOWN;

    val isFinal: Boolean get() = this == APPLIED || this == REJECTED || this == FAILED || this == UNKNOWN
}

data class Operation(val requestId: String, val type: String, val status: OperationStatus, val errorCode: String?)
