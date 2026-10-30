package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.DecryptedMessage
import com.example.data.model.Vault
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalMateColors
import com.example.ui.theme.SanctuaryGold
import com.example.util.AnimatedGifView
import com.example.util.EditedImagePayload
import com.example.util.MediaCompressionUtil
import com.example.util.VideoMetadataSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val QUICK_EMOJIS = listOf(
    "❤️", "😘", "🥰", "😂", "🥹", "🔥", "✨", "🙌",
    "🤗", "😍", "🙏", "🎉", "🌙", "☕", "🌹", "🔒"
)

@Composable
fun ChatsTabContent(
    vault: Vault,
    currentUserId: String,
    secretCode: String,
    safetyFingerprint: String,
    lastDecryptedPreview: String?,
    unreadCount: Int,
    sharedPhotoCount: Int,
    onOpenChat: () -> Unit,
    onOpenMediaGallery: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val mateColors = LocalMateColors.current
    val partnerName = vault.getPartnerName(currentUserId)
    val partnerPhoto = vault.getPartnerPhoto(currentUserId)
    val isPartnerTyping = vault.isPartnerTyping(currentUserId)
    val isPartnerOnline = vault.isPartnerOnline(currentUserId)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. E2EE Security & Strict 2-User Lock Status Banner
            item {
                Surface(
                    color = mateColors.e2eeBannerBg,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Encrypted",
                            tint = SanctuaryGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (vault.isLockedTwoUsers()) {
                                    "Vault Permanently Locked (2 / 2 Mates Paired)"
                                } else {
                                    "Waiting for Mate #2 • Share Code: $secretCode"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = mateColors.e2eeBannerText
                            )
                            Text(
                                text = "Safety Number: $safetyFingerprint • Zero 3rd-party access",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = mateColors.e2eeBannerText.copy(alpha = 0.85f)
                            )
                        }
                        if (!vault.isLockedTwoUsers()) {
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(secretCode)) },
                                modifier = Modifier.testTag("copy_waiting_secret_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy pairing code",
                                    tint = mateColors.e2eeBannerText
                                )
                            }
                        }
                    }
                }
            }

            // 2. Classic WhatsApp Single-Partner Chat Row
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat() }
                        .testTag("mate_chat_row_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MateAvatar(
                            name = partnerName,
                            photoUrl = partnerPhoto,
                            isOnline = isPartnerOnline,
                            sizeDp = 58
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = partnerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val timeFormatted = vault.updatedAt?.toDate()?.let {
                                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                } ?: ""
                                Text(
                                    text = timeFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (unreadCount > 0) mateColors.unreadBadge else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        isPartnerTyping -> "typing…"
                                        !lastDecryptedPreview.isNullOrBlank() -> lastDecryptedPreview
                                        vault.isLockedTwoUsers() -> "Tap to send an end-to-end encrypted message ❤️"
                                        else -> "Share code $secretCode with your mate to begin"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isPartnerTyping) mateColors.unreadBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isPartnerTyping || unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(mateColors.unreadBadge),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = unreadCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF04261C),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Quick Encrypted Actions & Shared Photo Vault Card
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        onClick = onOpenMediaGallery,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("shared_media_gallery_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = "Shared Photos",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Media Gallery",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "$sharedPhotoCount HD photos, GIFs & videos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Card(
                        onClick = onStartVideoCall,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_video_call_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Videocam,
                                contentDescription = "Video Call",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "WebRTC Video",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "SRTP Encrypted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. Strict 2-User Security Verification Footer
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Adding contacts or 3rd-party users is permanently disabled.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenChat,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("open_chat_fab")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Open chat with your Mate"
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    vault: Vault,
    currentUserId: String,
    messages: List<DecryptedMessage>,
    replyingTo: DecryptedMessage?,
    mediaUploadProgress: Float?,
    mediaUploadLabel: String?,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSendMessage: (String, String?) -> Unit,
    onSendEditedImage: (String, EditedImagePayload) -> Unit,
    onSendGif: (String, ByteArray) -> Unit,
    onSendVideo: (String, ByteArray, String?, Long, Boolean) -> Unit,
    onMediaError: (String) -> Unit,
    onSetReplyTarget: (DecryptedMessage?) -> Unit,
    onDeleteOwnMessage: (String) -> Unit,
    onOpenPhotoLightbox: (DecryptedMessage) -> Unit,
    onOpenMediaGallery: () -> Unit,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mateColors = LocalMateColors.current
    var draftText by rememberSaveable { mutableStateOf("") }
    var showEmojiBar by rememberSaveable { mutableStateOf(false) }
    var showGifSheet by remember { mutableStateOf(false) }
    var pendingBitmapForEditor by remember { mutableStateOf<Bitmap?>(null) }
    var pendingVideoPreview by remember { mutableStateOf<Pair<Uri, VideoMetadataSummary>?>(null) }
    var selectedMessageForMenu by remember { mutableStateOf<DecryptedMessage?>(null) }
    val listState = rememberLazyListState()

    val partnerName = vault.getPartnerName(currentUserId)
    val partnerPhoto = vault.getPartnerPhoto(currentUserId)
    val isPartnerTyping = vault.isPartnerTyping(currentUserId)
    val isPartnerOnline = vault.isPartnerOnline(currentUserId)

    // 1. Zero-permission Photo Picker -> Opens Before-Send Image Editing Studio (or sends GIF if .gif picked!)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val mime = context.contentResolver.getType(uri) ?: ""
                if (mime.contains("gif", ignoreCase = true)) {
                    val gifRes = withContext(Dispatchers.IO) {
                        MediaCompressionUtil.readUriBytesLimited(context, uri)
                    }
                    gifRes.onSuccess { bytes ->
                        onSendGif(draftText, bytes)
                        draftText = ""
                    }.onFailure { err ->
                        onMediaError(err.localizedMessage ?: "Could not load GIF.")
                    }
                } else {
                    val decoded = withContext(Dispatchers.IO) {
                        MediaCompressionUtil.decodeSampledBitmap(context, uri, targetMaxPx = 1600)
                    }
                    if (decoded != null) {
                        pendingBitmapForEditor = decoded
                    } else {
                        onMediaError("Unable to open selected image.")
                    }
                }
            }
        }
    }

    // 2. Zero-permission Video Picker -> Opens Before-Send HD Video Preview Studio
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val meta = withContext(Dispatchers.IO) {
                    MediaCompressionUtil.extractVideoMetadata(context, uri)
                }
                if (meta.sizeBytes > MediaCompressionUtil.MAX_ENCRYPTED_MEDIA_BYTES) {
                    val mb = String.format("%.1f", meta.sizeBytes / (1024f * 1024f))
                    onMediaError("Video is $mb MB. Maximum encrypted HD vault transfer is 14 MB.")
                } else {
                    pendingVideoPreview = uri to meta
                }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mateColors.chatWallBackground)
            .imePadding()
    ) {
        // WhatsApp Classic Conversation Top Bar
        Surface(
            color = mateColors.headerBackground,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = mateColors.headerContent
                    )
                }

                MateAvatar(
                    name = partnerName,
                    photoUrl = partnerPhoto,
                    isOnline = isPartnerOnline,
                    sizeDp = 40
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partnerName,
                        style = MaterialTheme.typography.titleMedium,
                        color = mateColors.headerContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isPartnerTyping -> "typing…"
                            isPartnerOnline -> "online • E2EE active"
                            vault.isLockedTwoUsers() -> "paired mate • E2EE active"
                            else -> "awaiting partner pairing"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPartnerTyping) Color(0xFF69F0AE) else mateColors.headerContent.copy(alpha = 0.8f)
                    )
                }

                IconButton(
                    onClick = onOpenMediaGallery,
                    modifier = Modifier.testTag("chat_header_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = "Shared Media Gallery",
                        tint = mateColors.headerContent
                    )
                }
                IconButton(
                    onClick = onStartVideoCall,
                    modifier = Modifier.testTag("chat_header_video_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Videocam,
                        contentDescription = "Start Video Call",
                        tint = mateColors.headerContent
                    )
                }
                IconButton(
                    onClick = onStartAudioCall,
                    modifier = Modifier.testTag("chat_header_audio_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Start Voice Call",
                        tint = mateColors.headerContent
                    )
                }
            }
        }

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = mateColors.e2eeBannerBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                                text = "Messages, HD photos, GIFs & HD videos are E2EE with AES-256-GCM.",
                                style = MaterialTheme.typography.labelSmall,
                                color = mateColors.e2eeBannerText
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderUid == currentUserId
                MessageBubbleItem(
                    message = msg,
                    isMine = isMine,
                    onClickPhoto = { onOpenPhotoLightbox(msg) },
                    onLongPress = { selectedMessageForMenu = msg }
                )
            }
        }

        // Quick Emoji Reaction & Picker Bar
        AnimatedVisibility(visible = showEmojiBar) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QUICK_EMOJIS.take(8).forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    draftText += emoji
                                    onDraftChanged(draftText)
                                }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }

        // Reply Banner
        AnimatedVisibility(visible = replyingTo != null) {
            if (replyingTo != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyingTo.senderName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyingTo.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onSetReplyTarget(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply")
                        }
                    }
                }
            }
        }

        // Encrypted HD Media Upload Progress Indicator
        AnimatedVisibility(visible = mediaUploadProgress != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mediaUploadLabel ?: "Encrypting & sending HD media…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        val pct = ((mediaUploadProgress ?: 0f) * 100).toInt().coerceIn(0, 100)
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (mediaUploadProgress ?: 0f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Bottom Composer Bar with Emoji, GIF, Photo Editor (HD), and HD Video buttons
        Surface(
            color = mateColors.chatWallBackground,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        IconButton(
                            onClick = { showEmojiBar = !showEmojiBar },
                            modifier = Modifier.testTag("toggle_emoji_bar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEmotions,
                                contentDescription = "Toggle quick emojis",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = draftText,
                            onValueChange = {
                                draftText = it
                                onDraftChanged(it)
                            },
                            placeholder = { Text("Message") },
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        IconButton(
                            onClick = { showGifSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("attach_gif_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GifBox,
                                contentDescription = "Send Animated GIF",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("attach_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = "Edit and Send HD Photo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("attach_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = "Send Encrypted HD Video",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (draftText.isNotBlank()) {
                            onSendMessage(draftText, null)
                            draftText = ""
                        }
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Encrypted Message"
                    )
                }
            }
        }
    }

    // Before-Send WhatsApp-Style Image Editing Studio (with HD 1600p Toggle!)
    if (pendingBitmapForEditor != null) {
        val editorBmp = pendingBitmapForEditor!!
        ImageEditorDialog(
            sourceBitmap = editorBmp,
            initialCaption = draftText,
            onDismiss = { pendingBitmapForEditor = null },
            onSendEditedImage = { caption, editedPayload ->
                pendingBitmapForEditor = null
                draftText = ""
                onSendEditedImage(caption, editedPayload)
            }
        )
    }

    // Before-Send HD Video Preview Studio
    if (pendingVideoPreview != null) {
        val (vidUri, vidMeta) = pendingVideoPreview!!
        VideoSendPreviewDialog(
            videoUri = vidUri,
            metadata = vidMeta,
            initialCaption = draftText,
            onDismiss = { pendingVideoPreview = null },
            onError = { err ->
                pendingVideoPreview = null
                onMediaError(err)
            },
            onSendVideo = { caption, bytes, thumbB64, durMs, isHd ->
                pendingVideoPreview = null
                draftText = ""
                onSendVideo(caption, bytes, thumbB64, durMs, isHd)
            }
        )
    }

    // Animated GIF Picker & Live GIF89a Composer Sheet
    if (showGifSheet) {
        GifPickerAndComposerSheet(
            onDismiss = { showGifSheet = false },
            onError = onMediaError,
            onSendGifBytes = { caption, gifBytes ->
                val finalCap = caption.ifBlank { draftText }
                draftText = ""
                onSendGif(finalCap, gifBytes)
            }
        )
    }

    if (selectedMessageForMenu != null) {
        val target = selectedMessageForMenu!!
        ModalBottomSheet(onDismissRequest = { selectedMessageForMenu = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                TextButton(
                    onClick = {
                        onSetReplyTarget(target)
                        selectedMessageForMenu = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Reply to message", modifier = Modifier.weight(1f))
                }
                if (target.senderUid == currentUserId) {
                    TextButton(
                        onClick = {
                            onDeleteOwnMessage(target.id)
                            selectedMessageForMenu = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Delete encrypted message",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: DecryptedMessage,
    isMine: Boolean,
    onClickPhoto: () -> Unit,
    onLongPress: () -> Unit
) {
    val mateColors = LocalMateColors.current
    val bubbleBg = if (isMine) mateColors.outgoingBubble else mateColors.incomingBubble
    val shape = if (isMine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val hasRichMedia = !message.imageBase64.isNullOrBlank() || message.messageType in listOf("image", "gif", "video")
    val defaultMediaLabels = setOf("📷 Photo", "📷 HD Photo", "🎞️ GIF", "🎬 Video", "🎬 HD Video")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleBg,
            shape = shape,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .combinedClickable(
                    onClick = {
                        if (hasRichMedia) onClickPhoto()
                    },
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                if (!message.replyPreview.isNullOrBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = "↩ ${message.replyPreview}",
                            style = MaterialTheme.typography.labelSmall,
                            color = mateColors.bubbleText.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                if (hasRichMedia) {
                    val inlineBytes = remember(message.id, message.imageBase64) {
                        MediaCompressionUtil.base64ToBytes(message.imageBase64)
                    }
                    val isInlineGif = message.messageType == "gif" && MediaCompressionUtil.isAnimatedGifBytes(inlineBytes)
                    val bitmap = remember(message.id, message.imageBase64, isInlineGif) {
                        if (isInlineGif) null else MediaCompressionUtil.base64ToBitmap(message.imageBase64)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (message.messageType == "gif") 172.dp else 196.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E1B22))
                            .clickable { onClickPhoto() }
                    ) {
                        when {
                            isInlineGif && inlineBytes != null -> {
                                AnimatedGifView(
                                    gifBytes = inlineBytes,
                                    contentDescription = "Animated encrypted GIF",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            bitmap != null -> {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Shared encrypted ${message.messageType}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Video Play Button + Duration Overlay
                        if (message.messageType == "video") {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.28f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = Color(0xDD00A884),
                                    shape = CircleShape,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Play encrypted video",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                            }

                            if (message.mediaDurationMs > 0L) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "▶ ${MediaCompressionUtil.formatDurationMillis(message.mediaDurationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // HD or GIF Corner Badge
                        if (message.isHd || message.messageType == "gif") {
                            Surface(
                                color = if (message.isHd) Color(0xEE00A884) else Color.Black.copy(alpha = 0.68f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = if (message.isHd) "HD" else "GIF",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (message.isHd) Color(0xFF04261C) else Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (message.text.isNotBlank() && !(hasRichMedia && message.text in defaultMediaLabels)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = mateColors.bubbleText
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formattedTime = message.createdAt?.toDate()?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                    } ?: "now"
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = mateColors.timestampText
                    )
                    if (isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val isRead = message.status == "read"
                        val isDeliveredOrRead = message.status == "delivered" || isRead
                        Icon(
                            imageVector = if (isDeliveredOrRead) Icons.Filled.DoneAll else Icons.Filled.Check,
                            contentDescription = "Message ${message.status}",
                            tint = if (isRead) mateColors.blueTick else mateColors.timestampText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MateAvatar(
    name: String,
    photoUrl: String?,
    isOnline: Boolean,
    sizeDp: Int
) {
    Box(modifier = Modifier.size(sizeDp.dp)) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$name profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.trim().take(1).uppercase().ifBlank { "M" },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF25D366))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMediaGalleryModal(
    photoMessages: List<DecryptedMessage>,
    onSelectPhoto: (DecryptedMessage) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Encrypted Shared Gallery (${photoMessages.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (photoMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Photos shared in your encrypted chat appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    items(photoMessages, key = { it.id }) { msg ->
                        val bmp = remember(msg.id, msg.imageBase64) {
                            MediaCompressionUtil.base64ToBitmap(msg.imageBase64)
                        }
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E1B22))
                                .clickable {
                                    onDismiss()
                                    onSelectPhoto(msg)
                                }
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = msg.text,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (msg.messageType == "video") {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .align(Alignment.Center)
                                )
                            }
                            if (msg.isHd || msg.messageType == "gif") {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                ) {
                                    Text(
                                        text = if (msg.isHd) "HD" else "GIF",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

