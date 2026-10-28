package com.example.findme.ui.meet

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.Geo
import com.example.findme.data.NavStore
import com.example.findme.data.SettingsStore
import com.example.findme.data.Place
import com.example.findme.data.auth.UserProfile
import com.example.findme.data.meet.LiveLocation
import com.example.findme.data.meet.MeetResult
import com.example.findme.data.meet.Meeting
import com.example.findme.data.meet.MeetingDraft
import com.example.findme.data.meet.MeetingRepository
import com.example.findme.data.meet.PolylineCodec
import com.example.findme.data.meet.RouteGeom
import com.example.findme.data.meet.RouteProjection
import com.example.findme.data.meet.SharedRoute
import com.example.findme.location.LocationShareService
import com.example.findme.location.Sharing
import com.example.findme.ui.profile.PhotoCache
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.Job
import com.example.findme.data.location.LocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the create/edit sheet is editing. */
sealed interface MeetEditor {
    /** New meeting at [place]. */
    data class New(val place: Place) : MeetEditor
    /** Organizer edits an existing meeting. */
    data class Edit(val meeting: Meeting) : MeetEditor
}

/** Meet-ups: the user's meetings, create / join / leave, live map, shared routes. */
class MeetViewModel(
    app: Application,
    private val repo: MeetingRepository,
    private val navStore: NavStore,
    private val location: LocationRepository,
    private val settings: SettingsStore,
) : AndroidViewModel(app) {

    var user by mutableStateOf<UserProfile?>(null); private set
    var meetings by mutableStateOf(emptyList<Meeting>()); private set

    /** The list of meet-ups is open. */
    var listOpen by mutableStateOf(false)
    /** Id of the meet-up whose sheet is open. */
    var openId by mutableStateOf<String?>(null); private set
    var editor by mutableStateOf<MeetEditor?>(null); private set
    var joinOpen by mutableStateOf(false); private set
    /** Prefill for the join dialog (from an invite link). */
    var joinCode by mutableStateOf(""); private set
    var busy by mutableStateOf(false); private set

    /** Messages for the toast; the screen clears it after showing. */
    var message by mutableStateOf<String?>(null)

    /** Set when an action needs sign-in first; the screen opens the profile sheet. */
    var needsSignIn by mutableStateOf(false)

    /** Invite code that arrived by link before the user signed in. */
    private var pendingCode: String? = null
    private var stopObserving: () -> Unit = {}
    /** The first snapshot of my meetings has arrived (an empty list before it means "not loaded"). */
    private var meetingsLoaded = false

    // ---------- Live map ----------

    /** Meeting I'm sending my location to (from the foreground service). */
    var sharing by mutableStateOf<Sharing?>(null); private set

    /** Meeting whose members are drawn on the map. */
    var mapMeetingId by mutableStateOf<String?>(null); private set
    val mapMeeting: Meeting? get() = mapMeetingId?.let { id -> meetings.firstOrNull { it.id == id } }

    /** Live positions in [mapMeetingId], by uid. */
    var locations by mutableStateOf<Map<String, LiveLocation>>(emptyMap()); private set

    /** Routes members drive in navigation in [mapMeetingId], by uid. */
    var routes by mutableStateOf<Map<String, SharedRoute>>(emptyMap()); private set

    /** Member photos for the map markers, by uid. */
    var photos by mutableStateOf<Map<String, Bitmap>>(emptyMap()); private set

    /** Ticks every few seconds so "updated 10 s ago" and stale markers stay current. */
    var now by mutableLongStateOf(System.currentTimeMillis()); private set

    /** My position, for "ahead of you" (not sent anywhere from here). */
    var myLocation by mutableStateOf<Point?>(null); private set

    private var stopLocations: () -> Unit = {}
    private var stopRoutes: () -> Unit = {}
    private var clockJob: Job? = null

    // ---------- My navigation ----------

    /** The route I drive in navigation to a meeting place, as the others see it. */
    var myNavRoute by mutableStateOf<RouteGeom?>(null); private set
    private var navMeetingId: String? = null
    /** Sharing was switched on by "Go", so it goes off with the navigation. */
    private var navStartedSharing = false
    private var publishedPoints: List<Point>? = null
    /** Last navigation from the map; applied again once sign-in and my meetings have loaded. */
    private var lastNav: MyNav? = null

    private val geoms = HashMap<String, RouteGeom>()
    private val joins = HashMap<Pair<RouteGeom, RouteGeom>, Double?>()
    /** Segment where each person (uid) was found on each route last time. */
    private val lastSegment = HashMap<Pair<String, RouteGeom>, Int>()

    init {
        viewModelScope.launch {
            location.fix.filterNotNull().collect { onMyLocation(it.point) }
        }
        viewModelScope.launch {
            LocationShareService.state.collect { s ->
                sharing = s
                // Sharing always shows that meeting on my map too.
                if (s != null && mapMeetingId != s.meetingId) showOnMap(s.meetingId)
            }
        }
    }

    val openMeeting: Meeting? get() = openId?.let { id -> meetings.firstOrNull { it.id == id } }

    /** Called by the screen whenever the signed-in user changes. */
    fun onUser(newUser: UserProfile?) {
        val old = user
        user = newUser
        if (old?.uid != newUser?.uid) {
            stopObserving()
            meetings = emptyList()
            meetingsLoaded = false
            openId = null
            hideMap()
            // Signed out or switched account: nobody should keep seeing me.
            if (old != null) stopSharing() else sharing?.let { showOnMap(it.meetingId) }
            stopObserving = if (newUser != null) {
                repo.observeMyMeetings(newUser.uid) { list ->
                    meetings = list
                    meetingsLoaded = true
                    syncMyInfo(list)
                    onMeetingsChanged(list)
                    // Navigation restored after a restart may start before the meetings arrive.
                    lastNav?.let { if (navMeetingId == null) applyNav(it) }
                }
            } else {
                {}
            }
        } else {
            syncMyInfo(meetings)
        }
        if (newUser != null) pendingCode?.let { code ->
            pendingCode = null
            openJoin(code)
        }
    }

    /** A meeting I left or that was deleted: stop sharing with it and take it off the map. */
    private fun onMeetingsChanged(list: List<Meeting>) {
        if (!meetingsLoaded) return
        val ids = list.mapTo(HashSet()) { it.id }
        sharing?.let { if (it.meetingId !in ids) stopSharing() }
        mapMeetingId?.let { if (it !in ids) hideMap() }
        navMeetingId?.let {
            if (it !in ids) {
                navMeetingId = null
                myNavRoute = null
                publishedPoints = null
            }
        }
        mapMeeting?.let(::loadPhotos)
    }

    /** Other members see the name and photo copied into each meeting: keep them up to date. */
    private fun syncMyInfo(list: List<Meeting>) {
        val me = user ?: return
        for (m in list) {
            val info = m.members.firstOrNull { it.uid == me.uid } ?: continue
            if (info.name != me.name || info.photoUrl != me.photoUrl) repo.updateMyInfo(m.id, me)
        }
    }

    // ---------- Opening sheets ----------

    fun openList() {
        if (!requireSignIn()) return
        listOpen = true
    }

    /** Opening a meet-up also shows it on the map: that's where its members are. */
    fun openMeeting(id: String) {
        listOpen = false
        openId = id
        showOnMap(id)
    }

    fun closeMeeting() {
        openId = null
    }

    /** "Meet" on a place card. */
    fun startNew(place: Place) {
        if (!requireSignIn()) return
        editor = MeetEditor.New(place)
    }

    /** "New meet-up" on the map: after sign-in the user picks the place first. */
    fun newFromMap(choosePlace: () -> Unit) {
        if (requireSignIn()) choosePlace()
    }

    fun startEdit(meeting: Meeting) {
        editor = MeetEditor.Edit(meeting)
    }

    fun closeEditor() {
        if (!busy) editor = null
    }

    fun openJoin(code: String = "") {
        if (user == null) {
            // Remember the code and ask to sign in; the dialog opens after sign-in.
            if (code.isNotBlank()) pendingCode = code
            requireSignIn()
            return
        }
        joinCode = MeetingRepository.normalizeCode(code)
        listOpen = false
        joinOpen = true
    }

    fun closeJoin() {
        if (!busy) joinOpen = false
    }

    private fun requireSignIn(): Boolean {
        if (!repo.available) {
            message = "Meet-ups are not set up yet"
            return false
        }
        if (user != null) return true
        needsSignIn = true
        message = "Sign in to plan meet-ups"
        return false
    }

    // ---------- Live map & sharing ----------

    /** Who had arrived at the last update; null before the first one of this meeting. */
    private var arrivedBefore: Set<String>? = null

    /** New positions; a friend who has just arrived gets a notification (if the setting is on). */
    private fun onLocations(new: Map<String, LiveLocation>) {
        locations = new
        val arrived = new.values.filter { it.arrived }.mapTo(HashSet()) { it.uid }
        val before = arrivedBefore
        arrivedBefore = arrived
        // Those already there when the meeting opened are not news.
        if (before == null || !settings.friendArrived) return
        val meeting = mapMeeting ?: return
        (arrived - before).filter { it != user?.uid }.forEach { uid ->
            val name = meeting.members.firstOrNull { it.uid == uid }?.name ?: return@forEach
            FriendArrivedNotifier.notify(getApplication(), uid, name.substringBefore(' '), meeting.placeName)
        }
    }

    /** Draws the meeting's members (and the routes they drive) on the map and keeps them updated. */
    fun showOnMap(meetingId: String) {
        if (mapMeetingId == meetingId) return
        stopLocations()
        stopRoutes()
        locations = emptyMap()
        routes = emptyMap()
        mapMeetingId = meetingId
        arrivedBefore = null
        stopLocations = repo.observeLocations(meetingId) { onLocations(it) }
        stopRoutes = repo.observeRoutes(meetingId) { list ->
            routes = list
            // Drop decoded lines nobody drives any more.
            val used = list.values.mapTo(HashSet()) { it.encoded }
            geoms.keys.retainAll(used)
            joins.keys.retainAll { it.second in geoms.values }
            lastSegment.keys.retainAll { it.second in geoms.values || it.second === myNavRoute }
        }
        mapMeeting?.let(::loadPhotos)
        if (clockJob?.isActive != true) {
            clockJob = viewModelScope.launch {
                while (isActive) {
                    now = System.currentTimeMillis()
                    delay(5_000)
                }
            }
        }
    }

    fun hideMap() {
        stopLocations()
        stopRoutes()
        stopLocations = {}
        stopRoutes = {}
        mapMeetingId = null
        locations = emptyMap()
        routes = emptyMap()
        clockJob?.cancel()
        clockJob = null
    }

    /** My position from [LocationRepository]; small moves are ignored to save work. */
    private fun onMyLocation(point: Point) {
        val last = myLocation
        if (last == null || Geo.distanceMeters(last, point) > 10) myLocation = point
    }

    /** The screen checks the location permission first: Android needs it to start tracking. */
    fun startSharing(meeting: Meeting) {
        LocationShareService.start(getApplication(), meeting.id, meeting.title, meeting.lat, meeting.lon)
        showOnMap(meeting.id)
        message = "Members of this meet-up now see you on the map"
    }

    fun stopSharing() {
        LocationShareService.stop(getApplication())
    }

    fun isSharing(meeting: Meeting) = sharing?.meetingId == meeting.id

    private fun loadPhotos(meeting: Meeting) {
        for (m in meeting.members) {
            val url = m.photoUrl ?: continue
            if (photos.containsKey(m.uid)) continue
            viewModelScope.launch {
                val bmp = PhotoCache.load(url)?.asAndroidBitmap() ?: return@launch
                photos = photos + (m.uid to bmp)
            }
        }
    }

    // ---------- Navigation: my route for the others ----------

    /**
     * The map's navigation changed. "Go" to a meeting place shares my location and publishes
     * the route I chose, so the others see me drive it; ending navigation takes the route back.
     */
    fun onNavigation(nav: MyNav?) {
        lastNav = nav
        applyNav(nav)
    }

    private fun applyNav(nav: MyNav?) {
        val me = user ?: return
        if (nav == null) {
            val id = navMeetingId ?: return
            navMeetingId = null
            myNavRoute = null
            publishedPoints = null
            LocationShareService.navProgress = null
            repo.removeRoute(id, me.uid)
            // At the place the service marks me "arrived" and stops by itself.
            val meeting = meetings.firstOrNull { it.id == id }
            val atPlace = meeting != null &&
                myLocation?.let { Geo.distanceMeters(it, Point(meeting.lat, meeting.lon)) < 150 } == true
            if (navStartedSharing && !atPlace && sharing?.meetingId == id) stopSharing()
            navStartedSharing = false
            navStore.sharedByNavMeetingId = null
            return
        }
        val meeting = meetings.firstOrNull { Geo.distanceMeters(Point(it.lat, it.lon), nav.destination) < 150 } ?: return
        if (navMeetingId != null && navMeetingId != meeting.id) applyNav(null)
        if (navMeetingId == null) {
            navMeetingId = meeting.id
            if (sharing?.meetingId != meeting.id) {
                navStartedSharing = true
                navStore.sharedByNavMeetingId = meeting.id
                LocationShareService.start(getApplication(), meeting.id, meeting.title, meeting.lat, meeting.lon)
                message = "Members of \"${meeting.title}\" see your route"
            } else {
                // Navigation came back after the app was closed: "Go" may have switched sharing on.
                navStartedSharing = navStore.sharedByNavMeetingId == meeting.id
            }
            showOnMap(meeting.id)
        }
        if (publishedPoints !== nav.points) {
            publishedPoints = nav.points
            myNavRoute = RouteGeom(nav.points)
            repo.publishRoute(meeting.id, me.uid, nav.mode, nav.points, nav.durationSeconds, nav.distanceMeters)
        }
    }

    /** Time and distance left in my navigation, sent with my position. */
    fun onNavProgress(etaSeconds: Double?, remainingMeters: Double?) {
        LocationShareService.navProgress =
            if (navMeetingId != null && etaSeconds != null && remainingMeters != null) etaSeconds to remainingMeters else null
    }

    // ---------- Members' trips ----------

    private fun routeGeom(uid: String): RouteGeom? {
        val r = routes[uid] ?: return null
        return geoms.getOrPut(r.encoded) { RouteGeom(PolylineCodec.decode(r.encoded)) }
    }

    /** What is left of a member's trip: their route (after "Go") and the time left. */
    fun memberTrip(uid: String): MemberTrip? {
        val loc = locations[uid] ?: return null
        if (loc.arrived) return null
        val route = routes[uid]
        val geom = routeGeom(uid)
        val proj = geom?.let { project(it, uid, Point(loc.lat, loc.lon)) }
        val left = if (geom != null && proj != null) (geom.length - proj.along).coerceAtLeast(0.0) else null
        val estimate = if (route != null && geom != null && left != null && geom.length > 0) {
            route.durationSeconds * left / geom.length
        } else {
            null
        }
        return MemberTrip(
            rest = if (geom != null && proj != null) geom.rest(proj) else null,
            etaSeconds = loc.etaSeconds ?: estimate,
            remainingMeters = loc.remainingMeters ?: left,
            navigating = route != null,
            geom = geom,
            along = proj?.takeIf { it.offRoute < ON_ROAD_METERS }?.along,
        )
    }

    /**
     * Where [uid] is on [geom], looking first around where they were last time, so a route
     * that passes the same street twice doesn't make them jump to the other pass.
     */
    private fun project(geom: RouteGeom, uid: String, pos: Point): RouteProjection? {
        val key = uid to geom
        return geom.project(pos, near = lastSegment[key])?.also { lastSegment[key] = it.segment }
    }

    /** Phase 5: is this member ahead of me / behind me on the same road, or where do our roads meet. */
    fun relation(uid: String): RoadRelation? {
        val me = myLocation ?: return null
        if (uid == user?.uid) return null
        val loc = locations[uid] ?: return null
        if (loc.arrived || isStale(loc, now)) return null
        val myUid = user?.uid ?: return null
        return roadRelation(
            me, Point(loc.lat, loc.lon), myNavRoute, routeGeom(uid),
            joinAlong = { mine, theirs, myAlong ->
                // A cached meeting still ahead of me stays right; once I've passed it, look again from here.
                val key = mine to theirs
                val cached = joins[key]
                if (joins.containsKey(key) && (cached == null || cached >= myAlong)) cached
                else mine.firstMeeting(theirs, from = myAlong).also { joins[key] = it }
            },
            project = { geom, pos, isMe -> project(geom, if (isMe) myUid else uid, pos) },
        )
    }

    // ---------- Actions ----------

    fun save(title: String, timeMillis: Long?) {
        val me = user ?: return
        val ed = editor ?: return
        if (busy) return
        val draft = when (ed) {
            is MeetEditor.New -> MeetingDraft(
                title = title.ifBlank { ed.place.name },
                placeName = ed.place.name,
                placeAddress = ed.place.address,
                lat = ed.place.lat,
                lon = ed.place.lon,
                timeMillis = timeMillis,
            )
            is MeetEditor.Edit -> MeetingDraft(
                title = title.ifBlank { ed.meeting.placeName },
                placeName = ed.meeting.placeName,
                placeAddress = ed.meeting.placeAddress,
                lat = ed.meeting.lat,
                lon = ed.meeting.lon,
                timeMillis = timeMillis,
            )
        }
        busy = true
        viewModelScope.launch {
            when (ed) {
                is MeetEditor.New -> when (val r = repo.create(me, draft)) {
                    is MeetResult.Ok -> {
                        editor = null
                        openMeeting(r.value)
                        message = "Meet-up created. Invite your friends"
                    }
                    is MeetResult.Error -> message = r.message
                }
                is MeetEditor.Edit -> when (val r = repo.update(ed.meeting.id, draft)) {
                    is MeetResult.Ok -> editor = null
                    is MeetResult.Error -> message = r.message
                }
            }
            busy = false
        }
    }

    fun join(code: String) {
        val me = user ?: return
        if (busy) return
        // Already a member: just open it.
        val clean = MeetingRepository.normalizeCode(code)
        meetings.firstOrNull { it.code == clean }?.let {
            joinOpen = false
            openMeeting(it.id)
            return
        }
        busy = true
        viewModelScope.launch {
            when (val r = repo.join(me, code)) {
                is MeetResult.Ok -> {
                    joinOpen = false
                    openMeeting(r.value)
                    message = "You joined the meet-up"
                }
                is MeetResult.Error -> message = r.message
            }
            busy = false
        }
    }

    fun leave(meeting: Meeting) {
        val me = user ?: return
        if (isSharing(meeting)) stopSharing()
        runAndClose { repo.leave(meeting.id, me.uid) }
    }

    fun delete(meeting: Meeting) {
        if (isSharing(meeting)) stopSharing()
        runAndClose { repo.delete(meeting) }
    }

    private fun runAndClose(block: suspend () -> MeetResult<Unit>) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            when (val r = block()) {
                is MeetResult.Ok -> openId = null
                is MeetResult.Error -> message = r.message
            }
            busy = false
        }
    }

    fun isOwner(meeting: Meeting) = meeting.ownerUid == user?.uid

    override fun onCleared() {
        stopObserving()
        stopLocations()
        stopRoutes()
    }
}
