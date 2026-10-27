package com.example.findme.ui.route

/**
 * The quickest order of the stops on a route. Points are numbered 0 = start,
 * 1..n = stops, n + 1 = destination; the start and the destination stay in place.
 * Every order is tried: at most 5! = 120.
 */
object StopOrder {

    /** The best order of stops 1..[stopCount] and the seconds it saves over the current one. */
    fun best(stopCount: Int, cost: Array<DoubleArray>): Pair<List<Int>, Double> {
        val current = (1..stopCount).toList()
        if (stopCount < 2) return current to 0.0
        val last = stopCount + 1
        fun total(order: List<Int>): Double {
            var t = cost[0][order.first()]
            for (k in 0 until order.size - 1) t += cost[order[k]][order[k + 1]]
            return t + cost[order.last()][last]
        }
        val best = permutations(current).minByOrNull { total(it) } ?: current
        return best to total(current) - total(best)
    }

    private fun permutations(items: List<Int>): List<List<Int>> =
        if (items.size <= 1) listOf(items)
        else items.flatMap { x -> permutations(items - x).map { listOf(x) + it } }
}
