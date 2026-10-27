package com.example.findme.maps

import com.example.findme.data.Place
import com.yandex.mapkit.GeoObject
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.BusinessObjectMetadata
import com.yandex.mapkit.search.BusinessRating1xObjectMetadata
import com.yandex.mapkit.search.ToponymObjectMetadata
import com.yandex.mapkit.uri.UriObjectMetadata
import com.yandex.runtime.Error
import com.yandex.runtime.network.NetworkError

fun GeoObject.uri(): String? =
    metadataContainer.getItem(UriObjectMetadata::class.java)?.uris?.firstOrNull()?.value

fun GeoObject.anyPoint(): Point? {
    geometry.firstNotNullOfOrNull { it.point }?.let { return it }
    metadataContainer.getItem(ToponymObjectMetadata::class.java)?.balloonPoint?.let { return it }
    val box = boundingBox ?: return null
    return Point(
        (box.southWest.latitude + box.northEast.latitude) / 2,
        (box.southWest.longitude + box.northEast.longitude) / 2,
    )
}

/** Converts a MapKit search result or a tapped map object into a [Place]. */
fun GeoObject.toPlace(fromPoi: Boolean = false): Place? {
    val point = anyPoint() ?: return null
    val md = metadataContainer
    val biz = md.getItem(BusinessObjectMetadata::class.java)
    val topo = md.getItem(ToponymObjectMetadata::class.java)
    val rating = md.getItem(BusinessRating1xObjectMetadata::class.java)
    val uri = uri()

    val name = biz?.name ?: name ?: topo?.address?.formattedAddress ?: "Point on the map"
    val address = biz?.address?.formattedAddress
        ?: descriptionText
        ?: topo?.address?.formattedAddress
    val category = biz?.categories?.firstOrNull()
    val website = biz?.links
        ?.firstOrNull { it.tag == "self" }?.link?.href
        ?: biz?.links?.firstOrNull()?.link?.href
    val state = biz?.workingHours?.state

    return Place(
        id = uri ?: biz?.oid ?: "pt:${point.latitude},${point.longitude}",
        name = name,
        lat = point.latitude,
        lon = point.longitude,
        category = category?.name ?: if (topo != null) "Address" else null,
        categoryClass = category?.categoryClass,
        address = address?.takeIf { it != name },
        uri = uri,
        phone = biz?.phones?.firstOrNull()?.formattedNumber,
        website = website,
        hoursText = state?.text ?: biz?.workingHours?.text,
        isOpenNow = state?.isOpenNow,
        rating = rating?.score,
        ratingsCount = rating?.ratings,
        isBusiness = biz != null,
        fromPoi = fromPoi,
    )
}

fun Error.toUserMessage(): String = when (this) {
    is NetworkError -> "No internet connection"
    else -> "Something went wrong. Please try again"
}
