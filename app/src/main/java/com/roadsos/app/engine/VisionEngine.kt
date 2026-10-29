package com.roadsos.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.roadsos.app.data.VisionResult
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Photo analysis that never leaves the phone.
 *
 * 1. The bitmap is re-encoded into app-private storage, which drops EXIF/GPS
 *    metadata before anything is kept. Privacy by construction.
 * 2. Heuristic CV always runs: luminance (night detection), red ratio (fire
 *    hint), saturation and edge density. It is honest about its confidence.
 * 3. Reporter-confirmed scene tags override the heuristic.
 */
object VisionEngine {

    private const val MAX_SIDE = 1600

    private val SEVERITY_BASE = mapOf("Low" to 20, "Medium" to 40, "High" to 65, "Critical" to 85)

    private data class Metrics(
        val meanLuma: Double,
        val darkRatio: Double,
        val redRatio: Double,
        val edgeDensity: Double,
        val saturation: Double,
        val lowLight: Boolean,
    )

    /** Decodes a photo, honouring EXIF rotation, capped at [MAX_SIDE] px. */
    fun decode(file: File): Bitmap? {
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
            ?: return null

        val rotation = runCatching {
            when (ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        if (rotation == 0f) {
            return downscale(decoded)
        }
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return downscale(rotated)
    }

    /**
     * inSampleSize only halves the source, so the decoded long side can still
     * be just under 2 x [MAX_SIDE]. This final scale makes the "<= 1600 px"
     * guarantee real (and keeps the stored JPEG small).
     */
    private fun downscale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_SIDE) return bitmap
        val scale = MAX_SIDE.toFloat() / longest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    /** Writes a clean JPEG (no EXIF) and returns its size in bytes. */
    fun writeCleanJpeg(bitmap: Bitmap, target: File): Long {
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return target.length()
    }

    fun analyze(file: File, sceneTags: Map<String, Boolean> = emptyMap()): VisionResult {
        val bitmap = decode(file) ?: throw IllegalArgumentException("Could not read that image. Try another photo.")
        return try {
            analyze(bitmap, sceneTags)
        } finally {
            bitmap.recycle()
        }
    }

    fun analyze(bitmap: Bitmap, sceneTags: Map<String, Boolean> = emptyMap()): VisionResult {
        val metrics = metrics(bitmap)
        val base = heuristic(metrics)
        return applyTags(base, sceneTags)
    }

    // ------------------------------------------------------------------ metrics
    private fun metrics(bitmap: Bitmap): Metrics {
        val side = 96
        val small = Bitmap.createScaledBitmap(bitmap, side, side, true)
        val pixels = IntArray(side * side)
        small.getPixels(pixels, 0, side, 0, 0, side, side)
        if (small != bitmap) small.recycle()

        var sumLuma = 0.0
        var dark = 0
        var reds = 0
        var sumSaturation = 0.0
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = 0.299 * r + 0.587 * g + 0.114 * b
            sumLuma += luma
            if (luma < 40) dark++
            if (r > 95 && r > 1.35 * g && r > 1.35 * b) reds++
            Color.RGBToHSV(r, g, b, hsv)
            sumSaturation += hsv[1].toDouble()
        }

        val count = pixels.size.toDouble()
        val meanLuma = sumLuma / count
        val darkRatio = dark / count
        val redRatio = reds / count
        val saturation = sumSaturation / count

        val edge = 160
        val grayBitmap = Bitmap.createScaledBitmap(bitmap, edge, edge, true)
        val grayPixels = IntArray(edge * edge)
        grayBitmap.getPixels(grayPixels, 0, edge, 0, 0, edge, edge)
        if (grayBitmap != bitmap) grayBitmap.recycle()

        val gray = IntArray(grayPixels.size) { index ->
            val pixel = grayPixels[index]
            ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
        }

        var edgeSum = 0.0
        var edgeSamples = 0
        for (y in 1 until edge - 1) {
            for (x in 1 until edge - 1) {
                val index = y * edge + x
                val centre = gray[index] * 8
                val neighbours = gray[index - edge - 1] + gray[index - edge] + gray[index - edge + 1] +
                    gray[index - 1] + gray[index + 1] +
                    gray[index + edge - 1] + gray[index + edge] + gray[index + edge + 1]
                edgeSum += abs(centre - neighbours)
                edgeSamples++
            }
        }
        val edgeDensity = if (edgeSamples == 0) 0.0 else (edgeSum / edgeSamples) / 255.0

        return Metrics(
            meanLuma = round(meanLuma, 1),
            darkRatio = round(darkRatio, 3),
            redRatio = round(redRatio, 3),
            edgeDensity = round(edgeDensity, 3),
            saturation = round(saturation, 3),
            lowLight = meanLuma < 58 || darkRatio > 0.55,
        )
    }

