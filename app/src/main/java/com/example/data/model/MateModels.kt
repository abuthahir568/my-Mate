package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Vault(
    @DocumentId val id: String = "",
    val pairingCodeHash: String = "",
    val user1Uid: String = "",
    val user1Name: String = "",
    val user1Photo: String? = null,
    val user2Uid: String = "",
    val user2Name: String = "",
    val user2Photo: String? = null,
    val memberUids: List<String> = emptyList(),
    val status: String = "waiting", // "waiting" or "locked"
    val user1Typing: Boolean = false,
    val user2Typing: Boolean = false,
    val user1Online: Boolean = false,
    val user2Online: Boolean = false,
    val lastMessagePreview: String? = null,
    val lastMessageIv: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isLockedTwoUsers(): Boolean = status == "locked" && user2Uid.isNotBlank()

    fun getPartnerUid(myUid: String): String =
        if (myUid == user1Uid) user2Uid else user1Uid

    fun getPartnerName(myUid: String): String {
        val raw = if (myUid == user1Uid) user2Name else user1Name
        return raw.ifBlank { "Awaiting Your Mate..." }
    }

    fun getPartnerPhoto(myUid: String): String? =
        if (myUid == user1Uid) user2Photo else user1Photo

    fun isPartnerTyping(myUid: String): Boolean =
        if (myUid == user1Uid) user2Typing else user1Typing

    fun isPartnerOnline(myUid: String): Boolean =
        if (myUid == user1Uid) user2Online else user1Online
}

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val vaultId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = "text", // "text" | "image" | "gif" | "video"
    val cipherText: String = "",
    val iv: String = "",
    val mediaCipherText: String? = null,
    val mediaIv: String? = null,
    @get:PropertyName("isHd") val isHd: Boolean = false,
    val mediaGroupId: String? = null,
    val totalChunks: Long? = null,
    val mediaDurationMs: Long? = null,
    val replyToId: String? = null,
    val replyPreviewCipher: String? = null,
    val status: String = "sent", // "sent" | "delivered" | "read"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MediaChunk(
    @DocumentId val id: String = "",
    val vaultId: String = "",
    val mediaGroupId: String = "",
    val senderUid: String = "",
    val memberUids: List<String> = emptyList(),
    val chunkIndex: Long = 0L,
    val totalChunks: Long = 1L,
    val cipherChunk: String = "",
    val iv: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StatusStory(
    @DocumentId val id: String = "",
    val vaultId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val memberUids: List<String> = emptyList(),
    val mediaType: String = "text_card", // "photo" | "video_clip" | "text_card"
    val captionCipher: String = "",
    val captionIv: String = "",
    val mediaCipher: String? = null,
    val mediaIv: String? = null,
    val backgroundHex: String = "#075E54",
    val durationSeconds: Long = 15L, // Strictly 1..30 seconds
    val expiresAtMillis: Long = 0L, // Strictly auto-deleted after 24h
    val viewedByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val vaultId: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val calleeUid: String = "",
    val calleeName: String = "",
    val memberUids: List<String> = emptyList(),
    val callType: String = "audio", // "audio" | "video"
    val state: String = "ringing", // "ringing" | "connected" | "ended" | "declined" | "missed"
    val sdpOffer: String? = null,
    val sdpAnswer: String? = null,
    val callerCandidatesJson: String? = null,
    val calleeCandidatesJson: String? = null,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

/**
 * Decrypted UI-layer projections so Compose renders fast without repeated PBKDF2/AES calls.
 */
data class DecryptedMessage(
    val id: String,
    val senderUid: String,
    val senderName: String,
    val messageType: String, // "text" | "image" | "gif" | "video"
    val text: String,
    val imageBase64: String?,
    val isHd: Boolean = false,
    val mediaGroupId: String? = null,
    val totalChunks: Int = 0,
    val mediaDurationMs: Long = 0L,
    val replyToId: String?,
    val replyPreview: String?,
    val status: String,
    val createdAt: Timestamp?
)

data class DecryptedStatus(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val mediaType: String,
    val caption: String,
    val imageBase64: String?,
    val backgroundHex: String,
    val durationSeconds: Int,
    val expiresAtMillis: Long,
    val viewedByUids: List<String>,
    val createdAt: Timestamp?
)
