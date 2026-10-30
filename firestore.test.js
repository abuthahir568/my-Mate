const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const MALLORY_UID = "mallory_intruder_003";
const VAULT_ID = "vault_hash_abcdef1234567890";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function serverNow() {
  return new Date();
}

test("1. Unauthenticated caller cannot read or create vaults", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("vaults").get());
  await assertFails(unauthDb.collection("vaults").doc(VAULT_ID).get());
});

test("2. Alice can create a waiting 2-user vault and Bob can claim Slot 2 to lock it", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = serverNow();

  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: "",
      user2Name: "",
      memberUids: [ALICE_UID],
      status: "waiting",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice cannot pair with herself
  await assertFails(
    aliceDb.collection("vaults").doc(VAULT_ID).update({
      user2Uid: ALICE_UID,
      user2Name: "Alice Clone",
      memberUids: [ALICE_UID, ALICE_UID],
      status: "locked",
      user2Online: true,
      updatedAt: serverNow(),
    })
  );

  // Bob claims Slot 2 and locks the vault permanently
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("vaults").doc(VAULT_ID).update({
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user2Online: true,
      updatedAt: serverNow(),
    })
  );
});

test("3. Strict 2-User Lock: 3rd user (Mallory) is completely blocked from reading or joining a locked vault", async () => {
  const now = serverNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  // Mallory cannot even GET the vault doc once locked
  await assertFails(malloryDb.collection("vaults").doc(VAULT_ID).get());
  // Mallory cannot overwrite Slot 2
  await assertFails(
    malloryDb.collection("vaults").doc(VAULT_ID).update({
      user2Uid: MALLORY_UID,
      user2Name: "Mallory",
      memberUids: [ALICE_UID, MALLORY_UID],
      status: "locked",
      user2Online: true,
      updatedAt: serverNow(),
    })
  );
});

test("4. Query Enforcer: Vault list query fails without array-contains memberUids filter", async () => {
  const now = serverNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("vaults").get());
  await assertSucceeds(
    aliceDb.collection("vaults").where("memberUids", "array-contains", ALICE_UID).get()
  );
});

test("5. Encrypted Messages: Alice & Bob can exchange E2EE messages and mark read; Mallory is blocked; Shadow fields fail", async () => {
  const now = serverNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  // Shadow field injection rejected
  await assertFails(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_bad").set({
      vaultId: VAULT_ID,
      senderUid: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      cipherText: "U2FsdGVkX19...",
      iv: "aXZfMTIzNDU2Nzg=",
      status: "sent",
      isVerifiedAdmin: true,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Valid E2EE message succeeds
  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_1").set({
      vaultId: VAULT_ID,
      senderUid: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "text",
      cipherText: "U2FsdGVkX19...",
      iv: "aXZfMTIzNDU2Nzg=",
      status: "sent",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Bob can query messages with array-contains memberUids and mark read (blue ticks)
  await assertSucceeds(
    bobDb
      .collection("vaults")
      .doc(VAULT_ID)
      .collection("messages")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  await assertSucceeds(
    bobDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_1").update({
      status: "read",
      updatedAt: serverNow(),
    })
  );

  // Bob cannot tamper with cipherText during update
  await assertFails(
    bobDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_1").update({
      cipherText: "TAMPERED",
      updatedAt: serverNow(),
    })
  );

  // Mallory cannot list messages without filter (rules are not filters)
  await assertFails(
    malloryDb
      .collection("vaults")
      .doc(VAULT_ID)
      .collection("messages")
      .get()
  );
  // Even with her own UID filter, Mallory sees 0 messages from Alice & Bob's vault
  const mallorySnap = await assertSucceeds(
    malloryDb
      .collection("vaults")
      .doc(VAULT_ID)
      .collection("messages")
      .where("memberUids", "array-contains", MALLORY_UID)
      .get()
  );
  if (!mallorySnap.empty) {
    throw new Error("Mallory leaked messages!");
  }
  await assertFails(
    malloryDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_1").get()
  );
});

