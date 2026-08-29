package com.personalstrava.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

/**
 * The "proper profile" screen — display name + avatar, both backed by the
 * `profiles` row that's already there for every signed-in user (see
 * ProfileViewModel). Reached from a small avatar/person icon on Home
 * (spec didn't call for a whole account-settings surface, just a place to
 * put a name and a photo to it).
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::uploadAvatar) }

    LaunchedEffect(state.saved) {
        // No-op hook point: nothing to navigate on save today (this is an
        // in-place editor, not a form you "submit and leave") — kept as a
        // LaunchedEffect so a future "toast on save" is a one-line add.
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Profile",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(Modifier.height(28.dp))

        Box(modifier = Modifier.size(96.dp)) {
            if (state.avatarUrl != null) {
                AsyncImage(
                    model = state.avatarUrl,
                    contentDescription = "Your avatar",
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(48.dp))
                }
            }
            if (state.uploadingAvatar) {
                Box(
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White)
                }
            }
            IconButton(
                onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color.Black, CircleShape),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        if (state.loading) {
            CircularProgressIndicator()
        } else {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::updateDisplayNameDraft,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.saved) "Saved" else "Save")
            }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = Color(0xFFB3261E), fontSize = 12.sp)
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = {
            viewModel.signOut()
            onSignedOut()
        }) {
            Text("Sign out")
        }
    }
}
