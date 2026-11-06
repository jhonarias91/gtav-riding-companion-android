package com.fariddroid.gtbikecompanion.feature.activities

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.ActivityDetail
import com.fariddroid.gtbikecompanion.core.model.ActivityItem
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.FitFile
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PowerSeries
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActivitiesData(
    val available: Boolean = true,
    val items: List<ActivityItem> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val errorCode: String? = null,
    val detail: ActivityDetail? = null,
    val detailError: String? = null,
    val confirmSave: Boolean = false,
    val downloading: Boolean = false,
    /** Ready to hand to the share sheet; consumed once by the screen. */
    val share: File? = null,
)

data class ActivitiesUiState(val data: ActivitiesData, val feed: TelemetryFeed?, val units: UnitSystem, val operation: OperationUi?) {
    private val busy get() = operation == OperationUi.Sending || operation == OperationUi.Waiting
    val saveBlock: String? get() = if (busy) "OPERATION_IN_PROGRESS" else ControlRules.blockReason(feed, CapabilityId.ACTIVITIES_FINISH)
}

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    telemetry: TelemetryRepository,
    preferences: AppPreferences,
    private val control: ControlRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val tracker = OperationTracker(viewModelScope, control)
    private val data = MutableStateFlow(ActivitiesData())

    val state: StateFlow<ActivitiesUiState> = combine(data, telemetry.feed, preferences.settings, tracker.state) { d, feed, settings, op ->
        ActivitiesUiState(d, feed, settings.units, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState(data.value, null, UnitSystem.METRIC, null))

    init {
        reload()
        saved.get<String>("activity")?.let(::open)
    }

    fun reload() = viewModelScope.launch {
        data.update { it.copy(loading = true, errorCode = null) }
        when (val r = control.activities(0, 100)) {
            is Outcome.Success -> data.update { it.copy(available = r.value.available, items = r.value.items, total = r.value.total, loading = false) }
            is Outcome.Failure -> data.update { it.copy(loading = false, errorCode = r.code) }
        }
    }

    fun open(id: String) {
        saved["activity"] = id
        viewModelScope.launch {
            when (val r = control.activity(id)) {
                is Outcome.Success -> data.update { it.copy(detail = r.value, detailError = null) }
                is Outcome.Failure -> data.update { it.copy(detailError = r.code) }
            }
        }
    }

    fun closeDetail() {
        saved.remove<String>("activity")
        data.update { it.copy(detail = null, detailError = null) }
    }

    fun askSave() = data.update { it.copy(confirmSave = true) }
    fun dismissSave() = data.update { it.copy(confirmSave = false) }

    /** One save per confirmation; an uncertain result is never retried automatically. */
    fun confirmSave() {
        data.update { it.copy(confirmSave = false) }
        val s = state.value
        val generation = s.feed?.snapshot?.sessionGeneration ?: return
        tracker.send(Command.FinishActivity(generation), s.feed.snapshot.adapterInstanceId) { reload() }
    }

    /** Downloads into the app's private cache (no storage permission) and shares it with a temporary URI. */
    fun download(context: Context, id: String) {
        if (data.value.downloading) return
        data.update { it.copy(downloading = true, detailError = null) }
        viewModelScope.launch {
            when (val r = control.downloadFit(id)) {
                is Outcome.Success -> {
                    val file = withContext(Dispatchers.IO) { writeCache(context, r.value) }
                    data.update { it.copy(downloading = false, share = file) }
                }
                is Outcome.Failure -> data.update { it.copy(downloading = false, detailError = r.code) }
            }
        }
    }

    private fun writeCache(context: Context, fit: FitFile): File {
        val dir = File(context.cacheDir, "fit").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // Keep only the file being shared.
        val safe = fit.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
        return File(dir, safe).apply { writeBytes(fit.bytes) }
    }

    fun shared() = data.update { it.copy(share = null) }
    fun dismissOperation() = tracker.dismiss()
}

