package com.roadsos.app.engine

import com.roadsos.app.data.Fusion
import com.roadsos.app.data.TextResult
import com.roadsos.app.data.VisionResult
import kotlin.math.roundToInt

/**
 * Severity fusion.
 *
 * Two independent channels (text NLP, photo vision) each produce a 0-100 score.
 * We trust the stronger channel, keep the weaker one as context, then apply a
 * corroboration boost, a low-confidence penalty and a safety floor so a report
 * is never triaged far below its most alarming signal.
 */
object SeverityFusion {

    fun fuse(text: TextResult?, vision: VisionResult?): Fusion {
        val nlpScore = text?.score ?: 0
        val visionScore = vision?.score ?: 0
        val confidence = vision?.confidence ?: 0.0
        val lowLight = vision?.lowLight == true

        val hasText = !text?.keywords.isNullOrEmpty()
        val hasPhoto = vision != null

        var final: Int
        val explanation: StringBuilder

        if (hasPhoto && (!hasText || nlpScore == 0)) {
            final = visionScore
            explanation = StringBuilder(
                "Photo-only triage: vision scored $visionScore " +
                    "(${vision?.severity ?: "Medium"}, confidence ${format(confidence)}). " +
                    "No usable text description, so the report inherits the photo severity.",
            )
            if (lowLight) explanation.append(" Low-light image: verify on scene.")
        } else if (hasText && !hasPhoto) {
            final = nlpScore
            explanation = StringBuilder(
                "Text-only triage: ${text?.keywords?.size ?: 0} keyword(s) scored $nlpScore. " +
                    "No photo attached, so the photo channel is neutral (0).",
            )
        } else {
            val hi = maxOf(nlpScore, visionScore)
            val lo = minOf(nlpScore, visionScore)
            var base = 0.65 * hi + 0.35 * lo

            explanation = StringBuilder(
                "Photo $visionScore vs text $nlpScore -> stronger signal weighted 65/35 " +
                    "(base ${base.roundToInt()}).",
            )

            if (hi >= 70 && lo >= 55) {
                base += 5
                explanation.append(" Both channels independently high, +5 corroboration.")
            }
            if (confidence < 0.40) {
                base -= 8
                explanation.append(" Vision confidence only ${format(confidence)}, -8 uncertainty penalty.")
            }

            final = clamp(base)
            if (final < hi - 10) {
                final = hi - 10
                explanation.append(" Held at $final so the report is not under-triaged.")
            }
        }

        final = clamp(final.toDouble())
        var severity = NlpEngine.band(final)

        // A fire / rollover / entrapment signal always carries at least High
        // severity - whether it came from the photo or from the reporter's text.
        // Scene tags are stored as "fire (photo)", so normalise before matching.
        val keywords = text?.keywords.orEmpty().map { it.removeSuffix(" (photo)") }
        val criticalEvidence = vision?.fireOrSmoke == true ||
            vision?.overturned == true ||
            "fire" in keywords ||
            "overturned" in keywords ||
            "trapped" in keywords ||
            "unconscious" in keywords

        if (criticalEvidence && final < 55) {
            final = 55
            severity = NlpEngine.band(final)
            explanation.append(" Fire/rollover/entrapment evidence enforced a High floor.")
        }

        return Fusion(score = final, severity = severity, explanation = explanation.toString())
    }

    /**
     * Every branch funnels through here. The corroboration boost can take a
     * raw base above 100 before this call, and the "strongest - 10" hold is at
     * most 90, so the fused score is always 0..100.
     */
    private fun clamp(value: Double): Int = value.coerceIn(0.0, 100.0).roundToInt()

    private fun format(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)
}
