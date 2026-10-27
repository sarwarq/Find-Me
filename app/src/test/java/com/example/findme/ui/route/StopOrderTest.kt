package com.example.findme.ui.route

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class StopOrderTest {

    /** Travel time = distance between positions on one straight road. */
    private fun costOnLine(vararg x: Double) = Array(x.size) { i -> DoubleArray(x.size) { j -> abs(x[i] - x[j]) } }

    @Test
    fun `finds the quickest order`() {
        // Start at 0, destination at 100, stops at 30, 10, 20: best is 10, 20, 30 = stops 2, 3, 1.
        val (best, saved) = StopOrder.best(3, costOnLine(0.0, 30.0, 10.0, 20.0, 100.0))
        assertEquals(listOf(2, 3, 1), best)
        assertEquals(40.0, saved, 1e-9) // 140 -> 100
    }

    @Test
    fun `keeps an order that is already best`() {
        val (best, saved) = StopOrder.best(3, costOnLine(0.0, 10.0, 20.0, 30.0, 100.0))
        assertEquals(listOf(1, 2, 3), best)
        assertEquals(0.0, saved, 1e-9)
    }

    @Test
    fun `one stop has nothing to reorder`() {
        assertEquals(listOf(1) to 0.0, StopOrder.best(1, costOnLine(0.0, 50.0, 100.0)))
    }

    @Test
    fun `five stops, the most the app allows`() {
        val (best, _) = StopOrder.best(5, costOnLine(0.0, 50.0, 10.0, 40.0, 20.0, 30.0, 100.0))
        assertEquals(listOf(2, 4, 5, 3, 1), best) // 10, 20, 30, 40, 50
    }

    @Test
    fun `one-way times count in their direction`() {
        // Going 1 -> 2 is slow (a one-way street), 2 -> 1 is quick.
        val cost = arrayOf(
            doubleArrayOf(0.0, 10.0, 10.0, 0.0),
            doubleArrayOf(0.0, 0.0, 100.0, 10.0),
            doubleArrayOf(0.0, 5.0, 0.0, 10.0),
            doubleArrayOf(0.0, 0.0, 0.0, 0.0),
        )
        val (best, saved) = StopOrder.best(2, cost)
        assertEquals(listOf(2, 1), best)
        assertEquals(95.0, saved, 1e-9) // 10+100+10 = 120 -> 10+5+10 = 25
    }
}
