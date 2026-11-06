package com.fariddroid.gtbikecompanion.feature.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.NumberSetting
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.PcSettings
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.core.rules.ControlRules
import com.fariddroid.gtbikecompanion.core.rules.ProfileInput
import com.fariddroid.gtbikecompanion.data.AppPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Local edits of the three profile numbers; applied only with Save (plan §8: explicit save). */
data class ProfileDraft(val weight: String = "", val ftp: String = "", val hrMax: String = "", val dirty: Boolean = false)

enum class FieldError { INVALID, OUT_OF_RANGE }

data class SettingsData(val settings: PcSettings? = null, val loading: Boolean = true, val errorCode: String? = null, val draft: ProfileDraft = ProfileDraft())

data class PcSettingsUiState(val data: SettingsData, val feed: TelemetryFeed?, val units: UnitSystem, val operation: OperationUi?) {
    val imperial get() = units == UnitSystem.IMPERIAL
    private val busy get() = operation == OperationUi.Sending || operation == OperationUi.Waiting
    private fun block(id: CapabilityId) = if (busy) "OPERATION_IN_PROGRESS" else ControlRules.blockReason(feed, id)
    val profileBlock get() = block(CapabilityId.PROFILE_UPDATE)
    val rideBlock get() = block(CapabilityId.RIDE_SETTINGS_UPDATE)
    val appearanceBlock get() = block(CapabilityId.APPEARANCE_SELECT)

    /** Weight limits shown in the user's unit; the contract stays in whole kg. */
    fun weightRange(s: NumberSetting): IntRange =
        ControlRules.kgToDisplay(s.min, imperial)..ControlRules.kgToDisplay(s.max, imperial)

    fun weightError(): FieldError? {
        val s = data.settings?.weightKg ?: return null
        val text = data.draft.weight.trim()
        if (text.isEmpty()) return null
        val v = text.toIntOrNull() ?: return FieldError.INVALID
        return if (ControlRules.parseProfileValue(ControlRules.weightToKg(v, imperial).toString(), s) is ProfileInput.Valid) null else FieldError.OUT_OF_RANGE
    }

    fun numberError(text: String, s: NumberSetting?): FieldError? = when (s?.let { ControlRules.parseProfileValue(text, it) }) {
        ProfileInput.Invalid -> FieldError.INVALID
        ProfileInput.OutOfRange -> FieldError.OUT_OF_RANGE
        else -> null
    }
}

