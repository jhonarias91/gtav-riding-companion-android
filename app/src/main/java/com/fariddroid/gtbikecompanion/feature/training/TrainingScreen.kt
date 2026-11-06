package com.fariddroid.gtbikecompanion.feature.training

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.DurationType
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.SegmentType
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.model.WorkoutDetail
import com.fariddroid.gtbikecompanion.core.model.WorkoutPlans
import com.fariddroid.gtbikecompanion.core.model.WorkoutProgress
import com.fariddroid.gtbikecompanion.core.model.WorkoutSegmentInfo
import com.fariddroid.gtbikecompanion.core.model.WorkoutSummary
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.core.rules.ControlRules
import com.fariddroid.gtbikecompanion.data.AppPreferences
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.feature.common.OperationTracker
import com.fariddroid.gtbikecompanion.feature.common.OperationUi
import com.fariddroid.gtbikecompanion.ui.components.DemoBanner
import com.fariddroid.gtbikecompanion.ui.components.OperationNotice
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.format.MetricFormat
import com.fariddroid.gtbikecompanion.ui.format.MetricKind
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import com.fariddroid.gtbikecompanion.ui.theme.LocalStatusColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingData(
    val plans: WorkoutPlans? = null,
    val planId: String? = null,
    val workouts: List<WorkoutSummary> = emptyList(),
    val detail: WorkoutDetail? = null,
    val errorCode: String? = null,
    val confirmSelect: Boolean = false,
)

data class TrainingUiState(
    val data: TrainingData,
    val progress: WorkoutProgress,
    val feed: TelemetryFeed?,
    val units: UnitSystem,
    val operation: OperationUi? = null,
) {
    private val busy get() = operation == OperationUi.Sending || operation == OperationUi.Waiting
    val selectBlock: String? get() = if (busy) "OPERATION_IN_PROGRESS" else ControlRules.blockReason(feed, CapabilityId.WORKOUTS_SELECT)

    /**
     * Name of the unfinished workout on the PC that a new selection would replace; null when there is none. The 1 s
     * state also says a workout is unfinished, so the warning shows even before the 2 s progress read names it.
     */
    val runningWorkoutName: String? get() {
        val fromProgress = progress.present && progress.finished != true
        val fromState = feed?.snapshot?.workout?.let { it.present == true && it.finished != true } == true
        return if (fromProgress || fromState) progress.name?.takeIf { progress.present } ?: "…" else null
    }
}

/**
 * Browse and follow workouts, and (P13) start one exactly as the mod's Select button does; the mod engages ERG itself.
 * There is no pause, skip, ERG, bias or end action anywhere in this feature: those stay on the PC.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    telemetry: TelemetryRepository,
    preferences: AppPreferences,
    private val control: ControlRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val data = MutableStateFlow(TrainingData())
    private val tracker = OperationTracker(viewModelScope, control)

    private val progress = flow {
        while (true) {
            emit((control.workoutProgress() as? Outcome.Success)?.value ?: WorkoutProgress.NONE)
            delay(2_000)
        }
    }

    val state: StateFlow<TrainingUiState> = combine(data, progress, telemetry.feed, preferences.settings, tracker.state) { d, p, feed, settings, op ->
        TrainingUiState(d, p, feed, settings.units, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingUiState(data.value, WorkoutProgress.NONE, null, UnitSystem.METRIC))

    init {
        loadPlans()
        saved.get<String>("plan")?.let(::openPlan)
        saved.get<String>("workout")?.let(::openWorkout)
    }

    fun loadPlans() = viewModelScope.launch {
        when (val r = control.workoutPlans()) {
            is Outcome.Success -> data.update { it.copy(plans = r.value, errorCode = null) }
            is Outcome.Failure -> data.update { it.copy(errorCode = r.code) }
        }
    }

    fun openPlan(id: String) {
        saved["plan"] = id
        viewModelScope.launch {
            when (val r = control.workouts(id)) {
                is Outcome.Success -> data.update { it.copy(planId = id, workouts = r.value, errorCode = null) }
                is Outcome.Failure -> data.update { it.copy(errorCode = r.code) }
            }
        }
    }

    fun openWorkout(id: String) {
        saved["workout"] = id
        viewModelScope.launch {
            when (val r = control.workout(id)) {
                is Outcome.Success -> data.update { it.copy(detail = r.value, errorCode = null) }
                is Outcome.Failure -> data.update { it.copy(errorCode = r.code) }
            }
        }
    }

    /** Select and start always explains its effect (ERG) and asks first. */
    fun askSelect() = data.update { it.copy(confirmSelect = true) }
    fun dismissSelect() = data.update { it.copy(confirmSelect = false) }

    fun confirmSelect() {
        val st = state.value
        data.update { it.copy(confirmSelect = false) }
        val workout = st.data.detail?.summary ?: return
        val revision = st.data.plans?.catalogRevision ?: return
        if (st.selectBlock != null) return
        tracker.send(Command.SelectWorkout(workout.id, revision), st.feed?.snapshot?.adapterInstanceId) { result ->
            // The PC library changed (new plans or another FTP): show the current one instead of guessing.
            if (result is OperationUi.Refused && result.code == "REVISION_CONFLICT") loadPlans()
        }
    }

    fun dismissOperation() = tracker.dismiss()

    /** Back: detail -> workouts -> plans. Never touches the PC. */
    fun back(): Boolean {
        val d = data.value
        return when {
            d.detail != null -> { saved.remove<String>("workout"); data.update { it.copy(detail = null) }; true }
            d.planId != null -> { saved.remove<String>("plan"); data.update { it.copy(planId = null, workouts = emptyList()) }; true }
            else -> false
        }
    }
}

