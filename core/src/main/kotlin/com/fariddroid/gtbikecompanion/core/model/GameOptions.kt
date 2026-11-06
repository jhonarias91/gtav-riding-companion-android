package com.fariddroid.gtbikecompanion.core.model

/**
 * P15 game options: the subset of GTBike V's GTBikeVConfig.ini the bridge manages (GET/POST /api/v1/mod-config).
 * They can be changed only with GTA V closed (GTBike V rewrites the file while it runs) and apply the next time the
 * game starts. A null field means the line is missing or unreadable in the file; the mod then uses its default.
 */
enum class TrafficStyle(val wire: String) {
    NORMAL("NORMAL"),
    AGGRESSIVE("AGRESSIVE"), // Spelled as GTBike V parses it.
    BREAKS_LAWS("BREAKLAWS");

    companion object {
        fun fromWire(value: String?): TrafficStyle? = entries.firstOrNull { it.wire == value }
    }
}

/** GTBike V route bot types (BotDefinition values). */
enum class BotType(val wire: Int) {
    RANDOM(0), COMPANION(1), CLIMBER(2), SPRINTER(3), TIME_TRIALER(4);

    companion object {
        fun fromWire(value: Int): BotType? = entries.firstOrNull { it.wire == value }
    }
}

data class GameOptions(
    val trafficBikesPercent: Int? = null,
    val trafficStyle: TrafficStyle? = null,
    val crowdsEnabled: Boolean? = null,
    val crowdsSlopePercent: Int? = null,
    val crowdsAmount: Int? = null,
    val bots: List<BotType>? = null,
    val defensiveLevel: Int? = null,
    /** AARRGGBB. */
    val waypointColor: String? = null,
    val waypointRadius: Int? = null,
    val fineSteering: Double? = null,
    val screenshotIntervalKm: Int? = null,
    val screenshotXOffset: Int? = null,
    val menuAutoOpen: Boolean? = null,
    val showClock: Boolean? = null,
    val debugWindow: Boolean? = null,
    val logLevel: Int? = null,
    val gpsOriginLat: Double? = null,
    val gpsOriginLon: Double? = null,
    val telemetry: Boolean? = null,
    val multiplayer: Boolean? = null,
) {
    /** Missing values replaced by [defaults]: what the mod actually uses. */
    fun orDefaults(defaults: GameOptions) = GameOptions(
        trafficBikesPercent ?: defaults.trafficBikesPercent, trafficStyle ?: defaults.trafficStyle,
        crowdsEnabled ?: defaults.crowdsEnabled, crowdsSlopePercent ?: defaults.crowdsSlopePercent, crowdsAmount ?: defaults.crowdsAmount,
        bots ?: defaults.bots, defensiveLevel ?: defaults.defensiveLevel, waypointColor ?: defaults.waypointColor,
        waypointRadius ?: defaults.waypointRadius, fineSteering ?: defaults.fineSteering,
        screenshotIntervalKm ?: defaults.screenshotIntervalKm, screenshotXOffset ?: defaults.screenshotXOffset,
        menuAutoOpen ?: defaults.menuAutoOpen, showClock ?: defaults.showClock, debugWindow ?: defaults.debugWindow,
        logLevel ?: defaults.logLevel, gpsOriginLat ?: defaults.gpsOriginLat, gpsOriginLon ?: defaults.gpsOriginLon,
        telemetry ?: defaults.telemetry, multiplayer ?: defaults.multiplayer,
    )
}

data class GameOptionsDoc(
    /** false: GTBikeVConfig.ini not found (start GTA V with GTBike V once). */
    val available: Boolean,
    val reasonCode: String?,
    val revision: String?,
    val gameRunning: Boolean,
    val editable: Boolean,
    val options: GameOptions?,
    val defaults: GameOptions,
)

