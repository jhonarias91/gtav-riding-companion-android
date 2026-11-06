package com.fariddroid.gtbikecompanion.feature.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.ModLayer
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.rules.ActivationDecision
import com.fariddroid.gtbikecompanion.ui.components.AdaptiveGrid
import com.fariddroid.gtbikecompanion.ui.components.DemoBanner
import com.fariddroid.gtbikecompanion.ui.components.MetricTile
import com.fariddroid.gtbikecompanion.ui.components.OperationNotice
import com.fariddroid.gtbikecompanion.ui.components.PowerGauge
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.SettingRow
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.components.startQrScan
import com.fariddroid.gtbikecompanion.ui.format.Labels
import com.fariddroid.gtbikecompanion.ui.format.MetricKind
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton

@Composable
fun RideRoute(onPairAgain: () -> Unit = {}, onOpenDevices: () -> Unit = {}, viewModel: RideViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val needsPairing by viewModel.needsPairing.collectAsStateWithLifecycle()
    val renew by viewModel.renew.collectAsStateWithLifecycle()
    state?.let {
        RideScreen(
            it, onToggleMod = viewModel::toggleMod, onDismissOperation = viewModel::dismissOperation,
            needsPairing = needsPairing, renew = renew, onRenewPin = viewModel::setRenewPin, onRenew = viewModel::renewPairing,
            onRenewQr = viewModel::renewFromQr, onRenewScanFailed = viewModel::renewQrScanFailed, onShowRenewPin = viewModel::showRenewPin,
            onPairAgain = onPairAgain, onOpenDevices = onOpenDevices,
        )
    }
}

