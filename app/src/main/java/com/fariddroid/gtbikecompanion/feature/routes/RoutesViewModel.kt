package com.fariddroid.gtbikecompanion.feature.routes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.RouteDetail
import com.fariddroid.gtbikecompanion.core.model.RoutePreview
import com.fariddroid.gtbikecompanion.core.model.RouteSummary
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.core.rules.ControlRules
import com.fariddroid.gtbikecompanion.data.AppPreferences
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.feature.common.OperationTracker
import com.fariddroid.gtbikecompanion.feature.common.OperationUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which confirmation dialog is open. Apply always explains its effects first (plan §9). */
enum class RouteConfirm { APPLY, FREE_RIDE }

data class RoutesData(
    val query: String = "",
    val items: List<RouteSummary> = emptyList(),
    val total: Int = 0,
    val catalogRevision: String? = null,
    val loading: Boolean = false,
    val errorCode: String? = null,
    val selected: RouteDetail? = null,
    val loop: Boolean = true,
    val reverse: Boolean = false,
    val confirm: RouteConfirm? = null,
    /** Name of the workout running on the PC, read when a confirmation opens (null until known). */
    val runningWorkoutName: String? = null,
)

data class RoutesUiState(
    val data: RoutesData,
    val feed: TelemetryFeed?,
    val units: UnitSystem,
    val operation: OperationUi?,
    /** What the PC selector shows now; refreshed every second while a route detail is open. */
    val preview: RoutePreview? = null,
) {
    private fun block(id: CapabilityId): String? = ControlRules.blockReason(feed, id)
    private val busy get() = operation == OperationUi.Sending || operation == OperationUi.Waiting
    val applyBlock: String? get() = if (busy) "OPERATION_IN_PROGRESS" else block(CapabilityId.ROUTES_APPLY)
    val openPanelBlock: String? get() = if (busy) "OPERATION_IN_PROGRESS" else block(CapabilityId.UI_OPEN_PANEL)
    val previewBlock: String? get() = if (busy) "OPERATION_IN_PROGRESS" else block(CapabilityId.ROUTES_PREVIEW)

    /**
     * "Show on PC": opens the PC selector if needed, then moves it to this route. With the selector open only the
     * preview rule applies; PANEL_CLOSED is ignored there because capabilities refresh every 5 s and can lag the
     * 1 s preview (the bridge checks the real state again before moving it).
     */
    val showOnPcBlock: String? get() = if (preview?.panelOpen == true) previewBlock.takeUnless { it == "PANEL_CLOSED" } else openPanelBlock
    /**
     * A workout is unfinished on the PC. Applying a route is still allowed (as in the mod's menu), but the PC then
     * saves the activity and restarts that workout from its countdown on the new route; free ride removes its bot.
     */
    val workoutRunning: Boolean get() = feed?.snapshot?.workout?.let { it.present == true && it.finished != true } == true
    val activeRouteId: String? get() = feed?.snapshot?.activeRoute?.id
    val activeRouteName: String? get() = activeRouteId?.let { id -> data.items.firstOrNull { it.id == id }?.name ?: data.selected?.summary?.takeIf { it.id == id }?.name }
}

