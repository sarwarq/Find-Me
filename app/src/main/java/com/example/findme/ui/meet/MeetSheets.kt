package com.example.findme.ui.meet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.auth.UserProfile
import com.example.findme.data.TravelMode
import com.example.findme.data.meet.LiveLocation
import com.example.findme.data.meet.Meeting
import com.example.findme.data.meet.MeetingMember
import com.example.findme.data.meet.MeetingRepository
import com.example.findme.ui.components.MapChip
import com.example.findme.ui.components.ActionPill
import com.example.findme.ui.components.SegmentedControl
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.profile.Avatar
import com.example.findme.ui.profile.avatarColor
import com.example.findme.ui.components.StatusKind
import com.example.findme.ui.components.StatusBadge
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import com.yandex.mapkit.geometry.Point
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Orange used for meet-ups everywhere, same as the friend's route on the map. */
val MeetColor = YColors.Coral

/** Things the meet-up sheets need from the rest of the app. */
class MeetActions(
    val directions: (Meeting) -> Unit,
    val invite: (Meeting) -> Unit,
    val copy: (String) -> Unit,
    /** "New meet-up" from the list: choose a place first. */
    val choosePlace: () -> Unit,
    /** Starts sharing my location; the screen asks for permissions first. */
    val share: (Meeting) -> Unit,
    /** Closes the sheet and fits the map to the place and the members. */
    val showOnMap: (Meeting) -> Unit,
)

// =====================================================================
// List of meet-ups
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetListSheet(vm: MeetViewModel, actions: MeetActions, userLocation: Point?) {
    ModalBottomSheet(
        onDismissRequest = { vm.listOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yPalette.sheet,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "Meet-ups",
                style = MaterialTheme.typography.headlineSmall,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionPill("New", R.drawable.ms_add, {
                    vm.listOpen = false
                    actions.choosePlace()
                }, primary = true, modifier = Modifier.weight(1f))
                ActionPill("Join by code", R.drawable.ms_person_add, { vm.openJoin() }, modifier = Modifier.weight(1f))
            }

            if (vm.meetings.isEmpty()) {
                Text(
                    "No meet-ups yet. Find a place on the map and tap Meet, or join a friend's meet-up with their code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            } else {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                    vm.meetings.forEach { m ->
                        MeetingRow(m, userLocation) { vm.openMeeting(m.id) }
                    }
                }
            }
        }
    }
}

/** Row of a meet-up in a list: orange icon, title, "Today, 18:30 · 3 people", distance. */
@Composable
fun MeetingRow(meeting: Meeting, userLocation: Point?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MeetColor),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(R.drawable.ms_group, null, tint = YColors.Ink, size = 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                meeting.title,
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(meetingTimeText(meeting.timeMillis) ?: "Any time", peopleText(meeting.members.size))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Geo.distanceText(userLocation, Point(meeting.lat, meeting.lon))?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
        }
    }
}

// =====================================================================
// One meet-up
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingSheet(vm: MeetViewModel, actions: MeetActions, userLocation: Point?) {
    ModalBottomSheet(
        onDismissRequest = { vm.closeMeeting() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yPalette.sheet,
    ) {
        val meeting = vm.openMeeting
        if (meeting == null) {
            // Just joined: the meeting arrives from the server in a moment.
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = yPalette.accent)
            }
            return@ModalBottomSheet
        }
        MeetingContent(vm, meeting, actions, userLocation)
    }
}

