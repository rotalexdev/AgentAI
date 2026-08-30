# 0007 — Android Tools: Initial Tool Set

## Status

Approved (blueprint; implementation scheduled for Phase 1).

## Context

The Android tools are the deterministic, permission-aware implementations the model can request. Each tool has a full specification: purpose, description, parameters, return value, validation rules, Android API used, permissions, security classification, confirmation requirements, failure modes, and acceptance criteria. Six tools constitute the initial set, covering SAFE queries and CONFIRMATION_REQUIRED mutations. The registry (spec 0005) and policy chain (spec 0006) gate all of them.

## Goals

- T1. `get_current_time` and `get_battery_status` SAFE.
- T2. `get_device_info` SAFE with PII allowlist (model, manufacturer, OS release, API level only).
- T3. `set_brightness` CONFIRMATION_REQUIRED + `WRITE_SETTINGS` preflight; `value` Int 0..100.
- T4. `set_volume` CONFIRMATION_REQUIRED + `MODIFY_AUDIO_SETTINGS` preflight.
- T5. `open_app` CONFIRMATION_REQUIRED + canonical-key allowlist + indirect Intent (no model extras); fallback FORBIDDEN when allowlist is empty; no arbitrary package launch.
- T6. Every tool produces a structured `ToolResult`; all failure modes map to `ToolError`.

## Non-goals

- Tools that grant unrestricted filesystem, shell, code execution, reflection, credential access, or arbitrary Intent launch.
- Model-supplied actions/enumeration beyond allowlisted canonical keys.
- A mutable, runtime-editable tool allowlist (allowlist is a compile-time application constant).

## Requirements

- T1 (RFC 2119 — SHALL): `get_current_time`: SAFE. Returns current local/UTC time. `get_battery_status`: SAFE. Returns battery level/status from an Android battery broadcast.
- T2 (RFC 2119 — SHALL): `get_device_info`: SAFE. Returns ONLY the PII allowlist fields: `model`, `manufacturer`, `os_release`, `api_level`. Never Android ID, serial, IMEI, MAC, or other identifiers.
- T3 (RFC 2119 — SHALL): `set_brightness`: CONFIRMATION_REQUIRED; permission preflight `WRITE_SETTINGS`; parameter `value` Int 0..100; returns success or `PERMISSION_DENIED`/`EXECUTION_ERROR`.
- T4 (RFC 2119 — SHALL): `set_volume`: CONFIRMATION_REQUIRED; permission preflight `MODIFY_AUDIO_SETTINGS`; sets stream volume; returns success or `PERMISSION_DENIED`/`EXECUTION_ERROR`.
- T5 (RFC 2119 — SHALL): `open_app`: CONFIRMATION_REQUIRED. Argument `app_key` is a canonical key from a compile-time application allowlist that maps deterministically to a single package + explicit Intent. No model-supplied extras, package names, or URIs. When the allowlist is empty, the tool's effective classification is FORBIDDEN (cannot register).
- T6 (RFC 2119 — SHALL): All tools **SHALL** produce `ToolResult.Success | Failure`, using only the enumerated `ToolError` codes.

### Scenarios (GIVEN/WHEN/THEN)

- GIVEN a canonical allowlist key for a known app, WHEN `open_app` is approved, THEN that single allowlisted package is launched via indirect Intent (canonical key → deterministic Intent).
- GIVEN a non-allowlisted key or any model-supplied extras/package/URI, WHEN `open_app` executes, THEN the request is denied / returns `Failure(EXECUTION_ERROR)`.
- GIVEN `get_device_info`, WHEN executed, THEN the result exposes exactly the four allowlisted PII fields and nothing else.

## Architecture

```
app:android-tools
  GetCurrentTimeTool     (SAFE)             — uses android.app… time APIs, no permissions
  GetBatteryStatusTool   (SAFE)             — ACTION_BATTERY_CHANGED reference
  GetDeviceInfoTool      (SAFE)             — Build.MODEL/MANUFACTURER, Build.VERSION.RELEASE, SDK_INT
  SetBrightnessTool      (CONFIRMATION_REQUIRED; WRITE_SETTINGS) — Settings.System
  SetVolumeTool          (CONFIRMATION_REQUIRED; MODIFY_AUDIO_SETTINGS) — AudioManager
  OpenAppTool            (CONFIRMATION_REQUIRED; allowlist; empty ⇒ FORBIDDEN) — Intent launching

Registered at app composition into ToolRegistry; every tool implements core:tool-contract `Tool`.
```

