package com.fariddroid.gtbikecompanion.core.rules

import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.BindingLayer
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.GameControl
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed

sealed interface ActivationDecision {
    /** The native toggle may be invoked once. */
    data object Allowed : ActivationDecision

    /** Observed state already matches; nothing to send. */
    data object AlreadyInState : ActivationDecision

    data class Blocked(val reasonCode: String) : ActivationDecision
}

/**
 * Client-side mirror of policy.json `modSetActiveGuard`. The bridge enforces the same rule;
 * this only decides whether the control is enabled and which reason/help to show.
 */
object ModActivationRule {
    fun observedActive(feed: TelemetryFeed): Boolean {
        val l = feed.snapshot.layers
        return l.antReceiver.countsAsOk || l.bleReceiver.countsAsOk
    }

    fun decide(feed: TelemetryFeed, enabled: Boolean): ActivationDecision {
        if (feed.origin == DataOrigin.DEMO) return ActivationDecision.Blocked("DEMO_MODE")
        val snap = feed.snapshot
        val capability = feed.capability(CapabilityId.MOD_SET_ACTIVE)
            ?: return ActivationDecision.Blocked("CAPABILITY_UNAVAILABLE")
        if (!capability.available) return ActivationDecision.Blocked(capability.reasonCode ?: "CAPABILITY_UNAVAILABLE")
        when (snap.layers.adapter) {
            AdapterLayer.ABSENT -> return ActivationDecision.Blocked("ADAPTER_ABSENT")
            AdapterLayer.STALE -> return ActivationDecision.Blocked("ADAPTER_STALE")
            AdapterLayer.FRESH -> Unit
        }
        if (snap.layers.binding == BindingLayer.INCOMPATIBLE || snap.layers.binding == BindingLayer.UNKNOWN) {
            return ActivationDecision.Blocked("UNSUPPORTED_VERSION")
        }
        if (snap.layers.gameControl == GameControl.PAUSED || snap.layers.gameControl == GameControl.LOADING) {
            return ActivationDecision.Blocked("GAME_NOT_IN_CONTROL")
        }
        val ant = snap.layers.antReceiver
        val ble = snap.layers.bleReceiver
        val active = observedActive(feed)
        // Transitions are checked before "already in state": IsDTOK counts STOPPING as active.
        return if (enabled) {
            when {
                ant.isTransitional || ble.isTransitional -> ActivationDecision.Blocked("MOD_TRANSITION_IN_PROGRESS")
                active -> ActivationDecision.AlreadyInState
                else -> ActivationDecision.Allowed
            }
        } else {
            when {
                ant == ReceiverState.STOPPING || ble == ReceiverState.STOPPING -> ActivationDecision.Blocked("MOD_TRANSITION_IN_PROGRESS")
                !active -> ActivationDecision.AlreadyInState
                else -> ActivationDecision.Allowed
            }
        }
    }
}
