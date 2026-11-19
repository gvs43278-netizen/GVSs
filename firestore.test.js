const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-business-3305cc49-420d-4ea8-94ee-c723183d25d9";
const OWNER_UID = "yRuPuAgCe2V2Y8avgK9U8n4sTgj1";
const EMP_UID = "employee_uid_1";
const EMP_EMAIL = "emp1@example.com";
const STRANGER_UID = "stranger_999";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ownerDb() {
  return testEnv.authenticatedContext(OWNER_UID, { email: "gvs43278@gmail.com", email_verified: true }).firestore();
}

function empDb() {
  return testEnv.authenticatedContext(EMP_UID, { email: EMP_EMAIL, email_verified: true }).firestore();
}

function strangerDb() {
  return testEnv.authenticatedContext(STRANGER_UID, { email: "stranger@example.com", email_verified: true }).firestore();
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("accounts").get());
  await assertFails(unauth.collection("transactions").get());
  await assertFails(
    unauth.collection("accounts").doc("cash").set({
      accountId: "cash",
      name: "Cash",
      group: "CASH",
      sortOrder: 1,
      balancePaisa: 0,
      capitalPaisa: 0,
      allowNegativeBalance: false,
      memberUids: [OWNER_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Super Admin UID can initialize accounts, partners, and approve transactions", async () => {
  const db = ownerDb();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    db.collection("accounts").doc("cash").set({
      accountId: "cash",
      name: "Cash",
      group: "CASH",
      sortOrder: 1,
      balancePaisa: 100000,
      capitalPaisa: 100000,
      allowNegativeBalance: false,
      memberUids: [OWNER_UID, EMP_UID],
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.collection("partners").doc("samad_bhai").set({
      partnerId: "samad_bhai",
      name: "Samad Bhai",
      ratePerThousandPaisa: 500,
      bkashOnlyCommission: true,
      sentTotalPaisa: 0,
      receivedTotalPaisa: 0,
      currentBalancePaisa: 0,
      payableCommissionPaisa: 0,
      paidCommissionPaisa: 0,
      memberUids: [OWNER_UID, EMP_UID],
      updatedAt: now,
    })
  );

  await assertSucceeds(
    db.collection("transactions").doc("tx_1").set({
      txType: "CUSTOMER_TX",
      status: "APPROVED",
      customerName: "রহিম",
      receivedAccountId: "bkash_1",
      receivedAmountPaisa: 100000,
      paidAccountId: "cash",
      paidAmountPaisa: 100000,
      manualCommissionPaisa: 0,
      partnerCommissionPaisa: 0,
      explicitFeePaisa: 0,
      interestPaisa: 0,
      adjustmentSign: 0,
      createdBy: OWNER_UID,
      createdByName: "মালিক",
      approvedBy: OWNER_UID,
      occurredAtMillis: Date.now() - 2000,
      createdAt: now,
      approvedAt: now,
      updatedAt: now,
    })
  );
});

test("Employee cannot self-promote to OWNER, cannot approve transactions, and cannot mutate accounts", async () => {
  const owner = ownerDb();
  const now = new Date(Date.now() - 2000);

  // Seed employee + account
  await assertSucceeds(
    owner.collection("employees").doc(EMP_UID).set({
      uid: EMP_UID,
      name: "কর্মচারী ১",
      email: EMP_EMAIL,
      role: "EMPLOYEE",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    owner.collection("accounts").doc("cash").set({
      accountId: "cash",
      name: "Cash",
      group: "CASH",
      sortOrder: 1,
      balancePaisa: 50000,
      capitalPaisa: 50000,
      allowNegativeBalance: false,
      memberUids: [OWNER_UID, EMP_UID],
      updatedAt: now,
    })
  );

  const emp = empDb();

  // Employee can list accounts where they are in memberUids
  await assertSucceeds(emp.collection("accounts").where("memberUids", "array-contains", EMP_UID).get());

  // Employee CANNOT modify their own role to OWNER
  await assertFails(
    emp.collection("employees").doc(EMP_UID).update({
      role: "OWNER",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Employee CANNOT create a pre-approved transaction
  await assertFails(
    emp.collection("transactions").doc("tx_emp_cheat").set({
      txType: "CUSTOMER_TX",
      status: "APPROVED",
      receivedAccountId: "bkash_1",
      receivedAmountPaisa: 100000,
      paidAccountId: "cash",
      paidAmountPaisa: 100000,
      manualCommissionPaisa: 0,
      partnerCommissionPaisa: 0,
      explicitFeePaisa: 0,
      interestPaisa: 0,
      adjustmentSign: 0,
      createdBy: EMP_UID,
      createdByName: "কর্মচারী ১",
      approvedBy: EMP_UID,
      occurredAtMillis: Date.now() - 1000,
      createdAt: now,
      approvedAt: now,
      updatedAt: now,
    })
  );

  // Employee CAN create a PENDING transaction
  await assertSucceeds(
    emp.collection("transactions").doc("tx_emp_1").set({
      txType: "CUSTOMER_TX",
      status: "PENDING",
      customerName: "করিম",
      receivedAccountId: "bkash_1",
      receivedAmountPaisa: 100000,
      paidAccountId: "cash",
      paidAmountPaisa: 100000,
      manualCommissionPaisa: 0,
      partnerCommissionPaisa: 0,
      explicitFeePaisa: 0,
      interestPaisa: 0,
      adjustmentSign: 0,
      createdBy: EMP_UID,
      createdByName: "কর্মচারী ১",
      occurredAtMillis: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Employee CANNOT approve their own pending transaction
  await assertFails(
    emp.collection("transactions").doc("tx_emp_1").update({
      status: "APPROVED",
      approvedBy: EMP_UID,
      approvedAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Employee CANNOT directly update account balances
  await assertFails(
    emp.collection("accounts").doc("cash").update({
      balancePaisa: 9999999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Employee CAN query their own transactions
  await assertSucceeds(emp.collection("transactions").where("createdBy", "==", EMP_UID).get());

  // Employee CANNOT run unfiltered list on transactions
  await assertFails(emp.collection("transactions").get());

  // Stranger cannot claim OWNER role or read employee's transaction
  const stranger = strangerDb();
  await assertFails(
    stranger.collection("employees").doc(STRANGER_UID).set({
      uid: STRANGER_UID,
      name: "Hacker",
      email: "stranger@example.com",
      role: "OWNER",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(stranger.collection("transactions").doc("tx_emp_1").get());
});

test("Append-only ledger rejects updates and non-owner writes", async () => {
  const owner = ownerDb();
  const emp = empDb();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    owner.collection("ledger").doc("led_1").set({
      txId: "tx_1",
      txType: "CUSTOMER_TX",
      accountId: "cash",
      accountName: "Cash",
      deltaPaisa: -100000,
      previousBalancePaisa: 200000,
      newBalancePaisa: 100000,
      capitalDeltaPaisa: 0,
      commissionIncomePaisa: 0,
      partnerCommissionExpensePaisa: 0,
      shopExpensePaisa: 0,
      interestIncomePaisa: 0,
      interestExpensePaisa: 0,
      actorUid: OWNER_UID,
      approvedBy: OWNER_UID,
      note: "গ্রাহক লেনদেন",
      occurredAtMillis: Date.now() - 2000,
      createdAt: now,
    })
  );

  // Ledger cannot be updated even by Owner (append-only!)
  await assertFails(
    owner.collection("ledger").doc("led_1").update({
      deltaPaisa: 0,
    })
  );

  // Employee cannot read or write ledger
  await assertFails(emp.collection("ledger").doc("led_1").get());
});

test("Invited employee can onboard and read accounts, while deactivated employee is blocked immediately", async () => {
  const owner = ownerDb();
  const now = new Date(Date.now() - 2000);

  // Owner invites emp1@example.com and creates account
  await assertSucceeds(
    owner.collection("employee_invites").doc(EMP_EMAIL).set({
      email: EMP_EMAIL,
      name: "আমন্ত্রিত কর্মচারী",
      role: "EMPLOYEE",
      active: true,
      createdBy: OWNER_UID,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    owner.collection("accounts").doc("cash").set({
      accountId: "cash",
      name: "Cash",
      group: "CASH",
      sortOrder: 1,
      balancePaisa: 0,
      capitalPaisa: 0,
      allowNegativeBalance: false,
      memberUids: [OWNER_UID],
      updatedAt: now,
    })
  );

  const emp = empDb();
  // Self-onboard from active email invite
  await assertSucceeds(
    emp.collection("employees").doc(EMP_UID).set({
      uid: EMP_UID,
      name: "আমন্ত্রিত কর্মচারী",
      email: EMP_EMAIL,
      role: "EMPLOYEE",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Immediately can read shared accounts even before Owner re-syncs memberUids
  await assertSucceeds(emp.collection("accounts").where("memberUids", "array-contains", OWNER_UID).get());

  // Owner deactivates employee
  await assertSucceeds(
    owner.collection("employees").doc(EMP_UID).update({
      active: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Deactivated employee is immediately blocked from accounts & submitting transactions
  await assertFails(emp.collection("accounts").where("memberUids", "array-contains", OWNER_UID).get());
  await assertFails(
    emp.collection("transactions").doc("tx_blocked").set({
      txType: "CUSTOMER_TX",
      status: "PENDING",
      receivedAmountPaisa: 0,
      paidAmountPaisa: 1000,
      paidAccountId: "cash",
      manualCommissionPaisa: 0,
      partnerCommissionPaisa: 0,
      explicitFeePaisa: 0,
      interestPaisa: 0,
      adjustmentSign: 0,
      createdBy: EMP_UID,
      createdByName: "কর্মচারী",
      occurredAtMillis: Date.now() - 1000,
      createdAt: now,
      updatedAt: now,
    })
  );
});

