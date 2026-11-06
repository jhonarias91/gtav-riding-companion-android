package com.fariddroid.gtbikecompanion.feature.devices

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.BleSensorClassifier
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PhoneSensor
import com.fariddroid.gtbikecompanion.core.model.PhoneSensorType
import com.fariddroid.gtbikecompanion.core.model.SensorDirectory
import com.fariddroid.gtbikecompanion.core.model.SensorListItem
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import com.fariddroid.gtbikecompanion.core.model.DeviceAction
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.core.rules.ControlRules
import com.fariddroid.gtbikecompanion.data.PhoneSensorScanner
import com.fariddroid.gtbikecompanion.data.ScanAvailability
import com.fariddroid.gtbikecompanion.data.ScanFailedException
import com.fariddroid.gtbikecompanion.feature.common.OperationTracker
import com.fariddroid.gtbikecompanion.feature.common.OperationUi
import com.fariddroid.gtbikecompanion.ui.components.DemoBanner
import com.fariddroid.gtbikecompanion.ui.components.OperationNotice
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.format.Labels
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class PhoneScan(
    val availability: ScanAvailability,
    val scanning: Boolean,
    val sensors: List<PhoneSensor>,
    val failedCode: Int?,
)

data class PhoneScanUiState(
    val scan: PhoneScan,
    val roles: DeviceRoles?,
    val feed: TelemetryFeed?,
    val operation: OperationUi?,
) {
    private val busy get() = operation == OperationUi.Sending || operation == OperationUi.Waiting
    private fun block(id: CapabilityId): String? = if (busy) "OPERATION_IN_PROGRESS" else ControlRules.blockReason(feed, id)
    val connectBlock: String? get() = block(CapabilityId.DEVICES_ASSIGN)
    val disconnectBlock: String? get() = block(CapabilityId.DEVICES_DISCONNECT)

    /** P12: sensors the PC uses (even if they stopped advertising) plus those the phone hears, connected first. */
    val items: List<SensorListItem> get() = SensorDirectory.merge(scan.sensors, roles)

    fun connectRole(item: SensorListItem): SensorRoleType? = item.type?.let { SensorDirectory.connectRole(it, roles) }
}