@Composable
private fun MeetingContent(vm: MeetViewModel, meeting: Meeting, actions: MeetActions, userLocation: Point?) {
    val owner = vm.isOwner(meeting)
    var confirm by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp),
    ) {
        // Header: coral tile, title, "Today 18:45 · Garden St 14", "in 14 min"
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(yPalette.coral),
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_group, null, tint = yPalette.onCoral, size = 24.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    meeting.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        meetingTimeText(meeting.timeMillis) ?: "Any time",
                        // The title is often the place name itself: then the address says more.
                        if (meeting.title != meeting.placeName) meeting.placeName else meeting.placeAddress,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            countdownText(meeting.timeMillis, vm.now)?.let {
                Spacer(Modifier.width(8.dp))
                StatusBadge(it, StatusKind.Neutral)
            }
        }

        // People
        val place = Point(meeting.lat, meeting.lon)
        val live = vm.mapMeetingId == meeting.id
        Column(Modifier.padding(top = 8.dp)) {
            meeting.members.forEachIndexed { i, member ->
                MemberRow(
                    member,
                    isOwner = member.uid == meeting.ownerUid,
                    isMe = member.uid == vm.user?.uid,
                    // Positions are loaded only for the meeting shown on the map.
                    status = if (live) {
                        memberStatus(
                            vm.locations[member.uid],
                            vm.memberTrip(member.uid),
                            vm.relation(member.uid),
                            vm.routes[member.uid]?.mode,
                            place,
                            meeting.timeMillis,
                            vm.now,
                        )
                    } else {
                        null
                    },
                    last = i == meeting.members.lastIndex,
                )
            }
        }

        ShareRow(
            sharing = vm.isSharing(meeting),
            onClick = { if (vm.isSharing(meeting)) vm.stopSharing() else actions.share(meeting) },
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(yPalette.accent)
                    .clickable { actions.directions(meeting) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YIcon(R.drawable.ms_navigation, null, tint = yPalette.onAccent, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Text("Go", color = yPalette.onAccent, style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(yPalette.accentSoft)
                    .clickable { actions.invite(meeting) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YIcon(R.drawable.ms_person_add, null, tint = yPalette.accent, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text("Invite", color = yPalette.accent, style = MaterialTheme.typography.labelLarge)
            }
        }

        HorizontalDivider(Modifier.padding(top = 16.dp), color = yPalette.field, thickness = 8.dp)

        // Where: tap to see the place and everyone on the map
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { actions.showOnMap(meeting) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(R.drawable.ms_location_on, null, tint = yPalette.textSecondary, size = 22.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(meeting.placeName, style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary)
                Text(
                    meeting.placeAddress?.takeIf { it != meeting.placeName } ?: "Show on map",
                    style = MaterialTheme.typography.bodySmall,
                    color = yPalette.textSecondary,
                )
            }
            Geo.distanceText(userLocation, Point(meeting.lat, meeting.lon))?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
            }
        }
        if (owner) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.startEdit(meeting) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                YIcon(R.drawable.ms_edit, null, tint = yPalette.textSecondary, size = 22.dp)
                Spacer(Modifier.width(14.dp))
                Text("Edit time and title", style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary)
            }
        }

        // Invite code
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(yPalette.field)
                .clickable { actions.copy(meeting.code) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Invite code", style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
                Text(
                    spacedCode(meeting.code),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = yPalette.textPrimary,
                )
            }
            YIcon(R.drawable.ms_content_copy, "Copy code", tint = yPalette.accent, size = 24.dp)
        }
        Text(
            "Friends open FindMe → Meet-ups → Join by code",
            style = MaterialTheme.typography.bodySmall,
            color = yPalette.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !vm.busy) { confirm = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(if (owner) R.drawable.ms_delete else R.drawable.ms_logout, null, tint = yPalette.onDangerSoft, size = 22.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                if (owner) "Delete meet-up" else "Leave meet-up",
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.onDangerSoft,
                modifier = Modifier.weight(1f),
            )
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), color = yPalette.onDangerSoft, strokeWidth = 2.dp)
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = yPalette.sheet,
            title = { Text(if (owner) "Delete meet-up?" else "Leave meet-up?", color = yPalette.textPrimary) },
            text = {
                Text(
                    if (owner) "It will disappear for everyone, and the invite code will stop working."
                    else "You can join again with the invite code.",
                    color = yPalette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (owner) vm.delete(meeting) else vm.leave(meeting)
                }) { Text(if (owner) "Delete" else "Leave", color = YColors.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = yPalette.textSecondary) }
            },
        )
    }
}

