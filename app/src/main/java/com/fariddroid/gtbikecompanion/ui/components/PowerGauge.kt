package com.fariddroid.gtbikecompanion.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.Metric
import com.fariddroid.gtbikecompanion.core.model.Quality
import com.fariddroid.gtbikecompanion.core.rules.PowerZones
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.ui.format.MetricKind
import com.fariddroid.gtbikecompanion.ui.format.metricText
import com.fariddroid.gtbikecompanion.ui.theme.LocalStatusColors
import com.fariddroid.gtbikecompanion.ui.theme.MetricText
import kotlin.math.cos
import kotlin.math.sin

private const val START_DEG = 150f
private const val SWEEP_DEG = 240f

/**
 * Hero power readout: open arc with the seven zone bands (when FTP is known) and a marker at current power.
 * Without FTP the arc is neutral. Stale values are drawn muted with no marker.
 */
@Composable
fun PowerGauge(power: Metric, ftpW: Int?, units: UnitSystem, modifier: Modifier = Modifier) {
    val status = LocalStatusColors.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val live = power.quality == Quality.VALID
    val zone = if (live) PowerZones.zoneOf(power.value, ftpW) else null
    val scaleMax = (ftpW?.times(1.6) ?: 600.0).coerceAtLeast(1.0)
    val text = metricText(power, MetricKind.POWER, units)
    val zoneLabel = zone?.let { stringResource(R.string.power_zone, it) }
        ?: if (ftpW == null || !live) null else stringResource(R.string.power_zone_unknown)
    val a11y = buildString {
        append(stringResource(R.string.gauge_a11y, "${text.value} ${text.unit}"))
        if (zoneLabel != null) append(", ").append(zoneLabel)
        if (power.quality == Quality.STALE) append(", ").append(stringResource(R.string.metric_stale_label))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .semantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = size.minDimension * 0.085f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2 + stroke * 0.6f)
            val arcSize = Size(diameter, diameter)
            drawArc(track, START_DEG, SWEEP_DEG, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (ftpW != null) {
                for (z in 1..PowerZones.COUNT) {
                    val (lo, hi) = PowerZones.bounds(z)
                    val from = (lo * ftpW / scaleMax).toFloat().coerceIn(0f, 1f)
                    val to = ((hi ?: 1.6) * ftpW / scaleMax).toFloat().coerceIn(0f, 1f)
                    val color = status.zones[z - 1].copy(alpha = if (z == zone) 1f else 0.35f)
                    drawArc(color, START_DEG + from * SWEEP_DEG, (to - from) * SWEEP_DEG, false, topLeft, arcSize,
                        style = Stroke(stroke * 0.7f, cap = StrokeCap.Butt))
                }
            }
            val value = power.value
            if (live && value != null) {
                val fraction = (value / scaleMax).toFloat().coerceIn(0f, 1f)
                val angle = Math.toRadians((START_DEG + fraction * SWEEP_DEG).toDouble())
                val radius = diameter / 2
                val center = Offset(topLeft.x + radius, topLeft.y + radius)
                val inner = radius - stroke * 1.1f
                val outer = radius + stroke * 0.9f
                drawLine(
                    onSurface,
                    Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                    Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                    strokeWidth = stroke * 0.32f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                BasicText(
                    text = text.value,
                    style = MetricText.hero.copy(color = if (live) onSurface else status.muted),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = MetricText.hero.fontSize),
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                Text(text.unit, style = MetricText.unit, color = status.muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            if (power.quality == Quality.STALE) {
                Text(stringResource(R.string.metric_stale_label), style = MaterialTheme.typography.labelLarge, color = status.muted)
            } else if (zoneLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (zone != null) Box(Modifier.size(10.dp).background(status.zones[zone - 1], CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(zoneLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
