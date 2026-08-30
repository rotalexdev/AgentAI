# 0008 — Agent UI: Compose Surface, StateFlow Observation, Confirmation Gate

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The runtime is a local Android tool-calling agent, not a chatbot. The UI is a thin observer of the runtime: it renders conversation state, shows the current `AgentStatus`, and — when a `CONFIRMATION_REQUIRED` tool is pending — presents a confirmation dialog. The UI MUST NOT execute tools directly; every tool call goes through the same `ToolRegistry` → `ToolPolicy` → `ConfirmationGate` chain as headless runs. The confirmation dialog is one `ConfirmationGate` implementation (`UiConfirmationGate` on a `StateFlow`), sharing the exact interface that `DenyByDefaultGate` implements, so the confirmation code path is identical with or without a UI.

## Goals

- U1. Target Jetpack Compose + Material 3 for the Phase 1 UI.
- U2. Observe the runtime through `StateFlow<AgentState>` exposed by the ViewModel.
- U3. Ensure the UI never executes tools directly — the runtime is the only execution path.
- U4. Implement the confirmation dialog as a single `ConfirmationGate` implementation (`UiConfirmationGate` on `StateFlow`), on the same interface as `DenyByDefaultGate`, rendering tool name + human-readable arguments.

## Non-goals

- A chatbot-style conversational interface (the product is an agent runtime, not primarily a chat app).
- UI logic inside tools, model adapters, or the registry.
- Tools called from the UI layer.
- Multi-step autonomous UI flows, agent memory, or long-term planning (reserved, not built).
- UI polish before correctness, security, determinism, and testability (priority order, spec 0001 F3).

## Requirements

- U1 (RFC 2119 — SHALL): The UI **SHALL** be built with Jetpack Compose and Material 3 in Phase 1. XML-based screens **SHALL NOT** be used unless technically necessary and documented.
- U2 (RFC 2119 — SHALL): The ViewModel **SHALL** expose the runtime state to the UI as a `StateFlow<AgentState>`. The UI **SHALL** collect that flow and render the current `AgentStatus` (IDLE / PROCESSING / AWAITING_CONFIRMATION / COMPLETE / ERROR) and its structured payload (reply or `ToolResult`).
- U3 (RFC 2119 — MUST NOT): The UI **MUST NOT** execute tools directly. There **MUST NOT** be any code path from a UI component to a `Tool` or to `ToolRegistry.execute` other than through `AgentRuntime.submit` / `AgentRuntime.confirm`. Tool selection, validation, policy, and execution are runtime responsibilities.
- U4 (RFC 2119 — SHALL): The confirmation dialog **SHALL** be exactly one `ConfirmationGate` implementation: `UiConfirmationGate`, backed by a `StateFlow`, implementing the same `interface ConfirmationGate { suspend fun requestApproval(call: ToolCall): Approval }` as `DenyByDefaultGate`. The dialog **SHALL** render the tool name and a human-readable rendering of the arguments before asking for approval. Approve/Deny **SHALL** be routed through the gate's `Approval` result (`Granted` / `Denied(reason)`).

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN the runtime is in `AWAITING_CONFIRMATION` for a `CONFIRMATION_REQUIRED` tool, WHEN the UI renders the dialog, THEN only a gate-approved call proceeds to execution; a `Denied` approval yields `ToolResult.Failure(CONFIRMATION_REQUIRED)`.
- GIVEN no UI is attached (headless/CLI/test run), WHEN a `CONFIRMATION_REQUIRED` call reaches the policy chain, THEN `DenyByDefaultGate` enforces confirmation on the identical code path — the dialog never appears and the request is denied unless explicitly granted.
- GIVEN the dialog shows a pending call, WHEN the user approves, THEN `UiConfirmationGate` returns `Approval.Granted` and the runtime continues to execution; the approval never bypasses validation or policy.

## Architecture

```
app:ui
  ComposeActivity / Compose surface          — collects StateFlow<AgentState>
    └── ViewModel                            — owns runtime reference; exposes StateFlow<AgentState>
          └── AgentRuntime (app:agent-runtime, orchestrated via interfaces only)
                ├── ToolRegistry ── ToolPolicy ── ConfirmationGate
                └── AgentModel (core:model-adapter)
```

- The ViewModel holds the single `AgentRuntime` reference; UI components never see the runtime, the registry, or any `Tool`.
- `UiConfirmationGate` (app:ui) and `DenyByDefaultGate` (core:security) both implement `ConfirmationGate`; `ToolPolicy` calls the gate through the interface only, so the confirmation code path is byte-identical for UI and headless runs (spec 0006 S5).
- Dependency direction is `ui → agent-runtime → {registry, security, model-adapter} → tool-contract` (design module map). `app:ui` may depend on `core:security` only to type-check `ConfirmationGate`/`Approval`.

