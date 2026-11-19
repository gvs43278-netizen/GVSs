package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val labelBn: String) {
    OWNER("সুপার অ্যাডমিন (Super Admin)"),
    EMPLOYEE("সাধারণ মেম্বার (General Member)");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: EMPLOYEE
    }
}

enum class AccountGroup(val labelBn: String) {
    CASH("নগদ ক্যাশ"),
    BKASH("বিকাশ"),
    MFS("মোবাইল ব্যাংকিং"),
    BANK("ব্যাংক"),
    RECHARGE("মোবাইল রিচার্জ");

    companion object {
        fun fromRaw(raw: String?): AccountGroup =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

enum class TxType(val labelBn: String) {
    CUSTOMER_TX("গ্রাহক লেনদেন"),
    INTERNAL_TRANSFER("অভ্যন্তরীণ ট্রান্সফার"),
    MOBILE_RECHARGE("মোবাইল রিচার্জ"),
    CAPITAL_ADD("মূলধন যোগ"),
    CAPITAL_WITHDRAW("মূলধন উত্তোলন"),
    PARTNER_SEND("পার্টনারকে পাঠানো"),
    PARTNER_RECEIVE("পার্টনার থেকে পাওয়া"),
    PARTNER_COMMISSION_PAY("পার্টনার কমিশন পরিশোধ"),
    LOAN_BORROW("ঋণ গ্রহণ (দায়)"),
    LOAN_LEND("ঋণ প্রদান (পাওনা)"),
    LOAN_REPAY("ঋণ পরিশোধ"),
    BILL_PAYMENT("বিল পেমেন্ট"),
    SHOP_EXPENSE("দোকানের খরচ"),
    BALANCE_ADJUSTMENT("ব্যালেন্স সমন্বয়"),
    REVERSAL("রিভার্সাল (সংশোধন)");

    companion object {
        fun fromRaw(raw: String?): TxType =
            entries.firstOrNull { it.name == raw } ?: CUSTOMER_TX
    }
}

enum class TxStatus(val labelBn: String) {
    PENDING("অপেক্ষমাণ (Pending)"),
    APPROVED("অনুমোদিত (Approved)"),
    REJECTED("বাতিল (Rejected)");

    companion object {
        fun fromRaw(raw: String?): TxStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class BillType(val labelBn: String) {
    ELECTRICITY("বিদ্যুৎ বিল"),
    WIFI("ওয়াইফাই বিল");

    companion object {
        fun fromRaw(raw: String?): BillType =
            entries.firstOrNull { it.name == raw } ?: ELECTRICITY
    }
}

enum class ExpenseCategory(val labelBn: String) {
    WIFI_BILL("ওয়াইফাই বিল"),
    ELECTRICITY_BILL("বিদ্যুৎ বিল"),
    SHOP_RENT("দোকান ভাড়া"),
    OTHER("অন্যান্য খরচ");

    companion object {
        fun fromRaw(raw: String?): ExpenseCategory =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

enum class LoanType(val labelBn: String) {
    BORROWED("ধার নিয়েছি (দায় / Liability)"),
    LENT("ধার দিয়েছি (পাওনা / Asset)");

    companion object {
        fun fromRaw(raw: String?): LoanType =
            entries.firstOrNull { it.name == raw } ?: BORROWED
    }
}

enum class LoanStatus(val labelBn: String) {
    OPEN("বকেয়া"),
    PARTIAL("আংশিক পরিশোধিত"),
    PAID("সম্পূর্ণ পরিশোধিত");

    companion object {
        fun fromRaw(raw: String?): LoanStatus =
            entries.firstOrNull { it.name == raw } ?: OPEN
    }
}

enum class ReportPeriod(val labelBn: String) {
    DAILY("দৈনিক"),
    WEEKLY("সাপ্তাহিক"),
    MONTHLY("মাসিক"),
    YEARLY("বাৎসরিক"),
    ALL("সর্বমোট")
}

@Keep
@IgnoreExtraProperties
data class Employee(
    @DocumentId val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String? = null,
    val role: String = UserRole.EMPLOYEE.name,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class EmployeeInvite(
    @DocumentId val id: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String? = null,
    val role: String = UserRole.EMPLOYEE.name,
    val active: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FinancialAccount(
    @DocumentId val id: String = "",
    val accountId: String = "",
    val name: String = "",
    val group: String = AccountGroup.CASH.name,
    val sortOrder: Long = 0L,
    val balancePaisa: Long = 0L,
    val capitalPaisa: Long = 0L,
    val allowNegativeBalance: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PartnerAccount(
    @DocumentId val id: String = "",
    val partnerId: String = "",
    val name: String = "",
    val ratePerThousandPaisa: Long = 0L,
    val bkashOnlyCommission: Boolean = true,
    val sentTotalPaisa: Long = 0L,
    val receivedTotalPaisa: Long = 0L,
    val currentBalancePaisa: Long = 0L,
    val payableCommissionPaisa: Long = 0L,
    val paidCommissionPaisa: Long = 0L,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BusinessTransaction(
    @DocumentId val id: String = "",
    val txType: String = TxType.CUSTOMER_TX.name,
    val status: String = TxStatus.PENDING.name,
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
    val partnerCommissionPaisa: Long = 0L,
    val explicitFeePaisa: Long = 0L,
    val interestPaisa: Long = 0L,
    val adjustmentSign: Long = 0L,
    val referenceTxId: String? = null,
    val notes: String? = null,
    val calculationExplanation: String? = null,
    val createdBy: String = "",
    val createdByName: String = "",
    val approvedBy: String? = null,
    val occurredAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val approvedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val rejectionReason: String? = null,
    val reversalReason: String? = null,
    val reversedByTxId: String? = null,
    val replacesTxId: String? = null,
    val accountChangesSummary: String? = null,
)

@Keep
@IgnoreExtraProperties
data class LedgerEntry(
    @DocumentId val id: String = "",
    val txId: String = "",
    val txType: String = TxType.CUSTOMER_TX.name,
    val accountId: String = "",
    val accountName: String = "",
    val deltaPaisa: Long = 0L,
    val previousBalancePaisa: Long = 0L,
    val newBalancePaisa: Long = 0L,
    val capitalDeltaPaisa: Long = 0L,
    val commissionIncomePaisa: Long = 0L,
    val partnerCommissionExpensePaisa: Long = 0L,
    val shopExpensePaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
    val actorUid: String = "",
    val approvedBy: String = "",
    val note: String = "",
    val occurredAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class LoanRecord(
    @DocumentId val id: String = "",
    val loanType: String = LoanType.BORROWED.name,
    val personName: String = "",
    val personPhone: String? = null,
    val accountId: String = BusinessConstants.ACC_CASH,
    val principalPaisa: Long = 0L,
    val interestPaisa: Long = 0L,
    val remainingPrincipalPaisa: Long = 0L,
    val remainingInterestPaisa: Long = 0L,
    val loanDateMillis: Long = 0L,
    val dueDateMillis: Long = 0L,
    val status: String = LoanStatus.OPEN.name,
    val notes: String? = null,
    val memberUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BusinessSettings(
    @DocumentId val id: String = "",
    val businessName: String = BusinessConstants.APP_NAME_BN,
    val superAdminUid: String = BusinessConstants.SUPER_ADMIN_UID,
    val initialized: Boolean = false,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
