# Phase 1 Implementation Plan — Local-First Android Tool-Calling Agent Runtime

## Status

Approved (blueprint; execution scheduled after Phase 0 DoD passes).

## Context

Phase 0 produced the complete engineering blueprint: specs 0001–0009, ADRs 0001–0005, and this plan. Phase 0's definition of done (see `docs/plans/phase-0-definition-of-done.md`) requires a zero-toolchain repository. Phase 1 introduces the Android toolchain through an explicit gate (ADR-0005) and implements the runtime in the order that maximizes security, determinism, and testability.

The plan follows the architecture module map from the design record:

```
JVM-testable pure-Kotlin core (zero Android deps)
  core:tool-contract   ToolDefinition/ToolCall/ToolResult, JsonSchema subset, Validator
  core:tool-registry   ToolRegistry (register/get/definitions/execute), determinism
  core:security        SecurityClassifier, ToolPolicy, PermissionEvaluator, ConfirmationGate
  core:model-adapter   AgentModel, ModelRequest/ModelResponse, MockModel, eval harness
Android layer
  app:agent-runtime    AgentRuntime state machine — orchestrates via interfaces only
  app:android-tools    6 tool impls (Android APIs); registered at app composition
  app:ui               Compose surface + confirmation dialog + ViewModel
```

Dependency direction: `ui → agent-runtime → {registry, security, model-adapter} → tool-contract`. `android-tools → tool-contract` only. Adapters never call Android APIs; tools never contain UI; UI never executes tools.

## Repository Hygiene Note (Phase-0-safe)

`git init` is permitted and recommended in Phase 0 to track the blueprint (docs-only commits). It introduces NO toolchain artifact: no Gradle files, no `build.gradle*`, no `settings.gradle*`, no `.kt` sources, no APK. The zero-toolchain gate (DoD) applies to build artifacts, not to version control. Commit docs only.

## Phase 1 Steps

### Step 0 — Toolchain Gate (ADR-0005)

**Gate:** Phase 0 DoD passed; ADR-0005 Approved; this plan reviewed and approved.

**Deliverables:**
- Introduce minimal Gradle project skeleton (settings.gradle.kts, build files) with Kotlin + JVM modules only; NO Android app module yet unless required for the first test.
- Minimal dependencies for JVM testing (JUnit, kotlin-test, kotlinx-serialization for contract types).
- A working test runner executing at least one JVM test.
- **Strict TDD activation:** the moment a test runner exists in this step, `strict_tdd` flips to true (ADR-0005 flip condition). All subsequent steps write RED tests first.

**Verification:** `./gradlew test` runs; the first RED test for the tool contract is written in Step 1.

### Step 1 — Tool Contract (core:tool-contract)

**Deliverables:** `ToolDefinition`, `ToolCall`, `ToolResult` (Success|Failure), `ToolError`, the bounded JSON Schema subset (primitives + arrays-of-primitives, `additionalProperties:false`), and `ToolCallValidator` with no-coercion exact-type validation (spec 0004 C2/C3).

**Tests (RED first):** string-vs-int rejected (`"value":"50"` against IntegerType); unknown additional properties rejected; valid calls pass; deterministic serialization.

**Verification:** JVM tests green; contract tests match spec 0004 scenarios.

### Step 2 — Tool Registry (core:tool-registry)

**Deliverables:** `ToolRegistry` with `register/unregister/get/definitions/execute` (spec 0005 G1). Duplicate names throw; `definitions()` sorted by name; `execute` never throws (unknown → UNKNOWN_TOOL, malformed → MALFORMED_ARGUMENTS).

**Tests (RED first):** duplicate registration rejected; unknown tool returns Failure; deterministic definitions order; `execute` returns Failure instead of throwing.

**Verification:** JVM tests green; registry scenarios from spec 0005 pass.

### Step 3 — Security Core (core:security)

**Deliverables:** `SecurityClassifier` (rule-based D3), `ToolPolicy` (ALLOW|DENY|NEEDS_CONFIRMATION), `PermissionEvaluator`, `ConfirmationGate` interface, `DenyByDefaultGate` (fail-closed), `AndroidPermission` (WRITE_SETTINGS, MODIFY_AUDIO_SETTINGS).

