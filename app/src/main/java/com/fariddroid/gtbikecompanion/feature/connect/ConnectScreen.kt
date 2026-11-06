package com.fariddroid.gtbikecompanion.feature.connect

import android.os.Build
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.bridge.PairingQr
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.data.BridgeIdentity
import com.fariddroid.gtbikecompanion.data.ConnectionManager
import com.fariddroid.gtbikecompanion.data.ConnectionMode
import com.fariddroid.gtbikecompanion.ui.components.SectionCard
import com.fariddroid.gtbikecompanion.ui.components.StatusLine
import com.fariddroid.gtbikecompanion.ui.components.StatusTone
import com.fariddroid.gtbikecompanion.ui.components.startQrScan
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.help.HelpTopic
import com.fariddroid.gtbikecompanion.ui.help.OptionHelpButton
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val host: String = "",
    val port: String = DEFAULT_PORT,
    val pin: String = "",
    val fingerprint: String = "",
    /** Bridge read with hello; the user must compare its id with the PC console before sending the PIN. */
    val identity: BridgeIdentity? = null,
    val working: Boolean = false,
    val errorCode: String? = null,
    val paired: Boolean = false,
    /** Bridge identity read from the PC's QR; cleared when the user edits the address by hand. */
    val qrBridgeId: String? = null,
) {
    /** Pairing from a scanned QR is in progress. */
    val pairingFromQr: Boolean get() = working && qrBridgeId != null

    companion object { const val DEFAULT_PORT = "8788" }
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connection: ConnectionManager,
    private val saved: SavedStateHandle,
) : ViewModel() {
    // Address and port survive rotation and process death; the PIN deliberately does not.
    private val state = MutableStateFlow(ConnectUiState(host = saved["host"] ?: "", port = saved["port"] ?: ConnectUiState.DEFAULT_PORT))
    val ui: StateFlow<ConnectUiState> = state.asStateFlow()
    val currentPc get() = (connection.mode.value as? ConnectionMode.Paired)?.pc

    fun setHost(v: String) { saved["host"] = v; state.update { it.copy(host = v.take(15), identity = null, errorCode = null, qrBridgeId = null) } }
    fun setPort(v: String) { saved["port"] = v; state.update { it.copy(port = v.filter(Char::isDigit).take(5), identity = null, errorCode = null, qrBridgeId = null) } }
    fun setPin(v: String) = state.update { it.copy(pin = v.filter(Char::isDigit).take(6), errorCode = null) }
    fun setFingerprint(v: String) = state.update { it.copy(fingerprint = v.trim().take(64), identity = null, errorCode = null, qrBridgeId = null) }

    fun qrScanFailed() = state.update { it.copy(errorCode = "QR_SCAN_UNAVAILABLE") }

    /**
     * Pairs straight from the PC's QR: it carries the address, the pinned key, the identity and a one-time pairing
     * key, so the user types nothing. The fields are filled in too, in case the user wants to retry by hand.
     */
    fun applyQr(text: String) {
        val qr = PairingQr.parse(text) ?: return state.update { it.copy(errorCode = "QR_INVALID", identity = null) }
        val first = qr.endpoints().first()
        saved["host"] = first.host
        saved["port"] = first.port.toString()
        state.update {
            it.copy(host = first.host, port = first.port.toString(), fingerprint = qr.keyFingerprint.orEmpty(), pin = "",
                identity = null, errorCode = null, qrBridgeId = qr.bridgeId, working = true)
        }
        viewModelScope.launch {
            when (val r = connection.pairFromQr(qr, Build.MODEL.take(40))) {
                is Outcome.Success -> state.update { it.copy(working = false, paired = true) }
                is Outcome.Failure -> state.update { it.copy(working = false, errorCode = r.code) }
            }
        }
    }

    fun probe() {
        val s = state.value
        val port = s.port.toIntOrNull() ?: return state.update { it.copy(errorCode = "ADDRESS_INVALID") }
        state.update { it.copy(working = true, errorCode = null, identity = null) }
        viewModelScope.launch {
            when (val r = connection.probe(s.host, port, s.fingerprint)) {
                is Outcome.Success -> state.update { it.copy(working = false, identity = r.value) }
                is Outcome.Failure -> state.update { it.copy(working = false, errorCode = r.code) }
            }
        }
    }

    fun pair() {
        val s = state.value
        val identity = s.identity ?: return
        if (s.pin.length != 6) return state.update { it.copy(errorCode = "PAIRING_PIN_INVALID") }
        state.update { it.copy(working = true, errorCode = null) }
        viewModelScope.launch {
            when (val r = connection.pair(identity, s.pin, Build.MODEL.take(40))) {
                is Outcome.Success -> state.update { it.copy(working = false, pin = "", paired = true) }
                is Outcome.Failure -> state.update { it.copy(working = false, pin = "", errorCode = r.code, identity = if (r.code == "BRIDGE_CHANGED") null else it.identity) }
            }
        }
    }

    fun useDemo() = connection.useDemo()
    fun forget() = viewModelScope.launch { connection.forget() }
}