/** "● You're sharing your location  [Stop]" (green) or "Your location is hidden  [Share]" (gray). */
@Composable
private fun ShareRow(sharing: Boolean, onClick: () -> Unit) {
    val p = yPalette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (sharing) p.successSoft else p.field)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YIcon(R.drawable.ms_near_me, null, tint = if (sharing) p.onSuccessSoft else p.textSecondary, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            if (sharing) "You're sharing your location" else "Your location is hidden",
            style = MaterialTheme.typography.labelMedium,
            color = if (sharing) p.onSuccessSoft else p.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (sharing) "Stop" else "Share",
            style = MaterialTheme.typography.labelMedium,
            color = if (sharing) p.textPrimary else p.onAccent,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (sharing) p.sheet else p.accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** "in 14 min" in the last hour before the meeting, "Now" up to an hour after it, else nothing. */
private fun countdownText(timeMillis: Long?, now: Long): String? {
    timeMillis ?: return null
    val left = (timeMillis - now) / 1000.0
    return when {
        left >= 3600 -> null
        left >= 60 -> "in ${Geo.durationText(left)}"
        left > -3600 -> "Now"
        else -> null
    }
}

/** Member's state on the way: the detail line, and a short badge with its color. */
private data class MemberStatus(val text: String, val kind: StatusKind?, val badge: String? = null)

/** Stale after this long without an update: the phone may be off or without signal. */
private const val STALE_MILLIS = 2 * 60 * 1000L

private fun memberStatus(
    loc: LiveLocation?,
    trip: MemberTrip?,
    relation: RoadRelation?,
    mode: TravelMode?,
    place: Point,
    meetingTime: Long?,
    now: Long,
): MemberStatus {
    loc ?: return MemberStatus("Location is off", null)
    val toGo = Geo.formatDistance(trip?.remainingMeters ?: Geo.distanceMeters(Point(loc.lat, loc.lon), place))
    if (loc.arrived) return MemberStatus("At the place", StatusKind.Info, badge = "Arrived")
    val age = now - (loc.updatedMillis ?: now)
    if (age > STALE_MILLIS) return MemberStatus("Seen ${agoText(age)} · $toGo to go", null)
    val road = relation?.let { " · ${it.text()}" }.orEmpty()
    val eta = trip?.etaSeconds
    if (trip?.navigating != true || eta == null) return MemberStatus("On the way · $toGo to go$road", StatusKind.Info)
    // The ETA was counted when they sent the location, not now.
    val arrival = (loc.updatedMillis ?: now) + (eta * 1000).toLong()
    val left = ((arrival - now) / 1000.0).coerceAtLeast(0.0)
    val text = "${modeVerb(mode)} · ${Geo.durationText(left)} · arrives ${clockOf(arrival)}"
    val late = lateMinutes(arrival, meetingTime)
    return when {
        late == null -> MemberStatus("$text$road", null)
        late > VERY_LATE_MIN -> MemberStatus("$text$road", StatusKind.Danger, badge = "+$late min")
        late > LATE_GRACE_MIN -> MemberStatus("$text$road", StatusKind.Warning, badge = "+$late min")
        else -> MemberStatus("$text$road", StatusKind.Success, badge = "On time")
    }
}

/** Later than this the "+N min" badge turns red. */
private const val VERY_LATE_MIN = 15

/** "Driving", "On transit"... for a member in navigation. */
private fun modeVerb(mode: TravelMode?) = when (mode) {
    TravelMode.Transit -> "On transit"
    TravelMode.Walk -> "Walking"
    TravelMode.Bike, TravelMode.Scooter -> "Cycling"
    else -> "Driving"
}

/** Minutes after the meeting time, or null when it has no time. */
private fun lateMinutes(arrivalMillis: Long, meetingTime: Long?): Long? =
    meetingTime?.let { Math.round((arrivalMillis - it) / 60_000.0) }

/** A couple of minutes late still counts as on time. */
private const val LATE_GRACE_MIN = 2

private fun clockOf(millis: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return clockText(t.hour, t.minute)
}

/** True when the position is older than [STALE_MILLIS]. */
fun isStale(loc: LiveLocation, now: Long) = now - (loc.updatedMillis ?: now) > STALE_MILLIS

/** "5 min ago", "2 hr ago". */
fun agoText(millis: Long): String {
    val min = millis / 60_000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        else -> "${min / 60} hr ago"
    }
}

/** "Ⓐ Aziz / 12 min · joins your road in 0.8 km  [+7 min]", a divider below unless [last]. */
@Composable
private fun MemberRow(member: MeetingMember, isOwner: Boolean, isMe: Boolean, status: MemberStatus?, last: Boolean) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(UserProfile(member.uid, member.name, null, member.photoUrl), 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isMe) "You" else member.name.substringBefore(' '),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(if (isOwner) "Organizer" else null, status?.text).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = yPalette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val badge = status?.badge
            val kind = status?.kind
            if (badge != null && kind != null) {
                Spacer(Modifier.width(8.dp))
                StatusBadge(badge, kind)
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 52.dp), color = yPalette.divider)
    }
}

