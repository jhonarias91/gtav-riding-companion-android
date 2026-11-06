package com.fariddroid.gtbikecompanion.ui.format

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.core.model.Metric
import com.fariddroid.gtbikecompanion.data.UnitSystem
import java.text.NumberFormat
import java.util.Locale

/** Display conversion only; the domain stays in canonical units (m, m/s, ms, W, fraction). */
enum class MetricKind { POWER, CADENCE, HEART_RATE, SPEED, DISTANCE, DURATION, ELEVATION, GRADE }

data class MetricText(val value: String, val unit: String)

object MetricFormat {
    private const val MPS_TO_KMH = 3.6
    private const val MPS_TO_MPH = 2.236936
    private const val M_PER_MI = 1609.344
    private const val FT_PER_M = 3.28084

    fun number(value: Double, decimals: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            isGroupingUsed = true
        }.format(value)

    fun duration(ms: Double): String {
        val total = (ms / 1000).toLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** Converted numeric value and unit resource, or null unit for durations. */
    fun convert(kind: MetricKind, value: Double, units: UnitSystem): Pair<Double, Int?> = when (kind) {
        MetricKind.POWER -> value to R.string.unit_watts
        MetricKind.CADENCE -> value to R.string.unit_rpm
        MetricKind.HEART_RATE -> value to R.string.unit_bpm
        MetricKind.SPEED -> if (units == UnitSystem.METRIC) value * MPS_TO_KMH to R.string.unit_kmh else value * MPS_TO_MPH to R.string.unit_mph
        MetricKind.DISTANCE -> if (units == UnitSystem.METRIC) value / 1000 to R.string.unit_km else value / M_PER_MI to R.string.unit_mi
        MetricKind.DURATION -> value to null
        MetricKind.ELEVATION -> if (units == UnitSystem.METRIC) value to R.string.unit_m else value * FT_PER_M to R.string.unit_ft
        MetricKind.GRADE -> value * 100 to R.string.unit_percent
    }

    fun decimals(kind: MetricKind): Int = when (kind) {
        MetricKind.SPEED, MetricKind.GRADE -> 1
        MetricKind.DISTANCE -> 2
        else -> 0
    }
}

/** Formats a metric for the current locale. Unavailable values show "--", never a fake zero. */
@Composable
fun metricText(metric: Metric, kind: MetricKind, units: UnitSystem): MetricText {
    val locale = LocalConfiguration.current.locales[0]
    val value = metric.value
    val (converted, unitRes) = MetricFormat.convert(kind, value ?: 0.0, units)
    val unit = unitRes?.let { stringResource(it) } ?: ""
    if (value == null) return MetricText(stringResource(R.string.metric_missing), unit)
    val text = if (kind == MetricKind.DURATION) MetricFormat.duration(converted) else MetricFormat.number(converted, MetricFormat.decimals(kind), locale)
    return MetricText(text, unit)
}

@StringRes
fun reasonText(code: String?): Int = when (code) {
    "DEMO_MODE" -> R.string.reason_demo_mode
    "ADAPTER_ABSENT" -> R.string.reason_adapter_absent
    "ADAPTER_STALE" -> R.string.reason_adapter_stale
    "UNSUPPORTED_VERSION" -> R.string.reason_unsupported_version
    "MOD_TRANSITION_IN_PROGRESS" -> R.string.reason_mod_transition
    "GAME_NOT_IN_CONTROL" -> R.string.reason_game_not_in_control
    "CAPABILITY_UNAVAILABLE" -> R.string.reason_capability_unavailable
    "SENSOR_MISSING" -> R.string.reason_sensor_missing
    "NOT_CAPTURED" -> R.string.reason_not_captured
    "MOD_INACTIVE" -> R.string.reason_mod_inactive
    "MOD_NOT_LOADED" -> R.string.reason_mod_not_loaded
    "WORKOUT_PC_CONTROL_REQUIRED" -> R.string.reason_workout_pc_control
    "PANEL_CLOSED" -> R.string.reason_panel_closed
    "RIDER_MOVING" -> R.string.reason_rider_moving
    "AUTOPILOT_ON" -> R.string.reason_autopilot_on
    "REVISION_CONFLICT" -> R.string.reason_revision_conflict
    "INSTANCE_CHANGED" -> R.string.reason_instance_changed
    "VALUE_OUT_OF_RANGE" -> R.string.reason_value_out_of_range
    "VALUE_NOT_ALLOWED" -> R.string.reason_value_not_allowed
    "BRIDGE_BUSY" -> R.string.reason_bridge_busy
    "OPERATION_IN_PROGRESS" -> R.string.reason_operation_in_progress
    "OPERATION_UNKNOWN" -> R.string.reason_operation_unknown
    "PC_UNREACHABLE" -> R.string.reason_pc_unreachable
    "AUTH_REQUIRED", "FORBIDDEN_SCOPE" -> R.string.reason_auth_required
    "PAIRING_PIN_INVALID" -> R.string.reason_pin_invalid
    "PAIRING_PIN_EXPIRED" -> R.string.reason_pin_expired
    "RATE_LIMITED" -> R.string.reason_rate_limited
    "ADDRESS_INVALID" -> R.string.reason_address_invalid
    "BRIDGE_CHANGED" -> R.string.reason_bridge_changed
    "QR_INVALID" -> R.string.reason_qr_invalid
    "QR_USED" -> R.string.reason_qr_used
    "QR_SCAN_UNAVAILABLE" -> R.string.reason_qr_scan_unavailable
    "TLS_REQUIRED" -> R.string.reason_tls_required
    "TLS_FINGERPRINT_INVALID" -> R.string.reason_tls_fingerprint
    "TLS_VERIFICATION_FAILED" -> R.string.reason_tls_verification
    "TLS_NOT_SUPPORTED_YET" -> R.string.reason_tls_not_supported
    "INVALID_RESPONSE" -> R.string.reason_invalid_response
    "NOT_FOUND" -> R.string.reason_not_found
    "PHASE_NOT_AVAILABLE" -> R.string.reason_phase_not_available
    "DEVICE_CHANGED" -> R.string.reason_device_changed
    "SAVE_TOO_SHORT" -> R.string.reason_save_too_short
    "FIT_INVALID" -> R.string.reason_fit_invalid
    "ASSET_UNAVAILABLE" -> R.string.reason_asset_unavailable
    "GAME_RUNNING" -> R.string.reason_game_running
    else -> R.string.reason_unknown
}
