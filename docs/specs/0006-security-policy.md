# 0006 — Security Policy: Rule-Based Classification, Preflight, Confirmation Gate

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The agent runtime treats all model output as untrusted data. Authorization must never come from the model; it belongs in deterministic application code. This specification defines the security boundary: a rule-based `SecurityClassifier`, a `ToolPolicy` preflight (permission evaluation + per-tool go/no-go), and an injectable `ConfirmationGate` that fails closed. Together these guarantee the model can only request registered tools, and that no tool executes without passing validation, policy, and (when required) explicit human confirmation.

## Goals

- S1. Treat model output as untrusted data — never as authorization.
- S2. Classify tools by rule (side-effects + PII exposure) into SAFE / CONFIRMATION_REQUIRED / FORBIDDEN.
- S3. Gate execution through `ToolPolicy.evaluate` with permission preflight (WRITE_SETTINGS → `set_brightness`, MODIFY_AUDIO_SETTINGS → `set_volume`).
- S4. Make FORBIDDEN executions impossible to register or execute.
- S5. Route confirmation through an injectable `ConfirmationGate`; `DenyByDefaultGate` is deny-by-default (fail-closed); gate absence/failure = deny.

## Non-goals

- Modeling arbitrary threat actors inside the runtime (this spec is the security design, not a process-integration boundary).
- Free-form/LLM-derived classification.
- Mutable, drifting per-tool "labels" that can go stale.
- A cloud-based policy backend.

## Requirements

- S1 (RFC 2119 — SHALL): Model output **SHALL** be treated as untrusted data and never as authorization. No model-inferred "permission", "tool trusted by model", or instruction embedded in arguments grants execution rights.
- S2 (RFC 2119 — SHALL): Classification **SHALL** be rule-based via `SecurityClassifier.classify(sideEffect, piiExposure)`:
  - No side effect + no PII exposure → SAFE.
  - Any side effect / system mutation / leaves the app → CONFIRMATION_REQUIRED.
  - Shell execution, arbitrary code execution, credential extraction, unrestricted filesystem access, reflection, arbitrary-Intent launch → FORBIDDEN. FORBIDDEN tools **MUST NOT** be registerable.
- S3 (RFC 2119 — SHALL): `ToolPolicy.evaluate(tool, call)` **SHALL** return `ALLOW | DENY | NEEDS_CONFIRMATION`. Permission preflight **SHALL** apply: `WRITE_SETTINGS` required for `set_brightness`; `MODIFY_AUDIO_SETTINGS` required for `set_volume`; preflight failure ⇒ `PERMISSION_DENIED`.
- S4 (RFC 2119 — MUST): Forbidden executions **MUST** be impossible to register/execute: a `FORBIDDEN` tool cannot be registered (registry rejects it), therefore `execute` can never see one.
- S5 (RFC 2119 — SHALL): Confirmation **SHALL** go through an injectable `ConfirmationGate`. `DenyByDefaultGate` **SHALL** deny by default (fail-closed) and be the default for tests and headless/CLI runs. If the gate is absent or fails, the outcome **MUST** be denial.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN a tool classified FORBIDDEN, WHEN `register()` is attempted, THEN registration is rejected and no execution path exists.
- GIVEN `set_brightness` on a device without `WRITE_SETTINGS`, WHEN `ToolPolicy` preflights, THEN `ToolResult.Failure(PERMISSION_DENIED)`.
- GIVEN a runtime in `AWAITING_CONFIRMATION` where the gate did not grant approval, WHEN execution is requested, THEN it is denied (fail-closed).

## Architecture

```
core:security
   SecurityClassifier.classify(sideEffect, piiExposure) → SAFE | CONFIRMATION_REQUIRED | FORBIDDEN
   PermissionEvaluator.hasPermission(p: AndroidPermission) → Boolean
   ConfirmationGate.requestApproval(call) → Approval { Granted | Denied(reason) }
   ToolPolicy.evaluate(tool, call) → PolicyDecision { ALLOW | DENY | NEEDS_CONFIRMATION }
```

Execution gate chain (before any `Tool.run`):

