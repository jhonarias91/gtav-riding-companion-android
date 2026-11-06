package com.fariddroid.gtbikecompanion.core.demo

import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Offline demo feed. Emits at the same 1 s cadence the bridge polling will use (plan §14). */
class DemoTelemetryRepository(
    initial: DemoScenario = DemoScenario.LIVE_CYCLING,
    private val periodMs: Long = 1_000,
) : TelemetryRepository {
    private val selected = MutableStateFlow(initial)
    val scenario: StateFlow<DemoScenario> = selected.asStateFlow()

    fun select(scenario: DemoScenario) {
        selected.value = scenario
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val feed: Flow<TelemetryFeed> = selected.flatMapLatest { scenario ->
        if (scenario == DemoScenario.LIVE_CYCLING) {
            flow {
                var tick = 0L
                while (true) {
                    emit(DemoScenarios.liveCycling(tick++))
                    delay(periodMs)
                }
            }
        } else {
            flowOf(DemoScenarios.snapshotFor(scenario, 0))
        }
    }
}
