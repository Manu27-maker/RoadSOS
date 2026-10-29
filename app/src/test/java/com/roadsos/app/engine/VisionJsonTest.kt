package com.roadsos.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionJsonTest {

    private val response = """
        {
          "choices": [
            {
              "message": {
                "content": "```json\n{\n  \"vehicles_count\": 2,\n  \"vehicle_types\": [\"car\", \"lorry\"],\n  \"vehicle_colors\": [\"blue\", \"white\"],\n  \"fire_or_smoke\": false,\n  \"overturned_vehicle\": false,\n  \"visible_injuries\": false,\n  \"road_blocked\": true,\n  \"severity\": \"High\",\n  \"confidence\": 0.55,\n  \"short_summary\": \"Night collision between a blue car and a white truck on a two-lane highway.\",\n  \"number_plate\": null,\n  \"low_light\": true\n}\n```"
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses vehicle details from a fenced model answer`() {
        val result = VisionJson.parseChatCompletion(response, "qwen/qwen3.8-27b")!!

        assertEquals(2, result.vehiclesCount)
        assertEquals(listOf("car", "truck"), result.vehicleTypes)
        assertEquals(listOf("blue", "white"), result.vehicleColors)
        assertEquals("High", result.severity)
        assertEquals(65, result.score)
        assertEquals(0.55, result.confidence!!, 0.001)
        assertEquals("groq:qwen/qwen3.8-27b", result.engine)
        assertTrue(result.roadBlocked == true)
        assertTrue(result.lowLight == true)
        assertNull(result.numberPlate)
        assertTrue(result.analysisDetails!!.any { it.label == "Vehicles" && it.value == "2 detected" })
        assertTrue(result.note!!.contains("EXIF"))
    }

    @Test
    fun `normalises aliases and reads a number plate`() {
        val json = """{
          "vehicles_count": 3,
          "vehicle_types": ["SUV", "motorbike", "tractor"],
          "vehicle_colors": ["gray", "navy", "pink"],
          "severity": "critical",
          "confidence": 1.7,
          "short_summary": "  Car and bike collide.  ",
          "number_plate": " ka 01 ab 1234 ",
          "fire_or_smoke": true
        }"""

        val result = VisionJson.parseModelJson(json, "test-model")!!

        assertEquals(listOf("car", "bike"), result.vehicleTypes)
        assertEquals(listOf("grey", "blue"), result.vehicleColors)
        assertEquals("Critical", result.severity)
        assertEquals(85, result.score)
        assertEquals(1.0, result.confidence!!, 0.0001)
        assertEquals("KA 01 AB 1234", result.numberPlate)
        assertEquals("Car and bike collide.", result.summary)
    }

    @Test
    fun `rejects answers without a usable severity`() {
        assertNull(VisionJson.parseModelJson("""{"vehicles_count": 1}""", "m"))
        assertNull(VisionJson.parseModelJson("not json", "m"))
        assertNull(VisionJson.parseChatCompletion("""{"choices":[]}""", "m"))
    }

    @Test
    fun `count is clamped instead of trusted blindly`() {
        val result = VisionJson.parseModelJson(
            """{"vehicles_count": 99, "severity": "Low", "confidence": -2}""",
            "m",
        )!!

        assertEquals(20, result.vehiclesCount)
        assertEquals(0.0, result.confidence!!, 0.0001)
        assertEquals(20, result.score)
    }
}
