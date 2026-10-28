package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.example.findme.ui.route.RouteController
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.RouteVariant
import com.example.findme.data.TravelMode
import com.example.findme.data.WaypointSlot
import com.example.findme.ui.MapViewModel
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

/** Collapsed route sheet: variant cards and the Go row; public transport adds a summary line. */
val ROUTE_PEEK = 228.dp
val ROUTE_PEEK_TRANSIT = 262.dp

/** Route sheet: route variants, "Go" and "share with friends". From/to and modes are in [RouteTopCard]. */
@Composable
fun RouteSheetContent(vm: MapViewModel, onShare: () -> Unit) {
    val result = vm.route.results[vm.route.mode]
    val variants = result?.variants.orEmpty()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.CenterStart) {
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
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val canGo = selected != null
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (canGo) yPalette.accent else yPalette.accent.copy(alpha = 0.4f))
                    .clickable(enabled = canGo, role = Role.Button) { vm.nav.start() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YIcon(R.drawable.ms_navigation, null, tint = yPalette.onAccent, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (onFoot) "Let's go" else "Go",
                    color = yPalette.onAccent,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(yPalette.accentSoft)
                    .clickable(enabled = canGo, role = Role.Button, onClick = onShare),
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_share, "Share route with friends", tint = yPalette.accent, size = 22.dp)
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * Floating card at the top of the route screen: back, From / stops / To fields, "Add stop",
 * swap, and the travel mode chips under it. [onBottomChanged] gets its bottom edge in px,
 * so the map fits the route below it.
 */
@Composable
fun RouteTopCard(vm: MapViewModel, onBottomChanged: (Int) -> Unit) {
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 12.dp)
            .fillMaxWidth()
            .onGloballyPositioned { onBottomChanged(it.boundsInRoot().bottom.toInt()) },
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = yPalette.sheet,
            shadowElevation = 4.dp,
        ) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    YIcon(R.drawable.ms_arrow_back_ios_new, "Back", tint = yPalette.icon, size = 20.dp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    WaypointField(vm.route.from?.name, "Choose start", start = true) { vm.pickWaypoint(WaypointSlot.From) }
                    StopsEditor(vm)
                    Spacer(Modifier.height(6.dp))
                    WaypointField(vm.route.to?.name, "Choose destination", start = false) { vm.pickWaypoint(WaypointSlot.To) }
                    StopActions(vm)
                }
                Box(
                    Modifier.size(44.dp, 94.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.route.swap() },
                    contentAlignment = Alignment.Center,
                ) {
                    YIcon(R.drawable.ms_swap_vert, "Swap start and destination", tint = yPalette.textSecondary, size = 22.dp)
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TravelMode.entries) { mode ->
                val r = vm.route.results[mode]
                val label = when {
                    r == null -> null
                    r.loading -> "…"
                    r.variants.isNotEmpty() -> r.variants.first().durationText
                    else -> "—"
                }
                ModeChip(mode, label, selected = mode == vm.route.mode) { vm.route.selectMode(mode) }
            }
        }
    }
}

/** Gray 44-dp field: blue dot for the start, ink square for the destination. */
@Composable
private fun WaypointField(name: String?, placeholder: String, start: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(yPalette.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (start) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(yPalette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(yPalette.accent))
            }
        } else {
            Box(Modifier.padding(3.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(yPalette.textPrimary))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            name ?: placeholder,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (!start && name != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (name != null) yPalette.textPrimary else yPalette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Stops on the way, under the header: "1 Bazaar", "2 Pharmacy"... Tap a stop to change it,
 * drag the handle to reorder, × to remove; then "+ Add stop" and "Optimize order".
 * Every row is [STOP_ROW_DP] high; the drag maths counts on that.
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

    Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)) {
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
    }
}

/** "+ Add stop" on the left, "Optimize order" / "Undo order" on the right. */
@Composable
private fun StopActions(vm: MapViewModel) {
    val stops = vm.route.stops
    Row(
        Modifier.fillMaxWidth().height(STOP_ROW_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stops.size < RouteController.MAX_STOPS) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.route.addStop() }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YIcon(R.drawable.ms_add, null, tint = yPalette.accent, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("Add stop", style = MaterialTheme.typography.labelMedium, color = yPalette.accent)
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

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = yPalette.accent,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** Blue circle with the stop number, like the pin on the map. */
@Composable
internal fun StopBadge(number: Int) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(YColors.Blue),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Height of one stop row and of the "+ Add stop" row; MapScreen sizes the sheet by it. */
private const val STOP_ROW_DP = 40

/** Floating travel mode chip: ink when selected, white otherwise, with the best time. */
@Composable
private fun ModeChip(mode: TravelMode, label: String?, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) yPalette.onChipSelected else yPalette.textPrimary
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) yPalette.chipSelected else yPalette.button,
        shadowElevation = 4.dp,
    ) {
        Row(
            Modifier.height(40.dp).padding(start = 12.dp, end = if (label != null) 14.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(mode.iconRes(), mode.title, tint = fg, size = 18.dp)
            if (label != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    color = fg,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Shared frame of a route option: soft blue with a blue outline when selected, gray otherwise. */
@Composable
private fun variantModifier(selected: Boolean, onClick: () -> Unit): Modifier {
    val shape = RoundedCornerShape(18.dp)
    return Modifier
        .height(96.dp)
        .clip(shape)
        .background(if (selected) yPalette.accentSoft else yPalette.field)
        .then(if (selected) Modifier.border(2.dp, yPalette.accent, shape) else Modifier)
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp)
}

/** "18 min / 7.4 km · arrive 18:42 / ● Fastest". */
@Composable
private fun VariantCard(variant: RouteVariant, index: Int, selected: Boolean, mode: TravelMode, onClick: () -> Unit) {
    if (mode == TravelMode.Transit) {
        TransitCard(variant, selected, onClick)
        return
    }
    val p = yPalette
    Column(
        variantModifier(selected, onClick).widthIn(min = 140.dp, max = 190.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            variant.durationText,
            color = p.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            listOfNotNull(variant.distanceText, "arrive ${Geo.arrivalText(variant.durationSeconds)}").joinToString(" · "),
            color = p.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val (tag, color) = when {
            variant.hasIncidents -> "Road closures" to p.onDangerSoft
            index == 0 -> "Fastest" to p.onSuccessSoft
            else -> "Alternative" to p.textSecondary
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (variant.hasIncidents) {
                YIcon(R.drawable.ms_car_crash, null, tint = color, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            } else if (index == 0) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(6.dp))
            }
            Text(tag, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@DrawableRes
internal fun vehicleIcon(type: String?): Int = when (type) {
    "underground" -> R.drawable.ms_subway
    "tramway", "tram" -> R.drawable.ms_tram
    "railway", "suburban" -> R.drawable.ms_train
    else -> R.drawable.ms_directions_bus
}

/** Public transport variant: "1 hr 22 min" and the lines, e.g. M [2] ▸ M [1] ▸ 🚌 [199]. */
@Composable
private fun TransitCard(variant: RouteVariant, selected: Boolean, onClick: () -> Unit) {
    Column(
        variantModifier(selected, onClick).widthIn(min = 140.dp, max = 300.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            variant.durationText,
            color = yPalette.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        TransitLegsRow(variant, onBlue = false)
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
            Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)).background(if (onBlue) Color.White else YColors.Blue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "M",
                color = if (onBlue) YColors.Blue else Color.White,
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
internal fun LineChip(label: String, color: Int?, outlined: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .height(24.dp)
            .widthIn(min = 26.dp)
            .clip(shape)
            .background(Color(color ?: 0xFF2F55F4.toInt()))
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