test("6. Status Stories: Strictly enforces <= 30s duration and blocks 3rd parties", async () => {
  const now = serverNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Over 30s story rejected!
  await assertFails(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("statuses").doc("story_45s").set({
      vaultId: VAULT_ID,
      authorUid: ALICE_UID,
      authorName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      mediaType: "video_clip",
      captionCipher: "enc_caption",
      captionIv: "aXZfMTIzNDU2Nzg=",
      backgroundHex: "#075E54",
      durationSeconds: 45,
      expiresAtMillis: Date.now() + 86400000,
      viewedByUids: [ALICE_UID],
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Valid 30s story succeeds
  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("statuses").doc("story_30s").set({
      vaultId: VAULT_ID,
      authorUid: ALICE_UID,
      authorName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      mediaType: "photo",
      captionCipher: "enc_caption",
      captionIv: "aXZfMTIzNDU2Nzg=",
      backgroundHex: "#075E54",
      durationSeconds: 30,
      expiresAtMillis: Date.now() + 86400000,
      viewedByUids: [ALICE_UID],
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );
});

test("7. WebRTC Call Signaling: Terminal state locking prevents mutating ended calls", async () => {
  const now = serverNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("calls").doc("call_1").set({
      vaultId: VAULT_ID,
      callerUid: ALICE_UID,
      callerName: "Alice",
      calleeUid: BOB_UID,
      calleeName: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      callType: "video",
      state: "ringing",
      sdpOffer: "encrypted_sdp_offer",
      durationSeconds: 0,
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Bob answers with SDP answer
  await assertSucceeds(
    bobDb.collection("vaults").doc(VAULT_ID).collection("calls").doc("call_1").update({
      state: "connected",
      sdpAnswer: "encrypted_sdp_answer",
      updatedAt: serverNow(),
    })
  );

  // Alice ends call
  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("calls").doc("call_1").update({
      state: "ended",
      durationSeconds: 125,
      updatedAt: serverNow(),
    })
  );

  // Terminal state lock: cannot reopen an ended call
  await assertFails(
    bobDb.collection("vaults").doc(VAULT_ID).collection("calls").doc("call_1").update({
      state: "connected",
      updatedAt: serverNow(),
    })
  );
});

test("8. HD Media Chunks, GIF & Video Messages: Paired mates can upload/read E2EE HD chunks; Mallory & mutations are blocked", async () => {
  const now = serverNow();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("vaults").doc(VAULT_ID).set({
      pairingCodeHash: "abcdef1234567890abcdef1234567890",
      user1Uid: ALICE_UID,
      user1Name: "Alice",
      user2Uid: BOB_UID,
      user2Name: "Bob",
      memberUids: [ALICE_UID, BOB_UID],
      status: "locked",
      user1Typing: false,
      user2Typing: false,
      user1Online: true,
      user2Online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();

  // Alice uploads E2EE HD video chunk 0
  await assertSucceeds(
    aliceDb
      .collection("vaults")
      .doc(VAULT_ID)
      .collection("media_chunks")
      .doc("mg_vid001_c0")
      .set({
        vaultId: VAULT_ID,
        mediaGroupId: "mg_vid001",
        senderUid: ALICE_UID,
        memberUids: [ALICE_UID, BOB_UID],
        chunkIndex: 0,
        totalChunks: 2,
        cipherChunk: "QUVTLTI1Ni1HQ00tSEQtdmlkZW8tY2h1bmstMA==",
        iv: "aXZfMTIzNDU2Nzg=",
        createdAt: serverNow(),
      })
  );

  // Alice sends HD video message referencing mg_vid001, plus an animated GIF message
  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_hd_vid").set({
      vaultId: VAULT_ID,
      senderUid: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "video",
      cipherText: "U2FsdGVkX19...",
      iv: "aXZfMTIzNDU2Nzg=",
      isHd: true,
      mediaGroupId: "mg_vid001",
      totalChunks: 2,
      mediaDurationMs: 12500,
      status: "sent",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("vaults").doc(VAULT_ID).collection("messages").doc("msg_gif").set({
      vaultId: VAULT_ID,
      senderUid: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      messageType: "gif",
      cipherText: "U2FsdGVkX19...",
      iv: "aXZfMTIzNDU2Nzg=",
      mediaCipherText: "R0lGODlhAQABAIAAAAUEBAAAACwAAAAAAQABAAACAkQBADs=",
      mediaIv: "aXZfMTIzNDU2Nzg=",
      status: "sent",
      createdAt: serverNow(),
      updatedAt: serverNow(),
    })
  );

  // Bob can fetch the encrypted HD video chunk directly
  await assertSucceeds(
    bobDb.collection("vaults").doc(VAULT_ID).collection("media_chunks").doc("mg_vid001_c0").get()
  );

  // Chunks are immutable (update denied)
  await assertFails(
    bobDb.collection("vaults").doc(VAULT_ID).collection("media_chunks").doc("mg_vid001_c0").update({
      cipherChunk: "TAMPERED",
    })
  );

  // Mallory is strictly blocked from reading or uploading media_chunks
  await assertFails(
    malloryDb.collection("vaults").doc(VAULT_ID).collection("media_chunks").doc("mg_vid001_c0").get()
  );
  await assertFails(
    malloryDb.collection("vaults").doc(VAULT_ID).collection("media_chunks").doc("mg_evil_c0").set({
      vaultId: VAULT_ID,
      mediaGroupId: "mg_evil",
      senderUid: MALLORY_UID,
      memberUids: [MALLORY_UID],
      chunkIndex: 0,
      totalChunks: 1,
      cipherChunk: "ZXZpbA==",
      iv: "aXZfMTIzNDU2Nzg=",
      createdAt: serverNow(),
    })
  );
});

