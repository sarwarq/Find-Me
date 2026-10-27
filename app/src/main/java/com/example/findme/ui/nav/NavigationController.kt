package com.example.findme.ui.nav

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.findme.data.NavStore
import com.example.findme.data.RouteResult
import com.example.findme.data.SavedNav
import com.example.findme.data.location.LocationFix
import com.example.findme.data.location.LocationRepository
import com.example.findme.ui.MapCommand
import com.example.findme.ui.route.RouteController
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/** Guidance along the chosen route: progress, the camera behind the arrow, surviving app kills. */
class NavigationController(
    private val route: RouteController,
    private val store: NavStore,
    private val location: LocationRepository,
    private val scope: CoroutineScope,
    private val commands: MutableSharedFlow<MapCommand>,
    private val say: (String) -> Unit,
) {
    var active by mutableStateOf(false); private set
    var progress by mutableStateOf<NavProgress?>(null); private set
    /** The user moved the map in guidance: the camera stops following for [FREE_MS]. */
    var free by mutableStateOf(false); private set

    private var tracker: NavTracker? = null
    /** Where I'm going: along the road on the route, else from GPS / movement. */
    private var heading: Float? = null
    /** Where I am along the route (null off it) and how fast, for the gliding arrow. */
    private var along: Double? = null
    private var speed: Double? = null
    private var freeJob: Job? = null
    private var lastGestureAt = 0L

    /** Navigation that was on when the app was closed; it restarts at the first location fix. */
    private var pendingResume: SavedNav? = store.load()
    /** Start guidance as soon as the rebuilt route arrives. */
    private var autoStart = false

    /** The saved navigation to restart, once. */
    fun takePendingResume(): SavedNav? = pendingResume.also { pendingResume = null }

    fun startWhenRouteArrives() {
        autoStart = true
    }

    fun cancelAutoStart() {
        autoStart = false
    }

    /** The route for the chosen mode arrived; returns true when it was taken for guidance. */
    fun onRouteLoaded(result: RouteResult): Boolean {
        if (!autoStart) return false
        autoStart = false
        if (result.variants.isEmpty()) return false
        start()
        return true
    }

    fun start() {
        val me = location.fix.value
        if (route.from?.isMyLocation != true || me == null) {
            say("Navigation starts from your location")
            return
        }
        val variant = route.currentVariant
        if (variant?.track == null) {
            say("No navigation for this travel mode")
            return
        }
        tracker = NavTracker(variant)
        active = true
        route.to?.let { store.save(it, route.mode, route.stops.toList()) }
        track(me, speed = null)
        if (active) follow(me.point)
    }

    /** "X" in guidance: back to the route overview. */
    fun stop() {
        end()
        commands.tryEmit(MapCommand.ResetNorth)
        route.fit()
    }

    /** Ends guidance without touching the camera (the route screen is closing). */
    fun end() {
        active = false
        location.stopSimulation() // a debug test drive ends with the guidance
        progress = null
        tracker = null
        freeJob?.cancel()
        freeJob = null
        free = false
        store.clear()
    }

    fun onFix(fix: LocationFix) {
        if (!active) return
        track(fix, fix.speedMps)
        // While I look around the map, the arrow still moves; the camera stays.
        if (active) follow(fix.point, moveCamera = !free)
    }

    /** The user moved the map in guidance: look around freely, back to the arrow after 30 s. */
    fun pauseFollow() {
        lastGestureAt = SystemClock.elapsedRealtime()
        free = true
        if (freeJob?.isActive == true) return
        freeJob = scope.launch {
            while (true) {
                val left = lastGestureAt + FREE_MS - SystemClock.elapsedRealtime()
                if (left <= 0) break
                delay(left)
            }
            freeJob = null
            resumeFollow()
        }
    }

    /** Back to the arrow at the guidance zoom (the button, or 30 s after the last gesture). */
    fun resumeFollow() {
        freeJob?.cancel()
        freeJob = null
        free = false
        val me = location.fix.value?.point ?: return
        if (active) follow(me)
    }

    private fun track(fix: LocationFix, speed: Double?) {
        val tracker = tracker ?: return
        val update = tracker.update(fix.point, speed, System.currentTimeMillis())
        heading = update?.routeHeading ?: fix.heading ?: heading
        along = update?.takeIf { it.onRoute }?.alongMeters
        this.speed = speed
        if (update == null) return
        update.reachedStops.forEach { i ->
            say("Stop ${i + 1} reached: ${route.stops.getOrNull(i)?.name.orEmpty()}")
        }
        if (update.reachedStops.isNotEmpty()) store.updateStops(route.stops.drop(tracker.reachedStops))
        progress = update.progress
        if (update.arrived) {
            say("You have arrived")
            stop()
        }
    }

    private fun follow(point: Point, moveCamera: Boolean = true) {
        commands.tryEmit(MapCommand.Follow(point, heading, moveCamera, along, speed))
    }

    private companion object {
        const val FREE_MS = 30_000L
    }
}
