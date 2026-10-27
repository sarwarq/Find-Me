package com.example.findme.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlace(
    val name: String,
    val address: String?,
    val lat: Double,
    val lon: Double,
    val uri: String?,
) {
    fun toPlace() = Place(
        id = uri ?: "pt:$lat,$lon",
        name = name,
        lat = lat,
        lon = lon,
        address = address,
        uri = uri,
    )
}

fun Place.toSaved() = SavedPlace(name, address, lat, lon, uri)

/** Bookmarks, home/work and search history kept in SharedPreferences. */
class SavedPlacesStore(context: Context) {
    private val prefs = context.getSharedPreferences("findme_saved", Context.MODE_PRIVATE)

    fun bookmarks(): List<SavedPlace> = readList(KEY_BOOKMARKS)
    fun setBookmarks(list: List<SavedPlace>) = writeList(KEY_BOOKMARKS, list)

    fun home(): SavedPlace? = readList(KEY_HOME).firstOrNull()
    fun setHome(place: SavedPlace?) = writeList(KEY_HOME, listOfNotNull(place))

    fun work(): SavedPlace? = readList(KEY_WORK).firstOrNull()
    fun setWork(place: SavedPlace?) = writeList(KEY_WORK, listOfNotNull(place))

    fun history(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun setHistory(list: List<String>) {
        prefs.edit().putString(KEY_HISTORY, JSONArray(list).toString()).apply()
    }

    private fun readList(key: String): List<SavedPlace> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                SavedPlace(
                    name = o.getString("name"),
                    address = o.optString("address").ifEmpty { null },
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    uri = o.optString("uri").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeList(key: String, list: List<SavedPlace>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("name", it.name)
                    .put("address", it.address ?: "")
                    .put("lat", it.lat)
                    .put("lon", it.lon)
                    .put("uri", it.uri ?: "")
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private companion object {
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_HOME = "home"
        const val KEY_WORK = "work"
        const val KEY_HISTORY = "history"
    }
}
