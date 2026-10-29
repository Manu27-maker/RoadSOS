package com.roadsos.app.engine

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.roadsos.app.data.VisionDetail
import com.roadsos.app.data.VisionResult
import kotlin.math.roundToInt

/**
 * Pure parsing for a Groq/OpenAI-compatible vision response.
 *
 * Kept free of Android types so it can be unit-tested on the JVM. Every value
 * is validated and clamped: a malformed or half-empty model answer returns
 * null and the caller falls back to the on-device heuristic instead of showing
 * invented vehicle details.
 */
object VisionJson {

    private val gson = Gson()

    private val SEVERITY_SCORE = linkedMapOf(
        "Low" to 20,
        "Medium" to 40,
        "High" to 65,
        "Critical" to 85,
    )

    private val TYPE_ALIASES = mapOf(
        "automobile" to "car",
        "suv" to "car",
        "van" to "car",
        "jeep" to "car",
        "taxi" to "car",
        "lorry" to "truck",
        "pickup" to "truck",
        "tanker" to "truck",
        "motorbike" to "bike",
        "motorcycle" to "bike",
        "scooter" to "bike",
        "two-wheeler" to "bike",
        "auto-rickshaw" to "auto",
        "autorickshaw" to "auto",
        "rickshaw" to "auto",
        "three-wheeler" to "auto",
    )

    private val KNOWN_TYPES = setOf("car", "truck", "bus", "bike", "auto", "unknown")

    private val COLOR_ALIASES = mapOf(
        "gray" to "grey",
        "dark" to "black",
        "maroon" to "red",
        "navy" to "blue",
    )

    private val KNOWN_COLORS = setOf(
        "white", "black", "silver", "red", "blue", "yellow", "green", "orange", "grey", "unknown",
    )

    /** Parse the full chat-completions envelope and return the model's analysis. */
    fun parseChatCompletion(body: String, model: String): VisionResult? {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
        val content = root?.let {
            runCatching {
                it.getAsJsonArray("choices")
                    ?.firstOrNull()
                    ?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")
                    ?.asString
            }.getOrNull()
        }

        if (content.isNullOrBlank()) {
            // Some providers return the analysis object directly.
            return parseModelJson(body, model)
        }
        return parseModelJson(content, model)
    }

    /** Parse loose model output: fenced blocks and prose around the JSON are tolerated. */
    fun parseModelJson(raw: String, model: String): VisionResult? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null

        val parsed = runCatching {
            gson.fromJson(raw.substring(start, end + 1), VisionResult::class.java)
        }.getOrNull() ?: return null

        val severity = parsed.severity
            ?.trim()
            ?.let { value -> SEVERITY_SCORE.keys.firstOrNull { it.equals(value, ignoreCase = true) } }
            ?: return null

        val count = parsed.vehiclesCount?.coerceIn(0, 20)

        val types = parsed.vehicleTypes.orEmpty()
            .mapNotNull { it?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .map { TYPE_ALIASES[it] ?: it }
            .filter { it in KNOWN_TYPES }
            .distinct()
            .take(8)

        val colors = parsed.vehicleColors.orEmpty()
            .mapNotNull { it?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            .map { COLOR_ALIASES[it] ?: it }
            .filter { it in KNOWN_COLORS }
            .distinct()
            .take(8)

        val plate = parsed.numberPlate
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", true) && !it.equals("none", true) }
            ?.uppercase()
            ?.take(16)

        val confidence = parsed.confidence?.let { value ->
            if (value.isNaN()) null else value.coerceIn(0.0, 1.0)
        }

        var score = SEVERITY_SCORE.getValue(severity)
        if (parsed.fireOrSmoke == true || parsed.overturned == true || parsed.visibleInjuries == true) {
            score = maxOf(score, SEVERITY_SCORE.getValue("High"))
        }
        score = score.coerceIn(0, 100)

        val summary = parsed.summary?.trim()?.takeIf(String::isNotEmpty)?.take(240)

        val details = buildList {
            add(VisionDetail("Vehicles", count?.let { "$it detected" } ?: "not returned"))
            if (types.isNotEmpty()) add(VisionDetail("Types", types.joinToString(", ")))
            if (colors.isNotEmpty()) add(VisionDetail("Colours", colors.joinToString(", ")))
            plate?.let { add(VisionDetail("Number plate", it)) }
            confidence?.let { add(VisionDetail("Model confidence", "${(it * 100).roundToInt()}%")) }
            add(VisionDetail("Model", model))
        }

        return VisionResult(
            score = score,
            severity = severity,
            confidence = confidence,
            engine = "groq:$model",
            summary = summary,
            fireOrSmoke = parsed.fireOrSmoke,
            roadBlocked = parsed.roadBlocked,
            overturned = parsed.overturned,
            visibleInjuries = parsed.visibleInjuries,
            lowLight = parsed.lowLight,
            vehiclesCount = count,
            vehicleTypes = types,
            vehicleColors = colors,
            numberPlate = plate,
            analysisDetails = details,
            note = "Cloud vision via Groq. Only the re-encoded JPEG (EXIF/GPS removed) was sent; " +
                "model output is unverified, so confirm on scene.",
        )
    }
}
