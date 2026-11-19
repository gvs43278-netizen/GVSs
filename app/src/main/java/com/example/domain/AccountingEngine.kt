package com.example.domain

import java.util.Calendar
import kotlin.math.max

data class AccountDelta(
    val accountId: String,
    val balanceDeltaPaisa: Long,
    val capitalDeltaPaisa: Long = 0L,
    val note: String,
)

data class PartnerDelta(
    val partnerId: String,
    val sentDeltaPaisa: Long = 0L,
    val receivedDeltaPaisa: Long = 0L,
    val currentBalanceDeltaPaisa: Long = 0L,
    val payableCommissionDeltaPaisa: Long = 0L,
    val paidCommissionDeltaPaisa: Long = 0L,
)

data class ComputedTxEffects(
    val accountDeltas: List<AccountDelta>,
    val partnerDelta: PartnerDelta? = null,
    val commissionIncomePaisa: Long = 0L,
    val partnerCommissionExpensePaisa: Long = 0L,
    val shopExpensePaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
    val calculationExplanation: String? = null,
)

data class ProfitLossSummary(
    val commissionIncomePaisa: Long = 0L,
    val shopExpensesPaisa: Long = 0L,
    val partnerCommissionExpensesPaisa: Long = 0L,
    val totalProfitPaisa: Long = 0L,
    val totalLossPaisa: Long = 0L,
    val netProfitLossPaisa: Long = 0L,
    val interestIncomePaisa: Long = 0L,
    val interestExpensePaisa: Long = 0L,
)

object AccountingEngine {

    fun calculatePartnerCommission(
        partner: PartnerAccount,
        sourceAccountId: String?,
        amountPaisa: Long,
        isSendToPartner: Boolean,
    ): Pair<Long, String> {
        if (!isSendToPartner) {
            return 0L to "পার্টনার থেকে টাকা পাওয়া গেছে — কমিশন ব্যয় ৳0"
        }
        if (amountPaisa <= 0L || partner.ratePerThousandPaisa <= 0L) {
            return 0L to "${partner.name}-এর জন্য কমিশন চার্জ ৳0 — প্রদত্ত/প্রাপ্ত সম্পূর্ণ পরিমাণ সরাসরি রেকর্ড হবে"
        }
        if (partner.bkashOnlyCommission && !BusinessConstants.isBkashAccount(sourceAccountId)) {
            val accName = BusinessConstants.accountDisplayName(sourceAccountId)
            return 0L to "$accName থেকে পাঠানো হয়েছে (শুধুমাত্র bKash-এ কমিশন প্রযোজ্য) — কমিশন ব্যয় ৳0"
        }
        val commissionPaisa = (amountPaisa * partner.ratePerThousandPaisa) / 100_000L
        val accName = BusinessConstants.accountDisplayName(sourceAccountId)
        val explanation =
            "$accName থেকে ${MoneyFormatter.formatTaka(amountPaisa)} পাঠানো হয়েছে × (${MoneyFormatter.formatTaka(partner.ratePerThousandPaisa)} / ৳1,000) = ${MoneyFormatter.formatTaka(commissionPaisa)} পার্টনার কমিশন ব্যয়"
        return commissionPaisa to explanation
    }

