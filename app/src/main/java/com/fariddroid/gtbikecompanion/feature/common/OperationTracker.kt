package com.fariddroid.gtbikecompanion.feature.common

import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.OperationStatus
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a screen shows about the last action it sent. */
sealed interface OperationUi {
    data object Sending : OperationUi
    data object Waiting : OperationUi
    data object Applied : OperationUi

    /** Refused before or by the PC: nothing changed. */
    data class Refused(val code: String) : OperationUi

    /** The PC may or may not have applied it: check the game before trying again. */
    data object Uncertain : OperationUi
}

/**
 * Sends one command per user action and follows its operation until a final state. Lives in a ViewModel, so a
 * rotation keeps the same tracker and never sends again. Lost connection while waiting only polls the same
 * requestId; it never creates a new command (CONTRATO_PUENTE_V1.md §8).
 */
class OperationTracker(private val scope: CoroutineScope, private val control: ControlRepository) {
    private val current = MutableStateFlow<OperationUi?>(null)
    val state: StateFlow<OperationUi?> = current.asStateFlow()
    private var job: Job? = null

    val busy: Boolean get() = current.value == OperationUi.Sending || current.value == OperationUi.Waiting

    fun send(command: Command, adapterInstanceId: String?, onFinal: (OperationUi) -> Unit = {}) {
        if (busy) return
        if (adapterInstanceId == null) {
            current.value = OperationUi.Refused("ADAPTER_ABSENT")
            return
        }
        current.value = OperationUi.Sending
        job = scope.launch {
            val result = when (val submitted = control.submit(command, adapterInstanceId)) {
                is Outcome.Failure -> if (submitted.code == "PC_UNREACHABLE" || submitted.code == "INVALID_RESPONSE") OperationUi.Uncertain else OperationUi.Refused(submitted.code)
                is Outcome.Success -> follow(submitted.value.requestId, submitted.value.status, submitted.value.errorCode)
            }
            current.value = result
            onFinal(result)
        }
    }

    private suspend fun follow(requestId: String, initial: OperationStatus, initialError: String?): OperationUi {
        var status = initial
        var error = initialError
        var misses = 0
        while (!status.isFinal) {
            current.value = OperationUi.Waiting
            delay(POLL_MS)
            when (val op = control.operation(requestId)) {
                is Outcome.Success -> { status = op.value.status; error = op.value.errorCode; misses = 0 }
                is Outcome.Failure -> if (++misses >= MAX_MISSES) return OperationUi.Uncertain
            }
        }
        return when (status) {
            OperationStatus.APPLIED -> OperationUi.Applied
            OperationStatus.REJECTED, OperationStatus.FAILED -> OperationUi.Refused(error ?: "CAPABILITY_UNAVAILABLE")
            else -> OperationUi.Uncertain
        }
    }

    fun dismiss() {
        if (!busy) current.value = null
    }

    private companion object {
        const val POLL_MS = 700L
        const val MAX_MISSES = 20
    }
}
