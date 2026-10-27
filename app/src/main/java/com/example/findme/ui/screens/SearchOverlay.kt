package com.example.findme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.SavedPlace
import com.example.findme.data.WaypointSlot
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.components.Categories
import com.example.findme.ui.components.CategoryTile
import com.example.findme.ui.components.CloseCircle
import com.example.findme.ui.components.Grabber
import com.example.findme.ui.components.ListRow
import com.example.findme.ui.components.SegmentedControl
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.search.SuggestItem

/** Full-screen search: a tall sheet over the map with rubrics, history and suggestions. */
@Composable
fun SearchOverlay(vm: MapViewModel, onVoice: () -> Unit) {
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(vm.search.query, TextRange(vm.search.query.length))) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm.search.query) {
        if (vm.search.query != field.text) field = TextFieldValue(vm.search.query, TextRange(vm.search.query.length))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val slot = vm.search.pickSlot
    val placeholder = when (slot) {
        WaypointSlot.From -> "From"
        WaypointSlot.To -> "To"
        WaypointSlot.Stop -> vm.route.pickStopTitle
        null -> "Search here"
    }

    Box(Modifier.fillMaxSize().background(Color(0x33000000)).clickable(enabled = false) {}) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = yPalette.sheet,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.imePadding()) {
                Grabber()
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.search.close() },
                        contentAlignment = Alignment.Center,
                    ) {
                        YIcon(R.drawable.ms_search, "Back", tint = yPalette.icon, size = 26.dp)
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(yPalette.field)
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (field.text.isEmpty()) {
                                Text(placeholder, color = yPalette.textSecondary, style = MaterialTheme.typography.bodyLarge)
                            }
                            BasicTextField(
                                value = field,
                                onValueChange = {
                                    field = it
                                    if (it.text != vm.search.query) vm.search.onQueryChange(it.text)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = yPalette.textPrimary),
                                cursorBrush = SolidColor(yPalette.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { vm.submitSearch(field.text) }),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        }
                        if (field.text.isEmpty()) {
                            Box(Modifier.size(44.dp).clickable(onClick = onVoice), contentAlignment = Alignment.Center) {
                                YIcon(R.drawable.ms_mic, "Voice search", tint = yPalette.textSecondary, size = 22.dp)
                            }
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    CloseCircle(onClick = {
                        if (field.text.isNotEmpty()) vm.search.onQueryChange("") else vm.search.close()
                    })
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    if (slot != null && field.text.isEmpty()) {
                        item {
                            ListRow(
                                icon = R.drawable.ms_near_me,
                                title = "Current location",
                                iconBackground = YColors.Blue,
                                iconTint = Color.White,
                            ) { vm.useMyLocationFor(slot) }
                        }
                        vm.home?.let { home -> item { SavedRow(R.drawable.ms_home, "Home", home) { vm.pickSaved(home, slot) } } }
                        vm.work?.let { work -> item { SavedRow(R.drawable.ms_work, "Work", work) { vm.pickSaved(work, slot) } } }
                        items(vm.bookmarks) { saved ->
                            SavedRow(R.drawable.ms_bookmark, saved.name, saved) { vm.pickSaved(saved, slot) }
                        }
                    }

                    if (field.text.isEmpty()) {
                        if (slot == null) {
                            item {
                                SegmentedControl(
                                    items = listOf("Categories", "History"),
                                    selected = tab,
                                    onSelect = { tab = it },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                        if (slot == null && tab == 0) {
                            items(Categories.chunked(4)) { row ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                                    row.forEach { category ->
                                        CategoryTile(category, Modifier.weight(1f)) { vm.submitSearch(category.query) }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            if (vm.search.history.isEmpty() && slot == null) {
                                item {
                                    Text(
                                        "Your searches will appear here",
                                        color = yPalette.textSecondary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(24.dp),
                                    )
                                }
                            }
                            items(vm.search.history.take(15)) { q ->
                                ListRow(icon = R.drawable.ms_history, title = q) { vm.submitSearch(q) }
                            }
                        }
                    } else {
                        items(vm.search.suggestions) { s ->
                            ListRow(
                                icon = when (s.type) {
                                    SuggestItem.Type.BUSINESS -> R.drawable.ms_storefront
                                    SuggestItem.Type.TRANSIT -> R.drawable.ms_directions_bus
                                    SuggestItem.Type.TOPONYM -> R.drawable.ms_location_on
                                    else -> R.drawable.ms_search
                                },
                                title = s.title,
                                subtitle = s.subtitle,
                                trailing = s.distance,
                            ) { vm.onSuggestClick(s) }
                        }
                    }
                    item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SavedRow(icon: Int, title: String, saved: SavedPlace, onClick: () -> Unit) {
    ListRow(icon = icon, title = title, subtitle = saved.address ?: saved.name, onClick = onClick)
}
