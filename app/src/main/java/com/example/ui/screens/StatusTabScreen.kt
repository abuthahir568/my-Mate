package com.example.ui.screens

import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.DecryptedStatus
import com.example.data.model.Vault
import com.example.ui.theme.WhatsAppEmeraldAccent
import com.example.util.MediaCompressionUtil
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private val STATUS_BG_SWATCHES = listOf(
    "#075E54", "#128C7E", "#1E2A38", "#6A1B9A", "#AD1457", "#C62828", "#2E7D32"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTabScreen(
    vault: Vault,
    currentUserId: String,
    currentUserName: String,
    statuses: List<DecryptedStatus>,
    onPostStatus: (mediaType: String, caption: String, durationSec: Int, bgHex: String, imageBase64: String?) -> Unit,
    onOpenStories: (List<DecryptedStatus>) -> Unit,
    onDeleteStatus: (String) -> Unit
) {
    val context = LocalContext.current
    var showComposerSheet by rememberSaveable { mutableStateOf(false) }
    var draftCaption by rememberSaveable { mutableStateOf("") }
    var draftDuration by rememberSaveable { mutableFloatStateOf(15f) }
    var draftBgHex by rememberSaveable { mutableStateOf(STATUS_BG_SWATCHES.first()) }
    var draftMediaType by rememberSaveable { mutableStateOf("text_card") }
    var draftImageBase64 by remember { mutableStateOf<String?>(null) }
    var mediaClipNotice by remember { mutableStateOf<String?>(null) }

    val myStories = remember(statuses, currentUserId) {
        statuses.filter { it.authorUid == currentUserId }
    }
    val partnerStories = remember(statuses, currentUserId) {
        statuses.filter { it.authorUid != currentUserId }
    }

    // Zero-permission Photo & Video Picker (extracts poster frame + enforces strict <=30s rule!)
    val visualPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 15_000L
                    val rawSeconds = (durationMs / 1000L).toInt().coerceAtLeast(1)
                    val frameBmp = retriever.getFrameAtTime(0)
                    if (frameBmp != null) {
                        val out = ByteArrayOutputStream()
                        frameBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 68, out)
                        draftImageBase64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    }
                    draftMediaType = "video_clip"
                    if (rawSeconds > 30) {
                        draftDuration = 30f
                        mediaClipNotice = "Video exceeded 30s — automatically trimmed to strict 30s maximum."
                    } else {
                        draftDuration = rawSeconds.toFloat()
                        mediaClipNotice = "Encrypted video clip ready (${rawSeconds}s • 24h auto-delete)."
                    }
                    showComposerSheet = true
                } catch (_: Exception) {
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            } else {
                val base64 = MediaCompressionUtil.uriToCompressedBase64(context, uri)
                if (!base64.isNullOrBlank()) {
                    draftImageBase64 = base64
                    draftMediaType = "photo"
                    draftDuration = 15f
                    mediaClipNotice = "Encrypted photo selected (max 30s • 24h auto-delete)."
                    showComposerSheet = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Notice Banner: 30s Max + 24h Auto-Delete
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Private E2EE Stories • Strictly 30s max duration • Auto-deletes after 24 hours.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // My Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myStories.isNotEmpty()) {
                                onOpenStories(myStories)
                            } else {
                                draftMediaType = "text_card"
                                draftImageBase64 = null
                                mediaClipNotice = null
                                showComposerSheet = true
                            }
                        }
                        .testTag("my_status_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(58.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (myStories.isNotEmpty()) {
                                            Modifier.border(2.5.dp, WhatsAppEmeraldAccent, CircleShape)
                                        } else Modifier
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = currentUserName.take(1).uppercase().ifBlank { "M" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd)
                                    .clip(CircleShape)
                                    .background(WhatsAppEmeraldAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Add Status",
                                    tint = Color(0xFF04261C),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "My Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (myStories.isEmpty()) {
                                    "Tap to share an encrypted 24h update"
                                } else {
                                    "${myStories.size} active story • Tap to view"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Partner's Updates Section
            item {
                Text(
                    text = "MATE'S UPDATES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }

            if (partnerStories.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "No active 24h stories from ${vault.getPartnerName(currentUserId)} right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                item {
                    val hasUnviewed = partnerStories.any { currentUserId !in it.viewedByUids }
                    Card(
                        onClick = { onOpenStories(partnerStories) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("partner_status_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .border(
                                        width = 3.dp,
                                        color = if (hasUnviewed) WhatsAppEmeraldAccent else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = vault.getPartnerName(currentUserId).take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vault.getPartnerName(currentUserId),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val hoursLeft = ((partnerStories.first().expiresAtMillis - System.currentTimeMillis()) / 3_600_000L)
                                    .coerceIn(1L, 24L)
                                Text(
                                    text = "${partnerStories.size} update(s) • Expires in ${hoursLeft}h",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Manage My Active Stories
            if (myStories.isNotEmpty()) {
                item {
                    Text(
                        text = "MANAGE MY ACTIVE STORIES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(myStories, key = { it.id }) { story ->
                    val hoursLeft = ((story.expiresAtMillis - System.currentTimeMillis()) / 3_600_000L)
                        .coerceIn(1L, 24L)
                    val mateViewed = story.viewedByUids.any { it != currentUserId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = story.caption,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.RemoveRedEye,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (mateViewed) WhatsAppEmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${if (mateViewed) "Viewed by Mate" else "Not viewed yet"} • ${story.durationSeconds}s • ${hoursLeft}h left",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteStatus(story.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete status",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dual WhatsApp Status FABs: Text Card + Photo/Video Picker
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    draftMediaType = "text_card"
                    draftImageBase64 = null
                    mediaClipNotice = null
                    showComposerSheet = true
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("create_text_status_fab")
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Write Text Status")
            }

            FloatingActionButton(
                onClick = {
                    visualPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_media_status_fab")
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Upload Photo or 30s Video Status")
            }
        }
    }

    if (showComposerSheet) {
        ModalBottomSheet(onDismissRequest = { showComposerSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Post Encrypted 24h Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = mediaClipNotice ?: "Strictly limited to 30 seconds max duration & auto-deleted in 24 hours.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (draftImageBase64 != null) {
                    val bmp = remember(draftImageBase64) {
                        MediaCompressionUtil.base64ToBitmap(draftImageBase64)
                    }
                    if (bmp != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Selected status preview",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                            if (draftMediaType == "video_clip") {
                                Surface(
                                    color = Color(0xBB000000),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Videocam,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "≤30s Clip",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                } else {
                    // Color Swatch Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        STATUS_BG_SWATCHES.forEach { hex ->
                            val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                .getOrDefault(Color(0xFF075E54))
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(parsed)
                                    .border(
                                        width = if (draftBgHex == hex) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { draftBgHex = hex }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = draftCaption,
                    onValueChange = { draftCaption = it },
                    label = { Text("Status caption or thought…") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_caption_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Story Duration (Strict 30s Cap)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${draftDuration.roundToInt()} sec",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = draftDuration,
                    onValueChange = { draftDuration = it.coerceIn(5f, 30f) },
                    valueRange = 5f..30f,
                    steps = 4
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        onPostStatus(
                            draftMediaType,
                            draftCaption.ifBlank { "✨ Moments with my Mate" },
                            draftDuration.roundToInt().coerceIn(1, 30),
                            draftBgHex,
                            draftImageBase64
                        )
                        draftCaption = ""
                        draftImageBase64 = null
                        showComposerSheet = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("publish_status_button")
                ) {
                    Text("Encrypt & Publish 24h Story")
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun FullScreenStoryViewerDialog(
    stories: List<DecryptedStatus>,
    onDismiss: () -> Unit
) {
    if (stories.isEmpty()) {
        onDismiss()
        return
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val currentStory = stories[currentIndex.coerceIn(0, stories.lastIndex)]
    val bgColor = remember(currentStory.backgroundHex) {
        runCatching { Color(android.graphics.Color.parseColor(currentStory.backgroundHex)) }
            .getOrDefault(Color(0xFF075E54))
    }

    LaunchedEffect(currentIndex, stories.size) {
        progress = 0f
        val durationMs = (currentStory.durationSeconds.coerceIn(1, 30) * 1000L)
        val tickMs = 50L
        val steps = (durationMs / tickMs).coerceAtLeast(1L)
        for (step in 1..steps) {
            delay(tickMs)
            progress = step.toFloat() / steps.toFloat()
        }
        if (currentIndex < stories.lastIndex) {
            currentIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .clickable {
                    if (currentIndex < stories.lastIndex) currentIndex++ else onDismiss()
                }
        ) {
            if (!currentStory.imageBase64.isNullOrBlank()) {
                val bmp = remember(currentStory.id, currentStory.imageBase64) {
                    MediaCompressionUtil.base64ToBitmap(currentStory.imageBase64)
                }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = currentStory.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentStory.caption,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Segmented Story Progress Bars + Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stories.forEachIndexed { idx, _ ->
                        val segProgress = when {
                            idx < currentIndex -> 1f
                            idx == currentIndex -> progress
                            else -> 0f
                        }
                        LinearProgressIndicator(
                            progress = { segProgress },
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = currentStory.authorName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        val hoursRemaining = ((currentStory.expiresAtMillis - System.currentTimeMillis()) / 3_600_000L)
                            .coerceIn(1L, 24L)
                        Text(
                            text = "${currentStory.durationSeconds}s max • Auto-deletes in ${hoursRemaining}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close story",
                            tint = Color.White
                        )
                    }
                }
            }

            if (!currentStory.imageBase64.isNullOrBlank() && currentStory.caption.isNotBlank()) {
                Surface(
                    color = Color(0xAA000000),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = currentStory.caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }
    }
}