@HiltViewModel
class RoutesViewModel @Inject constructor(
    telemetry: TelemetryRepository,
    preferences: AppPreferences,
    private val control: ControlRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val tracker = OperationTracker(viewModelScope, control)
    private val data = MutableStateFlow(RoutesData(query = saved["query"] ?: ""))
    private var searchJob: Job? = null
    private var lastPreview: RoutePreview? = null

    /**
     * Every second while a route detail is visible: the PC selector opens, closes and moves with the PC keyboard at
     * any time (P3-R4..R6). Reading it once when the detail opened left the buttons and the text out of date.
     * A failed read keeps the last answer; the connection state already blocks the actions.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val preview: Flow<RoutePreview?> = data.map { it.selected != null }.distinctUntilChanged().flatMapLatest { open ->
        if (!open) flowOf(lastPreview) else flow {
            emit(lastPreview)
            while (true) {
                (control.routePreview() as? Outcome.Success)?.let { lastPreview = it.value }
                emit(lastPreview)
                delay(PREVIEW_POLL_MS)
            }
        }
    }

    val state: StateFlow<RoutesUiState> = combine(data, telemetry.feed, preferences.settings, tracker.state, preview) { d, feed, settings, op, p ->
        RoutesUiState(d, feed, settings.units, op, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutesUiState(data.value, null, UnitSystem.METRIC, null))

    init {
        load(reset = true)
        saved.get<String>("selected")?.let(::select)
    }

    fun setQuery(q: String) {
        val query = q.take(60)
        saved["query"] = query
        data.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            load(reset = true)
        }
    }

    fun load(reset: Boolean = false) {
        val current = data.value
        if (current.loading && !reset) return
        val offset = if (reset) 0 else current.items.size
        data.update { it.copy(loading = true, errorCode = null) }
        viewModelScope.launch {
            when (val page = control.routes(current.query, offset, PAGE)) {
                is Outcome.Success -> data.update {
                    // A new catalog revision restarts the list; never mix entries from two catalogs.
                    val restart = reset || it.catalogRevision != page.value.catalogRevision
                    it.copy(
                        items = if (restart) page.value.items else it.items + page.value.items,
                        total = page.value.total,
                        catalogRevision = page.value.catalogRevision,
                        loading = false,
                    )
                }
                is Outcome.Failure -> data.update { it.copy(loading = false, errorCode = page.code) }
            }
        }
    }

    fun select(id: String) {
        saved["selected"] = id
        viewModelScope.launch {
            when (val detail = control.route(id)) {
                is Outcome.Success -> data.update {
                    // Native defaults (CourseInfo.LoopActive = true, Reverse = false), limited to what the route allows.
                    it.copy(selected = detail.value, loop = detail.value.summary.loopAllowed, reverse = false, catalogRevision = it.catalogRevision ?: null)
                }
                is Outcome.Failure -> data.update { it.copy(errorCode = detail.code) }
            }
        }
    }

    fun closeDetail() {
        saved.remove<String>("selected")
        data.update { it.copy(selected = null, confirm = null) }
    }

    fun setLoop(v: Boolean) = data.update { it.copy(loop = v, reverse = if (v) it.reverse else false) }
    fun setReverse(v: Boolean) = data.update { it.copy(reverse = v) }

    /**
     * Opens the PC route selector (if closed) and moves it to the route shown in the app. Two commands, the second
     * only after the PC confirmed the first; it never selects or applies the route.
     */
    fun showOnPc() {
        val st = state.value
        val revision = st.data.catalogRevision ?: return
        val routeId = st.data.selected?.summary?.id ?: return
        if (st.showOnPcBlock != null) return
        val moveTo = Command.PreviewRoute(routeId, revision)
        if (st.preview?.panelOpen == true) send(moveTo)
        else send(Command.OpenRoutesPanel) { opened -> if (opened == OperationUi.Applied) send(moveTo) }
    }

    fun askApply() = ask(RouteConfirm.APPLY)
    fun askFreeRide() = ask(RouteConfirm.FREE_RIDE)

    /** Opens the confirmation; with a workout running, also reads its name so the warning can say which one. */
    private fun ask(kind: RouteConfirm) {
        data.update { it.copy(confirm = kind, runningWorkoutName = null) }
        if (!state.value.workoutRunning) return
        viewModelScope.launch {
            val name = (control.workoutProgress() as? Outcome.Success)?.value?.takeIf { it.present }?.name
            data.update { if (it.confirm == kind) it.copy(runningWorkoutName = name) else it }
        }
    }
    fun dismissConfirm() = data.update { it.copy(confirm = null) }

    fun confirm() {
        val d = data.value
        val revision = d.catalogRevision ?: return
        val command = when (d.confirm) {
            RouteConfirm.APPLY -> {
                val route = d.selected?.summary ?: return
                val (loop, reverse) = ControlRules.routeFlags(route, d.loop, d.reverse)
                Command.ApplyRoute(route.id, loop, reverse, revision)
            }
            RouteConfirm.FREE_RIDE -> Command.ApplyRoute(null, loop = false, reverse = false, catalogRevision = revision)
            null -> return
        }
        data.update { it.copy(confirm = null) }
        send(command)
    }

    private fun send(command: Command, then: (OperationUi) -> Unit = {}) {
        val instance = state.value.feed?.snapshot?.adapterInstanceId
        tracker.send(command, instance) { result ->
            // A catalog conflict means the PC list changed: reload instead of guessing.
            if (result is OperationUi.Refused && result.code == "REVISION_CONFLICT") load(reset = true)
            then(result)
        }
    }

    fun dismissOperation() = tracker.dismiss()

    private companion object {
        const val PAGE = 100
        const val PREVIEW_POLL_MS = 1_000L
    }
}
