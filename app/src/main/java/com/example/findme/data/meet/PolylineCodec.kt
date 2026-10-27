package com.example.findme.data.meet

import com.yandex.mapkit.geometry.Point
import kotlin.math.roundToLong

/**
 * Google "encoded polyline" format (5 decimal places, about 1 m): a route of thousands of
 * points fits in a few tens of kilobytes of text, small enough for one Firestore document.
 */
object PolylineCodec {

    fun encode(points: List<Point>): String {
        val sb = StringBuilder(points.size * 6)
        var lastLat = 0L
        var lastLon = 0L
        for (p in points) {
            val lat = (p.latitude * 1e5).roundToLong()
            val lon = (p.longitude * 1e5).roundToLong()
            write(sb, lat - lastLat)
            write(sb, lon - lastLon)
            lastLat = lat
            lastLon = lon
        }
        return sb.toString()
    }

    fun decode(text: String): List<Point> {
        val out = ArrayList<Point>(text.length / 6)
        var i = 0
        var lat = 0L
        var lon = 0L
        while (i < text.length) {
            val (dLat, next) = read(text, i) ?: break
            val (dLon, after) = read(text, next) ?: break
            lat += dLat
            lon += dLon
            out.add(Point(lat / 1e5, lon / 1e5))
            i = after
        }
        return out
    }

    private fun write(sb: StringBuilder, value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }

    /** One number starting at [start], and the index after it; null on broken input. */
    private fun read(text: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            if (i >= text.length) return null
            val b = text[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }
}
