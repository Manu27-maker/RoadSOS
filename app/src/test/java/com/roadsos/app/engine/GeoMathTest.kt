package com.roadsos.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun `bearing delta wraps around north`() {
        assertEquals(20.0, GeoMath.bearingDelta(350.0, 10.0), 0.0001)
        assertEquals(20.0, GeoMath.bearingDelta(10.0, 350.0), 0.0001)
        assertEquals(0.0, GeoMath.bearingDelta(45.0, 45.0), 0.0001)
    }

    @Test
    fun `silk board to electronic city is roughly nine kilometres`() {
        val km = GeoMath.haversineKm(12.9172, 77.6234, 12.8452, 77.6602)

        assertTrue("unexpected distance: $km", km in 8.0..9.5)
    }
}
