package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AccountingEngine
import com.example.domain.AuditLog
import com.example.domain.BusinessConstants
import com.example.domain.BusinessTransaction
import com.example.domain.Employee
import com.example.domain.EmployeeInvite
import com.example.domain.FinancialAccount
import com.example.domain.LedgerEntry
import com.example.domain.LoanRecord
import com.example.domain.LoanStatus
import com.example.domain.LoanType
import com.example.domain.MoneyFormatter
import com.example.domain.PartnerAccount
import com.example.domain.TxStatus
import com.example.domain.TxType
import com.example.domain.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface SessionResolution {
    data object Loading : SessionResolution
    data class Authorized(val employee: Employee, val isOwner: Boolean) : SessionResolution
    data class Unauthorized(val uid: String, val email: String, val displayName: String) : SessionResolution
    data class Error(val message: String) : SessionResolution
}

data class NewTxRequest(
    val txType: TxType,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val operator: String? = null,
    val billType: String? = null,
    val expenseCategory: String? = null,
    val partnerId: String? = null,
    val loanId: String? = null,
    val loanType: String? = null,
    val receivedAccountId: String? = null,
    val receivedAmountPaisa: Long = 0L,
    val paidAccountId: String? = null,
    val paidAmountPaisa: Long = 0L,
    val manualCommissionPaisa: Long = 0L,
    val explicitFeePaisa: Long = 0L,
    val interestPaisa: Long = 0L,
    val adjustmentSign: Long = 0L,
    val referenceTxId: String? = null,
    val notes: String? = null,
    val occurredAtMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
)

class BusinessRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Firebase-এ সাইন-ইন করা আবশ্যক।")

    private fun isSuperAdminUid(uid: String): Boolean =
        uid == BusinessConstants.SUPER_ADMIN_UID

    suspend fun resolveAndBootstrapSession(): SessionResolution {
        val user = auth.currentUser ?: return SessionResolution.Error("সাইন-ইন করা নেই।")
        val uid = user.uid
        val email = (user.email ?: "").trim().lowercase()
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: email.substringBefore("@").ifBlank { "ব্যবহারকারী" }

        return try {
            if (isSuperAdminUid(uid)) {
                bootstrapOwnerAndSeedData(uid, displayName, email)
                val ownerDoc = db.collection("employees").document(uid).get().await()
                    .toObject(Employee::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: Employee(
                        id = uid,
                        uid = uid,
                        name = displayName,
                        email = email.ifBlank { "owner@business.local" },
                        role = UserRole.OWNER.name,
                        active = true,
                    )
                SessionResolution.Authorized(ownerDoc, isOwner = true)
            } else {
                val empSnap = db.collection("employees").document(uid).get().await()
                if (empSnap.exists()) {
                    val emp = empSnap.toObject(Employee::class.java, ServerTimestampBehavior.ESTIMATE)
                    if (emp != null && emp.active) {
                        return SessionResolution.Authorized(emp, isOwner = false)
                    }
                    return SessionResolution.Unauthorized(uid, email, displayName)
                }

                // Check email invite if verified
                if (email.isNotBlank() && user.isEmailVerified) {
                    val inviteSnap = db.collection("employee_invites").document(email).get().await()
                    val invite = inviteSnap.toObject(EmployeeInvite::class.java, ServerTimestampBehavior.ESTIMATE)
                    if (invite != null && invite.active) {
                        val payload = mapOf(
                            "uid" to uid,
                            "name" to invite.name,
                            "email" to email,
                            "phone" to invite.phone,
                            "role" to UserRole.EMPLOYEE.name,
                            "active" to true,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ).filterValues { it != null }
                        db.collection("employees").document(uid).set(payload).await()
                        val created = Employee(
                            id = uid,
                            uid = uid,
                            name = invite.name,
                            email = email,
                            phone = invite.phone,
                            role = UserRole.EMPLOYEE.name,
                            active = true,
                        )
                        return SessionResolution.Authorized(created, isOwner = false)
                    }
                }
                SessionResolution.Unauthorized(uid, email, displayName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "employees/$uid")
            SessionResolution.Error(translateErrorToBengali(e))
        }
    }

    private suspend fun bootstrapOwnerAndSeedData(uid: String, name: String, email: String) {
        val safeEmail = email.ifBlank { "owner@business.local" }
        val empRef = db.collection("employees").document(uid)
        val empSnap = empRef.get().await()
        if (!empSnap.exists()) {
            empRef.set(
                mapOf(
                    "uid" to uid,
                    "name" to name,
                    "email" to safeEmail,
                    "role" to UserRole.OWNER.name,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        }

        val allEmployees = db.collection("employees").get().await()
            .toObjects(Employee::class.java, ServerTimestampBehavior.ESTIMATE)
        val memberUids = (listOf(uid) + allEmployees.filter { it.active }.map { it.uid }).distinct().take(50)

        val batch = db.batch()
        var hasWrites = false

        val existingAccounts = db.collection("accounts").get().await().documents.map { it.id }.toSet()
        for (seed in BusinessConstants.ALL_ACCOUNTS) {
            if (seed.accountId !in existingAccounts) {
                hasWrites = true
                batch.set(
                    db.collection("accounts").document(seed.accountId),
                    mapOf(
                        "accountId" to seed.accountId,
                        "name" to seed.name,
                        "group" to seed.group.name,
                        "sortOrder" to seed.sortOrder,
                        "balancePaisa" to 0L,
                        "capitalPaisa" to 0L,
                        "allowNegativeBalance" to false,
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
        }

        val existingPartnersSnap = db.collection("partners").get().await()
        val existingPartnersById = existingPartnersSnap.documents.associateBy { it.id }
        for (pSeed in BusinessConstants.ALL_PARTNERS) {
            val existingDoc = existingPartnersById[pSeed.partnerId]
            if (existingDoc == null) {
                hasWrites = true
                batch.set(
                    db.collection("partners").document(pSeed.partnerId),
                    mapOf(
                        "partnerId" to pSeed.partnerId,
                        "name" to pSeed.name,
                        "ratePerThousandPaisa" to pSeed.defaultRatePerThousandPaisa,
                        "bkashOnlyCommission" to pSeed.bkashOnlyCommission,
                        "sentTotalPaisa" to 0L,
                        "receivedTotalPaisa" to 0L,
                        "currentBalancePaisa" to 0L,
                        "payableCommissionPaisa" to 0L,
                        "paidCommissionPaisa" to 0L,
                        "memberUids" to memberUids,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else if (
                (pSeed.partnerId == BusinessConstants.PARTNER_SAMAD ||
                    pSeed.partnerId == BusinessConstants.PARTNER_JAHIR) &&
                existingDoc.getLong("ratePerThousandPaisa") == 500L &&
                existingDoc.getBoolean("bkashOnlyCommission") == true
            ) {
                hasWrites = true
                batch.update(
                    existingDoc.reference,
                    mapOf(
                        "ratePerThousandPaisa" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
        }

        val settingsRef = db.collection("settings").document("business_config")
        if (!settingsRef.get().await().exists()) {
            hasWrites = true
            batch.set(
                settingsRef,
                mapOf(
                    "businessName" to BusinessConstants.APP_NAME_BN,
                    "superAdminUid" to BusinessConstants.SUPER_ADMIN_UID,
                    "initialized" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        if (hasWrites) {
            batch.commit().await()
        }
    }

    // --- REALTIME OBSERVERS (Two-Tier Flow Safe) ---

    fun observeAccounts(uid: String, isOwner: Boolean): Flow<List<FinancialAccount>> = flow {
        val query = if (isOwner) {
            db.collection("accounts")
        } else {
            db.collection("accounts").whereArrayContains("memberUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(FinancialAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.sortOrder }
                }
                .catch { handleStreamError(it, "accounts", uid); throw it }
        )
    }

    fun observePartners(uid: String, isOwner: Boolean): Flow<List<PartnerAccount>> = flow {
        val query = if (isOwner) {
            db.collection("partners")
        } else {
            db.collection("partners").whereArrayContains("memberUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(PartnerAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { handleStreamError(it, "partners", uid); throw it }
        )
    }

    fun observeTransactions(uid: String, isOwner: Boolean): Flow<List<BusinessTransaction>> = flow {
        val query = if (isOwner) {
            db.collection("transactions")
        } else {
            db.collection("transactions").whereEqualTo("createdBy", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(BusinessTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.occurredAtMillis }
                }
                .catch { handleStreamError(it, "transactions", uid); throw it }
        )
    }

    fun observeLoans(uid: String, isOwner: Boolean): Flow<List<LoanRecord>> = flow {
        val query = if (isOwner) {
            db.collection("loans")
        } else {
            db.collection("loans").whereArrayContains("memberUids", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(LoanRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.loanDateMillis }
                }
                .catch { handleStreamError(it, "loans", uid); throw it }
        )
    }

    fun observeLedger(uid: String): Flow<List<LedgerEntry>> = flow {
        emitAll(
            db.collection("ledger").snapshots()
                .map { snap ->
                    snap.toObjects(LedgerEntry::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.occurredAtMillis }
                }
                .catch { handleStreamError(it, "ledger", uid); throw it }
        )
    }

    fun observeEmployees(uid: String): Flow<List<Employee>> = flow {
        emitAll(
            db.collection("employees").snapshots()
                .map { snap -> snap.toObjects(Employee::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { handleStreamError(it, "employees", uid); throw it }
        )
    }

    fun observeInvites(uid: String): Flow<List<EmployeeInvite>> = flow {
        emitAll(
            db.collection("employee_invites").snapshots()
                .map { snap -> snap.toObjects(EmployeeInvite::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { handleStreamError(it, "employee_invites", uid); throw it }
        )
    }

    fun observeAuditLogs(uid: String): Flow<List<AuditLog>> = flow {
        emitAll(
            db.collection("audit_logs").snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { handleStreamError(it, "audit_logs", uid); throw it }
        )
    }

    private fun handleStreamError(error: Throwable, path: String, uid: String) {
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) return
        val ex = fsError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(ex, OperationType.LIST, path)
        }
    }

    // --- ATOMIC TRANSACTIONS & WORKFLOWS ---

    suspend fun submitTransaction(
        request: NewTxRequest,
        actorName: String,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val isOwner = isSuperAdminUid(uid)
        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        return try {
            // Duplicate referenceTxId check (single-field query avoids composite index requirement in production Firestore)
            val refId = request.referenceTxId?.trim()?.takeIf { it.isNotBlank() }
            if (refId != null) {
                val dupQuery = if (isOwner) {
                    db.collection("transactions").whereEqualTo("referenceTxId", refId).get().await()
                } else {
                    db.collection("transactions").whereEqualTo("createdBy", uid).get().await()
                }
                val activeDup = dupQuery.toObjects(BusinessTransaction::class.java)
                    .any {
                        it.referenceTxId == refId &&
                            it.status != TxStatus.REJECTED.name &&
                            it.reversedByTxId.isNullOrBlank()
                    }
                if (activeDup) {
                    return Result.failure(IllegalStateException("এই ট্রানজেকশন আইডি ($refId) ইতিমধ্যেই ব্যবহৃত হয়েছে!"))
                }
            }

            val draftTx = BusinessTransaction(
                id = txId,
                txType = request.txType.name,
                status = if (isOwner) TxStatus.APPROVED.name else TxStatus.PENDING.name,
                customerName = request.customerName?.trim()?.takeIf { it.isNotBlank() },
                customerPhone = request.customerPhone?.trim()?.takeIf { it.isNotBlank() },
                operator = request.operator?.trim()?.takeIf { it.isNotBlank() },
                billType = request.billType?.trim()?.takeIf { it.isNotBlank() },
                expenseCategory = request.expenseCategory?.trim()?.takeIf { it.isNotBlank() },
                partnerId = request.partnerId?.trim()?.takeIf { it.isNotBlank() },
                loanId = request.loanId?.trim()?.takeIf { it.isNotBlank() },
                loanType = request.loanType?.trim()?.takeIf { it.isNotBlank() },
                receivedAccountId = request.receivedAccountId?.trim()?.takeIf { it.isNotBlank() },
                receivedAmountPaisa = request.receivedAmountPaisa,
                paidAccountId = request.paidAccountId?.trim()?.takeIf { it.isNotBlank() },
                paidAmountPaisa = request.paidAmountPaisa,
                manualCommissionPaisa = request.manualCommissionPaisa,
                explicitFeePaisa = request.explicitFeePaisa,
                interestPaisa = request.interestPaisa,
                adjustmentSign = request.adjustmentSign,
                referenceTxId = refId,
                notes = request.notes?.trim()?.takeIf { it.isNotBlank() },
                createdBy = uid,
                createdByName = actorName,
                approvedBy = if (isOwner) uid else null,
                occurredAtMillis = request.occurredAtMillis,
            )

            if (!isOwner) {
                // Validate structure before creating PENDING entry
                val dummyPartners = BusinessConstants.ALL_PARTNERS.associate {
                    it.partnerId to PartnerAccount(
                        partnerId = it.partnerId,
                        name = it.name,
                        ratePerThousandPaisa = it.defaultRatePerThousandPaisa,
                        bkashOnlyCommission = it.bkashOnlyCommission,
                    )
                }
                val check = AccountingEngine.computeEffectsForApproval(draftTx, dummyPartners)
                if (check.isFailure) {
                    return Result.failure(check.exceptionOrNull()!!)
                }
                val payload = buildTransactionCreateMap(draftTx, isOwnerApproved = false, replacesTxId = null)
                db.collection("transactions").document(txId).set(payload).await()
                Result.success(txId)
            } else {
                // Owner creates and atomically approves in a single Firestore transaction
                applyApprovalAtomic(
                    txId = txId,
                    existingOrDraftTx = draftTx,
                    isNewTx = true,
                    approverUid = uid,
                    approverName = actorName,
                    dueDateMillis = request.dueDateMillis,
                    replacesTxId = null,
                )
                Result.success(txId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "transactions/$txId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun approvePendingTransaction(txId: String, ownerName: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (!isSuperAdminUid(uid)) {
            return Result.failure(SecurityException("শুধুমাত্র সুপার অ্যাডমিন লেনদেন অনুমোদন করতে পারবেন।"))
        }
        return try {
            val snap = db.collection("transactions").document(txId).get().await()
            val tx = snap.toObject(BusinessTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalArgumentException("লেনদেন পাওয়া যায়নি।"))
            if (tx.status != TxStatus.PENDING.name) {
                return Result.failure(IllegalStateException("এই লেনদেনটি ইতিমধ্যেই ${TxStatus.fromRaw(tx.status).labelBn} অবস্থায় আছে (পুনরায় অনুমোদন সম্ভব নয়)।"))
            }
            applyApprovalAtomic(
                txId = txId,
                existingOrDraftTx = tx,
                isNewTx = false,
                approverUid = uid,
                approverName = ownerName,
                dueDateMillis = null,
                replacesTxId = null,
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "transactions/$txId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun removePendingTransaction(txId: String, ownerName: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (!isSuperAdminUid(uid)) {
            return Result.failure(SecurityException("শুধুমাত্র সুপার অ্যাডমিন অপেক্ষমাণ এন্ট্রি রিমুভ করতে পারবেন।"))
        }
        return try {
            db.runTransaction { txn ->
                val txRef = db.collection("transactions").document(txId)
                val snap = txn.get(txRef)
                val current = snap.toObject(BusinessTransaction::class.java)
                    ?: throw IllegalArgumentException("অপেক্ষমাণ এন্ট্রি পাওয়া যায়নি।")
                if (current.status != TxStatus.PENDING.name) {
                    throw IllegalStateException("শুধুমাত্র অপেক্ষমাণ এন্ট্রি রিমুভ করা যায়।")
                }
                txn.delete(txRef)
                val logRef = db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
                val amt = if (current.receivedAmountPaisa > 0L) current.receivedAmountPaisa else current.paidAmountPaisa
                txn.set(
                    logRef,
                    mapOf(
                        "action" to "REMOVE_PENDING_TX",
                        "actorUid" to uid,
                        "actorName" to ownerName,
                        "targetType" to "TRANSACTION",
                        "targetId" to txId,
                        "details" to "অপেক্ষমাণ ${TxType.fromRaw(current.txType).labelBn} রিমুভ (${current.createdByName}: ${MoneyFormatter.formatTaka(amt)})",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "transactions/$txId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun rejectPendingTransaction(txId: String, reason: String, ownerName: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (!isSuperAdminUid(uid)) {
            return Result.failure(SecurityException("শুধুমাত্র সুপার অ্যাডমিন লেনদেন বাতিল করতে পারবেন।"))
        }
        return try {
            db.runTransaction { txn ->
                val txRef = db.collection("transactions").document(txId)
                val snap = txn.get(txRef)
                val current = snap.toObject(BusinessTransaction::class.java)
                    ?: throw IllegalArgumentException("লেনদেন পাওয়া যায়নি।")
                if (current.status != TxStatus.PENDING.name) {
                    throw IllegalStateException("শুধুমাত্র অপেক্ষমাণ লেনদেন বাতিল করা যায়।")
                }
                txn.update(
                    txRef,
                    mapOf(
                        "status" to TxStatus.REJECTED.name,
                        "approvedBy" to uid,
                        "approvedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "rejectionReason" to reason.ifBlank { "মালিক কর্তৃক বাতিল" },
                    )
                )
                val logRef = db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
                txn.set(
                    logRef,
                    mapOf(
                        "action" to "REJECT_TX",
                        "actorUid" to uid,
                        "actorName" to ownerName,
                        "targetType" to "TRANSACTION",
                        "targetId" to txId,
                        "details" to "লেনদেন বাতিল: ${reason.ifBlank { "কারণ উল্লেখ নেই" }}",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                null
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "transactions/$txId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    /**
     * Edits/corrects an approved transaction WITHOUT losing original history:
     * 1. Creates a REVERSAL transaction that reverses all balance/capital/partner/P&L effects.
     * 2. Marks original transaction `reversedByTxId`.
     * 3. Optionally creates a new corrected transaction with `replacesTxId = originalTxId`.
     */
    suspend fun reverseAndCorrectTransaction(
        originalTxId: String,
        reason: String,
        correctedRequest: NewTxRequest?,
        ownerName: String,
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        if (!isSuperAdminUid(uid)) {
            return Result.failure(SecurityException("শুধুমাত্র সুপার অ্যাডমিন লেনদেন সংশোধন/রিভার্স করতে পারবেন।"))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("সংশোধন বা রিভার্সালের কারণ উল্লেখ করা আবশ্যক।"))
        }

        val reversalTxId = "rev_${UUID.randomUUID().toString().replace("-", "").take(14)}"

        return try {
            val origSnap = db.collection("transactions").document(originalTxId).get().await()
            val orig = origSnap.toObject(BusinessTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: return Result.failure(IllegalArgumentException("মূল লেনদেন পাওয়া যায়নি।"))
            if (orig.status != TxStatus.APPROVED.name) {
                return Result.failure(IllegalStateException("শুধুমাত্র অনুমোদিত লেনদেন রিভার্স করা যায়।"))
            }
            if (!orig.reversedByTxId.isNullOrBlank()) {
                return Result.failure(IllegalStateException("এই লেনদেনটি ইতিমধ্যেই রিভার্স করা হয়েছে।"))
            }
            if (orig.txType == TxType.REVERSAL.name) {
                return Result.failure(IllegalStateException("রিভার্সাল এন্ট্রি পুনরায় রিভার্স করা যায় না।"))
            }

            // Read ledger entries created by originalTxId so reversal is 100% exact
            val origLedgerSnaps = db.collection("ledger").whereEqualTo("txId", originalTxId).get().await()
            val origLedgers = origLedgerSnaps.toObjects(LedgerEntry::class.java, ServerTimestampBehavior.ESTIMATE)

            val partnersSnap = db.collection("partners").get().await()
            val partnersById = partnersSnap.toObjects(PartnerAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                .associateBy { it.partnerId }

            val origEffects = AccountingEngine.computeEffectsForApproval(orig, partnersById).getOrThrow()

            db.runTransaction { txn ->
                val origRef = db.collection("transactions").document(originalTxId)
                val freshOrig = txn.get(origRef).toObject(BusinessTransaction::class.java)
                    ?: throw IllegalStateException("মূল লেনদেন পাওয়া যায়নি।")
                if (!freshOrig.reversedByTxId.isNullOrBlank()) {
                    throw IllegalStateException("এই লেনদেনটি ইতিমধ্যেই রিভার্স করা হয়েছে।")
                }

                // Read all accounts involved
                val accIds = origLedgers.map { it.accountId }.distinct()
                val accDocs = accIds.associateWith { id ->
                    txn.get(db.collection("accounts").document(id)).toObject(FinancialAccount::class.java)
                        ?: throw IllegalStateException("অ্যাকাউন্ট পাওয়া যায়নি: $id")
                }.toMutableMap()

                val partnerSnapDoc = orig.partnerId?.let { pId ->
                    txn.get(db.collection("partners").document(pId)).toObject(PartnerAccount::class.java)
                }

                val linkedLoanDoc = orig.loanId?.takeIf { it.isNotBlank() }?.let { lId ->
                    txn.get(db.collection("loans").document(lId)).toObject(LoanRecord::class.java)
                }

                val summaryParts = mutableListOf<String>()
                // Reverse each ledger entry atomically
                origLedgers.forEachIndexed { idx, le ->
                    val acc = accDocs.getValue(le.accountId)
                    val revDelta = -le.deltaPaisa
                    val prevBal = acc.balancePaisa
                    val newBal = prevBal + revDelta
                    if (revDelta < 0L && !acc.allowNegativeBalance && newBal < 0L) {
                        throw IllegalStateException("${acc.name}-এ পর্যাপ্ত ব্যালেন্স না থাকায় রিভার্স করা সম্ভব নয়।")
                    }
                    val updatedAcc = acc.copy(
                        balancePaisa = newBal,
                        capitalPaisa = acc.capitalPaisa - le.capitalDeltaPaisa,
                    )
                    accDocs[le.accountId] = updatedAcc
                    summaryParts += "${acc.name}: ${MoneyFormatter.formatTaka(revDelta, showPlusSign = true)}"

                    val revLedgerRef = db.collection("ledger").document("led_${reversalTxId}_$idx")
                    txn.set(
                        revLedgerRef,
                        mapOf(
                            "txId" to reversalTxId,
                            "txType" to TxType.REVERSAL.name,
                            "accountId" to acc.accountId,
                            "accountName" to acc.name,
                            "deltaPaisa" to revDelta,
                            "previousBalancePaisa" to prevBal,
                            "newBalancePaisa" to newBal,
                            "capitalDeltaPaisa" to -le.capitalDeltaPaisa,
                            "commissionIncomePaisa" to -le.commissionIncomePaisa,
                            "partnerCommissionExpensePaisa" to -le.partnerCommissionExpensePaisa,
                            "shopExpensePaisa" to -le.shopExpensePaisa,
                            "interestIncomePaisa" to -le.interestIncomePaisa,
                            "interestExpensePaisa" to -le.interestExpensePaisa,
                            "actorUid" to uid,
                            "approvedBy" to uid,
                            "note" to "রিভার্সাল ($originalTxId): $reason",
                            "occurredAtMillis" to System.currentTimeMillis(),
                            "createdAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                // Write updated accounts
                accDocs.forEach { (accId, updated) ->
                    txn.update(
                        db.collection("accounts").document(accId),
                        mapOf(
                            "balancePaisa" to updated.balancePaisa,
                            "capitalPaisa" to updated.capitalPaisa,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                // Reverse partner balance/commission if partner was involved
                val pDelta = origEffects.partnerDelta
                if (partnerSnapDoc != null && pDelta != null) {
                    val revPartnerComm = -orig.partnerCommissionPaisa
                    txn.update(
                        db.collection("partners").document(partnerSnapDoc.partnerId),
                        mapOf(
                            "sentTotalPaisa" to (partnerSnapDoc.sentTotalPaisa - pDelta.sentDeltaPaisa).coerceAtLeast(0L),
                            "receivedTotalPaisa" to (partnerSnapDoc.receivedTotalPaisa - pDelta.receivedDeltaPaisa).coerceAtLeast(0L),
                            "currentBalancePaisa" to (partnerSnapDoc.currentBalancePaisa - pDelta.currentBalanceDeltaPaisa),
                            "payableCommissionPaisa" to (partnerSnapDoc.payableCommissionPaisa + revPartnerComm - pDelta.paidCommissionDeltaPaisa).coerceAtLeast(0L),
                            "paidCommissionPaisa" to (partnerSnapDoc.paidCommissionPaisa - pDelta.paidCommissionDeltaPaisa).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    )
                }

                // Reverse linked loan state if this was a loan disbursement or repayment
                if (linkedLoanDoc != null) {
                    when (TxType.fromRaw(orig.txType)) {
                        TxType.LOAN_REPAY -> {
                            val principalToRestore = if (linkedLoanDoc.loanType == LoanType.BORROWED.name) {
                                orig.paidAmountPaisa
                            } else {
                                orig.receivedAmountPaisa
                            }
                            val restoredPrincipal = (linkedLoanDoc.remainingPrincipalPaisa + principalToRestore)
                                .coerceAtMost(linkedLoanDoc.principalPaisa)
                            val restoredInterest = (linkedLoanDoc.remainingInterestPaisa + orig.interestPaisa)
                                .coerceAtMost(linkedLoanDoc.interestPaisa)
                            val restoredStatus = if (restoredPrincipal == linkedLoanDoc.principalPaisa && restoredInterest == linkedLoanDoc.interestPaisa) {
                                LoanStatus.OPEN.name
                            } else {
                                LoanStatus.PARTIAL.name
                            }
                            txn.update(
                                db.collection("loans").document(linkedLoanDoc.id),
                                mapOf(
                                    "remainingPrincipalPaisa" to restoredPrincipal,
                                    "remainingInterestPaisa" to restoredInterest,
                                    "status" to restoredStatus,
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                )
                            )
                        }
                        TxType.LOAN_BORROW, TxType.LOAN_LEND -> {
                            txn.update(
                                db.collection("loans").document(linkedLoanDoc.id),
                                mapOf(
                                    "remainingPrincipalPaisa" to 0L,
                                    "remainingInterestPaisa" to 0L,
                                    "status" to LoanStatus.PAID.name,
                                    "notes" to "[রিভার্সড: $reason] ${linkedLoanDoc.notes ?: ""}".trim().take(480),
                                    "updatedAt" to FieldValue.serverTimestamp(),
                                )
                            )
                        }
                        else -> Unit
                    }
                }

                // Mark original transaction reversed (history preserved!)
                txn.update(
                    origRef,
                    mapOf(
                        "reversedByTxId" to reversalTxId,
                        "reversalReason" to reason,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )

                // Create REVERSAL transaction document
                val revTxMap = mapOf(
                    "txType" to TxType.REVERSAL.name,
                    "status" to TxStatus.APPROVED.name,
                    "customerName" to orig.customerName,
                    "partnerId" to orig.partnerId,
                    "receivedAccountId" to orig.paidAccountId,
                    "receivedAmountPaisa" to orig.paidAmountPaisa,
                    "paidAccountId" to orig.receivedAccountId,
                    "paidAmountPaisa" to orig.receivedAmountPaisa,
                    "manualCommissionPaisa" to 0L,
                    "partnerCommissionPaisa" to 0L,
                    "explicitFeePaisa" to 0L,
                    "interestPaisa" to 0L,
                    "adjustmentSign" to 0L,
                    "notes" to "মূল লেনদেন ($originalTxId) রিভার্স: $reason",
                    "reversalReason" to reason,
                    "replacesTxId" to originalTxId,
                    "accountChangesSummary" to summaryParts.joinToString(" | "),
                    "createdBy" to uid,
                    "createdByName" to ownerName,
                    "approvedBy" to uid,
                    "occurredAtMillis" to System.currentTimeMillis(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "approvedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                txn.set(db.collection("transactions").document(reversalTxId), revTxMap)

                val logRef = db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
                txn.set(
                    logRef,
                    mapOf(
                        "action" to "REVERSAL_TX",
                        "actorUid" to uid,
                        "actorName" to ownerName,
                        "targetType" to "TRANSACTION",
                        "targetId" to originalTxId,
                        "details" to "রিভার্সাল ($reversalTxId): $reason",
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                null
            }.await()

            // Step 2: Create corrected replacement entry if provided
            if (correctedRequest != null) {
                val correctedTxId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val correctedDraft = BusinessTransaction(
                    id = correctedTxId,
                    txType = correctedRequest.txType.name,
                    status = TxStatus.APPROVED.name,
                    customerName = correctedRequest.customerName?.trim()?.takeIf { it.isNotBlank() },
                    customerPhone = correctedRequest.customerPhone?.trim()?.takeIf { it.isNotBlank() },
                    operator = correctedRequest.operator?.trim()?.takeIf { it.isNotBlank() },
                    billType = correctedRequest.billType?.trim()?.takeIf { it.isNotBlank() },
                    expenseCategory = correctedRequest.expenseCategory?.trim()?.takeIf { it.isNotBlank() },
                    partnerId = correctedRequest.partnerId?.trim()?.takeIf { it.isNotBlank() },
                    loanId = correctedRequest.loanId?.trim()?.takeIf { it.isNotBlank() },
                    loanType = correctedRequest.loanType?.trim()?.takeIf { it.isNotBlank() },
                    receivedAccountId = correctedRequest.receivedAccountId?.trim()?.takeIf { it.isNotBlank() },
                    receivedAmountPaisa = correctedRequest.receivedAmountPaisa,
                    paidAccountId = correctedRequest.paidAccountId?.trim()?.takeIf { it.isNotBlank() },
                    paidAmountPaisa = correctedRequest.paidAmountPaisa,
                    manualCommissionPaisa = correctedRequest.manualCommissionPaisa,
                    explicitFeePaisa = correctedRequest.explicitFeePaisa,
                    interestPaisa = correctedRequest.interestPaisa,
                    adjustmentSign = correctedRequest.adjustmentSign,
                    referenceTxId = correctedRequest.referenceTxId?.trim()?.takeIf { it.isNotBlank() },
                    notes = "সংশোধিত এন্ট্রি (পূর্বের আইডি: $originalTxId): ${correctedRequest.notes ?: ""}".trim(),
                    createdBy = uid,
                    createdByName = ownerName,
                    approvedBy = uid,
                    occurredAtMillis = correctedRequest.occurredAtMillis,
                    replacesTxId = originalTxId,
                )
                applyApprovalAtomic(
                    txId = correctedTxId,
                    existingOrDraftTx = correctedDraft,
                    isNewTx = true,
                    approverUid = uid,
                    approverName = ownerName,
                    dueDateMillis = correctedRequest.dueDateMillis,
                    replacesTxId = originalTxId,
                )
                return Result.success(correctedTxId)
            }

            Result.success(reversalTxId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "transactions/$originalTxId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    private suspend fun applyApprovalAtomic(
        txId: String,
        existingOrDraftTx: BusinessTransaction,
        isNewTx: Boolean,
        approverUid: String,
        approverName: String,
        dueDateMillis: Long?,
        replacesTxId: String?,
    ) {
        val allEmployees = db.collection("employees").get().await()
            .toObjects(Employee::class.java, ServerTimestampBehavior.ESTIMATE)
        val memberUids = (listOf(BusinessConstants.SUPER_ADMIN_UID) + allEmployees.filter { it.active }.map { it.uid })
            .distinct().take(50)

        db.runTransaction { txn ->
            val txRef = db.collection("transactions").document(txId)
            val tx = if (isNewTx) {
                existingOrDraftTx
            } else {
                val snap = txn.get(txRef)
                val loaded = snap.toObject(BusinessTransaction::class.java)
                    ?: throw IllegalArgumentException("লেনদেন পাওয়া যায়নি।")
                if (loaded.status != TxStatus.PENDING.name) {
                    throw IllegalStateException("লেনদেনটি ইতিমধ্যেই অনুমোদিত বা বাতিল হয়েছে — একই লেনদেন দুইবার অনুমোদন করা যাবে না।")
                }
                loaded
            }

            // Read partner if needed
            val partnersMap = mutableMapOf<String, PartnerAccount>()
            if (!tx.partnerId.isNullOrBlank()) {
                val pSnap = txn.get(db.collection("partners").document(tx.partnerId))
                val partner = pSnap.toObject(PartnerAccount::class.java)
                    ?: throw IllegalArgumentException("পার্টনার পাওয়া যায়নি: ${tx.partnerId}")
                partnersMap[tx.partnerId] = partner
            }

            val effects = AccountingEngine.computeEffectsForApproval(tx, partnersMap).getOrThrow()

            // Read all involved accounts inside transaction
            val accIds = effects.accountDeltas.map { it.accountId }.distinct()
            val accountsMap = accIds.associateWith { id ->
                val snap = txn.get(db.collection("accounts").document(id))
                snap.toObject(FinancialAccount::class.java)
                    ?: throw IllegalArgumentException("অ্যাকাউন্ট পাওয়া যায়নি: $id")
            }

            // Read loan if LOAN_REPAY
            val existingLoan = if (TxType.fromRaw(tx.txType) == TxType.LOAN_REPAY && !tx.loanId.isNullOrBlank()) {
                txn.get(db.collection("loans").document(tx.loanId)).toObject(LoanRecord::class.java)
                    ?: throw IllegalArgumentException("ঋণের রেকর্ড পাওয়া যায়নি।")
            } else null

            // Validate & apply account deltas (checks allowNegativeBalance!)
            val (updatedAccounts, transitions) =
                AccountingEngine.validateAndApplyAccountDeltas(accountsMap, effects.accountDeltas).getOrThrow()

            // Persist account updates
            updatedAccounts.forEach { (accId, updated) ->
                txn.update(
                    db.collection("accounts").document(accId),
                    mapOf(
                        "balancePaisa" to updated.balancePaisa,
                        "capitalPaisa" to updated.capitalPaisa,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            // Persist partner update if applicable
            val pDelta = effects.partnerDelta
            if (pDelta != null) {
                val partner = partnersMap.getValue(pDelta.partnerId)
                if (TxType.fromRaw(tx.txType) == TxType.PARTNER_COMMISSION_PAY && tx.paidAmountPaisa > partner.payableCommissionPaisa) {
                    throw IllegalStateException(
                        "${partner.name}-এর প্রদেয় কমিশনের (${MoneyFormatter.formatTaka(partner.payableCommissionPaisa)}) চেয়ে বেশি টাকা পরিশোধ করা যাবে না।"
                    )
                }
                val newPayable = (partner.payableCommissionPaisa + pDelta.payableCommissionDeltaPaisa).coerceAtLeast(0L)
                txn.update(
                    db.collection("partners").document(partner.partnerId),
                    mapOf(
                        "sentTotalPaisa" to (partner.sentTotalPaisa + pDelta.sentDeltaPaisa),
                        "receivedTotalPaisa" to (partner.receivedTotalPaisa + pDelta.receivedDeltaPaisa),
                        "currentBalancePaisa" to (partner.currentBalancePaisa + pDelta.currentBalanceDeltaPaisa),
                        "payableCommissionPaisa" to newPayable,
                        "paidCommissionPaisa" to (partner.paidCommissionPaisa + pDelta.paidCommissionDeltaPaisa),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }

            // Persist Loan creation or repayment
            var resolvedLoanId = tx.loanId
            when (TxType.fromRaw(tx.txType)) {
                TxType.LOAN_BORROW, TxType.LOAN_LEND -> {
                    val loanType = if (tx.txType == TxType.LOAN_BORROW.name) LoanType.BORROWED else LoanType.LENT
                    val principal = if (loanType == LoanType.BORROWED) tx.receivedAmountPaisa else tx.paidAmountPaisa
                    val accId = (if (loanType == LoanType.BORROWED) tx.receivedAccountId else tx.paidAccountId)
                        ?: BusinessConstants.ACC_CASH
                    val newLoanId = "loan_${UUID.randomUUID().toString().replace("-", "").take(14)}"
                    resolvedLoanId = newLoanId
                    txn.set(
                        db.collection("loans").document(newLoanId),
                        mapOf(
                            "loanType" to loanType.name,
                            "personName" to (tx.customerName ?: "ব্যক্তি"),
                            "personPhone" to tx.customerPhone,
                            "accountId" to accId,
                            "principalPaisa" to principal,
                            "interestPaisa" to tx.interestPaisa,
                            "remainingPrincipalPaisa" to principal,
                            "remainingInterestPaisa" to tx.interestPaisa,
                            "loanDateMillis" to tx.occurredAtMillis,
                            "dueDateMillis" to (dueDateMillis ?: (tx.occurredAtMillis + 30L * 86_400_000L)),
                            "status" to LoanStatus.OPEN.name,
                            "notes" to tx.notes,
                            "memberUids" to memberUids,
                            "createdBy" to tx.createdBy,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ).filterValues { it != null }
                    )
                }

                TxType.LOAN_REPAY -> {
                    if (existingLoan != null) {
                        val principalPaid = if (existingLoan.loanType == LoanType.BORROWED.name) {
                            tx.paidAmountPaisa
                        } else {
                            tx.receivedAmountPaisa
                        }
                        val newRemPrincipal = (existingLoan.remainingPrincipalPaisa - principalPaid).coerceAtLeast(0L)
                        val newRemInterest = (existingLoan.remainingInterestPaisa - tx.interestPaisa).coerceAtLeast(0L)
                        val newStatus = if (newRemPrincipal == 0L && newRemInterest == 0L) {
                            LoanStatus.PAID.name
                        } else {
                            LoanStatus.PARTIAL.name
                        }
                        txn.update(
                            db.collection("loans").document(existingLoan.id),
                            mapOf(
                                "remainingPrincipalPaisa" to newRemPrincipal,
                                "remainingInterestPaisa" to newRemInterest,
                                "status" to newStatus,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }
                }
                else -> Unit
            }

            // Build human-readable summary & append-only ledger entries
            val summaryBuilder = mutableListOf<String>()
            transitions.forEachIndexed { idx, (accAfter, prevBal, newBal) ->
                val deltaObj = effects.accountDeltas[idx]
                summaryBuilder += "${accAfter.name}: ${MoneyFormatter.formatTaka(prevBal)} -> ${MoneyFormatter.formatTaka(newBal)}"

                // Attach transaction-level P&L totals only on the first ledger entry so they are never double-counted
                val isFirst = idx == 0
                val ledgerRef = db.collection("ledger").document("led_${txId}_$idx")
                txn.set(
                    ledgerRef,
                    mapOf(
                        "txId" to txId,
                        "txType" to tx.txType,
                        "accountId" to accAfter.accountId,
                        "accountName" to accAfter.name,
                        "deltaPaisa" to deltaObj.balanceDeltaPaisa,
                        "previousBalancePaisa" to prevBal,
                        "newBalancePaisa" to newBal,
                        "capitalDeltaPaisa" to deltaObj.capitalDeltaPaisa,
                        "commissionIncomePaisa" to if (isFirst) effects.commissionIncomePaisa else 0L,
                        "partnerCommissionExpensePaisa" to if (isFirst) effects.partnerCommissionExpensePaisa else 0L,
                        "shopExpensePaisa" to if (isFirst) effects.shopExpensePaisa else 0L,
                        "interestIncomePaisa" to if (isFirst) effects.interestIncomePaisa else 0L,
                        "interestExpensePaisa" to if (isFirst) effects.interestExpensePaisa else 0L,
                        "actorUid" to tx.createdBy,
                        "approvedBy" to approverUid,
                        "note" to deltaObj.note,
                        "occurredAtMillis" to tx.occurredAtMillis,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            val summaryText = summaryBuilder.joinToString(" | ")

            if (isNewTx) {
                val txMap = buildTransactionCreateMap(
                    tx.copy(
                        loanId = resolvedLoanId,
                        partnerCommissionPaisa = effects.partnerCommissionExpensePaisa,
                        calculationExplanation = effects.calculationExplanation,
                        accountChangesSummary = summaryText,
                    ),
                    isOwnerApproved = true,
                    replacesTxId = replacesTxId,
                )
                txn.set(txRef, txMap)
            } else {
                val updateMap = mutableMapOf<String, Any?>(
                    "status" to TxStatus.APPROVED.name,
                    "approvedBy" to approverUid,
                    "approvedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "accountChangesSummary" to summaryText,
                    "partnerCommissionPaisa" to effects.partnerCommissionExpensePaisa,
                    "calculationExplanation" to effects.calculationExplanation,
                    "loanId" to resolvedLoanId,
                ).filterValues { it != null } as Map<String, Any>
                txn.update(txRef, updateMap)
            }

            val logRef = db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}")
            txn.set(
                logRef,
                mapOf(
                    "action" to "APPROVE_TX",
                    "actorUid" to approverUid,
                    "actorName" to approverName,
                    "targetType" to "TRANSACTION",
                    "targetId" to txId,
                    "details" to "${TxType.fromRaw(tx.txType).labelBn} অনুমোদিত ($summaryText)",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            null
        }.await()
    }

    private fun buildTransactionCreateMap(
        tx: BusinessTransaction,
        isOwnerApproved: Boolean,
        replacesTxId: String?,
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any?>(
            "txType" to tx.txType,
            "status" to if (isOwnerApproved) TxStatus.APPROVED.name else TxStatus.PENDING.name,
            "customerName" to tx.customerName,
            "customerPhone" to tx.customerPhone,
            "operator" to tx.operator,
            "billType" to tx.billType,
            "expenseCategory" to tx.expenseCategory,
            "partnerId" to tx.partnerId,
            "loanId" to tx.loanId,
            "loanType" to tx.loanType,
            "receivedAccountId" to tx.receivedAccountId,
            "receivedAmountPaisa" to tx.receivedAmountPaisa,
            "paidAccountId" to tx.paidAccountId,
            "paidAmountPaisa" to tx.paidAmountPaisa,
            "manualCommissionPaisa" to tx.manualCommissionPaisa,
            "partnerCommissionPaisa" to tx.partnerCommissionPaisa,
            "explicitFeePaisa" to tx.explicitFeePaisa,
            "interestPaisa" to tx.interestPaisa,
            "adjustmentSign" to tx.adjustmentSign,
            "referenceTxId" to tx.referenceTxId,
            "notes" to tx.notes,
            "calculationExplanation" to tx.calculationExplanation,
            "createdBy" to tx.createdBy,
            "createdByName" to tx.createdByName,
            "occurredAtMillis" to tx.occurredAtMillis,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
            "replacesTxId" to replacesTxId,
            "accountChangesSummary" to tx.accountChangesSummary,
        )
        if (isOwnerApproved) {
            map["approvedBy"] = tx.approvedBy ?: BusinessConstants.SUPER_ADMIN_UID
            map["approvedAt"] = FieldValue.serverTimestamp()
        }
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    // --- OWNER SETTINGS & EMPLOYEE MANAGEMENT ---

    suspend fun updateAccountAllowNegative(accountId: String, allow: Boolean, ownerName: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (!isSuperAdminUid(uid)) return Result.failure(SecurityException("অনুমতি নেই।"))
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("accounts").document(accountId),
                mapOf("allowNegativeBalance" to allow, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(
                db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}"),
                mapOf(
                    "action" to "ACCOUNT_SETTING",
                    "actorUid" to uid,
                    "actorName" to ownerName,
                    "targetType" to "ACCOUNT",
                    "targetId" to accountId,
                    "details" to "${BusinessConstants.accountDisplayName(accountId)}: ঋণাত্মক ব্যালেন্স ${if (allow) "চালু" else "বন্ধ"}",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "accounts/$accountId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun updatePartnerCommissionSettings(
        partnerId: String,
        ratePerThousandPaisa: Long,
        bkashOnlyCommission: Boolean,
        ownerName: String,
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (!isSuperAdminUid(uid)) return Result.failure(SecurityException("অনুমতি নেই।"))
        if (ratePerThousandPaisa < 0L) return Result.failure(IllegalArgumentException("কমিশন হার ০-এর কম হতে পারে না।"))
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("partners").document(partnerId),
                mapOf(
                    "ratePerThousandPaisa" to ratePerThousandPaisa,
                    "bkashOnlyCommission" to bkashOnlyCommission,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.set(
                db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}"),
                mapOf(
                    "action" to "PARTNER_RATE_UPDATE",
                    "actorUid" to uid,
                    "actorName" to ownerName,
                    "targetType" to "PARTNER",
                    "targetId" to partnerId,
                    "details" to "হার: ${MoneyFormatter.formatTaka(ratePerThousandPaisa)}/হাজার (শুধু bKash: $bkashOnlyCommission)",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "partners/$partnerId")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun inviteOrAddEmployee(
        name: String,
        email: String,
        directUid: String?,
        phone: String?,
        ownerName: String,
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (!isSuperAdminUid(uid)) return Result.failure(SecurityException("অনুমতি নেই।"))
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanDirectUid = directUid?.trim()?.takeIf { it.isNotBlank() }
        if (cleanName.isBlank() || cleanEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("সাধারণ মেম্বারের নাম ও ইমেইল আবশ্যক।"))
        }
        return try {
            val batch = db.batch()
            val inviteMap = mapOf(
                "email" to cleanEmail,
                "name" to cleanName,
                "phone" to phone?.trim()?.takeIf { it.isNotBlank() },
                "role" to UserRole.EMPLOYEE.name,
                "active" to true,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ).filterValues { it != null }
            batch.set(db.collection("employee_invites").document(cleanEmail), inviteMap)

            if (cleanDirectUid != null && cleanDirectUid != BusinessConstants.SUPER_ADMIN_UID) {
                val empMap = mapOf(
                    "uid" to cleanDirectUid,
                    "name" to cleanName,
                    "email" to cleanEmail,
                    "phone" to phone?.trim()?.takeIf { it.isNotBlank() },
                    "role" to UserRole.EMPLOYEE.name,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                batch.set(db.collection("employees").document(cleanDirectUid), empMap)
            }

            batch.set(
                db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}"),
                mapOf(
                    "action" to "EMPLOYEE_ADD",
                    "actorUid" to uid,
                    "actorName" to ownerName,
                    "targetType" to "EMPLOYEE",
                    "targetId" to (cleanDirectUid ?: cleanEmail),
                    "details" to "সাধারণ মেম্বার যুক্ত: $cleanName ($cleanEmail)",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
            if (cleanDirectUid != null) {
                syncMemberUidsAcrossSharedCollections()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "employees")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun toggleEmployeeActive(targetUid: String, active: Boolean, ownerName: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (!isSuperAdminUid(uid)) return Result.failure(SecurityException("অনুমতি নেই।"))
        if (targetUid == BusinessConstants.SUPER_ADMIN_UID) {
            return Result.failure(IllegalArgumentException("সুপার অ্যাডমিনকে নিষ্ক্রিয় করা যাবে না।"))
        }
        return try {
            db.collection("employees").document(targetUid).update(
                mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}").set(
                mapOf(
                    "action" to "EMPLOYEE_STATUS",
                    "actorUid" to uid,
                    "actorName" to ownerName,
                    "targetType" to "EMPLOYEE",
                    "targetId" to targetUid,
                    "details" to "সাধারণ মেম্বার অবস্থা পরিবর্তন: ${if (active) "সক্রিয়" else "নিষ্ক্রিয়"}",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            syncMemberUidsAcrossSharedCollections()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "employees/$targetUid")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun deleteEmployeeInvite(inviteEmail: String, ownerName: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (!isSuperAdminUid(uid)) return Result.failure(SecurityException("অনুমতি নেই।"))
        val cleanEmail = inviteEmail.trim().lowercase()
        return try {
            db.collection("employee_invites").document(cleanEmail).delete().await()
            db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}").set(
                mapOf(
                    "action" to "INVITE_DELETE",
                    "actorUid" to uid,
                    "actorName" to ownerName,
                    "targetType" to "INVITE",
                    "targetId" to cleanEmail,
                    "details" to "সাধারণ মেম্বার আমন্ত্রণ বাতিল: $cleanEmail",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "employee_invites/$cleanEmail")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun syncMemberUidsAcrossSharedCollections() {
        val uid = auth.currentUser?.uid ?: return
        if (!isSuperAdminUid(uid)) return
        val emps = db.collection("employees").get().await()
            .toObjects(Employee::class.java, ServerTimestampBehavior.ESTIMATE)
        val memberUids = (listOf(BusinessConstants.SUPER_ADMIN_UID) + emps.filter { it.active }.map { it.uid })
            .distinct().take(50)

        val batch = db.batch()
        db.collection("accounts").get().await().documents.forEach { doc ->
            batch.update(doc.reference, mapOf("memberUids" to memberUids, "updatedAt" to FieldValue.serverTimestamp()))
        }
        db.collection("partners").get().await().documents.forEach { doc ->
            batch.update(doc.reference, mapOf("memberUids" to memberUids, "updatedAt" to FieldValue.serverTimestamp()))
        }
        db.collection("loans").get().await().documents.forEach { doc ->
            batch.update(doc.reference, mapOf("memberUids" to memberUids, "updatedAt" to FieldValue.serverTimestamp()))
        }
        batch.commit().await()
    }
}
