package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.DriveEta
import com.example.findme.data.Geo
import com.example.findme.data.SavedPlace
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.components.ListRow
import com.example.findme.ui.components.QuickCategories
import com.example.findme.ui.components.SectionTitle
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.meet.ActiveMeetCard
import com.example.findme.ui.meet.MeetViewModel
import com.example.findme.ui.meet.MeetingRow
import com.example.findme.ui.meet.featuredMeeting
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.geometry.Point

/** Height of the collapsed main sheet, with and without the meet-up card (see [HomeSheetContent]). */
val HOME_PEEK = 166.dp
val HOME_PEEK_WITH_MEET = 252.dp

/**
 * Main sheet: the current meet-up, Home / Work / Saved tiles and category chips;
 * pulled up, the meet-ups, saved places and search history.
 */
@Composable
fun HomeSheetContent(vm: MapViewModel, actions: PlaceActions, meet: MeetViewModel) {
    val featured = meet.featuredMeeting()
    LazyColumn(Modifier.fillMaxSize()) {
        // One item for the card and the tiles: a card arriving later must not push the list
        // (a lazy list keeps its first visible item in place, which would hide the card above it).
        item(key = "top") { Column {
            if (featured != null) {
                ActiveMeetCard(meet, featured, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SavedTile(R.drawable.ms_home, "Home", vm.home, vm.homeEta, vm) {
                    val home = vm.home
                    if (home != null) vm.routeToSaved(home, "Home")
                    else vm.message = "Open a place and tap “Set as home”"
                }
                SavedTile(R.drawable.ms_work, "Work", vm.work, vm.workEta, vm) {
                    val work = vm.work
                    if (work != null) vm.routeToSaved(work, "Work")
                    else vm.message = "Open a place and tap “Set as work”"
                }
                val count = vm.bookmarks.size
                if (count > 0) {
                    Tile(R.drawable.ms_bookmarks, "Saved", if (count == 1) "1 place" else "$count places") {
                        vm.openBookmarks()
                    }
                } else {
                    Tile(R.drawable.ms_add, null, "Add place", outlined = true) {
                        vm.search.open()
                        vm.message = "Find a place, then tap Save"
                    }
                }
            }
        } }
        item(key = "chips") {
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(QuickCategories, key = { it.title }) { category ->
                    Chip(category.icon, category.title) { vm.submitSearch(category.query) }
                }
                item(key = "taxi") { Chip(R.drawable.ms_local_taxi, "Taxi") { actions.taxi(null) } }
            }
        }

        // ---- Pulled up ----
        item(key = "meet-title") {
            SectionTitle("Meet-ups") {
                if (meet.meetings.isNotEmpty()) {
                    Text(
                        "See all",
                        color = yPalette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { meet.openList() }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
        }
        if (meet.meetings.isEmpty()) {
            item(key = "meet-join") {
                ListRow(
                    icon = R.drawable.ms_person_add,
                    title = "Join a friend's meet-up",
                    subtitle = "Enter the code from their invite",
                ) { meet.openJoin() }
            }
        } else {
            items(meet.meetings, key = { "m:${it.id}" }) { m ->
                MeetingRow(m, vm.userLocation) { meet.openMeeting(m.id) }
            }
        }
        if (vm.bookmarks.isNotEmpty()) {
            item(key = "saved-title") { SectionTitle("Saved") }
            items(vm.bookmarks, key = { "b:${it.lat},${it.lon},${it.name}" }) { saved ->
                ListRow(
                    icon = R.drawable.ms_bookmark,
                    title = saved.name,
                    subtitle = saved.address,
                    trailing = Geo.distanceText(vm.userLocation, Point(saved.lat, saved.lon)),
                    iconBackground = yPalette.coralSoft,
                    iconTint = yPalette.coralText,
                ) { vm.openSaved(saved) }
            }
        }
        if (vm.search.history.isNotEmpty()) {
            item(key = "history-title") {
                SectionTitle("History") {
                    Text(
                        "Clear",
                        color = yPalette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.search.clearHistory() }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }
            items(vm.search.history.take(10), key = { "h:$it" }) { q ->
                ListRow(icon = R.drawable.ms_history, title = q) { vm.submitSearch(q) }
            }
        }
        item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

/** Home or Work: travel time colored by traffic, the distance without it, or "Set" when not saved. */
@Composable
private fun RowScope.SavedTile(
    @DrawableRes icon: Int,
    title: String,
    saved: SavedPlace?,
    eta: DriveEta?,
    vm: MapViewModel,
    onClick: () -> Unit,
) {
    val p = yPalette
    val (detail, color) = when {
        saved == null -> "Set" to p.textSecondary
        eta != null -> eta.text.replace(" ", " ") to when {
            eta.trafficRatio < 1.2 -> p.onSuccessSoft
            eta.trafficRatio < 1.5 -> p.onWarningSoft
            else -> p.onDangerSoft
        }
        else -> (Geo.distanceText(vm.userLocation, Point(saved.lat, saved.lon)) ?: "Go") to p.textSecondary
    }
    Tile(icon, title, detail, detailColor = color, onClick = onClick)
}

/** 72-dp tile: icon + title on top, a short detail below. */
@Composable
private fun RowScope.Tile(
    @DrawableRes icon: Int,
    title: String?,
    detail: String,
    detailColor: Color = yPalette.textSecondary,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .height(72.dp)
            .clip(shape)
            .then(
                if (outlined) Modifier.border(1.5.dp, yPalette.divider, shape)
                else Modifier.background(yPalette.field)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YIcon(icon, null, tint = yPalette.icon, size = 18.dp)
            if (title != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = detailColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Gray pill chip with an icon: a quick search. */
@Composable
private fun Chip(@DrawableRes icon: Int, title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(yPalette.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YIcon(icon, null, tint = yPalette.icon, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = yPalette.textPrimary,
            maxLines = 1,
        )
    }
}
