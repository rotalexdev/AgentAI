package com.agentai.core.security

/**
 * Security classification for a tool (spec 0006 S2, design D3).
 *
 * Derived by rules from declared side effects and PII exposure —
 * never from free-form labels.
 */
enum class SecurityClassification {
    /** No side effect, no PII exposure (e.g. get_current_time, get_battery_status). */
    SAFE,

    /** Any side effect, system mutation, or action leaving the app. */
    CONFIRMATION_REQUIRED,

    /** Shell, code execution, credentials, filesystem, reflection, arbitrary Intents. Cannot be registered. */
    FORBIDDEN,
}