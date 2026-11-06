package com.fariddroid.gtbikecompanion.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.BoolSetting
import com.fariddroid.gtbikecompanion.core.model.ChoiceSetting
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.DifficultySetting
import com.fariddroid.gtbikecompanion.core.model.NumberSetting
import com.fariddroid.gtbikecompanion.ui.components.DemoBanner
import com.fariddroid.gtbikecompanion.ui.components.OperationNotice
import com.fariddroid.gtbikecompanion.ui.components.RadioGroup
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.SettingRow
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton

@Composable
fun PcSettingsRoute(viewModel: PcSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PcSettingsScreen(state, viewModel)
}

@Composable
private fun PcSettingsScreen(state: PcSettingsUiState, vm: PcSettingsViewModel) {
    val settings = state.data.settings
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 12.dp).testTag("pcSettingsScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.feed?.origin == DataOrigin.DEMO) DemoBanner(null)
        Text(stringResource(R.string.pc_settings_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.pc_settings_intro), style = MaterialTheme.typography.bodyMedium)
        state.data.errorCode?.let { Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error) }
        OperationNotice(state.operation, vm::dismissOperation)
        if (settings == null) {
            if (state.data.loading) Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = vm::reload, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
            return@Column
        }

        SectionCard(stringResource(R.string.pc_settings_profile)) {
            val weightRange = state.weightRange(settings.weightKg)
            NumberField(
                label = stringResource(if (state.imperial) R.string.profile_weight_lb else R.string.profile_weight_kg),
                value = state.data.draft.weight,
                onChange = vm::setWeight,
                setting = settings.weightKg,
                range = "${weightRange.first}–${weightRange.last}",
                error = state.weightError(),
                globalBlock = state.profileBlock,
                help = HelpTopic.PROFILE_WEIGHT,
                tag = "weightField",
            )
            NumberField(stringResource(R.string.profile_ftp), state.data.draft.ftp, vm::setFtp, settings.ftpW, "50–500", state.numberError(state.data.draft.ftp, settings.ftpW), state.profileBlock, HelpTopic.PROFILE_FTP, "ftpField")
            NumberField(stringResource(R.string.profile_hr_max), state.data.draft.hrMax, vm::setHrMax, settings.hrMaxBpm, "50–220", state.numberError(state.data.draft.hrMax, settings.hrMaxBpm), state.profileBlock, HelpTopic.PROFILE_HR_MAX, "hrMaxField")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = vm::discardDraft, enabled = state.data.draft.dirty, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_discard))
                }
                Button(
                    onClick = vm::saveProfile,
                    enabled = state.profileBlock == null && vm.profileChanges() != null,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("saveProfile"),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }

        SectionCard(stringResource(R.string.pc_settings_ride)) {
            BoolRow(stringResource(R.string.ride_autopilot), settings.autopilot, state.rideBlock, HelpTopic.RIDE_AUTOPILOT, vm::setAutopilot, "autopilotSwitch")
            BoolRow(stringResource(R.string.ride_obstacles), settings.avoidObstacles, state.rideBlock, HelpTopic.RIDE_OBSTACLES, vm::setObstacles, "obstaclesSwitch")
            BoolRow(stringResource(R.string.ride_road_feel), settings.roadFeel, state.rideBlock, HelpTopic.RIDE_ROAD_FEEL, vm::setRoadFeel, "roadFeelSwitch")
            DifficultyRow(settings.difficulty, state.rideBlock, vm::setDifficulty)
        }

        SectionCard(stringResource(R.string.pc_settings_appearance)) {
            ChoiceRow(stringResource(R.string.appearance_bike), settings.bike, state.appearanceBlock, HelpTopic.APPEARANCE_BIKE, vm::selectBike)
            ChoiceRow(stringResource(R.string.appearance_character), settings.character, state.appearanceBlock, HelpTopic.APPEARANCE_CHARACTER, vm::selectCharacter)
        }
    }
}

/** Field-level reason first (the mod's own state), then the connection-level one. */
@Composable
private fun blockText(fieldEditable: Boolean, fieldReason: String?, globalBlock: String?): String? = when {
    globalBlock != null -> stringResource(reasonText(globalBlock))
    !fieldEditable -> stringResource(reasonText(fieldReason))
    else -> null
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    setting: NumberSetting,
    range: String,
    error: FieldError?,
    globalBlock: String?,
    help: HelpTopic,
    tag: String,
) {
    val reason = blockText(setting.editable, setting.reasonCode, globalBlock)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            enabled = reason == null,
            singleLine = true,
            isError = error != null,
            label = { Text(label) },
            supportingText = {
                Text(
                    when (error) {
                        FieldError.INVALID -> stringResource(R.string.field_whole_number)
                        FieldError.OUT_OF_RANGE -> stringResource(R.string.field_range, range)
                        null -> reason ?: stringResource(R.string.field_range, range)
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag(tag),
        )
        OptionHelpButton(help, label, currentReason = reason)
    }
}

@Composable
private fun BoolRow(label: String, setting: BoolSetting, globalBlock: String?, help: HelpTopic, onChange: (Boolean) -> Unit, tag: String) {
    val reason = blockText(setting.editable, setting.reasonCode, globalBlock)
    SettingRow(
        label = label,
        help = help,
        enabled = reason == null,
        reason = reason,
        control = {
            // Shows the value read back from the PC; it moves only after the PC confirms.
            Switch(checked = setting.value == true, onCheckedChange = onChange, enabled = reason == null && setting.value != null, modifier = Modifier.testTag(tag))
        },
    )
}

@Composable
private fun DifficultyRow(setting: DifficultySetting, globalBlock: String?, onSelect: (Int) -> Unit) {
    val reason = blockText(setting.editable, setting.reasonCode, globalBlock)
    SettingRow(label = stringResource(R.string.ride_difficulty), help = HelpTopic.RIDE_DIFFICULTY, enabled = reason == null, reason = reason)
    val label = @Composable { level: Int -> if (level == DifficultySetting.DISABLED) stringResource(R.string.ride_difficulty_disabled) else "$level %" }
    val options = setting.allowed.map { it to label(it) }
    val value = setting.value
    if (reason == null && value != null) {
        RadioGroup(options = options, selected = value, onSelect = { if (it != value) onSelect(it) }, modifier = Modifier.testTag("difficulty"))
    } else {
        Text(value?.let { label(it) } ?: stringResource(R.string.metric_missing), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChoiceRow(label: String, setting: ChoiceSetting, globalBlock: String?, help: HelpTopic, onSelect: (String) -> Unit) {
    val reason = blockText(setting.editable, setting.reasonCode, globalBlock)
    val current = setting.options.firstOrNull { it.id == setting.selectedId }?.label
    SettingRow(label = label, help = help, enabled = reason == null, value = current ?: stringResource(R.string.metric_missing), reason = reason)
    val selected = setting.selectedId
    if (reason == null && selected != null) {
        RadioGroup(
            options = setting.options.map { it.id to it.label },
            selected = selected,
            onSelect = { if (it != selected) onSelect(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