/** Ranges the bridge enforces, and the change set a save sends. */
object GameOptionRules {
    val TRAFFIC_PERCENT = 0..100
    val CROWD_SLOPE = 0..20
    val CROWD_AMOUNT = 0..100
    const val MAX_BOTS = 5
    val DEFENSIVE = 0..2
    val WAYPOINT_RADIUS = 0..40
    val FINE_STEERING = 0.1..1.0
    val SCREENSHOT_KM = 1..100
    val SCREENSHOT_OFFSET = -20000..20000
    val LOG_LEVEL = 0..5
    val LATITUDE = -90.0..90.0
    val LONGITUDE = -180.0..180.0
    private val COLOR = Regex("^[0-9A-F]{8}$")

    /** Field names (wire) whose value is out of range or malformed; empty when [o] can be saved. */
    fun invalid(o: GameOptions): List<String> = buildList {
        fun check(name: String, ok: Boolean) { if (!ok) add(name) }
        o.trafficBikesPercent?.let { check("trafficBikesPercent", it in TRAFFIC_PERCENT) }
        o.crowdsSlopePercent?.let { check("crowdsSlopePercent", it in CROWD_SLOPE) }
        o.crowdsAmount?.let { check("crowdsAmount", it in CROWD_AMOUNT) }
        o.bots?.let { check("bots", it.size <= MAX_BOTS) }
        o.defensiveLevel?.let { check("defensiveLevel", it in DEFENSIVE) }
        o.waypointColor?.let { check("waypointColor", COLOR.matches(it)) }
        o.waypointRadius?.let { check("waypointRadius", it in WAYPOINT_RADIUS) }
        o.fineSteering?.let { check("fineSteering", it in FINE_STEERING) }
        o.screenshotIntervalKm?.let { check("screenshotIntervalKm", it in SCREENSHOT_KM) }
        o.screenshotXOffset?.let { check("screenshotXOffset", it in SCREENSHOT_OFFSET) }
        o.logLevel?.let { check("logLevel", it in LOG_LEVEL) }
        o.gpsOriginLat?.let { check("gpsOriginLat", it in LATITUDE) }
        o.gpsOriginLon?.let { check("gpsOriginLon", it in LONGITUDE) }
    }

    /**
     * Wire name -> wire value for every field of [edited] that differs from what the file holds ([current]).
     * A field missing in the file counts as changed so saving writes it.
     */
    fun changes(current: GameOptions?, edited: GameOptions): Map<String, Any> = buildMap {
        fun <T : Any> put(name: String, now: T?, new: T?, wire: (T) -> Any = { it }) {
            if (new != null && new != now) this[name] = wire(new)
        }
        val c = current ?: GameOptions()
        put("trafficBikesPercent", c.trafficBikesPercent, edited.trafficBikesPercent)
        put("trafficStyle", c.trafficStyle, edited.trafficStyle) { it.wire }
        put("crowdsEnabled", c.crowdsEnabled, edited.crowdsEnabled)
        put("crowdsSlopePercent", c.crowdsSlopePercent, edited.crowdsSlopePercent)
        put("crowdsAmount", c.crowdsAmount, edited.crowdsAmount)
        put("bots", c.bots, edited.bots) { list -> list.map { it.wire } }
        put("defensiveLevel", c.defensiveLevel, edited.defensiveLevel)
        put("waypointColor", c.waypointColor, edited.waypointColor)
        put("waypointRadius", c.waypointRadius, edited.waypointRadius)
        put("fineSteering", c.fineSteering?.let(::round2), edited.fineSteering?.let(::round2))
        put("screenshotIntervalKm", c.screenshotIntervalKm, edited.screenshotIntervalKm)
        put("screenshotXOffset", c.screenshotXOffset, edited.screenshotXOffset)
        put("menuAutoOpen", c.menuAutoOpen, edited.menuAutoOpen)
        put("showClock", c.showClock, edited.showClock)
        put("debugWindow", c.debugWindow, edited.debugWindow)
        put("logLevel", c.logLevel, edited.logLevel)
        put("gpsOriginLat", c.gpsOriginLat, edited.gpsOriginLat)
        put("gpsOriginLon", c.gpsOriginLon, edited.gpsOriginLon)
        put("telemetry", c.telemetry, edited.telemetry)
        put("multiplayer", c.multiplayer, edited.multiplayer)
    }

    private fun round2(v: Double) = Math.round(v * 100) / 100.0
}
