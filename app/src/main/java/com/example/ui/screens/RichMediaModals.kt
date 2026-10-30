package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.model.DecryptedMessage
import com.example.ui.theme.SanctuaryGold
import com.example.util.AnimatedGifCodec
import com.example.util.AnimatedGifView
import com.example.util.MediaCompressionUtil
import com.example.util.VideoMetadataSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WhatsApp-style Before-Send HD Video Preview & Encryption Studio.
 */
@Composable
fun VideoSendPreviewDialog(
    videoUri: Uri,
    metadata: VideoMetadataSummary,
    initialCaption: String,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
    onSendVideo: (caption: String, videoBytes: ByteArray, thumbnailB64: String?, durationMs: Long, isHd: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isHd by remember { mutableStateOf(true) }
    var caption by remember { mutableStateOf(initialCaption) }
    var isPreparing by remember { mutableStateOf(false) }

    val sizeMbStr = remember(metadata.sizeBytes) {
        if (metadata.sizeBytes > 0L) String.format("%.1f MB", metadata.sizeBytes / (1024f * 1024f)) else "MP4 Stream"
    }
    val durationFormatted = remember(metadata.durationMs) {
        MediaCompressionUtil.formatDurationMillis(metadata.durationMs)
    }

    Dialog(
        onDismissRequest = { if (!isPreparing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B141A))
                .imePadding()
        ) {
            // Top Bar with Close + HD Toggle Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    enabled = !isPreparing,
                    modifier = Modifier.testTag("video_preview_close_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel video", tint = Color.White)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "$durationFormatted • $sizeMbStr",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        onClick = { isHd = !isHd },
                        color = if (isHd) Color(0xFF00A884) else Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("video_hd_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hd,
                                contentDescription = "Toggle HD Video Quality",
                                tint = if (isHd) Color(0xFF04261C) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isHd) "HD VIDEO" else "SD VIDEO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isHd) Color(0xFF04261C) else Color.White
                            )
                        }
                    }
                }
            }

            // E2EE Chunk Stream Notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = SanctuaryGold,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHd) {
                        "Full HD clarity • Encrypted in AES-256-GCM chunks for your Mate only"
                    } else {
                        "Standard video • End-to-end encrypted with AES-256-GCM"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF69F0AE)
                )
            }

            // Live Video Player Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoURI(videoUri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    }
                )
            }

            // Bottom Caption Input & Send FAB
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.editor_add_caption_hint),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    },
                    maxLines = 3,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF1F2C34),
                        unfocusedContainerColor = Color(0xFF1F2C34),
                        focusedBorderColor = Color(0xFF00A884),
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("video_caption_input")
                )

                Spacer(modifier = Modifier.width(10.dp))

                FloatingActionButton(
                    onClick = {
                        if (isPreparing) return@FloatingActionButton
                        isPreparing = true
                        scope.launch {
                            val bytesResult = withContext(Dispatchers.IO) {
                                MediaCompressionUtil.readUriBytesLimited(context, videoUri)
                            }
                            isPreparing = false
                            bytesResult.onSuccess { bytes ->
                                onSendVideo(caption, bytes, metadata.thumbnailBase64, metadata.durationMs, isHd)
                            }.onFailure { err ->
                                onError(err.localizedMessage ?: "Selected video could not be read.")
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = Color(0xFF00A884),
                    contentColor = Color(0xFF04261C),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("video_send_button")
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(
                            color = Color(0xFF04261C),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Encrypted HD Video"
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated GIF Picker & Live GIF89a Studio Bottom Sheet.
 * Supports picking `.gif` files from device storage OR sending instant multi-frame animated GIFs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerAndComposerSheet(
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
    onSendGifBytes: (caption: String, gifBytes: ByteArray) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var customGifCaption by remember { mutableStateOf("") }
    var isBuildingCustom by remember { mutableStateOf(false) }

    // Pre-generate the 8 live animated GIF89a reaction byte arrays off the main thread
    var presetGifMap by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) }
    LaunchedEffect(Unit) {
        val built = withContext(Dispatchers.Default) {
            AnimatedGifCodec.PRESET_REACTIONS.associate { p ->
                p.id to AnimatedGifCodec.generateAnimatedReactionGifBytes(
                    emoji = p.emoji,
                    caption = p.title,
                    startColorArgb = p.primaryColor,
                    endColorArgb = p.secondaryColor
                )
            }
        }
        presetGifMap = built
    }

    val deviceGifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    MediaCompressionUtil.readUriBytesLimited(context, uri)
                }
                res.onSuccess { bytes ->
                    onSendGifBytes("", bytes)
                    onDismiss()
                }.onFailure { err ->
                    onError(err.localizedMessage ?: "Unable to read GIF file.")
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.GifBox,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Encrypted Animated GIFs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { deviceGifLauncher.launch("image/gif") },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("pick_device_gif_button")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick .GIF")
                }
            }

            // Custom Animated GIF Text Maker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customGifCaption,
                    onValueChange = { customGifCaption = it.take(22) },
                    placeholder = { Text("Create custom animated GIF text…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_gif_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        if (isBuildingCustom) return@FloatingActionButton
                        val label = customGifCaption.trim().ifBlank { "My Mate ❤️" }
                        isBuildingCustom = true
                        scope.launch {
                            val bytes = withContext(Dispatchers.Default) {
                                AnimatedGifCodec.generateAnimatedReactionGifBytes(
                                    emoji = "✨",
                                    caption = label,
                                    startColorArgb = 0xFF075E54.toInt(),
                                    endColorArgb = 0xFF00A884.toInt()
                                )
                            }
                            isBuildingCustom = false
                            onSendGifBytes("", bytes)
                            onDismiss()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_custom_gif_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Custom Animated GIF")
                }
            }

            Text(
                text = "Tap an animated reaction to send instantly:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                items(AnimatedGifCodec.PRESET_REACTIONS, key = { it.id }) { preset ->
                    val bytes = presetGifMap[preset.id]
                    Card(
                        onClick = {
                            if (bytes != null) {
                                onSendGifBytes("", bytes)
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .testTag("preset_gif_${preset.id}")
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (bytes != null) {
                                AnimatedGifView(
                                    gifBytes = bytes,
                                    contentDescription = preset.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "GIF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-Screen Lightbox supporting:
 * 1. Pinch-to-zoom Full-Resolution HD Photos (resolving chunked 1600p HD bytes automatically)
 * 2. Full-Screen Looping Animated GIFs
 * 3. Full-Screen Encrypted HD MP4 Video Playback with transport controls
 */
@Composable
fun FullScreenMediaLightbox(
    message: DecryptedMessage,
    resolveMediaFile: suspend (File, DecryptedMessage) -> Result<File>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var resolvedFile by remember(message.id) { mutableStateOf<File?>(null) }
    var resolvedBytes by remember(message.id) { mutableStateOf<ByteArray?>(null) }
    var isLoadingChunks by remember(message.id) { mutableStateOf(true) }
    var loadError by remember(message.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(message.id) {
        isLoadingChunks = true
        val res = resolveMediaFile(context.cacheDir, message)
        res.onSuccess { file ->
            resolvedFile = file
            if (message.messageType != "video") {
                resolvedBytes = withContext(Dispatchers.IO) { file.readBytes() }
            }
        }.onFailure { err ->
            loadError = err.localizedMessage ?: "Failed to decrypt HD media."
        }
        isLoadingChunks = false
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when {
                isLoadingChunks && message.messageType == "video" -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF25D366))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Decrypting ${if (message.isHd) "HD " else ""}video stream…",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                loadError != null -> {
                    Text(
                        text = loadError!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                message.messageType == "video" && resolvedFile != null -> {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val mediaController = MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setVideoPath(resolvedFile!!.absolutePath)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = false
                                    start()
                                }
                            }
                        }
                    )
                }

                message.messageType == "gif" || MediaCompressionUtil.isAnimatedGifBytes(resolvedBytes) -> {
                    val gifData = resolvedBytes ?: MediaCompressionUtil.base64ToBytes(message.imageBase64)
                    if (gifData != null) {
                        AnimatedGifView(
                            gifBytes = gifData,
                            contentDescription = message.text,
                            cropCenter = false,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }

                else -> {
                    val hdBitmap = remember(resolvedBytes, message.imageBase64) {
                        if (resolvedBytes != null) {
                            BitmapFactory.decodeByteArray(resolvedBytes, 0, resolvedBytes!!.size)
                        } else {
                            MediaCompressionUtil.base64ToBitmap(message.imageBase64)
                        }
                    }
                    if (hdBitmap != null) {
                        Image(
                            bitmap = hdBitmap.asImageBitmap(),
                            contentDescription = message.text,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 4f)
                                        offset = if (scale > 1f) offset + pan else Offset.Zero
                                    }
                                }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                }
            }

            // Top Bar with Close + HD / GIF / E2EE Badges
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close media viewer",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLoadingChunks && message.messageType != "video") {
                        CircularProgressIndicator(
                            color = Color(0xFF25D366),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (message.isHd) {
                        Surface(
                            color = Color(0xFF00A884),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "HD QUALITY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF04261C),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (message.messageType == "gif") {
                        Surface(
                            color = SanctuaryGold,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ANIMATED GIF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1400),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val defaultPlaceholders = setOf("📷 Photo", "📷 HD Photo", "🎞️ GIF", "🎬 Video", "🎬 HD Video")
            if (message.text.isNotBlank() && message.text !in defaultPlaceholders) {
                Surface(
                    color = Color(0xAA000000),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }
    }
}
