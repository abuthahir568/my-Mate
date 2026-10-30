# Security Specification: My Mate (Strict 2-User Encrypted Sanctuary)

## 1. Data Invariants
1. **Strict 2-User Vault Capacity**: A `vaults/{vaultId}` document stores `user1Uid` (immutable creator) and optional `user2Uid`. Once `user2Uid` is claimed (`user2Uid != ""`), `status` transitions to `'locked'` and **no third user can ever join, read, or write** to the vault or any of its subcollections.
2. **Denormalized Zero-Trust Subcollections**: Every document in `messages`, `statuses`, and `calls` contains `memberUids` (a strictly bounded 2-element list `[user1Uid, user2Uid]`) and `senderUid == request.auth.uid`, ensuring `allow get, list` never leaks data across vaults or to third parties without incurring `get()` O(n) costs on `list`.
3. **Parent Gate on Creation**: Creating a message, status, or call requires `get(/databases/$(database)/documents/vaults/$(vaultId))` confirming the caller is `user1Uid` or `user2Uid` in that active vault.
4. **End-to-End Encrypted Payloads**: Message bodies, media thumbnails, and status captions are stored as AES-256-GCM ciphertexts (`cipherText`, `iv`) bounded in size to prevent Denial-of-Wallet resource exhaustion.
5. **Ephemeral 24h & 30s Status Bounds**: Statuses enforce `durationSeconds >= 1 && durationSeconds <= 30` and `expiresAtMillis > 0`.
6. **Immutable Origins**: `createdAt`, `senderUid`, and `user1Uid` are strictly immutable across all updates.

## 2. The Dirty Dozen Payloads
1. **Unauthenticated Vault Scrape**: `unauthDb.collection("vaults").get()` -> Rejected (`request.auth != null`).
2. **3rd User Intrusion on Locked Vault**: `intruderDb` updates `vaults/{id}` to overwrite `user2Uid` when `existing().user2Uid != ""` -> Rejected (slot already claimed).
3. **Self-Pairing Spoof**: `aliceDb` updates `vaults/{id}` setting `user2Uid = ALICE_UID` -> Rejected (`user2Uid != existing().user1Uid`).
4. **Shadow Field Injection**: `aliceDb` creates message with `{ ..., isAdmin: true }` -> Rejected (`keys().hasOnly(...)`).
5. **Identity Spoofing in Message**: `aliceDb` creates message with `senderUid: BOB_UID` -> Rejected (`incoming().senderUid == request.auth.uid`).
6. **Cross-User Message Read by 3rd Party**: `malloryDb` queries `vaults/{id}/messages` -> Rejected (`request.auth.uid in resource.data.memberUids`).
7. **Unfiltered Query Scraping**: `aliceDb` runs `collection("vaults").get()` without `whereArrayContains("memberUids", ALICE_UID)` -> Rejected (`rules are not filters`).
8. **1MB Payload Poisoning**: `aliceDb` sends 500KB `cipherText` in a text message -> Rejected (`cipherText.size() <= 350000`).
9. **Status Over-Duration (>30s)**: `aliceDb` uploads video status with `durationSeconds: 45` -> Rejected (`durationSeconds >= 1 && durationSeconds <= 30`).
10. **Immutable Timestamp Tampering**: `bobDb` marks message read while mutating `createdAt` or `cipherText` -> Rejected (`affectedKeys().hasOnly(['status', 'readAt', 'updatedAt'])` and `createdAt == existing().createdAt`).
11. **ID Poisoning**: Document ID with slashes/spaces or >128 chars -> Rejected (`isValidId()`).
12. **WebRTC Call Hijack**: `malloryDb` writes SDP answer to `vaults/{id}/calls/{callId}` -> Rejected (not in `memberUids`).
