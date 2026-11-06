package com.fariddroid.gtbikecompanion.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.DeviceAction
import com.fariddroid.gtbikecompanion.core.model.DevicePolicy
import com.fariddroid.gtbikecompanion.core.model.DeviceRole
import com.fariddroid.gtbikecompanion.core.model.DeviceRoles
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.core.rules.ControlRules
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class DevicesUiState(val roles: DeviceRoles?, val errorCode: String?, val feed: TelemetryFeed?, val operation: OperationUi?) {
    private val busy get() = operation == OperationUi.Sending || operation == OperationUi.Waiting
    val block: String? get() = if (busy) "OPERATION_IN_PROGRESS" else ControlRules.blockReason(feed, CapabilityId.DEVICES_CONFIGURE)
}

@HiltViewModel
class DevicesViewModel @Inject constructor(telemetry: TelemetryRepository, private val control: ControlRepository) : ViewModel() {
    private val tracker = OperationTracker(viewModelScope, control)

    /** Every 2 s while visible: the PC may switch sources on its own (plan §9, sensor flow). */
    private val roles = flow {
        while (true) {
            emit(control.deviceRoles())
            delay(2_000)
        }
    }

    val state: StateFlow<DevicesUiState> = combine(roles, telemetry.feed, tracker.state) { r, feed, op ->
        DevicesUiState((r as? Outcome.Success)?.value, (r as? Outcome.Failure)?.code, feed, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState(null, null, null, null))

    fun act(role: DeviceRole, action: DeviceAction) {
        val s = state.value
        val revision = s.roles?.revision ?: return
        if (s.block != null || !role.allows(action)) return
        val expected = if (action == DeviceAction.AUTO) null else role.current ?: return
        tracker.send(Command.ConfigureDevice(role.role, action, expected, revision), s.feed?.snapshot?.adapterInstanceId)
    }

    fun dismissOperation() = tracker.dismiss()
}

@Composable
fun DevicesRoute(onOpenPhoneScan: () -> Unit = {}, viewModel: DevicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("devicesScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.devices_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
            OptionHelpButton(HelpTopic.DEVICES_POLICY, stringResource(R.string.devices_title))
        }
        Text(stringResource(R.string.devices_intro), style = MaterialTheme.typography.bodyMedium)
        // P11: the phone lists BLE sensors; the PC locks the chosen one and connects to it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onOpenPhoneScan, modifier = Modifier.heightIn(min = 48.dp).testTag("openPhoneScan")) { Text(stringResource(R.string.devices_phone_scan)) }
            OptionHelpButton(HelpTopic.DEVICES_PHONE_SCAN, stringResource(R.string.devices_phone_scan))
        }
        OperationNotice(state.operation, viewModel::dismissOperation)
        val roles = state.roles
        val unavailable = state.errorCode ?: roles?.reasonCode
        if (unavailable != null) Text(stringResource(reasonText(unavailable)), color = MaterialTheme.colorScheme.secondary)
        roles?.roles?.filter { it.visible }?.forEach { role -> RoleCard(role, state.block) { viewModel.act(role, it) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleCard(role: DeviceRole, block: String?, onAction: (DeviceAction) -> Unit) {
    SectionCard(stringResource(Labels.sensorRole(role.role)), help = HelpTopic.DEVICES_TECHNOLOGY) {
        val current = role.current
        StatusLine(
            stringResource(R.string.devices_current),
            when {
                current == null -> stringResource(R.string.devices_none)
                // P12: the name the sensor advertises; the ID only for ANT and unnamed sensors.
                current.name != null -> current.name + " · " + current.source.name
                else -> stringResource(R.string.sensor_present, current.source.name, Labels.shortDeviceId(current.deviceId)) + " · " + current.sensorType
            },
            if (current == null) StatusTone.NEUTRAL else StatusTone.GOOD,
        )
        StatusLine(
            stringResource(R.string.devices_policy),
            stringResource(
                when (role.policy) {
                    DevicePolicy.AUTO -> R.string.devices_policy_auto
                    DevicePolicy.LOCKED -> R.string.devices_policy_locked
                    DevicePolicy.FILTERED -> R.string.devices_policy_filtered
                },
            ),
            StatusTone.NEUTRAL,
        )
        if (block != null) Text(stringResource(reasonText(block)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ActionButton(R.string.devices_auto, HelpTopic.DEVICES_AUTO, block == null && role.canAuto, "auto:${role.role}") { onAction(DeviceAction.AUTO) }
            ActionButton(R.string.devices_lock, HelpTopic.DEVICES_LOCK, block == null && role.canLock, "lock:${role.role}") { onAction(DeviceAction.LOCK) }
            ActionButton(R.string.devices_skip, HelpTopic.DEVICES_SKIP, block == null && role.canSkip, "skip:${role.role}") { onAction(DeviceAction.SKIP) }
        }
    }
}

@Composable
private fun ActionButton(label: Int, help: HelpTopic, enabled: Boolean, tag: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) { Text(stringResource(label)) }
        OptionHelpButton(help, stringResource(label))
    }
}
