package com.agentai.core.contract

import kotlinx.serialization.Serializable

/**
 * Bounded JSON Schema subset (design D4, spec 0004 C2/C3).
 *
 * Supported types: primitives (string, integer, number, boolean) and
 * arrays-of-primitives. Object types always have `additionalProperties = false`
 * (enforced structurally: no field exists to allow extra properties).
 * Validation requires exact types — NO coercion is ever applied.
 */
@Serializable
sealed interface JsonSchemaType {

    /** String with optional enum restriction. */
    @Serializable
    data class StringType(
        val enum: List<String>? = null,
    ) : JsonSchemaType

    /** Integer with optional inclusive bounds. */
    @Serializable
    data class IntegerType(
        val minimum: Int? = null,
        val maximum: Int? = null,
    ) : JsonSchemaType

    /** Number (double) with optional inclusive bounds. */
    @Serializable
    data class NumberType(
        val minimum: Double? = null,
        val maximum: Double? = null,
    ) : JsonSchemaType

    @Serializable
    data object BooleanType : JsonSchemaType

    /** Array whose items must be a primitive type (design D4: arrays-of-primitives only). */
    @Serializable
    data class ArrayType(
        val items: JsonSchemaType,
    ) : JsonSchemaType

    /**
     * Object with named properties and a required-property list.
     * `additionalProperties` is always false: any property not declared here is rejected.
     */
    @Serializable
    data class ObjectType(
        val properties: Map<String, JsonSchemaType> = emptyMap(),
        val required: List<String> = emptyList(),
    ) : JsonSchemaType
}