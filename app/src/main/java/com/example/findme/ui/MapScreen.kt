package com.example.findme.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.findme.data.Geo
import com.example.findme.data.Place
import com.example.findme.data.meet.Meeting
import com.example.findme.data.RouteVariant
import com.example.findme.data.TravelMode
import com.example.findme.data.Waypoint
import com.example.findme.maps.MapEngine
import com.example.findme.maps.MeetMapData
import com.example.findme.maps.MeetMarker
import com.example.findme.ui.components.MapControls
import com.example.findme.ui.components.SheetStop
import com.example.findme.ui.components.YSheet
import com.example.findme.ui.components.YSheetState
import com.example.findme.ui.meet.JoinDialog
import com.example.findme.ui.meet.LiveMeetChip
import com.example.findme.ui.meet.MeetNavStrip
import com.example.findme.ui.meet.MyNav
import com.example.findme.ui.meet.short
import com.example.findme.ui.meet.agoText
import com.example.findme.ui.meet.isStale
import com.example.findme.ui.profile.avatarColor
import com.example.findme.ui.meet.MeetActions
import com.example.findme.ui.meet.MeetEditorSheet
import com.example.findme.ui.meet.MeetListSheet
import com.example.findme.ui.meet.MeetViewModel
import com.example.findme.ui.meet.MeetingSheet
import com.example.findme.ui.meet.inviteText
import com.example.findme.ui.profile.AuthViewModel
import com.example.findme.ui.profile.ProfileSheet
import com.example.findme.ui.screens.EventChip
import com.example.findme.ui.screens.HomeSheetContent
import com.example.findme.ui.screens.LaneStrip
import com.example.findme.ui.screens.LayersSheet
import com.example.findme.ui.screens.NavigationPanel
import com.example.findme.ui.screens.PlaceActions
import com.example.findme.ui.screens.PlaceCardContent
import com.example.findme.ui.screens.ResultsSheetContent
import com.example.findme.ui.screens.RouteSheetContent
import com.example.findme.ui.screens.SavedSheetContent
import com.example.findme.ui.screens.SearchOverlay
import com.example.findme.ui.screens.SpeedBadges
import com.example.findme.ui.screens.TopSearchBar
import com.example.findme.ui.theme.yPalette
import com.example.findme.R
import com.example.findme.ui.components.MapButton
import com.example.findme.ui.components.YIcon
import androidx.compose.foundation.shape.CircleShape
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private data class SheetConfig(
    val key: String,
    val peek: Dp,
    val stops: List<SheetStop>,
    val initial: SheetStop,
)

private fun sheetConfig(screen: Screen, transit: Boolean, stops: Int = 0) = when (screen) {
    Screen.Home -> SheetConfig("home", 232.dp, listOf(SheetStop.Collapsed, SheetStop.Expanded), SheetStop.Collapsed)
    is Screen.Results -> SheetConfig(
        "results", 150.dp, listOf(SheetStop.Collapsed, SheetStop.Half, SheetStop.Expanded), SheetStop.Half,
    )
    is Screen.PlaceCard -> SheetConfig("place", 244.dp, listOf(SheetStop.Collapsed, SheetStop.Expanded), SheetStop.Collapsed)
    // Public transport has an extra summary row under the variants.
    Screen.Route -> SheetConfig(
        (if (transit) "route-transit" else "route") + "-$stops",
        (if (transit) 340.dp else 306.dp) + STOPS_ROW + STOP_ROW * stops,
        listOf(SheetStop.Collapsed),
        SheetStop.Collapsed,
    )
    Screen.Saved -> SheetConfig(
        "saved", 150.dp, listOf(SheetStop.Collapsed, SheetStop.Half, SheetStop.Expanded), SheetStop.Half,
    )
}

/** Height of the "+ Add stop" row, and of each stop in the route sheet (see RouteHeader). */
private val STOPS_ROW = 40.dp
private val STOP_ROW = 40.dp

