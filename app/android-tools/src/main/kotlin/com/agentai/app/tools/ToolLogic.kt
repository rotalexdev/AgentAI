package com.agentai.app.tools

import com.agentai.core.contract.JsonSchemaType
import com.agentai.core.contract.ToolDefinition
import com.agentai.core.security.PiiExposure
import com.agentai.core.security.SideEffect

/**
 * Pure, JVM-testable logic for tools (spec 0007 T2). The PII allowlist is the
 * ONLY device info fields a tool may return: model, manufacturer, OS release,
 * API level. Anything else is out of scope by design.
 */
object DeviceInfoFields {
    val ALLOWLISTED_FIELDS = setOf("model", "manufacturer", "os_release", "api_level")

    fun isAllowlisted(field: String): Boolean = field in ALLOWLISTED_FIELDS
}

/**
 * Pure brightness bounds (spec 0007 T3): integer 0..100, no coercion.
 */
object BrightnessBounds {
    const val MIN = 0
    const val MAX = 100

    /** Android SCREEN_BRIGHTNESS is 0..255; map the 0..100 contract linearly. */
    const val SCREEN_MAX = 255

    fun isValid(value: Int): Boolean = value in MIN..MAX

    fun toScreenScale(value: Int): Int =
        if (!isValid(value)) -1
        else (value / 100f * SCREEN_MAX).toInt().coerceIn(0, SCREEN_MAX)
}

/**
 * Pure volume bounds: integer 0..100, no coercion.
 */
object VolumeBounds {
    const val MIN = 0
    const val MAX = 100

    fun isValid(value: Int): Boolean = value in MIN..MAX
}

/**
 * Pure open_app allowlist (spec 0007 T5): canonical key → package name.
 * An empty allowlist means the tool is FORBIDDEN (cannot launch anything).
 * The model can only reference canonical keys; it can never supply a raw
 * package name or Intent extras.
 */
class OpenAppAllowlist(
    private val entries: Map<String, String>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Canonical keys the model may reference (schema enum source). */
    val keys: Set<String> get() = entries.keys

    fun canonicalKey(packageName: String): String? =
        entries.entries.firstOrNull { it.value == packageName }?.key

    fun packageFor(key: String): String? = entries[key]

    /** True when the model-supplied key exists in the allowlist. */
    fun isAllowed(key: String): Boolean = key in entries
}