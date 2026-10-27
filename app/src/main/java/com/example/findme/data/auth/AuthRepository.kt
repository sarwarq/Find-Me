package com.example.findme.data.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Signed-in user as the app shows it. [name] is what friends will see in meetings. */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String?,
    val photoUrl: String?,
)

/** How sign-in ended. [Cancelled] is silent; [Error] is shown to the user. */
sealed interface SignInResult {
    data object Success : SignInResult
    data object Cancelled : SignInResult
    data class Error(val message: String) : SignInResult
}

/**
 * Google sign-in through Credential Manager + Firebase Auth, and the user's profile
 * document `users/{uid}` in Firestore (only `name` and `photoUrl`, no email: other
 * meeting members will read it later).
 *
 * Works without `google-services.json`: [available] is false and nothing is called.
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext

    /** Firebase is set up: `google-services.json` was present at build time. */
    val available: Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    private val auth: FirebaseAuth? = if (available) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = if (available) FirebaseFirestore.getInstance() else null

    /** OAuth "Web client" ID generated from `google-services.json` once Google sign-in is enabled. */
    private val webClientId: String? = run {
        @Suppress("DiscouragedApi")
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        if (id == 0) null else appContext.getString(id)
    }

    /**
     * Calls [onChange] with the current user now and on every change (sign-in, sign-out,
     * name edits). Returns a function that stops listening.
     */
    fun observeUser(onChange: (UserProfile?) -> Unit): () -> Unit {
        val auth = auth ?: run { onChange(null); return {} }
        var docListener: ListenerRegistration? = null
        val authListener = FirebaseAuth.AuthStateListener { fa ->
            docListener?.remove()
            docListener = null
            val user = fa.currentUser
            if (user == null) {
                onChange(null)
                return@AuthStateListener
            }
            val base = UserProfile(
                uid = user.uid,
                name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore('@') ?: "Me",
                email = user.email,
                photoUrl = user.photoUrl?.toString(),
            )
            onChange(base)
            docListener = db?.collection(USERS)?.document(user.uid)?.addSnapshotListener { snap, error ->
                // No database yet or no network: keep the name from the Google account.
                if (error != null) {
                    Log.w(TAG, "Profile listener failed", error)
                    return@addSnapshotListener
                }
                val name = snap?.getString("name")?.takeIf { it.isNotBlank() } ?: return@addSnapshotListener
                onChange(base.copy(name = name))
            }
        }
        auth.addAuthStateListener(authListener)
        return {
            auth.removeAuthStateListener(authListener)
            docListener?.remove()
        }
    }

    /** Shows the Google account picker and signs in to Firebase. [activity] must be an Activity. */
    suspend fun signInWithGoogle(activity: Context): SignInResult {
        val auth = auth ?: return SignInResult.Error("Sign-in is not set up yet")
        val clientId = webClientId
            ?: return SignInResult.Error("Enable Google sign-in in Firebase and update google-services.json")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (_: GetCredentialCancellationException) {
            return SignInResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google account", e)
            return SignInResult.Error("Add a Google account to this phone first")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager failed", e)
            return SignInResult.Error("Google sign-in failed")
        }

        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return SignInResult.Error("Google sign-in failed")
        }

        return try {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user
                ?: return SignInResult.Error("Google sign-in failed")
            createProfileIfMissing(user.uid, user.displayName, user.photoUrl?.toString())
            SignInResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-in failed", e)
            SignInResult.Error("Could not sign in. Check the internet connection")
        }
    }

    /** Changes the name friends see. Saved offline too; Firestore syncs it later. */
    fun updateName(name: String) {
        val uid = auth?.currentUser?.uid ?: return
        db?.collection(USERS)?.document(uid)?.set(
            mapOf("name" to name.trim(), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    suspend fun signOut(activity: Context) {
        auth?.signOut()
        // Forget the chosen account so the picker shows again next time.
        try {
            CredentialManager.create(activity).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "clearCredentialState failed", e)
        }
    }

    /** First sign-in: copy the Google name and photo. Later sign-ins keep a name the user edited. */
    private suspend fun createProfileIfMissing(uid: String, googleName: String?, photoUrl: String?) {
        val doc = db?.collection(USERS)?.document(uid) ?: return
        val fields = mutableMapOf<String, Any?>(
            "photoUrl" to photoUrl,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            val snap = doc.get().await()
            if (!snap.exists()) fields["createdAt"] = FieldValue.serverTimestamp()
            if (snap.getString("name").isNullOrBlank() && !googleName.isNullOrBlank()) fields["name"] = googleName
        } catch (e: Exception) {
            // Offline or no database yet: the name stays as in the Google account.
            Log.w(TAG, "Could not read profile", e)
            return
        }
        doc.set(fields, SetOptions.merge())
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val USERS = "users"
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
