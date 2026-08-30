# 0002 — Agent Runtime: Single-Shot Tool-Calling Loop

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The agent runtime is the orchestrator between the user, the model adapter, and the tool system. Its responsibilities are: accept user text, drive the model, validate the resulting structured tool call, route it through security/permission policy, execute it, and produce a structured `AgentResponse`. Per the foundation spec (0001), the runtime executes a **single-shot** loop in the initial implementation: one user input, at most one tool call, one result. Autonomous/iterative/multi-step behavior is deliberately out of scope and only reserved at the API level.

## Goals

- R1. Enable the full pipeline: User → `AgentRuntime` → `AgentModel` → `ToolCall` → `ToolRegistry` → validation → `ToolPolicy` → `Tool` → `ToolResult` → `AgentResponse`.
- R2. Enforce single-shot semantics: at most one tool call per turn.
- R3. Define the deterministic state machine and guarantee every terminal state carries a structured result or reply.
- R4. Reserve (not build) the multi-step execution limits.
- R5. Expose a minimal, deterministic entry surface (`submit` / `confirm` / `reset`).

## Non-goals

- Autonomous agents, infinite loops, long-term planning, memory, or multi-step reasoning in the initial implementation.
- Re-planning or retrying based on tool results within one turn.
- Model-initiated follow-up tool calls (model can only request, runtime decides execution scope).
- Executing more than one tool call per turn, even when the model returns several.
- Any Android API access from the runtime beyond the tool implementations it invokes.

## Requirements

- R1 (RFC 2119 — SHALL): The runtime **SHALL** execute the pipeline in order: User → `AgentRuntime` → `AgentModel.generate` → structured `ToolCall` → `ToolRegistry.execute` → argument validation → `ToolPolicy.evaluate` → `Tool.run` → `ToolResult` → `AgentResponse`.
- R2 (RFC 2119 — SHALL): The runtime **SHALL** be single-shot: at most one `ToolCall` executes per turn. If `ModelResponse.ToolCalls` contains more than one call, the runtime **SHALL** discard all but the first. `ModelRequest.maxToolCalls` **SHALL** default to `1` and be sent to the model.
- R3 (RFC 2119 — SHALL): The runtime **SHALL** follow the state machine `IDLE → MODEL_PENDING → VALIDATING → AWAITING_CONFIRMATION → EXECUTING → COMPLETE` (or `ERROR`). Every terminal state (`COMPLETE`, `ERROR`) **MUST** carry a structured `ToolResult` or reply text.
- R4 (RFC 2119 — SHALL): Future multi-step execution **SHALL** be reserved in the API (not built): `maxIterations`, `maxToolCallsPerTurn` (initially `1`), `timeout`, `retryLimit`, `tokenBudget`, `permissionEscalationRules`.
- R5 (RFC 2119 — SHALL): `submit(userText)` **SHALL** be the only entry point for user turns; `confirm(callId, approved)` **SHALL** resolve a pending confirmation; `reset()` **SHALL** return the runtime to `IDLE`.
- The model output **MUST** never reach an Android API, shell, arbitrary Intent, reflection, filesystem, or arbitrary Kotlin code. The only execution target is a registered tool gated by the ToolRegistry and ToolPolicy (see spec 0005 and 0006).

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN user text that maps to exactly one tool call, WHEN `submit` is called, THEN the state machine reaches `COMPLETE` with exactly one `ToolResult`.
- GIVEN a `ModelResponse.ToolCalls` containing `n > 1` calls, WHEN processed, THEN only the first call executes and all remaining calls are discarded with no side effects.
- GIVEN `confirm(callId, false)` on a pending call in `AWAITING_CONFIRMATION`, WHEN confirmed, THEN the runtime reaches `COMPLETE` with a `CONFIRMATION_REQUIRED`-type failure result.
- GIVEN an unknown tool name or malformed arguments, WHEN the registry executes, THEN a `Failure` result is produced without ever reaching a `Tool` (see spec 0005).

## Architecture

```
User text ──▶ AgentRuntime ──▶ AgentModel.generate(ModelRequest)   [definitions, maxToolCalls=1]
                    │  ◀── ModelResponse { Answer | ToolCalls | Refused }
                    ▼
             ToolRegistry.execute(call) ── ToolCallValidator (no coercion)
                    ▼
             ToolPolicy.evaluate: classification → PermissionEvaluator → ConfirmationGate
                    ▼
             Tool.run(arguments) ──▶ ToolResult ──▶ AgentResponse → UI
```

Dependencies: the runtime orchestrates other components via interfaces only. It depends on `AgentModel`, `ToolRegistry`, `ToolPolicy`, and `ConfirmationGate`; it never implements tools and never calls Android APIs directly.

Reserved multi-step limits (present in the API contract but not exercised in Phase 1):

| Limit | Default | Semantics |
|---|---|---|
| `maxIterations` | reserved | maximum outer agent iterations (multi-step future) |
| `maxToolCallsPerTurn` | 1 | tool calls the runtime will execute per user turn |
| `timeout` | reserved | wall-clock budget for a turn |
| `retryLimit` | reserved | retries on transient tool failures |
| `tokenBudget` | reserved | total token budget across the turn |
| `permissionEscalationRules` | reserved | rules governing confirmation escalation |

## Data Contracts

Kotlin API sketch (design artifact — not implementation):

