package com.roadsos.app.engine

import com.roadsos.app.data.TextResult

/**
 * On-device severity triage from free text.
 *
 * This is a straight port of the RoadSOS rule engine: a weighted keyword
 * lexicon with negation handling, injury estimates and safety floors. It runs
 * entirely on the phone, so a report never depends on connectivity.
 */
object NlpEngine {

    private const val ENGINE = "on-device-rules"

    private data class Rule(
        val label: String,
        val pattern: Regex,
        val weight: Int,
        val category: String,
        val hazard: String?,
    )

    private data class Hit(
        val label: String,
        val weight: Int,
        val category: String,
        val hazard: String?,
        val matchText: String,
    )

    private val RULES: List<Rule> = listOf(
        // life threat
        Rule("trapped", Regex("""\btrapp?ed\b|\bpinned\b|\bstuck (?:inside|under)\b|\bperson under\b""", RegexOption.IGNORE_CASE), 26, "life", "Person(s) trapped"),
        Rule("unconscious", Regex("""\bunconscious\b|\bnot (?:breathing|responsive)\b|\bunresponsive\b|\bcoma\b""", RegexOption.IGNORE_CASE), 26, "life", "Unresponsive casualty"),
        Rule("severe bleeding", Regex("""\bbleeding\b|\bblood\b|\bhaemorrhag\w*|\bhemorrhag\w*""", RegexOption.IGNORE_CASE), 18, "life", "Active bleeding"),
        // fire / hazmat
        Rule("fire", Regex("""\bfires?\b|\bflames?\b|\bburning\b|\bblaze\b""", RegexOption.IGNORE_CASE), 22, "fire", "Active fire"),
        Rule("smoke", Regex("""\bsmoke\b|\bsmoulder\w*|\bsmolder\w*""", RegexOption.IGNORE_CASE), 10, "fire", "Smoke reduces visibility"),
        Rule("fuel leak", Regex("""\bfuel (?:leak|spill)\b|\bdiesel (?:leak|spill)\b|\bpetrol (?:leak|spill)\b|\bgas leak\b|\bchemical\b""", RegexOption.IGNORE_CASE), 22, "fire", "Flammable spill / hazmat"),
        Rule("explosion", Regex("""\bexplosion\b|\bblast\b|\bexploded\b""", RegexOption.IGNORE_CASE), 30, "fire", "Explosion"),
        // impact / rollover
        Rule("overturned", Regex("""\boverturned\b|\bcapsized\b|\bflipped\b|\brolled over\b|\bturned over\b|\btoppled\b""", RegexOption.IGNORE_CASE), 16, "impact", "Rollover / unstable vehicle"),
        Rule("head-on", Regex("""\bhead[- ]on\b|\bfrontal\b""", RegexOption.IGNORE_CASE), 18, "impact", "Head-on impact"),
        Rule("collision", Regex("""\bcollision\b|\bcrashed?\b|\baccident\b|\brammed\b|\bhit\b""", RegexOption.IGNORE_CASE), 8, "impact", null),
        // scale
        Rule("multiple vehicles", Regex("""\bmultiple (?:vehicles|cars|people)\b|\bpile[- ]?up\b|\bmulti[- ]vehicle\b|\bseveral (?:cars|vehicles)\b|\b3 cars\b""", RegexOption.IGNORE_CASE), 14, "scale", "Multi-vehicle scene"),
        Rule("truck", Regex("""\btruck\b|\blorry\b|\bcontainer\b|\btanker\b""", RegexOption.IGNORE_CASE), 12, "scale", "Heavy vehicle involved"),
        Rule("bus", Regex("""\bbus\b|\bcoach\b""", RegexOption.IGNORE_CASE), 14, "scale", "Bus involved (high casualty potential)"),
        Rule("two-wheeler", Regex("""\bbikes?\b|\bmotorcycles?\b|\btwo[- ]wheeler\b|\bscooter\b|\bcyclist\b""", RegexOption.IGNORE_CASE), 6, "scale", "Two-wheeler involved"),
        Rule("pedestrian", Regex("""\bpedestrians?\b|\bjaywalk\w*""", RegexOption.IGNORE_CASE), 10, "scale", "Pedestrian involved"),
        // vulnerability
        Rule("child", Regex("""\bchild\b|\bkids?\b|\bbaby\b|\binfant\b""", RegexOption.IGNORE_CASE), 12, "vulnerable", "Child involved"),
        Rule("pregnant", Regex("""\bpregnant\b""", RegexOption.IGNORE_CASE), 14, "vulnerable", "Pregnant casualty"),
        Rule("elderly", Regex("""\belderly\b|\bold (?:man|woman|person)\b|\bsenior citizen\b""", RegexOption.IGNORE_CASE), 10, "vulnerable", "Elderly casualty"),
        // aggravators
        Rule("night", Regex("""\bnight\b|\bdark\b|\bmidnight\b""", RegexOption.IGNORE_CASE), 5, "aggravator", "Night-time low visibility"),
        Rule("rain", Regex("""\brain\b|\bwet road\b|\bslippery\b""", RegexOption.IGNORE_CASE), 5, "aggravator", "Wet / slippery road"),
        Rule("fog", Regex("""\bfog\b|\bmist\b|\blow visibility\b""", RegexOption.IGNORE_CASE), 6, "aggravator", "Low visibility"),
        Rule("highway", Regex("""\bhighway\b|\bexpressway\b|\bflyover\b|\bnh[- ]?\d+\b|\bouter ring road\b""", RegexOption.IGNORE_CASE), 5, "aggravator", "High-speed corridor"),
        Rule("blocked", Regex("""\broad (?:is )?blocked\b|\bblocking traffic\b|\bstandstill\b|\btraffic jam\b|\bfull stop\b""", RegexOption.IGNORE_CASE), 8, "aggravator", "Carriageway blocked"),
    )