## Data Contracts

```kotlin
// app:ui — one ConfirmationGate implementation, same interface as DenyByDefaultGate (core:security)
class UiConfirmationGate(
    private val requests: StateFlow<ConfirmationRequest?>,   // dialog input
    private val decisions: StateFlow<Approval?>,             // dialog output (Approve/Deny)
) : ConfirmationGate {
    override suspend fun requestApproval(call: ToolCall): Approval {
        requests.value = ConfirmationRequest(call, renderHumanReadable(call))
        return decisions.filterNotNull().first()   // resolves only when the user decides
    }
}

data class ConfirmationRequest(
    val call: ToolCall,
    val humanReadableArguments: String,   // e.g. "set_brightness: value = 50"
)

fun renderHumanReadable(call: ToolCall): String   // tool name + argument preview, no raw JSON to users

// core:security — the shared seam (spec 0006), verbatim:
interface ConfirmationGate { suspend fun requestApproval(call: ToolCall): Approval }
class DenyByDefaultGate : ConfirmationGate     // headless, fail-closed — tests & CLI default
sealed interface Approval {
    data object Granted : Approval
    data class Denied(val reason: String) : Approval
}

// app:agent-runtime — state surface observed by the UI (design contract)
interface AgentRuntime {
    val state: StateFlow<AgentState>
    suspend fun submit(userText: String)
    suspend fun confirm(callId: String, approved: Boolean)
    fun reset()
}
// AgentState carries AgentStatus (IDLE/PROCESSING/AWAITING_CONFIRMATION/COMPLETE/ERROR) plus
// the pending call, reply, or ToolResult; exact field shape is finalized in Phase 1 (see plan Step 8).
```

## Security

- The UI is a renderer, never an authorization source: an "Approve" tap returns `Approval.Granted` through the gate interface; it cannot bypass the validator, `ToolPolicy`, or permission preflight.
- Headless runs stay fail-closed: with no UI attached, `DenyByDefaultGate` denies any `CONFIRMATION_REQUIRED` call not explicitly granted (spec 0006 S5).
- The dialog renders only the tool name and a human-readable argument preview — never raw model JSON and never implementation details of `ToolError` or permission internals.
- No UI path may construct or mutate a `ToolCall` that was not produced by the runtime.

## Error Handling

| Failure | Handling |
|---|---|
| Runtime in `AWAITING_CONFIRMATION`, user denies | `UiConfirmationGate` returns `Approval.Denied`; runtime completes with `ToolResult.Failure(CONFIRMATION_REQUIRED)`. |
| No UI attached (headless) | `DenyByDefaultGate` denies; identical code path, no dialog. |
| Gate throws / state flow broken | Policy denies (fail-closed, spec 0006 S5). |
| UI collects state after runtime `reset()` | `AgentState` returns to IDLE; UI re-renders from the single flow (no stale fragments). |
| `AgentStatus.ERROR` with structured `ToolResult.Failure` | UI renders the error surfaced by the runtime; it does not guess or re-derive failure causes. |

## Acceptance Criteria

- AC1. The ViewModel exposes `StateFlow<AgentState>` and the UI renders every `AgentStatus` (U2).
- AC2. No UI component holds a reference to a `Tool`, `ToolRegistry`, or `AgentModel`; the only entry points are `AgentRuntime.submit`/`confirm` (U3).
- AC3. `UiConfirmationGate` and `DenyByDefaultGate` implement the same `ConfirmationGate` interface and are interchangeable at the `ToolPolicy` seam (U4).
- AC4. The dialog renders tool name + human-readable arguments before any decision (U4).
- AC5. Headless runs exercise the identical confirmation code path: `DenyByDefaultGate` denies without a dialog, and the runtime result is `CONFIRMATION_REQUIRED` (U4 + spec 0006 S5).
- AC6. An approved call still passes validation and `ToolPolicy` before execution; the UI cannot skip the chain (U3 + spec 0006).

## Verification

- V1. JVM tests (Phase 1, strict TDD after ADR-0005): `UiConfirmationGate` contract tests using a fake `StateFlow` decision source; `DenyByDefaultGate` behavior identical; both satisfy `ConfirmationGate`.
- V2. Integration: runtime + registry + policy with `UiConfirmationGate` (grant and deny paths) and with `DenyByDefaultGate` (headless deny path) — same policy outcome asserted.
- V3. Compose UI test (Phase 1): `AWAITING_CONFIRMATION` renders the dialog with tool name + human-readable args; Approve routes `Granted`, Deny routes `Denied`.
- V4. Static check: grep `app:ui` for any direct `ToolRegistry.execute`, `Tool.run`, or `AgentModel.generate` call — none may exist (U3).
- V5. Headless verification: run the runtime with `DenyByDefaultGate` and confirm the confirmation code path and result equal the UI run minus the dialog (U4).