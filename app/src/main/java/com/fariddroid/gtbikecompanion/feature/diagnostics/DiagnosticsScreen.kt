package com.fariddroid.gtbikecompanion.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.BuildConfig
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.BridgeInfo
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class DiagnosticsUiState(val feed: TelemetryFeed?, val bridge: BridgeInfo?, val bridgeError: String?) {
    /**
     * Plain text for support (plan §15): versions, layers and capability reasons only. No token, PIN, PC address,
     * device IDs, rider names or file paths.
     */
    fun report(): String = buildString {
        appendLine("GTAV Riding Companion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${if (BuildConfig.DEBUG) "debug" else "release"}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Data: ${feed?.origin ?: "-"} / channel ${feed?.channel ?: "-"}")
        appendLine("Bridge: ${bridge?.let { "${it.bridgeVersion} api 1.${it.apiMinor} ${it.transport}" } ?: bridgeError ?: "-"}")
        val s = feed?.snapshot
        if (s != null) {
            appendLine("Mod: ${s.modVersion ?: "-"} profile ${s.bindingProfile ?: "-"}")
            with(s.layers) { appendLine("Layers: adapter=$adapter binding=$binding mod=$mod ant=$antReceiver ble=$bleReceiver game=$gameControl") }
            appendLine("Snapshot age: ${s.snapshotAgeMs ?: "-"} ms")
        }
        feed?.capabilities?.forEach { appendLine("${it.id.wireId}: ${if (it.available) "ok" else it.reasonCode}") }
    }
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(telemetry: TelemetryRepository, private val control: ControlRepository) : ViewModel() {
    private val bridge = flow { emit(control.bridgeInfo()) }

    val state: StateFlow<DiagnosticsUiState> = combine(telemetry.feed, bridge) { feed, info ->
        DiagnosticsUiState(feed, (info as? Outcome.Success)?.value, (info as? Outcome.Failure)?.code)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState(null, null, null))
}

@Composable
fun DiagnosticsRoute(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("diagnosticsScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            OptionHelpButton(HelpTopic.DIAGNOSTICS_LOCAL, stringResource(R.string.diagnostics_title))
        }
        Text(stringResource(R.string.diagnostics_intro), style = MaterialTheme.typography.bodyMedium)
        SectionCard(stringResource(R.string.diagnostics_versions)) {
            StatusLine(stringResource(R.string.diagnostics_app), "${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " · debug" else ""}", StatusTone.NEUTRAL)
            val bridge = state.bridge
            StatusLine(
                stringResource(R.string.diagnostics_bridge),
                bridge?.let { "${it.bridgeVersion} · API 1.${it.apiMinor}" } ?: stringResource(if (state.feed?.origin == DataOrigin.DEMO) R.string.state_demo else R.string.metric_missing),
                if (bridge != null) StatusTone.GOOD else StatusTone.NEUTRAL,
            )
            bridge?.let {
                StatusLine(stringResource(R.string.diagnostics_transport), if (it.transport == "https") "HTTPS" else stringResource(R.string.diagnostics_http_dev), if (it.transport == "https") StatusTone.GOOD else StatusTone.WARN)
            }
            val s = state.feed?.snapshot
            StatusLine(stringResource(R.string.diagnostics_mod), s?.modVersion ?: stringResource(R.string.metric_missing), StatusTone.NEUTRAL)
            StatusLine(stringResource(R.string.diagnostics_profile), s?.bindingProfile ?: stringResource(R.string.metric_missing), if (s?.bindingProfile != null) StatusTone.GOOD else StatusTone.WARN)
        }
        SectionCard(stringResource(R.string.diagnostics_capabilities)) {
            state.feed?.capabilities?.forEach {
                StatusLine(it.id.wireId, if (it.available) stringResource(R.string.diagnostics_available) else it.reasonCode ?: "-", if (it.available) StatusTone.GOOD else StatusTone.NEUTRAL)
            }
        }
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("GTAV Riding Companion diagnostics", state.report()))
                copied = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("copyDiagnostics"),
        ) { Text(stringResource(if (copied) R.string.diagnostics_copied else R.string.diagnostics_copy)) }
        Text(stringResource(R.string.diagnostics_privacy), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
