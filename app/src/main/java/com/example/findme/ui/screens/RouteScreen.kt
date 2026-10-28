package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.LaneArrow
import com.example.findme.data.RoadEventKind
import com.example.findme.data.RoadLane
import com.example.findme.data.RouteEvent
import com.example.findme.ui.nav.VoicePrompter
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
private fun StopBadge(number: Int) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(YColors.Blue),
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
    Maneuver.Roundabout, Maneuver.RoundaboutExit -> R.drawable.ms_roundabout_right
    Maneuver.Finish -> R.drawable.ms_sports_score
}

@DrawableRes
private fun LaneArrow.iconRes(): Int = when (this) {
    LaneArrow.UturnLeft -> R.drawable.ms_u_turn_left
    LaneArrow.SharpLeft, LaneArrow.Left -> R.drawable.ms_turn_left
    LaneArrow.SlightLeft -> R.drawable.ms_turn_slight_left
    LaneArrow.Straight -> R.drawable.ms_straight
    LaneArrow.SlightRight -> R.drawable.ms_turn_slight_right
    LaneArrow.Right, LaneArrow.SharpRight -> R.drawable.ms_turn_right
    LaneArrow.UturnRight -> R.drawable.ms_u_turn_right
}

/** The lanes ahead, left to right: the arrow to follow in blue, the rest grey. */
@Composable
fun LaneStrip(lanes: List<RoadLane>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // Many lanes get smaller, so the strip still fits next to the speed badges.
        val size = ((maxWidth - 12.dp) / lanes.size - 3.dp).coerceIn(18.dp, 32.dp)
        Surface(shape = RoundedCornerShape(14.dp), color = yPalette.sheet, shadowElevation = 4.dp) {
            Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                lanes.forEachIndexed { i, lane ->
                    if (i > 0) Box(Modifier.padding(horizontal = 1.dp).width(1.dp).height(size).background(yPalette.divider))
                    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                        lane.arrows.filter { it != lane.take }.forEach {
                            YIcon(it.iconRes(), null, tint = yPalette.iconSecondary.copy(alpha = 0.45f), size = size)
                        }
                        lane.take?.let { YIcon(it.iconRes(), "Take this lane", tint = yPalette.accent, size = size) }
                    }
                }
            }
        }
    }
}

private fun RoadEventKind.icon(): ImageVector = when (this) {
    RoadEventKind.SpeedCamera, RoadEventKind.LaneCamera, RoadEventKind.Camera -> Icons.Filled.PhotoCamera
    RoadEventKind.Police -> Icons.Filled.LocalPolice
    RoadEventKind.Accident -> Icons.Filled.CarCrash
    RoadEventKind.Works -> Icons.Filled.Construction
    RoadEventKind.Closed -> Icons.Filled.Block
    RoadEventKind.Danger -> Icons.Filled.Warning
    RoadEventKind.School -> Icons.Filled.School
}

/** The next camera, accident or works ahead: what, its speed limit, how far. */
@Composable
fun EventChip(event: RouteEvent, meters: Double, modifier: Modifier = Modifier) {
    val alarm = event.kind in setOf(RoadEventKind.Accident, RoadEventKind.Closed, RoadEventKind.Danger, RoadEventKind.Works)
    Surface(shape = RoundedCornerShape(14.dp), color = yPalette.sheet, shadowElevation = 4.dp, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                event.kind.icon(),
                contentDescription = VoicePrompter.eventText(event.kind),
                tint = if (alarm) YColors.Red else yPalette.textPrimary,
                modifier = Modifier.size(22.dp),
            )
            event.limitKmh?.let { limit ->
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(28.dp).background(Color.White, CircleShape).border(3.dp, YColors.Red, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Geo.speed(limit).toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = YColors.Ink)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(Geo.shortDistance(meters), color = yPalette.textPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
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
                    Geo.speed(progress.speedKmh).toString(),
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
                    Modifier.border(6.dp, YColors.Red, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        Geo.speed(limit).toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = YColors.Ink,
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
        drawPath(arrow, YColors.Yellow)
    }
}
