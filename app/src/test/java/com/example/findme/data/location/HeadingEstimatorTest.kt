package com.example.findme.data.location

import com.example.findme.TestGeo.pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingEstimatorTest {

    private val h = HeadingEstimator()

    @Test
    fun `unknown until I move`() {
        assertNull(h.update(pt(0.0, 0.0), speedMps = 0.0, course = null))
        assertNull(h.update(pt(0.0, 3.0), speedMps = 0.0, course = null))
    }

    @Test
    fun `GPS course is used when driving`() {
        assertEquals(123f, h.update(pt(0.0, 0.0), speedMps = 10.0, course = 123.0)!!, 0.01f)
    }

    @Test
    fun `GPS course is ignored when slow`() {
        assertNull(h.update(pt(0.0, 0.0), speedMps = 1.0, course = 200.0))
    }

    @Test
    fun `direction from my moves when GPS gives no course`() {
        h.update(pt(0.0, 0.0), 0.5, null)
        assertEquals(0f, h.update(pt(0.0, 10.0), 0.5, null)!!, 1f) // north
        assertEquals(90f, h.update(pt(10.0, 10.0), 0.5, null)!!, 1f) // then east
        assertEquals(180f, h.update(pt(10.0, 0.0), 0.5, null)!!, 1f) // then south
    }

    @Test
    fun `GPS jitter while standing does not turn the map`() {
        h.update(pt(0.0, 0.0), 0.0, null)
        h.update(pt(0.0, 20.0), 0.0, null) // walked north
        // Standing at a crossing, the fix wanders 3-5 m around.
        assertEquals(0f, h.update(pt(4.0, 18.0), 0.0, null)!!, 1f)
        assertEquals(0f, h.update(pt(-3.0, 22.0), 0.0, null)!!, 1f)
    }

    @Test
    fun `keeps the driving direction after stopping at a light`() {
        h.update(pt(0.0, 0.0), 12.0, 270.0)
        assertEquals(270f, h.update(pt(-1.0, 0.0), 0.0, null)!!, 0.01f)
        assertEquals(270f, h.update(pt(-1.0, 0.0), 0.0, null)!!, 0.01f)
    }

    @Test
    fun `after a GPS course, moves count from where it was`() {
        h.update(pt(0.0, 0.0), 12.0, 90.0) // driving east
        assertEquals(90f, h.update(pt(0.0, 5.0), 0.5, null)!!, 0.01f) // 5 m: too little
        assertEquals(0f, h.update(pt(0.0, 9.0), 0.5, null)!!, 1f) // 9 m north of the GPS fix
    }
}