    private val WHITESPACE = Regex("""\s+""")

    private val NEGATION = Regex(
        """\b(?:no|not|without|nobody|none|zero|denies)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A negation only applies inside its own clause: in "no fire, but two
     * people are trapped" the "no" must not hide "trapped". "minor" is not a
     * negation cue either - "minor injuries, one person unconscious" is still
     * an unconscious casualty.
     */
    private val CLAUSE_BREAK = Regex(
        """[.,;:!?]|\b(?:but|however|and|while|although|though)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val NUMBER_WORDS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    )

    private val EXPLICIT_INJURIES = Regex(
        """(\d+|""" + NUMBER_WORDS.keys.joinToString("|") + """)""" +
            """\s*(?:people|persons?|passengers?|victims?|injured|hurt|occupants?|killed|dead|casualt\w*)""",
        RegexOption.IGNORE_CASE,
    )

    private val TAG_TO_KEYWORD = mapOf(
        "fire_or_smoke" to "fire",
        "overturned_vehicle" to "overturned",
        "road_blocked" to "blocked",
        "visible_injuries" to "severe bleeding",
        "multiple_vehicles" to "multiple vehicles",
    )

    private val RULES_BY_LABEL: Map<String, Rule> by lazy { RULES.associateBy { it.label } }

    fun band(score: Int): String = when {
        score >= 75 -> "Critical"
        score >= 50 -> "High"
        score >= 25 -> "Medium"
        else -> "Low"
    }

