package com.example.findme.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Navigation that was on when the app was closed: where to, the stops not reached yet, and how. */
data class SavedNav(
    val to: Waypoint,
    val mode: TravelMode,
    val startedMillis: Long,
    val stops: List<Waypoint> = emptyList(),
)

/**
 * Keeps the running navigation in SharedPreferences, so it comes back after the app is
 * swiped away and opened again. It is forgotten after [MAX_AGE_MS], like location sharing.
 */
class NavStore(context: Context) {
    private val prefs = context.getSharedPreferences("findme_nav", Context.MODE_PRIVATE)

    fun save(to: Waypoint, mode: TravelMode, stops: List<Waypoint> = emptyList()) {
        val arr = JSONArray()
        stops.forEach { s ->
            arr.put(JSONObject().put("name", s.name).put("lat", s.lat).put("lon", s.lon))
        }
        prefs.edit()
            .putString("stops", arr.toString())
            .putString("name", to.name)
            .putString("lat", to.lat.toString())
            .putString("lon", to.lon.toString())
            .putString("categoryClass", to.categoryClass)
            .putString("category", to.category)
            .putString("mode", mode.name)
            .putLong("started", System.currentTimeMillis())
            .apply()
    }

    fun load(): SavedNav? {
        val lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("lon", null)?.toDoubleOrNull() ?: return null
        val started = prefs.getLong("started", 0L)
        if (System.currentTimeMillis() - started > MAX_AGE_MS) {
            clear()
            return null
        }
        val mode = TravelMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: TravelMode.Car
        val to = Waypoint(
            name = prefs.getString("name", null) ?: "Destination",
            lat = lat,
            lon = lon,
            categoryClass = prefs.getString("categoryClass", null),
            category = prefs.getString("category", null),
        )
        val stops = runCatching {
            val arr = JSONArray(prefs.getString("stops", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Waypoint(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))
            }
        }.getOrDefault(emptyList())
        return SavedNav(to, mode, started, stops)
    }

    /** A stop was reached: only the ones still ahead come back after a restart. */
    fun updateStops(stops: List<Waypoint>) {
        val load = load() ?: return
        save(load.to, load.mode, stops)
        // Keep the original start time, so the 4-hour limit still counts from "Go".
        prefs.edit().putLong("started", load.startedMillis).apply()
    }

    /** Forgets the destination; the meet-up sharing flag below is kept. */
    fun clear() {
        prefs.edit()
            .remove("name").remove("lat").remove("lon").remove("categoryClass")
            .remove("category").remove("mode").remove("started").remove("stops")
            .apply()
    }

    /** Meeting whose location sharing was switched on by "Go" (so it ends with the navigation). */
    var sharedByNavMeetingId: String?
        get() = prefs.getString("sharedByNav", null)
        set(value) = prefs.edit().putString("sharedByNav", value).apply()

    companion object {
        const val MAX_AGE_MS = 4 * 60 * 60 * 1000L
    }
}
