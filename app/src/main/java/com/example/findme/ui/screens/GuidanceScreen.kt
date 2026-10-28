package com.example.findme.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.LaneArrow
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.RoadEventKind
import com.example.findme.data.RoadLane
import com.example.findme.data.RouteEvent
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import com.example.findme.data.rideName
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.components.MapButton
import com.example.findme.ui.components.MapChip
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.nav.NavProgress
import com.example.findme.ui.nav.VoicePrompter
import com.example.findme.ui.theme.ManeuverStyle
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette

/** Height of the bottom bar without the navigation bar inset; the speed badges and buttons sit above it. */
private val BAR_HEIGHT = 88.dp

/** "Then ..." shows only when the maneuver after the next one comes this soon after it. */
private const val THEN_GAP_M = 1500.0

/** The darker band of the maneuver card ("Then ↰ in 1.1 km", lanes). */
private val BlueDeep = Color(0xFF2443C9)

/**
 * Everything over the map in guidance: the blue maneuver card on top with chips under it
 * (new route, next stop, camera, friends), the speed bottom left, voice and overview bottom
 * right, and the bar with the time left. [onBarTop] reports the bar's top edge for the map logo.
 */
@Composable
fun GuidanceOverlay(
    vm: MapViewModel,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    onBarTop: (Float) -> Unit,
    friends: @Composable () -> Unit,
) {
    val progress = vm.nav.progress
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ManeuverCard(vm, progress)
            Column(Modifier.widthIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.nav.rerouting) {
                    MapChip("Recalculating route…") {
                        CircularProgressIndicator(Modifier.size(20.dp), color = yPalette.accent, strokeWidth = 2.5.dp)
                    }
                }
                progress?.takeIf { it.nextStop >= 0 }?.let { p ->
                    vm.route.stops.getOrNull(p.nextStop)?.let { stop ->
                        MapChip(stop.name, "${Geo.shortDistance(p.toStopMeters)} · ${Geo.durationText(p.toStopSeconds)}") {
                            StopBadge(p.nextStop + 1)
                        }
                    }
                }
                progress?.event?.let { EventChip(it, progress.eventInMeters) }
                friends()
            }
        }

        val mode = vm.route.mode
        SpeedBadges(
            progress,
            showSpeed = mode != TravelMode.Walk && mode != TravelMode.Transit,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 12.dp, bottom = BAR_HEIGHT + 12.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = BAR_HEIGHT + 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val muted = vm.voice.muted
            GuidanceButton(onClick = { vm.voice.toggleMuted() }) {
                Icon(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (muted) "Turn voice on" else "Turn voice off",
                    tint = if (muted) yPalette.textSecondary else yPalette.icon,
                )
            }
            if (vm.nav.free) {
                // The map was moved: back to the arrow now (it also comes back by itself after 30 s).
                GuidanceButton(onClick = { vm.nav.resumeFollow() }) {
                    YIcon(R.drawable.ms_navigation, "Back to route", tint = yPalette.accent, size = 28.dp)
                }
            } else {
                GuidanceButton(onClick = { vm.nav.overview() }) {
                    YIcon(R.drawable.ms_route, "Route overview", tint = yPalette.icon)
                }
            }
        }

        GuidanceBar(
            vm,
            progress,
            onShare,
            onSettings,
            Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { onBarTop(it.boundsInRoot().top) },
        )
    }
}

@Composable
private fun GuidanceButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    MapButton(onClick = onClick, size = 56.dp, shape = RoundedCornerShape(18.dp)) { content() }
}

// ---------------------------------------------------------------------------
// Maneuver card
// ---------------------------------------------------------------------------

