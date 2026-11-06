package com.fariddroid.gtbikecompanion.feature.options

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.BotType
import com.fariddroid.gtbikecompanion.core.model.TrafficStyle
import com.fariddroid.gtbikecompanion.ui.components.RadioGroup
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import kotlin.math.roundToInt

@Composable
fun GameOptionsRoute(viewModel: GameOptionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameOptionsScreen(state, viewModel)
}

/** Waypoint colours offered as swatches (AARRGGBB); the file's own colour is kept until another is chosen. */
private val WAYPOINT_COLORS = listOf("FFADFF2F", "FF47D9E3", "FFFFD15C", "FFFF9A4D", "FFFF5FAE", "FFFFFFFF", "FF3B82F6", "FFEF4444")

@Composable
private fun GameOptionsScreen(state: GameOptionsUiState, vm: GameOptionsViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp, vertical = 12.dp).testTag("gameOptionsScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.options_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.options_intro), style = MaterialTheme.typography.bodyMedium)
        StatusBanner(state)
        state.message?.let { Message(it, vm::dismissMessage) }
        val o = state.draft
        if (o == null) {
            if (state.loading) Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = vm::reload, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
            return@Column
        }
        val on = state.editable
        SaveButtons(state, vm)

        SectionCard(stringResource(R.string.options_traffic), help = HelpTopic.MOD_CONFIG_OPTIONS) {
            SliderRow(stringResource(R.string.options_traffic_percent), o.trafficBikesPercent ?: 0, 0..100, 5, "%", on, vm::setTrafficPercent, "trafficPercent")
            Text(stringResource(R.string.options_traffic_style), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
            if (on) RadioGroup(
                options = listOf(TrafficStyle.NORMAL to stringResource(R.string.options_style_normal), TrafficStyle.AGGRESSIVE to stringResource(R.string.options_style_aggressive), TrafficStyle.BREAKS_LAWS to stringResource(R.string.options_style_breaks_laws)),
                selected = o.trafficStyle ?: TrafficStyle.NORMAL, onSelect = vm::setTrafficStyle,
            ) else Value(when (o.trafficStyle) { TrafficStyle.AGGRESSIVE -> stringResource(R.string.options_style_aggressive); TrafficStyle.BREAKS_LAWS -> stringResource(R.string.options_style_breaks_laws); else -> stringResource(R.string.options_style_normal) })
            Hint(stringResource(R.string.options_traffic_hint))
        }

        SectionCard(stringResource(R.string.options_crowds)) {
            SwitchRow(stringResource(R.string.options_crowds_enabled), o.crowdsEnabled == true, on, vm::setCrowdsEnabled, "crowdsEnabled")
            SliderRow(stringResource(R.string.options_crowds_slope), o.crowdsSlopePercent ?: 0, 0..20, 1, "%", on && o.crowdsEnabled == true, vm::setCrowdSlope, "crowdsSlope")
            SliderRow(stringResource(R.string.options_crowds_amount), o.crowdsAmount ?: 0, 0..100, 5, "", on && o.crowdsEnabled == true, vm::setCrowdAmount, "crowdsAmount")
        }

        SectionCard(stringResource(R.string.options_bots)) {
            val bots = o.bots.orEmpty()
            Text(pluralStringResource(R.plurals.options_bots_total, bots.size, bots.size, 5), style = MaterialTheme.typography.bodyMedium)
            BotType.entries.forEach { type ->
                Counter(botLabel(type), bots.count { it == type }, on && type in bots, on && bots.size < 5, { vm.changeBots(type, -1) }, { vm.changeBots(type, +1) }, "bots:${type.name}")
            }
            Hint(stringResource(R.string.options_bots_hint))
        }

        SectionCard(stringResource(R.string.options_riding)) {
            Text(stringResource(R.string.options_defensive), style = MaterialTheme.typography.bodyLarge)
            val levels = listOf(0 to stringResource(R.string.options_defensive_passive), 1 to stringResource(R.string.options_defensive_defensive), 2 to stringResource(R.string.options_defensive_aggressive))
            if (on) RadioGroup(levels, o.defensiveLevel ?: 1, vm::setDefensive) else Value(levels.firstOrNull { it.first == o.defensiveLevel }?.second ?: "—")
            Hint(stringResource(R.string.options_defensive_hint))
            val steering = o.fineSteering ?: 0.7
            LabeledSlider(stringResource(R.string.options_fine_steering), "%.2f".format(steering), steering.toFloat(), 0.1f..1f, 17, on, { vm.setFineSteering(it.toDouble()) }, "fineSteering")
            Hint(stringResource(R.string.options_fine_steering_hint))
        }

        SectionCard(stringResource(R.string.options_waypoints)) {
            Text(stringResource(R.string.options_waypoint_color), style = MaterialTheme.typography.bodyLarge)
            ColorSwatches(o.waypointColor ?: "FFADFF2F", on, vm::setWaypointColor)
            SliderRow(stringResource(R.string.options_waypoint_radius), o.waypointRadius ?: 20, 0..40, 1, "", on, vm::setWaypointRadius, "waypointRadius")
            Hint(stringResource(R.string.options_waypoint_hint))
        }

        SectionCard(stringResource(R.string.options_screenshots)) {
            SliderRow(stringResource(R.string.options_screenshot_km), o.screenshotIntervalKm ?: 5, 1..50, 1, " km", on, vm::setScreenshotKm, "screenshotKm")
            NumberText(stringResource(R.string.options_screenshot_offset), state.texts.xOffset, vm::setXOffsetText, state.xOffsetError, stringResource(R.string.field_range, "-20000–20000"), on, true, "screenshotOffset")
            Hint(stringResource(R.string.options_screenshot_hint))
        }

        SectionCard(stringResource(R.string.options_menu_screen)) {
            SwitchRow(stringResource(R.string.options_menu_auto_open), o.menuAutoOpen == true, on, vm::setMenuAutoOpen, "menuAutoOpen")
            SwitchRow(stringResource(R.string.options_show_clock), o.showClock == true, on, vm::setShowClock, "showClock")
            SwitchRow(stringResource(R.string.options_debug_window), o.debugWindow == true, on, vm::setDebugWindow, "debugWindow")
            SliderRow(stringResource(R.string.options_log_level), o.logLevel ?: 3, 0..5, 1, "", on, vm::setLogLevel, "logLevel")
            Hint(stringResource(R.string.options_log_hint))
        }

        SectionCard(stringResource(R.string.options_gps)) {
            NumberText(stringResource(R.string.options_gps_lat), state.texts.lat, vm::setLatText, state.latError, stringResource(R.string.field_range, "-90–90"), on, false, "gpsLat")
            NumberText(stringResource(R.string.options_gps_lon), state.texts.lon, vm::setLonText, state.lonError, stringResource(R.string.field_range, "-180–180"), on, false, "gpsLon")
            Hint(stringResource(R.string.options_gps_hint))
        }

        SectionCard(stringResource(R.string.options_network)) {
            SwitchRow(stringResource(R.string.options_telemetry), o.telemetry == true, on, vm::setTelemetry, "telemetry")
            SwitchRow(stringResource(R.string.options_multiplayer), o.multiplayer == true, on, vm::setMultiplayer, "multiplayer")
            Hint(stringResource(R.string.options_multiplayer_hint))
        }
        SaveButtons(state, vm)
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun StatusBanner(state: GameOptionsUiState) {
    val doc = state.doc
    val (text, warn) = when {
        state.errorCode == "NOT_FOUND" && doc == null -> stringResource(R.string.options_bridge_too_old) to true
        state.errorCode != null && doc == null -> stringResource(reasonText(state.errorCode)) to true
        doc == null -> return
        doc.reasonCode == "DEMO_MODE" -> stringResource(R.string.reason_demo_mode) to true
        !doc.available -> stringResource(R.string.options_file_missing) to true
        doc.gameRunning -> stringResource(R.string.options_game_running) to true
        state.dirty -> stringResource(R.string.options_unsaved) to false
        else -> stringResource(R.string.options_game_closed) to false
    }
    Surface(
        color = if (warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("optionsStatus"),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
            color = if (warn) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun Message(message: OptionsMessage, onDismiss: () -> Unit) {
    val text = when (message) {
        is OptionsMessage.Saved -> pluralStringResource(R.plurals.options_saved, message.count, message.count)
        is OptionsMessage.Failed -> when (message.code) {
            "GAME_RUNNING" -> stringResource(R.string.options_game_running)
            "REVISION_CONFLICT" -> stringResource(R.string.options_conflict)
            else -> stringResource(reasonText(message.code))
        }
        OptionsMessage.NothingChanged -> stringResource(R.string.options_nothing_changed)
    }
    val failed = message is OptionsMessage.Failed
    Surface(
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss).testTag("optionsMessage"),
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
}

@Composable
private fun SaveButtons(state: GameOptionsUiState, vm: GameOptionsViewModel) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::save, enabled = state.canSave, modifier = Modifier.heightIn(min = 48.dp).testTag("optionsSave")) {
            Text(stringResource(if (state.saving) R.string.options_saving else R.string.action_save))
        }
        OutlinedButton(onClick = vm::discard, enabled = state.dirty && !state.saving, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_discard)) }
        OutlinedButton(onClick = vm::restoreDefaults, enabled = state.editable, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.options_defaults)) }
    }
}

