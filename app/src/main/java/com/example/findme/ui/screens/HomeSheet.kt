package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.components.ListRow
import com.example.findme.ui.components.QuickCategories
import com.example.findme.ui.components.SectionTitle
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.meet.MeetColor
import com.example.findme.ui.meet.MeetViewModel
import com.example.findme.ui.meet.MeetingRow
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.geometry.Point

/** Main sheet: search field, rubrics, services, then bookmarks and history when pulled up. */
@Composable
fun HomeSheetContent(vm: MapViewModel, actions: PlaceActions, meet: MeetViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    text = "Search here",
                    onClick = { vm.search.open() },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(width = 56.dp, height = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(yPalette.field)
                        .clickable { vm.openBookmarks() },
                    contentAlignment = Alignment.Center,
                ) {
                    YIcon(R.drawable.ms_bookmarks, "Saved", tint = yPalette.iconSecondary, size = 24.dp)
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(QuickCategories) { category ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { vm.submitSearch(category.query) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFA6AAAB)),
                            contentAlignment = Alignment.Center,
                        ) {
                            YIcon(category.icon, null, tint = Color.White, size = 18.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            category.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = yPalette.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        item { HorizontalDivider(color = yPalette.divider) }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            ) {
                ServiceTile("Meet-ups", R.drawable.ms_group, MeetColor, badge = meet.meetings.size) { meet.openList() }
                ServiceTile("Navigator", R.drawable.ic_nav_arrow, Color.Unspecified) { vm.startRoutePlanning() }
                ServiceTile("Fuel", R.drawable.ms_local_gas_station, Color(0xFF19B74C)) { vm.submitSearch("Gas stations") }
                ServiceTile("Transport", R.drawable.ms_directions_bus, Color(0xFF1E7BFF)) { vm.submitSearch("Public transport stops") }
                ServiceTile("Ride", R.drawable.ms_local_taxi, Color(0xFFFFC300)) { actions.taxi(null) }
            }
        }

        if (meet.meetings.isNotEmpty()) {
            item { SectionTitle("Meet-ups") }
            items(meet.meetings, key = { "m:${it.id}" }) { m ->
                MeetingRow(m, vm.userLocation) { meet.openMeeting(m.id) }
            }
        }
        if (vm.bookmarks.isNotEmpty()) {
            item { SectionTitle("Saved") }
            items(vm.bookmarks, key = { "b:${it.lat},${it.lon},${it.name}" }) { saved ->
                ListRow(
                    icon = R.drawable.ms_bookmark,
                    title = saved.name,
                    subtitle = saved.address,
                    trailing = Geo.distanceText(vm.userLocation, Point(saved.lat, saved.lon)),
                    iconBackground = YColors.Red,
                    iconTint = Color.White,
                ) { vm.openSaved(saved) }
            }
        }
        if (vm.search.history.isNotEmpty()) {
            item {
                SectionTitle("History") {
                    Text(
                        "Clear",
                        color = yPalette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { vm.search.clearHistory() },
                    )
                }
            }
            items(vm.search.history.take(10), key = { "h:$it" }) { q ->
                ListRow(icon = R.drawable.ms_history, title = q) { vm.submitSearch(q) }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
    }
}

@Composable
private fun RowScope.ServiceTile(
    title: String,
    @DrawableRes icon: Int,
    tint: Color,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(yPalette.field),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(icon, null, tint = tint, size = 34.dp)
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(YColors.Red),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 9) "9+" else badge.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = yPalette.textPrimary,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Gray search field that opens the full-screen search. */
@Composable
fun SearchField(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = yPalette.textSecondary,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(yPalette.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
