# Phase 0 — Definition of Done Checklist

## Status

Draft — this checklist is the Phase 0 completion gate. All items must be checked before Phase 1 Step 0 (ADR-0005 toolchain gate) may begin.

## Zero-Toolchain Gate

The repository MUST contain NO toolchain artifact before Phase 1 Step 0:

- [ ] No Gradle files (`build.gradle*`, `settings.gradle*`, `gradle/`, `.gradle/`)
- [ ] No Java/JDK installation or requirement
- [ ] No Android SDK or Android Studio project files
- [ ] No APK or build outputs
- [ ] No Kotlin/Java source files (`.kt`, `.java`) — inline Kotlin API sketches inside markdown are permitted
- [ ] No Android build tools

## Definition of Done Items (AGENTS.md)

| # | DoD item | Evidence file | Check |
|---|----------|---------------|-------|
| 1 | Complete system architecture | docs/specs/0001-project-foundation.md (F6, Architecture) + docs/plans/phase-1-implementation-plan.md (module map) | [ ] |
| 2 | SDD specifications | docs/specs/0001–0009 | [ ] |
| 3 | Model abstraction | docs/specs/0003-model-adapter.md + docs/decisions/ADR-0002-model-abstraction.md | [ ] |
| 4 | Tool contract | docs/specs/0004-tool-contract.md | [ ] |
| 5 | Tool registry specification | docs/specs/0005-tool-registry.md | [ ] |
| 6 | Security policy | docs/specs/0006-security-policy.md + docs/decisions/ADR-0004-security-classification.md | [ ] |
| 7 | Initial Android tool specifications | docs/specs/0007-android-tools.md | [ ] |
| 8 | Agent execution flow | docs/specs/0002-agent-runtime.md | [ ] |
| 9 | Evaluation methodology | docs/specs/0009-model-evaluation.md | [ ] |
| 10 | Test strategy | docs/specs/0001 (Verification) + docs/plans/phase-1-implementation-plan.md (Steps 1–9 tests) | [ ] |
| 11 | ADRs for important architectural decisions | docs/decisions/ADR-0001..0005 | [ ] |
| 12 | Implementation plan for Phase 1 | docs/plans/phase-1-implementation-plan.md | [ ] |

## Verification

- [ ] V1. Every spec under docs/specs/ conforms to the 11-section template (Status, Context, Goals, Non-goals, Requirements, Architecture, Data Contracts, Security, Error Handling, Acceptance Criteria, Verification).
- [ ] V2. Every ADR under docs/decisions/ uses Status/Context/Decision/Consequences and cites its design decision(s).
- [ ] V3. Shared contract identifiers (`AgentModel`, `ModelResponse`, `ToolDefinition`, `ToolCall`, `ToolResult`, `ToolRegistry`, `SecurityClassifier`, `ConfirmationGate`, `ToolPolicy`) match the design record verbatim — no drift.
- [ ] V4. `git status` (or tree listing if not a git repo) shows no toolchain artifacts; the only toolchain tokens in docs are explicit prohibitions or ADR-0005 gate references.
- [ ] V5. ADR-0005 is Approved and cited by spec 0001 (F5).
- [ ] V6. The evaluation dataset is specified (120 records, 12×10 categories, fixed seed) and results are stored separately (spec 0009).

## Completion Rule

Phase 0 is complete when all checklist items above are checked AND the zero-toolchain gate holds. At that point Phase 1 Step 0 may begin (toolchain introduction + strict TDD flip per ADR-0005).