# 0010 — GitHub CI + APK Download Process

## Status

Draft; process specification for Phase 1 pipeline integration.

## Context

The repository already has GitHub Actions workflow infrastructure (`/.github/workflows/android-build.yml`) and a download script (`/scripts/get-apk.sh`). This specification documents the process for: (1) uploading the repo to GitHub, (2) triggering the build workflow, and (3) downloading the resulting APK. This enables the cloud-based build cycle without local toolchain usage, consistent with Phase 0 constraints (no local Gradle/JDK/APK build).

## Goals

- G1. Document the exact steps to push the local repo to GitHub remote and trigger the `android-build` workflow.
- G2. Document the steps to download the compiled APK from the successful workflow run using the existing `scripts/get-apk.sh`.
- G3. Ensure the process does not require local installation of Java, JDK, Gradle, or Android SDK.
- G4. Align with the existing `android-build.yml` workflow and `get-apk.sh` script contracts.

## Non-goals

- Building APKs locally.
- Installing JDK, Gradle, or Android SDK on the local machine.
- Modifying the GitHub Actions workflow or the download script (those are separate change requests).

## Requirements

- R1 (MUST): The local repo must be pushed to a GitHub remote on branch `main` with push triggering enabled for the `android-build` workflow.
- R2 (MUST): The `android-build` workflow must execute successfully (status: success) on the `main` branch.
- R3 (MUST): After a successful run, `scripts/get-apk.sh` must be invocable to download the APK from GitHub Actions artifacts.
- R4 (SHOULD): The `get-apk.sh` script parameters respect the repository name, workflow name, and default flavor (`arm64`), unless overridden.
- R5 (SHALL): No local toolchain artifacts (Gradle files, JDK, Android SDK, APKs) are created or required during this process.

### R1 Details — Push to GitHub

| Step | Command | Description |
|------|---------|-------------|
| 1 | `git remote add origin <github-url>` | Add the GitHub remote repository URL. |
| 2 | `git branch -M main` | Ensure local branch is `main`. |
| 3 | `git push -u origin main` | Push the repo to GitHub, tracking the remote `main`. |

### R2 Details — Trigger and Monitor Workflow

| Step | Command | Description |
|------|---------|-------------|
| 1 | `gh run workflow android-build.yml -F branch=main` | Trigger the workflow manually from the command line (optional). |
| 2 | `gh run list --repo <owner>/<repo> --workflow android-build.yml --branch main` | List runs; find the most recent successful one. |
| 3 | Verify the run status is `success` before proceeding. |

### R3 Details — Download APK

| Step | Parameter | Description |
|------|-----------|-------------|
| 1 | `REPO` | GitHub repository in `owner/repo` format (or set via `NOTESCRIBE_REPO` env). |
| 2 | `WORKFLOW` | `android-build.yml` (default, matches the workflow file name). |
| 3 | `FLAVOR` | `arm64` (default), `all`, or a specific flavor name to filter the APK. |
| 4 | `DEST_DIR` | Destination directory for the downloaded APK (defaults to `$HOME/<repo>-builds`). |
| Ejemplo: | `./scripts/get-apk.sh arm64 ~/my-apks` | Download the `arm64` APK to `~/my-apks`. |

## Architecture

The process flows as a three-stage pipeline:

```
Local Repo  ── git push ──► GitHub Remote
      │                     │
      ▼                     ▼
  gh run list        android-build.yml workflow
      │                     │
      ▼                     ▼
  Successful run    │ APK artifact uploaded
      │                     │
      └──────┬───────────────┘
             ▼
    scripts/get-apk.sh download
```

Dependencies: the process depends on the `android-build.yml` workflow definition and the `get-apk.sh` script. No local build tools are involved.

## Data Contracts

- **Workflow**: `android-build.yml` must produce an artifact named `apk` containing `app/build/outputs/apk/*/release/*.apk`.
- **Download script**: `get-apk.sh` accepts `REPO`, `WORKFLOW`, `FLAVOR`, `DEST_DIR` parameters (environment variables or positional args).
- **APK artifact**: Must match the pattern `app-$FLAVOR-release.apk` or `app-release.apk` (default flavor).

## Security

- Model/output untrusted: the downloaded APK is a binary artifact from CI; verify signatures if required (not covered by this spec).
- The `get-apk.sh` script uses `gh auth status` to ensure the CLI is authenticated; do not run with expired credentials.
- No secret exposure: the script does not handle signing keys; those are managed via GitHub Secrets in the workflow.

## Error Handling

| Failure | Handling |
|---|---|
| Push rejected (e.g., non-fast-forward) | `git pull --rebase` then `git push -u origin main` |
| No successful workflow run | Verify workflow syntax; check GitHub Actions logs; ensure secrets are set. |
| No APK artifact found | Verify `android-build.yml` upload step; check that the build produced release APKs. |
| `get-apk.sh` fails (gh not authenticated) | `gh auth login` and re-run the script. |

## Acceptance Criteria

- AC1. The local repo is pushed to GitHub remote on `main` and the push completes without errors.
- AC2. The `android-build` workflow triggers and completes with status `success` on the `main` branch.
- AC3. After a successful run, `scripts/get-apk.sh` downloads an APK to the specified destination without errors.
- AC4. The downloaded APK matches the expected flavor (`arm64` by default).
- AC5. No local Gradle, JDK, or Android SDK installation occurs during the entire process.

## Verification

- V1. `git status` shows clean state; `git remote -v` shows the GitHub URL.
- V2. `gh run list --repo <owner>/<repo> --workflow android-build.yml --branch main` shows a recent successful run.
- V3. `./scripts/get-apk.sh` completes successfully and places an `.apk` file in the destination directory.
- V4. Grep the repo for `gradle`, `build.gradle`, `jdk`, `android-` SDK paths: zero matches (confirming no local toolchain involvement).
- V5. Compare the downloaded APK name against the flavor specified in `get-apk.sh`.

## Related Artifacts

- `/.github/workflows/android-build.yml` — the GitHub Action workflow (already exists).
- `/scripts/get-apk.sh` — the APK download script (already exists).
- `opencode.json` — MCP klibs configuration (already exists).
- `ADR-0005-toolchain-introduction-gate.md` — gate for transitioning from Phase 0 to Phase 1.