@Composable
fun RideScreen(
    state: RideUiState,
    modifier: Modifier = Modifier,
    onToggleMod: () -> Unit = {},
    onDismissOperation: () -> Unit = {},
    needsPairing: Boolean = false,
    renew: RenewPairingUi = RenewPairingUi(),
    onRenewPin: (String) -> Unit = {},
    onRenew: () -> Unit = {},
    onRenewQr: (String) -> Unit = {},
    onRenewScanFailed: () -> Unit = {},
    onShowRenewPin: () -> Unit = {},
    onPairAgain: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
) {
    val snapshot = state.feed.snapshot
    val metrics = snapshot.metrics
    val units = state.units
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Columns adapt to available width and font scale before shrinking text.
        val fontScale = LocalDensity.current.fontScale
        val columns = when {
            maxWidth >= 600.dp -> 3
            maxWidth / fontScale < 300.dp -> 1
            else -> 2
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("rideScreen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isDemo) DemoBanner(state.demoScenario?.let { stringResource(Labels.scenario(it)) })
            if (needsPairing) RenewPairingCard(renew, onRenewPin, onRenew, onRenewQr, onRenewScanFailed, onShowRenewPin, onPairAgain)

            Header(state)

            SectionCard(title = stringResource(R.string.metric_power), help = HelpTopic.METRICS_POWER) {
                PowerGauge(metrics.powerW, state.ftpW, units)
                if (state.ftpW != null && state.isDemo) {
                    Text(
                        stringResource(R.string.power_zone_demo_ftp, state.ftpW!!),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                AdaptiveGrid(
                    columns = if (columns == 1) 1 else 2,
                    items = listOf<@Composable (Modifier) -> Unit>(
                        { m -> MetricTile(stringResource(R.string.metric_cadence), metrics.cadenceRpm, MetricKind.CADENCE, units, m, HelpTopic.METRICS_CADENCE, large = true) },
                        { m -> MetricTile(stringResource(R.string.metric_heart_rate), metrics.heartRateBpm, MetricKind.HEART_RATE, units, m, HelpTopic.METRICS_HEART_RATE, large = true) },
                    ),
                )
            }

            AdaptiveGrid(
                columns = columns,
                items = listOf<@Composable (Modifier) -> Unit>(
                    { m -> MetricTile(stringResource(R.string.metric_speed), metrics.speedMps, MetricKind.SPEED, units, m, HelpTopic.METRICS_SPEED) },
                    { m -> MetricTile(stringResource(R.string.metric_distance), metrics.distanceM, MetricKind.DISTANCE, units, m) },
                    { m -> MetricTile(stringResource(R.string.metric_riding_time), metrics.ridingTimeMs, MetricKind.DURATION, units, m, HelpTopic.METRICS_TIME) },
                    { m -> MetricTile(stringResource(R.string.metric_moving_time), metrics.movingTimeMs, MetricKind.DURATION, units, m, HelpTopic.METRICS_MOVING_TIME) },
                    { m -> MetricTile(stringResource(R.string.metric_elevation_gain), metrics.elevationGainM, MetricKind.ELEVATION, units, m, HelpTopic.METRICS_ELEVATION) },
                    { m -> MetricTile(stringResource(R.string.metric_grade), metrics.slopeFraction, MetricKind.GRADE, units, m) },
                ),
            )

            com.fariddroid.gtbikecompanion.feature.world.GameMapCard(state.feed)
            ModControl(state, onToggleMod, onDismissOperation)
            StatusCard(state)
            SensorsCard(state, onOpenDevices)
        }
    }
}

@Composable
private fun Header(state: RideUiState) {
    val snapshot = state.feed.snapshot
    val title = when {
        snapshot.activeRoute != null -> stringResource(R.string.ride_title_route, snapshot.activeRoute!!.id)
        snapshot.layers.adapter == AdapterLayer.FRESH && snapshot.layers.mod == ModLayer.DATA_READY -> stringResource(R.string.ride_title_free)
        else -> stringResource(R.string.ride_title_no_session)
    }
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (snapshot.layers.adapter == AdapterLayer.STALE && snapshot.snapshotAgeMs != null) {
            val seconds = (snapshot.snapshotAgeMs!! / 1000).toInt()
            Row {
                Text(
                    pluralStringResource(R.plurals.stale_seconds, seconds, seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                OptionHelpButton(HelpTopic.CONNECTION_STALE, stringResource(R.string.state_stale))
            }
        }
    }
}

@Composable
private fun ModControl(state: RideUiState, onToggle: () -> Unit, onDismiss: () -> Unit) {
    val decision = state.toggleDecision
    val reason = (decision as? ActivationDecision.Blocked)?.let { stringResource(reasonText(it.reasonCode)) }
    val allowed = decision is ActivationDecision.Allowed
    SectionCard(title = stringResource(R.string.layer_mod)) {
        SettingRow(
            label = stringResource(R.string.mod_active_label),
            help = if (state.modActive) HelpTopic.MOD_DEACTIVATE else HelpTopic.MOD_ACTIVATE,
            enabled = allowed,
            reason = reason,
            control = {
                // The switch shows the OBSERVED state. A tap sends mod.setActive for the opposite state and the
                // switch only moves when later snapshots show the mod changed (never optimistic).
                Switch(
                    checked = state.modActive,
                    onCheckedChange = { onToggle() },
                    enabled = allowed,
                    modifier = Modifier.testTag("modSwitch"),
                )
            },
        )
        OperationNotice(state.operation, onDismiss)
    }
}

/**
 * Shown when the PC stops accepting this phone. Pairing lasts 30 days, so this usually means it expired: the user
 * scans the QR the PC console shows and the phone pairs again by itself. Typing the code is a fallback.
 */
@Composable
private fun RenewPairingCard(
    renew: RenewPairingUi,
    onPin: (String) -> Unit,
    onRenew: () -> Unit,
    onQr: (String) -> Unit,
    onScanFailed: () -> Unit,
    onShowPin: () -> Unit,
    onAnotherPc: () -> Unit,
) {
    val title = if (renew.expired) R.string.pair_expired_title else R.string.pair_again_title
    val body = if (renew.expired) R.string.pair_expired_body else R.string.pair_again_body
    val context = LocalContext.current
    SectionCard(title = stringResource(title), help = HelpTopic.CONNECTION_PAIRING) {
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { startQrScan(context, onQr, onScanFailed) },
            enabled = !renew.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("renewScanQr"),
        ) { Text(stringResource(R.string.connect_scan_qr)) }
        renew.errorCode?.let {
            Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("renewError"))
        }
        if (!renew.showPin) {
            TextButton(onClick = onShowPin, enabled = !renew.working, modifier = Modifier.fillMaxWidth().testTag("renewShowPin")) {
                Text(stringResource(R.string.pair_type_code_instead))
            }
        } else {
            RenewPinEntry(renew, onPin, onRenew)
        }
        // A different bridge (reinstalled, other PC) needs the full flow with identity check.
        TextButton(onClick = onAnotherPc, modifier = Modifier.fillMaxWidth().testTag("pairAgain")) {
            Text(stringResource(R.string.pair_use_another_pc))
        }
    }
}

@Composable
private fun RenewPinEntry(renew: RenewPairingUi, onPin: (String) -> Unit, onRenew: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = renew.pin,
            onValueChange = onPin,
            singleLine = true,
            enabled = !renew.working,
            label = { Text(stringResource(R.string.connect_pin)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onRenew() }),
            modifier = Modifier.fillMaxWidth().testTag("renewPin"),
        )
        OutlinedButton(
            onClick = onRenew,
            enabled = !renew.working && renew.pin.length == 6,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("renewPairing"),
        ) { Text(stringResource(R.string.pair_renew_button)) }
    }
}

