package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.ChatMessage
import com.example.data.model.MediaChunk
import com.example.data.model.StatusStory
import com.example.data.model.Vault
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.example.security.E2EECrypto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Strict 2-User Firestore Repository for My Mate.
 *
 * Enforces:
 * - Named database instance via `R.string.firestore_database_id`
 * - Zero-trust query alignment (`whereArrayContains("memberUids", uid)`)
 * - Server timestamps (`FieldValue.serverTimestamp()`)
 * - Client-side AES-256-GCM encryption prior to every write
 * - 30-second max status duration and automatic 24-hour expiration cleanup
 */
class MateRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must verify mobile number before accessing My Mate Vault.")
    }

    /**
     * Observes the authenticated user's single 2-person Vault using query alignment
     * (`whereArrayContains("memberUids", uid)`).
     */
    fun observeMyVault(userId: String = requireUserId()): Flow<Vault?> = flow {
        val path = "vaults"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", userId)
                .limit(1)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.firstOrNull()?.toObject(
                        Vault::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    /**
     * Creates a new 2-seat Vault as User 1 (`status = "waiting"`), awaiting only the 2nd Mate.
     */
    suspend fun createTwoUserVault(
        pairingSecret: String,
        displayName: String,
        photoUrl: String? = null
    ): Result<Vault> {
        val uid = requireUserId()
        val vaultId = E2EECrypto.deriveVaultId(pairingSecret)
        val codeHash = E2EECrypto.derivePairingHash(pairingSecret)
        val docRef = db.collection("vaults").document(vaultId)

        return try {
            val payload = mutableMapOf<String, Any>(
                "pairingCodeHash" to codeHash,
                "user1Uid" to uid,
                "user1Name" to displayName.ifBlank { "Mate 1" }.take(80),
                "user2Uid" to "",
                "user2Name" to "",
                "memberUids" to listOf(uid),
                "status" to "waiting",
                "user1Typing" to false,
                "user2Typing" to false,
                "user1Online" to true,
                "user2Online" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoUrl.isNullOrBlank()) {
                payload["user1Photo"] = photoUrl.take(500)
            }
            docRef.set(payload).await()
            val created = docRef.get().await().toObject(
                Vault::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: Vault(
                id = vaultId,
                pairingCodeHash = codeHash,
                user1Uid = uid,
                user1Name = displayName.ifBlank { "Mate 1" },
                memberUids = listOf(uid),
                status = "waiting"
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Claims Slot 2 in an existing waiting Vault using the one-time secret pairing code,
     * transitioning `status` to `"locked"` permanently so NO 3rd party can ever join or read.
     */
    suspend fun joinVaultWithPairingSecret(
        pairingSecret: String,
        displayName: String,
        photoUrl: String? = null
    ): Result<Vault> {
        val uid = requireUserId()
        val vaultId = E2EECrypto.deriveVaultId(pairingSecret)
        val expectedHash = E2EECrypto.derivePairingHash(pairingSecret)
        val docRef = db.collection("vaults").document(vaultId)

        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                return Result.failure(IllegalArgumentException("Invalid pairing code or Vault already locked to 2 users."))
            }
            val existing = snapshot.toObject(Vault::class.java)
                ?: return Result.failure(IllegalStateException("Unable to read Vault metadata."))

            if (existing.pairingCodeHash != expectedHash) {
                return Result.failure(SecurityException("Cryptographic pairing hash mismatch."))
            }

            // If user is already one of the 2 paired mates (e.g., reinstalling on new phone), simply unlock locally!
            if (uid == existing.user1Uid || uid == existing.user2Uid) {
                return Result.success(existing)
            }

            if (existing.status == "locked" || existing.user2Uid.isNotBlank()) {
                return Result.failure(SecurityException("Strict 2-User Limit Reached: This vault is permanently locked."))
            }

            val updates = mutableMapOf<String, Any>(
                "user2Uid" to uid,
                "user2Name" to displayName.ifBlank { "Mate 2" }.take(80),
                "memberUids" to listOf(existing.user1Uid, uid),
                "status" to "locked",
                "user2Online" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!photoUrl.isNullOrBlank()) {
                updates["user2Photo"] = photoUrl.take(500)
            }
            docRef.update(updates).await()

            val updated = docRef.get().await().toObject(
                Vault::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: existing.copy(
                user2Uid = uid,
                user2Name = displayName.ifBlank { "Mate 2" },
                memberUids = listOf(existing.user1Uid, uid),
                status = "locked"
            )
            Result.success(updated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(
                SecurityException("Access Denied: Vault does not exist or is strictly locked to 2 users.", e)
            )
        }
    }

    /**
     * Updates real-time typing or online presence indicator for the current mate.
     */
    suspend fun updatePresenceOrTyping(
        vault: Vault,
        isTyping: Boolean? = null,
        isOnline: Boolean? = null
    ) {
        val uid = requireUserId()
        if (vault.id.isBlank() || uid !in vault.memberUids) return
        val isUser1 = uid == vault.user1Uid
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isTyping != null) {
            updates[if (isUser1) "user1Typing" else "user2Typing"] = isTyping
        }
        if (isOnline != null) {
            updates[if (isUser1) "user1Online" else "user2Online"] = isOnline
        }
        val docRef = db.collection("vaults").document(vault.id)
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    /**
     * Observes end-to-end encrypted real-time messages in the vault.
     */
    fun observeMessages(vaultId: String, userId: String = requireUserId()): Flow<List<ChatMessage>> = flow {
        val path = "vaults/$vaultId/messages"
        emitAll(
            db.collection("vaults").document(vaultId).collection("messages")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    /**
     * Uploads multi-megabyte HD photos, animated GIFs, or HD MP4 videos by splitting raw bytes
     * into <= 480 KB slices, encrypting each slice with AES-256-GCM + unique IV, and storing
     * them in `/vaults/{vaultId}/media_chunks/{mediaGroupId}_c{index}`.
     */
    suspend fun uploadEncryptedMediaChunks(
        vault: Vault,
        rawBytes: ByteArray,
        secretKey: SecretKey,
        onProgress: ((Float) -> Unit)? = null
    ): Result<Pair<String, Int>> {
        val uid = requireUserId()
        if (rawBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Media payload is empty."))
        }
        val chunks = rawBytes.asList().chunked(RAW_BYTES_PER_CHUNK).map { it.toByteArray() }
        if (chunks.size > MAX_MEDIA_CHUNKS) {
            return Result.failure(
                IllegalArgumentException("HD media exceeds the 15 MB encrypted vault transfer limit. Select a shorter clip.")
            )
        }
        val mediaGroupId = "mg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val totalChunks = chunks.size

        return try {
            for ((index, slice) in chunks.withIndex()) {
                val encChunk = E2EECrypto.encryptBytes(slice, secretKey)
                val chunkDocId = "${mediaGroupId}_c$index"
                val chunkRef = db.collection("vaults")
                    .document(vault.id)
                    .collection("media_chunks")
                    .document(chunkDocId)

                val chunkPayload = mapOf(
                    "vaultId" to vault.id,
                    "mediaGroupId" to mediaGroupId,
                    "senderUid" to uid,
                    "memberUids" to vault.memberUids,
                    "chunkIndex" to index.toLong(),
                    "totalChunks" to totalChunks.toLong(),
                    "cipherChunk" to encChunk.cipherText,
                    "iv" to encChunk.iv,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                try {
                    chunkRef.set(chunkPayload).await()
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        handleFirestoreError(e, OperationType.CREATE, chunkRef.path)
                    }
                    throw e
                }
                onProgress?.invoke((index + 1).toFloat() / totalChunks.toFloat())
            }
            Result.success(mediaGroupId to totalChunks)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Downloads and decrypts all AES-256-GCM chunks for an HD image, animated GIF, or HD video,
     * reassembling the exact binary bytes on-device.
     */
    suspend fun downloadAndDecryptMediaBytes(
        vaultId: String,
        mediaGroupId: String,
        totalChunks: Int,
        secretKey: SecretKey
    ): Result<ByteArray> = coroutineScope {
        requireUserId()
        val safeTotal = totalChunks.coerceIn(1, MAX_MEDIA_CHUNKS)
        try {
            val deferredSlices = (0 until safeTotal).map { index ->
                async {
                    val chunkDocId = "${mediaGroupId}_c$index"
                    val chunkRef = db.collection("vaults")
                        .document(vaultId)
                        .collection("media_chunks")
                        .document(chunkDocId)
                    val snap = try {
                        chunkRef.get().await()
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            handleFirestoreError(e, OperationType.GET, chunkRef.path)
                        }
                        throw e
                    }
                    val chunkObj = snap.toObject(MediaChunk::class.java)
                        ?: throw IllegalStateException("Missing encrypted chunk $index for $mediaGroupId")
                    E2EECrypto.decryptBytes(chunkObj.cipherChunk, chunkObj.iv, secretKey)
                        ?: throw SecurityException("Failed to decrypt HD media chunk $index")
                }
            }
            val decryptedSlices = deferredSlices.awaitAll()
            val out = ByteArrayOutputStream()
            for (slice in decryptedSlices) {
                out.write(slice)
            }
            Result.success(out.toByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sends an AES-256-GCM encrypted text, edited/HD photo, animated GIF, or HD video message to the vault.
     */
    suspend fun sendEncryptedMessage(
        vault: Vault,
        senderName: String,
        plainText: String,
        secretKey: SecretKey,
        imageBase64: String? = null,
        messageType: String? = null,
        isHd: Boolean = false,
        rawMediaBytes: ByteArray? = null,
        mediaDurationMs: Long? = null,
        replyToId: String? = null,
        replyPlainPreview: String? = null,
        onUploadProgress: ((Float) -> Unit)? = null
    ): Result<String> {
        val uid = requireUserId()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "")}"
        val msgRef = db.collection("vaults").document(vault.id).collection("messages").document(msgId)

        val resolvedType = messageType ?: if (!imageBase64.isNullOrBlank() || rawMediaBytes != null) "image" else "text"
        val defaultLabel = when (resolvedType) {
            "video" -> if (isHd) "🎬 HD Video" else "🎬 Video"
            "gif" -> "🎞️ GIF"
            "image" -> if (isHd) "📷 HD Photo" else "📷 Photo"
            else -> ""
        }

        return try {
            var uploadedGroupId: String? = null
            var uploadedTotalChunks: Int? = null
            if (rawMediaBytes != null && rawMediaBytes.isNotEmpty()) {
                val chunkRes = uploadEncryptedMediaChunks(
                    vault = vault,
                    rawBytes = rawMediaBytes,
                    secretKey = secretKey,
                    onProgress = onUploadProgress
                ).getOrThrow()
                uploadedGroupId = chunkRes.first
                uploadedTotalChunks = chunkRes.second
            }

            val effectiveText = plainText.ifBlank { defaultLabel }
            val encryptedText = E2EECrypto.encrypt(effectiveText, secretKey)
            val payload = mutableMapOf<String, Any>(
                "vaultId" to vault.id,
                "senderUid" to uid,
                "senderName" to senderName.ifBlank { "Mate" }.take(80),
                "memberUids" to vault.memberUids,
                "messageType" to resolvedType,
                "cipherText" to encryptedText.cipherText,
                "iv" to encryptedText.iv,
                "status" to if (vault.isPartnerOnline(uid)) "delivered" else "sent",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (!imageBase64.isNullOrBlank()) {
                val encMedia = E2EECrypto.encrypt(imageBase64, secretKey)
                payload["mediaCipherText"] = encMedia.cipherText
                payload["mediaIv"] = encMedia.iv
            }
            if (isHd) {
                payload["isHd"] = true
            }
            if (!uploadedGroupId.isNullOrBlank() && uploadedTotalChunks != null) {
                payload["mediaGroupId"] = uploadedGroupId
                payload["totalChunks"] = uploadedTotalChunks.toLong()
            }
            if (mediaDurationMs != null && mediaDurationMs >= 0L) {
                payload["mediaDurationMs"] = mediaDurationMs
            }
            if (!replyToId.isNullOrBlank() && !replyPlainPreview.isNullOrBlank()) {
                val encReply = E2EECrypto.encrypt(replyPlainPreview.take(120), secretKey)
                payload["replyToId"] = replyToId
                payload["replyPreviewCipher"] = "${encReply.iv}:${encReply.cipherText}"
            }

            msgRef.set(payload).await()

            // Update Vault's encrypted last message preview
            val previewEnc = E2EECrypto.encrypt(effectiveText.take(80), secretKey)
            db.collection("vaults").document(vault.id).update(
                mapOf(
                    "lastMessagePreview" to previewEnc.cipherText,
                    "lastMessageIv" to previewEnc.iv,
                    if (uid == vault.user1Uid) "user1Typing" to false else "user2Typing" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    /**
     * Marks incoming unread messages as `"read"` (triggering WhatsApp-style Blue Double Ticks).
     */
    suspend fun markMessagesAsRead(vaultId: String, unreadMessages: List<ChatMessage>) {
        val uid = requireUserId()
        val toMark = unreadMessages.filter { it.senderUid != uid && it.status != "read" && it.id.isNotBlank() }
        for (msg in toMark) {
            val docRef = db.collection("vaults").document(vaultId).collection("messages").document(msg.id)
            try {
                docRef.update(
                    mapOf(
                        "status" to "read",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            }
        }
    }

    suspend fun deleteOwnMessage(vaultId: String, messageId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("vaults").document(vaultId).collection("messages").document(messageId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Observes active 24-hour Status Stories and automatically deletes any story older than 24 hours.
     */
    fun observeActiveStatuses(vaultId: String, userId: String = requireUserId()): Flow<List<StatusStory>> = flow {
        val path = "vaults/$vaultId/statuses"
        emitAll(
            db.collection("vaults").document(vaultId).collection("statuses")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    val nowMillis = System.currentTimeMillis()
                    val all = snapshot.toObjects(
                        StatusStory::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    val active = mutableListOf<StatusStory>()
                    for (story in all) {
                        if (story.expiresAtMillis in 1..nowMillis && story.id.isNotBlank()) {
                            // Auto-delete expired >24h story in background
                            db.collection("vaults").document(vaultId)
                                .collection("statuses").document(story.id)
                                .delete()
                        } else {
                            active.add(story)
                        }
                    }
                    active.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    /**
     * Uploads an E2EE Status Story strictly capped at 30 seconds maximum duration and 24-hour TTL.
     */
    suspend fun postEncryptedStatus(
        vault: Vault,
        authorName: String,
        mediaType: String,
        caption: String,
        durationSeconds: Int,
        backgroundHex: String,
        secretKey: SecretKey,
        imageBase64: String? = null
    ): Result<String> {
        val uid = requireUserId()
        val safeDuration = durationSeconds.coerceIn(1, 30)
        val expiresAtMillis = System.currentTimeMillis() + 24L * 60L * 60L * 1000L // Strictly 24h TTL
        val statusId = "st_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("vaults").document(vault.id).collection("statuses").document(statusId)

        return try {
            val encCaption = E2EECrypto.encrypt(caption.ifBlank { "✨ Moments" }, secretKey)
            val payload = mutableMapOf<String, Any>(
                "vaultId" to vault.id,
                "authorUid" to uid,
                "authorName" to authorName.ifBlank { "Mate" }.take(80),
                "memberUids" to vault.memberUids,
                "mediaType" to mediaType,
                "captionCipher" to encCaption.cipherText,
                "captionIv" to encCaption.iv,
                "backgroundHex" to backgroundHex,
                "durationSeconds" to safeDuration.toLong(),
                "expiresAtMillis" to expiresAtMillis,
                "viewedByUids" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!imageBase64.isNullOrBlank()) {
                val encMedia = E2EECrypto.encrypt(imageBase64, secretKey)
                payload["mediaCipher"] = encMedia.cipherText
                payload["mediaIv"] = encMedia.iv
            }
            docRef.set(payload).await()
            Result.success(statusId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markStatusViewed(vaultId: String, story: StatusStory) {
        val uid = requireUserId()
        if (story.id.isBlank() || uid in story.viewedByUids) return
        val updatedViewers = (story.viewedByUids + uid).distinct().take(2)
        val docRef = db.collection("vaults").document(vaultId).collection("statuses").document(story.id)
        try {
            docRef.update(
                mapOf(
                    "viewedByUids" to updatedViewers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    suspend fun deleteStatus(vaultId: String, statusId: String) {
        requireUserId()
        val docRef = db.collection("vaults").document(vaultId).collection("statuses").document(statusId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
        }
    }

    /**
     * Observes WebRTC call sessions & call history for the 2 paired mates.
     */
    fun observeCalls(vaultId: String, userId: String = requireUserId()): Flow<List<CallSession>> = flow {
        val path = "vaults/$vaultId/calls"
        emitAll(
            db.collection("vaults").document(vaultId).collection("calls")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    /**
     * Starts an E2EE WebRTC audio or video call session with an encrypted SDP offer.
     */
    suspend fun initiateWebRtcCall(
        vault: Vault,
        callerName: String,
        callType: String,
        encryptedSdpOffer: String
    ): Result<String> {
        val uid = requireUserId()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("vaults").document(vault.id).collection("calls").document(callId)
        val partnerUid = vault.getPartnerUid(uid)
        val partnerName = vault.getPartnerName(uid)

        return try {
            val payload = mapOf(
                "vaultId" to vault.id,
                "callerUid" to uid,
                "callerName" to callerName.ifBlank { "Mate" }.take(80),
                "calleeUid" to partnerUid,
                "calleeName" to partnerName.take(80),
                "memberUids" to vault.memberUids,
                "callType" to callType,
                "state" to "ringing",
                "sdpOffer" to encryptedSdpOffer,
                "durationSeconds" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Updates WebRTC call signaling state (SDP answer, ICE candidates, connected, ended, declined).
     */
    suspend fun updateCallSignaling(
        vaultId: String,
        callId: String,
        newState: String? = null,
        encryptedSdpOffer: String? = null,
        encryptedSdpAnswer: String? = null,
        encryptedCallerCandidates: String? = null,
        encryptedCalleeCandidates: String? = null,
        durationSeconds: Long? = null
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("vaults").document(vaultId).collection("calls").document(callId)
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newState != null) updates["state"] = newState
            if (encryptedSdpOffer != null) updates["sdpOffer"] = encryptedSdpOffer
            if (encryptedSdpAnswer != null) updates["sdpAnswer"] = encryptedSdpAnswer
            if (encryptedCallerCandidates != null) updates["callerCandidatesJson"] = encryptedCallerCandidates
            if (encryptedCalleeCandidates != null) updates["calleeCandidatesJson"] = encryptedCalleeCandidates
            if (durationSeconds != null) updates["durationSeconds"] = durationSeconds
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        // 480 KB raw binary -> ~640 KB Base64 AES-GCM ciphertext (< 750,000 rule limit & < 1 MiB Firestore doc cap)
        const val RAW_BYTES_PER_CHUNK = 480 * 1024
        const val MAX_MEDIA_CHUNKS = 32
    }
}

