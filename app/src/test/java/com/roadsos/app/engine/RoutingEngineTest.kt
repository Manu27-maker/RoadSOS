package com.roadsos.app.engine

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingEngineTest {

    private val trafficJson = """
        {
          "status": "OK",
          "routes": [
            {
              "summary": "NH 44",
              "legs": [
                {
                  "distance": { "text": "8.2 km", "value": 8200 },
                  "duration": { "text": "15 mins", "value": 900 },
                  "duration_in_traffic": { "text": "19 mins", "value": 1140 }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private val noTrafficJson = """
        {
          "status": "OK",
          "routes": [
            {
              "summary": "Outer Ring Road",
              "legs": [
                {
                  "distance": { "text": "6.0 km", "value": 6000 },
                  "duration": { "text": "12 mins", "value": 720 }
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses traffic-aware duration and adds mobilisation`() {
        val plan = RoutingEngine.parseDirections(trafficJson)!!

        assertEquals(RoutingEngine.ENGINE_GOOGLE, plan.engine)
        assertEquals(RoutingEngine.STATUS_LIVE, plan.status)
        assertEquals(8.2, plan.distanceKm!!, 0.001)
        assertEquals(15.0, plan.durationMin!!, 0.001)
        assertEquals(19.0, plan.trafficDurationMin!!, 0.001)
        // 19 min traffic + 2 min mobilisation.
        assertEquals(21.0, plan.etaMin!!, 0.001)
        assertEquals(4.0, plan.trafficDelayMin!!, 0.001)
        assertEquals("NH 44", plan.summary)
        assertTrue(plan.note!!.contains("traffic-aware"))
    }

    @Test
    fun `without traffic duration it keeps free-flow duration and says so`() {
        val plan = RoutingEngine.parseDirections(noTrafficJson)!!

        assertEquals(RoutingEngine.ENGINE_GOOGLE, plan.engine)
        assertNull(plan.trafficDurationMin)
        assertNull(plan.trafficDelayMin)
        assertEquals(14.0, plan.etaMin!!, 0.001)
        assertTrue(plan.note!!.contains("no traffic duration"))
    }

    @Test
    fun `non-OK response is rejected instead of invented`() {
        val json = """{"status": "ZERO_RESULTS", "routes": []}"""

        assertNull(RoutingEngine.parseDirections(json))
    }

    @Test
    fun `malformed response is rejected`() {
        assertNull(RoutingEngine.parseDirections("not json"))
        assertNull(RoutingEngine.parseDirections("""{"status":"OK","routes":[]}"""))
        assertNull(
            RoutingEngine.parseDirections(
                """{"status":"OK","routes":[{"legs":[{"distance":{"value":1000}}]}]}""",
            ),
        )
    }

    @Test
    fun `offline estimate is distance over 38 kmh plus mobilisation`() {
        val plan = RoutingEngine.offline(19.0, RoutingEngine.STATUS_FALLBACK, "test")

        assertEquals(RoutingEngine.ENGINE_OFFLINE, plan.engine)
        assertEquals(19.0, plan.distanceKm!!, 0.001)
        assertEquals(30.0, plan.durationMin!!, 0.001)
        assertEquals(32.0, plan.etaMin!!, 0.001)
        assertNull(plan.trafficDurationMin)
    }

    @Test
    fun `toggle off never selects the online engine`() = runBlocking {
        val plan = RoutingEngine.route(
            originLat = 12.9121,
            originLng = 77.6446,
            destLat = 12.9172,
            destLng = 77.6234,
            onlineEnabled = false,
            onlineAvailable = true,
            apiKey = "unused-key",
        )

        assertEquals(RoutingEngine.STATUS_OFF, plan.status)
        assertEquals(RoutingEngine.ENGINE_OFFLINE, plan.engine)
    }

    @Test
    fun `no internet falls back immediately and says it resumes automatically`() = runBlocking {
        val plan = RoutingEngine.route(
            originLat = 12.9121,
            originLng = 77.6446,
            destLat = 12.9172,
            destLng = 77.6234,
            onlineEnabled = true,
            onlineAvailable = false,
            apiKey = "key-would-be-ignored",
        )

        assertEquals(RoutingEngine.STATUS_NO_NETWORK, plan.status)
        assertEquals(RoutingEngine.ENGINE_OFFLINE, plan.engine)
        assertTrue(plan.note!!.contains("No internet"))
    }

    private val osrmJson = """
        {
          "code": "Ok",
          "routes": [
            { "distance": 8213.4, "duration": 912.6 }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses OSRM road route without claiming traffic`() {
        val plan = RoutingEngine.parseOsrm(osrmJson)!!

        assertEquals(RoutingEngine.ENGINE_OSRM, plan.engine)
        assertEquals(RoutingEngine.STATUS_OSRM, plan.status)
        assertEquals(8.21, plan.distanceKm!!, 0.001)
        assertEquals(15.2, plan.durationMin!!, 0.05)
        assertEquals(17.2, plan.etaMin!!, 0.05)
        assertNull(plan.trafficDurationMin)
        assertNull(plan.trafficDelayMin)
        assertTrue(plan.note!!.contains("no live traffic"))
    }

    @Test
    fun `OSRM errors are rejected instead of invented`() {
        assertNull(RoutingEngine.parseOsrm("""{"code":"NoRoute","routes":[]}"""))
        assertNull(RoutingEngine.parseOsrm("not json"))
        assertNull(RoutingEngine.parseOsrm("""{"code":"Ok","routes":[]}"""))
    }
}
