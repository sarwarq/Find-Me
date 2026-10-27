package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.example.findme.ui.nav.NavProgress
import com.example.findme.ui.route.RouteController
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.NavTrack
import com.example.findme.data.RouteVariant
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import com.example.findme.data.WaypointSlot
import com.example.findme.data.rideName
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.components.CircleIconButton
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette

@DrawableRes
fun TravelMode.iconRes(): Int = when (this) {
    TravelMode.Car -> R.drawable.ms_directions_car
    TravelMode.Transit -> R.drawable.ms_directions_bus
    TravelMode.Walk -> R.drawable.ms_directions_walk
    TravelMode.Bike -> R.drawable.ms_pedal_bike
    TravelMode.Scooter -> R.drawable.ms_electric_scooter
}

/** Route sheet: from/to header, travel mode tabs, route variants and the "Go" button. */
@Composable
fun RouteSheetContent(vm: MapViewModel) {
    val result = vm.route.results[vm.route.mode]
    val variants = result?.variants.orEmpty()

    Column(Modifier.fillMaxSize()) {
        RouteHeader(vm)
        StopsEditor(vm)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(TravelMode.entries) { mode ->
                val r = vm.route.results[mode]
                val label = when {
                    r == null -> null
                    r.loading -> "..."
                    r.variants.isNotEmpty() -> r.variants.first().durationText
                    else -> "—"
                }
                ModeTab(mode, label, selected = mode == vm.route.mode) { vm.route.selectMode(mode) }
            }
        }

        Box(Modifier.fillMaxWidth().heightIn(min = 112.dp), contentAlignment = Alignment.CenterStart) {
            when {
                vm.route.from == null || vm.route.to == null -> CenterText("Choose where to start and where to go")
                result == null || result.loading -> Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = yPalette.accent)
                    Spacer(Modifier.width(12.dp))
                    Text("Building a route...", color = yPalette.textSecondary, style = MaterialTheme.typography.bodyLarge)
                }
                variants.isEmpty() -> CenterText(result.error ?: "No route found")
                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(variants) { index, variant ->
                        VariantCard(variant, index, index == vm.route.selectedVariant, vm.route.mode) { vm.route.selectVariant(index) }
                    }
                }
            }
        }

        val selected = vm.route.currentVariant
        if (vm.route.mode == TravelMode.Transit && selected != null && result?.loading != true) {
            TransitSummary(selected)
        }

        val onFoot = vm.route.mode in setOf(TravelMode.Walk, TravelMode.Bike, TravelMode.Scooter)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable {
                    if (onFoot) {
                        vm.pickWaypoint(WaypointSlot.To)
                    } else {
                        vm.message = "Leaving now, arriving at ${vm.route.currentVariant?.let { Geo.arrivalText(it.durationSeconds) } ?: "—"}"
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(
                    if (onFoot) R.drawable.ms_add_location_alt else R.drawable.ms_schedule,
                    if (onFoot) "Change destination" else "Departure time",
                    tint = yPalette.iconSecondary, size = 26.dp,
                )
            }
            Spacer(Modifier.width(8.dp))
            val canGo = vm.route.currentVariant?.track != null
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canGo) yPalette.accent else yPalette.accent.copy(alpha = 0.4f))
                    .clickable(enabled = vm.route.currentVariant != null) { vm.nav.start() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (onFoot) "Let's go" else "Go", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.layersOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_tune, "Options", tint = yPalette.iconSecondary, size = 26.dp)
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun RouteHeader(vm: MapViewModel) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleIconButton(R.drawable.ms_arrow_back_ios_new, "Back", onClick = { vm.onBack() }, size = 30.dp)
        Column(
            Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "From: ${vm.route.from?.name ?: "choose a point"}",
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.pickWaypoint(WaypointSlot.From) },
            )
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.pickWaypoint(WaypointSlot.To) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "To: ${vm.route.to?.name ?: "choose a point"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                YIcon(R.drawable.ms_arrow_drop_down, null, tint = yPalette.textPrimary, size = 22.dp)
            }
        }
        Box {
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_more_vert, "More", tint = yPalette.textSecondary, size = 24.dp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = yPalette.sheet) {
                DropdownMenuItem(
                    text = { Text("Swap", style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { YIcon(R.drawable.ms_swap_vert, null, size = 22.dp) },
                    onClick = { menu = false; vm.route.swap() },
                )
                DropdownMenuItem(
                    text = { Text("Change start point", style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = { YIcon(R.drawable.ms_my_location, null, size = 22.dp) },
                    onClick = { menu = false; vm.pickWaypoint(WaypointSlot.From) },
                )
            }
        }
    }
}

/**
 * Stops on the way, under the header: "1 Bazaar", "2 Pharmacy"... Tap a stop to change it,
 * drag the handle to reorder, × to remove; then "+ Add stop" and "Optimize order".
 * Every row is [STOP_ROW_DP] high; the sheet height in MapScreen counts on that.
 */
@Composable
private fun StopsEditor(vm: MapViewModel) {
    val stops = vm.route.stops
    val density = LocalDensity.current
    val rowPx = with(density) { STOP_ROW_DP.dp.toPx() }
    val handlePx = with(density) { 40.dp.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var moved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp)) {
        if (stops.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o ->
                            // Only the handle (second from the right) starts a drag.
                            val onHandle = o.x > size.width - handlePx * 2 && o.x < size.width - handlePx
                            dragging = if (onHandle) (o.y / rowPx).toInt().coerceIn(0, stops.lastIndex) else -1
                            dragOffset = 0f
                            moved = false
                        },
                        onDrag = { change, amount ->
                            if (dragging < 0) return@detectDragGestures
                            change.consume()
                            dragOffset += amount.y
                            while (dragOffset > rowPx / 2 && dragging < stops.lastIndex) {
                                vm.route.moveStop(dragging, dragging + 1)
                                dragging++
                                dragOffset -= rowPx
                                moved = true
                            }
                            while (dragOffset < -rowPx / 2 && dragging > 0) {
                                vm.route.moveStop(dragging, dragging - 1)
                                dragging--
                                dragOffset += rowPx
                                moved = true
                            }
                        },
                        onDragEnd = {
                            if (moved) vm.route.onStopsReordered()
                            dragging = -1
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            if (moved) vm.route.onStopsReordered()
                            dragging = -1
                            dragOffset = 0f
                        },
                    )
                },
            ) {
                stops.forEachIndexed { i, stop ->
                    val isDragged = i == dragging
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(STOP_ROW_DP.dp)
                            .zIndex(if (isDragged) 1f else 0f)
                            .offset { IntOffset(0, if (isDragged) dragOffset.roundToInt() else 0) }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDragged) yPalette.field else Color.Transparent),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StopBadge(i + 1)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stop.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = yPalette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { vm.route.editStop(i) },
                        )
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            YIcon(R.drawable.ms_drag_indicator, "Drag to reorder", tint = yPalette.iconSecondary, size = 22.dp)
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { vm.route.removeStop(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            YIcon(R.drawable.ms_close, "Remove stop", tint = yPalette.iconSecondary, size = 20.dp)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(STOP_ROW_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stops.size < RouteController.MAX_STOPS) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.route.addStop() }.padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YIcon(R.drawable.ms_add, null, tint = yPalette.accent, size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Add stop", style = MaterialTheme.typography.labelLarge, color = yPalette.accent)
                }
            }
            Spacer(Modifier.weight(1f))
            when {
                vm.route.optimizing -> CircularProgressIndicator(
                    Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp, color = yPalette.accent,
                )
                vm.route.stopsBeforeOptimize != null -> TextAction("Undo order") { vm.route.undoOptimize() }
                stops.size >= 2 -> TextAction("Optimize order") { vm.route.optimizeOrder() }
            }
        }
    }
}

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = yPalette.accent,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Blue circle with the stop number, like the pin on the map. */
@Composable
private fun StopBadge(number: Int) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF196DFF)),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** In guidance: "② Pharmacy · 1.2 km · 5 min" until the next stop. */
@Composable
private fun NextStopLine(vm: MapViewModel, progress: NavProgress) {
    val stop = vm.route.stops.getOrNull(progress.nextStop) ?: return
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StopBadge(progress.nextStop + 1)
        Spacer(Modifier.width(10.dp))
        Text(
            stop.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = yPalette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${Geo.shortDistance(progress.toStopMeters)} · ${Geo.durationText(progress.toStopSeconds)}",
            style = MaterialTheme.typography.bodyMedium,
            color = yPalette.textSecondary,
            maxLines = 1,
        )
    }
}

