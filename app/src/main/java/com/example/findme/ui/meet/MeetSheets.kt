package com.example.findme.ui.meet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.example.findme.ui.components.ActionPill
import com.example.findme.ui.components.CloseCircle
import com.example.findme.ui.components.SegmentedControl
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.profile.Avatar
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
val MeetColor = Color(0xFFFF7A00)

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
            YIcon(R.drawable.ms_group, null, tint = Color.White, size = 22.dp)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    meeting.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = yPalette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    meetingTimeText(meeting.timeMillis) ?: "Time not set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (meeting.timeMillis != null) MeetColor else yPalette.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            CloseCircle(onClick = { vm.closeMeeting() }, modifier = Modifier.padding(top = 2.dp))
        }

        ShareButton(
            sharing = vm.isSharing(meeting),
            onClick = { if (vm.isSharing(meeting)) vm.stopSharing() else actions.share(meeting) },
        )

        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ActionPill("Directions", R.drawable.ms_route, { actions.directions(meeting) }, primary = true) }
            item { ActionPill("Map", R.drawable.ms_location_on, { actions.showOnMap(meeting) }) }
            item { ActionPill("Invite", R.drawable.ms_person_add, { actions.invite(meeting) }) }
            if (owner) item { ActionPill(null, R.drawable.ms_edit, { vm.startEdit(meeting) }) }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = yPalette.divider)

        // Where
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(R.drawable.ms_location_on, null, tint = yPalette.textSecondary, size = 22.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(meeting.placeName, style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary)
                meeting.placeAddress?.takeIf { it != meeting.placeName }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
                }
            }
            Geo.distanceText(userLocation, Point(meeting.lat, meeting.lon))?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
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

        // People
        Text(
            "People · ${meeting.members.size}",
            style = MaterialTheme.typography.titleLarge,
            color = yPalette.textPrimary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        val place = Point(meeting.lat, meeting.lon)
        val live = vm.mapMeetingId == meeting.id
        meeting.members.forEach { member ->
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
            )
        }

        HorizontalDivider(Modifier.padding(top = 8.dp), color = yPalette.divider)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !vm.busy) { confirm = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(if (owner) R.drawable.ms_delete else R.drawable.ms_logout, null, tint = YColors.Red, size = 22.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                if (owner) "Delete meet-up" else "Leave meet-up",
                style = MaterialTheme.typography.bodyLarge,
                color = YColors.Red,
                modifier = Modifier.weight(1f),
            )
            if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), color = YColors.Red, strokeWidth = 2.dp)
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

/** Big green "Share my location" / red "Stop sharing" button of a meet-up. */
@Composable
private fun ShareButton(sharing: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (sharing) YColors.Red.copy(alpha = 0.12f) else YColors.Green,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).height(52.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YIcon(
                if (sharing) R.drawable.ms_close else R.drawable.ms_near_me,
                null,
                tint = if (sharing) YColors.Red else Color.White,
                size = 22.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (sharing) "Stop sharing my location" else "Share my location",
                style = MaterialTheme.typography.labelLarge,
                color = if (sharing) YColors.Red else Color.White,
            )
        }
    }
}

/** Member's state on the way: text and color. */
private data class MemberStatus(val text: String, val color: Color?)

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
    loc ?: return MemberStatus("Not sharing location", null)
    val toGo = Geo.formatDistance(trip?.remainingMeters ?: Geo.distanceMeters(Point(loc.lat, loc.lon), place))
    if (loc.arrived) return MemberStatus("Arrived", YColors.Green)
    val age = now - (loc.updatedMillis ?: now)
    if (age > STALE_MILLIS) return MemberStatus("Seen ${agoText(age)} · $toGo to go", null)
    val road = relation?.let { " · ${it.text()}" }.orEmpty()
    val eta = trip?.etaSeconds
    if (trip?.navigating != true || eta == null) return MemberStatus("On the way · $toGo to go$road", MeetColor)
    val arrival = now + (eta * 1000).toLong()
    val text = "${modeVerb(mode)} · ${Geo.durationText(eta)} · arrives ${clockOf(arrival)}"
    val late = lateMinutes(arrival, meetingTime)
    return when {
        late == null -> MemberStatus("$text$road", MeetColor)
        late > LATE_GRACE_MIN -> MemberStatus("$text · $late min late$road", YColors.Red)
        else -> MemberStatus("$text · on time$road", YColors.Green)
    }
}

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

