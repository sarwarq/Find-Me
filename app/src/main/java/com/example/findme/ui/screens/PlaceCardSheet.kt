package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.Place
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.Screen
import com.example.findme.ui.components.ActionPill
import com.example.findme.ui.components.CloseCircle
import com.example.findme.ui.components.RatingRow
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.geometry.Point

/** External actions that need an Android Context. */
class PlaceActions(
    val call: (String) -> Unit,
    val openSite: (String) -> Unit,
    val share: (Place) -> Unit,
    val copy: (String) -> Unit,
    /** Opens a taxi app, to [Point] when given. */
    val taxi: (Point?) -> Unit,
)

@Composable
fun PlaceCardContent(vm: MapViewModel, card: Screen.PlaceCard, actions: PlaceActions, onMeet: (Place) -> Unit) {
    val place = card.place
    val bookmarked = vm.isBookmarked(place)
    val eta = vm.cardEta?.takeIf { it.first == place.id }?.second

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                place.category?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = yPalette.textSecondary)
                }
            }
            Spacer(Modifier.width(8.dp))
            CloseCircle(onClick = { vm.onBack() }, modifier = Modifier.padding(top = 2.dp))
        }

        if (card.loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(2.dp),
                color = yPalette.accent,
                trackColor = yPalette.field,
            )
        }

        place.rating?.let {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 16.dp)) { RatingRow(it, place.ratingsCount) }
        }

        // "Working hours unknown"            🚗 21 min • 10.2 km
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hours = place.hoursText ?: if (place.isBusiness) "Working hours unknown" else place.address
            Text(
                hours.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = when (place.isOpenNow) {
                    true -> YColors.Green
                    false -> YColors.Red
                    null -> yPalette.textSecondary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val distance = Geo.distanceText(vm.userLocation, place.point)
            if (eta != null || distance != null) {
                Spacer(Modifier.width(8.dp))
                YIcon(
                    if (eta != null) R.drawable.ms_directions_car else R.drawable.ms_near_me,
                    null, tint = yPalette.textSecondary, size = 20.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    buildAnnotatedString {
                        if (eta != null) {
                            withStyle(SpanStyle(color = yPalette.textPrimary, fontWeight = FontWeight.SemiBold)) {
                                append(eta.text.replace(" ", " "))
                            }
                            withStyle(SpanStyle(color = yPalette.textSecondary)) { append(" • ${eta.distanceText}") }
                        } else {
                            withStyle(SpanStyle(color = yPalette.textSecondary)) { append(distance) }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ActionPill("Directions", R.drawable.ms_route, { vm.routeTo(place) }, primary = true) }
            item { ActionPill("Meet", R.drawable.ms_group, { onMeet(place) }) }
            place.phone?.let { phone -> item { ActionPill(null, R.drawable.ms_call, { actions.call(phone) }) } }
            item { ActionPill("Ride", R.drawable.ms_local_taxi, { actions.taxi(place.point) }) }
            item {
                ActionPill(
                    null,
                    if (bookmarked) R.drawable.ms_bookmark else R.drawable.ms_bookmarks,
                    { vm.toggleBookmark(place) },
                )
            }
            item { ActionPill(null, R.drawable.ms_share, { actions.share(place) }) }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = yPalette.divider)

        place.address?.let { InfoRow(R.drawable.ms_location_on, it, hint = "Copy address") { actions.copy(it) } }
        place.hoursText?.let { InfoRow(R.drawable.ms_schedule, it) {} }
        place.phone?.let { phone -> InfoRow(R.drawable.ms_call, phone) { actions.call(phone) } }
        place.website?.let { site ->
            InfoRow(R.drawable.ms_language, site.removePrefix("https://").removePrefix("http://")) { actions.openSite(site) }
        }
        val coords = Geo.coordinatesText(place.lat, place.lon)
        InfoRow(R.drawable.ms_my_location, coords, hint = "Tap to copy") { actions.copy(coords) }

        HorizontalDivider(color = yPalette.divider)
        InfoRow(R.drawable.ms_route, "Directions from here") { vm.routeFrom(place) }
        InfoRow(R.drawable.ms_home, "Set as home") { vm.setHome(place) }
        InfoRow(R.drawable.ms_work, "Set as work") { vm.setWork(place) }

        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

@Composable
private fun InfoRow(@DrawableRes icon: Int, text: String, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YIcon(icon, null, tint = yPalette.textSecondary, size = 22.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = yPalette.textSecondary) }
        }
    }
}