@Composable
fun TrainingRoute(viewModel: TrainingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val d = state.data
    BackHandler(enabled = d.detail != null || d.planId != null) { viewModel.back() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("trainingScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.training_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            OptionHelpButton(HelpTopic.TRAINING_CATALOG, stringResource(R.string.training_title))
        }
        Text(stringResource(R.string.training_read_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        if (state.progress.present) ProgressCard(state.progress, state.units)
        d.errorCode?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error) }
        when {
            d.detail != null -> {
                DetailView(d.detail, state.units, viewModel::back)
                SelectAndStart(state, viewModel)
            }
            d.planId != null -> WorkoutList(d.workouts, state.units, viewModel::openWorkout, viewModel::back)
            else -> PlanList(d.plans, viewModel::openPlan)
        }
    }
    if (d.confirmSelect && d.detail != null) {
        // Like the mod's menu, a new selection replaces the workout running on the PC: say so before sending.
        val running = state.runningWorkoutName
        AlertDialog(
            onDismissRequest = viewModel::dismissSelect,
            title = {
                Text(
                    if (running != null) stringResource(R.string.training_select_replace_title)
                    else stringResource(R.string.training_select_confirm_title, d.detail.summary.name),
                )
            },
            text = {
                Text(
                    if (running != null) stringResource(R.string.training_select_replace_body, running, d.detail.summary.name)
                    else stringResource(R.string.training_select_confirm_body),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSelect, modifier = Modifier.testTag("confirmWorkout")) {
                    Text(stringResource(if (running != null) R.string.training_select_replace else R.string.training_select_start))
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissSelect) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** P13: starts this workout on the PC through the mod's own Select path, after a confirmation. */
@Composable
private fun SelectAndStart(state: TrainingUiState, vm: TrainingViewModel) {
    val block = state.selectBlock
    block?.takeIf { it != "OPERATION_IN_PROGRESS" }?.let {
        Text(stringResource(reasonText(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = vm::askSelect,
            enabled = block == null && state.data.plans?.catalogRevision != null,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("selectWorkout"),
        ) { Text(stringResource(R.string.training_select_start)) }
        OptionHelpButton(HelpTopic.TRAINING_SELECT, stringResource(R.string.training_select_start), currentReason = block?.let { stringResource(reasonText(it)) })
    }
    OperationNotice(state.operation, vm::dismissOperation)
}

@Composable
private fun amount(value: Double, type: DurationType, units: UnitSystem): String {
    if (type == DurationType.TIME) return MetricFormat.duration(value * 1000)
    val (v, unit) = MetricFormat.convert(MetricKind.DISTANCE, value, units)
    return MetricFormat.number(v, 1, LocalConfiguration.current.locales[0]) + " " + stringResource(unit!!)
}

@Composable
private fun segmentLabel(type: SegmentType): String = stringResource(
    when (type) {
        SegmentType.WARMUP -> R.string.segment_warmup
        SegmentType.COOLDOWN -> R.string.segment_cooldown
        SegmentType.INTERVAL -> R.string.segment_interval
        SegmentType.STEADY -> R.string.segment_steady
        SegmentType.RAMP -> R.string.segment_ramp
        SegmentType.FREE_RIDE -> R.string.segment_free_ride
        SegmentType.UNKNOWN -> R.string.segment_unknown
    },
)

@Composable
private fun powerLabel(s: WorkoutSegmentInfo): String? {
    val start = s.startPowerW
    val end = s.endPowerW
    return when {
        s.type == SegmentType.FREE_RIDE || start == null -> null
        end == null || start == end -> stringResource(R.string.training_reference_w, start)
        else -> stringResource(R.string.training_reference_range_w, start, end)
    }
}

@Composable
private fun ProgressCard(p: WorkoutProgress, units: UnitSystem) {
    SectionCard(stringResource(R.string.training_active_on_pc), help = HelpTopic.TRAINING_PROGRESS) {
        Text(p.name ?: "—", style = MaterialTheme.typography.titleMedium)
        val status = when {
            p.finished == true -> R.string.training_status_finished
            p.inCountdown == true -> R.string.training_status_countdown
            p.started == true -> R.string.training_status_running
            else -> R.string.training_status_waiting
        }
        StatusLine(stringResource(R.string.training_status), stringResource(status), if (p.finished == true) StatusTone.GOOD else StatusTone.NEUTRAL)
        val type = p.durationType ?: DurationType.TIME
        val elapsed = p.elapsed
        val total = p.total
        if (elapsed != null && total != null && total > 0) {
            LinearProgressIndicator(progress = { (elapsed / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            StatusLine(stringResource(R.string.training_remaining), amount(p.remaining ?: 0.0, type, units), StatusTone.NEUTRAL)
        }
        p.segment?.let { s ->
            val count = p.segmentCount
            StatusLine(
                stringResource(R.string.training_segment),
                (if (count != null) "${s.index + 1}/$count · " else "") + segmentLabel(s.type),
                StatusTone.NEUTRAL,
            )
            powerLabel(s)?.let { StatusLine(stringResource(R.string.training_reference), it, StatusTone.NEUTRAL) }
            s.cadenceRpm?.let { StatusLine(stringResource(R.string.metric_cadence), "$it ${stringResource(R.string.unit_rpm)}", StatusTone.NEUTRAL) }
        }
        Text(stringResource(R.string.training_pc_controls), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlanList(plans: WorkoutPlans?, onOpen: (String) -> Unit) {
    when {
        plans == null -> Text(stringResource(R.string.loading))
        !plans.ready -> Text(stringResource(R.string.training_loading_pc))
        plans.plans.isEmpty() -> Text(stringResource(R.string.training_empty))
        else -> plans.plans.forEach { p ->
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(p.id) }.padding(vertical = 8.dp).testTag("plan:${p.id}")) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.training_workout_count, p.workoutCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun WorkoutList(workouts: List<WorkoutSummary>, units: UnitSystem, onOpen: (String) -> Unit, onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    if (workouts.isEmpty()) Text(stringResource(R.string.training_empty))
    workouts.forEach { w ->
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(w.id) }.padding(vertical = 8.dp).testTag("workout:${w.id}")) {
            Text(w.name, style = MaterialTheme.typography.titleMedium)
            Text(amount(w.total, w.durationType, units) + " · " + stringResource(R.string.training_segments, w.segmentCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}

@Composable
private fun DetailView(detail: WorkoutDetail, units: UnitSystem, onBack: () -> Unit) {
    val w = detail.summary
    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    Text(w.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    w.author?.let { Text(stringResource(R.string.routes_author, it), style = MaterialTheme.typography.bodyMedium) }
    Text(amount(w.total, w.durationType, units), style = MaterialTheme.typography.bodyLarge)
    detail.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    SegmentProfile(detail.segments, w.total)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            detail.ftpReferenceW?.let { stringResource(R.string.training_reference_ftp, it.toInt()) } ?: stringResource(R.string.training_reference),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        OptionHelpButton(HelpTopic.TRAINING_REFERENCE, stringResource(R.string.training_reference))
    }
    detail.segments.forEach { s ->
        StatusLine(
            "${s.index + 1}. ${segmentLabel(s.type)} · ${amount(s.end - s.start, w.durationType, units)}",
            listOfNotNull(powerLabel(s), s.cadenceRpm?.let { "$it ${stringResource(R.string.unit_rpm)}" }).joinToString(" · ").ifEmpty { "—" },
            StatusTone.NEUTRAL,
        )
    }
    Text(stringResource(R.string.training_pc_controls), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Reference power shape; bar height relative to the highest reference in the workout. */
@Composable
private fun SegmentProfile(segments: List<WorkoutSegmentInfo>, total: Double) {
    if (segments.isEmpty() || total <= 0) return
    val max = segments.maxOf { maxOf(it.startPowerW ?: 0, it.endPowerW ?: 0) }.coerceAtLeast(1)
    val zones = LocalStatusColors.current.zones
    val description = stringResource(R.string.training_profile_description, segments.size)
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = description }) {
        segments.forEach { s ->
            val x0 = (s.start / total * size.width).toFloat()
            val x1 = (s.end / total * size.width).toFloat()
            val p = ((s.startPowerW ?: 0) + (s.endPowerW ?: s.startPowerW ?: 0)) / 2f
            val h = (p / max * size.height).coerceAtLeast(2f)
            val color = if (s.type == SegmentType.FREE_RIDE) Color.Gray else zones[((p / max) * (zones.size - 1)).toInt().coerceIn(0, zones.size - 1)]
            drawRect(color, topLeft = Offset(x0, size.height - h), size = Size((x1 - x0 - 1f).coerceAtLeast(1f), h))
        }
    }
}