```kotlin
// app:agent-runtime
enum class AgentStatus { IDLE, PROCESSING, AWAITING_CONFIRMATION, COMPLETE, ERROR }

sealed interface AgentState {
    data class Idle(val status: AgentStatus = AgentStatus.IDLE) : AgentState
    data class Processing(val status: AgentStatus, val phase: Phase) : AgentState  // MODEL_PENDING | VALIDATING | EXECUTING
    data class AwaitingConfirmation(val call: ToolCall, val toolName: String, val summary: String) : AgentState
    data class Complete(val response: AgentResponse) : AgentState
    data class Error(val message: String) : AgentState
}

interface AgentRuntime {
    val state: StateFlow<AgentState>
    suspend fun submit(userText: String)
    suspend fun confirm(callId: String, approved: Boolean)
    fun reset()
}

// Reserved multi-step limits (designed, not built in Phase 1)
data class AgentLimits(
    val maxIterations: Int? = null,
    val maxToolCallsPerTurn: Int = 1,
    val timeoutMillis: Long? = null,
    val retryLimit: Int = 0,
    val tokenBudget: Long? = null,
    val permissionEscalationRules: List<EscalationRule> = emptyList(),
)

sealed interface AgentResponse {
    data class Text(val text: String) : AgentResponse
    data class ToolResultData(val result: ToolResult, val confirmed: Boolean) : AgentResponse
    data class Refusal(val reason: String, val text: String) : AgentResponse
}
```

The `ToolCall`, `ToolResult`, `ToolRegistry`, and `ToolPolicy` types are defined in specs 0004, 0005, and 0006; this spec references them without redefining contract semantics.

## State Machine

```
IDLE → submit() → MODEL_PENDING → ModelResponse.Answer/Refused ⇒ COMPLETE
                  MODEL_PENDING → ToolCalls(≤1) → VALIDATING
                      VALIDATING → Policy DENY (unknown/malformed/permission) ⇒ COMPLETE(Error)
                      VALIDATING → NEEDS_CONFIRMATION → AWAITING_CONFIRMATION
                          AWAITING_CONFIRMATION → denied ⇒ COMPLETE(CONFIRMATION_REQUIRED error)
                          AWAITING_CONFIRMATION → approved → EXECUTING
                              EXECUTING → Tool.run() ⇒ COMPLETE(ToolResult)
```

Invariants:
- `MODEL_PENDING`, `VALIDATING`, and `EXECUTING` are grouped under an internal `PROCESSING` status for UI purposes.
- No transition from `COMPLETE`/`ERROR` to any processing state except via a new `submit()` after `reset()` (or implicit reset at next submit).
- Unknown tool / malformed arguments never reach a `Tool`: the registry returns `Failure(UNKNOWN_TOOL | MALFORMED_ARGUMENTS)`.

## Security

- Model output flowing into the runtime is UNTRUSTED data. The runtime must not treat any model-inferred "approval", "tool we can trust", or instruction embedded in arguments as authorization.
- Confirmation is handled by an injectable `ConfirmationGate`; absence or failure of the gate means denial (fail-closed). See spec 0006.
- The runtime must not invoke any tool without passing through `ToolRegistry.execute` and `ToolPolicy.evaluate`.

## Error Handling

| Failure | Handling |
|---|---|
| Model returns `Refused` | Terminal `COMPLETE` with refusal text; no execution. |
| Model returns `ToolCalls(n>1)` | Execute first only; discard the rest; log the discard count. |
| ToolRegistry returns `Failure(UNKNOWN_TOOL)` | Terminal `COMPLETE` with error result; no tool invoked. |
| ToolRegistry returns `Failure(MALFORMED_ARGUMENTS)` | Terminal `COMPLETE`; rejection enforced by no-coercion validator. |
| ToolPolicy returns `DENY` (permission) | Terminal `COMPLETE` with `PERMISSION_DENIED`. |
| Confirmation denied | Terminal `COMPLETE` with `CONFIRMATION_REQUIRED`. |
| Gate missing/fails | Deny (fail-closed); `CONFIRMATION_REQUIRED`. |
| Tool throws unexpectedly | Wrapped as `Failure(EXECUTION_ERROR)`; runtime reaches `ERROR` only for orchestration faults. |

The runtime never blocks: every user-visible path ends in a structured `AgentResponse` or an explicit error state.

## Acceptance Criteria

- AC1. `submit("What is the time?")` with a SAFE tool selected reaches `COMPLETE` carrying one `ToolResult.Success`.
- AC2. A model response with two `ToolCall`s results in exactly one execution; the second call has no side effects.
- AC3. `confirm(callId, false)` on a pending call yields a `CONFIRMATION_REQUIRED` failure and no tool execution.
- AC4. Unknown-tool and malformed-argument requests end in `Failure` without invoking any `Tool`.
- AC5. Every terminal state exposes a structured result or reply (scenario invariants R3).
- AC6. Headless operation (no UI) still enforces confirmation via `DenyByDefaultGate` — identical code path to UI confirmation.

## Verification

- V1. Pure-Kotlin unit tests over the runtime with `MockModel` (Phase 1, strict TDD after ADR-0005): state transition table, n>1 cap, deny path, permission-denied path.
- V2. Integration tests: runtime + registry + policy with a fake gate; every `PolicyDecision` path incl. permission refusal.
- V3. Confirm every terminal state carries a structured result or reply in all test scenarios (AC1–AC6).
- V4. Runtime tests never instantiate an Android `Context` or call Android APIs (pure-Kotlin orchestration).