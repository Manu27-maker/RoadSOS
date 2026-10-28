package com.roadsos.app.engine

import com.roadsos.app.data.TextResult
import com.roadsos.app.data.VisionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeverityFusionTest {

    private fun text(score: Int, keywords: List<String> = listOf("collision")) =
        TextResult(
            score = score,
            severity = NlpEngine.band(score),
            keywords = keywords,
            injuries = 0,
        )

    private fun vision(
        score: Int,
        confidence: Double = 0.8,
        fire: Boolean = false,
        overturned: Boolean = false,
    ) = VisionResult(
        score = score,
        severity = NlpEngine.band(score),
        confidence = confidence,
        fireOrSmoke = fire,
        overturned = overturned,
    )

    @Test
    fun `fused scores always stay inside 0 to 100`() {
        val cases = listOf(
            text(100) to vision(100, confidence = 0.0),
            text(100) to vision(55, confidence = 0.0),
            text(0) to vision(100, confidence = 1.0),
            text(100) to vision(0, confidence = 0.0),
            text(0, keywords = emptyList()) to null,
            null to null,
            text(100, keywords = emptyList()) to vision(0, confidence = 0.0),
        )

        cases.forEach { (textResult, visionResult) ->
            val fused = SeverityFusion.fuse(textResult, visionResult)
            val score = fused.score ?: -1
            assertTrue("$score is outside 0..100 for $textResult / $visionResult", score in 0..100)
        }
    }

    @Test
    fun `the documented low-confidence example holds at strongest minus ten`() {
        val fused = SeverityFusion.fuse(text(46), vision(68, confidence = 0.35))

        assertEquals(58, fused.score ?: -1)
        assertEquals("High", fused.severity)
        assertTrue(fused.explanation.orEmpty().contains("Held at 58"))
    }

    @Test
    fun `fire in the text alone still gets the high floor`() {
        val fused = SeverityFusion.fuse(text(22, listOf("fire")), null)

        assertEquals(55, fused.score ?: -1)
        assertEquals("High", fused.severity)
    }

    @Test
    fun `a text rollover still gets the high floor`() {
        val fused = SeverityFusion.fuse(text(16, listOf("overturned")), null)

        assertEquals(55, fused.score ?: -1)
        assertEquals("High", fused.severity)
    }

    @Test
    fun `a fire photo without text still gets the high floor`() {
        val fused = SeverityFusion.fuse(null, vision(20, confidence = 0.6, fire = true))

        assertEquals(55, fused.score ?: -1)
        assertEquals("High", fused.severity)
    }

    @Test
    fun `a fire scene tag alone still gets the high floor`() {
        // Reporter confirmation arrives as the keyword "fire (photo)"; the
        // floor must match the normalised label, not only a literal "fire".
        val tagged = NlpEngine.ruleScore("", mapOf("fire_or_smoke" to true))
        val fused = SeverityFusion.fuse(tagged, null)

        assertEquals(55, fused.score ?: -1)
        assertEquals("High", fused.severity)
    }

    @Test
    fun `the corroboration bonus cannot exceed one hundred`() {
        val fused = SeverityFusion.fuse(text(100), vision(100, confidence = 0.9))

        assertEquals(100, fused.score ?: -1)
        assertEquals("Critical", fused.severity)
    }

    @Test
    fun `no channel combination can escape the zero to one hundred clamp`() {
        // Includes out-of-contract inputs (-20, 130) to prove the clamp is the
        // last line of defence, not just the happy path.
        val scores = listOf(-20, 0, 1, 17, 25, 49, 50, 55, 74, 75, 90, 100, 130)
        val confidences = listOf(0.0, 0.2, 0.39, 0.4, 0.75, 1.0)

        for (nlp in scores) {
            for (visionScore in scores) {
                for (confidence in confidences) {
                    val fused = SeverityFusion.fuse(
                        text(nlp),
                        vision(visionScore, confidence = confidence),
                    )
                    val score = fused.score ?: -1
                    assertTrue(
                        "text=$nlp vision=$visionScore confidence=$confidence -> $score",
                        score in 0..100,
                    )
                    assertEquals(NlpEngine.band(score), fused.severity)
                }
            }
        }

        // Single-channel extremes go through the same clamp.
        assertTrue((SeverityFusion.fuse(text(130), null).score ?: -1) in 0..100)
        assertTrue((SeverityFusion.fuse(text(-20), null).score ?: -1) in 0..100)
        assertTrue((SeverityFusion.fuse(null, vision(130, confidence = 1.0)).score ?: -1) in 0..100)
        assertTrue((SeverityFusion.fuse(null, vision(-20, confidence = 0.0)).score ?: -1) in 0..100)
    }
}
