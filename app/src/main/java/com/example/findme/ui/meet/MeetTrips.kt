package com.example.findme.ui.meet

import com.example.findme.data.Geo
import com.example.findme.data.TravelMode
import com.example.findme.data.meet.RouteGeom
import com.yandex.mapkit.geometry.Point
import kotlin.math.abs

/** My navigation as the map knows it: where to, how, and the chosen route line. */
data class MyNav(
    val destination: Point,
    val mode: TravelMode,
    val points: List<Point>,
    val durationSeconds: Double,
    val distanceMeters: Double,
)

/** A member's trip as others see it. */
data class MemberTrip(
    /** The rest of the route they drive (only after they tapped "Go"). */
    val rest: List<Point>?,
    /** Time left: from their navigation, else estimated from their route. */
    val etaSeconds: Double?,
    val remainingMeters: Double?,
    /** They follow a route in navigation. */
    val navigating: Boolean,
)

/** Where a member is compared with me on the road (phase 5). */
sealed interface RoadRelation {
    /** On the same road, [meters] further along it. */
    data class Ahead(val meters: Double) : RoadRelation
    data class Behind(val meters: Double) : RoadRelation
    /** On the same road, within [TOGETHER_METERS]. */
    data object Together : RoadRelation
    /** On another road now; our routes meet in [meters] along mine. */
    data class JoinsIn(val meters: Double) : RoadRelation
}

/** "1.2 km ahead of you" for lists. */
fun RoadRelation.text(): String = when (this) {
    is RoadRelation.Ahead -> "${Geo.formatDistance(meters)} ahead of you"
    is RoadRelation.Behind -> "${Geo.formatDistance(meters)} behind you"
    RoadRelation.Together -> "next to you"
    is RoadRelation.JoinsIn -> "joins your road in ${Geo.formatDistance(meters)}"
}

/** "1.2 km ahead" for map labels. */
fun RoadRelation.short(): String = when (this) {
    is RoadRelation.Ahead -> "${Geo.formatDistance(meters)} ahead"
    is RoadRelation.Behind -> "${Geo.formatDistance(meters)} behind"
    RoadRelation.Together -> "with you"
    is RoadRelation.JoinsIn -> "joins in ${Geo.formatDistance(meters)}"
}

/** Closer than this along the road counts as "together". */
private const val TOGETHER_METERS = 50.0

/** Farther than this from a road is "not on it" (GPS error on a wide street is ~20–30 m). */
private const val ON_ROAD_METERS = 40.0

/**
 * Compares me and a member on the road:
 * 1. both on my route → ahead/behind along it;
 * 2. both on their route → ahead/behind along theirs;
 * 3. different roads, both navigating → where our routes meet.
 * [joinAlong] gives (and caches) where along [mine] it first meets [theirs].
 */
fun roadRelation(
    me: Point,
    them: Point,
    mine: RouteGeom?,
    theirs: RouteGeom?,
    joinAlong: (RouteGeom, RouteGeom) -> Double?,
): RoadRelation? {
    fun along(geom: RouteGeom): RoadRelation? {
        val a = geom.project(me) ?: return null
        val b = geom.project(them) ?: return null
        if (a.offRoute > ON_ROAD_METERS || b.offRoute > ON_ROAD_METERS) return null
        val diff = b.along - a.along
        return when {
            abs(diff) < TOGETHER_METERS -> RoadRelation.Together
            diff > 0 -> RoadRelation.Ahead(diff)
            else -> RoadRelation.Behind(-diff)
        }
    }
    mine?.let { g -> along(g)?.let { return it } }
    theirs?.let { g -> along(g)?.let { return it } }
    if (mine != null && theirs != null) {
        val join = joinAlong(mine, theirs) ?: return null
        val myAlong = mine.project(me)?.along ?: return null
        val left = join - myAlong
        if (left > TOGETHER_METERS) return RoadRelation.JoinsIn(left)
    }
    return null
}
