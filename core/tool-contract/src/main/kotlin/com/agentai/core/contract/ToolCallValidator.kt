package com.agentai.core.contract

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Validates [ToolCall.arguments] against a [ToolDefinition] schema
 * (spec 0004 C2/C3). Validation requires EXACT types — no coercion.
 *
 * Rules:
 * - Unknown properties are rejected (`additionalProperties: false`).
 * - Missing required properties are rejected.
 * - Type mismatches (e.g. `"value":"50"` as string against IntegerType) are rejected.
 * - Bounds (integer/number min/max) and string enums are enforced.
 * - Arrays allow primitive items only.
 */
object ToolCallValidator {

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(call: ToolCall, definition: ToolDefinition): Result {
        val schema = definition.parameters
        return validateObject(call.arguments, schema, definition.name)
    }

    private fun validateObject(
        value: JsonObject,
        schema: JsonSchemaType.ObjectType,
        toolName: String,
    ): Result {
        // additionalProperties: false
        for (key in value.keys) {
            if (key !in schema.properties) {
                return Result.Invalid("$toolName: unknown property '$key'")
            }
        }
        // required
        for (key in schema.required) {
            if (key !in value) {
                return Result.Invalid("$toolName: missing required property '$key'")
            }
        }
        for ((key, propertySchema) in schema.properties) {
            val element = value[key] ?: continue
            when (val r = validateElement(element, propertySchema, "$toolName.$key")) {
                is Result.Invalid -> return r
                Result.Valid -> Unit
            }
        }
        return Result.Valid
    }

    private fun validateElement(element: JsonElement, schema: JsonSchemaType, path: String): Result {
        if (element is JsonNull) return Result.Invalid("$path: null is not allowed")
        return when (schema) {
            is JsonSchemaType.StringType -> {
                if (element !is JsonPrimitive || !element.isString) {
                    Result.Invalid("$path: expected string")
                } else {
                    val text = element.content
                    if (schema.enum != null && text !in schema.enum) {
                        Result.Invalid("$path: value '$text' not in enum ${schema.enum}")
                    } else Result.Valid
                }
            }
            is JsonSchemaType.IntegerType -> {
                // Exact integer required; "50" (string) is NOT coerced.
                if (element !is JsonPrimitive || element.isString) {
                    Result.Invalid("$path: expected integer")
                } else {
                    val num = element.content.toIntOrNull()
                    if (num == null) Result.Invalid("$path: expected integer")
                    else {
                        val min = schema.minimum
                        val max = schema.maximum
                        when {
                            min != null && num < min -> Result.Invalid("$path: below minimum $min")
                            max != null && num > max -> Result.Invalid("$path: above maximum $max")
                            else -> Result.Valid
                        }
                    }
                }
            }
            is JsonSchemaType.NumberType -> {
                if (element !is JsonPrimitive || element.isString) {
                    Result.Invalid("$path: expected number")
                } else {
                    val num = element.content.toDoubleOrNull()
                    if (num == null || !num.isFinite()) Result.Invalid("$path: expected number")
                    else {
                        val min = schema.minimum
                        val max = schema.maximum
                        when {
                            min != null && num < min -> Result.Invalid("$path: below minimum $min")
                            max != null && num > max -> Result.Invalid("$path: above maximum $max")
                            else -> Result.Valid
                        }
                    }
                }
            }
            is JsonSchemaType.BooleanType -> {
                if (element !is JsonPrimitive || element.isString || element.content != "true" && element.content != "false") {
                    Result.Invalid("$path: expected boolean")
                } else Result.Valid
            }
            is JsonSchemaType.ArrayType -> validateArray(element, schema, path)
            is JsonSchemaType.ObjectType -> {
                if (element !is JsonObject) Result.Invalid("$path: expected object")
                else validateObject(element, schema, path)
            }
        }
    }

    private fun validateArray(element: JsonElement, schema: JsonSchemaType.ArrayType, path: String): Result {
        if (element !is JsonArray) return Result.Invalid("$path: expected array")
        // Arrays-of-primitives only (design D4): nested objects/arrays rejected.
        if (schema.items is JsonSchemaType.ObjectType || schema.items is JsonSchemaType.ArrayType) {
            return Result.Invalid("$path: nested arrays/objects not allowed in this subset")
        }
        for ((index, item) in element.withIndex()) {
            when (val r = validateElement(item, schema.items, "$path[$index]")) {
                is Result.Invalid -> return r
                Result.Valid -> Unit
            }
        }
        return Result.Valid
    }
}