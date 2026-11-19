package com.example.data

import com.example.domain.BackupPayload
import com.example.domain.BackupSerializer
import com.example.domain.BusinessConstants
import com.example.domain.Employee
import com.example.domain.FinancialAccount
import com.example.domain.LedgerEntry
import com.example.domain.LoanRecord
import com.example.domain.PartnerAccount
import com.example.domain.BusinessTransaction
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BackupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    private fun requireSuperAdmin(): String {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("সাইন-ইন করা নেই।")
        if (uid != BusinessConstants.SUPER_ADMIN_UID) {
            throw SecurityException("শুধুমাত্র সুপার অ্যাডমিন ব্যাকআপ ও রিস্টোর করতে পারবেন।")
        }
        return uid
    }

    suspend fun createFullBackupJson(): Result<String> {
        return try {
            val uid = requireSuperAdmin()
            val accounts = db.collection("accounts").get().await()
                .toObjects(FinancialAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.sortOrder }
            val partners = db.collection("partners").get().await()
                .toObjects(PartnerAccount::class.java, ServerTimestampBehavior.ESTIMATE)
            val transactions = db.collection("transactions").get().await()
                .toObjects(BusinessTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
            val ledger = db.collection("ledger").get().await()
                .toObjects(LedgerEntry::class.java, ServerTimestampBehavior.ESTIMATE)
            val loans = db.collection("loans").get().await()
                .toObjects(LoanRecord::class.java, ServerTimestampBehavior.ESTIMATE)
            val employees = db.collection("employees").get().await()
                .toObjects(Employee::class.java, ServerTimestampBehavior.ESTIMATE)

            val payload = BackupPayload(
                schemaVersion = BackupSerializer.CURRENT_SCHEMA_VERSION,
                exportedAtMillis = System.currentTimeMillis(),
                exportedBy = uid,
                accounts = accounts,
                partners = partners,
                transactions = transactions,
                ledger = ledger,
                loans = loans,
                employees = employees,
            )
            Result.success(BackupSerializer.toJson(payload))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "backup")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    suspend fun restoreFromBackupJson(
        jsonString: String,
        ownerName: String,
        onProgress: (Float, String) -> Unit,
    ): Result<String> {
        return try {
            val uid = requireSuperAdmin()
            onProgress(0.05f, "ব্যাকআপ ফাইল যাচাই করা হচ্ছে...")
            val payload = BackupSerializer.parseAndValidate(jsonString).getOrElse {
                return Result.failure(it)
            }

            // Create safety pre-restore backup first
            onProgress(0.15f, "রিস্টোরের আগে বর্তমান ডেটার নিরাপত্তা ব্যাকআপ তৈরি হচ্ছে...")
            val safetyJson = createFullBackupJson().getOrElse {
                return Result.failure(IllegalStateException("নিরাপত্তা ব্যাকআপ তৈরি ব্যর্থ হওয়ায় রিস্টোর বাতিল করা হয়েছে।"))
            }

            // Delete existing dynamic collections (transactions, ledger, loans) and non-superadmin employees
            onProgress(0.30f, "পূর্বের ডেটা পরিষ্কার করা হচ্ছে...")
            deleteCollectionInChunks("transactions")
            deleteCollectionInChunks("ledger")
            deleteCollectionInChunks("loans")
            val existingEmpDocs = db.collection("employees").get().await().documents
                .filter { it.id != BusinessConstants.SUPER_ADMIN_UID }
            existingEmpDocs.chunked(400).forEach { chunk ->
                val delBatch = db.batch()
                chunk.forEach { delBatch.delete(it.reference) }
                delBatch.commit().await()
            }

            onProgress(0.50f, "১১টি অ্যাকাউন্ট ও পার্টনার ডেটা রিস্টোর হচ্ছে...")
            val baseOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            payload.accounts.forEach { acc ->
                baseOps += db.collection("accounts").document(acc.accountId) to mapOf(
                    "accountId" to acc.accountId,
                    "name" to acc.name,
                    "group" to acc.group,
                    "sortOrder" to acc.sortOrder,
                    "balancePaisa" to acc.balancePaisa,
                    "capitalPaisa" to acc.capitalPaisa,
                    "allowNegativeBalance" to acc.allowNegativeBalance,
                    "memberUids" to acc.memberUids,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            }
            payload.partners.forEach { p ->
                baseOps += db.collection("partners").document(p.partnerId) to mapOf(
                    "partnerId" to p.partnerId,
                    "name" to p.name,
                    "ratePerThousandPaisa" to p.ratePerThousandPaisa,
                    "bkashOnlyCommission" to p.bkashOnlyCommission,
                    "sentTotalPaisa" to p.sentTotalPaisa,
                    "receivedTotalPaisa" to p.receivedTotalPaisa,
                    "currentBalancePaisa" to p.currentBalancePaisa,
                    "payableCommissionPaisa" to p.payableCommissionPaisa,
                    "paidCommissionPaisa" to p.paidCommissionPaisa,
                    "memberUids" to p.memberUids,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            }
            payload.employees.forEach { emp ->
                if (emp.uid != BusinessConstants.SUPER_ADMIN_UID) {
                    val empMap = mapOf(
                        "uid" to emp.uid,
                        "name" to emp.name,
                        "email" to emp.email,
                        "phone" to emp.phone,
                        "role" to emp.role,
                        "active" to emp.active,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ).filterValues { it != null } as Map<String, Any>
                    baseOps += db.collection("employees").document(emp.uid) to empMap
                }
            }
            commitInChunks(baseOps)

            onProgress(0.70f, "লেনদেন, লেজার ও ঋণ রিস্টোর হচ্ছে...")
            val recordOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

            payload.transactions.forEach { tx ->
                @Suppress("UNCHECKED_CAST")
                val txMap = mapOf(
                    "txType" to tx.txType,
                    "status" to tx.status,
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
                    "approvedBy" to tx.approvedBy,
                    "occurredAtMillis" to tx.occurredAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "approvedAt" to if (tx.approvedBy != null) FieldValue.serverTimestamp() else null,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "rejectionReason" to tx.rejectionReason,
                    "reversalReason" to tx.reversalReason,
                    "reversedByTxId" to tx.reversedByTxId,
                    "replacesTxId" to tx.replacesTxId,
                    "accountChangesSummary" to tx.accountChangesSummary,
                ).filterValues { it != null } as Map<String, Any>
                recordOps += db.collection("transactions").document(tx.id) to txMap
            }

            payload.ledger.forEach { le ->
                recordOps += db.collection("ledger").document(le.id) to mapOf(
                    "txId" to le.txId,
                    "txType" to le.txType,
                    "accountId" to le.accountId,
                    "accountName" to le.accountName,
                    "deltaPaisa" to le.deltaPaisa,
                    "previousBalancePaisa" to le.previousBalancePaisa,
                    "newBalancePaisa" to le.newBalancePaisa,
                    "capitalDeltaPaisa" to le.capitalDeltaPaisa,
                    "commissionIncomePaisa" to le.commissionIncomePaisa,
                    "partnerCommissionExpensePaisa" to le.partnerCommissionExpensePaisa,
                    "shopExpensePaisa" to le.shopExpensePaisa,
                    "interestIncomePaisa" to le.interestIncomePaisa,
                    "interestExpensePaisa" to le.interestExpensePaisa,
                    "actorUid" to le.actorUid,
                    "approvedBy" to le.approvedBy,
                    "note" to le.note,
                    "occurredAtMillis" to le.occurredAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            }

            payload.loans.forEach { loan ->
                @Suppress("UNCHECKED_CAST")
                val loanMap = mapOf(
                    "loanType" to loan.loanType,
                    "personName" to loan.personName,
                    "personPhone" to loan.personPhone,
                    "accountId" to loan.accountId,
                    "principalPaisa" to loan.principalPaisa,
                    "interestPaisa" to loan.interestPaisa,
                    "remainingPrincipalPaisa" to loan.remainingPrincipalPaisa,
                    "remainingInterestPaisa" to loan.remainingInterestPaisa,
                    "loanDateMillis" to loan.loanDateMillis,
                    "dueDateMillis" to loan.dueDateMillis,
                    "status" to loan.status,
                    "notes" to loan.notes,
                    "memberUids" to loan.memberUids,
                    "createdBy" to loan.createdBy,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null } as Map<String, Any>
                recordOps += db.collection("loans").document(loan.id) to loanMap
            }

            commitInChunks(recordOps)

            onProgress(0.95f, "অডিট লগ সংরক্ষণ হচ্ছে...")
            db.collection("audit_logs").document("log_${UUID.randomUUID().toString().take(12)}").set(
                mapOf(
                    "action" to "BACKUP_RESTORE",
                    "actorUid" to uid,
                    "actorName" to ownerName,
                    "targetType" to "SYSTEM",
                    "targetId" to "backup_restore",
                    "details" to "রিস্টোর সম্পন্ন: ${payload.transactions.size}টি লেনদেন, ${payload.ledger.size}টি লেজার এন্ট্রি",
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            onProgress(1.0f, "রিস্টোর সফলভাবে সম্পন্ন হয়েছে!")
            Result.success(safetyJson)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "restore")
            Result.failure(Exception(translateErrorToBengali(e), e))
        }
    }

    private suspend fun deleteCollectionInChunks(collectionName: String) {
        val docs = db.collection(collectionName).get().await().documents
        docs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private suspend fun commitInChunks(ops: List<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>) {
        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit().await()
        }
    }
}
