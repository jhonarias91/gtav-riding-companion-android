package com.fariddroid.gtbikecompanion.core.model

/** Where the data shown on screen comes from. Demo data must never be presented as a real PC. */
enum class DataOrigin { DEMO, BRIDGE }

/** Channel state machine from PLAN_ANDROID.md §14. Orthogonal to sensor state. */
enum class ChannelState {
    UNPAIRED, PERMISSION_NEEDED, PERMISSION_DENIED, CONNECTING, PAIRING, WAITING_FOR_ADAPTER,
    READY, STALE, RECONNECTING, SUSPENDED, INCOMPATIBLE, DEMO,
}

/**
 * v1 capability catalog (contracts/v1/policy.json). ERG, target power and workout control (pause, skip, end, bias)
 * do not exist here; P13 adds only workouts.select, the mod's own Select path.
 */
enum class CapabilityId(val wireId: String) {
    METRICS_READ("metrics.read"),
    SENSORS_READ("sensors.read"),
    ACTIVITY_READ("activity.read"),
    MOD_SET_ACTIVE("mod.setActive"),
    ROUTES_READ("routes.read"),
    ROUTES_PREVIEW("routes.preview"),
    ROUTES_APPLY("routes.apply"),
    UI_OPEN_PANEL("ui.openPanel"),
    UI_CLOSE_PANEL("ui.closePanel"),
    PROFILE_READ("profile.read"),
    PROFILE_UPDATE("profile.update"),
    RIDE_SETTINGS_UPDATE("rideSettings.update"),
    APPEARANCE_SELECT("appearance.select"),
    DEVICES_CONFIGURE("devices.configure"),
    WORKOUTS_READ("workouts.read"),
    WORKOUTS_OBSERVE("workouts.observe"),
    ACTIVITIES_LIST("activities.list"),
    ACTIVITIES_DOWNLOAD("activities.download"),
    ACTIVITIES_FINISH("activities.finish"),
    RIDERS_READ("riders.read"),
    MULTIPLAYER_JOIN_BUSIEST_ROUTE("multiplayer.joinBusiestRoute"),
    MAP_POSITION("map.position"),
    ADVANCED_SETTINGS_UPDATE("advancedSettings.update"),
    DEVICES_ASSIGN("devices.assign"),
    DEVICES_DISCONNECT("devices.disconnect"),
    WORKOUTS_SELECT("workouts.select");

    companion object {
        /** Unknown ids from a newer bridge are ignored and never enable an action. */
        fun fromWire(id: String): CapabilityId? = entries.firstOrNull { it.wireId == id }
    }
}

data class Capability(val id: CapabilityId, val available: Boolean, val reasonCode: String?, val helpId: String) {
    init {
        require(available == (reasonCode == null)) { "available capability has no reason; unavailable needs one" }
    }
}

/** Everything a screen needs to know about the feed it is rendering. */
data class TelemetryFeed(
    val origin: DataOrigin,
    val channel: ChannelState,
    val snapshot: TelemetrySnapshot,
    val capabilities: List<Capability>,
) {
    fun capability(id: CapabilityId): Capability? = capabilities.firstOrNull { it.id == id }
}
