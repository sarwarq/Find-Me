package com.example.findme.data.meet

import com.example.findme.TestGeo.pt
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineCodecTest {

    // The example from Google's "Encoded Polyline Algorithm Format" page.
    private val google = listOf(Point(38.5, -120.2), Point(40.7, -120.95), Point(43.252, -126.453))
    private val googleText = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    private fun assertSame(expected: List<Point>, actual: List<Point>, delta: Double = 1e-5) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) ->
            assertEquals(e.latitude, a.latitude, delta)
            assertEquals(e.longitude, a.longitude, delta)
        }
    }

    @Test
    fun `encodes like Google`() {
        assertEquals(googleText, PolylineCodec.encode(google))
    }

    @Test
    fun `decodes like Google`() {
        assertSame(google, PolylineCodec.decode(googleText))
    }

    @Test
    fun `a route survives the round trip to about a meter`() {
        val route = (0..200).map { pt(it * 7.3, it * 13.1) }
        assertSame(route, PolylineCodec.decode(PolylineCodec.encode(route)))
    }

    @Test
    fun `empty route`() {
        assertEquals("", PolylineCodec.encode(emptyList()))
        assertTrue(PolylineCodec.decode("").isEmpty())
    }

    @Test
    fun `broken text gives the points before the break, no crash`() {
        val cut = googleText.dropLast(3)
        val points = PolylineCodec.decode(cut)
        assertSame(google.take(2), points)
    }
}
