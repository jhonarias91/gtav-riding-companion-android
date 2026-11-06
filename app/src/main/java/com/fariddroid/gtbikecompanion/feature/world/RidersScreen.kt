package com.fariddroid.gtbikecompanion.feature.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.CourseRef
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.RiderKind
import com.fariddroid.gtbikecompanion.core.model.RidersBoard
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.data.AppPreferences
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.ui.components.DemoBanner
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.format.MetricFormat
import com.fariddroid.gtbikecompanion.ui.format.MetricKind
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class RidersUiState(val board: RidersBoard?, val errorCode: String?, val feed: TelemetryFeed?, val units: UnitSystem)

/** P8b, read only. Joining another course stays on the PC menu (the native join defers loading; see P8 doc). */
@HiltViewModel
class RidersViewModel @Inject constructor(telemetry: TelemetryRepository, preferences: AppPreferences, private val control: ControlRepository) : ViewModel() {
    private val board = flow {
        while (true) {
            emit(control.riders())
            delay(2_000)
        }
    }

    val state: StateFlow<RidersUiState> = combine(board, telemetry.feed, preferences.settings) { r, feed, settings ->
        RidersUiState((r as? Outcome.Success)?.value, (r as? Outcome.Failure)?.code, feed, settings.units)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RidersUiState(null, null, null, UnitSystem.METRIC))
}

@Composable
private fun courseText(course: CourseRef): String =
    stringResource(R.string.ride_title_route, course.id) + if (course.reverse) " · " + stringResource(R.string.routes_reverse) else ""

@Composable
fun RidersRoute(viewModel: RidersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("ridersScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.riders_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            OptionHelpButton(HelpTopic.MULTIPLAYER_RIDERS, stringResource(R.string.riders_title))
        }
        state.errorCode?.let { Text(stringResource(if (it == "PHASE_NOT_AVAILABLE") R.string.riders_disabled_on_pc else reasonText(it)), color = MaterialTheme.colorScheme.secondary) }
        val board = state.board ?: return@Column
        SectionCard(stringResource(R.string.riders_session)) {
            StatusLine(
                stringResource(R.string.riders_multiplayer),
                stringResource(if (board.connected) R.string.riders_connected else R.string.riders_offline),
                if (board.connected) StatusTone.GOOD else StatusTone.NEUTRAL,
            )
            board.course?.let { StatusLine(stringResource(R.string.riders_course), courseText(it), StatusTone.NEUTRAL) }
            board.busiestRoute?.let { b ->
                StatusLine(stringResource(R.string.riders_busiest), courseText(b), StatusTone.NEUTRAL)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.riders_join_on_pc), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    OptionHelpButton(HelpTopic.MULTIPLAYER_JOIN, stringResource(R.string.riders_busiest))
                }
            }
        }
        if (board.riders.isEmpty()) Text(stringResource(R.string.riders_empty))
        board.riders.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${r.rank}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        r.name ?: stringResource(R.string.riders_unnamed),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (r.kind == RiderKind.LOCAL) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        stringResource(
                            when (r.kind) {
                                RiderKind.LOCAL -> R.string.riders_kind_you
                                RiderKind.HUMAN -> R.string.riders_kind_human
                                RiderKind.BOT -> R.string.riders_kind_bot
                                RiderKind.UNKNOWN -> R.string.riders_kind_unknown
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(r.wattsPerKg?.let { MetricFormat.number(it, 1, locale) + " W/kg" } ?: stringResource(R.string.metric_missing), style = MaterialTheme.typography.bodyMedium)
                    val speed = r.speedMps?.let {
                        val (v, unit) = MetricFormat.convert(MetricKind.SPEED, it, state.units)
                        MetricFormat.number(v, 1, locale) + " " + stringResource(unit!!)
                    }
                    Text(speed ?: stringResource(R.string.metric_missing), style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider()
        }
    }
}
