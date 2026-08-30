# 0001 — Project Foundation: Local-First Android Tool-Calling Agent Runtime

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

This repository hosts a native Android AI-agent project written exclusively in Kotlin. The core objective is a **local-first Android agent** that converts natural-language instructions into safe, structured tool calls using extremely small on-device models (e.g. Cactus Needle ~26M parameters, Google FunctionGemma 270M). The architecture MUST NOT depend directly on any specific model.

The application is NOT primarily a chatbot. It is a **local Android tool-calling agent runtime**: the model's job is natural language → intent → tool selection → structured arguments; the Android application performs tool validation → permission validation → execution → result handling. The model NEVER directly executes Android APIs.

The current phase is **PHASE 0 — ARCHITECTURE AND SDD**. No toolchain (Java/JDK, Gradle, Android SDK, Kotlin compiler) may be installed, no APK built, no Gradle project generated. The only allowed outputs are specifications, architecture, contracts, sketches, schemas, security policies, test cases, evaluation datasets, ADRs, documentation, and implementation plans. Development MUST follow Specification-Driven Development (SDD) exclusively.

This document is the foundation specification. Every other specification (0002–0009), decision record (ADR-0001–0005), and implementation plan inherits its scope, priorities, and constraints.

## Goals

- F1. Define the product as a **local-first** agent runtime: no cloud model or backend may be required for the core loop.
- F2. Establish **SDD-only** development governance: no bare requirement → implementation jump.
- F3. Fix the **decision priority order** used to resolve every architectural conflict.
- F4. Enforce the **Phase 0 no-toolchain constraint** (Gradle/JDK/SDK/APK/build files are forbidden).
- F5. Define the **toolchain-introduction gate** (ADR-0005) that must be passed before any Phase 1 build.
- F6. Document the **definition of done** for Phase 0 and the pipeline that the final blueprint must describe (User → AgentRuntime → ModelAdapter → Structured ToolCall → ToolRegistry → Argument Validation → Security/Permission Policy → Android Tool → ToolResult → AgentResponse).

## Non-goals

- Installing or requiring any toolchain (Java, JDK, Gradle, Gradle wrapper, Android SDK, Android Studio, Kotlin compiler, Android build tools).
- Building an APK or any build artifact.
- Generating a Gradle project or `build.gradle`/`settings.gradle` files.
- Adding Gradle dependencies.
- Implementing any application code (Kotlin source files are out of scope during Phase 0; inline Kotlin API sketches inside markdown are permitted).
- Coupling the architecture to any specific model (Needle is only the initial reference model).
- Optimizing for performance before correctness/security/determinism.
- Building multi-step autonomous agents, memory, or long-term planning in the initial implementation.

## Requirements

- F1 (RFC 2119 — MUST): The runtime **MUST** be local-first: the core agent loop **MUST NOT** require a cloud model, cloud backend, or network for correct operation.
- F2 (RFC 2119 — SHALL): Development **SHALL** follow SDD exclusively. A feature **MUST NOT** jump from requirement to implementation: it must pass Requirement → Specification → Design → Plan → Implementation → Verification. If a required specification does not exist, it **MUST** be created before implementation. Ambiguous requirements **MUST NOT** be implemented. Requirements in conflict with an existing specification **MUST** stop work until the specification is updated.
- F3 (RFC 2119 — SHALL): Architectural decisions **SHALL** be prioritized in this order: Security, Determinism, Correct tool execution, Model independence, Local execution, Testability, Maintainability, Performance, UI polish. Lower-priority goals **MUST NOT** be satisfied at the expense of higher-priority goals.
- F4 (RFC 2119 — MUST NOT): Phase 0 **MUST NOT** introduce any toolchain artifact: no Gradle files, no `build.gradle`, no `settings.gradle`, no Kotlin/Java source, no APK, no build outputs, no Android SDK/JDK/Gradle installation.
- F5 (RFC 2119 — SHALL): Toolchain introduction **SHALL** be gated by ADR-0005 (`docs/decisions/ADR-0005-toolchain-introduction-gate.md`) before any Phase 1 build begins.
- F6 (RFC 2119 — MUST): The complete system **MUST** follow the core flow: User → `AgentRuntime` → `ModelAdapter` → Structured `ToolCall` → `ToolRegistry` → Argument Validation → Security/Permission Policy → Android Tool → `ToolResult` → `AgentResponse`. The model can only request registered tools; it can never reach arbitrary Kotlin code, shell, arbitrary Intents, reflection, filesystem, or unrestricted Android APIs.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN a priority conflict between security or determinism on one side and model convenience or performance on the other, WHEN a decision is made, THEN security and determinism always win.
- GIVEN the repository during Phase 0, WHEN any toolchain artifact (Gradle/JDK/SDK/APK/build file) appears before ADR-0005 is recorded, THEN the blueprint is non-conforming and Phase 0 fails its definition of done.
- GIVEN a new feature request, WHEN no specification and no approved plan exist for it, THEN implementation is blocked until the missing specification and plan are created and approved.
- GIVEN a user instruction that the model maps to a request, WHEN the runtime processes it, THEN the only possible execution target is a registered tool via the ToolRegistry and policy chain.

