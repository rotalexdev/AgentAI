package com.agentai.core.model.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * RED tests for spec 0009 E1: the dataset MUST be 10 categories x 12 records
 * = 120 records, English-only, deterministic (same seed ⇒ same dataset).
 */
class SeedDatasetTest {

    @Test
    fun `dataset has exactly 120 records`() {
        val dataset = SeedDataset.build()
        assertEquals(120, dataset.size)
    }

    @Test
    fun `dataset has 12 records per category`() {
        val dataset = SeedDataset.build()
        for (category in EvaluationCategory.entries) {
            val count = dataset.count { it.category == category }
            assertEquals(12, count, "category $category must have 12 records")
        }
    }

    @Test
    fun `every record has a unique id`() {
        val dataset = SeedDataset.build()
        assertEquals(dataset.size, dataset.map { it.id }.toSet().size)
    }

    @Test
    fun `every record has exactly one expectation`() {
        val dataset = SeedDataset.build()
        for (record in dataset) {
            val hasTool = record.expectedTool != null
            val hasRejection = record.expectedRejection != null
            assertTrue(
                hasTool xor hasRejection,
                "record ${record.id} must have exactly one expectation (tool xor rejection)",
            )
        }
    }

    @Test
    fun `dataset is deterministic across builds`() {
        assertEquals(SeedDataset.build(), SeedDataset.build())
    }

    @Test
    fun `prompts are non-empty and english-only ascii`() {
        val dataset = SeedDataset.build()
        for (record in dataset) {
            assertFalse(record.prompt.isBlank(), "record ${record.id} prompt must not be blank")
            // ASCII-only check: any non-ASCII byte means non-English / encoding issue.
            assertTrue(
                record.prompt.all { it.code < 128 },
                "record ${record.id} prompt must be ASCII (English-only)",
            )
        }
    }

    @Test
    fun `expected args are valid for integer tools`() {
        val dataset = SeedDataset.build()
        for (record in dataset) {
            if (record.expectedTool == "set_brightness" || record.expectedTool == "set_volume") {
                val value = record.expectedArgs?.get("value") as? Int
                assertTrue(
                    value != null && value in 0..100,
                    "record ${record.id}: ${record.expectedTool} value must be 0..100",
                )
            }
        }
    }
}