// =====================================================================
// Create / edit
// =====================================================================

private enum class Day(val title: String) { Any("Any time"), Today("Today"), Tomorrow("Tomorrow"), Other("") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetEditorSheet(vm: MeetViewModel) {
    val editor = vm.editor ?: return
    val zone = ZoneId.systemDefault()
    val existing = (editor as? MeetEditor.Edit)?.meeting
    val placeName = when (editor) {
        is MeetEditor.New -> editor.place.name
        is MeetEditor.Edit -> editor.meeting.placeName
    }
    val placeAddress = when (editor) {
        is MeetEditor.New -> editor.place.address
        is MeetEditor.Edit -> editor.meeting.placeAddress
    }

    // An existing time on another day keeps its own date as a fourth choice.
    val existingTime = existing?.timeMillis?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) }
    val today = LocalDate.now(zone)
    val initialDay = when (existingTime?.toLocalDate()) {
        null -> if (existing != null) Day.Any else Day.Today
        today -> Day.Today
        today.plusDays(1) -> Day.Tomorrow
        else -> Day.Other
    }

    var title by rememberSaveable { mutableStateOf(existing?.title ?: placeName) }
    var day by rememberSaveable { mutableStateOf(initialDay) }
    var minutes by rememberSaveable {
        mutableIntStateOf(existingTime?.toLocalTime()?.let { it.hour * 60 + it.minute } ?: defaultMinutes(zone))
    }
    var pickingTime by remember { mutableStateOf(false) }

    val days = buildList {
        add(Day.Any); add(Day.Today); add(Day.Tomorrow)
        if (initialDay == Day.Other) add(Day.Other)
    }
    val otherDate = existingTime?.toLocalDate()

    fun chosenMillis(): Long? {
        val date = when (day) {
            Day.Any -> return null
            Day.Today -> today
            Day.Tomorrow -> today.plusDays(1)
            Day.Other -> otherDate ?: today
        }
        return date.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone).toInstant().toEpochMilli()
    }

    ModalBottomSheet(
        onDismissRequest = { vm.closeEditor() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yPalette.ground,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (existing == null) "New meet-up" else "Edit meet-up",
                style = MaterialTheme.typography.headlineSmall,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )

            // Name
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(yPalette.sheet).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Name", style = MaterialTheme.typography.labelSmall, color = yPalette.textSecondary)
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(MeetingRepository.MAX_TITLE) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = yPalette.textPrimary),
                    cursorBrush = SolidColor(yPalette.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Place
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(yPalette.sheet).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(yPalette.coral),
                    contentAlignment = Alignment.Center,
                ) {
                    YIcon(R.drawable.ms_location_on, null, tint = yPalette.onCoral, size = 22.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        placeName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    placeAddress?.takeIf { it != placeName }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = yPalette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // When
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(yPalette.sheet).padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text("When", style = MaterialTheme.typography.labelSmall, color = yPalette.textSecondary)
                Spacer(Modifier.height(10.dp))
                SegmentedControl(
                    items = days.map { if (it == Day.Other) shortDate(otherDate) else it.title },
                    selected = days.indexOf(day).coerceAtLeast(0),
                    onSelect = { day = days[it] },
                )
                if (day != Day.Any) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { pickingTime = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        YIcon(R.drawable.ms_schedule, null, tint = yPalette.textSecondary, size = 22.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Time", style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary, modifier = Modifier.weight(1f))
                        Text(
                            clockText(minutes / 60, minutes % 60),
                            style = MaterialTheme.typography.titleMedium,
                            color = yPalette.accent,
                        )
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                }
            }

            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = { vm.save(title, chosenMillis()) },
                enabled = !vm.busy && title.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                color = if (title.isNotBlank()) yPalette.accent else yPalette.accent.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (vm.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                    } else {
                        Text(
                            if (existing == null) "Create" else "Save",
                            style = MaterialTheme.typography.titleMedium,
                            color = yPalette.onAccent,
                        )
                    }
                }
            }
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = yPalette.sheet,
            text = {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = yPalette.field,
                        selectorColor = yPalette.accent,
                        timeSelectorSelectedContainerColor = yPalette.accentSoft,
                        timeSelectorSelectedContentColor = yPalette.accent,
                        timeSelectorUnselectedContainerColor = yPalette.field,
                        timeSelectorUnselectedContentColor = yPalette.textPrimary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    minutes = state.hour * 60 + state.minute
                    pickingTime = false
                }) { Text("OK", color = yPalette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text("Cancel", color = yPalette.textSecondary) }
            },
        )
    }
}

