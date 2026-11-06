package com.fariddroid.gtbikecompanion.core.demo

import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.BotType
import com.fariddroid.gtbikecompanion.core.model.GameOptions
import com.fariddroid.gtbikecompanion.core.model.GameOptionsDoc
import com.fariddroid.gtbikecompanion.core.model.TrafficStyle
import com.fariddroid.gtbikecompanion.core.model.ActivityItem
import com.fariddroid.gtbikecompanion.core.model.ActivityPage
import com.fariddroid.gtbikecompanion.core.model.BoolSetting
import com.fariddroid.gtbikecompanion.core.model.BridgeInfo
import com.fariddroid.gtbikecompanion.core.model.CourseRef
import com.fariddroid.gtbikecompanion.core.model.GameMap
import com.fariddroid.gtbikecompanion.core.model.RiderKind
import com.fariddroid.gtbikecompanion.core.model.RiderRow
import com.fariddroid.gtbikecompanion.core.model.RidersBoard
import com.fariddroid.gtbikecompanion.core.model.DeviceRole
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.DevicePolicy
import com.fariddroid.gtbikecompanion.core.model.DurationType
import com.fariddroid.gtbikecompanion.core.model.FitFile
import com.fariddroid.gtbikecompanion.core.model.PcDevice
import com.fariddroid.gtbikecompanion.core.model.PowerSeries
import com.fariddroid.gtbikecompanion.core.model.SegmentType
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import com.fariddroid.gtbikecompanion.core.model.Sport
import com.fariddroid.gtbikecompanion.core.model.WorkoutDetail
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlan
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlans
import com.fariddroid.gtbikecompanion.core.model.WorkoutProgress
import com.fariddroid.gtbikecompanion.core.model.WorkoutSegmentInfo
import com.fariddroid.gtbikecompanion.core.model.WorkoutSummary
import com.fariddroid.gtbikecompanion.core.model.ChoiceOption
import com.fariddroid.gtbikecompanion.core.model.ChoiceSetting
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DifficultySetting
import com.fariddroid.gtbikecompanion.core.model.NumberSetting
import com.fariddroid.gtbikecompanion.core.model.Operation
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PcSettings
import com.fariddroid.gtbikecompanion.core.model.RouteDetail
import com.fariddroid.gtbikecompanion.core.model.RoutePage
import com.fariddroid.gtbikecompanion.core.model.RoutePreview
import com.fariddroid.gtbikecompanion.core.model.RouteSummary
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository

/**
 * Offline demo catalog and settings. Clearly synthetic names; every command is refused with DEMO_MODE so the
 * demo can never look like it changed a PC.
 */
class DemoControlRepository : ControlRepository {
    private val routes = listOf(
        RouteSummary("1", "Demo Coastal Loop", "Demo author", 24_300.0, 310.0, 310.0, 120.0, listOf("Flat"), loopAllowed = true, reverseAllowed = true),
        RouteSummary("2", "Demo Hill Climb", null, 8_100.0, 640.0, 12.0, 690.0, listOf("Climb"), loopAllowed = false, reverseAllowed = false),
        RouteSummary("3", "Demo City Sprint", "Demo author", 5_200.0, 40.0, 40.0, 60.0, listOf("Urban", "Sprint"), loopAllowed = true, reverseAllowed = true),
    )

