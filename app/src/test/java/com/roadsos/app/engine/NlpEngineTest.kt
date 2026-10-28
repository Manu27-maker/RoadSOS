package com.roadsos.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NlpEngineTest {

    @Test
    fun `negation stops at the clause boundary`() {
        val result = NlpEngine.ruleScore("No fire, but two people are trapped")

        assertTrue("trapped should survive the earlier 'no'", result.keywords.orEmpty().contains("trapped"))
        assertFalse("fire was negated", result.keywords.orEmpty().contains("fire"))
        assertTrue((result.score ?: 0) >= 55)
        assertEquals("High", result.severity)
    }

    @Test
    fun `negation does not jump across and`() {
        val result = NlpEngine.ruleScore("No smoke and one person is trapped")

        assertTrue(result.keywords.orEmpty().contains("trapped"))
        assertFalse(result.keywords.orEmpty().contains("smoke"))
        assertEquals("High", result.severity)
    }

    @Test
    fun `minor does not hide an unconscious casualty`() {
        val result = NlpEngine.ruleScore("Minor injuries, one person unconscious")

        assertTrue(result.keywords.orEmpty().contains("unconscious"))
        assertEquals("High", result.severity)
    }

    @Test
    fun `a negated hazard scores nothing`() {
        val result = NlpEngine.ruleScore("There is no fire")

        assertEquals(0, result.score ?: -1)
        assertEquals("Low", result.severity)
        assertTrue(result.keywords.orEmpty().isEmpty())
    }

    @Test
    fun `explosion is floored at critical`() {
        val result = NlpEngine.ruleScore("There was an explosion")

        assertEquals(85, result.score ?: -1)
        assertEquals("Critical", result.severity)
    }

    @Test
    fun `confirmed scene tags count as evidence`() {
        val result = NlpEngine.ruleScore("", mapOf("fire_or_smoke" to true))

        assertTrue((result.score ?: 0) >= 18)
        assertTrue(result.keywords.orEmpty().any { it.startsWith("fire") })
    }
}
