package com.example.domain

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val exportedBy: String,
    val accounts: List<FinancialAccount>,
    val partners: List<PartnerAccount>,
    val transactions: List<BusinessTransaction>,
    val ledger: List<LedgerEntry>,
    val loans: List<LoanRecord>,
    val employees: List<Employee>,
)

object BackupSerializer {
    const val CURRENT_SCHEMA_VERSION = 1

    fun toJson(payload: BackupPayload): String {
        val root = JSONObject()
        root.put("schemaVersion", payload.schemaVersion)
        root.put("exportedAtMillis", payload.exportedAtMillis)
        root.put("exportedBy", payload.exportedBy)
        root.put("app", BusinessConstants.APP_NAME_BN)

        root.put("accounts", JSONArray().apply {
            payload.accounts.forEach { acc ->
                put(JSONObject().apply {
                    put("accountId", acc.accountId)
                    put("name", acc.name)
                    put("group", acc.group)
                    put("sortOrder", acc.sortOrder)
                    put("balancePaisa", acc.balancePaisa)
                    put("capitalPaisa", acc.capitalPaisa)
                    put("allowNegativeBalance", acc.allowNegativeBalance)
                    put("memberUids", JSONArray(acc.memberUids))
                })
            }
        })

        root.put("partners", JSONArray().apply {
            payload.partners.forEach { p ->
                put(JSONObject().apply {
                    put("partnerId", p.partnerId)
                    put("name", p.name)
                    put("ratePerThousandPaisa", p.ratePerThousandPaisa)
                    put("bkashOnlyCommission", p.bkashOnlyCommission)
                    put("sentTotalPaisa", p.sentTotalPaisa)
                    put("receivedTotalPaisa", p.receivedTotalPaisa)
                    put("currentBalancePaisa", p.currentBalancePaisa)
                    put("payableCommissionPaisa", p.payableCommissionPaisa)
                    put("paidCommissionPaisa", p.paidCommissionPaisa)
                    put("memberUids", JSONArray(p.memberUids))
                })
            }
        })

        root.put("transactions", JSONArray().apply {
            payload.transactions.forEach { tx ->
                put(JSONObject().apply {
                    put("id", tx.id)
                    put("txType", tx.txType)
                    put("status", tx.status)
                    tx.customerName?.let { put("customerName", it) }
                    tx.customerPhone?.let { put("customerPhone", it) }
                    tx.operator?.let { put("operator", it) }
                    tx.billType?.let { put("billType", it) }
                    tx.expenseCategory?.let { put("expenseCategory", it) }
                    tx.partnerId?.let { put("partnerId", it) }
                    tx.loanId?.let { put("loanId", it) }
                    tx.loanType?.let { put("loanType", it) }
                    tx.receivedAccountId?.let { put("receivedAccountId", it) }
                    put("receivedAmountPaisa", tx.receivedAmountPaisa)
                    tx.paidAccountId?.let { put("paidAccountId", it) }
                    put("paidAmountPaisa", tx.paidAmountPaisa)
                    put("manualCommissionPaisa", tx.manualCommissionPaisa)
                    put("partnerCommissionPaisa", tx.partnerCommissionPaisa)
                    put("explicitFeePaisa", tx.explicitFeePaisa)
                    put("interestPaisa", tx.interestPaisa)
                    put("adjustmentSign", tx.adjustmentSign)
                    tx.referenceTxId?.let { put("referenceTxId", it) }
                    tx.notes?.let { put("notes", it) }
                    tx.calculationExplanation?.let { put("calculationExplanation", it) }
                    put("createdBy", tx.createdBy)
                    put("createdByName", tx.createdByName)
                    tx.approvedBy?.let { put("approvedBy", it) }
                    put("occurredAtMillis", tx.occurredAtMillis)
                    tx.rejectionReason?.let { put("rejectionReason", it) }
                    tx.reversalReason?.let { put("reversalReason", it) }
                    tx.reversedByTxId?.let { put("reversedByTxId", it) }
                    tx.replacesTxId?.let { put("replacesTxId", it) }
                    tx.accountChangesSummary?.let { put("accountChangesSummary", it) }
                })
            }
        })

        root.put("ledger", JSONArray().apply {
            payload.ledger.forEach { le ->
                put(JSONObject().apply {
                    put("id", le.id)
                    put("txId", le.txId)
                    put("txType", le.txType)
                    put("accountId", le.accountId)
                    put("accountName", le.accountName)
                    put("deltaPaisa", le.deltaPaisa)
                    put("previousBalancePaisa", le.previousBalancePaisa)
                    put("newBalancePaisa", le.newBalancePaisa)
                    put("capitalDeltaPaisa", le.capitalDeltaPaisa)
                    put("commissionIncomePaisa", le.commissionIncomePaisa)
                    put("partnerCommissionExpensePaisa", le.partnerCommissionExpensePaisa)
                    put("shopExpensePaisa", le.shopExpensePaisa)
                    put("interestIncomePaisa", le.interestIncomePaisa)
                    put("interestExpensePaisa", le.interestExpensePaisa)
                    put("actorUid", le.actorUid)
                    put("approvedBy", le.approvedBy)
                    put("note", le.note)
                    put("occurredAtMillis", le.occurredAtMillis)
                })
            }
        })

        root.put("loans", JSONArray().apply {
            payload.loans.forEach { loan ->
                put(JSONObject().apply {
                    put("id", loan.id)
                    put("loanType", loan.loanType)
                    put("personName", loan.personName)
                    loan.personPhone?.let { put("personPhone", it) }
                    put("accountId", loan.accountId)
                    put("principalPaisa", loan.principalPaisa)
                    put("interestPaisa", loan.interestPaisa)
                    put("remainingPrincipalPaisa", loan.remainingPrincipalPaisa)
                    put("remainingInterestPaisa", loan.remainingInterestPaisa)
                    put("loanDateMillis", loan.loanDateMillis)
                    put("dueDateMillis", loan.dueDateMillis)
                    put("status", loan.status)
                    loan.notes?.let { put("notes", it) }
                    put("memberUids", JSONArray(loan.memberUids))
                    put("createdBy", loan.createdBy)
                })
            }
        })

        root.put("employees", JSONArray().apply {
            payload.employees.forEach { emp ->
                put(JSONObject().apply {
                    put("uid", emp.uid)
                    put("name", emp.name)
                    put("email", emp.email)
                    emp.phone?.let { put("phone", it) }
                    put("role", emp.role)
                    put("active", emp.active)
                })
            }
        })

        return root.toString(2)
    }

