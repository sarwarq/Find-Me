package com.example.findme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.Place
import com.example.findme.maps.CategoryStyles
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.components.CloseCircle
import com.example.findme.ui.components.ListRow
import com.example.findme.ui.components.RatingRow
import com.example.findme.ui.components.SectionTitle
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.geometry.Point

/**
 * White card at the top of the map with the current query, shown over search results
 * and cards opened from search. Reports its bottom edge in pixels.
 */
@Composable
fun TopSearchBar(text: String, onClick: () -> Unit, onBottomChanged: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 10.dp)
            .fillMaxWidth()
            .onGloballyPositioned { onBottomChanged(it.boundsInRoot().bottom.toInt()) },
        shape = RoundedCornerShape(16.dp),
        color = yPalette.sheet,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier
                .height(56.dp)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                YIcon(R.drawable.ms_search, null, tint = yPalette.icon, size = 26.dp)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(yPalette.field)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ResultsSheetContent(vm: MapViewModel, query: String) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                query,
                style = MaterialTheme.typography.titleLarge,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (vm.search.results.isNotEmpty()) {
                Text(
                    "${vm.search.results.size}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = yPalette.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
            CloseCircle(onClick = { vm.goHome() })
        }
        when {
            vm.search.loading -> Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = yPalette.accent)
                Spacer(Modifier.width(12.dp))
                Text("Searching...", color = yPalette.textSecondary, style = MaterialTheme.typography.bodyLarge)
            }

            vm.search.error != null -> Text(
                vm.search.error.orEmpty(),
                color = yPalette.textSecondary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(vm.search.results, key = { i, p -> "$i:${p.id}" }) { _, place ->
                    ResultRow(place, Geo.distanceText(vm.userLocation, place.point)) { vm.onResultClick(place) }
                    HorizontalDivider(Modifier.padding(start = 68.dp), color = yPalette.divider)
                }
                item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ResultRow(place: Place, distance: String?, onClick: () -> Unit) {
    val style = CategoryStyles.of(place.categoryClass, place.category, place.isBusiness)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Color(style.color)),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(style.icon, null, tint = Color.White, size = 20.dp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    place.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                distance?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
                }
            }
            place.category?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary, maxLines = 1)
            }
            place.rating?.let {
                Spacer(Modifier.height(4.dp))
                RatingRow(it, place.ratingsCount, starSize = 15.dp)
            }
            place.hoursText?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (place.isOpenNow) {
                        true -> YColors.Green
                        false -> YColors.Red
                        null -> yPalette.textSecondary
                    },
                )
            }
            place.address?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Saved": home, work and bookmarked places. */
@Composable
fun SavedSheetContent(vm: MapViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Saved",
                style = MaterialTheme.typography.titleLarge,
                color = yPalette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            CloseCircle(onClick = { vm.onBack() })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val home = vm.home
                ListRow(
                    icon = R.drawable.ms_home,
                    title = "Home",
                    subtitle = home?.address ?: home?.name ?: "Open a place and tap “Set as home”",
                    trailing = vm.homeEta?.text,
                ) { home?.let { vm.routeToSaved(it, "Home") } }
            }
            item {
                val work = vm.work
                ListRow(
                    icon = R.drawable.ms_work,
                    title = "Work",
                    subtitle = work?.address ?: work?.name ?: "Open a place and tap “Set as work”",
                    trailing = vm.workEta?.text,
                ) { work?.let { vm.routeToSaved(it, "Work") } }
            }
            item { SectionTitle("My places") }
            if (vm.bookmarks.isEmpty()) {
                item {
                    Text(
                        "Save places with the bookmark button on a place card",
                        color = yPalette.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(vm.bookmarks, key = { "s:${it.lat},${it.lon},${it.name}" }) { saved ->
                ListRow(
                    icon = R.drawable.ms_bookmark,
                    title = saved.name,
                    subtitle = saved.address,
                    trailing = Geo.distanceText(vm.userLocation, Point(saved.lat, saved.lon)),
                    iconBackground = YColors.Red,
                    iconTint = Color.White,
                ) { vm.openSaved(saved) }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}