/** Height of one stop row and of the "+ Add stop" row; MapScreen sizes the sheet by it. */
private const val STOP_ROW_DP = 40

@Composable
private fun ModeTab(mode: TravelMode, label: String?, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color.White else yPalette.iconSecondary
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) yPalette.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YIcon(mode.iconRes(), mode.title, tint = fg, size = 22.dp)
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = if (selected) Color.White else yPalette.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VariantCard(variant: RouteVariant, index: Int, selected: Boolean, mode: TravelMode, onClick: () -> Unit) {
    if (mode == TravelMode.Transit) {
        TransitCard(variant, selected, onClick)
        return
    }
    val bg = if (selected) yPalette.accent else yPalette.field
    val primary = if (selected) Color.White else yPalette.textPrimary
    val secondary = if (selected) Color.White.copy(alpha = 0.75f) else yPalette.textSecondary
    Column(
        Modifier
            .widthIn(min = 112.dp, max = 170.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            when {
                index == 0 -> "Fast"
                variant.hasIncidents -> "Road closures"
                else -> "Alternative"
            },
            color = secondary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(variant.durationText, color = primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        run {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(variant.distanceText.orEmpty(), color = secondary, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (variant.hasIncidents) {
                    Spacer(Modifier.width(6.dp))
                    YIcon(R.drawable.ms_car_crash, "Accidents or closures", tint = if (selected) Color.White else YColors.Red, size = 18.dp)
                }
            }
        }
    }
}

@DrawableRes
private fun vehicleIcon(type: String?): Int = when (type) {
    "underground" -> R.drawable.ms_subway
    "tramway", "tram" -> R.drawable.ms_tram
    "railway", "suburban" -> R.drawable.ms_train
    else -> R.drawable.ms_directions_bus
}

/** Public transport variant: "1 hr 22 min" and the lines, e.g. M [2] ▸ M [1] ▸ 🚌 [199]. */
@Composable
private fun TransitCard(variant: RouteVariant, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .widthIn(min = 120.dp, max = 300.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) yPalette.accent else yPalette.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            variant.durationText,
            color = if (selected) Color.White else yPalette.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        TransitLegsRow(variant, onBlue = selected)
    }
}

@Composable
private fun TransitLegsRow(variant: RouteVariant, onBlue: Boolean) {
    val rides = variant.legs.filter { it.kind == LegKind.Transport }
    if (rides.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            YIcon(R.drawable.ms_directions_walk, null, tint = if (onBlue) Color.White else yPalette.iconSecondary, size = 20.dp)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        rides.forEachIndexed { i, leg ->
            if (i > 0) {
                YIcon(
                    R.drawable.ms_play_arrow, null,
                    tint = if (onBlue) Color.White.copy(alpha = 0.7f) else yPalette.textSecondary,
                    size = 14.dp,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
            VehicleBadge(leg.vehicle, onBlue)
            Spacer(Modifier.width(5.dp))
            LineChip(leg.label, leg.color, outlined = onBlue)
        }
    }
}

/** Gray bus icon, or the blue "M" sign for the metro. */
@Composable
private fun VehicleBadge(vehicle: String?, onBlue: Boolean) {
    if (vehicle == "underground") {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (onBlue) Color.White else Color(0xFF1E7BFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "M",
                color = if (onBlue) Color(0xFF1E7BFF) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    } else {
        YIcon(vehicleIcon(vehicle), null, tint = if (onBlue) Color.White else yPalette.iconSecondary, size = 22.dp)
    }
}

/** Line number on the line's color, like "43m" on purple. */
@Composable
private fun LineChip(label: String, color: Int?, outlined: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .height(24.dp)
            .widthIn(min = 26.dp)
            .clip(shape)
            .background(Color(color ?: 0xFF196DFF.toInt()))
            .then(if (outlined) Modifier.border(1.5.dp, Color.White, shape) else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.ifEmpty { "—" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** "🚶 14 min on foot   🚌 [43m]   Departs 11:20" under the variant cards. */
@Composable
private fun TransitSummary(variant: RouteVariant) {
    val first = variant.legs.firstOrNull { it.kind == LegKind.Transport }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (variant.walkSeconds > 0) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(yPalette.field),
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_directions_walk, null, tint = yPalette.iconSecondary, size = 18.dp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${Geo.durationText(variant.walkSeconds)} on foot",
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.width(16.dp))
        }
        if (first != null) {
            VehicleBadge(first.vehicle, onBlue = false)
            Spacer(Modifier.width(5.dp))
            LineChip(first.label, first.color, outlined = false)
            variant.departureText?.let {
                Spacer(Modifier.width(10.dp))
                Text(
                    "Departs $it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Text(
        text,
        color = yPalette.textSecondary,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

// ---------------------------------------------------------------------------
// Guidance mode
// ---------------------------------------------------------------------------

@DrawableRes
private fun Maneuver.iconRes(): Int = when (this) {
    Maneuver.Straight -> R.drawable.ms_straight
    Maneuver.SlightLeft, Maneuver.ForkLeft -> R.drawable.ms_turn_slight_left
    Maneuver.SlightRight -> R.drawable.ms_turn_slight_right
    Maneuver.ForkRight -> R.drawable.ms_fork_right
    Maneuver.Left, Maneuver.HardLeft -> R.drawable.ms_turn_left
    Maneuver.Right, Maneuver.HardRight -> R.drawable.ms_turn_right
    Maneuver.UturnLeft -> R.drawable.ms_u_turn_left
    Maneuver.UturnRight -> R.drawable.ms_u_turn_right
    Maneuver.Roundabout -> R.drawable.ms_roundabout_right
    Maneuver.Finish -> R.drawable.ms_sports_score
}

/** Current speed and the speed limit, top right in guidance mode. */
@Composable
fun SpeedBadges(vm: MapViewModel, modifier: Modifier = Modifier) {
    val progress = vm.nav.progress ?: return
    Row(
        modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val limit = progress.limitKmh
        val over = limit != null && progress.speedKmh > limit
        Surface(shape = CircleShape, color = yPalette.sheet, shadowElevation = 4.dp, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    progress.speedKmh.toString(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (over) YColors.Red else yPalette.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        if (limit != null) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp, modifier = Modifier.size(64.dp)) {
                Box(
                    Modifier.border(6.dp, Color(0xFFE24742), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        limit.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF21201F),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

/** Bottom bar of guidance: next maneuver, distance, arrival time, time left, progress and exit. */
@Composable
fun NavigationPanel(vm: MapViewModel, modifier: Modifier = Modifier) {
    val variant = vm.route.currentVariant
    val progress = vm.nav.progress
    val remainingMeters = progress?.remainingMeters ?: variant?.distanceMeters ?: 0.0
    val remainingSeconds = progress?.remainingSeconds ?: variant?.durationSeconds ?: 0.0
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = yPalette.sheet,
        shadowElevation = 10.dp,
    ) {
        val steps = variant?.steps.orEmpty()
        val stepIndex = progress?.step ?: -1
        val step = steps.getOrNull(stepIndex)
        Column {
        progress?.takeIf { it.nextStop >= 0 }?.let { NextStopLine(vm, it) }
        if (step != null) TransitInstruction(steps, stepIndex)
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(yPalette.field),
                contentAlignment = Alignment.Center,
            ) {
                if (step != null) {
                    val icon = if (step.kind == LegKind.Walk) R.drawable.ms_directions_walk else vehicleIcon(step.vehicle)
                    YIcon(icon, null, tint = yPalette.iconSecondary, size = 28.dp)
                } else {
                    YIcon((progress?.next ?: Maneuver.Straight).iconRes(), "Next turn", tint = yPalette.iconSecondary, size = 28.dp)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NavNumber(Geo.shortDistance(remainingMeters))
                    NavNumber(Geo.arrivalText(remainingSeconds))
                    NavNumber(Geo.durationText(remainingSeconds))
                }
                Spacer(Modifier.height(6.dp))
                RouteProgressBar(variant?.track, progress?.fraction ?: 0f)
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(yPalette.field).clickable { vm.nav.stop() },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_close, "End", tint = yPalette.iconSecondary, size = 26.dp)
            }
        }
        }
    }
}

/**
 * What to do now on public transport, above the guidance numbers:
 * "Walk to «Central Park» · bus 12 at 14:05" or "Bus 12 · 6 stops · get off at «Market»".
 */
@Composable
private fun TransitInstruction(steps: List<TransitStep>, index: Int) {
    val step = steps[index]
    val now = System.currentTimeMillis()
    val (title, subtitle) = if (step.kind == LegKind.Walk) {
        val ride = steps.drop(index + 1).firstOrNull { it.kind == LegKind.Transport }
        if (ride == null) {
            "Walk to the destination" to Geo.durationText(step.seconds)
        } else {
            val stop = ride.fromStop?.let { "Walk to «$it»" } ?: "Walk to the stop"
            stop to listOfNotNull(
                Geo.durationText(step.seconds),
                rideName(ride) + (ride.departureText?.let { " at $it" } ?: ""),
            ).joinToString(" · ")
        }
    } else {
        val waitMin = step.departureMillis?.let { (it - now) / 60_000 }?.takeIf { it > 0 }
        val head = rideName(step) + if (step.stops > 0) " · ${stopsText(step.stops)}" else ""
        head to when {
            waitMin != null -> "Departs ${step.departureText} · in $waitMin min"
            step.toStop != null -> "Get off at «${step.toStop}»"
            else -> Geo.durationText(step.seconds)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.kind == LegKind.Transport && step.lineName != null) {
            LineChip(step.lineName, step.color, outlined = false)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun stopsText(n: Int) = if (n == 1) "1 stop" else "$n stops"

@Composable
private fun NavNumber(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = yPalette.textPrimary,
        maxLines = 1,
    )
}

/** Thin bar colored by traffic along the route, with the passed part grayed out. */
@Composable
private fun RouteProgressBar(track: NavTrack?, fraction: Float) {
    val passed = yPalette.closeCircle
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val barH = 5.dp.toPx()
        val top = (size.height - barH) / 2
        val colors = track?.segmentColors
        if (track == null || colors == null || track.length <= 0) {
            drawRoundRect(YColors.JamFree, Offset(0f, top), Size(size.width, barH), CornerRadius(barH / 2))
        } else {
            var start = 0
            while (start < colors.size) {
                var end = start
                while (end + 1 < colors.size && colors[end + 1] == colors[start]) end++
                val x0 = (track.cumulative[start] / track.length * size.width).toFloat()
                val x1 = (track.cumulative[end + 1] / track.length * size.width).toFloat()
                drawRect(Color(colors[start]), Offset(x0, top), Size((x1 - x0).coerceAtLeast(1f), barH))
                start = end + 1
            }
        }
        val px = size.width * fraction
        drawRect(passed, Offset(0f, top), Size(px, barH))
        val arrow = Path().apply {
            moveTo(px + 7.dp.toPx(), size.height / 2)
            lineTo(px - 4.dp.toPx(), top - 1.dp.toPx())
            lineTo(px - 1.dp.toPx(), size.height / 2)
            lineTo(px - 4.dp.toPx(), top + barH + 1.dp.toPx())
            close()
        }
        drawPath(arrow, Color(0xFFF2A900))
    }
}