// =====================================================================
// Join by code
// =====================================================================

@Composable
fun JoinDialog(vm: MeetViewModel) {
    var code by rememberSaveable { mutableStateOf(vm.joinCode) }
    val valid = code.length == MeetingRepository.CODE_LENGTH
    AlertDialog(
        onDismissRequest = { vm.closeJoin() },
        containerColor = yPalette.sheet,
        title = { Text("Join a meet-up", color = yPalette.textPrimary) },
        text = {
            Column {
                Text(
                    "Enter the code your friend sent you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = yPalette.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = MeetingRepository.normalizeCode(it) },
                    singleLine = true,
                    placeholder = { Text("ABC123") },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { if (valid) vm.join(code) }),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.join(code) }, enabled = valid && !vm.busy) {
                if (vm.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = yPalette.accent, strokeWidth = 2.dp)
                } else {
                    Text("Join", color = if (valid) yPalette.accent else yPalette.textSecondary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.closeJoin() }) { Text("Cancel", color = yPalette.textSecondary) }
        },
    )
}

// =====================================================================
// Helpers
// =====================================================================

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = yPalette.accent,
    focusedLabelColor = yPalette.accent,
    cursorColor = yPalette.accent,
    focusedTextColor = yPalette.textPrimary,
    unfocusedTextColor = yPalette.textPrimary,
)

/** "Today, 18:30", "Tomorrow, 9:05", "Mon 28 Oct, 18:30"; null when no time. */
fun meetingTimeText(millis: Long?): String? {
    millis ?: return null
    val zone = ZoneId.systemDefault()
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val today = LocalDate.now(zone)
    val day = when (t.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> shortDate(t.toLocalDate())
    }
    return "$day, ${clockText(t.hour, t.minute)}"
}

private fun shortDate(date: LocalDate?): String =
    date?.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) ?: ""

private fun clockText(hour: Int, minute: Int) = "%d:%02d".format(hour, minute)

private fun peopleText(n: Int) = if (n == 1) "1 person" else "$n people"

/** "K7MPQ2" → "K7M PQ2", easier to read aloud. */
private fun spacedCode(code: String) = if (code.length == 6) code.take(3) + " " + code.drop(3) else code

/** One hour from now, rounded up to a quarter. */
private fun defaultMinutes(zone: ZoneId): Int {
    val now = LocalTime.now(zone)
    val m = now.hour * 60 + now.minute + 60
    return ((m + 14) / 15 * 15) % (24 * 60)
}

/** Text sent with "Invite". */
fun inviteText(meeting: Meeting): String = buildString {
    appendLine("Join my meet-up \"${meeting.title}\" in FindMe")
    meetingTimeText(meeting.timeMillis)?.let { appendLine(it) }
    appendLine(meeting.placeAddress?.takeIf { it != meeting.placeName }?.let { "${meeting.placeName}, $it" } ?: meeting.placeName)
    appendLine()
    appendLine("Code: ${meeting.code}")
    appendLine("findme://join/${meeting.code}")
    append("https://yandex.com/maps/?pt=${meeting.lon},${meeting.lat}&z=17&l=map")
}

// =====================================================================
// Live chip on the map
// =====================================================================

/** Meet-up for the main sheet: the one on the map, else the next one (or one that began < 2 h ago). */
fun MeetViewModel.featuredMeeting(nowMillis: Long = System.currentTimeMillis()): Meeting? {
    mapMeeting?.let { return it }
    val from = nowMillis - 2 * 60 * 60 * 1000L
    return meetings
        .filter { (it.timeMillis ?: Long.MIN_VALUE) >= from }
        .minByOrNull { it.timeMillis ?: Long.MAX_VALUE }
}