@HiltViewModel
class PcSettingsViewModel @Inject constructor(
    telemetry: TelemetryRepository,
    private val preferences: AppPreferences,
    private val control: ControlRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val tracker = OperationTracker(viewModelScope, control)
    private val data = MutableStateFlow(SettingsData(draft = saved.get<ProfileDraftState>(DRAFT)?.toDraft() ?: ProfileDraft()))

    /**
     * Every 2 s while visible: the PC changes these values and their locks on its own (applying a route turns the
     * autopilot on, a PC workout locks FTP, turning the mod off locks almost everything, the mod menu edits them).
     * Read once on open, the screen showed old values and sent old revisions. A draft being edited is kept.
     */
    private val poll = flow {
        while (true) {
            delay(SETTINGS_POLL_MS)
            fetch()
            emit(Unit)
        }
    }.onStart { emit(Unit) }

    val state: StateFlow<PcSettingsUiState> = combine(data, telemetry.feed, preferences.settings, tracker.state, poll) { d, feed, settings, op, _ ->
        PcSettingsUiState(d, feed, settings.units, op)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PcSettingsUiState(data.value, null, UnitSystem.METRIC, null))

    init { reload() }

    fun reload() {
        data.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        // Units read here, not from the combined state, so a draft is never built in the wrong unit.
        val imperial = preferences.settings.first().units == UnitSystem.IMPERIAL
        when (val r = control.settings()) {
            is Outcome.Success -> data.update { d ->
                val draft = if (d.draft.dirty) d.draft else draftFrom(r.value, imperial)
                d.copy(settings = r.value, loading = false, errorCode = null, draft = draft)
            }
            is Outcome.Failure -> data.update { it.copy(loading = false, errorCode = r.code) }
        }
    }

    private fun draftFrom(s: PcSettings, imperial: Boolean) = ProfileDraft(
        weight = s.weightKg.value?.let { ControlRules.kgToDisplay(it, imperial).toString() } ?: "",
        ftp = s.ftpW.value?.let { Math.round(it).toString() } ?: "",
        hrMax = s.hrMaxBpm.value?.let { Math.round(it).toString() } ?: "",
    )

    private fun edit(transform: (ProfileDraft) -> ProfileDraft) {
        data.update { it.copy(draft = transform(it.draft).copy(dirty = true)) }
        saved[DRAFT] = ProfileDraftState.from(data.value.draft)
    }

    fun setWeight(v: String) = edit { it.copy(weight = v.filter(Char::isDigit).take(3)) }
    fun setFtp(v: String) = edit { it.copy(ftp = v.filter(Char::isDigit).take(3)) }
    fun setHrMax(v: String) = edit { it.copy(hrMax = v.filter(Char::isDigit).take(3)) }

    fun discardDraft() {
        saved.remove<ProfileDraftState>(DRAFT)
        data.update { it.copy(draft = it.draft.copy(dirty = false)) }
        reload()
    }

    /** Sends only changed, editable, valid fields. Never clamps: invalid input keeps Save disabled. */
    fun profileChanges(): Command.UpdateProfile? {
        val st = state.value
        val s = st.data.settings ?: return null
        val revision = s.revision ?: return null
        val d = st.data.draft
        if (st.weightError() != null || st.numberError(d.ftp, s.ftpW) != null || st.numberError(d.hrMax, s.hrMaxBpm) != null) return null
        val weight = d.weight.toIntOrNull()?.let { ControlRules.weightToKg(it, st.imperial) }
            ?.takeIf { s.weightKg.editable && it != s.weightKg.value?.let(Math::round)?.toInt() }
        val ftp = d.ftp.toIntOrNull()?.takeIf { s.ftpW.editable && it != s.ftpW.value?.let(Math::round)?.toInt() }
        val hr = d.hrMax.toIntOrNull()?.takeIf { s.hrMaxBpm.editable && it != s.hrMaxBpm.value?.let(Math::round)?.toInt() }
        if (weight == null && ftp == null && hr == null) return null
        return Command.UpdateProfile(weight, ftp, hr, revision)
    }

    fun saveProfile() {
        val command = profileChanges() ?: return
        send(command) { applied -> if (applied) { saved.remove<ProfileDraftState>(DRAFT); data.update { it.copy(draft = it.draft.copy(dirty = false)) } } }
    }

    private fun revision() = data.value.settings?.revision
    fun setAutopilot(v: Boolean) = revision()?.let { send(Command.UpdateRide(autopilot = v, settingsRevision = it)) }
    fun setObstacles(v: Boolean) = revision()?.let { send(Command.UpdateRide(avoidObstacles = v, settingsRevision = it)) }
    fun setDifficulty(level: Int) = revision()?.let { send(Command.UpdateRide(difficulty = level, settingsRevision = it)) }
    fun setRoadFeel(v: Boolean) = revision()?.let { send(Command.UpdateRide(roadFeel = v, settingsRevision = it)) }
    fun selectBike(id: String) = revision()?.let { send(Command.SelectBike(id, it)) }
    fun selectCharacter(id: String) = revision()?.let { send(Command.SelectCharacter(id, it)) }

    private fun send(command: Command, after: (Boolean) -> Unit = {}) {
        tracker.send(command, state.value.feed?.snapshot?.adapterInstanceId) { result ->
            after(result == OperationUi.Applied)
            reload() // Always show what the PC reports now, applied or not.
        }
    }

    fun dismissOperation() = tracker.dismiss()

    private companion object {
        const val DRAFT = "profileDraft"
        const val SETTINGS_POLL_MS = 2_000L
    }
}

/** Parcel-free representation of the draft for SavedStateHandle (process death). */
data class ProfileDraftState(val weight: String, val ftp: String, val hrMax: String) : java.io.Serializable {
    fun toDraft() = ProfileDraft(weight, ftp, hrMax, dirty = true)

    companion object {
        fun from(d: ProfileDraft) = ProfileDraftState(d.weight, d.ftp, d.hrMax)
    }
}