Tool/API bindings:

| Tool | Classification | Android API | Permission | Confirmation |
|---|---|---|---|---|
| `get_current_time` | SAFE | `java.time` / system clock | none | no |
| `get_battery_status` | SAFE | battery broadcast reference | none | no |
| `get_device_info` | SAFE | `Build` + `Build.VERSION` | none | no |
| `set_brightness` | CONFIRMATION_REQUIRED | `Settings.System.SCREEN_BRIGHTNESS` | `WRITE_SETTINGS` | yes |
| `set_volume` | CONFIRMATION_REQUIRED | `AudioManager.setStreamVolume` | `MODIFY_AUDIO_SETTINGS` | yes |
| `open_app` | CONFIRMATION_REQUIRED (FORBIDDEN if allowlist empty) | `Intent` with explicit package | none (allowlist only) | yes |

## Data Contracts

```kotlin
// parameters per tool (JSON Schema subset, spec 0004)
get_current_time:      {}
get_battery_status:    {}
get_device_info:       {}
set_brightness:        { "value": IntegerType(0, 100) }  required=[value]
set_volume:            { "stream": StringType(enum=[ALARM,AUDIO_VOICE_CALL,BELL,MUSIC,NOTIFICATION,SYSTEM,RING,DTMF,ACCESSIBILITY]), "value": IntegerType(0,100) }
open_app:              { "app_key": StringType(enum=<allowlist keys>) }  required=[app_key]
```

## Security

- PII allowlist: `get_device_info` returns only `model`, `manufacturer`, `os_release`, `api_level`. Identifier-grade data (Android ID, serial, IMEI, MAC, advertising ID) is never exposed.
- `open_app` indirect launch: canonical key → single deterministic package + Intent; model cannot supply package, URI, extras, or flags; empty allowlist forces FORBIDDEN.
- System mutations (`set_brightness`, `set_volume`) require runtime permission preflight AND explicit confirmation per spec 0006.
- Never shell, never code exec, never reflection, never raw filesystem.

## Error Handling

| Failure | Handling |
|---|---|
| Missing `WRITE_SETTINGS`/`MODIFY_AUDIO_SETTINGS` | `Failure(PERMISSION_DENIED)`. |
| Confirmation not granted | `Failure(CONFIRMATION_REQUIRED)`. |
| `set_brightness` value out of 0..100 | `Failure(MALFORMED_ARGUMENTS)` from validator. |
| `set_volume` stream not in enum | `Failure(MALFORMED_ARGUMENTS)`. |
| `open_app` non-allowlisted key / extras / URI | `Failure(EXECUTION_ERROR)` / denied. |
| Volume/brightness write throws (e.g. permission revoked) | `Failure(EXECUTION_ERROR)`. |
| Battery/time source unavailable | `Failure(EXECUTION_ERROR)` with descriptive message. |

## Acceptance Criteria

- AC1. `get_current_time` and `get_battery_status` execute with no confirmation and return structured success.
- AC2. `get_device_info` returns only the four allowlisted fields; no identifier values.
- AC3. `set_brightness` requires confirmation + `WRITE_SETTINGS`; `value` bounds enforced (0..100).
- AC4. `set_volume` requires confirmation + `MODIFY_AUDIO_SETTINGS`.
- AC5. `open_app` launches only allowlisted packages via indirect Intent; empty allowlist ⇒ FORBIDDEN (unregisterable).
- AC6. Every failure mode maps to a `ToolError`; no tool throws.
- AC7. No tool exposes shell, code execution, reflection, filesystem, or arbitrary-Intent capability.

## Verification

- V1. JVM unit tests on validation and non-Android logic (parameter bounds, allowlist mapping determinism).
- V2. Instrumented androidTest (Phase 1) on an emulator: SAFE tools, permission preflight denials, confirmation dialog flow, `open_app` allowlist-only launches, PII allowlist assertion.
- V3. Static security scan of `app:android-tools`: no `Runtime.exec`, no reflection (`Class.forName`), no raw file writes, no `PackageManager.getLaunchIntentForPackage` on model input.
- V4. Grep: `get_device_info` code path cannot reference `Settings.Secure.ANDROID_ID`, `Build.SERIAL`, telephony IMEI, or MAC.