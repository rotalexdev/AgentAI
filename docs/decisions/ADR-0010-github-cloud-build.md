# ADR-0010 — GitHub Cloud Build + APK Download Workflow

## Context

The repository currently exists locally at `/root/dev/kotlin/AgentAI` with Phase 0 constraints prohibiting local toolchain installation (no Gradle, JDK, Android SDK, APK builds). The project already has GitHub Actions workflow infrastructure (`/.github/workflows/android-build.yml`) and a download script (`/scripts/get-apk.sh`). The team decided to document and formalize the process for pushing the repo to GitHub, triggering the cloud build, and downloading the resulting APK without local compilation.

## Decision

Use a **GitHub cloud-based build workflow** for Android compilation and APK generation, followed by artifact download via the existing `scripts/get-apk.sh`. The process operates entirely in the cloud (GitHub Actions) and requires no local installation of Java, JDK, Gradle, or Android SDK during Phase 0. The `android-build.yml` workflow and `get-apk.sh` script are existing artifacts that this ADR formalizes the usage of.

## Alternatives Considered

| Option | Description | Pros | Cons |
|---|---|---|---|
| **Local Gradle build** | Install JDK, Gradle, Android SDK locally; run `./gradlew assembleRelease` | Full control over build process; no external dependencies | Violates Phase 0 constraints (F4 MUST NOT); requires toolchain installation; not portable across environments |
| **Manual APK provisioning** | Obtain APK from external source or prior build | No build required | Does not incorporate latest code changes; not reproducible |
| **GitHub Actions cloud build** (chosen) | Push repo to GitHub; trigger `android-build.yml` workflow; download artifact | Phase 0 compliant; leverages existing infrastructure; reproducible; zero local toolchain | Requires GitHub remote and authenticated `gh` CLI; depends on workflow success |

## Consequences

### Positive

- **Phase 0 compliance**: No local Gradle, JDK, Android SDK, or APK builds are performed. The repo can remain in a clean state with no toolchain artifacts.
- **Leverages existing infrastructure**: Reuses the already-configured `/.github/workflows/android-build.yml` and `/scripts/get-apk.sh` — no new tooling required.
- **Deterministic**: The workflow runs on `main` branch with `push` and `workflow_dispatch` triggers; the APK artifact is a deterministic output of the build configuration.
- **Scalable**: The GitHub Actions infrastructure scales; build time does not depend on local machine capabilities.

### Negative

- **Network dependency**: Requires internet access and GitHub authentication (`gh auth login`).
- **Workflow execution time**: Builds take up to 90 minutes (configured timeout in `android-build.yml`); the process is not instantaneous.
- **Artifact availability**: The APK is uploaded as a GitHub Actions artifact with default retention; manual cleanup may be needed.
- **Limited inspection**: The binary APK cannot be inspected for intermediate build artifacts or debug symbols without the source-level build output.

## Related Artifacts

- `docs/specs/0010-github-ci-apk.md` — process specification this ADR formalizes.
- `docs/specs/0011-github-ci-apk-tasks.md` — task breakdown for implementing the process.
- `/.github/workflows/android-build.yml` — the GitHub Action workflow (existing).
- `/scripts/get-apk.sh` — the APK download script (existing).
- `ADR-0005-toolchain-introduction-gate.md` — the gate that must be passed before transitioning from Phase 0 to Phase 1 (local toolchain introduction).

## Status

Proposed; under review. To be finalized when the team confirms the GitHub remote URL and validates the workflow-trigger + download process end-to-end.

## Decision ID

ADR-0010

## Related Requirements

- F4 (RFC 2119 — MUST NOT): Phase 0 MUST NOT introduce any toolchain artifact. This ADR ensures compliance by documenting a zero-local-toolchain process.
- F5 (RFC 2119 — SHALL): Toolchain introduction SHALL be gated by ADR-0005 before any Phase 1 build begins. This ADR operates within Phase 0 constraints and does not trigger the Phase 1 gate.
- F6 (RFC 2119 — MUST): The complete system MUST follow the core flow: User → AgentRuntime → ModelAdapter → Structured ToolCall → ToolRegistry → Argument Validation → Security/Permission Policy → Android Tool → ToolResult → AgentResponse. This ADR addresses the build/deployment aspect external to the runtime pipeline.