**Tests (RED first):** FORBIDDEN tools cannot register; permission preflight failure → PERMISSION_DENIED; DenyByDefaultGate denies without approval; classifier rules deterministic.

**Verification:** JVM tests green; security scenarios from spec 0006 pass.

### Step 4 — Model Adapter + MockModel (core:model-adapter)

**Deliverables:** `AgentModel`, `ModelRequest`, `ModelResponse` (Answer|ToolCalls|Refused), `MockModel` (seed-driven determinism), and the evaluation harness contract (`evaluate(model, definitions, dataset)`).

**Tests (RED first):** same seed → identical response; no Android imports in adapters (unit-enforced); harness reports 8 metrics (spec 0009 E4).

**Verification:** JVM tests green; spec 0003 A4/A5 and spec 0009 scenarios pass with MockModel.

### Step 5 — Single-Shot Agent Runtime (app:agent-runtime)

**Deliverables:** `AgentRuntime` state machine IDLE→PROCESSING→AWAITING_CONFIRMATION→EXECUTING→COMPLETE|ERROR (spec 0002 R3); `submit(userText)` and `confirm(callId, approved)`; caps ToolCalls at 1/turn (R2); reserves multi-step limits (R4) without building them.

**Tests (RED first):** single-shot pipeline happy path; n>1 ToolCalls capped to first; confirm(false) → fail-closed CONFIRMATION_REQUIRED error.

**Verification:** JVM integration tests with MockModel + fake gate green; runtime scenarios from spec 0002 pass.

### Step 6 — First SAFE Tools (app:android-tools, SAFE subset)

**Deliverables:** `get_current_time`, `get_battery_status`, `get_device_info` (PII allowlist: model, manufacturer, OS release, API level — spec 0007 T1/T2) registered at app composition.

**Tests (RED first):** structured ToolResult; `get_device_info` returns only the 4 allowlisted fields.

**Verification:** JVM tests where tool logic is JVM-testable; instrumented tests deferred to Step 9.

### Step 7 — CONFIRMATION_REQUIRED Tools + Policy Wiring (app:android-tools)

**Deliverables:** `set_brightness` (WRITE_SETTINGS preflight), `set_volume` (MODIFY_AUDIO_SETTINGS preflight), `open_app` (canonical-key allowlist + indirect Intent, no model extras; empty allowlist ⇒ FORBIDDEN — spec 0007 T3/T4/T5).

**Tests (RED first):** permission preflight denial → PERMISSION_DENIED; non-allowlisted open_app → Failure; confirmation path via DenyByDefaultGate.

**Verification:** JVM tests green; spec 0007 scenarios pass.

### Step 8 — UI + Confirmation (app:ui)

**Deliverables:** Jetpack Compose + Material 3 surface; ViewModel exposing `StateFlow<AgentState>` (spec 0008 U1/U2); `UiConfirmationGate` — a `ConfirmationGate` implementation on StateFlow sharing the interface with `DenyByDefaultGate` (U4). UI must never execute tools directly (U3).

**Tests (RED first):** headless run exercises the identical confirmation code path (spec 0008 U4 scenario); dialog renders on AWAITING_CONFIRMATION.

**Verification:** JVM tests for gate logic; instrumented UI tests in Step 9.

### Step 9 — Instrumented Tests + Evaluation Harness Run

**Deliverables:** androidTest coverage for the 6 tools on device (real PermissionEvaluator preflight, confirmation dialog flow); first evaluation run of the 120-record dataset against MockModel; Needle baseline run (spec 0009 E5).

**Verification:** instrumented tests pass on emulator; evaluation report stored in the results store (never hard-coded).

## Definition of Done for Phase 1 Entry

- Phase 0 DoD checklist fully complete (see `docs/plans/phase-0-definition-of-done.md`).
- ADR-0005 gate passed at Step 0 with strict TDD flip.
- Every spec 0001–0009 requirement implemented and verified per its Verification section.
- Zero-toolchain gate lifted only at Step 0; no toolchain artifact exists before it.