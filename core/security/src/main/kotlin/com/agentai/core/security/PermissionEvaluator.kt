package com.agentai.core.security

/**
 * Android runtime permissions a tool may require (spec 0007 preflight).
 * Deterministic, typed — never strings.
 */
enum class AndroidPermission {
    WRITE_SETTINGS,
    MODIFY_AUDIO_SETTINGS,
}

/**
 * Platform permission evaluator (spec 0006 S3). Injected so the security
 * core remains JVM-testable without Android APIs.
 */
fun interface PermissionEvaluator {
    fun hasPermission(permission: AndroidPermission): Boolean
}