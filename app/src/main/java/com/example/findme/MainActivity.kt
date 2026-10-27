package com.example.findme

import android.graphics.Color
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.findme.location.LocationShareService
import com.example.findme.update.UpdateAvailableDialog
import com.example.findme.update.UpdateGate
import com.example.findme.update.UpdateRequiredScreen
import com.example.findme.update.UpdateState
import com.example.findme.ui.MapScreen
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.meet.MeetViewModel
import com.example.findme.ui.profile.AuthViewModel
import com.example.findme.ui.theme.FindMeTheme
import com.yandex.mapkit.MapKitFactory

class MainActivity : ComponentActivity() {

    private val vm: MapViewModel by viewModels { container.viewModelFactory }
    private val authVm: AuthViewModel by viewModels { container.viewModelFactory }
    private val meetVm: MeetViewModel by viewModels { container.viewModelFactory }
    private lateinit var updates: UpdateGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateGate(this)
        // Opened by an invite link; skip it when the activity is only being recreated.
        if (savedInstanceState == null) handleInvite(intent)
        setContent {
            val dark = vm.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.WHITE)
                } else {
                    SystemBarStyle.light(Color.WHITE, Color.WHITE)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            FindMeTheme(darkTheme = dark) {
                when (val update = updates.state) {
                    is UpdateState.Required -> {
                        // An unsupported version must not keep sending the location either.
                        LaunchedEffect(Unit) { LocationShareService.stop(this@MainActivity) }
                        UpdateRequiredScreen(update.message, onUpdate = updates::update)
                    }
                    else -> {
                        MapScreen(vm, authVm, meetVm, darkMap = dark)
                        if (update is UpdateState.Optional) {
                            UpdateAvailableDialog(onUpdate = updates::update, onLater = updates::later)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updates.onResume()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInvite(intent)
    }

    /** `findme://join/ABC123` → the "Join a meet-up" dialog with the code filled in. */
    private fun handleInvite(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW || uri.scheme != "findme") return
        // Debug builds only, test drive along the current route with a fake location:
        // adb shell am start -n bsb.findme.app/com.example.findme.MainActivity
        //   -a android.intent.action.VIEW -d "findme://simulate?speed=15&heading=0"
        // shift=80 drives 80 m east of the route, to try rebuilding the route.
        if (uri.host == "simulate" && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val speed = uri.getQueryParameter("speed")?.toDoubleOrNull() ?: 15.0
            vm.simulateDrive(
                speed,
                gpsHeading = uri.getQueryParameter("heading") != "0",
                shiftM = uri.getQueryParameter("shift")?.toDoubleOrNull() ?: 0.0,
            )
            return
        }
        if (uri.host != "join") return
        val code = uri.lastPathSegment ?: return
        meetVm.openJoin(code)
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}
