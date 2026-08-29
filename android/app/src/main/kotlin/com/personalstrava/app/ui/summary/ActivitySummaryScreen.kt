package com.personalstrava.app.ui.summary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.personalstrava.app.data.local.entity.PhotoEntity
import com.personalstrava.app.share.ShareCard
import com.personalstrava.app.share.ShareCardRenderer
import kotlinx.coroutines.launch

/**
 * Shown immediately after RecordingViewModel.stop() finalizes a ride (see
 * PersonalStravaNavHost) — title it, add a note and a couple of photos
 * while it's fresh, preview the shareable card, then Done. None of this
 * blocks the ride from already being saved: everything here is editing an
 * activity that's already a real Room row with syncStatus = pending_sync.
 */
@Composable
fun ActivitySummaryScreen(
    activityId: String,
    onDone: () -> Unit,
    viewModel: ActivitySummaryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    LaunchedEffect(activityId) { viewModel.load(activityId) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris -> viewModel.addPhotos(uris) }

    val activity = state.activity

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(text = "Nice work", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        if (activity != null) {
            item {
                OutlinedTextField(
                    value = state.titleDraft,
                    onValueChange = viewModel::updateTitleDraft,
                    label = { Text("Title") },
                    placeholder = { Text(activity.activityType.replaceFirstChar { it.uppercase() }) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.notesDraft,
                    onValueChange = viewModel::updateNotesDraft,
                    label = { Text("Notes") },
                    placeholder = { Text("How'd it go? (just for you — never shared)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    Text(text = "Photos", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.photos, key = { it.id }) { photo -> PhotoThumb(photo, onDelete = { viewModel.deletePhoto(photo) }) }
                        item {
                            AddPhotoButton(onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                    }
                }
            }

            item {
                Column {
                    Text(text = "Share card preview", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            },
                    ) {
                        ShareCard(activity)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        scope.launch { ShareCardRenderer.shareImage(context, graphicsLayer) }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = {
                        viewModel.save()
                        onDone()
                    }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(photo: PhotoEntity, onDelete: () -> Unit) {
    Box(modifier = Modifier.size(84.dp)) {
        AsyncImage(
            model = photo.localUri,
            contentDescription = null,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun AddPhotoButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add photos")
    }
}
