package com.agentai.app.ui

import com.agentai.core.contract.ToolCall
import com.agentai.core.security.Approval
import com.agentai.core.security.ConfirmationGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One [ConfirmationGate] implementation backed by a [StateFlow] (spec 0008 U4).
 *
 * The Compose dialog observes [request] (a pending [ToolCall]) and pushes the
 * human's decision via [grant] / [deny]. [requestApproval] suspends on a
 * [CompletableDeferred] until the dialog decides.
 *
 * This is the SAME interface `DenyByDefaultGate` implements (spec 0006 S5),
 * so headless runs and UI runs exercise the identical confirmation code path —
 * the dialog never appears in tests because the headless gate is used instead.
 */
class UiConfirmationGate : ConfirmationGate {

    private val _request = MutableStateFlow<ToolCall?>(null)
    val request: StateFlow<ToolCall?> = _request.asStateFlow()

    // Recreated on each request: a completed deferred must never satisfy a
    // later request without showing the dialog again.
    private var pending = CompletableDeferred<Approval>()

    override suspend fun requestApproval(call: ToolCall): Approval {
        pending = CompletableDeferred()
        _request.value = call
        return pending.await()
    }

    /** Called by the dialog on Approve. */
    fun grant() {
        pending.complete(Approval.Granted)
        _request.value = null
    }

    /** Called by the dialog on Deny. */
    fun deny(reason: String = "user denied") {
        pending.complete(Approval.Denied(reason))
        _request.value = null
    }
}