@HiltViewModel
class PhoneScanViewModel @Inject constructor(
    telemetry: TelemetryRepository,
    private val control: ControlRepository,
    private val scanner: PhoneSensorScanner,
) : ViewModel() {
    private val tracker = OperationTracker(viewModelScope, control)
    private val scan = MutableStateFlow(PhoneScan(scanner.availability(), scanning = false, sensors = emptyList(), failedCode = null))
    private var job: Job? = null

    /** The PC decides what is connected: re-read its roles every 2 s while visible, like the P5 screen. */
    private val roles = flow {
        while (true) {
            emit((control.deviceRoles() as? Outcome.Success)?.value)
            delay(2_000)
        }
    }

    val state: StateFlow<PhoneScanUiState> = combine(scan, roles, telemetry.feed, tracker.state) { s, r, feed, op ->
        PhoneScanUiState(s, r, feed, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhoneScanUiState(scan.value, null, null, null))

    val requiredPermissions: Array<String> get() = scanner.requiredPermissions

    fun refreshAvailability() {
        scan.value = scan.value.copy(availability = scanner.availability())
    }

    /** One bounded scan (Android throttles apps that restart scans often). Results merge by address. */
    fun start() {
        refreshAvailability()
        if (scan.value.availability != ScanAvailability.READY || job?.isActive == true) return
        scan.value = scan.value.copy(scanning = true, failedCode = null)
        job = viewModelScope.launch {
            withTimeoutOrNull(SCAN_MS) {
                scanner.advertisements()
                    .catch { e -> scan.value = scan.value.copy(failedCode = (e as? ScanFailedException)?.errorCode ?: -1) }
                    .collect { ad ->
                        val sensor = BleSensorClassifier.sensor(ad) ?: return@collect
                        val others = scan.value.sensors.filterNot { it.address == sensor.address }
                        scan.value = scan.value.copy(sensors = (others + sensor).sortedByDescending { it.rssi })
                    }
            }
            scan.value = scan.value.copy(scanning = false)
        }
    }

    fun stop() {
        job?.cancel()
        scan.value = scan.value.copy(scanning = false)
    }

    /** "Connect": the PC locks this BLE sensor for its main function and connects to it itself. */
    fun connect(item: SensorListItem) {
        val s = state.value
        val revision = s.roles?.revision ?: return
        val type = item.type ?: return
        val role = s.connectRole(item) ?: return
        if (s.connectBlock != null || item.source != SensorSource.BLE) return
        tracker.send(Command.AssignDevice(role, type, item.deviceId, revision), s.feed?.snapshot?.adapterInstanceId)
    }

    /** "Disconnect": the PC drops this BLE sensor; nothing of that type reconnects until one is connected again. */
    fun disconnect(item: SensorListItem) {
        val s = state.value
        val revision = s.roles?.revision ?: return
        val type = item.type ?: return
        if (s.disconnectBlock != null || !item.connected || item.source != SensorSource.BLE) return
        tracker.send(Command.DisconnectDevice(type, item.deviceId, revision), s.feed?.snapshot?.adapterInstanceId)
    }

    /** "Cancel" on a sensor the PC is still waiting for: that function goes back to the mod's Auto. */
    fun cancelWaiting(item: SensorListItem) {
        val s = state.value
        val roles = s.roles ?: return
        val revision = roles.revision ?: return
        if (s.connectBlock != null) return
        val role = roles.roles.firstOrNull { it.role in item.waitingRoles && it.canAuto } ?: return
        tracker.send(Command.ConfigureDevice(role.role, DeviceAction.AUTO, null, revision), s.feed?.snapshot?.adapterInstanceId)
    }

    fun dismissOperation() = tracker.dismiss()

    private companion object {
        const val SCAN_MS = 30_000L
    }
}

@Composable
fun PhoneScanRoute(viewModel: PhoneScanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        viewModel.refreshAvailability()
        if (granted.values.all { it }) viewModel.start()
    }
    LaunchedEffect(Unit) { viewModel.start() }
    // Scanning stops as soon as the screen leaves; it never runs in the background.
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("phoneScanScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.phone_scan_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            OptionHelpButton(HelpTopic.DEVICES_PHONE_SCAN, stringResource(R.string.phone_scan_title))
        }
        Text(stringResource(R.string.phone_scan_intro), style = MaterialTheme.typography.bodyMedium)
        OperationNotice(state.operation, viewModel::dismissOperation)

        val scan = state.scan
        when (scan.availability) {
            ScanAvailability.NO_BLUETOOTH -> Text(stringResource(R.string.phone_scan_no_bluetooth), color = MaterialTheme.colorScheme.secondary)
            ScanAvailability.PERMISSION_NEEDED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { permissions.launch(viewModel.requiredPermissions) }, modifier = Modifier.heightIn(min = 48.dp).testTag("phoneScanPermission")) {
                    Text(stringResource(R.string.phone_scan_allow))
                }
                OptionHelpButton(HelpTopic.DEVICES_PHONE_PERMISSION, stringResource(R.string.phone_scan_allow))
            }
            ScanAvailability.BLUETOOTH_OFF -> SettingsHint(R.string.phone_scan_bluetooth_off) {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
            ScanAvailability.LOCATION_OFF -> SettingsHint(R.string.phone_scan_location_off) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            ScanAvailability.READY -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scan.scanning) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.phone_scan_scanning), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = viewModel::stop, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.phone_scan_stop)) }
                } else {
                    Button(onClick = viewModel::start, modifier = Modifier.heightIn(min = 48.dp).testTag("phoneScanStart")) { Text(stringResource(R.string.phone_scan_start)) }
                }
            }
        }
        if (scan.failedCode != null) Text(stringResource(R.string.phone_scan_failed), color = MaterialTheme.colorScheme.error)
        state.connectBlock?.let { Text(stringResource(reasonText(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary) }
        val items = state.items
        if (scan.availability == ScanAvailability.READY && !scan.scanning && items.isEmpty()) {
            Text(stringResource(R.string.phone_scan_empty), style = MaterialTheme.typography.bodyMedium)
        }
        val (connected, others) = items.partition { it.connected }
        if (connected.isNotEmpty()) Text(stringResource(R.string.phone_scan_section_pc), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        connected.forEach { SensorCard(it, state, viewModel) }
        if (others.isNotEmpty()) Text(stringResource(R.string.phone_scan_section_nearby), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        others.forEach { SensorCard(it, state, viewModel) }
    }
}

@Composable
private fun SettingsHint(text: Int, open: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(text), color = MaterialTheme.colorScheme.secondary)
        OutlinedButton(onClick = open, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.phone_scan_open_settings)) }
    }
}

