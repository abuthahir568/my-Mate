package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.DecryptedMessage
import com.example.data.model.DecryptedStatus
import com.example.data.model.StatusStory
import com.example.data.model.Vault
import com.example.data.preferences.MatePreferences
import com.example.data.preferences.ThemeMode
import com.example.data.repository.MateRepository
import com.example.security.E2EECrypto
import com.example.util.EditedImagePayload
import com.example.util.MediaCompressionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey

sealed interface VaultUiState {
    data object Loading : VaultUiState
    data object NeedsPairingSetup : VaultUiState
    data class NeedsKeyUnlock(val vault: Vault) : VaultUiState
    data class Ready(
        val vault: Vault,
        val secretCode: String,
        val safetyFingerprint: String
    ) : VaultUiState
    data class Error(val message: String) : VaultUiState
}

enum class MainTab {
    CHATS, STATUS, CALLS
}

@OptIn(ExperimentalCoroutinesApi::class)
class MateViewModel(
    private val repository: MateRepository,
    private val preferences: MatePreferences,
    val currentUserId: String,
    val currentUserName: String,
    val currentUserPhoto: String?
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(MainTab.CHATS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _isChatConversationOpen = MutableStateFlow(false)
    val isChatConversationOpen: StateFlow<Boolean> = _isChatConversationOpen.asStateFlow()

    private val _activePhotoGalleryMessage = MutableStateFlow<DecryptedMessage?>(null)
    val activePhotoGalleryMessage: StateFlow<DecryptedMessage?> = _activePhotoGalleryMessage.asStateFlow()

    private val _isMediaGallerySheetOpen = MutableStateFlow(false)
    val isMediaGallerySheetOpen: StateFlow<Boolean> = _isMediaGallerySheetOpen.asStateFlow()

    private val _activeStoryViewerList = MutableStateFlow<List<DecryptedStatus>?>(null)
    val activeStoryViewerList: StateFlow<List<DecryptedStatus>?> = _activeStoryViewerList.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<DecryptedMessage?>(null)
    val replyingToMessage: StateFlow<DecryptedMessage?> = _replyingToMessage.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _mediaUploadProgress = MutableStateFlow<Float?>(null)
    val mediaUploadProgress: StateFlow<Float?> = _mediaUploadProgress.asStateFlow()

    private val _mediaUploadLabel = MutableStateFlow<String?>(null)
    val mediaUploadLabel: StateFlow<String?> = _mediaUploadLabel.asStateFlow()

    private val _downloadingMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingMessageIds: StateFlow<Set<String>> = _downloadingMessageIds.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = preferences.themeModeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ThemeMode.SYSTEM
    )

    val readReceiptsEnabled: StateFlow<Boolean> = preferences.readReceiptsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = true
    )

    private val secretFlow: StateFlow<String?> = preferences.observeVaultSecret(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val derivedKeyFlow: StateFlow<Pair<String, SecretKey>?> = secretFlow
        .map { secret ->
            if (secret.isNullOrBlank()) null
            else secret to E2EECrypto.deriveAesKey(secret)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val rawVaultFlow: StateFlow<Result<Vault?>?> = repository.observeMyVault(currentUserId)
        .map<Vault?, Result<Vault?>?> { Result.success(it) }
        .catch { err ->
            Log.w(TAG, "Error observing vault", err)
            emit(Result.failure(err))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val vaultUiState: StateFlow<VaultUiState> = combine(rawVaultFlow, secretFlow) { vaultResult, secret ->
        if (vaultResult == null) {
            VaultUiState.Loading
        } else if (vaultResult.isFailure) {
            VaultUiState.Error(vaultResult.exceptionOrNull()?.localizedMessage ?: "Unable to sync Vault")
        } else {
            val vault = vaultResult.getOrNull()
            if (vault == null) {
                VaultUiState.NeedsPairingSetup
            } else if (secret.isNullOrBlank() || E2EECrypto.derivePairingHash(secret) != vault.pairingCodeHash) {
                VaultUiState.NeedsKeyUnlock(vault)
            } else {
                VaultUiState.Ready(
                    vault = vault,
                    secretCode = secret,
                    safetyFingerprint = E2EECrypto.deriveSafetyFingerprint(secret, vault.id)
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = VaultUiState.Loading
    )

    private val rawMessagesFlow: StateFlow<List<ChatMessage>> = rawVaultFlow
        .flatMapLatest { res ->
            val vault = res?.getOrNull()
            if (vault == null || vault.id.isBlank()) flowOf(emptyList())
            else repository.observeMessages(vault.id, currentUserId)
                .catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val decryptedMessages: StateFlow<List<DecryptedMessage>> = combine(
        rawMessagesFlow,
        derivedKeyFlow
    ) { rawList, keyPair ->
        val key = keyPair?.second ?: return@combine emptyList()
        rawList.map { msg ->
            val decryptedText = E2EECrypto.decrypt(msg.cipherText, msg.iv, key)
            val decryptedImage = if (!msg.mediaCipherText.isNullOrBlank() && !msg.mediaIv.isNullOrBlank()) {
                E2EECrypto.decrypt(msg.mediaCipherText, msg.mediaIv, key)
                    .takeIf { !it.startsWith("🔒") }
            } else null

            val replyPreview = msg.replyPreviewCipher?.let { packed ->
                val idx = packed.indexOf(':')
                if (idx > 0) {
                    val rIv = packed.substring(0, idx)
                    val rCipher = packed.substring(idx + 1)
                    E2EECrypto.decrypt(rCipher, rIv, key)
                } else null
            }

            DecryptedMessage(
                id = msg.id,
                senderUid = msg.senderUid,
                senderName = msg.senderName,
                messageType = msg.messageType,
                text = decryptedText,
                imageBase64 = decryptedImage,
                isHd = msg.isHd,
                mediaGroupId = msg.mediaGroupId,
                totalChunks = (msg.totalChunks ?: 0L).toInt(),
                mediaDurationMs = msg.mediaDurationMs ?: 0L,
                replyToId = msg.replyToId,
                replyPreview = replyPreview,
                status = msg.status,
                createdAt = msg.createdAt
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val unreadIncomingCount: StateFlow<Int> = rawMessagesFlow
        .map { list -> list.count { it.senderUid != currentUserId && it.status != "read" } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    private val rawStatusesFlow: StateFlow<List<StatusStory>> = rawVaultFlow
        .flatMapLatest { res ->
            val vault = res?.getOrNull()
            if (vault == null || vault.id.isBlank()) flowOf(emptyList())
            else repository.observeActiveStatuses(vault.id, currentUserId)
                .catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val decryptedStatuses: StateFlow<List<DecryptedStatus>> = combine(
        rawStatusesFlow,
        derivedKeyFlow
    ) { stories, keyPair ->
        val key = keyPair?.second ?: return@combine emptyList()
        stories.map { st ->
            val caption = E2EECrypto.decrypt(st.captionCipher, st.captionIv, key)
            val imageBase64 = if (!st.mediaCipher.isNullOrBlank() && !st.mediaIv.isNullOrBlank()) {
                E2EECrypto.decrypt(st.mediaCipher, st.mediaIv, key)
                    .takeIf { !it.startsWith("🔒") }
            } else null

            DecryptedStatus(
                id = st.id,
                authorUid = st.authorUid,
                authorName = st.authorName,
                mediaType = st.mediaType,
                caption = caption,
                imageBase64 = imageBase64,
                backgroundHex = st.backgroundHex,
                durationSeconds = st.durationSeconds.toInt().coerceIn(1, 30),
                expiresAtMillis = st.expiresAtMillis,
                viewedByUids = st.viewedByUids,
                createdAt = st.createdAt
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val callsHistory: StateFlow<List<CallSession>> = rawVaultFlow
        .flatMapLatest { res ->
            val vault = res?.getOrNull()
            if (vault == null || vault.id.isBlank()) flowOf(emptyList())
            else repository.observeCalls(vault.id, currentUserId)
                .catch { emit(emptyList()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val activeLiveCall: StateFlow<CallSession?> = callsHistory
        .map { calls ->
            val cutoff = System.currentTimeMillis() - 120_000L
            calls.firstOrNull { call ->
                call.state in listOf("ringing", "connected") &&
                    ((call.createdAt?.toDate()?.time ?: System.currentTimeMillis()) >= cutoff || call.state == "connected")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private var typingDebounceJob: Job? = null

    init {
        // Automatically mark online when ViewModel starts and mark read when conversation is open
        viewModelScope.launch {
            rawVaultFlow.collect { res ->
                val v = res?.getOrNull()
                if (v != null) {
                    repository.updatePresenceOrTyping(v, isOnline = true)
                }
            }
        }

        viewModelScope.launch {
            combine(
                _isChatConversationOpen,
                rawMessagesFlow,
                rawVaultFlow,
                readReceiptsEnabled
            ) { isOpen, msgs, vRes, receiptsOn ->
                val vault = vRes?.getOrNull()
                if (isOpen && receiptsOn && vault != null) {
                    vault.id to msgs
                } else null
            }.collect { pair ->
                if (pair != null) {
                    repository.markMessagesAsRead(pair.first, pair.second)
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun openChatConversation() {
        _isChatConversationOpen.value = true
    }

    fun closeChatConversation() {
        _isChatConversationOpen.value = false
        _replyingToMessage.value = null
        val vault = (vaultUiState.value as? VaultUiState.Ready)?.vault
        if (vault != null) {
            viewModelScope.launch {
                repository.updatePresenceOrTyping(vault, isTyping = false)
            }
        }
    }

    fun setReplyTarget(message: DecryptedMessage?) {
        _replyingToMessage.value = message
    }

    fun openPhotoLightbox(message: DecryptedMessage?) {
        _activePhotoGalleryMessage.value = message
    }

    fun setMediaGalleryOpen(open: Boolean) {
        _isMediaGallerySheetOpen.value = open
    }

    fun openStoryViewer(stories: List<DecryptedStatus>?) {
        _activeStoryViewerList.value = stories
        if (!stories.isNullOrEmpty()) {
            val vault = (vaultUiState.value as? VaultUiState.Ready)?.vault ?: return
            viewModelScope.launch {
                val rawList = rawStatusesFlow.value
                stories.forEach { dec ->
                    val raw = rawList.firstOrNull { it.id == dec.id }
                    if (raw != null) {
                        repository.markStatusViewed(vault.id, raw)
                    }
                }
            }
        }
    }

    fun clearError() {
        _actionError.value = null
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setReadReceipts(enabled: Boolean) {
        viewModelScope.launch { preferences.setReadReceipts(enabled) }
    }

    fun createNewVault(customSecretOrAuto: String) {
        val normalized = E2EECrypto.normalizeSecret(customSecretOrAuto)
        if (normalized.length < 6) {
            _actionError.value = "Pairing secret code must be at least 6 characters."
            return
        }
        _isBusy.value = true
        viewModelScope.launch {
            val result = repository.createTwoUserVault(
                pairingSecret = normalized,
                displayName = currentUserName,
                photoUrl = currentUserPhoto
            )
            _isBusy.value = false
            result.onSuccess {
                preferences.saveVaultSecret(currentUserId, normalized)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not create 2-user vault."
            }
        }
    }

    fun joinExistingVault(enteredSecret: String) {
        val normalized = E2EECrypto.normalizeSecret(enteredSecret)
        if (normalized.length < 6) {
            _actionError.value = "Enter the valid pairing code shared by your mate."
            return
        }
        _isBusy.value = true
        viewModelScope.launch {
            val result = repository.joinVaultWithPairingSecret(
                pairingSecret = normalized,
                displayName = currentUserName,
                photoUrl = currentUserPhoto
            )
            _isBusy.value = false
            result.onSuccess {
                preferences.saveVaultSecret(currentUserId, normalized)
            }.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Vault locked or invalid pairing code."
            }
        }
    }

    fun unlockExistingVaultWithKey(enteredSecret: String) {
        val state = vaultUiState.value as? VaultUiState.NeedsKeyUnlock ?: return
        val normalized = E2EECrypto.normalizeSecret(enteredSecret)
        if (E2EECrypto.derivePairingHash(normalized) != state.vault.pairingCodeHash) {
            _actionError.value = "Incorrect Vault Secret Key. Decryption hash does not match."
            return
        }
        viewModelScope.launch {
            preferences.saveVaultSecret(currentUserId, normalized)
        }
    }

    fun onDraftChanged(draft: String) {
        val vault = (vaultUiState.value as? VaultUiState.Ready)?.vault ?: return
        typingDebounceJob?.cancel()
        if (draft.isBlank()) {
            viewModelScope.launch { repository.updatePresenceOrTyping(vault, isTyping = false) }
            return
        }
        typingDebounceJob = viewModelScope.launch {
            repository.updatePresenceOrTyping(vault, isTyping = true)
            delay(2800L)
            repository.updatePresenceOrTyping(vault, isTyping = false)
        }
    }

    fun sendMessage(plainText: String, imageBase64: String? = null) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        if (plainText.isBlank() && imageBase64.isNullOrBlank()) return

        val replyTarget = _replyingToMessage.value
        _replyingToMessage.value = null
        typingDebounceJob?.cancel()

        viewModelScope.launch {
            val res = repository.sendEncryptedMessage(
                vault = ready.vault,
                senderName = currentUserName,
                plainText = plainText.trim(),
                secretKey = key,
                imageBase64 = imageBase64,
                replyToId = replyTarget?.id,
                replyPlainPreview = replyTarget?.text
            )
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to send encrypted message."
            }
        }
    }

    /**
     * Sends an edited photo from the Image Editor Studio in Standard or True HD (1600p) quality.
     */
    fun sendEditedImageMessage(caption: String, editedPayload: EditedImagePayload) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        val replyTarget = _replyingToMessage.value
        _replyingToMessage.value = null
        typingDebounceJob?.cancel()

        viewModelScope.launch {
            val needsChunks = editedPayload.hdChunkBytes != null
            if (needsChunks) {
                _mediaUploadLabel.value = "Encrypting & sending HD photo…"
                _mediaUploadProgress.value = 0.05f
            }
            val res = withContext(Dispatchers.IO) {
                repository.sendEncryptedMessage(
                    vault = ready.vault,
                    senderName = currentUserName,
                    plainText = caption.trim(),
                    secretKey = key,
                    imageBase64 = editedPayload.inlinePreviewBase64,
                    messageType = "image",
                    isHd = editedPayload.isHd,
                    rawMediaBytes = editedPayload.hdChunkBytes,
                    replyToId = replyTarget?.id,
                    replyPlainPreview = replyTarget?.text,
                    onUploadProgress = { p -> _mediaUploadProgress.value = p }
                )
            }
            _mediaUploadProgress.value = null
            _mediaUploadLabel.value = null
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to send edited photo."
            }
        }
    }

    /**
     * Sends an animated `.gif` (either picked from device storage or generated in GIF Studio)
     * preserving all animation frames under AES-256-GCM encryption.
     */
    fun sendGifMessage(caption: String, gifBytes: ByteArray) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        if (gifBytes.isEmpty()) return

        val replyTarget = _replyingToMessage.value
        _replyingToMessage.value = null
        typingDebounceJob?.cancel()

        viewModelScope.launch {
            val isInlineSmall = gifBytes.size <= 230_000
            if (!isInlineSmall) {
                _mediaUploadLabel.value = "Encrypting & sending animated GIF…"
                _mediaUploadProgress.value = 0.05f
            }
            val res = withContext(Dispatchers.IO) {
                val inlineB64 = if (isInlineSmall) {
                    MediaCompressionUtil.bytesToBase64(gifBytes)
                } else {
                    // Extract first frame static preview for instant bubble rendering while chunks stream
                    val firstFrameBmp = android.graphics.BitmapFactory.decodeByteArray(gifBytes, 0, gifBytes.size)
                    if (firstFrameBmp != null) {
                        val out = java.io.ByteArrayOutputStream()
                        firstFrameBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                        MediaCompressionUtil.bytesToBase64(out.toByteArray())
                    } else null
                }

                repository.sendEncryptedMessage(
                    vault = ready.vault,
                    senderName = currentUserName,
                    plainText = caption.trim(),
                    secretKey = key,
                    imageBase64 = inlineB64,
                    messageType = "gif",
                    isHd = false,
                    rawMediaBytes = if (isInlineSmall) null else gifBytes,
                    replyToId = replyTarget?.id,
                    replyPlainPreview = replyTarget?.text,
                    onUploadProgress = { p -> _mediaUploadProgress.value = p }
                )
            }
            _mediaUploadProgress.value = null
            _mediaUploadLabel.value = null
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to send encrypted GIF."
            }
        }
    }

    /**
     * Sends an encrypted video (Standard or HD) using multi-chunk AES-256-GCM binary streaming.
     */
    fun sendVideoMessage(
        caption: String,
        videoBytes: ByteArray,
        thumbnailBase64: String?,
        durationMs: Long,
        isHd: Boolean
    ) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        if (videoBytes.isEmpty()) return

        val replyTarget = _replyingToMessage.value
        _replyingToMessage.value = null
        typingDebounceJob?.cancel()

        viewModelScope.launch {
            _mediaUploadLabel.value = if (isHd) "Encrypting & sending HD video…" else "Encrypting & sending video…"
            _mediaUploadProgress.value = 0.04f
            val res = withContext(Dispatchers.IO) {
                repository.sendEncryptedMessage(
                    vault = ready.vault,
                    senderName = currentUserName,
                    plainText = caption.trim(),
                    secretKey = key,
                    imageBase64 = thumbnailBase64,
                    messageType = "video",
                    isHd = isHd,
                    rawMediaBytes = videoBytes,
                    mediaDurationMs = durationMs,
                    replyToId = replyTarget?.id,
                    replyPlainPreview = replyTarget?.text,
                    onUploadProgress = { p -> _mediaUploadProgress.value = p }
                )
            }
            _mediaUploadProgress.value = null
            _mediaUploadLabel.value = null
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to send encrypted HD video."
            }
        }
    }

    /**
     * Resolves or downloads+decrypts chunked HD media (HD photo, large GIF, or HD MP4 video)
     * into a local cache file in `cacheDir` for instant playback/viewing.
     */
    suspend fun resolveOrDownloadMediaFile(
        cacheDir: File,
        message: DecryptedMessage
    ): Result<File> = withContext(Dispatchers.IO) {
        val ext = when (message.messageType) {
            "video" -> "mp4"
            "gif" -> "gif"
            else -> "jpg"
        }
        val targetFile = File(cacheDir, "mymate_e2ee_${message.id}.$ext")
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext Result.success(targetFile)
        }

        val ready = vaultUiState.value as? VaultUiState.Ready
            ?: return@withContext Result.failure(IllegalStateException("Vault is not unlocked."))
        val key = derivedKeyFlow.value?.second
            ?: return@withContext Result.failure(IllegalStateException("Missing decryption key."))

        if (!message.mediaGroupId.isNullOrBlank() && message.totalChunks > 0) {
            _downloadingMessageIds.update { it + message.id }
            try {
                val bytes = repository.downloadAndDecryptMediaBytes(
                    vaultId = ready.vault.id,
                    mediaGroupId = message.mediaGroupId,
                    totalChunks = message.totalChunks,
                    secretKey = key
                ).getOrThrow()
                targetFile.writeBytes(bytes)
                Result.success(targetFile)
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _downloadingMessageIds.update { it - message.id }
            }
        } else if (!message.imageBase64.isNullOrBlank()) {
            val inlineBytes = MediaCompressionUtil.base64ToBytes(message.imageBase64)
                ?: return@withContext Result.failure(IllegalStateException("Invalid inline media payload."))
            targetFile.writeBytes(inlineBytes)
            Result.success(targetFile)
        } else {
            Result.failure(IllegalStateException("No media attached to message."))
        }
    }

    fun deleteMessage(messageId: String) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        viewModelScope.launch {
            repository.deleteOwnMessage(ready.vault.id, messageId)
        }
    }

    fun postStatusStory(
        mediaType: String,
        caption: String,
        durationSeconds: Int,
        backgroundHex: String,
        imageBase64: String? = null
    ) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        val clampedDuration = durationSeconds.coerceIn(1, 30)
        _isBusy.value = true
        viewModelScope.launch {
            val res = repository.postEncryptedStatus(
                vault = ready.vault,
                authorName = currentUserName,
                mediaType = mediaType,
                caption = caption,
                durationSeconds = clampedDuration,
                backgroundHex = backgroundHex,
                secretKey = key,
                imageBase64 = imageBase64
            )
            _isBusy.value = false
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Failed to post 24h status."
            }
        }
    }

    fun deleteStatusStory(statusId: String) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        viewModelScope.launch {
            repository.deleteStatus(ready.vault.id, statusId)
        }
    }

    fun decryptPreviewOrNull(cipherText: String?, iv: String?): String? {
        if (cipherText.isNullOrBlank() || iv.isNullOrBlank()) return null
        val key = derivedKeyFlow.value?.second ?: return null
        return E2EECrypto.decrypt(cipherText, iv, key)
    }

    fun startCall(callType: String) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        if (!ready.vault.isLockedTwoUsers()) {
            _actionError.value = "Wait for your Mate to pair into Slot 2 before placing a call."
            return
        }
        val key = derivedKeyFlow.value?.second ?: return
        viewModelScope.launch {
            val placeholderOffer = E2EECrypto.encrypt("INIT_WEBRTC_RING", key)
            val packedOffer = "${placeholderOffer.iv}:${placeholderOffer.cipherText}"
            val res = repository.initiateWebRtcCall(
                vault = ready.vault,
                callerName = currentUserName,
                callType = callType,
                encryptedSdpOffer = packedOffer
            )
            res.onFailure { err ->
                _actionError.value = err.localizedMessage ?: "Could not start encrypted call."
            }
        }
    }

    fun publishLocalSdpOffer(callId: String, rawSdpOfferJson: String) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        val enc = E2EECrypto.encrypt(rawSdpOfferJson, key)
        viewModelScope.launch {
            repository.updateCallSignaling(
                vaultId = ready.vault.id,
                callId = callId,
                encryptedSdpOffer = "${enc.iv}:${enc.cipherText}"
            )
        }
    }

    fun acceptIncomingCall(callId: String, rawSdpAnswerJson: String? = null) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        val packedAnswer = rawSdpAnswerJson?.let {
            val enc = E2EECrypto.encrypt(it, key)
            "${enc.iv}:${enc.cipherText}"
        }
        viewModelScope.launch {
            repository.updateCallSignaling(
                vaultId = ready.vault.id,
                callId = callId,
                newState = "connected",
                encryptedSdpAnswer = packedAnswer
            )
        }
    }

    fun publishIceCandidates(callId: String, isCaller: Boolean, rawCandidatesJson: String) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val key = derivedKeyFlow.value?.second ?: return
        val enc = E2EECrypto.encrypt(rawCandidatesJson, key)
        val packed = "${enc.iv}:${enc.cipherText}"
        viewModelScope.launch {
            if (isCaller) {
                repository.updateCallSignaling(
                    vaultId = ready.vault.id,
                    callId = callId,
                    encryptedCallerCandidates = packed
                )
            } else {
                repository.updateCallSignaling(
                    vaultId = ready.vault.id,
                    callId = callId,
                    encryptedCalleeCandidates = packed
                )
            }
        }
    }

    fun unpackSignalingPayload(packed: String?): String? {
        if (packed.isNullOrBlank()) return null
        val key = derivedKeyFlow.value?.second ?: return null
        val idx = packed.indexOf(':')
        if (idx <= 0) return null
        val iv = packed.substring(0, idx)
        val cipher = packed.substring(idx + 1)
        val plain = E2EECrypto.decrypt(cipher, iv, key)
        return if (plain.startsWith("🔒") || plain == "INIT_WEBRTC_RING") null else plain
    }

    fun endOrDeclineCall(call: CallSession, elapsedSeconds: Long) {
        val ready = vaultUiState.value as? VaultUiState.Ready ?: return
        val finalState = if (call.state == "ringing" && call.callerUid != currentUserId) {
            "declined"
        } else {
            "ended"
        }
        viewModelScope.launch {
            repository.updateCallSignaling(
                vaultId = ready.vault.id,
                callId = call.id,
                newState = finalState,
                durationSeconds = elapsedSeconds.coerceAtLeast(0L)
            )
        }
    }

    private companion object {
        const val TAG = "MateViewModel"
    }
}