    override suspend fun routes(query: String, offset: Int, limit: Int): Outcome<RoutePage> {
        val q = query.trim().lowercase()
        val all = routes.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.categories.any { c -> c.lowercase().contains(q) } }
        return Outcome.Success(RoutePage(DEMO_CATALOG, all.size, offset, all.drop(offset).take(limit)))
    }

    override suspend fun route(id: String): Outcome<RouteDetail> =
        routes.firstOrNull { it.id == id }?.let { Outcome.Success(RouteDetail(it, "Demo route for trying the app without a PC.", null)) }
            ?: Outcome.Failure("NOT_FOUND")

    override suspend fun routePreview(): Outcome<RoutePreview> = Outcome.Success(RoutePreview(DEMO_CATALOG, false, null, null, null))

    override suspend fun settings(): Outcome<PcSettings> = Outcome.Success(
        PcSettings(
            revision = "demo-settings",
            weightKg = NumberSetting(72.0, 40.0, 150.0, editable = false, reasonCode = DEMO),
            ftpW = NumberSetting(DemoScenarios.DEMO_FTP_W.toDouble(), 50.0, 500.0, editable = false, reasonCode = DEMO),
            hrMaxBpm = NumberSetting(185.0, 50.0, 220.0, editable = false, reasonCode = DEMO),
            autopilot = BoolSetting(true, editable = false, reasonCode = DEMO),
            avoidObstacles = BoolSetting(false, editable = false, reasonCode = DEMO),
            difficulty = DifficultySetting(50, DifficultySetting.LEVELS, editable = false, reasonCode = DEMO),
            roadFeel = BoolSetting(true, editable = false, reasonCode = DEMO),
            bike = ChoiceSetting("Demo bike", listOf(ChoiceOption("Demo bike", "Demo bike")), editable = false, reasonCode = DEMO),
            character = ChoiceSetting("Demo rider", listOf(ChoiceOption("Demo rider", "Demo rider")), editable = false, reasonCode = DEMO),
        ),
    )

    private val trainer = PcDevice("54321", SensorSource.ANT, "FEC")
    private val hr = PcDevice("18446744073709551615", SensorSource.BLE, "HR")

    override suspend fun deviceRoles(): Outcome<DeviceRoles> = Outcome.Success(
        DeviceRoles(
            "demo-devices", null,
            listOf(
                DeviceRole(SensorRoleType.POWER, true, trainer, DevicePolicy.AUTO, emptyList(), canAuto = false, canLock = true, canSkip = true),
                DeviceRole(SensorRoleType.RESISTANCE, true, trainer, DevicePolicy.AUTO, emptyList(), canAuto = false, canLock = true, canSkip = true),
                DeviceRole(SensorRoleType.CADENCE, true, trainer, DevicePolicy.AUTO, emptyList(), canAuto = false, canLock = true, canSkip = true),
                DeviceRole(SensorRoleType.HEART_RATE, true, hr, DevicePolicy.AUTO, emptyList(), canAuto = false, canLock = true, canSkip = true),
                DeviceRole(SensorRoleType.STEERING, true, null, DevicePolicy.AUTO, emptyList(), canAuto = false, canLock = false, canSkip = false),
                DeviceRole(SensorRoleType.CONTROLS, true, null, DevicePolicy.AUTO, emptyList(), canAuto = false, canLock = false, canSkip = false),
            ),
        ),
    )

    private val demoWorkout = WorkoutSummary("w-00000000000000d1", "p-00000000000000d1", "Demo Sweet Spot", "Demo coach", DurationType.TIME, 2700.0, 3)

    override suspend fun workoutPlans(): Outcome<WorkoutPlans> =
        Outcome.Success(WorkoutPlans("demo-workouts", true, DemoScenarios.DEMO_FTP_W.toDouble(), listOf(WorkoutPlan("p-00000000000000d1", "Demo plan", "Synthetic plan for trying the app.", 1))))

    override suspend fun workouts(planId: String): Outcome<List<WorkoutSummary>> =
        if (planId == demoWorkout.planId) Outcome.Success(listOf(demoWorkout)) else Outcome.Failure("NOT_FOUND")

    override suspend fun workout(id: String): Outcome<WorkoutDetail> =
        if (id != demoWorkout.id) Outcome.Failure("NOT_FOUND") else Outcome.Success(
            WorkoutDetail(
                demoWorkout, "Synthetic workout.",
                listOf(
                    WorkoutSegmentInfo(0, SegmentType.WARMUP, 0.0, 600.0, 125, 188, null),
                    WorkoutSegmentInfo(1, SegmentType.STEADY, 600.0, 2400.0, 225, 225, 90),
                    WorkoutSegmentInfo(2, SegmentType.COOLDOWN, 2400.0, 2700.0, 163, 110, null),
                ),
                DemoScenarios.DEMO_FTP_W.toDouble(),
            ),
        )

    override suspend fun workoutProgress(): Outcome<WorkoutProgress> = Outcome.Success(WorkoutProgress.NONE)

    private val demoActivity = ActivityItem("a-00000000000000d1", "GTBikeV_demo.fit", "2026-11-04T13:00:00Z", 182_340)

    override suspend fun activities(offset: Int, limit: Int): Outcome<ActivityPage> =
        Outcome.Success(ActivityPage(true, 1, offset, listOf(demoActivity).drop(offset).take(limit)))

    override suspend fun activity(id: String): Outcome<ActivityDetail> =
        if (id != demoActivity.id) Outcome.Failure("NOT_FOUND") else Outcome.Success(
            ActivityDetail(
                demoActivity.id, demoActivity.fileName, demoActivity.sizeBytes, Sport.CYCLING, "2026-11-04T12:00:00Z",
                3700.0, 3600.0, 30_120.0, 410.0, 185.0, 610.0, 142.0, 171.0, 87.0, 640.0,
                PowerSeries(60.0, List(60) { i -> if (i == 30) null else 170.0 + (i % 7) * 6 }),
            ),
        )

    /** The demo has no real file to share. */
    override suspend fun downloadFit(id: String): Outcome<FitFile> = Outcome.Failure(DEMO)

    /** No map image ships with the app (licence not verified); the demo has none either. */
    override suspend fun gameMap(): Outcome<GameMap> = Outcome.Failure(DEMO)
    override suspend fun mapImage(assetId: String): Outcome<ByteArray> = Outcome.Failure(DEMO)

    override suspend fun riders(): Outcome<RidersBoard> = Outcome.Success(
        RidersBoard(
            connected = true,
            course = CourseRef("1", reverse = false),
            busiestRoute = null,
            riders = listOf(
                RiderRow(1, RiderKind.BOT, "Demo bot", 3.4, 9.1),
                RiderRow(2, RiderKind.LOCAL, "You (demo)", 3.1, 8.8),
                RiderRow(3, RiderKind.HUMAN, "Demo rider", 2.9, 8.5),
            ),
        ),
    )

    override suspend fun bridgeInfo(): Outcome<BridgeInfo> = Outcome.Failure(DEMO)

    /** Sample of what a PC shows; read-only in demo mode. */
    override suspend fun gameOptions(): Outcome<GameOptionsDoc> {
        val defaults = GameOptions(50, TrafficStyle.NORMAL, true, 6, 50, listOf(BotType.COMPANION, BotType.RANDOM, BotType.RANDOM), 1, "FFADFF2F", 20, 0.7, 5, 0, true, true, false, 3, -19.10637, -169.871, true, true)
        return Outcome.Success(GameOptionsDoc(available = true, reasonCode = DEMO, revision = null, gameRunning = false, editable = false, options = defaults, defaults = defaults))
    }

    override suspend fun saveGameOptions(revision: String, changes: Map<String, Any>): Outcome<GameOptionsDoc> = Outcome.Failure(DEMO)

    override suspend fun submit(command: Command, adapterInstanceId: String): Outcome<Operation> = Outcome.Failure(DEMO)
    override suspend fun operation(requestId: String): Outcome<Operation> = Outcome.Failure("NOT_FOUND")

    private companion object {
        const val DEMO = "DEMO_MODE"
        const val DEMO_CATALOG = "demo-catalog"
    }
}
