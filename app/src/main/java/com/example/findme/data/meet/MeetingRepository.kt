package com.example.findme.data.meet

import android.content.Context
import android.util.Log
import com.example.findme.data.Geo
import com.example.findme.data.TravelMode
import com.yandex.mapkit.geometry.Point
import com.example.findme.data.auth.UserProfile
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class MeetingMember(val uid: String, val name: String, val photoUrl: String?)

/**
 * The route a member chose and is driving now (published when they tap "Go").
 * [encoded] is the raw polyline text, so an unchanged route is not decoded again.
 */
data class SharedRoute(
    val uid: String,
    val mode: TravelMode,
    val encoded: String,
    val durationSeconds: Double,
    val distanceMeters: Double,
)

/** A meet-up: one place and an optional time. Only its members can read it. */
data class Meeting(
    val id: String,
    val title: String,
    val ownerUid: String,
    val code: String,
    val placeName: String,
    val placeAddress: String?,
    val lat: Double,
    val lon: Double,
    /** Meeting time in epoch millis, or null when not set. */
    val timeMillis: Long?,
    /** Organizer first, then in the order people joined. */
    val members: List<MeetingMember>,
    val createdMillis: Long,
)

/** A member's last shared position in a meeting. */
data class LiveLocation(
    val uid: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    /** Direction of travel in degrees, null when standing. */
    val heading: Float?,
    /** Meters per second. */
    val speed: Float?,
    val arrived: Boolean,
    /** When the server got it. */
    val updatedMillis: Long?,
    /** From their own navigation, while they drive a shared route. */
    val etaSeconds: Double? = null,
    val remainingMeters: Double? = null,
)

/** What a new or edited meeting is about. */
data class MeetingDraft(
    val title: String,
    val placeName: String,
    val placeAddress: String?,
    val lat: Double,
    val lon: Double,
    val timeMillis: Long?,
)

sealed interface MeetResult<out T> {
    data class Ok<T>(val value: T) : MeetResult<T>
    data class Error(val message: String) : MeetResult<Nothing>
}

/**
 * Meetings in Firestore.
 *
 * - `meetings/{id}`: title, place, time, `members` (uids, used by the security rules and
 *   the "my meetings" query) and `memberInfo` (uid → name and photo, so members see each other
 *   without reading other users' profiles).
 * - `invites/{code}`: `meetingId` + `ownerUid`. Anyone signed in may read one invite by its
 *   code, never list them. Joining adds only yourself to the meeting.
 * - `meetings/{id}/locations/{uid}`: a member's live position while they share it.
 *   Only members can read; each member writes only their own.
 *
 * Works without `google-services.json`: [available] is false and nothing is called.
 */
class MeetingRepository(context: Context) {

    val available: Boolean = FirebaseApp.getApps(context.applicationContext).isNotEmpty()

    private val db: FirebaseFirestore? = if (available) FirebaseFirestore.getInstance() else null
    private val random = SecureRandom()

