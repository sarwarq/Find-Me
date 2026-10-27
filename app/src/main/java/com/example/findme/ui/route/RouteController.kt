package com.example.findme.ui.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.findme.data.Geo
import com.example.findme.data.RouteResult
import com.example.findme.data.RouteVariant
import com.example.findme.data.TravelMode
import com.example.findme.data.Waypoint
import com.example.findme.data.WaypointSlot
import com.example.findme.maps.RouteService
import com.example.findme.ui.MapCommand
import com.yandex.mapkit.geometry.BoundingBoxHelper
import kotlinx.coroutines.flow.MutableSharedFlow

/** The route being planned: start, stops, destination, travel mode and the variants found. */
class RouteController(
    private val routeService: RouteService,
    private val commands: MutableSharedFlow<MapCommand>,
    private val say: (String) -> Unit,
    /** Opens the search to choose a place for a slot of the route. */
    private val openSearch: (WaypointSlot) -> Unit,
) {
    var from by mutableStateOf<Waypoint?>(null); private set
    var to by mutableStateOf<Waypoint?>(null); private set
    /** Places to visit on the way, in order, between [from] and [to]. */
    val stops = mutableStateListOf<Waypoint>()
    /** Order of the stops before "Optimize order", for "Undo". */
    var stopsBeforeOptimize by mutableStateOf<List<Waypoint>?>(null); private set
    var optimizing by mutableStateOf(false); private set
    var mode by mutableStateOf(TravelMode.Car); private set
    val results = mutableStateMapOf<TravelMode, RouteResult>()
    var selectedVariant by mutableIntStateOf(0); private set

    /** Stop the search replaces; [stops].size means "add a new one". */
    private var pickStopIndex = 0

    /** Called when the route for the chosen mode arrives. */
    var onLoaded: (RouteResult) -> Unit = { fit() }

    val currentVariant: RouteVariant?
        get() = results[mode]?.variants?.getOrNull(selectedVariant)

    /** Title of the search while choosing a stop: "Stop 2". */
    val pickStopTitle: String get() = "Stop ${pickStopIndex + 1}"

    /** Replaces the whole route and looks for it. */
    fun set(from: Waypoint?, to: Waypoint?, stops: List<Waypoint> = emptyList()) {
        this.from = from
        this.to = to
        this.stops.clear()
        this.stops.addAll(stops)
        stopsBeforeOptimize = null
        selectedVariant = 0
        request()
    }

    /** The route screen closed. */
    fun clear() {
        routeService.cancelAll()
        results.clear()
    }

    fun swap() {
        val a = from
        from = to
        to = a
        stops.reverse()
        stopsBeforeOptimize = null
        request()
    }

    fun setWaypoint(slot: WaypointSlot, wp: Waypoint) {
        when (slot) {
            WaypointSlot.From -> from = wp
            WaypointSlot.To -> to = wp
            WaypointSlot.Stop -> {
                if (pickStopIndex in stops.indices) stops[pickStopIndex] = wp else stops.add(wp)
                stopsBeforeOptimize = null
            }
        }
        request()
    }

    fun selectMode(mode: TravelMode) {
        this.mode = mode
        selectedVariant = 0
        // After a reroute only the mode in use was rebuilt; the others load when chosen.
        if (results[mode] == null && results.isNotEmpty()) requestMode(mode) else fit()
    }

    fun selectVariant(index: Int) {
        selectedVariant = index
    }

    // ---------- Stops on the way ----------

    /** "+ Add stop": choose a place, it goes before the destination. */
    fun addStop() {
        if (stops.size >= MAX_STOPS) {
            say("Up to $MAX_STOPS stops")
            return
        }
        pickStopIndex = stops.size
        openSearch(WaypointSlot.Stop)
    }

    fun editStop(index: Int) {
        pickStopIndex = index
        openSearch(WaypointSlot.Stop)
    }

    fun removeStop(index: Int) {
        if (index !in stops.indices) return
        stops.removeAt(index)
        stopsBeforeOptimize = null
        request()
    }

    /** Drag in the list: the stop moves, the route is rebuilt when the finger lifts. */
    fun moveStop(from: Int, to: Int) {
        if (from !in stops.indices || to !in stops.indices || from == to) return
        stops.add(to, stops.removeAt(from))
    }

    fun onStopsReordered() {
        stopsBeforeOptimize = null
        request()
    }

    /**
     * Puts the stops in the quickest order; the destination stays last. By car the times come
     * from MapKit (one request per pair of points), on foot or by transit straight distances
     * are good enough.
     */
    fun optimizeOrder() {
        val from = from ?: return
        val to = to ?: return
        val stops = stops.toList()
        if (stops.size < 2 || optimizing) return
        val points = listOf(from.point) + stops.map { it.point } + to.point
        val n = points.size
        val cost = Array(n) { DoubleArray(n) }
        // Straight distance at ~30 km/h: the fallback, and the answer for non-car modes.
        for (i in 0 until n) for (j in 0 until n) cost[i][j] = Geo.distanceMeters(points[i], points[j]) / 8.3
        if (mode != TravelMode.Car) {
            applyBestOrder(stops, cost)
            return
        }
        optimizing = true
        // Only the pairs an order can use: start to stop, stop to stop, stop to destination.
        val pairs = buildList {
            for (i in 1..stops.size) add(0 to i)
            for (i in 1..stops.size) for (j in 1..stops.size) if (i != j) add(i to j)
            for (i in 1..stops.size) add(i to n - 1)
        }
        var left = pairs.size
        pairs.forEach { (i, j) ->
            routeService.requestDriveEta("opt:$i:$j", points[i], points[j]) { eta ->
                eta?.let { cost[i][j] = it.seconds }
                left--
                if (left == 0) {
                    optimizing = false
                    if (this.stops.toList() == stops) applyBestOrder(stops, cost)
                }
            }
        }
    }

    private fun applyBestOrder(stops: List<Waypoint>, cost: Array<DoubleArray>) {
        val (best, saved) = StopOrder.best(stops.size, cost)
        if (best == (1..stops.size).toList() || saved < 30) {
            say("The stops are already in the best order")
            return
        }
        stopsBeforeOptimize = stops
        this.stops.clear()
        this.stops.addAll(best.map { stops[it - 1] })
        say("New order saves about ${Geo.durationText(saved)}")
        request()
    }

    fun undoOptimize() {
        val old = stopsBeforeOptimize ?: return
        stopsBeforeOptimize = null
        stops.clear()
        stops.addAll(old)
        request()
    }

    // ---------- Routing ----------

    private fun request() {
        routeService.cancelAll()
        results.clear()
        selectedVariant = 0
        TravelMode.entries.forEach { requestMode(it) }
    }

    private fun requestMode(mode: TravelMode) {
        val to = to ?: return
        val from = from ?: return
        results[mode] = RouteResult(loading = true)
        val points = listOf(from.point) + stops.map { it.point } + to.point
        routeService.request(mode, points) { result ->
            results[mode] = result
            if (mode == this.mode) onLoaded(result)
        }
    }

    /**
     * I left the route in guidance: a new one from [from] through the [stops] still ahead,
     * for the mode in use only. The old route stays on the map until the new one arrives;
     * [done] gets false when none was found.
     */
    fun reroute(from: Waypoint, stops: List<Waypoint>, done: (Boolean) -> Unit) {
        val to = to ?: return done(false)
        val mode = mode
        routeService.cancelAll()
        // The other modes were planned from the old start; they load again when chosen.
        TravelMode.entries.filter { it != mode }.forEach { results.remove(it) }
        routeService.request(mode, listOf(from.point) + stops.map { it.point } + to.point) { result ->
            if (result.variants.isEmpty() || mode != this.mode) return@request done(false)
            this.from = from
            this.stops.clear()
            this.stops.addAll(stops)
            stopsBeforeOptimize = null
            selectedVariant = 0
            results[mode] = result
            done(true)
        }
    }

    /** Shows all route variants. */
    fun fit() {
        val boxes = results[mode]?.variants.orEmpty().mapNotNull { it.bbox }
        if (boxes.isEmpty()) return
        val union = boxes.reduce { a, b -> BoundingBoxHelper.getBounds(a, b) }
        commands.tryEmit(MapCommand.Fit(union))
    }

    companion object {
        const val MAX_STOPS = 5
    }
}