    fun computeEffectsForApproval(
        tx: BusinessTransaction,
        partnersById: Map<String, PartnerAccount>,
    ): Result<ComputedTxEffects> {
        val type = TxType.fromRaw(tx.txType)
        return when (type) {
            TxType.CUSTOMER_TX,
            TxType.MOBILE_RECHARGE,
            TxType.BILL_PAYMENT -> {
                val deltas = mutableListOf<AccountDelta>()
                if (!tx.receivedAccountId.isNullOrBlank() && tx.receivedAmountPaisa > 0L) {
                    deltas += AccountDelta(
                        accountId = tx.receivedAccountId,
                        balanceDeltaPaisa = tx.receivedAmountPaisa,
                        note = "${type.labelBn}: গ্রহণ (${tx.customerName ?: "গ্রাহক"})",
                    )
                }
                if (!tx.paidAccountId.isNullOrBlank() && tx.paidAmountPaisa > 0L) {
                    deltas += AccountDelta(
                        accountId = tx.paidAccountId,
                        balanceDeltaPaisa = -tx.paidAmountPaisa,
                        note = "${type.labelBn}: প্রদান (${tx.customerName ?: "গ্রাহক"})",
                    )
                }
                if (deltas.isEmpty()) {
                    return Result.failure(IllegalArgumentException("গ্রহণ বা প্রদান পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = deltas,
                        commissionIncomePaisa = tx.manualCommissionPaisa,
                    )
                )
            }

            TxType.INTERNAL_TRANSFER -> {
                val fromId = tx.paidAccountId
                    ?: return Result.failure(IllegalArgumentException("সোর্স অ্যাকাউন্ট নির্বাচন করুন।"))
                val toId = tx.receivedAccountId
                    ?: return Result.failure(IllegalArgumentException("গন্তব্য অ্যাকাউন্ট নির্বাচন করুন।"))
                if (fromId == toId) {
                    return Result.failure(IllegalArgumentException("একই অ্যাকাউন্টে ট্রান্সফার করা যাবে না।"))
                }
                if (tx.paidAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("ট্রান্সফার পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                val totalDebit = tx.paidAmountPaisa + tx.explicitFeePaisa
                val creditAmount = if (tx.receivedAmountPaisa > 0L) tx.receivedAmountPaisa else tx.paidAmountPaisa
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = fromId,
                                balanceDeltaPaisa = -totalDebit,
                                note = "অভ্যন্তরীণ ট্রান্সফার (প্রেরণ -> ${BusinessConstants.accountDisplayName(toId)})",
                            ),
                            AccountDelta(
                                accountId = toId,
                                balanceDeltaPaisa = creditAmount,
                                note = "অভ্যন্তরীণ ট্রান্সফার (গ্রহণ <- ${BusinessConstants.accountDisplayName(fromId)})",
                            ),
                        ),
                        shopExpensePaisa = tx.explicitFeePaisa,
                    )
                )
            }

            TxType.CAPITAL_ADD -> {
                val accId = tx.receivedAccountId
                    ?: return Result.failure(IllegalArgumentException("মূলধন অ্যাকাউন্ট নির্বাচন করুন।"))
                if (tx.receivedAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("মূলধনের পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = tx.receivedAmountPaisa,
                                capitalDeltaPaisa = tx.receivedAmountPaisa,
                                note = "মূলধন যোগ: ${tx.notes ?: ""}".trim(),
                            )
                        )
                    )
                )
            }

            TxType.CAPITAL_WITHDRAW -> {
                val accId = tx.paidAccountId
                    ?: return Result.failure(IllegalArgumentException("মূলধন উত্তোলনের অ্যাকাউন্ট নির্বাচন করুন।"))
                if (tx.paidAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("উত্তোলনের পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = -tx.paidAmountPaisa,
                                capitalDeltaPaisa = -tx.paidAmountPaisa,
                                note = "মূলধন উত্তোলন: ${tx.notes ?: ""}".trim(),
                            )
                        )
                    )
                )
            }

            TxType.PARTNER_SEND -> {
                val accId = tx.paidAccountId
                    ?: return Result.failure(IllegalArgumentException("সোর্স অ্যাকাউন্ট নির্বাচন করুন।"))
                val partnerId = tx.partnerId
                    ?: return Result.failure(IllegalArgumentException("পার্টনার নির্বাচন করুন।"))
                val partner = partnersById[partnerId]
                    ?: return Result.failure(IllegalArgumentException("অজানা পার্টনার: $partnerId"))
                if (tx.paidAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("টাকার পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                val (commissionPaisa, explanation) = calculatePartnerCommission(
                    partner = partner,
                    sourceAccountId = accId,
                    amountPaisa = tx.paidAmountPaisa,
                    isSendToPartner = true,
                )
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = -tx.paidAmountPaisa,
                                note = "${partner.name}-কে পাঠানো হয়েছে",
                            )
                        ),
                        partnerDelta = PartnerDelta(
                            partnerId = partnerId,
                            sentDeltaPaisa = tx.paidAmountPaisa,
                            currentBalanceDeltaPaisa = tx.paidAmountPaisa,
                            payableCommissionDeltaPaisa = commissionPaisa,
                        ),
                        partnerCommissionExpensePaisa = commissionPaisa,
                        calculationExplanation = explanation,
                    )
                )
            }

            TxType.PARTNER_RECEIVE -> {
                val accId = tx.receivedAccountId
                    ?: return Result.failure(IllegalArgumentException("গ্রহণকারী অ্যাকাউন্ট নির্বাচন করুন।"))
                val partnerId = tx.partnerId
                    ?: return Result.failure(IllegalArgumentException("পার্টনার নির্বাচন করুন।"))
                val partner = partnersById[partnerId]
                    ?: return Result.failure(IllegalArgumentException("অজানা পার্টনার: $partnerId"))
                if (tx.receivedAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("টাকার পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                val (_, explanation) = calculatePartnerCommission(
                    partner = partner,
                    sourceAccountId = accId,
                    amountPaisa = tx.receivedAmountPaisa,
                    isSendToPartner = false,
                )
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = tx.receivedAmountPaisa,
                                note = "${partner.name} থেকে পাওয়া গেছে",
                            )
                        ),
                        partnerDelta = PartnerDelta(
                            partnerId = partnerId,
                            receivedDeltaPaisa = tx.receivedAmountPaisa,
                            currentBalanceDeltaPaisa = -tx.receivedAmountPaisa,
                        ),
                        partnerCommissionExpensePaisa = 0L,
                        calculationExplanation = explanation,
                    )
                )
            }

            TxType.PARTNER_COMMISSION_PAY -> {
                val accId = tx.paidAccountId
                    ?: return Result.failure(IllegalArgumentException("পেমেন্ট অ্যাকাউন্ট নির্বাচন করুন।"))
                val partnerId = tx.partnerId
                    ?: return Result.failure(IllegalArgumentException("পার্টনার নির্বাচন করুন।"))
                if (tx.paidAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("কমিশন পরিশোধের পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = -tx.paidAmountPaisa,
                                note = "পার্টনার কমিশন পরিশোধ",
                            )
                        ),
                        partnerDelta = PartnerDelta(
                            partnerId = partnerId,
                            payableCommissionDeltaPaisa = -tx.paidAmountPaisa,
                            paidCommissionDeltaPaisa = tx.paidAmountPaisa,
                        ),
                    )
                )
            }

            TxType.SHOP_EXPENSE -> {
                val accId = tx.paidAccountId
                    ?: return Result.failure(IllegalArgumentException("খরচের অ্যাকাউন্ট নির্বাচন করুন।"))
                if (tx.paidAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("খরচের পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                val catLabel = ExpenseCategory.fromRaw(tx.expenseCategory).labelBn
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = -tx.paidAmountPaisa,
                                note = "দোকানের খরচ ($catLabel): ${tx.notes ?: ""}".trim(),
                            )
                        ),
                        shopExpensePaisa = tx.paidAmountPaisa,
                    )
                )
            }

            TxType.LOAN_BORROW -> {
                val accId = tx.receivedAccountId
                    ?: return Result.failure(IllegalArgumentException("গ্রহণকারী অ্যাকাউন্ট নির্বাচন করুন।"))
                if (tx.receivedAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("ঋণের মূল পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = tx.receivedAmountPaisa,
                                note = "ঋণ গ্রহণ (${tx.customerName ?: "ব্যক্তি"})",
                            )
                        )
                    )
                )
            }

            TxType.LOAN_LEND -> {
                val accId = tx.paidAccountId
                    ?: return Result.failure(IllegalArgumentException("প্রদানকারী অ্যাকাউন্ট নির্বাচন করুন।"))
                if (tx.paidAmountPaisa <= 0L) {
                    return Result.failure(IllegalArgumentException("ঋণের মূল পরিমাণ ০-এর বেশি হতে হবে।"))
                }
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = -tx.paidAmountPaisa,
                                note = "ঋণ প্রদান (${tx.customerName ?: "ব্যক্তি"})",
                            )
                        )
                    )
                )
            }

            TxType.LOAN_REPAY -> {
                val loanType = LoanType.fromRaw(tx.loanType)
                if (loanType == LoanType.BORROWED) {
                    val accId = tx.paidAccountId
                        ?: return Result.failure(IllegalArgumentException("পরিশোধের অ্যাকাউন্ট নির্বাচন করুন।"))
                    val totalPaid = tx.paidAmountPaisa + tx.interestPaisa
                    if (totalPaid <= 0L) {
                        return Result.failure(IllegalArgumentException("পরিশোধের পরিমাণ ০-এর বেশি হতে হবে।"))
                    }
                    Result.success(
                        ComputedTxEffects(
                            accountDeltas = listOf(
                                AccountDelta(
                                    accountId = accId,
                                    balanceDeltaPaisa = -totalPaid,
                                    note = "ঋণ পরিশোধ (${tx.customerName ?: ""})",
                                )
                            ),
                            interestExpensePaisa = tx.interestPaisa,
                        )
                    )
                } else {
                    val accId = tx.receivedAccountId
                        ?: return Result.failure(IllegalArgumentException("গ্রহণকারী অ্যাকাউন্ট নির্বাচন করুন।"))
                    val totalReceived = tx.receivedAmountPaisa + tx.interestPaisa
                    if (totalReceived <= 0L) {
                        return Result.failure(IllegalArgumentException("আদায়ের পরিমাণ ০-এর বেশি হতে হবে।"))
                    }
                    Result.success(
                        ComputedTxEffects(
                            accountDeltas = listOf(
                                AccountDelta(
                                    accountId = accId,
                                    balanceDeltaPaisa = totalReceived,
                                    note = "ধার দেওয়া টাকা ফেরত (${tx.customerName ?: ""})",
                                )
                            ),
                            interestIncomePaisa = tx.interestPaisa,
                        )
                    )
                }
            }

            TxType.BALANCE_ADJUSTMENT -> {
                val isPlus = tx.adjustmentSign >= 0L
                val accId = if (isPlus) tx.receivedAccountId else tx.paidAccountId
                val amt = if (isPlus) tx.receivedAmountPaisa else tx.paidAmountPaisa
                if (accId.isNullOrBlank() || amt <= 0L) {
                    return Result.failure(IllegalArgumentException("সমন্বয়ের অ্যাকাউন্ট ও পরিমাণ সঠিক হতে হবে।"))
                }
                val delta = if (isPlus) amt else -amt
                Result.success(
                    ComputedTxEffects(
                        accountDeltas = listOf(
                            AccountDelta(
                                accountId = accId,
                                balanceDeltaPaisa = delta,
                                note = "ব্যালেন্স সমন্বয় (${if (isPlus) "+" else "-"}): ${tx.notes ?: ""}".trim(),
                            )
                        )
                    )
                )
            }

            TxType.REVERSAL -> {
                Result.failure(IllegalArgumentException("রিভার্সাল সরাসরি অনুমোদনযোগ্য নয়।"))
            }
        }
    }

    /**
     * Validates that applying [deltas] to [accountsById] will not violate negative-balance rules.
     * Multiple deltas on the same account are aggregated sequentially.
     */
    fun validateAndApplyAccountDeltas(
        accountsById: Map<String, FinancialAccount>,
        deltas: List<AccountDelta>,
    ): Result<Pair<Map<String, FinancialAccount>, List<Triple<FinancialAccount, Long, Long>>>> {
        val working = accountsById.toMutableMap()
        val transitions = mutableListOf<Triple<FinancialAccount, Long, Long>>()

        for (delta in deltas) {
            val acc = working[delta.accountId]
                ?: return Result.failure(IllegalArgumentException("অ্যাকাউন্ট পাওয়া যায়নি: ${delta.accountId}"))
            val prevBalance = acc.balancePaisa
            val newBalance = prevBalance + delta.balanceDeltaPaisa
            if (delta.balanceDeltaPaisa < 0L && !acc.allowNegativeBalance && newBalance < 0L) {
                return Result.failure(
                    IllegalStateException(
                        "${acc.name}-এ পর্যাপ্ত ব্যালেন্স নেই! প্রয়োজন: ${MoneyFormatter.formatTaka(-delta.balanceDeltaPaisa)}, আছে: ${MoneyFormatter.formatTaka(prevBalance)}।"
                    )
                )
            }
            val updated = acc.copy(
                balancePaisa = newBalance,
                capitalPaisa = acc.capitalPaisa + delta.capitalDeltaPaisa,
            )
            working[delta.accountId] = updated
            transitions += Triple(updated, prevBalance, newBalance)
        }
        return Result.success(working to transitions)
    }

    /**
     * Computes exact Profit & Loss summary from Ledger entries using:
     * Net Profit/Loss = Commission Income - Shop Expenses - Partner Commission Expenses
     * Interest income/expense is tracked separately.
     * Capital, Loan Principal, and Internal Transfers are excluded.
     */
    fun calculateProfitLoss(
        ledgerEntries: List<LedgerEntry>,
        period: ReportPeriod = ReportPeriod.ALL,
        nowMillis: Long = System.currentTimeMillis(),
    ): ProfitLossSummary {
        val minMillis = periodStartMillis(period, nowMillis)
        val filtered = ledgerEntries.filter { it.occurredAtMillis >= minMillis }

        var commissionIncome = 0L
        var shopExpenses = 0L
        var partnerCommissions = 0L
        var interestIncome = 0L
        var interestExpense = 0L

        for (entry in filtered) {
            commissionIncome += entry.commissionIncomePaisa
            shopExpenses += entry.shopExpensePaisa
            partnerCommissions += entry.partnerCommissionExpensePaisa
            interestIncome += entry.interestIncomePaisa
            interestExpense += entry.interestExpensePaisa
        }

        val net = commissionIncome - shopExpenses - partnerCommissions
        val totalProfit = max(0L, net)
        val totalLoss = if (net < 0L) -net else 0L

        return ProfitLossSummary(
            commissionIncomePaisa = commissionIncome,
            shopExpensesPaisa = shopExpenses,
            partnerCommissionExpensesPaisa = partnerCommissions,
            totalProfitPaisa = totalProfit,
            totalLossPaisa = totalLoss,
            netProfitLossPaisa = net,
            interestIncomePaisa = interestIncome,
            interestExpensePaisa = interestExpense,
        )
    }

    fun periodStartMillis(period: ReportPeriod, nowMillis: Long = System.currentTimeMillis()): Long {
        if (period == ReportPeriod.ALL) return 0L
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        when (period) {
            ReportPeriod.DAILY -> Unit
            ReportPeriod.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -6)
            ReportPeriod.MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
            ReportPeriod.YEARLY -> cal.set(Calendar.DAY_OF_YEAR, 1)
            ReportPeriod.ALL -> return 0L
        }
        return cal.timeInMillis
    }
}