    /** Live list of the user's meetings, soonest first. Returns a function that stops listening. */
    fun observeMyMeetings(uid: String, onChange: (List<Meeting>) -> Unit): () -> Unit {
        val db = db ?: return {}
        val reg = db.collection(MEETINGS)
            .whereArrayContains("members", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "Meetings listener failed", error)
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { it.toMeeting() }
                onChange(
                    list.sortedWith(
                        compareBy<Meeting> { it.timeMillis ?: Long.MAX_VALUE }.thenByDescending { it.createdMillis },
                    ),
                )
            }
        return { reg.remove() }
    }

    /** Creates the meeting and its invite code together. Returns the new meeting id. */
    suspend fun create(user: UserProfile, draft: MeetingDraft): MeetResult<String> {
        val db = db ?: return NOT_SET_UP
        repeat(3) {
            val code = newCode()
            val meetingRef = db.collection(MEETINGS).document()
            val batch = db.batch()
            batch.set(
                meetingRef,
                draft.toFields() + mapOf(
                    "ownerUid" to user.uid,
                    "code" to code,
                    "members" to listOf(user.uid),
                    "memberInfo" to mapOf(user.uid to user.memberInfo()),
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                db.collection(INVITES).document(code),
                mapOf("meetingId" to meetingRef.id, "ownerUid" to user.uid),
            )
            try {
                batch.commit().await()
                return MeetResult.Ok(meetingRef.id)
            } catch (e: FirebaseFirestoreException) {
                // The code may be taken (the rules don't let us overwrite an invite): try another one.
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return e.toError("create the meet-up")
                Log.w(TAG, "Create failed, retrying with a new code", e)
            }
        }
        return MeetResult.Error("Could not create the meet-up. Check that the Firestore rules are published")
    }

    /** Owner changes title, place or time. */
    suspend fun update(meetingId: String, draft: MeetingDraft): MeetResult<Unit> {
        val db = db ?: return NOT_SET_UP
        return try {
            db.collection(MEETINGS).document(meetingId).update(draft.toFields()).await()
            MeetResult.Ok(Unit)
        } catch (e: Exception) {
            e.toError("save the changes")
        }
    }

    /** Finds the meeting by invite code and adds the user to it. Returns the meeting id. */
    suspend fun join(user: UserProfile, rawCode: String): MeetResult<String> {
        val db = db ?: return NOT_SET_UP
        val code = normalizeCode(rawCode)
        if (code.length != CODE_LENGTH) return MeetResult.Error("The code has $CODE_LENGTH letters and digits")
        val meetingId = try {
            db.collection(INVITES).document(code).get().await().getString("meetingId")
        } catch (e: Exception) {
            return e.toError("find the meet-up")
        } ?: return MeetResult.Error("No meet-up with this code")

        return try {
            db.collection(MEETINGS).document(meetingId).update(
                mapOf(
                    "members" to FieldValue.arrayUnion(user.uid),
                    "memberInfo.${user.uid}" to user.memberInfo(),
                ),
            ).await()
            MeetResult.Ok(meetingId)
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> MeetResult.Error("This meet-up was deleted")
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    MeetResult.Error("Could not join: the meet-up is full or was deleted")
                else -> e.toError("join")
            }
        }
    }

    /** Keeps the name and photo other members see in sync with the profile. Keeps the travel mode. */
    fun updateMyInfo(meetingId: String, user: UserProfile) {
        db?.collection(MEETINGS)?.document(meetingId)
            ?.update(
                mapOf(
                    "memberInfo.${user.uid}.name" to user.name,
                    "memberInfo.${user.uid}.photoUrl" to user.photoUrl,
                ),
            )
            ?.addOnFailureListener { Log.w(TAG, "updateMyInfo failed", it) }
    }

    suspend fun leave(meetingId: String, uid: String): MeetResult<Unit> {
        val db = db ?: return NOT_SET_UP
        return try {
            // While still a member: afterwards the rules no longer let us delete them.
            locations(db, meetingId).document(uid).delete().await()
            routes(db, meetingId).document(uid).delete().await()
            db.collection(MEETINGS).document(meetingId).update(
                mapOf(
                    "members" to FieldValue.arrayRemove(uid),
                    "memberInfo.$uid" to FieldValue.delete(),
                ),
            ).await()
            MeetResult.Ok(Unit)
        } catch (e: Exception) {
            e.toError("leave the meet-up")
        }
    }

    /** Owner only: removes the meeting and its invite code. */
    suspend fun delete(meeting: Meeting): MeetResult<Unit> {
        val db = db ?: return NOT_SET_UP
        return try {
            // Firestore does not delete subcollections with their parent.
            val shared = locations(db, meeting.id).get().await().documents +
                routes(db, meeting.id).get().await().documents
            val batch = db.batch()
            shared.forEach { batch.delete(it.reference) }
            batch.delete(db.collection(INVITES).document(meeting.code))
            batch.delete(db.collection(MEETINGS).document(meeting.id))
            batch.commit().await()
            MeetResult.Ok(Unit)
        } catch (e: Exception) {
            e.toError("delete the meet-up")
        }
    }

    // ---------- Live locations ----------

    /** Live positions of the meeting's members, by uid. Returns a function that stops listening. */
    fun observeLocations(meetingId: String, onChange: (Map<String, LiveLocation>) -> Unit): () -> Unit {
        val db = db ?: return {}
        val reg = locations(db, meetingId).addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Locations listener failed", error)
                return@addSnapshotListener
            }
            onChange(snap?.documents.orEmpty().mapNotNull { it.toLiveLocation() }.associateBy { it.uid })
        }
        return { reg.remove() }
    }

    /** Sends my position. Fire-and-forget: Firestore queues it while offline. */
    fun shareLocation(
        meetingId: String,
        uid: String,
        lat: Double,
        lon: Double,
        accuracy: Float?,
        heading: Float?,
        speed: Float?,
        arrived: Boolean,
        etaSeconds: Double? = null,
        remainingMeters: Double? = null,
    ) {
        val db = db ?: return
        locations(db, meetingId).document(uid).set(
            mapOf(
                "lat" to lat,
                "lon" to lon,
                "accuracy" to accuracy?.toDouble(),
                "heading" to heading?.toDouble(),
                "speed" to speed?.toDouble(),
                "arrived" to arrived,
                "etaSeconds" to etaSeconds,
                "remainingMeters" to remainingMeters,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).addOnFailureListener { Log.w(TAG, "shareLocation failed", it) }
    }

    /** Stops showing me on the others' maps. */
    fun removeLocation(meetingId: String, uid: String) {
        val db = db ?: return
        locations(db, meetingId).document(uid).delete()
            .addOnFailureListener { Log.w(TAG, "removeLocation failed", it) }
    }

    private fun locations(db: FirebaseFirestore, meetingId: String) =
        db.collection(MEETINGS).document(meetingId).collection(LOCATIONS)

    // ---------- Shared routes ----------

    /** Routes members are driving now, by uid. Returns a function that stops listening. */
    fun observeRoutes(meetingId: String, onChange: (Map<String, SharedRoute>) -> Unit): () -> Unit {
        val db = db ?: return {}
        val reg = routes(db, meetingId).addSnapshotListener { snap, error ->
            if (error != null) {
                Log.w(TAG, "Routes listener failed", error)
                return@addSnapshotListener
            }
            onChange(snap?.documents.orEmpty().mapNotNull { it.toSharedRoute() }.associateBy { it.uid })
        }
        return { reg.remove() }
    }

    /** "I'm driving this route": the others draw it as it is on my screen. */
    fun publishRoute(meetingId: String, uid: String, mode: TravelMode, points: List<Point>, durationSeconds: Double, distanceMeters: Double) {
        val db = db ?: return
        // Points closer than a few meters add size without changing the line.
        val thin = ArrayList<Point>(points.size)
        for (p in points) {
            val last = thin.lastOrNull()
            if (last == null || Geo.distanceMeters(last, p) >= 3.0) thin.add(p)
        }
        points.lastOrNull()?.let { if (thin.lastOrNull() !== it) thin.add(it) }
        routes(db, meetingId).document(uid).set(
            mapOf(
                "mode" to mode.name.lowercase(),
                "polyline" to PolylineCodec.encode(thin),
                "durationSeconds" to durationSeconds,
                "distanceMeters" to distanceMeters,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).addOnFailureListener { Log.w(TAG, "publishRoute failed", it) }
    }

    fun removeRoute(meetingId: String, uid: String) {
        val db = db ?: return
        routes(db, meetingId).document(uid).delete()
            .addOnFailureListener { Log.w(TAG, "removeRoute failed", it) }
    }

    private fun routes(db: FirebaseFirestore, meetingId: String) =
        db.collection(MEETINGS).document(meetingId).collection(ROUTES)

    private fun DocumentSnapshot.toSharedRoute(): SharedRoute? {
        val encoded = getString("polyline") ?: return null
        val mode = TravelMode.entries.firstOrNull { it.name.equals(getString("mode"), ignoreCase = true) } ?: TravelMode.Car
        return SharedRoute(
            uid = id,
            mode = mode,
            encoded = encoded,
            durationSeconds = getDouble("durationSeconds") ?: 0.0,
            distanceMeters = getDouble("distanceMeters") ?: 0.0,
        )
    }

    private fun DocumentSnapshot.toLiveLocation(): LiveLocation? {
        val lat = getDouble("lat") ?: return null
        val lon = getDouble("lon") ?: return null
        return LiveLocation(
            uid = id,
            lat = lat,
            lon = lon,
            accuracy = getDouble("accuracy")?.toFloat(),
            heading = getDouble("heading")?.toFloat(),
            speed = getDouble("speed")?.toFloat(),
            arrived = getBoolean("arrived") == true,
            etaSeconds = getDouble("etaSeconds"),
            remainingMeters = getDouble("remainingMeters"),
            // A write still on its way gets the phone's estimate instead of null.
            updatedMillis = getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
        )
    }

    private fun newCode(): String = buildString {
        repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
    }

    private fun MeetingDraft.toFields(): Map<String, Any?> = mapOf(
        "title" to title.trim().take(MAX_TITLE),
        "placeName" to placeName.take(MAX_TEXT),
        "placeAddress" to placeAddress?.take(MAX_TEXT),
        "lat" to lat,
        "lon" to lon,
        "time" to timeMillis?.let { Timestamp(Date(it)) },
    )

    private fun UserProfile.memberInfo() = mapOf("name" to name, "photoUrl" to photoUrl)

    private fun DocumentSnapshot.toMeeting(): Meeting? {
        val ownerUid = getString("ownerUid") ?: return null
        val lat = getDouble("lat") ?: return null
        val lon = getDouble("lon") ?: return null
        val uids = (get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val info = get("memberInfo") as? Map<*, *> ?: emptyMap<String, Any>()
        val members = uids.map { uid ->
            val m = info[uid] as? Map<*, *>
            MeetingMember(
                uid = uid,
                name = (m?.get("name") as? String)?.takeIf { it.isNotBlank() } ?: "Member",
                photoUrl = m?.get("photoUrl") as? String,
            )
        }.sortedBy { if (it.uid == ownerUid) 0 else 1 }
        return Meeting(
            id = id,
            title = getString("title").orEmpty(),
            ownerUid = ownerUid,
            code = getString("code").orEmpty(),
            placeName = getString("placeName").orEmpty(),
            placeAddress = getString("placeAddress"),
            lat = lat,
            lon = lon,
            timeMillis = getTimestamp("time")?.toDate()?.time,
            members = members,
            // The server timestamp is still pending right after creating: treat it as "now".
            createdMillis = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
        )
    }

    private fun Exception.toError(action: String): MeetResult.Error {
        Log.w(TAG, "Failed to $action", this)
        val reason = when ((this as? FirebaseFirestoreException)?.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Check the internet connection"
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Access denied"
            else -> "Try again"
        }
        return MeetResult.Error("Could not $action. $reason")
    }

    companion object {
        private const val TAG = "MeetingRepository"
        private const val MEETINGS = "meetings"
        private const val INVITES = "invites"
        private const val LOCATIONS = "locations"
        private const val ROUTES = "routes"
        const val CODE_LENGTH = 6
        const val MAX_TITLE = 60
        private const val MAX_TEXT = 200
        private val NOT_SET_UP = MeetResult.Error("Meet-ups are not set up yet")

        /** No 0/O and 1/I, so a code read aloud or typed by hand is not mistaken. */
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** "abc 123", "ABC-123" → "ABC123"; also accepts a whole `findme://join/ABC123` link. */
        fun normalizeCode(raw: String): String =
            raw.substringAfterLast('/').uppercase().filter { it in CODE_ALPHABET }.take(CODE_LENGTH)
    }
}

/** Waits for a Play services task without pulling in kotlinx-coroutines-play-services. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}
