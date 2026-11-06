package com.fariddroid.gtbikecompanion.feature.routes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.RouteSummary
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.ui.components.DemoBanner
import com.fariddroid.gtbikecompanion.ui.components.OperationNotice
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.SettingRow
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.format.MetricFormat
import com.fariddroid.gtbikecompanion.ui.format.MetricKind
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton

@Composable
fun RoutesRoute(viewModel: RoutesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.data.selected
    if (selected == null) {
        RouteList(state, viewModel)
    } else {
        // Back closes the detail; it never applies, previews or cancels anything on the PC.
        BackHandler(onBack = viewModel::closeDetail)
        RouteDetailView(state, viewModel)
    }
    // With a workout unfinished on the PC the actions stay available, as in the mod's menu, but say what happens to it.
    val workout = if (state.workoutRunning) state.data.runningWorkoutName ?: stringResource(R.string.routes_workout_current) else null
    when (state.data.confirm) {
        RouteConfirm.APPLY -> ConfirmDialog(
            title = if (workout != null) stringResource(R.string.routes_confirm_workout_title) else stringResource(R.string.routes_confirm_apply_title, selected?.summary?.name ?: ""),
            body = if (workout != null) stringResource(R.string.routes_confirm_workout_body, workout, selected?.summary?.name ?: "") else stringResource(R.string.routes_confirm_apply_body),
            confirmLabel = if (workout != null) stringResource(R.string.routes_confirm_workout_action) else null,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirm,
        )
        RouteConfirm.FREE_RIDE -> ConfirmDialog(
            title = stringResource(R.string.routes_free_ride),
            body = stringResource(R.string.routes_confirm_free_ride_body) + if (workout != null) "\n\n" + stringResource(R.string.routes_confirm_free_ride_workout, workout) else "",
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismissConfirm,
        )
        null -> Unit
    }
}

@Composable
private fun RouteList(state: RoutesUiState, vm: RoutesViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("routesScreen"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.routes_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    OptionHelpButton(HelpTopic.ROUTES_BROWSE, stringResource(R.string.routes_title))
                }
                ActiveRouteLine(state)
                OutlinedTextField(
                    value = state.data.query,
                    onValueChange = vm::setQuery,
                    singleLine = true,
                    label = { Text(stringResource(R.string.routes_search)) },
                    modifier = Modifier.fillMaxWidth().testTag("routeSearch"),
                )
                state.data.errorCode?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error) }
                if (!state.data.loading && state.data.items.isEmpty() && state.data.errorCode == null) {
                    Text(stringResource(R.string.routes_empty), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(
                    onClick = vm::askFreeRide,
                    enabled = state.applyBlock == null && state.data.catalogRevision != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("freeRide"),
                ) { Text(stringResource(R.string.routes_free_ride)) }
                OperationNotice(state.operation, vm::dismissOperation)
            }
        }
        items(state.data.items, key = { it.id }) { route ->
            RouteRow(route, route.id == state.activeRouteId, state.units) { vm.select(route.id) }
            HorizontalDivider()
        }
        if (state.data.items.size < state.data.total) {
            item {
                TextButton(onClick = { vm.load() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.routes_load_more))
                }
            }
        }
    }
}

@Composable
private fun ActiveRouteLine(state: RoutesUiState) {
    val text = when {
        state.feed?.snapshot?.activeRoute == null -> stringResource(R.string.routes_active_none)
        else -> state.activeRouteName ?: stringResource(R.string.ride_title_route, state.activeRouteId ?: "")
    }
    StatusLine(stringResource(R.string.routes_active), text, if (state.activeRouteId != null) StatusTone.GOOD else StatusTone.NEUTRAL)
}

