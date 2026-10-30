package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.preferences.ThemeMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalMateColors
import com.example.ui.theme.SanctuaryGold
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MateViewModel
import com.example.ui.viewmodel.VaultUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MateWorkspaceScreen(
    viewModel: MateViewModel,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mateColors = LocalMateColors.current

    val vaultState by viewModel.vaultUiState.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isChatOpen by viewModel.isChatConversationOpen.collectAsStateWithLifecycle()
    val messages by viewModel.decryptedMessages.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadIncomingCount.collectAsStateWithLifecycle()
    val statuses by viewModel.decryptedStatuses.collectAsStateWithLifecycle()
    val callsHistory by viewModel.callsHistory.collectAsStateWithLifecycle()
    val activeLiveCall by viewModel.activeLiveCall.collectAsStateWithLifecycle()
    val replyingTo by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val activeLightboxMsg by viewModel.activePhotoGalleryMessage.collectAsStateWithLifecycle()
    val isMediaGalleryOpen by viewModel.isMediaGallerySheetOpen.collectAsStateWithLifecycle()
    val activeStoriesViewer by viewModel.activeStoryViewerList.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val mediaUploadProgress by viewModel.mediaUploadProgress.collectAsStateWithLifecycle()
    val mediaUploadLabel by viewModel.mediaUploadLabel.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsStateWithLifecycle()

    var showSecurityInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionError) {
        if (!actionError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(actionError!!)
            viewModel.clearError()
        }
    }

    when (val state = vaultState) {
        is VaultUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is VaultUiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { signOutFromMyMate(context, scope, onSignedOut) }) {
                        Text("Sign out")
                    }
                }
            }
        }

        is VaultUiState.NeedsPairingSetup -> {
            VaultPairingScreen(
                userName = viewModel.currentUserName,
                existingLockedVaultNeedingKey = null,
                isBusy = isBusy,
                errorMessage = actionError,
                onCreateVault = viewModel::createNewVault,
                onJoinVault = viewModel::joinExistingVault,
                onUnlockVaultKey = viewModel::unlockExistingVaultWithKey,
                onSignOut = { signOutFromMyMate(context, scope, onSignedOut) }
            )
        }

        is VaultUiState.NeedsKeyUnlock -> {
            VaultPairingScreen(
                userName = viewModel.currentUserName,
                existingLockedVaultNeedingKey = state.vault,
                isBusy = isBusy,
                errorMessage = actionError,
                onCreateVault = viewModel::createNewVault,
                onJoinVault = viewModel::joinExistingVault,
                onUnlockVaultKey = viewModel::unlockExistingVaultWithKey,
                onSignOut = { signOutFromMyMate(context, scope, onSignedOut) }
            )
        }

        is VaultUiState.Ready -> {
            val photoMessages = remember(messages) {
                messages.filter {
                    !it.imageBase64.isNullOrBlank() || it.messageType in listOf("image", "gif", "video")
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideTablet = maxWidth >= 700.dp

                // On compact phones, opening the chat enters classic full-screen WhatsApp Chat view
                if (!isWideTablet && isChatOpen) {
                    ChatConversationScreen(
                        vault = state.vault,
                        currentUserId = viewModel.currentUserId,
                        messages = messages,
                        replyingTo = replyingTo,
                        mediaUploadProgress = mediaUploadProgress,
                        mediaUploadLabel = mediaUploadLabel,
                        onBack = viewModel::closeChatConversation,
                        onDraftChanged = viewModel::onDraftChanged,
                        onSendMessage = viewModel::sendMessage,
                        onSendEditedImage = viewModel::sendEditedImageMessage,
                        onSendGif = viewModel::sendGifMessage,
                        onSendVideo = viewModel::sendVideoMessage,
                        onMediaError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        onSetReplyTarget = viewModel::setReplyTarget,
                        onDeleteOwnMessage = viewModel::deleteMessage,
                        onOpenPhotoLightbox = viewModel::openPhotoLightbox,
                        onOpenMediaGallery = { viewModel.setMediaGalleryOpen(true) },
                        onStartAudioCall = { viewModel.startCall("audio") },
                        onStartVideoCall = { viewModel.startCall("video") }
                    )
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            Surface(
                                color = mateColors.headerBackground,
                                tonalElevation = 4.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.app_name),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = mateColors.headerContent,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            color = SanctuaryGold.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = if (state.vault.isLockedTwoUsers()) "2/2 LOCKED" else "1/2 WAITING",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SanctuaryGold,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = { showSecurityInfoDialog = true },
                                            modifier = Modifier.testTag("security_verification_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Security,
                                                contentDescription = "Verify End-to-End Encryption",
                                                tint = mateColors.headerContent
                                            )
                                        }
                                        IconButton(
                                            onClick = { showSettingsSheet = true },
                                            modifier = Modifier.testTag("settings_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "Theme & Privacy Settings",
                                                tint = mateColors.headerContent
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        bottomBar = {
                            if (!isWideTablet) {
                                NavigationBar {
                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.CHATS,
                                        onClick = { viewModel.selectTab(MainTab.CHATS) },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (unreadCount > 0) {
                                                        Badge { Text(unreadCount.toString()) }
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selectedTab == MainTab.CHATS) {
                                                        Icons.AutoMirrored.Filled.Chat
                                                    } else {
                                                        Icons.AutoMirrored.Outlined.Chat
                                                    },
                                                    contentDescription = "Chats"
                                                )
                                            }
                                        },
                                        label = { Text(stringResource(R.string.tab_chats)) },
                                        modifier = Modifier.testTag("nav_tab_chats")
                                    )

                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.STATUS,
                                        onClick = { viewModel.selectTab(MainTab.STATUS) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selectedTab == MainTab.STATUS) {
                                                    Icons.Filled.MotionPhotosOn
                                                } else {
                                                    Icons.Outlined.MotionPhotosOn
                                                },
                                                contentDescription = "Status"
                                            )
                                        },
                                        label = { Text(stringResource(R.string.tab_status)) },
                                        modifier = Modifier.testTag("nav_tab_status")
                                    )

                                    NavigationBarItem(
                                        selected = selectedTab == MainTab.CALLS,
                                        onClick = { viewModel.selectTab(MainTab.CALLS) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selectedTab == MainTab.CALLS) {
                                                    Icons.Filled.Call
                                                } else {
                                                    Icons.Outlined.Call
                                                },
                                                contentDescription = "Calls"
                                            )
                                        },
                                        label = { Text(stringResource(R.string.tab_calls)) },
                                        modifier = Modifier.testTag("nav_tab_calls")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isWideTablet) {
                                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.CHATS,
                                        onClick = { viewModel.selectTab(MainTab.CHATS) },
                                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                                        label = { Text("Chats") }
                                    )
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.STATUS,
                                        onClick = { viewModel.selectTab(MainTab.STATUS) },
                                        icon = { Icon(Icons.Filled.MotionPhotosOn, contentDescription = "Status") },
                                        label = { Text("Status") }
                                    )
                                    NavigationRailItem(
                                        selected = selectedTab == MainTab.CALLS,
                                        onClick = { viewModel.selectTab(MainTab.CALLS) },
                                        icon = { Icon(Icons.Filled.Call, contentDescription = "Calls") },
                                        label = { Text("Calls") }
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                when (selectedTab) {
                                    MainTab.CHATS -> {
                                        val lastPreview = remember(state.vault.lastMessagePreview, state.vault.lastMessageIv) {
                                            viewModel.decryptPreviewOrNull(
                                                state.vault.lastMessagePreview,
                                                state.vault.lastMessageIv
                                            )
                                        }
                                        ChatsTabContent(
                                            vault = state.vault,
                                            currentUserId = viewModel.currentUserId,
                                            secretCode = state.secretCode,
                                            safetyFingerprint = state.safetyFingerprint,
                                            lastDecryptedPreview = lastPreview,
                                            unreadCount = unreadCount,
                                            sharedPhotoCount = photoMessages.size,
                                            onOpenChat = viewModel::openChatConversation,
                                            onOpenMediaGallery = { viewModel.setMediaGalleryOpen(true) },
                                            onStartAudioCall = { viewModel.startCall("audio") },
                                            onStartVideoCall = { viewModel.startCall("video") }
                                        )
                                    }

                                    MainTab.STATUS -> {
                                        StatusTabScreen(
                                            vault = state.vault,
                                            currentUserId = viewModel.currentUserId,
                                            currentUserName = viewModel.currentUserName,
                                            statuses = statuses,
                                            onPostStatus = viewModel::postStatusStory,
                                            onOpenStories = { list -> viewModel.openStoryViewer(list) },
                                            onDeleteStatus = viewModel::deleteStatusStory
                                        )
                                    }

                                    MainTab.CALLS -> {
                                        CallsTabScreen(
                                            vault = state.vault,
                                            currentUserId = viewModel.currentUserId,
                                            callsHistory = callsHistory,
                                            onStartAudioCall = { viewModel.startCall("audio") },
                                            onStartVideoCall = { viewModel.startCall("video") }
                                        )
                                    }
                                }
                            }

                            // Canonical List-Detail Pane on Wide Tablets when Chats tab is active
                            if (isWideTablet && selectedTab == MainTab.CHATS) {
                                Box(modifier = Modifier.weight(1.25f)) {
                                    ChatConversationScreen(
                                        vault = state.vault,
                                        currentUserId = viewModel.currentUserId,
                                        messages = messages,
                                        replyingTo = replyingTo,
                                        mediaUploadProgress = mediaUploadProgress,
                                        mediaUploadLabel = mediaUploadLabel,
                                        onBack = viewModel::closeChatConversation,
                                        onDraftChanged = viewModel::onDraftChanged,
                                        onSendMessage = viewModel::sendMessage,
                                        onSendEditedImage = viewModel::sendEditedImageMessage,
                                        onSendGif = viewModel::sendGifMessage,
                                        onSendVideo = viewModel::sendVideoMessage,
                                        onMediaError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                        onSetReplyTarget = viewModel::setReplyTarget,
                                        onDeleteOwnMessage = viewModel::deleteMessage,
                                        onOpenPhotoLightbox = viewModel::openPhotoLightbox,
                                        onOpenMediaGallery = { viewModel.setMediaGalleryOpen(true) },
                                        onStartAudioCall = { viewModel.startCall("audio") },
                                        onStartVideoCall = { viewModel.startCall("video") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Overlays: Rich Media Lightbox (HD Photos, GIFs, HD Videos), Shared Media Gallery, 24h Story Viewer, Live WebRTC Call
            if (isMediaGalleryOpen) {
                SharedMediaGalleryModal(
                    photoMessages = photoMessages,
                    onSelectPhoto = { viewModel.openPhotoLightbox(it) },
                    onDismiss = { viewModel.setMediaGalleryOpen(false) }
                )
            }

            if (activeLightboxMsg != null) {
                FullScreenMediaLightbox(
                    message = activeLightboxMsg!!,
                    resolveMediaFile = viewModel::resolveOrDownloadMediaFile,
                    onDismiss = { viewModel.openPhotoLightbox(null) }
                )
            }

            if (!activeStoriesViewer.isNullOrEmpty()) {
                FullScreenStoryViewerDialog(
                    stories = activeStoriesViewer!!,
                    onDismiss = { viewModel.openStoryViewer(null) }
                )
            }

            if (activeLiveCall != null) {
                val liveCall = activeLiveCall!!
                ActiveWebRtcCallOverlay(
                    call = liveCall,
                    currentUserId = viewModel.currentUserId,
                    safetyFingerprint = state.safetyFingerprint,
                    unpackSignalingPayload = viewModel::unpackSignalingPayload,
                    onPublishLocalOffer = { sdp -> viewModel.publishLocalSdpOffer(liveCall.id, sdp) },
                    onAcceptCallWithAnswer = { ans -> viewModel.acceptIncomingCall(liveCall.id, ans) },
                    onPublishIceCandidates = { callerFlag, cands ->
                        viewModel.publishIceCandidates(liveCall.id, callerFlag, cands)
                    },
                    onEndOrDecline = { dur -> viewModel.endOrDeclineCall(liveCall, dur) }
                )
            }

            if (showSecurityInfoDialog) {
                AlertDialog(
                    onDismissRequest = { showSecurityInfoDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("End-to-End Encryption Verified") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Compare this 12-digit Safety Number with your Mate. If they match, your AES-256-GCM cipher & WebRTC SRTP channel are mathematically verified:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.safetyFingerprint,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                            Text(
                                text = "• Strict 2-User Lock: ${if (state.vault.isLockedTwoUsers()) "Active (2/2 seats sealed)" else "1/2 seats claimed"}\n• Pairing Code: ${state.secretCode}\n• Third-party signup & search: Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSecurityInfoDialog = false }) {
                            Text("Verified")
                        }
                    }
                )
            }

            if (showSettingsSheet) {
                ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Appearance & Privacy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("App Theme", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Read Receipts (Blue Ticks)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Notify your Mate when you read messages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = readReceiptsEnabled,
                                onCheckedChange = viewModel::setReadReceipts
                            )
                        }

                        HorizontalDivider()

                        TextButton(
                            onClick = {
                                showSettingsSheet = false
                                signOutFromMyMate(context, scope, onSignedOut)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out of Account")
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
    }
}