    fun parseAndValidate(jsonString: String): Result<BackupPayload> {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("schemaVersion", -1)
            if (version != CURRENT_SCHEMA_VERSION) {
                return Result.failure(IllegalArgumentException("অসমর্থিত ব্যাকআপ সংস্করণ (schemaVersion=$version)।"))
            }
            if (!root.has("accounts") || !root.has("partners") || !root.has("transactions") || !root.has("ledger")) {
                return Result.failure(IllegalArgumentException("অসম্পূর্ণ ব্যাকআপ ফাইল: প্রয়োজনীয় সেকশন পাওয়া যায়নি।"))
            }

            val accountsArr = root.getJSONArray("accounts")
            val accounts = mutableListOf<FinancialAccount>()
            for (i in 0 until accountsArr.length()) {
                val obj = accountsArr.getJSONObject(i)
                val accId = obj.getString("accountId")
                if (BusinessConstants.ALL_ACCOUNTS.none { it.accountId == accId }) {
                    return Result.failure(IllegalArgumentException("অবৈধ অ্যাকাউন্ট আইডি: $accId"))
                }
                accounts += FinancialAccount(
                    id = accId,
                    accountId = accId,
                    name = obj.getString("name"),
                    group = obj.getString("group"),
                    sortOrder = obj.getLong("sortOrder"),
                    balancePaisa = obj.getLong("balancePaisa"),
                    capitalPaisa = obj.getLong("capitalPaisa"),
                    allowNegativeBalance = obj.optBoolean("allowNegativeBalance", false),
                    memberUids = jsonArrayToStrings(obj.optJSONArray("memberUids")).ifEmpty {
                        listOf(BusinessConstants.SUPER_ADMIN_UID)
                    },
                )
            }
            val requiredIds = BusinessConstants.ALL_ACCOUNTS.map { it.accountId }.toSet()
            if (accounts.map { it.accountId }.toSet() != requiredIds) {
                return Result.failure(IllegalArgumentException("ব্যাকআপে ১১টি অ্যাকাউন্ট সম্পূর্ণ থাকতে হবে।"))
            }

            val partnersArr = root.getJSONArray("partners")
            val partners = mutableListOf<PartnerAccount>()
            for (i in 0 until partnersArr.length()) {
                val obj = partnersArr.getJSONObject(i)
                val pId = obj.getString("partnerId")
                if (BusinessConstants.ALL_PARTNERS.none { it.partnerId == pId }) {
                    return Result.failure(IllegalArgumentException("অবৈধ পার্টনার আইডি: $pId"))
                }
                partners += PartnerAccount(
                    id = pId,
                    partnerId = pId,
                    name = obj.getString("name"),
                    ratePerThousandPaisa = obj.getLong("ratePerThousandPaisa"),
                    bkashOnlyCommission = obj.optBoolean("bkashOnlyCommission", true),
                    sentTotalPaisa = obj.getLong("sentTotalPaisa"),
                    receivedTotalPaisa = obj.getLong("receivedTotalPaisa"),
                    currentBalancePaisa = obj.getLong("currentBalancePaisa"),
                    payableCommissionPaisa = obj.getLong("payableCommissionPaisa"),
                    paidCommissionPaisa = obj.getLong("paidCommissionPaisa"),
                    memberUids = jsonArrayToStrings(obj.optJSONArray("memberUids")).ifEmpty {
                        listOf(BusinessConstants.SUPER_ADMIN_UID)
                    },
                )
            }

            val txArr = root.getJSONArray("transactions")
            val transactions = mutableListOf<BusinessTransaction>()
            for (i in 0 until txArr.length()) {
                val obj = txArr.getJSONObject(i)
                val txId = obj.getString("id")
                require(txId.isNotBlank()) { "লেনদেন আইডি খালি থাকতে পারবে না।" }
                transactions += BusinessTransaction(
                    id = txId,
                    txType = obj.getString("txType"),
                    status = obj.getString("status"),
                    customerName = obj.optStringOrNull("customerName"),
                    customerPhone = obj.optStringOrNull("customerPhone"),
                    operator = obj.optStringOrNull("operator"),
                    billType = obj.optStringOrNull("billType"),
                    expenseCategory = obj.optStringOrNull("expenseCategory"),
                    partnerId = obj.optStringOrNull("partnerId"),
                    loanId = obj.optStringOrNull("loanId"),
                    loanType = obj.optStringOrNull("loanType"),
                    receivedAccountId = obj.optStringOrNull("receivedAccountId"),
                    receivedAmountPaisa = obj.getLong("receivedAmountPaisa"),
                    paidAccountId = obj.optStringOrNull("paidAccountId"),
                    paidAmountPaisa = obj.getLong("paidAmountPaisa"),
                    manualCommissionPaisa = obj.getLong("manualCommissionPaisa"),
                    partnerCommissionPaisa = obj.getLong("partnerCommissionPaisa"),
                    explicitFeePaisa = obj.getLong("explicitFeePaisa"),
                    interestPaisa = obj.getLong("interestPaisa"),
                    adjustmentSign = obj.getLong("adjustmentSign"),
                    referenceTxId = obj.optStringOrNull("referenceTxId"),
                    notes = obj.optStringOrNull("notes"),
                    calculationExplanation = obj.optStringOrNull("calculationExplanation"),
                    createdBy = obj.getString("createdBy"),
                    createdByName = obj.getString("createdByName"),
                    approvedBy = obj.optStringOrNull("approvedBy"),
                    occurredAtMillis = obj.getLong("occurredAtMillis"),
                    rejectionReason = obj.optStringOrNull("rejectionReason"),
                    reversalReason = obj.optStringOrNull("reversalReason"),
                    reversedByTxId = obj.optStringOrNull("reversedByTxId"),
                    replacesTxId = obj.optStringOrNull("replacesTxId"),
                    accountChangesSummary = obj.optStringOrNull("accountChangesSummary"),
                )
            }

            val ledgerArr = root.getJSONArray("ledger")
            val ledger = mutableListOf<LedgerEntry>()
            for (i in 0 until ledgerArr.length()) {
                val obj = ledgerArr.getJSONObject(i)
                val id = obj.getString("id")
                require(id.isNotBlank()) { "লেজার আইডি খালি থাকতে পারবে না।" }
                ledger += LedgerEntry(
                    id = id,
                    txId = obj.getString("txId"),
                    txType = obj.getString("txType"),
                    accountId = obj.getString("accountId"),
                    accountName = obj.getString("accountName"),
                    deltaPaisa = obj.getLong("deltaPaisa"),
                    previousBalancePaisa = obj.getLong("previousBalancePaisa"),
                    newBalancePaisa = obj.getLong("newBalancePaisa"),
                    capitalDeltaPaisa = obj.getLong("capitalDeltaPaisa"),
                    commissionIncomePaisa = obj.getLong("commissionIncomePaisa"),
                    partnerCommissionExpensePaisa = obj.getLong("partnerCommissionExpensePaisa"),
                    shopExpensePaisa = obj.getLong("shopExpensePaisa"),
                    interestIncomePaisa = obj.getLong("interestIncomePaisa"),
                    interestExpensePaisa = obj.getLong("interestExpensePaisa"),
                    actorUid = obj.getString("actorUid"),
                    approvedBy = obj.getString("approvedBy"),
                    note = obj.optString("note", ""),
                    occurredAtMillis = obj.getLong("occurredAtMillis"),
                )
            }

            val loansArr = root.optJSONArray("loans") ?: JSONArray()
            val loans = mutableListOf<LoanRecord>()
            for (i in 0 until loansArr.length()) {
                val obj = loansArr.getJSONObject(i)
                val id = obj.getString("id")
                loans += LoanRecord(
                    id = id,
                    loanType = obj.getString("loanType"),
                    personName = obj.getString("personName"),
                    personPhone = obj.optStringOrNull("personPhone"),
                    accountId = obj.getString("accountId"),
                    principalPaisa = obj.getLong("principalPaisa"),
                    interestPaisa = obj.getLong("interestPaisa"),
                    remainingPrincipalPaisa = obj.getLong("remainingPrincipalPaisa"),
                    remainingInterestPaisa = obj.getLong("remainingInterestPaisa"),
                    loanDateMillis = obj.getLong("loanDateMillis"),
                    dueDateMillis = obj.getLong("dueDateMillis"),
                    status = obj.getString("status"),
                    notes = obj.optStringOrNull("notes"),
                    memberUids = jsonArrayToStrings(obj.optJSONArray("memberUids")).ifEmpty {
                        listOf(BusinessConstants.SUPER_ADMIN_UID)
                    },
                    createdBy = obj.getString("createdBy"),
                )
            }

            val employeesArr = root.optJSONArray("employees") ?: JSONArray()
            val employees = mutableListOf<Employee>()
            for (i in 0 until employeesArr.length()) {
                val obj = employeesArr.getJSONObject(i)
                val uid = obj.getString("uid")
                val rawRole = obj.getString("role")
                val safeRole = if (uid == BusinessConstants.SUPER_ADMIN_UID) UserRole.OWNER.name else UserRole.EMPLOYEE.name
                if (rawRole == UserRole.OWNER.name && uid != BusinessConstants.SUPER_ADMIN_UID) {
                    return Result.failure(IllegalArgumentException("অবৈধ ব্যাকআপ: সুপার অ্যাডমিন UID ছাড়া অন্য কাউকে OWNER করা যাবে না।"))
                }
                employees += Employee(
                    id = uid,
                    uid = uid,
                    name = obj.getString("name"),
                    email = obj.getString("email"),
                    phone = obj.optStringOrNull("phone"),
                    role = safeRole,
                    active = obj.optBoolean("active", true),
                )
            }

            Result.success(
                BackupPayload(
                    schemaVersion = version,
                    exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis()),
                    exportedBy = root.optString("exportedBy", BusinessConstants.SUPER_ADMIN_UID),
                    accounts = accounts,
                    partners = partners,
                    transactions = transactions,
                    ledger = ledger,
                    loans = loans,
                    employees = employees,
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("অবৈধ ব্যাকআপ ফাইল: ${e.localizedMessage ?: "JSON পার্স ব্যর্থ"}"))
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

    private fun jsonArrayToStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
    }
}