@Composable
private fun RouteRow(route: RouteSummary, active: Boolean, units: UnitSystem, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp).testTag("route:${route.id}"),
    ) {
        Text(route.name + if (active) " • " + stringResource(R.string.routes_active_badge) else "", style = MaterialTheme.typography.titleMedium)
        Text(summaryLine(route, units), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun summaryLine(route: RouteSummary, units: UnitSystem): String {
    val locale = LocalConfiguration.current.locales[0]
    val parts = mutableListOf<String>()
    route.distanceM?.let {
        val (v, unit) = MetricFormat.convert(MetricKind.DISTANCE, it, units)
        parts += MetricFormat.number(v, 1, locale) + " " + stringResource(unit!!)
    }
    route.elevationGainM?.let {
        val (v, unit) = MetricFormat.convert(MetricKind.ELEVATION, it, units)
        parts += "↑ " + MetricFormat.number(v, 0, locale) + " " + stringResource(unit!!)
    }
    parts += route.categories
    return parts.joinToString(" · ")
}

@Composable
private fun RouteDetailView(state: RoutesUiState, vm: RoutesViewModel) {
    val detail = state.data.selected ?: return
    val route = detail.summary
    val preview = state.preview
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("routeDetail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = vm::closeDetail) { Text(stringResource(R.string.action_back)) }
        Text(route.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        route.author?.let { Text(stringResource(R.string.routes_author, it), style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(summaryLine(route, state.units), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OptionHelpButton(HelpTopic.ROUTES_PROFILE, stringResource(R.string.routes_title))
        }
        // Third-party text, shown as written (not translated).
        detail.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        ActiveRouteLine(state)

        SectionCard(stringResource(R.string.routes_options)) {
            SettingRow(
                label = stringResource(R.string.routes_loop),
                help = HelpTopic.ROUTES_LOOP,
                enabled = route.loopAllowed,
                reason = if (route.loopAllowed) null else stringResource(R.string.routes_flag_not_supported),
                control = { Switch(checked = state.data.loop && route.loopAllowed, onCheckedChange = vm::setLoop, enabled = route.loopAllowed, modifier = Modifier.testTag("loopSwitch")) },
            )
            SettingRow(
                label = stringResource(R.string.routes_reverse),
                help = HelpTopic.ROUTES_REVERSE,
                enabled = route.reverseAllowed,
                reason = if (route.reverseAllowed) null else stringResource(R.string.routes_flag_not_supported),
                control = { Switch(checked = state.data.reverse && route.reverseAllowed, onCheckedChange = vm::setReverse, enabled = route.reverseAllowed, modifier = Modifier.testTag("reverseSwitch")) },
            )
        }

        SectionCard(stringResource(R.string.routes_pc_preview), help = HelpTopic.ROUTES_PC_PREVIEW) {
            val previewText = when {
                preview == null || !preview.panelOpen -> stringResource(R.string.routes_pc_selector_closed)
                preview.routeId == route.id -> stringResource(R.string.routes_pc_showing_this)
                else -> stringResource(R.string.routes_pc_showing_other)
            }
            Text(previewText, style = MaterialTheme.typography.bodyMedium)
            // Opens the PC selector if needed and moves it to this route; disabled once it already shows it.
            val showingThis = preview?.panelOpen == true && preview.routeId == route.id
            OutlinedButton(
                onClick = vm::showOnPc,
                enabled = state.showOnPcBlock == null && !showingThis && state.data.catalogRevision != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("previewOnPc"),
            ) { Text(stringResource(R.string.routes_show_on_pc)) }
            state.showOnPcBlock?.takeIf { it != "OPERATION_IN_PROGRESS" }?.let {
                Text(stringResource(reasonText(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }

        state.applyBlock?.let { Text(stringResource(reasonText(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = vm::askApply, enabled = state.applyBlock == null, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("applyRoute")) {
                Text(stringResource(R.string.routes_apply))
            }
            OptionHelpButton(HelpTopic.ROUTES_APPLY, stringResource(R.string.routes_apply), currentReason = state.applyBlock?.let { stringResource(reasonText(it)) })
        }
        OperationNotice(state.operation, vm::dismissOperation)
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, onDismiss: () -> Unit, confirmLabel: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm")) { Text(confirmLabel ?: stringResource(R.string.action_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