@Composable
private fun SensorCard(item: SensorListItem, state: PhoneScanUiState, vm: PhoneScanViewModel) {
    val typeText = item.type?.let { stringResource(typeLabel(it)) } ?: item.pcSensorType ?: stringResource(R.string.phone_scan_unnamed)
    // The name the sensor advertises; without one, its type plus the last address bytes to tell two apart.
    val title = item.name ?: listOfNotNull(typeText, item.shortAddress?.let { "…$it" }).joinToString(" ")
    SectionCard(title) {
        if (item.name != null) StatusLine(stringResource(R.string.phone_scan_type), typeText, StatusTone.NEUTRAL)
        if (item.source == SensorSource.ANT) StatusLine(stringResource(R.string.phone_scan_technology), "ANT+", StatusTone.NEUTRAL)
        item.rssi?.let { StatusLine(stringResource(R.string.phone_scan_signal), stringResource(signalLabel(it)), if (it >= -75) StatusTone.GOOD else StatusTone.WARN) }
        when {
            item.connected -> {
                val uses = item.connectedRoles.map { stringResource(Labels.sensorRole(it)) }.joinToString(", ")
                StatusLine(stringResource(R.string.phone_scan_connected), uses, StatusTone.GOOD)
                if (item.source == SensorSource.BLE && item.type != null) {
                    val block = state.disconnectBlock
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.disconnect(item) }, enabled = block == null, modifier = Modifier.heightIn(min = 48.dp).testTag("disconnect:${item.deviceId}")) {
                            Text(stringResource(R.string.phone_scan_disconnect))
                        }
                        OptionHelpButton(HelpTopic.DEVICES_PHONE_DISCONNECT, stringResource(R.string.phone_scan_disconnect), currentReason = block?.let { stringResource(reasonText(it)) })
                    }
                } else {
                    Text(stringResource(R.string.phone_scan_managed_on_pc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
            item.waitingRoles.isNotEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusLine(stringResource(R.string.phone_scan_waiting), item.waitingRoles.map { stringResource(Labels.sensorRole(it)) }.joinToString(", "), StatusTone.WARN, Modifier.weight(1f))
                    OptionHelpButton(HelpTopic.DEVICES_PHONE_WAITING, stringResource(R.string.phone_scan_waiting))
                }
                OutlinedButton(onClick = { vm.cancelWaiting(item) }, enabled = state.connectBlock == null, modifier = Modifier.heightIn(min = 48.dp).testTag("cancel:${item.deviceId}")) {
                    Text(stringResource(R.string.phone_scan_cancel_waiting))
                }
            }
            else -> {
                val role = state.connectRole(item)
                if (role == null) {
                    Text(stringResource(R.string.phone_scan_no_function), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                } else {
                    StatusLine(stringResource(R.string.phone_scan_will_use_for), stringResource(Labels.sensorRole(role)), StatusTone.NEUTRAL)
                    Button(onClick = { vm.connect(item) }, enabled = state.connectBlock == null, modifier = Modifier.heightIn(min = 48.dp).testTag("connect:${item.deviceId}")) {
                        Text(stringResource(R.string.phone_scan_connect))
                    }
                }
            }
        }
    }
}

private fun typeLabel(type: PhoneSensorType): Int = when (type) {
    PhoneSensorType.SMART_BIKE -> R.string.phone_type_smart_bike
    PhoneSensorType.TRAINER -> R.string.phone_type_trainer
    PhoneSensorType.POWER_METER -> R.string.phone_type_power_meter
    PhoneSensorType.SPEED_CADENCE -> R.string.phone_type_speed_cadence
    PhoneSensorType.RUNNING_POD -> R.string.phone_type_running_pod
    PhoneSensorType.STEERING -> R.string.phone_type_steering
    PhoneSensorType.HEART_RATE -> R.string.phone_type_heart_rate
    PhoneSensorType.CONTROLLER -> R.string.phone_type_controller
}

private fun signalLabel(rssi: Int): Int = when {
    rssi >= -65 -> R.string.phone_signal_strong
    rssi >= -80 -> R.string.phone_signal_medium
    else -> R.string.phone_signal_weak
}
