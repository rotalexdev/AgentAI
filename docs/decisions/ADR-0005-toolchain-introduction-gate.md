# ADR-0005 — Toolchain Introduction Gate

## Status

Approved.

## Context

The project is in Phase 0 — ARCHITECTURE AND SDD. The AGENTS.md charter explicitly forbids installing or requiring any toolchain (Java/JDK, Gradle, Android SDK, Android Studio, Kotlin compiler) and forbids building an APK or generating a Gradle project. The only allowed outputs are specifications, architecture, contracts, schemas, policies, test cases, evaluation datasets, ADRs, documentation, and implementation plans.

Phase 1 must eventually introduce the Android toolchain to implement the blueprint. That introduction is a one-way, high-impact architectural change: it brings build configuration, dependency management, and test infrastructure into the repository. Without an explicit gate, toolchain artifacts could leak into Phase 0 or be introduced ad hoc, violating the charter and the definition of done.

Decision priority (AGENTS.md): Security and determinism precede everything; the toolchain gate protects the Phase 0 blueprint's integrity and the SDD-only development rule.

## Decision

Toolchain introduction SHALL be gated by this ADR. The gate is passed at **Phase 1 Step 0** of `docs/plans/phase-1-implementation-plan.md`, and only then may Gradle, the Android SDK, the Kotlin compiler, and build files be introduced.

Gate conditions, all of which MUST hold before any toolchain artifact appears:

1. The complete Phase 0 blueprint is committed (all specs 0001–0009, ADRs 0001–0005, Phase 1 plan, DoD checklist) and the Phase 0 definition of done passes with the zero-toolchain gate.
2. This ADR is recorded with Status: Approved and cited by spec 0001 (F5).
3. The Phase 1 Step 0 plan is reviewed and approved; it defines the minimal first toolchain footprint (project skeleton, minimal dependencies, no Hilt/Koin by default).

Strict TDD flip condition: SDD strict TDD mode was resolved disabled during Phase 0 because no test runner exists (no toolchain). The flip trigger is explicit and deterministic — **strict_tdd flips to true when a test runner exists in Phase 1 Step 0**. The Phase 1 Step 0 deliverable includes a working test runner (e.g. JUnit via Gradle) that can execute the JVM-testable pure-Kotlin core; once that runner exists and the first RED test is written, strict TDD mode is active for all subsequent Phase 1 work.

## Consequences

- No Gradle, JDK, Android SDK, APK, or build artifact may exist in the repository before Phase 1 Step 0 — a zero-toolchain gate enforced by the Phase 0 DoD checklist.
- Phase 1 begins with a controlled, minimal toolchain introduction and strict TDD activation in the same step.
- The JVM-testable pure-Kotlin core (tool-contract, tool-registry, security, model-adapter with MockModel) is designed so that its first tests run immediately once the runner exists.
- Any deviation from this gate is a charter violation and must be recorded rather than silently introduced.