@Composable
private fun MemberRow(member: MeetingMember, isOwner: Boolean, isMe: Boolean, status: MemberStatus?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(UserProfile(member.uid, member.name, null, member.photoUrl), 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isMe) "${member.name} (you)" else member.name,
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(if (isOwner) "Organizer" else null, status?.text).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = status?.color ?: yPalette.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
        containerColor = yPalette.sheet,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                if (existing == null) "New meet-up" else "Edit meet-up",
                style = MaterialTheme.typography.headlineSmall,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MeetColor),
                    contentAlignment = Alignment.Center,
                ) {
                    YIcon(R.drawable.ms_location_on, null, tint = Color.White, size = 22.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        placeName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    placeAddress?.takeIf { it != placeName }?.let {
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

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(MeetingRepository.MAX_TITLE) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Text(
                "When",
                style = MaterialTheme.typography.titleMedium,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            )
            SegmentedControl(
                items = days.map { if (it == Day.Other) shortDate(otherDate) else it.title },
                selected = days.indexOf(day).coerceAtLeast(0),
                onSelect = { day = days[it] },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (day != Day.Any) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickingTime = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    YIcon(R.drawable.ms_schedule, null, tint = yPalette.textSecondary, size = 22.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Time", style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        clockText(minutes / 60, minutes % 60),
                        style = MaterialTheme.typography.titleMedium,
                        color = yPalette.accent,
                    )
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { vm.save(title, chosenMillis()) },
                enabled = !vm.busy && title.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                color = yPalette.accent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (vm.busy) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                    } else {
                        Text(
                            if (existing == null) "Create" else "Save",
                            style = MaterialTheme.typography.labelLarge,
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

/** Pill at the top of the map while a meet-up is shown: "● Title · 2 on the way  ✕". */
@Composable
fun LiveMeetChip(vm: MeetViewModel, onOpen: (Meeting) -> Unit, modifier: Modifier = Modifier) {
    val meeting = vm.mapMeeting ?: return
    val me = vm.user?.uid
    val others = vm.locations.values.filter { it.uid != me }
    val moving = others.count { !it.arrived && !isStale(it, vm.now) }
    val arrived = others.count { it.arrived }
    val sharing = vm.isSharing(meeting)
    Surface(
        onClick = { onOpen(meeting) },
        shape = RoundedCornerShape(22.dp),
        color = yPalette.sheet,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (sharing) YColors.Green else MeetColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 160.dp)) {
                Text(
                    meeting.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = yPalette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildList {
                        if (sharing) add("You're visible")
                        if (moving > 0) add("$moving on the way")
                        if (arrived > 0) add("$arrived arrived")
                        if (isEmpty()) add("No one on the way yet")
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = yPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable { vm.hideMap() },
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_close, "Hide from map", tint = yPalette.iconSecondary, size = 18.dp)
            }
        }
    }
}

// =====================================================================
// Members during my navigation
// =====================================================================

/** Over the navigation panel: "Ali · 1.2 km ahead · 8 min" for members on their way. */
@Composable
fun MeetNavStrip(vm: MeetViewModel, modifier: Modifier = Modifier) {
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
    if (rows.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = yPalette.sheet,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            rows.take(3).forEach { (m, detail) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(UserProfile(m.uid, m.name, null, m.photoUrl), 26.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${m.name.substringBefore(' ')} · $detail",
                        style = MaterialTheme.typography.bodyMedium,
                        color = yPalette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
