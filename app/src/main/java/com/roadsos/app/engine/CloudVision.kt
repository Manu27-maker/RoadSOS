package com.roadsos.app.engine

import android.graphics.Bitmap
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.roadsos.app.data.VisionResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Optional cloud vision, called only when the user has left online photo
 * analysis on, the phone has a validated connection and a Groq key exists.
 *
 * The image is first decoded and re-encoded on the phone, so the bytes that
 * leave the device are a clean JPEG with EXIF/GPS removed. Nothing else -
 * no contacts, report text, device id or coordinates - is sent.
 *
 * The model answers with strict JSON (vehicle count, types, colours, plate,
 * flags, severity, confidence). [VisionJson] validates it; any failure throws
 * and the caller keeps the on-device result.
 */
object CloudVision {

    const val DEFAULT_MODEL = "qwen/qwen3.8-27b"
    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MAX_SIDE = 1024
    private const val QUALITY = 80
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000

    private const val SYSTEM = """You are a highway crash-scene analyst supporting 112 dispatch in India.
Return STRICT JSON only. Never guess a number plate you cannot read.
Be conservative: if the image is dark, blurry, or ambiguous, lower `confidence`."""

    private const val PROMPT = """Analyse this road-accident photo for emergency responders.

Return ONLY this JSON object:
{
  "vehicles_count": <int 0-20>,
  "vehicle_types": ["car"|"truck"|"bus"|"bike"|"auto"|"unknown", ...],
  "vehicle_colors": ["white"|"black"|"silver"|"red"|"blue"|"yellow"|"green"|"orange"|"grey"|"unknown", ...],
  "fire_or_smoke": <bool>,
  "overturned_vehicle": <bool>,
  "visible_injuries": <bool>,
  "road_blocked": <bool>,
  "severity": "Low"|"Medium"|"High"|"Critical",
  "confidence": <0.0-1.0>,
  "short_summary": "<max 25 words, factual>",
  "number_plate": "<plate text if clearly readable, else null>",
  "low_light": <bool>
}

Rules:
- Count only road vehicles involved in the incident, not passing traffic.
- fire_or_smoke is true for flames, thick smoke, or a large fuel spill.
- visible_injuries is true only if people or casualties are visible.
- Severity: Low = damage only, Medium = minor injury, High = serious injury or
  blocked lane, Critical = fire, entrapment, or multiple casualties.
- If it is a night shot, set low_light true and keep confidence <= 0.6."""

    /** Blocking call - always invoke from an IO dispatcher. Throws on any failure. */
    fun analyze(bitmap: Bitmap, apiKey: String, model: String = DEFAULT_MODEL): VisionResult {
        if (apiKey.isBlank()) throw IOException("No Groq key configured")

        val dataUrl = jpegDataUrl(bitmap)
        val payload = JsonObject().apply {
            addProperty("model", model)
            addProperty("temperature", 0)
            addProperty("max_tokens", 700)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", PROMPT)
                        })
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply { addProperty("url", dataUrl) })
                        })
                    })
                })
            })
        }

        val body = post(payload.toString(), apiKey)
        return VisionJson.parseChatCompletion(body, model)
            ?: throw IOException("Groq returned no usable analysis JSON")
    }

    /** Re-encode at up to [MAX_SIDE] px and return a base64 data URL. */
    private fun jpegDataUrl(bitmap: Bitmap): String {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
        if (scaled !== bitmap) scaled.recycle()

        val encoded = Base64.getEncoder().encodeToString(bytes)
        return "data:image/jpeg;base64,$encoded"
    }

    private fun post(body: String, apiKey: String): String {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "RoadSOS/1.0 (Android)")
        }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("Groq HTTP $code: ${errorMessage(text)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    /** Pull the provider's error message without ever echoing the API key. */
    private fun errorMessage(body: String): String {
        val message = runCatching {
            com.google.gson.JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")
                ?.get("message")
                ?.asString
        }.getOrNull() ?: body
        return message.replace(Regex("\\s+"), " ").trim().take(160)
    }
}