/**
 * Pairing in two explicit steps (plan §15): 1) read the bridge identity, 2) after the user compares it with
 * the PC console, send the PIN. Increment 2a uses development HTTP, available only in debug builds.
 */
@Composable
fun ConnectScreen(onPaired: () -> Unit, onUseDemo: () -> Unit, modifier: Modifier = Modifier, viewModel: ConnectViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(state.paired) { if (state.paired) onPaired() }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .testTag("connectScreen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.connect_intro), style = MaterialTheme.typography.bodyMedium)

        viewModel.currentPc?.let { pc ->
            SectionCard(stringResource(R.string.connect_current_pc), help = HelpTopic.APP_UNPAIR) {
                StatusLine(stringResource(R.string.connect_identity), pc.bridgeId, StatusTone.GOOD)
                StatusLine(stringResource(R.string.connect_address), "${pc.endpoint.host}:${pc.endpoint.port}", StatusTone.NEUTRAL)
                pc.expiresAt?.let { expiresAt ->
                    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(expiresAt)
                    if (pc.isExpired()) StatusLine(stringResource(R.string.connect_pairing_expired), date, StatusTone.WARN)
                    else StatusLine(stringResource(R.string.connect_paired_until), date, StatusTone.GOOD)
                }
                OutlinedButton(onClick = { viewModel.forget() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("forgetPc")) {
                    Text(stringResource(R.string.connect_forget))
                }
            }
        }

        val context = LocalContext.current
        Button(
            onClick = { startQrScan(context, viewModel::applyQr, viewModel::qrScanFailed) },
            enabled = !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("scanQr"),
        ) { Text(stringResource(R.string.connect_scan_qr)) }
        if (state.pairingFromQr) {
            Text(stringResource(R.string.connect_pairing_from_qr), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("pairingFromQr"))
        } else {
            Text(stringResource(R.string.connect_scan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Field(stringResource(R.string.connect_address), state.host, viewModel::setHost, HelpTopic.CONNECTION_ADDRESS, KeyboardType.Uri, "hostField")
        Field(stringResource(R.string.connect_port), state.port, viewModel::setPort, HelpTopic.CONNECTION_ADDRESS, KeyboardType.Number, "portField")
        Field(stringResource(R.string.connect_tls_fingerprint), state.fingerprint, viewModel::setFingerprint, HelpTopic.CONNECTION_IDENTITY, KeyboardType.Ascii, "tlsFingerprint")
        OutlinedButton(
            onClick = viewModel::probe,
            enabled = !state.working && state.host.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("probe"),
        ) { Text(stringResource(R.string.connect_find_pc)) }

        state.identity?.let { identity ->
            SectionCard(stringResource(R.string.connect_verify_title), help = HelpTopic.CONNECTION_IDENTITY) {
                Text(stringResource(R.string.connect_verify_body), style = MaterialTheme.typography.bodyMedium)
                Text(identity.bridgeId, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp).testTag("bridgeId"))
                if (identity.endpoint.keyFingerprint == null) {
                    Text(stringResource(R.string.connect_http_dev_warning), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Field(stringResource(R.string.connect_pin), state.pin, viewModel::setPin, HelpTopic.CONNECTION_PAIRING, KeyboardType.NumberPassword, "pinField")
            Button(
                onClick = viewModel::pair,
                enabled = !state.working && state.pin.length == 6,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("pair"),
            ) { Text(stringResource(R.string.connect_button)) }
        }

        state.errorCode?.let {
            Text(stringResource(reasonText(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("connectError"))
        }

        OutlinedButton(
            onClick = { viewModel.useDemo(); onUseDemo() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("useDemo"),
        ) { Text(stringResource(R.string.connect_use_demo)) }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, help: HelpTopic, keyboard: KeyboardType, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.weight(1f).testTag(tag),
        )
        OptionHelpButton(help, label)
    }
}
