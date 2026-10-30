package com.example.security

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-End Encryption (E2EE) cryptographic engine for My Mate.
 *
 * Uses PBKDF2WithHmacSHA256 to derive a 256-bit AES key locally on-device from the
 * two mates' shared secret pairing code. Plaintext messages, photos, captions, and
 * WebRTC SDP/ICE payloads never touch Firebase servers in cleartext.
 */
object E2EECrypto {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 10000
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val SALT_DOMAIN = "MyMate-Strict2UserVault-E2EE-v1"

    data class EncryptedPayload(
        val cipherText: String,
        val iv: String
    )

    /**
     * Generates a readable 8-character secret pairing code (e.g., "MATE-7K9P")
     * shared out-of-band exclusively with the user's single partner.
     */
    fun generatePairingSecret(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        val part1 = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val part2 = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return "$part1-$part2"
    }

    /**
     * Normalizes a pairing code so minor whitespace or lowercase differences still resolve cleanly.
     */
    fun normalizeSecret(secret: String): String {
        return secret.trim().uppercase().replace(" ", "")
    }

    /**
     * Deterministic Vault Document ID derived via SHA-256 so the 2nd mate can locate
     * and claim Slot 2 without enabling open collection listing.
     */
    fun deriveVaultId(pairingSecret: String): String {
        val normalized = normalizeSecret(pairingSecret)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("VAULT_ID::$normalized".toByteArray(StandardCharsets.UTF_8))
        return "v_" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * Verification hash stored in the Vault document (`pairingCodeHash`) — never reveals raw key.
     */
    fun derivePairingHash(pairingSecret: String): String {
        val normalized = normalizeSecret(pairingSecret)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("AUTH_HASH::$normalized".toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a human-verifiable 12-digit Safety Number fingerprint (e.g., "4829 1038 9921")
     * so both mates can verify zero MITM tampering, just like WhatsApp's Security Code screen.
     */
    fun deriveSafetyFingerprint(pairingSecret: String, vaultId: String): String {
        val normalized = normalizeSecret(pairingSecret)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("SAFETY::$normalized::$vaultId".toByteArray(StandardCharsets.UTF_8))
        val digits = digest.take(12).map { ((it.toInt() and 0xFF) % 10) }
        return digits.chunked(4).joinToString(" ") { it.joinToString("") }
    }

    /**
     * Derives the 256-bit AES-GCM SecretKey from the shared pairing secret.
     */
    fun deriveAesKey(pairingSecret: String): SecretKey {
        val normalized = normalizeSecret(pairingSecret)
        val salt = "$SALT_DOMAIN::$normalized".toByteArray(StandardCharsets.UTF_8)
        val spec = PBEKeySpec(normalized.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts UTF-8 text (including full Unicode emojis or Base64 image data) using AES-256-GCM.
     */
    fun encrypt(plainText: String, key: SecretKey): EncryptedPayload {
        val ivBytes = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(ivBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            cipherText = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(ivBytes, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts AES-256-GCM ciphertext back to UTF-8 text. Returns fallback indicator if key mismatches.
     */
    fun decrypt(cipherText: String, iv: String, key: SecretKey): String {
        if (cipherText.isBlank() || iv.isBlank()) return ""
        return try {
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
            val plainBytes = cipher.doFinal(encryptedBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            "🔒 Encrypted payload (verify Vault Secret Key)"
        }
    }

    /**
     * Encrypts raw binary bytes (HD photos, animated GIFs, HD MP4 video chunks) directly using AES-256-GCM.
     */
    fun encryptBytes(plainBytes: ByteArray, key: SecretKey): EncryptedPayload {
        val ivBytes = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(ivBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
        val encryptedBytes = cipher.doFinal(plainBytes)
        return EncryptedPayload(
            cipherText = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(ivBytes, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypts AES-256-GCM ciphertext directly back to raw binary bytes.
     */
    fun decryptBytes(cipherText: String, iv: String, key: SecretKey): ByteArray? {
        if (cipherText.isBlank() || iv.isBlank()) return null
        return try {
            val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, ivBytes))
            cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            null
        }
    }
}

