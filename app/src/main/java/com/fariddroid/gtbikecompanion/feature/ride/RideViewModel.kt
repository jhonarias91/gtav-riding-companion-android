package com.fariddroid.gtbikecompanion.feature.ride

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.bridge.PairingQr
import com.fariddroid.gtbikecompanion.core.demo.DemoScenario
import com.fariddroid.gtbikecompanion.core.demo.DemoScenarios
import com.fariddroid.gtbikecompanion.core.demo.DemoTelemetryRepository
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.core.rules.ActivationDecision
import com.fariddroid.gtbikecompanion.core.rules.ModActivationRule
import com.fariddroid.gtbikecompanion.data.AppPreferences
import com.fariddroid.gtbikecompanion.data.ConnectionManager
import com.fariddroid.gtbikecompanion.data.ConnectionMode
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.feature.common.OperationTracker
import com.fariddroid.gtbikecompanion.feature.common.OperationUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RideUiState(
    val feed: TelemetryFeed,
    val units: UnitSystem,
    val demoScenario: DemoScenario?,
    val operation: OperationUi? = null,
    /** FTP read back from the PC profile (P4); null when unknown, so no zones are guessed. */
    val pcFtpW: Int? = null,
) {
    val isDemo: Boolean get() = feed.origin == DataOrigin.DEMO
    val modActive: Boolean get() = ModActivationRule.observedActive(feed)

    /** Decision for the only meaningful action: flipping the observed state. */
    val toggleDecision: ActivationDecision get() =
        if (operation == OperationUi.Sending || operation == OperationUi.Waiting) ActivationDecision.Blocked("OPERATION_IN_PROGRESS")
        else ModActivationRule.decide(feed, enabled = !modActive)

    val ftpW: Int? get() = if (isDemo) DemoScenarios.DEMO_FTP_W else pcFtpW
}

/** Pairing renewal from Ride: scan the PC's QR again (or type its code), for the PC this phone already trusts. */
data class RenewPairingUi(
    /** True when the saved pairing reached its 30-day expiry; false when the PC refused it for another reason. */
    val expired: Boolean = false,
    /** The code field is a fallback for phones that cannot scan; hidden until asked for. */
    val showPin: Boolean = false,
    val pin: String = "",
    val working: Boolean = false,
    val errorCode: String? = null,
)

@HiltViewModel
class RideViewModel @Inject constructor(
    telemetry: TelemetryRepository,
    demo: DemoTelemetryRepository,
    preferences: AppPreferences,
    private val control: ControlRepository,
    private val connection: ConnectionManager,
) : ViewModel() {
    private val tracker = OperationTracker(viewModelScope, control)

    /** Profile FTP every 30 s while Ride is visible; settings rarely change. */
    private val ftp = flow {
        while (true) {
            emit((control.settings() as? Outcome.Success)?.value?.ftpW?.value?.toInt())
            delay(30_000)
        }
    }

    private val feed = telemetry.feed

    /** WhileSubscribed: the feed stops when Ride is not visible (no background polling, plan §14). */
    val state: StateFlow<RideUiState?> = combine(feed, preferences.settings, demo.scenario, tracker.state, ftp) { feed, settings, scenario, op, ftp ->
        RideUiState(feed, settings.units, scenario.takeIf { feed.origin == DataOrigin.DEMO }, op, ftp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Banner shown when the PC no longer accepts this phone's token. */
    val needsPairing: StateFlow<Boolean> = feed.map { it.origin == DataOrigin.BRIDGE && it.channel == com.fariddroid.gtbikecompanion.core.model.ChannelState.UNPAIRED }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val renewInput = MutableStateFlow(RenewPairingUi())

    val renew: StateFlow<RenewPairingUi> = combine(connection.mode, renewInput) { mode, input ->
        input.copy(expired = (mode as? ConnectionMode.Paired)?.pc?.isExpired() == true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RenewPairingUi())

    fun showRenewPin() = renewInput.update { it.copy(showPin = true, errorCode = null) }

    fun renewQrScanFailed() = renewInput.update { it.copy(showPin = true, errorCode = "QR_SCAN_UNAVAILABLE") }

    /** Renews from the PC's QR: same bridge as before, new one-time key, nothing typed. */
    fun renewFromQr(text: String) {
        val qr = PairingQr.parse(text) ?: return renewInput.update { it.copy(errorCode = "QR_INVALID") }
        val pc = (connection.mode.value as? ConnectionMode.Paired)?.pc ?: return
        renewInput.update { it.copy(working = true, errorCode = null) }
        viewModelScope.launch {
            when (val r = connection.pairFromQr(qr, Build.MODEL.take(40), expectedBridgeId = pc.bridgeId)) {
                is Outcome.Success -> renewInput.value = RenewPairingUi()
                is Outcome.Failure -> renewInput.update { it.copy(working = false, errorCode = r.code) }
            }
        }
    }

    fun setRenewPin(value: String) = renewInput.update { it.copy(pin = value.filter(Char::isDigit).take(6), errorCode = null) }

    fun renewPairing() {
        val pin = renewInput.value.pin
        if (pin.length != 6) return renewInput.update { it.copy(errorCode = "PAIRING_PIN_INVALID") }
        renewInput.update { it.copy(working = true, errorCode = null) }
        viewModelScope.launch {
            when (val r = connection.renew(pin, Build.MODEL.take(40))) {
                is Outcome.Success -> renewInput.value = RenewPairingUi()
                is Outcome.Failure -> renewInput.update { it.copy(working = false, pin = "", errorCode = r.code) }
            }
        }
    }

    fun toggleMod() {
        val s = state.value ?: return
        if (s.toggleDecision !is ActivationDecision.Allowed) return
        if (s.feed.snapshot.layers.adapter != AdapterLayer.FRESH) return
        tracker.send(Command.SetModActive(!s.modActive), s.feed.snapshot.adapterInstanceId)
    }

    fun dismissOperation() = tracker.dismiss()
}