@Composable
private fun StatusCard(state: RideUiState) {
    val layers = state.feed.snapshot.layers
    SectionCard(title = stringResource(R.string.status_title), help = HelpTopic.CONNECTION_STATUS) {
        StatusLine(
            stringResource(R.string.layer_bridge),
            if (state.isDemo) stringResource(R.string.state_demo) else stringResource(R.string.state_fresh),
            if (state.isDemo) StatusTone.WARN else StatusTone.GOOD,
        )
        val (adapterText, adapterTone) = Labels.adapter(layers.adapter, layers.binding)
        StatusLine(stringResource(R.string.layer_adapter), stringResource(adapterText), adapterTone)
        val (modText, modTone) = Labels.mod(layers.mod)
        StatusLine(stringResource(R.string.layer_mod), stringResource(modText), modTone)
        // A receiver the PC does not have (no ANT+ stick, say) is hidden while the other one works.
        if (ReceiverState.shown(layers.antReceiver, layers.bleReceiver)) {
            val (antText, antTone) = Labels.receiver(layers.antReceiver)
            StatusLine(stringResource(R.string.layer_ant), stringResource(antText), antTone)
        }
        if (ReceiverState.shown(layers.bleReceiver, layers.antReceiver)) {
            val (bleText, bleTone) = Labels.receiver(layers.bleReceiver)
            StatusLine(stringResource(R.string.layer_ble), stringResource(bleText), bleTone)
        }
    }
}

@Composable
private fun SensorsCard(state: RideUiState, onOpenDevices: () -> Unit) {
    val sensors = state.feed.snapshot.sensors
    SectionCard(title = stringResource(R.string.sensors_title), help = HelpTopic.DEVICES_MISSING) {
        if (sensors.isEmpty()) {
            Text(stringResource(R.string.sensor_unknown), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }
        sensors.forEach { sensor ->
            val value = when (sensor.present) {
                true -> sensor.name?.let { it + " · " + sensor.source.name } ?: stringResource(R.string.sensor_present, sensor.source.name, Labels.shortDeviceId(sensor.deviceId!!))
                false -> stringResource(R.string.sensor_absent)
                null -> stringResource(R.string.sensor_unknown)
            }
            StatusLine(
                stringResource(Labels.sensorRole(sensor.role)),
                value,
                if (sensor.present == true) StatusTone.GOOD else StatusTone.NEUTRAL,
            )
        }
        androidx.compose.material3.TextButton(onClick = onOpenDevices, modifier = Modifier.testTag("manageDevices")) {
            Text(stringResource(R.string.devices_manage))
        }
    }
}