/** Blue card on top: the next turn, how far, onto which street; below it what comes next and the lanes. */
@Composable
private fun ManeuverCard(vm: MapViewModel, progress: NavProgress?) {
    val steps = vm.route.currentVariant?.steps.orEmpty()
    val step = progress?.step?.takeIf { it in steps.indices }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = YColors.Blue,
        contentColor = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (step != null) TransitInstruction(steps, step) else ManeuverRow(vm, progress)
            val lanes = progress?.lanes?.takeIf { step == null }
            val then = progress?.then?.takeIf {
                step == null && progress.nextSegment >= 0 && progress.thenInMeters - progress.nextInMeters <= THEN_GAP_M
            }
            if (lanes != null || then != null) {
                Row(
                    Modifier.fillMaxWidth().background(BlueDeep).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (then != null) {
                        Text("Then", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        YIcon(then.iconRes(), maneuverTitle(then), tint = Color.White, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "in ${Geo.shortDistance(progress.thenInMeters)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (lanes != null) {
                        LaneStrip(lanes, Modifier.weight(1f), alignEnd = then != null)
                    }
                }
            }
        }
    }
}

/** "↱ 300 m / Navoi Avenue"; on a route without turns (walking, cycling) the distance to the end. */
@Composable
private fun ManeuverRow(vm: MapViewModel, progress: NavProgress?) {
    val noTurns = progress == null || progress.nextSegment < 0
    val maneuver = if (noTurns) Maneuver.Finish else progress!!.next
    val meters = if (noTurns) {
        progress?.remainingMeters ?: vm.route.currentVariant?.distanceMeters ?: 0.0
    } else {
        progress!!.nextInMeters
    }
    val title = when {
        maneuver == Maneuver.Finish -> vm.route.to?.name ?: maneuverTitle(maneuver)
        else -> progress?.nextStreet ?: maneuverTitle(maneuver)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YIcon(maneuver.iconRes(), maneuverTitle(maneuver), tint = Color.White, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(Geo.shortDistance(meters), style = ManeuverStyle, color = Color.White, maxLines = 1)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Turn right", "Keep left", "Arrive" for a maneuver with no street name. */
private fun maneuverTitle(m: Maneuver): String = when (m) {
    Maneuver.Straight -> "Continue straight"
    Maneuver.Finish -> "Arrive"
    else -> VoicePrompter.actionText(m)?.replaceFirstChar { it.uppercase() } ?: "Continue"
}

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

/** The lanes ahead, left to right: the ones to take white, the rest faint. Many lanes get narrower. */
@Composable
private fun LaneStrip(lanes: List<RoadLane>, modifier: Modifier, alignEnd: Boolean) {
    BoxWithConstraints(modifier, contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
        val gap = 6.dp
        val width = ((maxWidth - gap * (lanes.size - 1)) / lanes.size).coerceIn(16.dp, 30.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            lanes.forEach { lane ->
                val take = lane.take != null
                Box(
                    Modifier
                        .size(width, 34.dp)
                        .background(if (take) Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = (width * 0.6f).coerceAtMost(18.dp)
                    lane.arrows.filter { it != lane.take }.forEach {
                        YIcon(it.iconRes(), null, tint = if (take) BlueDeep.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f), size = icon)
                    }
                    lane.take?.let { YIcon(it.iconRes(), "Take this lane", tint = BlueDeep, size = icon) }
                }
            }
        }
    }
}

/**
 * Public transport in the card: "Walk to «Central Park» / 4 min · bus 12 at 14:05"
 * or "[12] Bus 12 · 6 stops / Get off at «Market»".
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
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (step.kind == LegKind.Walk) R.drawable.ms_directions_walk else vehicleIcon(step.vehicle)
        YIcon(icon, null, tint = Color.White, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step.kind == LegKind.Transport && step.lineName != null) {
                    LineChip(step.lineName, step.color, outlined = true)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun stopsText(n: Int) = if (n == 1) "1 stop" else "$n stops"

// ---------------------------------------------------------------------------
// Chips, speed, bottom bar
// ---------------------------------------------------------------------------

private fun RoadEventKind.icon(): ImageVector = when (this) {
    RoadEventKind.SpeedCamera, RoadEventKind.LaneCamera, RoadEventKind.Camera -> Icons.Filled.PhotoCamera
    RoadEventKind.Police -> Icons.Filled.LocalPolice
    RoadEventKind.Accident -> Icons.Filled.CarCrash
    RoadEventKind.Works -> Icons.Filled.Construction
    RoadEventKind.Closed -> Icons.Filled.Block
    RoadEventKind.Danger -> Icons.Filled.Warning
    RoadEventKind.School -> Icons.Filled.School
}

private fun RoadEventKind.label(): String = when (this) {
    RoadEventKind.SpeedCamera, RoadEventKind.Camera -> "Camera"
    RoadEventKind.LaneCamera -> "Lane camera"
    RoadEventKind.Police -> "Police"
    RoadEventKind.Accident -> "Accident"
    RoadEventKind.Works -> "Road works"
    RoadEventKind.Closed -> "Road closed"
    RoadEventKind.Danger -> "Danger"
    RoadEventKind.School -> "School"
}

/** "(camera) Camera · 60  in 400 m": amber for cameras and warnings, red for accidents and closures. */
@Composable
private fun EventChip(event: RouteEvent, meters: Double) {
    val (bg, fg) = when (event.kind) {
        RoadEventKind.Accident, RoadEventKind.Closed -> YColors.Red to Color.White
        RoadEventKind.Police -> YColors.Blue to Color.White
        else -> YColors.Yellow to YColors.Ink
    }
    val label = event.kind.label() + (event.limitKmh?.let { " · ${Geo.speed(it)}" } ?: "")
    val detail = if (meters < 15) "now" else "in ${Geo.shortDistance(meters)}"
    MapChip(label, detail) {
        Box(Modifier.size(28.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
            Icon(event.kind.icon(), VoicePrompter.eventText(event.kind), tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

/** The speed limit sign and my speed, bottom left; my speed turns red over the limit. */
@Composable
private fun SpeedBadges(progress: NavProgress?, showSpeed: Boolean, modifier: Modifier) {
    progress ?: return
    val limit = progress.limitKmh
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (limit != null) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 4.dp, modifier = Modifier.size(56.dp)) {
                Box(Modifier.border(5.dp, YColors.Red, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        Geo.speed(limit).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = YColors.Ink,
                    )
                }
            }
        }
        if (showSpeed) {
            val over = limit != null && progress.speedKmh > limit
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (over) yPalette.dangerSoft else yPalette.sheet,
                shadowElevation = 4.dp,
                modifier = Modifier.size(56.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        Geo.speed(progress.speedKmh).toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 24.sp,
                        color = if (over) yPalette.onDangerSoft else yPalette.textPrimary,
                    )
                    Text(
                        Geo.speedUnit,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = if (over) yPalette.onDangerSoft else yPalette.textSecondary,
                    )
                }
            }
        }
    }
}

/** Bottom bar: end (left), "12 min / 4.6 km · arrive 18:41" (center), more (right). */
@Composable
private fun GuidanceBar(
    vm: MapViewModel,
    progress: NavProgress?,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier,
) {
    val variant = vm.route.currentVariant
    val meters = progress?.remainingMeters ?: variant?.distanceMeters ?: 0.0
    val seconds = progress?.remainingSeconds ?: variant?.durationSeconds ?: 0.0
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = yPalette.sheet,
        shadowElevation = 12.dp,
    ) {
        Row(
            Modifier.navigationBarsPadding().height(BAR_HEIGHT).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(onClick = { vm.nav.stop() }, shape = CircleShape, color = yPalette.dangerSoft, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    YIcon(R.drawable.ms_close, "End guidance", tint = yPalette.onDangerSoft, size = 26.dp)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Geo.durationText(seconds),
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                )
                Text(
                    "${Geo.shortDistance(meters)} · arrive ${Geo.arrivalText(seconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                    maxLines = 1,
                )
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                Surface(onClick = { menu = true }, shape = CircleShape, color = yPalette.field, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MoreHoriz, "More options", tint = yPalette.icon)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = yPalette.sheet) {
                    DropdownMenuItem(
                        text = { Text("Share arrival time", color = yPalette.textPrimary) },
                        leadingIcon = { YIcon(R.drawable.ms_share, null, tint = yPalette.icon) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings", color = yPalette.textPrimary) },
                        leadingIcon = { YIcon(R.drawable.ms_tune, null, tint = yPalette.icon) },
                        onClick = { menu = false; onSettings() },
                    )
                }
            }
        }
    }
}
