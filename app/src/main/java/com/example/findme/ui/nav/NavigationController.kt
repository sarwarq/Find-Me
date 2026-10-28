package com.example.findme.ui.nav

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.findme.data.NavStore
import com.example.findme.data.RouteResult
import com.example.findme.data.SavedNav
import com.example.findme.data.SettingsStore
import com.example.findme.data.TravelMode
import com.example.findme.data.Waypoint
import com.example.findme.data.location.LocationFix
import com.example.findme.data.location.LocationRepository
import com.example.findme.data.Geo
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
    /** Spoken guidance: turns, stops, arrival, a new route. */
    private val speak: (String) -> Unit,
    /** A beep: speeding. */
    private val beep: () -> Unit = {},
    /** Camera alerts and the speeding margin. */
    private val settings: SettingsStore? = null,
) {
    var active by mutableStateOf(false); private set
    var progress by mutableStateOf<NavProgress?>(null); private set
    /** The user moved the map in guidance: the camera stops following for [FREE_MS]. */
    var free by mutableStateOf(false); private set
    /** I left the route and a new one is on its way. */
    var rerouting by mutableStateOf(false); private set

    private var tracker: NavTracker? = null
    private val offRoute = OffRouteDetector()
    private val overspeed = OverspeedAlarm()
    private var prompter: VoicePrompter? = null
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
        prompter = VoicePrompter(route.mode)
        offRoute.reset()
        location.setTravelMode(route.mode)
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
        location.setTravelMode(null)
        progress = null
        tracker = null
        prompter = null
        rerouting = false
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

    /** The rest of the route on one screen; back to the arrow by the button or 30 s later. */
    fun overview() {
        val track = route.currentVariant?.track ?: return
        val from = (progress?.trackSegment ?: 0).coerceIn(0, track.points.size - 1)
        val box = Geo.boundsOf(track.points.subList(from, track.points.size)) ?: return
        pauseFollow()
        commands.tryEmit(MapCommand.Fit(box))
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
            val name = route.stops.getOrNull(i)?.name.orEmpty()
            say("Stop ${i + 1} reached: $name")
            speak("You have reached $name")
        }
        if (update.reachedStops.isNotEmpty()) store.updateStops(route.stops.drop(tracker.reachedStops))
        // Cameras switched off in the settings are neither shown nor said.
        val camerasOff = settings?.cameraAlerts == false && update.progress.event?.kind?.isCamera == true
        val shown = if (camerasOff) update.progress.copy(event = null) else update.progress
        progress = shown
        if (update.arrived) {
            say("You have arrived")
            speak("You have arrived")
            stop()
            return
        }
        val margin = settings?.speedingMarginKmh ?: 10
        if (margin > 0 && overspeed.update(shown.speedKmh, shown.limitKmh, SystemClock.elapsedRealtime(), margin)) beep()
        // Transit routes are not rebuilt: a bus does not follow the drawn line exactly.
        if (route.mode != TravelMode.Transit && !rerouting &&
            offRoute.update(update.offRouteMeters, SystemClock.elapsedRealtime())
        ) {
            reroute(fix.point)
            return
        }
        // Off the route the distances to the turns mean nothing.
        if (update.offRouteMeters < OFF_ROUTE_M && !rerouting) {
            prompter?.update(update.progress, route.currentVariant?.steps.orEmpty(), speed)?.let(speak)
            prompter?.event(shown, speed)?.let(speak)
        }
    }

    /** I left the route: a new one from where I am, keeping the stops not reached yet. */
    private fun reroute(here: Point) {
        val left = route.stops.drop(tracker?.reachedStops ?: 0)
        rerouting = true
        speak("Recalculating the route")
        route.reroute(Waypoint("Current location", here.latitude, here.longitude, isMyLocation = true), left) { found ->
            rerouting = false
            if (!active) return@reroute
            val variant = route.currentVariant
            if (!found || variant?.track == null) {
                say("Could not rebuild the route")
                return@reroute
            }
            tracker = NavTracker(variant)
            offRoute.reset()
            prompter?.reset()
            route.to?.let { store.save(it, route.mode, route.stops.toList()) }
            say("Route rebuilt")
            location.fix.value?.let { me ->
                track(me, me.speedMps)
                if (active) follow(me.point, moveCamera = !free)
            }
        }
    }

    private fun follow(point: Point, moveCamera: Boolean = true) {
        commands.tryEmit(MapCommand.Follow(point, heading, moveCamera, along, speed))
    }

    private companion object {
        const val FREE_MS = 30_000L
        /** Farther than this from the route, I'm off it (as [OffRouteDetector] counts). */
        const val OFF_ROUTE_M = 50.0
    }
}
