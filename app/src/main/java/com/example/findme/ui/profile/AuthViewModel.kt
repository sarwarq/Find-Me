package com.example.findme.ui.profile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.auth.AuthRepository
import com.example.findme.data.auth.SignInResult
import com.example.findme.data.auth.UserProfile
import kotlinx.coroutines.launch

/** Sign-in state for the profile button and the profile sheet. */
class AuthViewModel(private val repo: AuthRepository) : ViewModel() {

    /** False until `google-services.json` is added to the project. */
    val available: Boolean get() = repo.available

    var user by mutableStateOf<UserProfile?>(null); private set
    var signingIn by mutableStateOf(false); private set
    var profileOpen by mutableStateOf(false)
    /** The settings sheet, opened from the profile. */
    var settingsOpen by mutableStateOf(false)

    /** Errors for the toast; the screen clears it after showing. */
    var error by mutableStateOf<String?>(null)

    private val stopObserving = repo.observeUser { user = it }

    /** [activity] is needed by Credential Manager to show the account picker. */
    fun signIn(activity: Context) {
        if (signingIn) return
        signingIn = true
        viewModelScope.launch {
            when (val result = repo.signInWithGoogle(activity)) {
                is SignInResult.Error -> error = result.message
                SignInResult.Cancelled, SignInResult.Success -> Unit
            }
            signingIn = false
        }
    }

    fun signOut(activity: Context) {
        viewModelScope.launch { repo.signOut(activity) }
    }

    fun rename(name: String) {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return
        user = user?.copy(name = clean)
        repo.updateName(clean)
    }

    override fun onCleared() {
        stopObserving()
    }

    companion object {
        const val MAX_NAME = 40
    }
}
