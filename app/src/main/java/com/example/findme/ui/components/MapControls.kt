package com.example.findme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.DriveEta
import com.example.findme.data.SavedPlace
import com.example.findme.data.TravelMode
import com.example.findme.data.auth.UserProfile
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.Screen
import com.example.findme.ui.profile.Avatar
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Color of a travel time on the home/work buttons: green, orange or red by traffic. */
fun etaColor(eta: DriveEta): Color = when {
    eta.trafficRatio < 1.2 -> YColors.Green
    eta.trafficRatio < 1.5 -> YColors.Orange
    else -> YColors.Red
}

/**
 * Floating map buttons, laid out like Yandex Maps: profile and layers at the top,
 * zoom on the right, compass and "my location" above the sheet, home/work shortcuts
 * on the main screen and the scale ruler on the left.
 */
@Composable
fun MapControls(
    vm: MapViewModel,
    sheetTop: Float,
    topBarPx: Int,
    user: UserProfile?,
    onProfileClick: () -> Unit,
    onLocationClick: () -> Unit,
    onVoice: () -> Unit,
) {
    val density = LocalDensity.current
    fun px(v: Dp) = with(density) { v.roundToPx() }
    val screen = vm.screen

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density)
        val top = if (topBarPx > 0) topBarPx + px(12.dp) else statusTop + px(12.dp)
        val sheet = if (sheetTop.isNaN()) fullHeight else sheetTop
        val showHomeRow = screen == Screen.Home
        val sheetLow = sheet > fullHeight * 0.5f

        // ---- Top left: profile ----
        if (screen == Screen.Home) {
            MapButton(
                onClick = onProfileClick,
                modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(px(12.dp), top) },
            ) {
                if (user != null) {
                    Avatar(user, 34.dp)
                } else {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(yPalette.field),
                        contentAlignment = Alignment.Center,
                    ) {
                        YIcon(R.drawable.ms_person, "Profile", tint = Color(0xFFB9B9B9), size = 28.dp)
                    }
                }
            }
        }

        // ---- Top right column ----
        val topButtons = buildList<Pair<Int, () -> Unit>> {
            when (screen) {
                Screen.Route -> {
                    add(R.drawable.ic_layers to { vm.layersOpen = true })
                    add(R.drawable.ms_directions_bus to { vm.route.selectMode(TravelMode.Transit) })
                }
                Screen.Home -> {
                    add(R.drawable.ic_layers to { vm.layersOpen = true })
                    add(R.drawable.ic_assistant to onVoice)
                }
                else -> add(R.drawable.ic_layers to { vm.layersOpen = true })
            }
        }
        Column(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(-px(12.dp), top) },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            topButtons.forEach { (icon, action) ->
                MapButton(onClick = action) {
                    val tint = when {
                        icon == R.drawable.ms_traffic && vm.trafficOn -> YColors.Green
                        icon == R.drawable.ic_layers || icon == R.drawable.ic_assistant -> Color.Unspecified
                        else -> yPalette.icon
                    }
                    val info = vm.trafficInfo
                    if (icon == R.drawable.ms_traffic && vm.trafficOn && info != null) {
                        TrafficBadge(info.level, Color(info.color))
                    } else {
                        YIcon(icon, null, tint = tint, size = 26.dp)
                    }
                }
            }
        }
        val topColumnBottom = top + px(48.dp) * topButtons.size + px(12.dp) * (topButtons.size - 1)

        // ---- Bottom: "my location" and compass, home/work row ----
        val rowTop = sheet - px(12.dp) - px(48.dp)
        val locationBottom = if (showHomeRow) rowTop - px(12.dp) else sheet - px(12.dp)
        val locationTop = locationBottom - px(56.dp)
        val showLocation = sheetLow && locationTop > topColumnBottom + px(12.dp)

        if (showLocation) {
            MapButton(
                onClick = onLocationClick,
                size = 56.dp,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(-px(12.dp), locationTop.roundToInt()) },
            ) {
                YIcon(
                    R.drawable.ms_near_me, "My location",
                    tint = if (vm.following) yPalette.accent else yPalette.icon, size = 28.dp,
                )
            }
            val az = ((vm.azimuth % 360) + 360) % 360
            if (az > 1f && az < 359f) {
                MapButton(
                    onClick = { vm.resetNorth() },
                    size = 48.dp,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .offset { IntOffset(-px(12.dp + 56.dp + 12.dp), (locationTop + px(4.dp)).roundToInt()) },
                ) {
                    Compass(az)
                }
            }
        }

        // ---- Zoom, in the middle of the right edge ----
        // Centered at ~47% of the screen, moved up when it would touch the "my location" button.
        val zoomLimit = (if (showLocation) locationTop else sheet) - px(12.dp) - px(108.dp)
        val zoomTop = minOf(fullHeight * 0.47f - px(54.dp), zoomLimit)
        if (sheetLow && zoomTop >= topColumnBottom + px(16.dp)) {
            Column(
                Modifier.align(Alignment.TopEnd).offset { IntOffset(-px(12.dp), zoomTop.roundToInt()) },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MapButton(onClick = { vm.zoomIn() }) { YIcon(R.drawable.ms_add, "Zoom in", size = 28.dp) }
                MapButton(onClick = { vm.zoomOut() }) { YIcon(R.drawable.ms_remove, "Zoom out", size = 28.dp) }
            }
            ScaleRuler(
                metersPerPx = vm.metersPerPx,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset(px(12.dp), (zoomTop + px(54.dp) - px(60.dp)).roundToInt()) },
            )
        }

        if (showHomeRow && sheetLow) {
            Row(
                Modifier.align(Alignment.TopStart).offset { IntOffset(px(12.dp), rowTop.roundToInt()) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapButton(onClick = { vm.startRoutePlanning() }, size = 48.dp) {
                    YIcon(R.drawable.ms_route, "Directions", tint = yPalette.icon, size = 26.dp)
                }
                SavedPill(R.drawable.ms_home, "Home", vm.home, vm.homeEta) {
                    val home = vm.home
                    if (home != null) vm.routeToSaved(home, "Home")
                    else vm.message = "Open a place and tap “Set as home”"
                }
                SavedPill(R.drawable.ms_work, "Work", vm.work, vm.workEta) {
                    val work = vm.work
                    if (work != null) vm.routeToSaved(work, "Work")
                    else vm.message = "Open a place and tap “Set as work”"
                }
            }
        }
    }
}

