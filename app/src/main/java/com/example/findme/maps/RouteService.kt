package com.example.findme.maps

import com.example.findme.data.DriveEta
import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.ManeuverPoint
import com.example.findme.data.NavTrack
import com.example.findme.data.RouteLine
import com.example.findme.data.RouteResult
import com.example.findme.data.RouteVariant
import com.example.findme.data.TransitLeg
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.Time
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.Action
import com.yandex.mapkit.directions.driving.DrivingSummarySession
import com.yandex.mapkit.directions.driving.Summary
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouterType
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.SubpolylineHelper
import com.yandex.mapkit.navigation.JamType
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.FilterVehicleTypes
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.Route
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.mapkit.transport.masstransit.TransitOptions
import com.yandex.runtime.Error
import kotlin.math.max

/** Builds routes for every travel mode with MapKit routers. */
class RouteService {
    private val drivingRouter =
        DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED)
    private val transport = TransportFactory.getInstance()
    private val transitRouter = transport.createMasstransitRouter()
    private val pedestrianRouter = transport.createPedestrianRouter()
    private val bikeRouter = transport.createBicycleRouterV2()
    private val scooterRouter = transport.createScooterRouter()

    // MapKit cancels a request when its session (or listener) is garbage collected,
    // so both are kept here until the next request.
    private val drivingSessions = mutableMapOf<TravelMode, DrivingSession>()
    private val transportSessions = mutableMapOf<TravelMode, Session>()
    private val listeners = mutableMapOf<TravelMode, Any>()

    private val etaSessions = mutableMapOf<String, DrivingSummarySession>()
    private val etaListeners = mutableMapOf<String, DrivingSummarySession.DrivingSummaryListener>()

    /** Quick travel time by car, without route geometry. [key] replaces an earlier request with the same key. */
    fun requestDriveEta(key: String, from: Point, to: Point, onResult: (DriveEta?) -> Unit) {
        etaSessions.remove(key)?.cancel()
        val listener = object : DrivingSummarySession.DrivingSummaryListener {
            override fun onDrivingSummaries(summaries: MutableList<Summary>) {
                etaSessions.remove(key); etaListeners.remove(key)
                val w = summaries.firstOrNull()?.weight
                onResult(
                    w?.let {
                        DriveEta(
                            seconds = it.timeWithTraffic.value,
                            text = Geo.durationText(it.timeWithTraffic.value),
                            distanceText = Geo.shortDistance(it.distance.value),
                            trafficRatio = if (it.time.value > 0) it.timeWithTraffic.value / it.time.value else 1.0,
                        )
                    }
                )
            }

            override fun onDrivingSummariesError(error: Error) {
                etaSessions.remove(key); etaListeners.remove(key)
                onResult(null)
            }
        }
        etaListeners[key] = listener
        etaSessions[key] = drivingRouter.requestRoutesSummary(
            listOf(
                RequestPoint(from, RequestPointType.WAYPOINT, null, null, null),
                RequestPoint(to, RequestPointType.WAYPOINT, null, null, null),
            ),
            DrivingOptions().setRoutesCount(1),
            VehicleOptions(),
            listener,
        )
    }

    fun cancelEtas() {
        etaSessions.values.forEach { it.cancel() }
        etaSessions.clear()
        etaListeners.clear()
    }

    fun cancelAll() {
        drivingSessions.values.forEach { it.cancel() }
        transportSessions.values.forEach { it.cancel() }
        drivingSessions.clear()
        transportSessions.clear()
        listeners.clear()
    }

    /**
     * Routes through [stops] in order: the start, the intermediate stops, the destination.
     * Every stop is a waypoint, so the route arrives there rather than just passing by.
     */
    fun request(mode: TravelMode, stops: List<Point>, onResult: (RouteResult) -> Unit) {
        val points = stops.map { RequestPoint(it, RequestPointType.WAYPOINT, null, null, null) }
        val middle = stops.drop(1).dropLast(1)
        if (mode == TravelMode.Car) {
            val listener = object : DrivingSession.DrivingRouteListener {
                override fun onDrivingRoutes(routes: MutableList<DrivingRoute>) {
                    onResult(RouteResult(variants = routes.map { drivingVariant(it).withStops(middle) }, error = emptyError(routes)))
                }

                override fun onDrivingRoutesError(error: Error) {
                    onResult(RouteResult(error = error.toUserMessage()))
                }
            }
            listeners[mode] = listener
            drivingSessions[mode] = drivingRouter.requestRoutes(
                points,
                DrivingOptions().setRoutesCount(3),
                VehicleOptions(),
                listener,
            )
            return
        }

        val listener = object : Session.RouteListener {
            override fun onMasstransitRoutes(routes: MutableList<Route>) {
                onResult(
                    RouteResult(
                        variants = routes.take(4).map { transportVariant(it, mode).withStops(middle) },
                        error = emptyError(routes),
                    )
                )
            }

            override fun onMasstransitRoutesError(error: Error) {
                onResult(RouteResult(error = error.toUserMessage()))
            }
        }
        listeners[mode] = listener
        // Default no-arg MapKit option objects contain null fields that crash the native
        // router (SIGABRT "field operation on NULL object"), so every field is set explicitly.
        val options = RouteOptions(FitnessOptions(false, false))
        val time = TimeOptions(null, null)
        transportSessions[mode] = when (mode) {
            TravelMode.Transit -> transitRouter.requestRoutes(
                points, TransitOptions(FilterVehicleTypes.NONE.value, time), options, listener,
            )
            TravelMode.Walk -> pedestrianRouter.requestRoutes(points, time, options, listener)
            TravelMode.Bike -> bikeRouter.requestRoutes(points, time, options, listener)
            TravelMode.Scooter -> scooterRouter.requestRoutes(points, time, options, listener)
            TravelMode.Car -> error("handled above")
        }
    }

    /** Finds, in order, the track segment where each intermediate stop is reached. */
    private fun RouteVariant.withStops(middle: List<Point>): RouteVariant {
        val pts = track?.points ?: return this
        if (middle.isEmpty() || pts.size < 2) return this
        var from = 0
        val dist = { stop: Point, i: Int -> Geo.distanceToSegment(stop, pts[i], pts[i + 1]).first }
        val segments = middle.map { stop ->
            // The first pass near the stop, not a later one (a route may come back the same way).
            var best = (from until pts.size - 1).firstOrNull { dist(stop, it) < NEAR_STOP_M }
            if (best != null) {
                while (best!! + 1 < pts.size - 1 && dist(stop, best + 1) < dist(stop, best)) best++
            } else {
                best = (from until pts.size - 1).minByOrNull { dist(stop, it) } ?: from
            }
            from = best
            best
        }
        return copy(stopSegments = segments)
    }

    private fun emptyError(routes: List<*>) = if (routes.isEmpty()) "No route found" else null

    private fun drivingVariant(route: DrivingRoute): RouteVariant {
        val weight = route.metadata.weight
        val points = route.geometry.points
        val jamColors = route.jamSegments.map { jamColor(it.jamType) }
        val flags = route.metadata.flags
        val details = listOfNotNull(
            if (flags.blocked) "Road closures" else null,
            if (flags.hasTolls) "Toll roads" else null,
            if (flags.hasFerries) "Ferry crossing" else null,
        ).joinToString(" • ").ifEmpty { null }
        val segmentColors = jamColors.takeIf { it.size == points.size - 1 }
        val maneuvers = route.sections.drop(1).mapNotNull { section ->
            val action = section.metadata.annotation.action ?: return@mapNotNull null
            ManeuverPoint(section.geometry.begin.segmentIndex, action.toManeuver())
        } + ManeuverPoint(max(points.size - 2, 0), Maneuver.Finish)
        return RouteVariant(
            durationText = Geo.durationText(weight.timeWithTraffic.value),
            durationSeconds = weight.timeWithTraffic.value,
            distanceText = Geo.shortDistance(weight.distance.value),
            details = details,
            lines = listOf(RouteLine(points = points, color = COLOR_ROUTE, segmentColors = segmentColors)),
            legs = emptyList(),
            bbox = Geo.boundsOf(points),
            distanceMeters = weight.distance.value,
            hasIncidents = flags.blocked || route.events.isNotEmpty(),
            track = NavTrack(points, route.speedLimits.map { it }, maneuvers, segmentColors),
        )
    }

    private fun transportVariant(route: Route, mode: TravelMode): RouteVariant {
        val weight = route.metadata.weight
        val lines = mutableListOf<RouteLine>()
        val legs = mutableListOf<TransitLeg>()
        val steps = mutableListOf<TransitStep>()
        var walkSeconds = 0.0
        var departure: String? = null

        if (mode == TravelMode.Transit) {
            route.sections.forEach { section ->
                val geometry = SubpolylineHelper.subpolyline(route.geometry, section.geometry).points
                val data = section.metadata.data
                val transports = data.transports
                val start = section.geometry.begin.segmentIndex
                val seconds = section.metadata.weight.time.value
                when {
                    !transports.isNullOrEmpty() -> {
                        val estimation = section.metadata.estimation
                            ?: transports.first().transports.firstOrNull()?.estimation
                        if (departure == null) departure = estimation?.departureTime?.text
                        val line = transports.first().line
                        val color = line.style?.color?.let { 0xFF000000.toInt() or it } ?: vehicleColor(line.vehicleTypes.firstOrNull())
                        val names = transports.mapNotNull { it.line.shortName ?: it.line.name }.distinct()
                        legs += TransitLeg(
                            kind = LegKind.Transport,
                            label = names.firstOrNull().orEmpty(),
                            color = color,
                            vehicle = line.vehicleTypes.firstOrNull(),
                        )
                        val stops = section.stops
                        steps += TransitStep(
                            kind = LegKind.Transport,
                            startSegment = start,
                            seconds = seconds,
                            // Several lines go the same way: "12, 45".
                            lineName = names.take(3).joinToString(", ").ifEmpty { null },
                            color = color,
                            vehicle = line.vehicleTypes.firstOrNull(),
                            fromStop = stops.firstOrNull()?.metadata?.stop?.name,
                            toStop = stops.lastOrNull()?.metadata?.stop?.name,
                            stops = (stops.size - 1).coerceAtLeast(0),
                            departureText = estimation?.departureTime?.text,
                            departureMillis = estimation?.departureTime?.millis(),
                            arrivalMillis = estimation?.arrivalTime?.millis(),
                        )
                        lines += RouteLine(geometry, color)
                    }
                    data.fitness != null -> {
                        walkSeconds += seconds
                        legs += TransitLeg(LegKind.Walk, Geo.durationText(seconds))
                        steps += TransitStep(kind = LegKind.Walk, startSegment = start, seconds = seconds)
                        lines += RouteLine(geometry, COLOR_TRANSIT_WALK, dashed = true)
                    }
                }
            }
        } else {
            val color = when (mode) {
                TravelMode.Bike -> COLOR_BIKE
                TravelMode.Scooter -> COLOR_SCOOTER
                else -> COLOR_WALK
            }
            lines += RouteLine(route.geometry.points, color, dashed = mode == TravelMode.Walk)
        }

        val details = when (mode) {
            TravelMode.Transit -> buildString {
                append(
                    when (weight.transfersCount) {
                        0 -> "No transfers"
                        1 -> "1 transfer"
                        else -> "${weight.transfersCount} transfers"
                    }
                )
                weight.walkingDistance?.let { append(" • walk ${Geo.shortDistance(it.value)}") }
            }
            else -> null
        }

        val points = route.geometry.points
        return RouteVariant(
            durationText = Geo.durationText(weight.time.value),
            durationSeconds = weight.time.value,
            distanceText = if (mode == TravelMode.Transit) null else weight.walkingDistance?.let { Geo.shortDistance(it.value) },
            walkSeconds = walkSeconds,
            departureText = departure,
            steps = steps,
            details = details,
            lines = lines,
            legs = legs,
            bbox = Geo.boundsOf(points),
            distanceMeters = weight.walkingDistance?.value ?: 0.0,
            track = NavTrack(points, emptyList(), listOf(ManeuverPoint(max(points.size - 2, 0), Maneuver.Finish)), null),
        )
    }

    /** MapKit times are seconds since the epoch (a value this big is already milliseconds). */
    private fun Time.millis(): Long = if (value > 100_000_000_000L) value else value * 1000

    /** Default line colors by vehicle type when the line has none, as in Yandex Maps. */
    private fun vehicleColor(type: String?): Int = when (type) {
        "minibus" -> 0xFFB047D3.toInt()
        "bus" -> 0xFF3CB33C.toInt()
        "trolleybus" -> 0xFF1E88E5.toInt()
        "tramway" -> 0xFFE53935.toInt()
        "suburban", "railway" -> 0xFF6E7FAA.toInt()
        else -> COLOR_TRANSIT
    }

    private fun Action.toManeuver(): Maneuver = when (this) {
        Action.SLIGHT_LEFT, Action.EXIT_LEFT -> Maneuver.SlightLeft
        Action.SLIGHT_RIGHT, Action.EXIT_RIGHT -> Maneuver.SlightRight
        Action.LEFT -> Maneuver.Left
        Action.RIGHT -> Maneuver.Right
        Action.HARD_LEFT -> Maneuver.HardLeft
        Action.HARD_RIGHT -> Maneuver.HardRight
        Action.FORK_LEFT -> Maneuver.ForkLeft
        Action.FORK_RIGHT -> Maneuver.ForkRight
        Action.UTURN_LEFT -> Maneuver.UturnLeft
        Action.UTURN_RIGHT -> Maneuver.UturnRight
        Action.ENTER_ROUNDABOUT, Action.LEAVE_ROUNDABOUT -> Maneuver.Roundabout
        Action.FINISH -> Maneuver.Finish
        else -> Maneuver.Straight
    }

    private fun jamColor(type: JamType): Int = when (type) {
        JamType.FREE -> 0xFF3CB83C.toInt()
        JamType.LIGHT -> 0xFFFFC820.toInt()
        JamType.HARD -> 0xFFFF4F3F.toInt()
        JamType.VERY_HARD, JamType.BLOCKED -> 0xFF9E1B0F.toInt()
        else -> COLOR_ROUTE
    }

    companion object {
        private const val NEAR_STOP_M = 60.0
        const val COLOR_ROUTE = 0xFF196DFF.toInt()
        const val COLOR_TRANSIT = 0xFF196DFF.toInt()
        /** Walking route: dashed indigo, like Yandex Maps. */
        const val COLOR_WALK = 0xFF6458E8.toInt()
        /** Walking parts of a public transport route. */
        const val COLOR_TRANSIT_WALK = 0xFF7C8AA5.toInt()
        const val COLOR_BIKE = 0xFF2FA84F.toInt()
        const val COLOR_SCOOTER = 0xFF8A4FFF.toInt()
    }
}