@Composable
private fun botLabel(type: BotType) = stringResource(
    when (type) {
        BotType.RANDOM -> R.string.options_bot_random
        BotType.COMPANION -> R.string.options_bot_companion
        BotType.CLIMBER -> R.string.options_bot_climber
        BotType.SPRINTER -> R.string.options_bot_sprinter
        BotType.TIME_TRIALER -> R.string.options_bot_time_trialer
    },
)

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))

@Composable
private fun Value(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, step: Int, unit: String, enabled: Boolean, onChange: (Int) -> Unit, tag: String) {
    val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
    LabeledSlider(label, "$value$unit", value.toFloat(), range.first.toFloat()..range.last.toFloat(), steps, enabled, { onChange((it / step).roundToInt() * step) }, tag)
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, enabled: Boolean, onChange: (Float) -> Unit, tag: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { stateDescription = valueText },
        )
    }
}

@Composable
private fun Counter(label: String, count: Int, canRemove: Boolean, canAdd: Boolean, onRemove: () -> Unit, onAdd: () -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val remove = stringResource(R.string.options_bot_remove, label)
        val add = stringResource(R.string.options_bot_add, label)
        OutlinedButton(onClick = onRemove, enabled = canRemove, modifier = Modifier.size(48.dp).semantics { contentDescription = remove }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("−") }
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.size(48.dp).semantics { contentDescription = add }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("+") }
    }
}

@Composable
private fun NumberText(label: String, value: String, onChange: (String) -> Unit, error: Boolean, range: String, enabled: Boolean, integer: Boolean, tag: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, enabled = enabled, singleLine = true, isError = error && enabled,
        label = { Text(label) }, supportingText = { Text(range) },
        keyboardOptions = KeyboardOptions(keyboardType = if (integer) KeyboardType.Number else KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag(tag),
    )
}

@Composable
private fun ColorSwatches(current: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val options = if (current in WAYPOINT_COLORS) WAYPOINT_COLORS else listOf(current) + WAYPOINT_COLORS
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { hex ->
            val selected = hex == current
            val description = stringResource(if (selected) R.string.options_color_selected else R.string.options_color, "#" + hex.substring(2))
            Box(
                Modifier.size(48.dp)
                    .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline), CircleShape)
                    .padding(6.dp)
                    .background(Color(hex.toLong(16).toInt()), CircleShape)
                    .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(hex) }
                    .semantics { contentDescription = description },
            )
        }
    }
}