## Architecture

The system is a pipeline of responsibility-separated layers:

```
UI
 ↓
ViewModel
 ↓
AgentRuntime
 ↓
ModelAdapter
 ↓
ToolRegistry
 ↓
ToolPolicy
 ↓
Android Tools
```

- UI must not execute tools directly.
- Android APIs must not be called from model adapters.
- Model adapters must not contain tool implementations.
- Tool implementations must not contain UI logic.
- The model can only request registered tools.

Phase 0 produces the engineering blueprint for this architecture; Phase 1 (see `docs/plans/phase-1-implementation-plan.md`) introduces the toolchain and implements it.

## Data Contracts

- No executable data contracts exist in Phase 0. Contracts are defined inline in the Kotlin API sketches within specifications 0002–0009 and codified as design artifacts in the design record.
- The full container/module boundary is defined in `docs/plans/phase-1-implementation-plan.md`.

## Security

- Treat every model output as untrusted input. Model output must never be interpreted as authorization.
- Authorization belongs to deterministic application code (ToolRegistry + ToolPolicy + ConfirmationGate).
- The forbidden model surfaces are: model → arbitrary Kotlin code, model → shell, model → arbitrary Intent, model → reflection, model → filesystem access, model → unrestricted Android APIs. See ADR-0003.
- Every tool carries a security classification (SAFE, CONFIRMATION_REQUIRED, FORBIDDEN). See spec 0006.

## Error Handling

- Phase 0 is a documentation phase; the only failure mode is blueprint non-conformance (missing sections, contract drift, or toolchain artifacts).
- Cross-document contract consistency is enforced at review: every spec MUST use the shared contracts verbatim from the design record (spec 0004 and the module map in the Phase 1 plan).
- If any Phase 0 deliverable deviates from the AGENTS.md charter, the deviation MUST be recorded in an ADR rather than silently changed.

## Acceptance Criteria

- AC1. The repository contains a complete system architecture describing the full pipeline. [DoD item 1]
- AC2. SDD specifications exist for every capability. [DoD item 2]
- AC3. The model abstraction is specified and model-independent (Needle is a plug-in, not the application). [DoD items 3]
- AC4. The tool contract, tool registry specification, and security policy are specified. [DoD items 4–6]
- AC5. Initial Android tool specifications exist (spec 0007). [DoD item 7]
- AC6. The agent execution flow (single-shot state machine) is specified. [DoD item 8]
- AC7. Evaluation methodology and test strategy exist. [DoD items 9–10]
- AC8. ADRs exist for important architectural decisions. [DoD item 11]
- AC9. The Phase 1 implementation plan exists and is gated by ADR-0005. [DoD item 12]
- AC10. There is NO Gradle, NO Java/JDK, NO Android SDK, NO APK, and NO Android build anywhere in the repository.

## Verification

- V1. Run `git status` (or list the tree if not a git repo) and assert no toolchain artifacts (`build.gradle*`, `settings.gradle*`, `*.kt` sources, `gradle/`, `.gradle/`, `*.apk`) exist.
- V2. Verify every file under `docs/specs/`, `docs/decisions/`, and `docs/plans/` in this repository conforms to its template (11 sections for specs; Status/Context/Decision/Consequences for ADRs).
- V3. Grep the specs for the shared contract identifiers (`AgentModel`, `ModelResponse`, `ToolDefinition`, `ToolCall`, `ToolResult`, `ToolRegistry`, `SecurityClassifier`, `ConfirmationGate`, `ToolPolicy`) and confirm they match the design record verbatim (no drift).
- V4. Grep `docs/specs/` and `docs/plans/` for toolchain-era tokens (`gradle`, `build.gradle`, `settings.gradle`, `.kt`, `apk`): the only permitted occurrences are explicit prohibitions or ADR-0005 gate references. Assert every requirement from F1–F6 is present in spec 0001.
- V5. Confirm the definition of done checklist (`docs/plans/phase-0-definition-of-done.md`) shows all 12 items complete.