package com.fariddroid.gtbikecompanion.feature.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.core.model.BotType
import com.fariddroid.gtbikecompanion.core.model.GameOptionRules
import com.fariddroid.gtbikecompanion.core.model.GameOptions
import com.fariddroid.gtbikecompanion.core.model.GameOptionsDoc
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.TrafficStyle
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Text fields typed by hand; numbers are taken from them only when they parse and are in range. */
data class OptionTexts(val xOffset: String = "", val lat: String = "", val lon: String = "")

sealed interface OptionsMessage {
    data class Saved(val count: Int) : OptionsMessage
    data class Failed(val code: String) : OptionsMessage
    data object NothingChanged : OptionsMessage
}

data class GameOptionsUiState(
    val doc: GameOptionsDoc? = null,
    val loading: Boolean = true,
    val errorCode: String? = null,
    /** What the form shows: the file's values (or the mod's defaults for missing lines) plus the user's edits. */
    val draft: GameOptions? = null,
    val texts: OptionTexts = OptionTexts(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val message: OptionsMessage? = null,
) {
    val editable get() = doc?.editable == true && !saving
    val xOffsetError get() = texts.xOffset.trim().toIntOrNull()?.let { it !in GameOptionRules.SCREENSHOT_OFFSET } ?: true
    val latError get() = texts.lat.trim().toDoubleOrNull()?.let { it !in GameOptionRules.LATITUDE } ?: true
    val lonError get() = texts.lon.trim().toDoubleOrNull()?.let { it !in GameOptionRules.LONGITUDE } ?: true
    val canSave get() = editable && dirty && !xOffsetError && !latError && !lonError && draft != null && GameOptionRules.invalid(draft).isEmpty()
}

@HiltViewModel
class GameOptionsViewModel @Inject constructor(private val control: ControlRepository) : ViewModel() {
    private val data = MutableStateFlow(GameOptionsUiState())

    /** Every 3 s while visible: GTA V starting or closing changes whether the options can be edited. Edits are kept. */
    private val poll = flow {
        while (true) {
            delay(POLL_MS)
            fetch()
            emit(Unit)
        }
    }.onStart { emit(Unit) }

    val state: StateFlow<GameOptionsUiState> = combine(data, poll) { d, _ -> d }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), data.value)

    init { reload() }

    fun reload() {
        data.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        when (val r = control.gameOptions()) {
            is Outcome.Success -> data.update { s ->
                if (s.dirty) s.copy(doc = r.value, loading = false, errorCode = null) else fromDoc(s, r.value)
            }
            is Outcome.Failure -> data.update { it.copy(loading = false, errorCode = r.code) }
        }
    }

    private fun fromDoc(s: GameOptionsUiState, doc: GameOptionsDoc): GameOptionsUiState {
        val values = (doc.options ?: GameOptions()).orDefaults(doc.defaults)
        return s.copy(doc = doc, loading = false, errorCode = null, draft = values, texts = textsOf(values), dirty = false)
    }

    private fun textsOf(o: GameOptions) = OptionTexts(
        xOffset = o.screenshotXOffset?.toString() ?: "",
        lat = o.gpsOriginLat?.toString() ?: "",
        lon = o.gpsOriginLon?.toString() ?: "",
    )

    private fun edit(transform: (GameOptions) -> GameOptions) {
        data.update { s -> s.draft?.let { s.copy(draft = transform(it), dirty = true, message = null) } ?: s }
    }

    fun setTrafficPercent(v: Int) = edit { it.copy(trafficBikesPercent = v.coerceIn(GameOptionRules.TRAFFIC_PERCENT)) }
    fun setTrafficStyle(v: TrafficStyle) = edit { it.copy(trafficStyle = v) }
    fun setCrowdsEnabled(v: Boolean) = edit { it.copy(crowdsEnabled = v) }
    fun setCrowdSlope(v: Int) = edit { it.copy(crowdsSlopePercent = v.coerceIn(GameOptionRules.CROWD_SLOPE)) }
    fun setCrowdAmount(v: Int) = edit { it.copy(crowdsAmount = v.coerceIn(GameOptionRules.CROWD_AMOUNT)) }
    fun setDefensive(v: Int) = edit { it.copy(defensiveLevel = v) }
    fun setFineSteering(v: Double) = edit { it.copy(fineSteering = Math.round(v * 20) / 20.0) }
    fun setWaypointColor(v: String) = edit { it.copy(waypointColor = v) }
    fun setWaypointRadius(v: Int) = edit { it.copy(waypointRadius = v.coerceIn(GameOptionRules.WAYPOINT_RADIUS)) }
    fun setScreenshotKm(v: Int) = edit { it.copy(screenshotIntervalKm = v.coerceIn(GameOptionRules.SCREENSHOT_KM)) }
    fun setMenuAutoOpen(v: Boolean) = edit { it.copy(menuAutoOpen = v) }
    fun setShowClock(v: Boolean) = edit { it.copy(showClock = v) }
    fun setDebugWindow(v: Boolean) = edit { it.copy(debugWindow = v) }
    fun setLogLevel(v: Int) = edit { it.copy(logLevel = v.coerceIn(GameOptionRules.LOG_LEVEL)) }
    fun setTelemetry(v: Boolean) = edit { it.copy(telemetry = v) }
    fun setMultiplayer(v: Boolean) = edit { it.copy(multiplayer = v) }

    /** Bot counts per type; the order written is the order of [BotType], up to five bots in total. */
    fun changeBots(type: BotType, delta: Int) = edit { o ->
        val bots = o.bots.orEmpty()
        val next = when {
            delta > 0 && bots.size < GameOptionRules.MAX_BOTS -> bots + type
            delta < 0 && type in bots -> bots.toMutableList().apply { removeAt(lastIndexOf(type)) }
            else -> bots
        }
        o.copy(bots = BotType.entries.flatMap { t -> List(next.count { it == t }) { t } })
    }

    fun setXOffsetText(v: String) {
        val clean = v.filterIndexed { i, c -> c.isDigit() || (c == '-' && i == 0) }.take(6)
        data.update { s -> s.copy(texts = s.texts.copy(xOffset = clean), dirty = true, message = null, draft = s.draft?.let { d -> d.copy(screenshotXOffset = clean.toIntOrNull() ?: d.screenshotXOffset) }) }
    }

    fun setLatText(v: String) = coordinate(v) { s, text, n -> s.copy(texts = s.texts.copy(lat = text), draft = s.draft?.let { d -> d.copy(gpsOriginLat = n ?: d.gpsOriginLat) }) }
    fun setLonText(v: String) = coordinate(v) { s, text, n -> s.copy(texts = s.texts.copy(lon = text), draft = s.draft?.let { d -> d.copy(gpsOriginLon = n ?: d.gpsOriginLon) }) }

    private fun coordinate(v: String, apply: (GameOptionsUiState, String, Double?) -> GameOptionsUiState) {
        val clean = v.replace(',', '.').filterIndexed { i, c -> c.isDigit() || c == '.' || (c == '-' && i == 0) }.take(12)
        data.update { s -> apply(s, clean, clean.toDoubleOrNull()).copy(dirty = true, message = null) }
    }

    fun discard() {
        data.update { s -> s.doc?.let { fromDoc(s, it) } ?: s.copy(dirty = false) }
    }

    fun restoreDefaults() {
        data.update { s ->
            val d = s.doc?.defaults ?: return@update s
            s.copy(draft = d, texts = textsOf(d), dirty = true, message = null)
        }
    }

    fun save() {
        val s = data.value
        val doc = s.doc ?: return
        val revision = doc.revision ?: return
        val draft = s.draft ?: return
        if (!s.canSave) return
        val changes = GameOptionRules.changes(doc.options, draft)
        if (changes.isEmpty()) {
            data.update { it.copy(dirty = false, message = OptionsMessage.NothingChanged) }
            return
        }
        data.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            when (val r = control.saveGameOptions(revision, changes)) {
                is Outcome.Success -> data.update { fromDoc(it.copy(saving = false), r.value).copy(message = OptionsMessage.Saved(changes.size)) }
                is Outcome.Failure -> data.update { it.copy(saving = false, message = OptionsMessage.Failed(r.code)) }
            }
        }
    }

    fun dismissMessage() = data.update { it.copy(message = null) }

    private companion object {
        const val POLL_MS = 3_000L
    }
}