    fun ruleScore(description: String, sceneTags: Map<String, Boolean> = emptyMap()): TextResult {
        val normalized = description.replace(WHITESPACE, " ").trim().lowercase()
        val text = " $normalized "

        val hits = mutableListOf<Hit>()
        val seen = mutableSetOf<String>()

        for (rule in RULES) {
            val match = rule.pattern.find(text) ?: continue
            if (rule.label in seen) continue
            if (isNegated(text, match.range.first)) continue
            seen.add(rule.label)
            hits.add(
                Hit(
                    label = rule.label,
                    weight = rule.weight,
                    category = rule.category,
                    hazard = rule.hazard,
                    matchText = match.value.take(40),
                ),
            )
        }

        // Reporter-confirmed scene tags count as evidence too (max 3, no double count).
        var tagsApplied = 0
        for ((tag, on) in sceneTags) {
            val label = TAG_TO_KEYWORD[tag] ?: continue
            if (!on || label in seen || tagsApplied >= 3) continue
            val rule = RULES_BY_LABEL[label] ?: continue
            seen.add(label)
            tagsApplied += 1
            hits.add(
                Hit(
                    label = "$label (photo)",
                    weight = minOf(rule.weight, 18),
                    category = rule.category,
                    hazard = rule.hazard,
                    matchText = "scene tag",
                ),
            )
        }

        var score = hits.sumOf { it.weight }.coerceIn(0, 100)
        val labels = hits.map { it.label.removeSuffix(" (photo)") }.toSet()

        // Safety floors: never under-triage a life threat.
        if ("trapped" in labels || "unconscious" in labels) score = maxOf(score, 55)
        if ("trapped" in labels && (labels.contains("fire") || labels.contains("fuel leak"))) {
            score = maxOf(score, 80)
        }
        if ("explosion" in labels) score = maxOf(score, 85)

        val explicitInjuries = EXPLICIT_INJURIES.find(text)?.groupValues?.getOrNull(1)?.let { token ->
            token.toIntOrNull() ?: NUMBER_WORDS[token.lowercase()] ?: 0
        } ?: 0

        var implied = 0
        if (labels.any { it == "trapped" || it == "unconscious" || it == "severe bleeding" }) implied = 2
        if ("trapped" in labels) implied += 1
        if ("multiple vehicles" in labels) implied = maxOf(implied, 3)
        if ("truck" in labels) implied = maxOf(implied, 2)
        if ("bus" in labels) implied = maxOf(implied, 8)
        if ("pedestrian" in labels) implied = maxOf(implied, 1)
        val injuries = minOf(50, maxOf(explicitInjuries, implied))

        val hazards = hits.mapNotNull { it.hazard }.distinct()
        val confidence = if (hits.isEmpty()) 0.35 else minOf(0.94, 0.55 + 0.06 * hits.size)

        return TextResult(
            score = score,
            severity = band(score),
            confidence = Math.round(confidence * 100.0) / 100.0,
            engine = ENGINE,
            hazards = hazards,
            injuries = injuries,
            keywords = hits.map { it.label },
            rationale = rationale(hits, score, explicitInjuries),
        )
    }

    /**
     * Looks back for a negation cue ("no fire", "not trapped"), but stops at
     * the previous clause boundary so a negation cannot leak into the next
     * observation.
     */
    private fun isNegated(text: String, start: Int): Boolean {
        val before = text.substring(maxOf(0, start - 40), start.coerceAtLeast(0))
        val boundary = CLAUSE_BREAK.findAll(before).lastOrNull()?.range?.last ?: -1
        val window = if (boundary >= 0) before.substring(boundary + 1) else before
        return NEGATION.containsMatchIn(window)
    }

    private fun rationale(hits: List<Hit>, score: Int, explicitInjuries: Int): String {
        if (hits.isEmpty()) {
            return "No high-risk keywords detected. Score held low; photo evidence and " +
                "reporter confirmation should decide."
        }
        val labels = hits.take(6).map { it.label }
        val builder = StringBuilder("Matched ${hits.size} signal(s): ${labels.joinToString(", ")}.")
        if (labels.any { it == "trapped" || it == "unconscious" }) {
            builder.append(" Life-threat indicator present, so the score is floored at High.")
        }
        if (explicitInjuries > 0) {
            builder.append(" Reporter stated $explicitInjuries casualty/casualties.")
        }
        builder.append(" Weighted score $score -> ${band(score)}.")
        return builder.toString()
    }
}