@Composable
private fun SavedPill(icon: Int, title: String, saved: SavedPlace?, eta: DriveEta?, onClick: () -> Unit) {
    MapPill(onClick = onClick) {
        YIcon(icon, title, tint = yPalette.icon, size = 22.dp)
        if (saved != null && eta != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                eta.text.replace(" ", " "),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = etaColor(eta),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TrafficBadge(level: Int, color: Color) {
    Box(Modifier.size(30.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(level.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Vertical scale bar with a round distance, like the "110 m" ruler of Yandex Maps. */
@Composable
private fun ScaleRuler(metersPerPx: Float, modifier: Modifier = Modifier) {
    if (metersPerPx <= 0f) return
    val density = LocalDensity.current
    val maxPx = with(density) { 120.dp.toPx() }
    val maxMeters = metersPerPx * maxPx
    val magnitude = 10.0.pow(floor(log10(maxMeters.toDouble())))
    val nice = listOf(5.0, 2.0, 1.0).map { it * magnitude }.first { it <= maxMeters }
    val heightDp = with(density) { (nice / metersPerPx).toFloat().toDp() }
    val label = if (nice >= 1000) {
        val km = nice / 1000
        "${if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()} km"
    } else {
        "${nice.toInt()} m"
    }
    val lineColor = if (yPalette.isDark) Color(0xFFE0E0E0) else Color(0xFF5A5A5A)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(heightDp).background(lineColor))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = lineColor)
    }
}

@Composable
private fun Compass(azimuth: Float) {
    Canvas(Modifier.size(28.dp).rotate(-azimuth)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val w = size.width * 0.2f
        val north = Path().apply { moveTo(cx, 0f); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
        val south = Path().apply { moveTo(cx, size.height); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
        drawPath(north, Color(0xFFFA453A))
        drawPath(south, Color(0xFFD0D0D0))
        drawCircle(Color.White, radius = w * 0.35f, center = Offset(cx, cy))
    }
}