@Composable
fun ActivitiesRoute(viewModel: ActivitiesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.activity_share)
    LaunchedEffect(state.data.share) {
        val file = state.data.share ?: return@LaunchedEffect
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fit", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.shared()
    }
    val d = state.data
    BackHandler(enabled = d.detail != null) { viewModel.closeDetail() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("activitiesScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.activities_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            OptionHelpButton(HelpTopic.ACTIVITY_HISTORY, stringResource(R.string.activities_title))
        }
        if (d.detail != null) {
            Detail(d.detail, state.units, d.downloading, d.detailError, onBack = viewModel::closeDetail, onDownload = { viewModel.download(context, d.detail.id) })
        } else {
            SaveCard(state, viewModel)
            d.errorCode?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error) }
            d.detailError?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error) }
            when {
                !d.available -> Text(stringResource(R.string.activities_folder_unknown))
                !d.loading && d.items.isEmpty() -> Text(stringResource(R.string.activities_empty))
            }
            d.items.forEach { a ->
                Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { viewModel.open(a.id) }.padding(vertical = 8.dp).testTag("activity:${a.id}")) {
                    Text(a.fileName.removeSuffix(".fit").removeSuffix(".FIT"), style = MaterialTheme.typography.titleMedium)
                    Text(a.modifiedUtc.take(16).replace('T', ' ') + " UTC · " + (a.sizeBytes / 1024).coerceAtLeast(1) + " KB", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
            TextButton(onClick = { viewModel.reload() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
        }
    }
    if (d.confirmSave) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSave,
            title = { Text(stringResource(R.string.activity_save_title)) },
            text = { Text(stringResource(R.string.activity_save_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmSave, modifier = Modifier.testTag("confirmSave")) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissSave) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SaveCard(state: ActivitiesUiState, vm: ActivitiesViewModel) {
    SectionCard(stringResource(R.string.activity_save_section), help = HelpTopic.ACTIVITY_SAVE) {
        Text(stringResource(R.string.activity_save_hint), style = MaterialTheme.typography.bodyMedium)
        state.saveBlock?.let { Text(stringResource(reasonText(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
        Button(onClick = vm::askSave, enabled = state.saveBlock == null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("saveActivity")) {
            Text(stringResource(R.string.activity_save_button))
        }
        OperationNotice(state.operation, vm::dismissOperation)
        if (state.operation is OperationUi.Uncertain) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.activity_save_uncertain), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                OptionHelpButton(HelpTopic.ACTIVITY_SAVE_UNKNOWN, stringResource(R.string.activity_save_section))
            }
        }
    }
}

@Composable
private fun Detail(a: ActivityDetail, units: UnitSystem, downloading: Boolean, error: String?, onBack: () -> Unit, onDownload: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
    Text(a.fileName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    @Composable
    fun line(label: Int, value: Double?, kind: MetricKind?, decimals: Int = 0) {
        val text = when {
            value == null -> stringResource(R.string.metric_missing)
            kind == MetricKind.DURATION -> MetricFormat.duration(value * 1000)
            kind != null -> {
                val (v, unit) = MetricFormat.convert(kind, value, units)
                MetricFormat.number(v, MetricFormat.decimals(kind), locale) + (unit?.let { " " + stringResource(it) } ?: "")
            }
            else -> MetricFormat.number(value, decimals, locale)
        }
        StatusLine(stringResource(label), text, StatusTone.NEUTRAL)
    }
    SectionCard(stringResource(R.string.activity_summary), help = HelpTopic.ACTIVITY_SUMMARY) {
        StatusLine(stringResource(R.string.activity_start), a.startUtc?.take(16)?.replace('T', ' ')?.plus(" UTC") ?: stringResource(R.string.metric_missing), StatusTone.NEUTRAL)
        line(R.string.metric_distance, a.distanceM, MetricKind.DISTANCE)
        line(R.string.metric_moving_time, a.movingS, MetricKind.DURATION)
        line(R.string.metric_riding_time, a.elapsedS, MetricKind.DURATION)
        line(R.string.metric_elevation_gain, a.ascentM, MetricKind.ELEVATION)
        line(R.string.activity_avg_power, a.avgPowerW, MetricKind.POWER)
        line(R.string.activity_max_power, a.maxPowerW, MetricKind.POWER)
        line(R.string.activity_avg_hr, a.avgHeartRateBpm, MetricKind.HEART_RATE)
        line(R.string.activity_avg_cadence, a.avgCadenceRpm, MetricKind.CADENCE)
        line(R.string.activity_calories, a.calories, null)
        Text(stringResource(R.string.activity_source_fit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionCard(stringResource(R.string.activity_power_chart), help = HelpTopic.ACTIVITY_POWER_CHART) {
        val power = a.power
        if (power == null) Text(stringResource(R.string.activity_no_power), style = MaterialTheme.typography.bodyMedium) else PowerChart(power)
    }
    error?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onDownload, enabled = !downloading, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("downloadFit")) {
            Text(stringResource(if (downloading) R.string.activity_downloading else R.string.activity_download))
        }
        OptionHelpButton(HelpTopic.ACTIVITY_DOWNLOAD, stringResource(R.string.activity_download))
    }
}

/** Line with breaks at gaps: a missing bucket is drawn as no data, never as zero watts. */
@Composable
private fun PowerChart(series: PowerSeries) {
    val max = series.values.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: return
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.activity_power_chart_description, max.toInt())
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val n = series.values.size
        if (n < 2) return@Canvas
        var previous: Offset? = null
        series.values.forEachIndexed { i, v ->
            if (v == null) { previous = null; return@forEachIndexed }
            val point = Offset(i.toFloat() / (n - 1) * size.width, size.height - (v / max * size.height).toFloat())
            previous?.let { drawLine(color, it, point, strokeWidth = 3f) }
            previous = point
        }
    }
}
