package com.fariddroid.gtbikecompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.Metric
import com.fariddroid.gtbikecompanion.core.model.Quality
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.ui.format.MetricKind
import com.fariddroid.gtbikecompanion.ui.format.metricText
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import com.fariddroid.gtbikecompanion.ui.theme.LocalStatusColors
import com.fariddroid.gtbikecompanion.ui.theme.MetricText

/** Fixed, always-visible marker that the data is synthetic (P1 acceptance: demo never looks like a real PC). */
@Composable
fun DemoBanner(scenarioLabel: String?, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().testTag("demoBanner"),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(stringResource(R.string.demo_banner), style = MaterialTheme.typography.labelLarge)
            if (scenarioLabel != null) {
                Text(stringResource(R.string.demo_banner_scenario, scenarioLabel), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    help: HelpTopic? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (help != null) OptionHelpButton(help, title) else Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.padding(end = 12.dp)) { content() }
        }
    }
}

/**
 * One metric. Valid -> value; stale -> last value muted with a label; unavailable -> "--" plus reason.
 * Accessibility reads one merged description, never per-sample announcements.
 */
@Composable
fun MetricTile(
    label: String,
    metric: Metric,
    kind: MetricKind,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    help: HelpTopic? = null,
    large: Boolean = false,
) {
    val text = metricText(metric, kind, units)
    val muted = LocalStatusColors.current.muted
    val reason = metric.reasonCode?.let { stringResource(reasonText(it)) }
    val description = when (metric.quality) {
        Quality.VALID -> "$label, ${text.value} ${text.unit}"
        Quality.STALE -> stringResource(R.string.metric_stale_a11y, label, text.value, text.unit)
        Quality.UNAVAILABLE -> stringResource(R.string.metric_unavailable_a11y, label)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
                if (help != null) OptionHelpButton(help, label) else Spacer(Modifier.size(48.dp))
            }
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 12.dp)) {
                    val style = if (large) MetricText.secondary else MetricText.tile
                    // Shrinks instead of clipping or splitting digits ("134" never becomes "13 / 4").
                    BasicText(
                        text = text.value,
                        style = style.copy(color = if (metric.quality == Quality.VALID) MaterialTheme.colorScheme.onSurface else muted),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (text.unit.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(text.unit, style = MetricText.unit, color = muted, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                when (metric.quality) {
                    Quality.STALE -> Text(stringResource(R.string.metric_stale_label), style = MaterialTheme.typography.labelMedium, color = muted)
                    Quality.UNAVAILABLE -> if (reason != null) Text(reason, style = MaterialTheme.typography.labelMedium, color = muted)
                    Quality.VALID -> Unit
                }
            }
        }
    }
}

/** Two-column grid that drops to one column when there is not enough width (large fonts, narrow phones). */
@Composable
fun AdaptiveGrid(items: List<@Composable (Modifier) -> Unit>, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item -> item(Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

enum class StatusTone { GOOD, WARN, BAD, NEUTRAL }

/** Status row: colored dot plus text, never color alone. */
@Composable
fun StatusLine(label: String, value: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val color: Color = when (tone) {
        StatusTone.GOOD -> status.success
        StatusTone.WARN -> status.warning
        StatusTone.BAD -> status.error
        StatusTone.NEUTRAL -> status.muted
    }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, overflow = TextOverflow.Ellipsis)
    }
}

/** Option row: label/value → help → control (focus order from AYUDA_CONTEXTUAL.md). */
@Composable
fun SettingRow(
    label: String,
    help: HelpTopic,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    value: String? = null,
    reason: String? = null,
    control: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else LocalStatusColors.current.muted,
            )
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reason != null) Text(reason, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        OptionHelpButton(help, label, currentReason = reason)
        if (control != null) control()
    }
}

/** Accessible radio group that survives 200% font (no segmented buttons that truncate). */
@Composable
fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(selected = value == selected, onClick = { onSelect(value) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
