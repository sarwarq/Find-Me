package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.Role
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
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.components.ratingsText
import com.example.findme.ui.meet.FriendHereRow
import com.example.findme.ui.meet.MeetViewModel
import com.example.findme.ui.meet.friendsAt
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.geometry.Point
import java.util.Locale

/** External actions that need an Android Context. */
class PlaceActions(
    val call: (String) -> Unit,
    val openSite: (String) -> Unit,
    val share: (Place) -> Unit,
    val copy: (String) -> Unit,
    /** Opens a taxi app, to [Point] when given. */
    val taxi: (Point?) -> Unit,
)

/** Collapsed place card before its header is measured. */
val PLACE_PEEK = 208.dp

/** Grabber above the content plus the gap left under the header in the collapsed sheet. */
private val PEEK_EXTRA = 22.dp + 16.dp

/**
 * Place card: name, "category · address · 8 min", rating and opening hours,
 * Route / Meet here / Save, a friend who is already there, then the details.
 * [onPeek] gets the collapsed height: everything down to the action row (or the friend row).
 */
@Composable
fun PlaceCardContent(
    vm: MapViewModel,
    card: Screen.PlaceCard,
    actions: PlaceActions,
    meet: MeetViewModel,
    onPeek: (Dp) -> Unit,
) {
    val place = card.place
    val bookmarked = vm.isBookmarked(place)
    val eta = vm.cardEta?.takeIf { it.first == place.id }?.second
    val friends = meet.friendsAt(place.point)
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      Column(Modifier.onSizeChanged { onPeek(with(density) { it.height.toDp() } + PEEK_EXTRA) }) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row {
                Text(
                    place.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                CloseCircle(onClick = { vm.onBack() }, size = 36.dp)
            }

            // "Coffee shop · Garden St 14 · 8 min · 3.2 km"
            val distance = eta?.let { "${it.text} · ${it.distanceText}" }
                ?: Geo.distanceText(vm.userLocation, place.point)
            val subtitle = listOfNotNull(
                place.category,
                place.address?.takeIf { it != place.name },
                distance,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // "★ 4.7  312 ratings · Open until 23:00"
            if (place.rating != null || place.hoursText != null) {
                Spacer(Modifier.height(6.dp))
                RatingHoursLine(place)
            }
        }

        if (card.loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(2.dp),
                color = yPalette.accent,
                trackColor = yPalette.field,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionPill("Route", R.drawable.ms_route, { vm.routeTo(place) }, primary = true, modifier = Modifier.weight(1f))
            ActionPill("Meet here", R.drawable.ms_person_add, { meet.startNew(place) }, coral = true, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (bookmarked) yPalette.accentSoft else yPalette.field)
                    .clickable(role = Role.Button) { vm.toggleBookmark(place) },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(
                    R.drawable.ms_bookmark,
                    if (bookmarked) "Remove from saved" else "Save place",
                    tint = if (bookmarked) yPalette.accent else yPalette.icon,
                    size = 22.dp,
                )
            }
        }

        if (friends.isNotEmpty()) {
            FriendHereRow(meet, friends, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
      }

        Spacer(Modifier.height(12.dp))
        place.address?.let { InfoRow(R.drawable.ms_location_on, it, hint = "Tap to copy") { actions.copy(it) } }
        place.hoursText?.let { InfoRow(R.drawable.ms_schedule, it, onClick = null) }
        place.phone?.let { phone -> InfoRow(R.drawable.ms_call, phone, hint = "Call") { actions.call(phone) } }
        place.website?.let { site ->
            InfoRow(R.drawable.ms_language, site.removePrefix("https://").removePrefix("http://")) { actions.openSite(site) }
        }
        InfoRow(R.drawable.ms_local_taxi, "Ride with a taxi") { actions.taxi(place.point) }
        InfoRow(R.drawable.ms_share, "Share") { actions.share(place) }
        val coords = Geo.coordinatesText(place.lat, place.lon)
        InfoRow(R.drawable.ms_my_location, coords, hint = "Tap to copy", last = true) { actions.copy(coords) }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = yPalette.field, thickness = 8.dp)
        InfoRow(R.drawable.ms_route, "Directions from here") { vm.routeFrom(place) }
        InfoRow(R.drawable.ms_home, "Set as home") { vm.setHome(place) }
        InfoRow(R.drawable.ms_work, "Set as work", last = true) { vm.setWork(place) }

        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}

/** Star rating with the count, then the opening hours in green or red. */
@Composable
private fun RatingHoursLine(place: Place) {
    val p = yPalette
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (place.rating != null) {
            YIcon(R.drawable.ms_star, null, tint = YColors.Star, size = 16.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            buildAnnotatedString {
                place.rating?.let { rating ->
                    withStyle(SpanStyle(color = p.textPrimary, fontWeight = FontWeight.Bold)) {
                        append(String.format(Locale.US, "%.1f", rating))
                    }
                    place.ratingsCount?.takeIf { it > 0 }?.let {
                        withStyle(SpanStyle(color = p.textSecondary)) { append("  ${ratingsText(it)}") }
                    }
                }
                place.hoursText?.let { hours ->
                    if (place.rating != null) withStyle(SpanStyle(color = p.textSecondary)) { append(" · ") }
                    val color = when (place.isOpenNow) {
                        true -> p.onSuccessSoft
                        false -> p.onDangerSoft
                        null -> p.textSecondary
                    }
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(hours) }
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Detail row: gray icon, text (and a hint under it), a divider below unless [last]. */
@Composable
private fun InfoRow(
    @DrawableRes icon: Int,
    text: String,
    hint: String? = null,
    last: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(icon, null, tint = yPalette.textSecondary, size = 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = yPalette.textSecondary) }
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 52.dp), color = yPalette.divider)
    }
}