    // ---------------------------------------------------------------- heuristic
    private fun heuristic(metrics: Metrics): VisionResult {
        val fireHint = metrics.redRatio > 0.10 && metrics.saturation > 0.35
        val busy = metrics.edgeDensity > 0.10

        var score = 40
        if (fireHint) score += 22
        if (busy) score += 6
        if (metrics.lowLight) score += 4

        val severity = SEVERITY_BASE.entries
            .sortedByDescending { it.value }
            .firstOrNull { score >= it.value }
            ?.key ?: "Low"

        val confidence = round(0.30 + (if (busy) 0.05 else 0.0) - (if (metrics.lowLight) 0.05 else 0.0), 2)

        return VisionResult(
            score = minOf(100, score),
            severity = severity,
            confidence = confidence,
            engine = "heuristic-cv",
            summary = "On-device heuristic estimate only: " +
                (if (metrics.lowLight) "low light detected, " else "") +
                "${if (busy) "high" else "moderate"} scene complexity. Confirm tags below.",
            fireOrSmoke = fireHint,
            roadBlocked = busy,
            overturned = false,
            visibleInjuries = false,
            lowLight = metrics.lowLight,
            vehiclesCount = 0,
        )
    }

    /** Reporter confirmation wins over the heuristic - they are physically there. */
    private fun applyTags(result: VisionResult, sceneTags: Map<String, Boolean>): VisionResult {
        if (sceneTags.isEmpty()) return result

        var fire = result.fireOrSmoke
        var overturned = result.overturned
        var injuries = result.visibleInjuries
        var blocked = result.roadBlocked

        sceneTags["fire_or_smoke"]?.let { fire = it }
        sceneTags["overturned_vehicle"]?.let { overturned = it }
        sceneTags["visible_injuries"]?.let { injuries = it }
        sceneTags["road_blocked"]?.let { blocked = it }
        // "multiple_vehicles" is a reporter tag consumed by the text rules.
        // The photo engine has no object detection, so it never invents a count.

        var severity = result.severity ?: "Medium"
        if (fire == true || overturned == true || injuries == true) {
            severity = if ((SEVERITY_BASE[severity] ?: 0) >= SEVERITY_BASE.getValue("High")) severity else "High"
        }

        val score = maxOf(result.score ?: 0, SEVERITY_BASE[severity] ?: 0)
        var confidence = result.confidence ?: 0.35
        if (result.lowLight == true && confidence > 0.62) confidence = 0.62

        return result.copy(
            score = score,
            severity = severity,
            confidence = confidence,
            engine = (result.engine ?: "heuristic-cv") + "+reporter-tags",
            fireOrSmoke = fire,
            overturned = overturned,
            visibleInjuries = injuries,
            roadBlocked = blocked,
            vehiclesCount = 0,
            lowLight = result.lowLight == true,
        )
    }

    private fun round(value: Double, places: Int): Double {
        val factor = Math.pow(10.0, places.toDouble())
        return Math.round(value * factor) / factor
    }
}