/** Coral card on the main sheet: "Coffee at Lumen · 18:45 · 2 on the way" with the members' avatars. */
@Composable
fun ActiveMeetCard(vm: MeetViewModel, meeting: Meeting, modifier: Modifier = Modifier) {
    val onMap = vm.mapMeetingId == meeting.id
    val me = vm.user?.uid
    val others = if (onMap) vm.locations.values.filter { it.uid != me } else emptyList()
    val moving = others.count { !it.arrived && !isStale(it, vm.now) }
    val arrived = others.count { it.arrived }
    val sharing = vm.isSharing(meeting)
    val detail = buildList {
        add(meetingTimeText(meeting.timeMillis)?.removePrefix("Today, ") ?: meeting.placeName)
        if (sharing) add("You're visible")
        if (moving > 0) add("$moving on the way")
        if (arrived > 0) add("$arrived arrived")
    }.joinToString(" · ")
    // The X hides the members from the map; while I share, the map keeps them anyway.
    val canHide = onMap && !sharing
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(yPalette.coralSoft)
            .clickable(onClickLabel = "Open meet-up") { vm.openMeeting(meeting.id) }
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = if (canHide) 4.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(yPalette.coral),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(R.drawable.ms_group, null, tint = yPalette.onCoral, size = 22.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                meeting.title,
                style = MaterialTheme.typography.titleMedium,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        AvatarStack(meeting.members.take(3), ring = yPalette.coralSoft)
        if (canHide) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { vm.hideMap() },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_close, "Hide from map", tint = yPalette.iconSecondary, size = 18.dp)
            }
        }
    }
}

/** Members of the meet-up on the map (not me) who are within [radiusM] of [point] now. */
fun MeetViewModel.friendsAt(point: Point, radiusM: Double = 80.0): List<MeetingMember> {
    val meeting = mapMeeting ?: return emptyList()
    val me = user?.uid
    return meeting.members.filter { m ->
        if (m.uid == me) return@filter false
        val loc = locations[m.uid] ?: return@filter false
        !isStale(loc, now) && Geo.distanceMeters(Point(loc.lat, loc.lon), point) <= radiusM
    }
}

/** "Malika is here · Coffee at Lumen  [Open]" on a place card, tinted in the friend's color. */
@Composable
fun FriendHereRow(vm: MeetViewModel, members: List<MeetingMember>, modifier: Modifier = Modifier) {
    val meeting = vm.mapMeeting ?: return
    val first = members.firstOrNull() ?: return
    val color = avatarColor(first.uid)
    val names = members.map { it.name.substringBefore(' ') }
    val who = when (names.size) {
        1 -> "${names[0]} is here"
        2 -> "${names[0]} and ${names[1]} are here"
        else -> "${names[0]} and ${names.size - 1} more are here"
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (yPalette.isDark) 0.22f else 0.12f))
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(UserProfile(first.uid, first.name, null, first.photoUrl), 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                who,
                style = MaterialTheme.typography.labelMedium,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                meeting.title,
                style = MaterialTheme.typography.bodySmall,
                color = yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Open",
            style = MaterialTheme.typography.labelMedium,
            color = yPalette.textPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(yPalette.sheet)
                .clickable { vm.openMeeting(meeting.id) }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/** Up to three overlapping avatars with a ring in the background color. */
@Composable
private fun AvatarStack(members: List<MeetingMember>, ring: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        members.forEach { m ->
            Avatar(
                UserProfile(m.uid, m.name, null, m.photoUrl),
                28.dp,
                Modifier.border(2.dp, ring, CircleShape),
            )
        }
    }
}

// =====================================================================
// Members during my navigation
// =====================================================================

/** Under the guidance card: "(A) Ali  1.2 km ahead · 8 min" for up to two members on their way. */
@Composable
fun MeetNavChips(vm: MeetViewModel) {
    val meeting = vm.mapMeeting ?: return
    val me = vm.user?.uid
    val rows = meeting.members.mapNotNull { m ->
        if (m.uid == me) return@mapNotNull null
        val loc = vm.locations[m.uid] ?: return@mapNotNull null
        val detail = when {
            loc.arrived -> "arrived"
            isStale(loc, vm.now) -> "seen ${agoText(vm.now - (loc.updatedMillis ?: vm.now))}"
            else -> listOfNotNull(
                vm.relation(m.uid)?.short(),
                vm.memberTrip(m.uid)?.etaSeconds?.let { Geo.durationText(it) },
            ).joinToString(" · ").ifEmpty { "on the way" }
        }
        m to detail
    }
    rows.take(2).forEach { (m, detail) ->
        MapChip(m.name.substringBefore(' '), detail) {
            Avatar(UserProfile(m.uid, m.name, null, m.photoUrl), 28.dp)
        }
    }
}
