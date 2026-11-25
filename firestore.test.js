const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_engineer_01";
const BOB_UID = "bob_manager_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validCustomerPayload(uid, overrides = {}) {
  const ts = new Date(Date.now() - 5000);
  return {
    userId: uid,
    name: "Apex Steel Works",
    meterNumber: "MTR-HT-4029",
    tariffPlan: "INDUSTRIAL_HT",
    sanctionedLoadKw: 450.5,
    accountCode: "ACC-8821",
    location: "Sector 4 Industrial Park",
    contactPhone: "+1-555-0142",
    notes: "Primary 11kV incomer",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validReadingPayload(uid, customerId, overrides = {}) {
  const ts = new Date(Date.now() - 3000);
  return {
    userId: uid,
    customerId,
    customerName: "Apex Steel Works",
    meterNumber: "MTR-HT-4029",
    readingDate: "2026-11-24",
    readingTime: "08:30",
    readingEpochMillis: 1795422600000,
    kwh: 18420.5,
    kvarh: 3120.2,
    kvah: 18683.0,
    powerFactor: 0.986,
    notes: "Normal daytime load",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

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

// --- UNAUTHENTICATED ACCESS DENIAL ---

test("Unauthenticated user: denied all reads and writes on users, customers, and readings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("readings").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set(validCustomerPayload(ALICE_UID))
  );
});

// --- USER PROFILE RULES ---

test("Authenticated owner: can create and update own profile, denied cross-user access and list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Rivera",
      email: "alice@voltledger.io",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice R. (Lead)",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // List on /users must be denied
  await assertFails(aliceDb.collection("users").get());
  // Cross-user get/update must be denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hijack" })
  );
});

// --- CUSTOMER CRUD, VALIDATION & ISOLATION ---

test("Authenticated owner: valid Kotlin Customer create, partial update, list, and delete succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const customersCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("customers");

  // Empty list succeeds (no existing() access in allow list)
  await assertSucceeds(customersCol.get());

  // Create without optional fields (mirrors Kotlin .filterValues { it != null })
  const ts = new Date(Date.now() - 5000);
  await assertSucceeds(
    customersCol.doc("cust_minimal").set({
      userId: ALICE_UID,
      name: "Sunrise Bakery",
      meterNumber: "MTR-LT-102",
      tariffPlan: "COMMERCIAL_LT",
      sanctionedLoadKw: 45,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Create with all fields
  await assertSucceeds(
    customersCol.doc("cust_full").set(validCustomerPayload(ALICE_UID))
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    customersCol.doc("cust_full").update({
      name: "Apex Steel Works Unit 2",
      sanctionedLoadKw: 520.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(customersCol.get());
  await assertSucceeds(customersCol.doc("cust_minimal").delete());
});

test("Customer security bounds: reject ghost fields, @DocumentId id key, invalid tariff, future timestamp, and immutable mutations", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const customersCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("customers");

  // Reject redundant id field (DocumentId parity)
  await assertFails(
    customersCol
      .doc("cust_bad_id")
      .set(validCustomerPayload(ALICE_UID, { id: "cust_bad_id" }))
  );

  // Reject ghost field
  await assertFails(
    customersCol
      .doc("cust_ghost")
      .set(validCustomerPayload(ALICE_UID, { isAdmin: true }))
  );

  // Reject invalid tariff plan
  await assertFails(
    customersCol
      .doc("cust_bad_tariff")
      .set(validCustomerPayload(ALICE_UID, { tariffPlan: "FREE_TIER" }))
  );

  // Reject spoofed userId
  await assertFails(
    customersCol
      .doc("cust_spoof")
      .set(validCustomerPayload(BOB_UID))
  );

  // Reject future timestamp
  await assertFails(
    customersCol
      .doc("cust_future")
      .set(
        validCustomerPayload(ALICE_UID, {
          createdAt: new Date(Date.now() + 120000),
        })
      )
  );

  // Create valid customer then test immutable field lock on update
  await assertSucceeds(
    customersCol.doc("cust_lock").set(validCustomerPayload(ALICE_UID))
  );
  await assertFails(
    customersCol.doc("cust_lock").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    customersCol.doc("cust_lock").update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- METER READING CRUD, PARENT EXISTENCE, VALIDATION & CROSS-USER ISOLATION ---

test("MeterReading: requires existing parent customer, validates PF/date/time bounds, allows valid partial update & queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const customersCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("customers");
  const readingsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("readings");

  // Empty list query succeeds
  await assertSucceeds(readingsCol.orderBy("readingEpochMillis", "desc").get());

  // Orphan reading without parent customer MUST fail
  await assertFails(
    readingsCol
      .doc("read_orphan")
      .set(validReadingPayload(ALICE_UID, "missing_cust"))
  );

  // Create parent customer first
  await assertSucceeds(
    customersCol.doc("cust_1").set(validCustomerPayload(ALICE_UID))
  );

  // Valid reading create succeeds
  await assertSucceeds(
    readingsCol
      .doc("read_1")
      .set(validReadingPayload(ALICE_UID, "cust_1"))
  );

  // Valid partial update succeeds
  await assertSucceeds(
    readingsCol.doc("read_1").update({
      kwh: 18455.25,
      kvarh: 3110.0,
      kvah: 18715.5,
      powerFactor: 0.986,
      notes: "Verified against check meter",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Invalid powerFactor > 1.0 fails
  await assertFails(
    readingsCol
      .doc("read_bad_pf")
      .set(validReadingPayload(ALICE_UID, "cust_1", { powerFactor: 1.15 }))
  );

  // Negative kWh fails
  await assertFails(
    readingsCol
      .doc("read_neg_kwh")
      .set(validReadingPayload(ALICE_UID, "cust_1", { kwh: -12.5 }))
  );

  // Invalid date format fails
  await assertFails(
    readingsCol
      .doc("read_bad_date")
      .set(validReadingPayload(ALICE_UID, "cust_1", { readingDate: "24/11/2026" }))
  );

  // Immutable customerId cannot be changed on update
  await assertFails(
    readingsCol.doc("read_1").update({
      customerId: "cust_other",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Cross-user isolation: Bob cannot read, list, update, or delete Alice's customers or readings
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("readings").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("readings")
      .doc("read_1")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("readings")
      .doc("read_1")
      .delete()
  );

  // Owner query matching repository succeeds
  await assertSucceeds(readingsCol.orderBy("readingEpochMillis", "desc").get());
  await assertSucceeds(readingsCol.doc("read_1").delete());
});
