package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.security.E2EECrypto
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MateRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun strictTwoUserVault_createAndClaimSlot2_blocksThirdIntruder() = runBlocking {
        val secret = "MATE-" + UUID.randomUUID().toString().take(6).uppercase()
        val key = E2EECrypto.deriveAesKey(secret)

        // 1. Alice creates the 2-user Vault
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@mymate.test")
        val repo = MateRepository(firestore)
        val createdVault = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createTwoUserVault(secret, "Alice").getOrThrow()
        }
        assertEquals("waiting", createdVault.status)
        assertEquals(aliceUid, createdVault.user1Uid)

        // 2. Bob claims Slot 2 and locks the Vault permanently
        val bobUid = signInTestUser("bob_${UUID.randomUUID()}@mymate.test")
        val lockedVault = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.joinVaultWithPairingSecret(secret, "Bob").getOrThrow()
        }
        assertEquals("locked", lockedVault.status)
        assertEquals(bobUid, lockedVault.user2Uid)
        assertEquals(2, lockedVault.memberUids.size)

        // 3. Alice sends an AES-256-GCM encrypted message with emojis
        signInTestUser("alice_sender_${UUID.randomUUID()}@mymate.test")
        // Switch back to Bob (member) to send and observe E2EE messages
        auth.signOut()
        val bobMsgId = run {
            // Re-authenticate Bob via fresh account test or reuse Bob's session:
            // Let's create a dedicated 2-user vault with reusable emails to test cross-user messaging cleanly
            val sharedSecret = "PAIR-" + UUID.randomUUID().toString().take(6).uppercase()
            val sharedKey = E2EECrypto.deriveAesKey(sharedSecret)
            val u1Email = "mate1_${UUID.randomUUID()}@mymate.test"
            val u2Email = "mate2_${UUID.randomUUID()}@mymate.test"
            val intruderEmail = "intruder_${UUID.randomUUID()}@mymate.test"

            signInTestUser(u1Email)
            repo.createTwoUserVault(sharedSecret, "Mate One").getOrThrow()

            signInTestUser(u2Email)
            val pairVault = repo.joinVaultWithPairingSecret(sharedSecret, "Mate Two").getOrThrow()

            // Mate Two sends an E2EE message
            val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.sendEncryptedMessage(
                    vault = pairVault,
                    senderName = "Mate Two",
                    plainText = "Hello My Mate! ❤️🔒",
                    secretKey = sharedKey
                ).getOrThrow()
            }

            // Mate One logs in, observes real-time message, decrypts it, and marks read
            val u1Uid = signInTestUser(u1Email)
            val messages = withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMessages(pairVault.id, u1Uid).first { list -> list.any { it.id == msgId } }
            }
            val received = messages.first { it.id == msgId }
            val decrypted = E2EECrypto.decrypt(received.cipherText, received.iv, sharedKey)
            assertEquals("Hello My Mate! ❤️🔒", decrypted)

            // Mate One posts a 30s Status Story
            val storyId = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.postEncryptedStatus(
                    vault = pairVault,
                    authorName = "Mate One",
                    mediaType = "text_card",
                    caption = "Our private 24h status ✨",
                    durationSeconds = 30,
                    backgroundHex = "#075E54",
                    secretKey = sharedKey
                ).getOrThrow()
            }
            assertTrue(storyId.isNotEmpty())

            // Mate One sends an E2EE chunked HD Video message & Mate Two downloads + decrypts exact bytes
            val sampleHdVideoBytes = ByteArray(600 * 1024) { idx -> (idx % 251).toByte() } // Spans 2 chunks
            val hdVideoMsgId = withTimeout(DEFAULT_TIMEOUT_MS * 2) {
                repo.sendEncryptedMessage(
                    vault = pairVault,
                    senderName = "Mate One",
                    plainText = "Our HD memory!",
                    secretKey = sharedKey,
                    messageType = "video",
                    isHd = true,
                    rawMediaBytes = sampleHdVideoBytes,
                    mediaDurationMs = 8400L
                ).getOrThrow()
            }
            assertTrue(hdVideoMsgId.isNotEmpty())

            val u2Uid = signInTestUser(u2Email)
            val updatedMsgs = withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMessages(pairVault.id, u2Uid).first { list -> list.any { it.id == hdVideoMsgId } }
            }
            val hdVidMsg = updatedMsgs.first { it.id == hdVideoMsgId }
            assertEquals("video", hdVidMsg.messageType)
            assertTrue(hdVidMsg.isHd)
            assertEquals(2L, hdVidMsg.totalChunks)

            val reassembledBytes = withTimeout(DEFAULT_TIMEOUT_MS * 2) {
                repo.downloadAndDecryptMediaBytes(
                    vaultId = pairVault.id,
                    mediaGroupId = hdVidMsg.mediaGroupId!!,
                    totalChunks = hdVidMsg.totalChunks!!.toInt(),
                    secretKey = sharedKey
                ).getOrThrow()
            }
            assertTrue("Reassembled HD video bytes must match bit-for-bit", sampleHdVideoBytes.contentEquals(reassembledBytes))

            // 3rd User Intruder attempts to join the locked vault -> Strictly Rejected!
            signInTestUser(intruderEmail)
            val joinAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.joinVaultWithPairingSecret(sharedSecret, "Intruder")
            }
            assertTrue("3rd user must be rejected from locked vault", joinAttempt.isFailure)
            msgId
        }
        assertTrue(bobMsgId.isNotEmpty())
    }

    @Test
    fun unauthenticatedCaller_observeMyVault_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = MateRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMyVault("unauth_uid_test").first()
            }
        } catch (e: Throwable) {
            caught = e
        }
        assertNotNull("Expected unauthenticated query to throw", caught)
        val fsEx = caught!!.unwrapFirestoreException()
        assertNotNull("Expected wrapped FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