```
ToolRegistry reserves tool
  → SecurityClassification (rule-based; FORBIDDEN never registered)
  → ToolCallValidator (no coercion)
  → ToolPolicy.evaluate
      ├── PermissionEvaluator preflight (WRITE_SETTINGS/MODIFY_AUDIO_SETTINGS/mapped perms)
      ├── ConfirmationGate for CONFIRMATION_REQUIRED
      └── Deterministic ALLOW / DENY / NEEDS_CONFIRMATION
  → Tool.run(arguments)
```

`app:ui`'s dialog is one `ConfirmationGate` implementation (`UiConfirmationGate`); headless runs use the same interface with `DenyByDefaultGate`. See spec 0008.

## Data Contracts

```kotlin
// core:security
enum class SecurityClassification { SAFE, CONFIRMATION_REQUIRED, FORBIDDEN }

enum class SideEffect { NONE, SYSTEM_MUTATION, LEAVES_APP, SHELL, CODE_EXECUTION }
enum class PiiExposure { NONE, LIMITED_ALLOWLIST, CREDENTIALS, SENSITIVE }

object SecurityClassifier {                    // rule-based (D3); deterministic
    fun classify(sideEffect: SideEffect, piiExposure: PiiExposure): SecurityClassification
}

enum class AndroidPermission { WRITE_SETTINGS, MODIFY_AUDIO_SETTINGS }
interface PermissionEvaluator { fun hasPermission(p: AndroidPermission): Boolean }

sealed interface Approval {
    data object Granted : Approval
    data class Denied(val reason: String) : Approval
}

interface ConfirmationGate { suspend fun requestApproval(call: ToolCall): Approval }
class DenyByDefaultGate : ConfirmationGate     // headless, fail-closed — tests & CLI default

sealed interface PolicyDecision {
    data object ALLOW : PolicyDecision
    data object DENY : PolicyDecision
    data class NEEDS_CONFIRMATION(val reason: String) : PolicyDecision
}

interface ToolPolicy {
    suspend fun evaluate(tool: Tool, call: ToolCall): PolicyDecision
}
```

## Security

Threat model:

| Threat | Control |
|---|---|
| Model requests superuser/shell action | FORBIDDEN classification; cannot register; execute impossible. |
| Model requests arbitrary code | FORBIDDEN; cannot register. |
| Model extracts credentials / private data | FORBIDDEN (or CONFIRMATION_REQUIRED for bounded cases). |
| Model gains unrestricted filesystem | FORBIDDEN; tools enumerate no path API. |
| Model triggers reflection | FORBIDDEN; no reflection surface. |
| Model launches arbitrary Intent | FORBIDDEN — only allowlisted, canonical-key indirect Intents (spec 0007). |
| System mutation without consent | CONFIRMATION_REQUIRED + permission preflight + ConfirmationGate. |
| Gate absence/failure | Deny (fail-closed). |
| Malformed/unknown calls | Validator + registry reject (spec 0004/0005). |

Authorization is always a deterministic app-code decision; the model is never an authorization source.

## Error Handling

| Failure | Handling |
|---|---|
| FORBIDDEN tool registration | Registration rejected; no runtime path. |
| Missing permission (preflight) | `Failure(PERMISSION_DENIED)`. |
| Requires confirmation, no gate granted | `Failure(CONFIRMATION_REQUIRED)` (deny-by-default). |
| Gate throws | Deny; `CONFIRMATION_REQUIRED` failure. |
| Unknown/unsupported classification input | Rule table is total: every `(SideEffect, PiiExposure)` pair maps to a classification; no `null`. |

## Acceptance Criteria

- AC1. FORBIDDEN classification blocks registration (S4).
- AC2. `set_brightness` without `WRITE_SETTINGS` → `PERMISSION_DENIED` (S3).
- AC3. `DenyByDefaultGate` denies any request not explicitly granted (S5).
- AC4. Gate absence or failure always results in denial (S5).
- AC5. Classification covers the full `SideEffect × PiiExposure` cross-product with deterministic output (S2).
- AC6. Model-inferred authorization never gates execution (S1) — no code path reads model output as a permission flag.

## Verification

- V1. Unit tests (Phase 1, strict TDD after ADR-0005): classifier truth table, FORBIDDEN registration rejection, preflight denials, DenyByDefaultGate behavior, gate-failure → deny.
- V2. Integration: runtime + registry + policy with fake gate; all PolicyDecision paths.
- V3. Static check: no code path consults model output for authorization (grep for authorization flags sourced from `ModelResponse`).
- V4. Threat-matrix walkthrough (table above) recorded in review.