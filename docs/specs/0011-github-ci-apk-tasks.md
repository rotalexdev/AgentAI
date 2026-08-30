# 0011 — GitHub CI + APK Download: Implementation Tasks

## Status

Draft; task breakdown for the GitHub + CI + APK process specified in 0010.

## Context

This task breakdown implements the process documented in spec 0010-github-ci-apk.md. The specification describes a three-stage pipeline for uploading a local repository to GitHub, triggering the `android-build` workflow, and downloading the resulting APK from GitHub Actions artifacts. This task breakdown decomposes each stage into concrete, testable tasks.

## Goals

- T1. Decompose the GitHub upload process into discrete tasks with clear entry/exit conditions.
- T2. Decompose the workflow trigger + monitoring process into tasks.
- T3. Decompose the APK download process into tasks that can be verified independently.
- T4. Ensure all tasks are consistent with Phase 0 constraints (no local toolchain, no Gradle/JDK/SDK installation).

## Non-goals

- Building APKs locally.
- Installing JDK, Gradle, or Android SDK.
- Modifying the GitHub Actions workflow or the download script.

## Requirements

- R1 (SHALL): Each task must have a clear description, expected result, and verification method.
- R2 (SHALL): Tasks must be ordered sequentially, with dependencies explicitly stated.
- R3 (SHOULD): Each task should be independently verifiable (can be checked without executing the full pipeline).
- R4 (MUST): No task may require local installation of Java, JDK, Gradle, or Android SDK.

### Task Breakdown

#### Stage 1: Push Local Repo to GitHub Remote

| Task ID | Description | Expected Result | Verification |
|---------|-------------|----------------|--------------|
| T1.1 | Execute `git remote add origin <github-url>` | Remote "origin" is added to local repo; URL is configurable | `git remote -v` shows the configured URL |
| T1.2 | Execute `git branch -M main` | Local branch is renamed to `main` (if not already) | `git branch` shows `*` at `main` |
| T1.3 | Execute `git push -u origin main` | Repo is pushed to GitHub remote; upstream tracking is set | `git status` shows no unpushed commits; GitHub shows `main` branch |

#### Stage 2: Trigger and Monitor `android-build` Workflow

| Task ID | Description | Expected Result | Verification |
|---------|-------------|----------------|--------------|
| T2.1 | List workflow runs: `gh run list --repo <owner>/<repo> --workflow android-build.yml --branch main` | Returns a list of runs with IDs, statuses, and branch information | Output contains at least one run entry |
| T2.2 | Filter for most recent successful run: status=success, branch=main | Identifies the latest run with status `success` on branch `main` | The run has `conclusion: success` and `head_branch: main` |
| T2.3 | Verify run status is `success` | Confirms the workflow completed successfully | `gh run view <run-id> --json conclusion` returns `conclusion: success` |

#### Stage 3: Download APK from GitHub Actions Artifact

| Task ID | Description | Expected Result | Verification |
|---------|-------------|----------------|--------------|
| T3.1 | Invoke `./scripts/get-apk.sh` with default parameters | Script runs without errors; APK is downloaded | Exit code is 0; an `.apk` file exists in the destination directory |
| T3.2 | Invoke `./scripts/get-apk.sh arm64 <dest-dir>` with custom flavor | Script downloads the `arm64` APK to the specified directory | Exit code is 0; `<dest-dir>/arm64/` contains the APK file |
| T3.3 | Verify downloaded APK name matches expected pattern | Confirms the correct APK was downloaded | `basename <apk-file>` matches `app-arm64-release.apk` or `app-release.apk` |

## Architecture

The tasks form a linear dependency graph:

```
T1.1 → T1.2 → T1.3 → T2.1 → T2.2 → T2.3 → T3.1 → T3.2 → T3.3
```

Each task depends on the successful completion of the previous task. If any task fails, the pipeline stops and the error must be resolved before proceeding.

Dependencies:
- Stage 1 (T1.1–T1.3) depends on having a local Git repo and GitHub remote URL configured.
- Stage 2 (T2.1–T2.3) depends on Stage 1 completing successfully (repo pushed to GitHub).
- Stage 3 (T3.1–T3.3) depends on Stage 2 completing successfully (workflow run found with status success).

## Data Contracts

- **Git remote URL**: Must be in `https://github.com/<owner>/<repo>.git` format, or `git@github.com:<owner>/<repo>.git` format.
- **Workflow name**: Must be `android-build.yml` (matches the file in `/.github/workflows/`).
- **APK artifact name**: The workflow must produce an artifact named `apk` (as defined in `android-build.yml`).
- **Script parameters**: `get-apk.sh` accepts `REPO`, `WORKFLOW`, `FLAVOR`, `DEST_DIR` env vars or positional args.

## Security

- No secrets are handled by these tasks (secrets are managed via GitHub Secrets in the workflow).
- The `gh` CLI must be authenticated (`gh auth status`); tasks assume this condition is met.
- Downloaded APKs are binary artifacts from CI; verification of signatures is outside the scope of these tasks.

## Error Handling

| Failure | Handling |
|---|---|
| T1.1 fails (remote already exists) | Use `git remote set-url origin <new-url>` or remove remote first: `git remote remove origin` |
| T1.3 fails (push rejected) | `git pull --rebase` then `git push -u origin main` |
| T2.2 fails (no successful run) | Trigger the workflow: `gh run workflow android-build.yml -F branch=main`; then re-run T2.2 |
| T3.1 fails (gh not authenticated) | `gh auth login` and re-run the script |
| T3.3 fails (no APK artifact) | Verify `android-build.yml` upload step; ensure the build produced release APKs |

## Acceptance Criteria

- AC1. All tasks T1.1 through T3.3 complete successfully (exit code 0 for each).
- AC2. The repo is pushed to GitHub on `main` and the `android-build` workflow triggers.
- AC3. The workflow completes with status `success`.
- AC4. The APK is downloaded to the specified destination without errors.
- AC5. No local Gradle, JDK, or Android SDK installation occurs during task execution.

## Verification

- V1. `git status` shows clean state; `git remote -v` shows GitHub URL.
- V2. `gh run list --repo <owner>/<repo> --workflow android-build.yml --branch main` shows a recent successful run.
- V3. `./scripts/get-apk.sh` completes successfully and places an `.apk` file in the destination directory.
- V4. The downloaded APK name matches the expected flavor pattern.
- V5. `git grep -i "gradle\|build.gradle\|jdk\|android-sdk"` returns zero matches in the repo root (confirming no local toolchain artifacts were created).

## Related Artifacts

- `docs/specs/0010-github-ci-apk.md` — the process specification this task breakdown implements.
- `/.github/workflows/android-build.yml` — the GitHub Action workflow (already exists).
- `/scripts/get-apk.sh` — the APK download script (already exists).
- `ADR-0005-toolchain-introduction-gate.md` — gate for transitioning from Phase 0 to Phase 1.