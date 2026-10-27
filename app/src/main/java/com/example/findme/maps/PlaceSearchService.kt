package com.example.findme.maps

import com.yandex.mapkit.GeoObject
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.Snippet
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestResponse
import com.yandex.mapkit.search.SuggestSession
import com.yandex.mapkit.search.SuggestType
import com.yandex.runtime.Error

/**
 * MapKit search: suggestions, text search, organization by URI, address of a point.
 * Each request has a key ("results", "details", "pick"): a new one replaces the previous
 * request with the same key.
 */
class PlaceSearchService {

    private val manager = SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    private val suggestSession: SuggestSession = manager.createSuggestSession()
    private var suggestListener: SuggestSession.SuggestListener? = null

    // MapKit cancels requests whose session or listener gets garbage collected.
    private val sessions = HashMap<String, Session>()
    private val listeners = HashMap<String, Session.SearchListener>()

    fun suggest(text: String, box: BoundingBox, user: Point?, onResult: (SuggestResponse) -> Unit) {
        val options = SuggestOptions()
            .setSuggestTypes(SuggestType.GEO.value or SuggestType.BIZ.value or SuggestType.TRANSIT.value)
        user?.let { options.setUserPosition(it) }
        val listener = object : SuggestSession.SuggestListener {
            override fun onResponse(response: SuggestResponse) = onResult(response)
            override fun onError(error: Error) = Unit
        }
        suggestListener = listener
        suggestSession.suggest(text, box, options, listener)
    }

    fun resetSuggest() {
        suggestSession.reset()
    }

    /** Places and addresses for [query] in [area]. */
    fun search(
        key: String, query: String, area: Geometry, user: Point?,
        onError: (Error) -> Unit = {}, onResult: (List<GeoObject>) -> Unit,
    ) = run(key, onResult, onError) { manager.submit(query, area, options(user), it) }

    /** An organization by its MapKit URI. */
    fun byUri(
        key: String, uri: String, user: Point?,
        onError: (Error) -> Unit = {}, onResult: (List<GeoObject>) -> Unit,
    ) = run(key, onResult, onError) { manager.searchByURI(uri, options(user), it) }

    /** The address at [point]. */
    fun address(
        key: String, point: Point,
        onError: (Error) -> Unit = {}, onResult: (List<GeoObject>) -> Unit,
    ) = run(key, onResult, onError) {
        manager.submit(point, 17, SearchOptions().setSearchTypes(SearchType.GEO.value).setResultPageSize(1), it)
    }

    fun cancel(key: String) {
        sessions.remove(key)?.cancel()
        listeners.remove(key)
    }

    fun cancelAll() {
        sessions.values.forEach { it.cancel() }
        sessions.clear()
        listeners.clear()
    }

    private fun run(
        key: String,
        onResult: (List<GeoObject>) -> Unit,
        onError: (Error) -> Unit,
        submit: (Session.SearchListener) -> Session,
    ) {
        sessions.remove(key)?.cancel()
        val listener = object : Session.SearchListener {
            override fun onSearchResponse(response: Response) =
                onResult(response.collection.children.mapNotNull { it.obj })

            override fun onSearchError(error: Error) = onError(error)
        }
        listeners[key] = listener
        sessions[key] = submit(listener)
    }

    private fun options(user: Point?): SearchOptions {
        val options = SearchOptions()
            .setSearchTypes(SearchType.GEO.value or SearchType.BIZ.value)
            .setResultPageSize(25)
            .setSnippets(Snippet.BUSINESS_RATING1X.value)
            .setGeometry(true)
        user?.let { options.setUserPosition(it) }
        return options
    }
}
