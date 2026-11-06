package com.fariddroid.gtbikecompanion.core.rules

import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.NumberSetting
import com.fariddroid.gtbikecompanion.core.model.RouteSummary
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed

/** Client-side mirror of bridge guards: decides which controls are enabled and which reason to show. */
object ControlRules {
    /**
     * null when the capability may be used now; otherwise the reason code to explain. No feed yet means nothing is
     * known about the PC. Screens must call this directly: `feed?.let { blockReason(it, id) } ?: "ADAPTER_ABSENT"`
     * turns an allowed action (null) into "adapter not running" and blocked every P3/P4 control (6 Nov 2026).
     */
    fun blockReason(feed: TelemetryFeed?, id: CapabilityId): String? {
        if (feed == null) return "ADAPTER_ABSENT"
        if (feed.origin == DataOrigin.DEMO) return "DEMO_MODE"
        when (feed.snapshot.layers.adapter) {
            AdapterLayer.ABSENT -> return "ADAPTER_ABSENT"
            AdapterLayer.STALE -> return "ADAPTER_STALE"
            AdapterLayer.FRESH -> Unit
        }
        val capability = feed.capability(id) ?: return "CAPABILITY_UNAVAILABLE"
        return if (capability.available) null else capability.reasonCode ?: "CAPABILITY_UNAVAILABLE"
    }

    /** Flags actually sent for a route: Loop/Reverse only where the catalog allows them. */
    fun routeFlags(route: RouteSummary?, loop: Boolean, reverse: Boolean): Pair<Boolean, Boolean> =
        if (route == null) false to false else (loop && route.loopAllowed) to (reverse && route.reverseAllowed)

    /**
     * Parses a whole-number profile entry. Out-of-range values are rejected here because the native setters
     * would silently store a default instead (Config.cs:90-140).
     */
    fun parseProfileValue(text: String, setting: NumberSetting): ProfileInput {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ProfileInput.Empty
        val value = trimmed.toIntOrNull() ?: return ProfileInput.Invalid
        return if (value < setting.min || value > setting.max) ProfileInput.OutOfRange else ProfileInput.Valid(value)
    }

    /** Converts a displayed weight to kg for the contract (whole kg, as the mod stores it). */
    fun weightToKg(value: Int, imperial: Boolean): Int = if (imperial) Math.round(value / 2.20462).toInt() else value
    fun kgToDisplay(kg: Double, imperial: Boolean): Int = if (imperial) Math.round(kg * 2.20462).toInt() else Math.round(kg).toInt()
}

sealed interface ProfileInput {
    data object Empty : ProfileInput
    data object Invalid : ProfileInput
    data object OutOfRange : ProfileInput
    data class Valid(val value: Int) : ProfileInput
}