/** Text of the white search bar at the top of the map, or null when it is hidden. */
private fun topBarText(screen: Screen): String? = when (screen) {
    is Screen.Results -> screen.query
    is Screen.PlaceCard -> screen.searchText
    else -> null
}

/** Root screen: the map with floating controls, the bottom sheet and overlays. */
@Composable
fun MapScreen(vm: MapViewModel, authVm: AuthViewModel, meetVm: MeetViewModel, darkMap: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember { MapEngine(context, vm) }
    val sheetState = remember { YSheetState() }
    var topBarPx by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> engine.onStart()
                Lifecycle.Event.ON_STOP -> engine.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.detach()
        }
    }

    // ---- Location permission ----
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> vm.onPermissionResult(result.values.any { it }) }

    fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun requestPermission() = permissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

    LaunchedEffect(Unit) {
        if (hasPermission()) vm.onPermissionResult(true) else requestPermission()
    }

    // ---- Voice search ----
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            ?.let { vm.submitSearch(it) }
    }
    val onVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What are you looking for?")
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            vm.message = "Voice input is not available on this device"
        }
    }

    val actions = remember(context) { placeActions(context, vm) }
    // Sharing needs location (and, to show the "sharing" notification, notifications on Android 13+).
    var shareFor by remember { mutableStateOf<Meeting?>(null) }
    val sharePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val meeting = shareFor
        shareFor = null
        if (hasPermission()) {
            vm.onPermissionResult(true)
            meeting?.let(meetVm::startSharing)
        } else if (result.isNotEmpty()) {
            vm.message = "Allow location access to share where you are"
        }
    }
    val startSharing: (Meeting) -> Unit = { meeting ->
        val needed = buildList {
            if (!hasPermission()) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) {
            meetVm.startSharing(meeting)
        } else {
            shareFor = meeting
            sharePermissionLauncher.launch(needed.toTypedArray())
        }
    }
    val meetActions = remember(context) {
        meetActions(context, vm, meetVm, actions, share = { startSharing(it) })
    }

    // ---- Keep the map in sync with the state ----
    LaunchedEffect(engine) { vm.commands.collect { engine.handle(it) } }
    LaunchedEffect(engine) {
        snapshotFlow { vm.search.results to vm.selectedResultId }
            .collect { (results, selected) -> engine.renderResults(results, selected) }
    }
    LaunchedEffect(engine) {
        snapshotFlow { vm.markerPlace to vm.poiSelected }
            .collect { (place, poi) -> engine.renderMarker(place, poi) }
    }
    LaunchedEffect(engine) {
        snapshotFlow {
            RouteDraw(
                vm.route.from, vm.route.to, vm.route.stops.toList(),
                vm.route.results[vm.route.mode]?.variants.orEmpty(),
                vm.route.selectedVariant,
                vm.screen is Screen.Route,
                vm.nav.active,
                vm.route.mode == TravelMode.Transit,
            )
        }.collect {
            engine.setNavigationMode(it.navigating)
            engine.renderRoute(it.from, it.to, it.stops, it.variants, it.selected, it.visible, it.transit)
        }
    }
    // Live meet-up: the place and every member who shares their location (except me).
    LaunchedEffect(engine) {
        // Members' routes would mix with my own route lines, so they show only off the route screen.
        // Members' routes stay hidden while I compare my own route variants, so the lines don't mix.
        snapshotFlow { meetMapData(meetVm, showRoutes = vm.nav.active || vm.screen !is Screen.Route) }
            .collect { engine.renderMeetup(it) }
    }
    // "Go" to a meeting place: the others see my route and my progress along it.
    LaunchedEffect(meetVm) {
        snapshotFlow { myNav(vm) }.collect { meetVm.onNavigation(it) }
    }
    LaunchedEffect(meetVm) {
        snapshotFlow { vm.nav.progress }.collect { meetVm.onNavProgress(it?.remainingSeconds, it?.remainingMeters) }
    }
    // Guidance: the part of the route behind me disappears.
    LaunchedEffect(engine) {
        snapshotFlow { vm.nav.progress }.collect { engine.setPassed(it?.trackSegment, it?.trackT ?: 0.0) }
    }
    LaunchedEffect(engine, darkMap) {
        snapshotFlow { vm.mapLayer to vm.trafficOn }
            .collect { (layer, traffic) -> engine.applyLayers(layer, darkMap, traffic) }
    }
    LaunchedEffect(engine) {
        snapshotFlow { vm.hasLocationPermission }.filter { it }.collect { engine.enableUserLocation() }
    }
    LaunchedEffect(engine) {
        snapshotFlow { Triple(sheetState.top, topBarPx, vm.nav.active) to (vm.screen == Screen.Home) }
            .collect { (state, home) ->
                val (top, bar, nav) = state
                // On the main screen the home/work row sits right above the sheet; the logo goes above it.
                engine.setViewport(
                    if (nav) 0f else bar.toFloat(),
                    if (nav) Float.NaN else top,
                    logoLiftDp = if (home && !nav) 60f else 0f,
                )
            }
    }

    // Sign-in errors use the same toast as the map.
    LaunchedEffect(authVm.error) {
        authVm.error?.let {
            vm.message = it
            authVm.error = null
        }
    }

    // ---- Meet-ups ----
    LaunchedEffect(authVm.user) { meetVm.onUser(authVm.user) }
    LaunchedEffect(meetVm.message) {
        meetVm.message?.let {
            vm.message = it
            meetVm.message = null
        }
    }
    LaunchedEffect(meetVm.needsSignIn) {
        if (meetVm.needsSignIn) {
            authVm.profileOpen = true
            meetVm.needsSignIn = false
        }
    }

    BackHandler(enabled = vm.canGoBack) { vm.onBack() }

    val screen = vm.screen
    val barText = topBarText(screen)
    LaunchedEffect(barText, vm.nav.active) { if (barText == null || vm.nav.active) topBarPx = 0 }
    // In guidance the screen stays on, like in a car navigator.
    val rootView = LocalView.current
    DisposableEffect(vm.nav.active) {
        rootView.keepScreenOn = vm.nav.active
        onDispose { rootView.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { engine.mapView }, modifier = Modifier.fillMaxSize())

        if (vm.nav.active) {
            SpeedBadges(vm, Modifier.align(Alignment.TopEnd))
            // Spoken guidance on/off, under the speed badges.
            MapButton(
                onClick = { vm.voice.toggleMuted() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 84.dp, end = 12.dp),
                size = 48.dp,
                shape = CircleShape,
            ) {
                Icon(
                    if (vm.voice.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (vm.voice.muted) "Turn voice on" else "Turn voice off",
                    tint = if (vm.voice.muted) yPalette.textSecondary else yPalette.accent,
                )
            }
            // Top left, clear of the speed badges: a new route on its way, lanes, the next camera.
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 10.dp, end = 164.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (vm.nav.rerouting) {
                    Surface(shape = CircleShape, color = yPalette.sheet, shadowElevation = 4.dp) {
                        Text(
                            "Recalculating route…",
                            color = yPalette.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
                val progress = vm.nav.progress
                progress?.lanes?.let { LaneStrip(it) }
                progress?.event?.let { EventChip(it, progress.eventInMeters) }
            }
            NavigationPanel(vm, Modifier.align(Alignment.BottomCenter))
            if (vm.nav.free) {
                // The map was moved: back to the arrow now (it also comes back by itself after 30 s).
                MapButton(
                    onClick = { vm.nav.resumeFollow() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 12.dp, bottom = 92.dp),
                    size = 56.dp,
                    shape = CircleShape,
                ) { YIcon(R.drawable.ms_navigation, "Back to route", tint = yPalette.accent, size = 30.dp) }
            }
            MeetNavStrip(
                meetVm,
                Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 92.dp),
            )
        } else {
            MapControls(
                vm = vm,
                sheetTop = sheetState.top,
                topBarPx = topBarPx,
                user = authVm.user,
                onProfileClick = { authVm.profileOpen = true },
                onLocationClick = { if (!vm.onMyLocationClick()) requestPermission() },
                onVoice = onVoice,
            )

            if (screen == Screen.Home && meetVm.mapMeeting != null) {
                LiveMeetChip(
                    meetVm,
                    onOpen = { meetVm.openMeeting(it.id) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 12.dp),
                )
            }

            if (screen is Screen.Results && vm.search.showSearchHere) {
                Surface(
                    onClick = { vm.searchHere() },
                    shape = RoundedCornerShape(20.dp),
                    color = yPalette.sheet,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 80.dp),
                ) {
                    Text(
                        "Search this area",
                        color = yPalette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }

            val config = sheetConfig(screen, transit = vm.route.mode == TravelMode.Transit, stops = vm.route.stops.size)
            YSheet(
                state = sheetState,
                contentKey = config.key,
                peekHeight = config.peek,
                stops = config.stops,
                initialStop = config.initial,
            ) {
                when (screen) {
                    Screen.Home -> HomeSheetContent(vm, actions, meetVm)
                    is Screen.Results -> ResultsSheetContent(vm, screen.query)
                    is Screen.PlaceCard -> PlaceCardContent(vm, screen, actions, onMeet = meetVm::startNew)
                    Screen.Route -> RouteSheetContent(vm)
                    Screen.Saved -> SavedSheetContent(vm)
                }
            }

            if (barText != null) {
                TopSearchBar(barText, onClick = { vm.search.open(barText) }) { topBarPx = it }
            }
        }

        AnimatedVisibility(
            visible = vm.search.isOpen,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 },
        ) {
            SearchOverlay(vm, onVoice)
        }

        if (vm.layersOpen) LayersSheet(vm)
        if (authVm.profileOpen) ProfileSheet(authVm)
        if (meetVm.listOpen) MeetListSheet(meetVm, meetActions, vm.userLocation)
        if (meetVm.openId != null) MeetingSheet(meetVm, meetActions, vm.userLocation)
        if (meetVm.editor != null) MeetEditorSheet(meetVm)
        if (meetVm.joinOpen) JoinDialog(meetVm)

        MessageToast(vm, Modifier.align(Alignment.TopCenter))
    }
}

private data class RouteDraw(
    val from: Waypoint?,
    val to: Waypoint?,
    val stops: List<Waypoint>,
    val variants: List<RouteVariant>,
    val selected: Int,
    val visible: Boolean,
    val navigating: Boolean,
    val transit: Boolean,
)

@Composable
private fun MessageToast(vm: MapViewModel, modifier: Modifier) {
    val text = vm.message
    LaunchedEffect(text) {
        if (text != null) {
            delay(2200)
            vm.message = null
        }
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xEE21201F),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 64.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                text.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private fun placeActions(context: Context, vm: MapViewModel) = PlaceActions(
    call = { phone ->
        val number = phone.filter { it.isDigit() || it == '+' }
        context.startSafely(Intent(Intent.ACTION_DIAL, "tel:$number".toUri()), vm)
    },
    openSite = { url ->
        val full = if (url.startsWith("http")) url else "https://$url"
        context.startSafely(Intent(Intent.ACTION_VIEW, full.toUri()), vm)
    },
    share = { place: Place ->
        val text = buildString {
            appendLine(place.name)
            place.address?.let { appendLine(it) }
            append("https://yandex.com/maps/?pt=${place.lon},${place.lat}&z=17&l=map")
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startSafely(Intent.createChooser(send, "Share"), vm)
    },
    copy = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("FindMe", text))
        vm.message = "Copied"
    },
    taxi = { to: Point? ->
        val uri = buildString {
            append("yandextaxi://route")
            if (to != null) append("?end-lat=${to.latitude}&end-lon=${to.longitude}")
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        } catch (_: ActivityNotFoundException) {
            context.startSafely(Intent(Intent.ACTION_VIEW, "https://go.yandex/".toUri()), vm)
        }
    },
)

/** Everything the live meet-up layer draws; equal values are not redrawn. */
/** My navigation for the meet-up: where to and the route line; null when not navigating. */
private fun myNav(vm: MapViewModel): MyNav? {
    if (!vm.nav.active) return null
    val to = vm.route.to ?: return null
    val variant = vm.route.currentVariant ?: return null
    val points = variant.track?.points ?: variant.lines.flatMap { it.points }
    if (points.size < 2) return null
    return MyNav(to.point, vm.route.mode, points, variant.durationSeconds, variant.distanceMeters)
}

private fun meetMapData(meetVm: MeetViewModel, showRoutes: Boolean): MeetMapData? {
    val meeting = meetVm.mapMeeting ?: return null
    val me = meetVm.user?.uid
    val place = Point(meeting.lat, meeting.lon)
    val markers = meeting.members.mapNotNull { member ->
        if (member.uid == me) return@mapNotNull null
        val loc = meetVm.locations[member.uid] ?: return@mapNotNull null
        val point = Point(loc.lat, loc.lon)
        val stale = isStale(loc, meetVm.now)
        val first = member.name.substringBefore(' ')
        val trip = if (loc.arrived || stale) null else meetVm.memberTrip(member.uid)
        val relation = if (loc.arrived || stale) null else meetVm.relation(member.uid)
        val detail = when {
            loc.arrived -> "arrived"
            stale -> agoText(meetVm.now - (loc.updatedMillis ?: meetVm.now))
            relation != null -> relation.short()
            trip?.etaSeconds != null -> Geo.durationText(trip.etaSeconds)
            else -> Geo.formatDistance(Geo.distanceMeters(point, place))
        }
        MeetMarker(
            uid = member.uid,
            name = member.name,
            label = "$first · $detail",
            point = point,
            color = avatarColor(member.uid).toArgb(),
            photo = meetVm.photos[member.uid],
            stale = stale,
            arrived = loc.arrived,
            route = if (showRoutes) trip?.rest else null,
            fixMillis = loc.updatedMillis,
            // A stale or arrived pin stays where it is.
            speedMps = if (loc.arrived || stale) 0.0 else loc.speed?.toDouble(),
            heading = loc.heading,
            track = trip?.geom?.track,
            along = trip?.along,
        )
    }
    return MeetMapData(place, meeting.title, markers)
}

private fun meetActions(
    context: Context,
    vm: MapViewModel,
    meetVm: MeetViewModel,
    places: PlaceActions,
    share: (Meeting) -> Unit,
) = MeetActions(
    directions = { m ->
        meetVm.closeMeeting()
        vm.routeToMeeting(m)
    },
    invite = { m ->
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, inviteText(m))
        context.startSafely(Intent.createChooser(send, "Invite to meet-up"), vm)
    },
    copy = places.copy,
    choosePlace = {
        vm.goHome()
        vm.search.open()
        vm.message = "Find the place, then tap Meet"
    },
    share = share,
    showOnMap = { m ->
        meetVm.closeMeeting()
        meetVm.showOnMap(m.id)
        vm.goHome()
        // Fit the place, the members on their way and me.
        val points = buildList {
            add(Point(m.lat, m.lon))
            meetVm.locations.values.forEach { add(Point(it.lat, it.lon)) }
            vm.userLocation?.let { add(it) }
        }
        Geo.boundsOf(points)?.let { vm.commands.tryEmit(MapCommand.Fit(it)) }
    },
)

private fun Context.startSafely(intent: Intent, vm: MapViewModel) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        vm.message = "No app can do this"
    }
}
