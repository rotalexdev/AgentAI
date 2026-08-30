package com.agentai.app.ui

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolCall
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.security.Approval
import com.agentai.core.security.ConfirmationGate
import com.agentai.core.security.DenyByDefaultGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0008 U4 — the confirmation dialog is one ConfirmationGate
 * implementation sharing the interface with DenyByDefaultGate. Executed in
 * Phase 1 Step 0 (ADR-0005 flip).
 */
class UiConfirmationGateTest {

    private val call = ToolCall("c1", "set_brightness", buildJsonObject { put("value", 50) })

    @Test
    fun `grant flows through the same ConfirmationGate interface`() = runTest {
        val gate: ConfirmationGate = UiConfirmationGate() // interface type, like DenyByDefaultGate
        val approval = CompletableDeferred<Approval>()
        val job = launch { approval.complete(gate.requestApproval(call)) }
        // Let the coroutine reach the await, then grant:
        kotlinx.coroutines.yield()
        kotlinx.coroutines.yield()
        (gate as UiConfirmationGate).grant()
        job.join()
        assertEquals(Approval.Granted, approval.await())
    }

    @Test
    fun `deny flows through the same ConfirmationGate interface`() = runTest {
        val gate = UiConfirmationGate()
        val approval = CompletableDeferred<Approval>()
        val job = launch { approval.complete(gate.requestApproval(call)) }
        kotlinx.coroutines.yield()
        kotlinx.coroutines.yield()
        gate.deny("user said no")
        job.join()
        assertTrue(approval.await() is Approval.Denied)
    }

    @Test
    fun `headless gate and UI gate share the interface contract`() {
        val headless: ConfirmationGate = DenyByDefaultGate()
        val ui: ConfirmationGate = UiConfirmationGate()
        assertTrue(headless is ConfirmationGate)
        assertTrue(ui is ConfirmationGate)
    }
}