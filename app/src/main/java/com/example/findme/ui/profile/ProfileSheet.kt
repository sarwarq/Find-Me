package com.example.findme.ui.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.auth.UserProfile
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette

/** Profile: Google sign-in, or the signed-in user's name, email and "Sign out". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(vm: AuthViewModel) {
    ModalBottomSheet(
        onDismissRequest = { vm.profileOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yPalette.sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val user = vm.user
            when {
                user != null -> SignedIn(vm, user)
                else -> SignedOut(vm)
            }
        }
    }
}

@Composable
private fun SignedOut(vm: AuthViewModel) {
    val context = LocalContext.current
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(yPalette.field),
        contentAlignment = Alignment.Center,
    ) {
        YIcon(R.drawable.ms_person, null, tint = Color(0xFFB9B9B9), size = 52.dp)
    }
    Spacer(Modifier.height(16.dp))
    Text("Sign in to FindMe", style = MaterialTheme.typography.titleLarge, color = yPalette.textPrimary)
    Spacer(Modifier.height(6.dp))
    Text(
        if (vm.available) {
            "Plan meet-ups with friends and see each other on the way"
        } else {
            "Sign-in is not set up yet: google-services.json is missing"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = yPalette.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 32.dp),
    )
    Spacer(Modifier.height(24.dp))
    Surface(
        onClick = { vm.signIn(context) },
        enabled = vm.available && !vm.signingIn,
        shape = RoundedCornerShape(14.dp),
        color = yPalette.field,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            if (vm.signingIn) {
                CircularProgressIndicator(Modifier.size(22.dp), color = yPalette.accent, strokeWidth = 2.5.dp)
            } else {
                YIcon(R.drawable.ic_google, null, tint = Color.Unspecified, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Continue with Google",
                style = MaterialTheme.typography.labelLarge,
                color = if (vm.available) yPalette.textPrimary else yPalette.textSecondary,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SignedIn(vm: AuthViewModel, user: UserProfile) {
    val context = LocalContext.current
    var renaming by rememberSaveable { mutableStateOf(false) }

    Avatar(user, 72.dp)
    Spacer(Modifier.height(12.dp))
    Text(
        user.name,
        style = MaterialTheme.typography.titleLarge,
        color = yPalette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    if (!user.email.isNullOrBlank()) {
        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
    }
    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = yPalette.divider)
    ProfileRow(R.drawable.ms_edit, "Edit name", "Friends see this name in meetings") { renaming = true }
    ProfileRow(R.drawable.ms_logout, "Sign out", tint = YColors.Red) {
        vm.signOut(context)
    }

    if (renaming) {
        RenameDialog(
            initial = user.name,
            onDismiss = { renaming = false },
            onSave = {
                vm.rename(it)
                renaming = false
            },
        )
    }
}

@Composable
private fun ProfileRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String? = null,
    tint: Color = yPalette.textPrimary,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(yPalette.field),
            contentAlignment = Alignment.Center,
        ) {
            YIcon(icon, null, tint = if (tint == yPalette.textPrimary) yPalette.iconSecondary else tint, size = 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = yPalette.textSecondary)
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val valid = text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = yPalette.sheet,
        title = { Text("Your name", color = yPalette.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(AuthViewModel.MAX_NAME) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = yPalette.accent,
                    cursorColor = yPalette.accent,
                    focusedTextColor = yPalette.textPrimary,
                    unfocusedTextColor = yPalette.textPrimary,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = valid) {
                Text("Save", color = if (valid) yPalette.accent else yPalette.textSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = yPalette.textSecondary) }
        },
    )
}
