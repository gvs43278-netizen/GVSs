package com.example

import com.example.domain.AccountingEngine
import com.example.domain.BackupPayload
import com.example.domain.BackupSerializer
import com.example.domain.BusinessConstants
import com.example.domain.BusinessTransaction
import com.example.domain.Employee
import com.example.domain.FinancialAccount
import com.example.domain.LedgerEntry
import com.example.domain.LoanRecord
import com.example.domain.LoanStatus
import com.example.domain.LoanType
import com.example.domain.MoneyFormatter
import com.example.domain.PartnerAccount
import com.example.domain.ReportPeriod
import com.example.domain.TxStatus
import com.example.domain.TxType
import com.example.domain.UserRole
import com.example.export.ReportExporter
import com.example.ui.computeCapitalAndDueBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountingAndSecurityTest {

    private fun seedAccounts(
        overrides: Map<String, Long> = emptyMap(),
        allowNegative: Set<String> = emptySet(),
    ): Map<String, FinancialAccount> =
        BusinessConstants.ALL_ACCOUNTS.associate { seed ->
            seed.accountId to FinancialAccount(
                id = seed.accountId,
                accountId = seed.accountId,
                name = seed.name,
                group = seed.group.name,
                sortOrder = seed.sortOrder,
                balancePaisa = overrides[seed.accountId] ?: 0L,
                capitalPaisa = 0L,
                allowNegativeBalance = seed.accountId in allowNegative,
                memberUids = listOf(BusinessConstants.SUPER_ADMIN_UID),
            )
        }

    private fun seedPartners(): Map<String, PartnerAccount> =
        BusinessConstants.ALL_PARTNERS.associate { seed ->
            seed.partnerId to PartnerAccount(
                id = seed.partnerId,
                partnerId = seed.partnerId,
                name = seed.name,
                ratePerThousandPaisa = seed.defaultRatePerThousandPaisa,
                bkashOnlyCommission = seed.bkashOnlyCommission,
                memberUids = listOf(BusinessConstants.SUPER_ADMIN_UID),
            )
        }

    // Test 1: Customer bKash 1,000 + Cash Paid 1,000 -> bKash-1 +1,000, Cash -1,000, Commission 0
    @Test
    fun `test 1 customer bkash received 1000 and cash paid 1000 updates only those accounts with 0 commission`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_CASH to 500_000L))
        val tx = BusinessTransaction(
            id = "tx_t1",
            txType = TxType.CUSTOMER_TX.name,
            status = TxStatus.APPROVED.name,
            customerName = "গ্রাহক ১",
            receivedAccountId = BusinessConstants.ACC_BKASH_1,
            receivedAmountPaisa = 100_000L,
            paidAccountId = BusinessConstants.ACC_CASH,
            paidAmountPaisa = 100_000L,
            manualCommissionPaisa = 0L,
        )
        val effects = AccountingEngine.computeEffectsForApproval(tx, seedPartners()).getOrThrow()
        assertEquals(0L, effects.commissionIncomePaisa)
        val (updated, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, effects.accountDeltas).getOrThrow()
        assertEquals(100_000L, updated[BusinessConstants.ACC_BKASH_1]?.balancePaisa)
        assertEquals(400_000L, updated[BusinessConstants.ACC_CASH]?.balancePaisa)
        assertEquals(0L, updated[BusinessConstants.ACC_BKASH_2]?.balancePaisa)
    }

    // Test 2: Mobile recharge 100 from Easy Recharge, customer paid Cash 105, manual commission 5 -> Cash +105, Easy Recharge -100, Comm Income +5
    @Test
    fun `test 2 mobile recharge 100 cash received 105 manual commission 5`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_EASY_RECHARGE to 100_000L))
        val tx = BusinessTransaction(
            id = "tx_t2",
            txType = TxType.MOBILE_RECHARGE.name,
            status = TxStatus.APPROVED.name,
            customerName = "রিচার্জ গ্রাহক",
            receivedAccountId = BusinessConstants.ACC_CASH,
            receivedAmountPaisa = 10_500L,
            paidAccountId = BusinessConstants.ACC_EASY_RECHARGE,
            paidAmountPaisa = 10_000L,
            manualCommissionPaisa = 500L,
        )
        val effects = AccountingEngine.computeEffectsForApproval(tx, seedPartners()).getOrThrow()
        assertEquals(500L, effects.commissionIncomePaisa)
        val (updated, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, effects.accountDeltas).getOrThrow()
        assertEquals(10_500L, updated[BusinessConstants.ACC_CASH]?.balancePaisa)
        assertEquals(90_000L, updated[BusinessConstants.ACC_EASY_RECHARGE]?.balancePaisa)
    }

    // Test 3: Internal Transfer Cash -> bKash-1 5,000 -> Cash -5,000, bKash-1 +5,000, Profit/Expense 0
    @Test
    fun `test 3 internal transfer cash to bkash_1 5000 does not count as income or expense`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_CASH to 1_000_000L))
        val tx = BusinessTransaction(
            id = "tx_t3",
            txType = TxType.INTERNAL_TRANSFER.name,
            status = TxStatus.APPROVED.name,
            paidAccountId = BusinessConstants.ACC_CASH,
            paidAmountPaisa = 500_000L,
            receivedAccountId = BusinessConstants.ACC_BKASH_1,
            receivedAmountPaisa = 500_000L,
            explicitFeePaisa = 0L,
        )
        val effects = AccountingEngine.computeEffectsForApproval(tx, seedPartners()).getOrThrow()
        assertEquals(0L, effects.commissionIncomePaisa)
        assertEquals(0L, effects.shopExpensePaisa)
        assertEquals(0L, effects.partnerCommissionExpensePaisa)
        val (updated, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, effects.accountDeltas).getOrThrow()
        assertEquals(500_000L, updated[BusinessConstants.ACC_CASH]?.balancePaisa)
        assertEquals(500_000L, updated[BusinessConstants.ACC_BKASH_1]?.balancePaisa)
    }

    // Test 4: Capital Addition +2,000 to UCB Bank -> UCB Bank +2,000, Total Capital +2,000, Profit 0
    @Test
    fun `test 4 capital add 2000 to ucb_bank increases balance and capital with 0 profit`() {
        val accounts = seedAccounts()
        val tx = BusinessTransaction(
            id = "tx_t4",
            txType = TxType.CAPITAL_ADD.name,
            status = TxStatus.APPROVED.name,
            receivedAccountId = BusinessConstants.ACC_UCB_BANK,
            receivedAmountPaisa = 200_000L,
        )
        val effects = AccountingEngine.computeEffectsForApproval(tx, seedPartners()).getOrThrow()
        assertEquals(0L, effects.commissionIncomePaisa)
        val (updated, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, effects.accountDeltas).getOrThrow()
        assertEquals(200_000L, updated[BusinessConstants.ACC_UCB_BANK]?.balancePaisa)
        assertEquals(200_000L, updated[BusinessConstants.ACC_UCB_BANK]?.capitalPaisa)
    }

    // Test 5: Partner Exemption -> Samad Bhai & Jahir Mama behave identically to Ariful (0 commission deduction)
    @Test
    fun `test 5 Samad Bhai Jahir Mama and Ariful record exact entered amount with 0 commission`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_BKASH_1 to 1_000_000L))
        val partners = seedPartners()
        assertEquals(0L, partners[BusinessConstants.PARTNER_SAMAD]?.ratePerThousandPaisa)
        assertEquals(0L, partners[BusinessConstants.PARTNER_JAHIR]?.ratePerThousandPaisa)
        assertEquals(0L, partners[BusinessConstants.PARTNER_ARIFUL]?.ratePerThousandPaisa)

        for (pId in listOf(BusinessConstants.PARTNER_SAMAD, BusinessConstants.PARTNER_JAHIR, BusinessConstants.PARTNER_ARIFUL)) {
            val tx = BusinessTransaction(
                id = "tx_$pId",
                txType = TxType.PARTNER_SEND.name,
                status = TxStatus.APPROVED.name,
                partnerId = pId,
                paidAccountId = BusinessConstants.ACC_BKASH_1,
                paidAmountPaisa = 200_000L, // 2,000 Taka
            )
            val effects = AccountingEngine.computeEffectsForApproval(tx, partners).getOrThrow()
            assertEquals(0L, effects.partnerCommissionExpensePaisa)
            assertEquals(0L, effects.partnerDelta?.payableCommissionDeltaPaisa)
            assertEquals(200_000L, effects.partnerDelta?.currentBalanceDeltaPaisa)
            val (updated, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, effects.accountDeltas).getOrThrow()
            assertEquals(800_000L, updated[BusinessConstants.ACC_BKASH_1]?.balancePaisa)
        }
    }

    // Test 6: Custom rate formula still computes when explicitly configured by Owner
    @Test
    fun `test 6 custom partner rate applies only if explicitly configured above zero`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_BKASH_1 to 500_000L))
        val customPartners = mapOf(
            "custom_partner" to PartnerAccount(
                id = "custom_partner",
                partnerId = "custom_partner",
                name = "Custom Partner",
                ratePerThousandPaisa = 500L,
                bkashOnlyCommission = true,
            )
        )
        val tx = BusinessTransaction(
            id = "tx_t6_custom",
            txType = TxType.PARTNER_SEND.name,
            status = TxStatus.APPROVED.name,
            partnerId = "custom_partner",
            paidAccountId = BusinessConstants.ACC_BKASH_1,
            paidAmountPaisa = 200_000L,
        )
        val effects = AccountingEngine.computeEffectsForApproval(tx, customPartners).getOrThrow()
        assertEquals(1_000L, effects.partnerCommissionExpensePaisa)
        assertEquals(1_000L, effects.partnerDelta?.payableCommissionDeltaPaisa)
        val (updated, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, effects.accountDeltas).getOrThrow()
        assertEquals(300_000L, updated[BusinessConstants.ACC_BKASH_1]?.balancePaisa)
    }

    // Test 7: Insufficient Balance -> reject deduction exceeding balance unless allowNegativeBalance = true
    @Test
    fun `test 7 insufficient balance rejected unless allowNegativeBalance enabled`() {
        val tx = BusinessTransaction(
            id = "tx_t7",
            txType = TxType.SHOP_EXPENSE.name,
            status = TxStatus.APPROVED.name,
            paidAccountId = BusinessConstants.ACC_CASH,
            paidAmountPaisa = 50_000L, // 500 Taka
        )
        val effects = AccountingEngine.computeEffectsForApproval(tx, seedPartners()).getOrThrow()

        // Default allowNegative = false, balance = 100 Taka
        val strictAccounts = seedAccounts(mapOf(BusinessConstants.ACC_CASH to 10_000L))
        val strictResult = AccountingEngine.validateAndApplyAccountDeltas(strictAccounts, effects.accountDeltas)
        assertTrue(strictResult.isFailure)

        // allowNegative = true
        val flexibleAccounts = seedAccounts(
            overrides = mapOf(BusinessConstants.ACC_CASH to 10_000L),
            allowNegative = setOf(BusinessConstants.ACC_CASH),
        )
        val flexResult = AccountingEngine.validateAndApplyAccountDeltas(flexibleAccounts, effects.accountDeltas)
        assertTrue(flexResult.isSuccess)
        assertEquals(-40_000L, flexResult.getOrThrow().first[BusinessConstants.ACC_CASH]?.balancePaisa)
    }

    // Test 8 & 13: Reversal math & Profit/Loss formula + Loan principal exclusion
    @Test
    fun `test 8 and 13 profit loss formula excludes capital internal transfer and loan principal`() {
        val now = System.currentTimeMillis()
        val ledger = listOf(
            // Commission income +500 Taka
            LedgerEntry(
                id = "l1",
                txId = "tx1",
                txType = TxType.CUSTOMER_TX.name,
                accountId = BusinessConstants.ACC_CASH,
                deltaPaisa = 50_000L,
                commissionIncomePaisa = 50_000L,
                occurredAtMillis = now,
            ),
            // Shop expense -200 Taka
            LedgerEntry(
                id = "l2",
                txId = "tx2",
                txType = TxType.SHOP_EXPENSE.name,
                accountId = BusinessConstants.ACC_CASH,
                deltaPaisa = -20_000L,
                shopExpensePaisa = 20_000L,
                occurredAtMillis = now,
            ),
            // Partner commission expense -50 Taka
            LedgerEntry(
                id = "l3",
                txId = "tx3",
                txType = TxType.PARTNER_SEND.name,
                accountId = BusinessConstants.ACC_BKASH_1,
                deltaPaisa = -1_000_000L,
                partnerCommissionExpensePaisa = 5_000L,
                occurredAtMillis = now,
            ),
            // Reversal subtracting 100 Taka commission income
            LedgerEntry(
                id = "l4",
                txId = "tx_rev",
                txType = TxType.REVERSAL.name,
                accountId = BusinessConstants.ACC_CASH,
                deltaPaisa = -10_000L,
                commissionIncomePaisa = -10_000L,
                occurredAtMillis = now,
            ),
            // Loan repay with 500 Taka principal + 25 Taka interest (principal excluded from P&L, interest separate)
            LedgerEntry(
                id = "l5",
                txId = "tx_loan",
                txType = TxType.LOAN_REPAY.name,
                accountId = BusinessConstants.ACC_CASH,
                deltaPaisa = 52_500L,
                interestIncomePaisa = 2_500L,
                occurredAtMillis = now,
            ),
        )

        val pl = AccountingEngine.calculateProfitLoss(ledger, ReportPeriod.ALL, now)
        // Net Commission Income = 500 - 100 = 400 Taka (40,000 paisa)
        assertEquals(40_000L, pl.commissionIncomePaisa)
        assertEquals(20_000L, pl.shopExpensesPaisa)
        assertEquals(5_000L, pl.partnerCommissionExpensesPaisa)
        // Net Profit = 400 - 200 - 50 = 150 Taka (15,000 paisa)
        assertEquals(15_000L, pl.netProfitLossPaisa)
        assertEquals(15_000L, pl.totalProfitPaisa)
        assertEquals(0L, pl.totalLossPaisa)
        assertEquals(2_500L, pl.interestIncomePaisa)
    }

    // Test 10: Backup & Restore JSON roundtrip + tampered non-superadmin OWNER rejection
    @Test
    fun `test 10 backup json roundtrip validates 11 accounts and rejects tampered non-superadmin owner`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_CASH to 250_000L)).values.toList()
        val partners = seedPartners().values.toList()
        val payload = BackupPayload(
            schemaVersion = BackupSerializer.CURRENT_SCHEMA_VERSION,
            exportedAtMillis = 1_700_000_000_000L,
            exportedBy = BusinessConstants.SUPER_ADMIN_UID,
            accounts = accounts,
            partners = partners,
            transactions = emptyList(),
            ledger = emptyList(),
            loans = emptyList(),
            employees = listOf(
                Employee(
                    id = BusinessConstants.SUPER_ADMIN_UID,
                    uid = BusinessConstants.SUPER_ADMIN_UID,
                    name = "Super Admin",
                    email = "owner@business.local",
                    role = UserRole.OWNER.name,
                    active = true,
                )
            ),
        )
        val json = BackupSerializer.toJson(payload)
        val parsed = BackupSerializer.parseAndValidate(json)
        assertTrue(parsed.isSuccess)
        assertEquals(11, parsed.getOrThrow().accounts.size)
        assertEquals(250_000L, parsed.getOrThrow().accounts.first { it.accountId == BusinessConstants.ACC_CASH }.balancePaisa)

        // Attempt tampering: assign OWNER role to a non-superadmin UID
        val tampered = json.replace(BusinessConstants.SUPER_ADMIN_UID, "hacker_uid_999")
        val rejected = BackupSerializer.parseAndValidate(tampered)
        assertTrue(rejected.isFailure)
    }

    @Test
    fun `test bengali digit parsing and taka formatting`() {
        assertEquals(125_50L, MoneyFormatter.parseTakaToPaisa("১২৫.৫০"))
        assertEquals(100_000L, MoneyFormatter.parseTakaToPaisa("১,০০০"))
        assertEquals("৳1,000", MoneyFormatter.formatTaka(100_000L))
        assertEquals("+৳125.50", MoneyFormatter.formatTaka(12_550L, showPlusSign = true))
        assertEquals("-৳50", MoneyFormatter.formatTaka(-5_000L))
    }

    @Test
    fun `test 13 loan borrow and repay interest separation`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_CASH to 100_000L))
        val borrowTx = BusinessTransaction(
            id = "tx_borrow",
            txType = TxType.LOAN_BORROW.name,
            status = TxStatus.APPROVED.name,
            customerName = "জামাল",
            loanType = LoanType.BORROWED.name,
            receivedAccountId = BusinessConstants.ACC_CASH,
            receivedAmountPaisa = 200_000L,
        )
        val borrowEffects = AccountingEngine.computeEffectsForApproval(borrowTx, seedPartners()).getOrThrow()
        assertEquals(0L, borrowEffects.commissionIncomePaisa)
        assertEquals(0L, borrowEffects.interestIncomePaisa)
        val (afterBorrow, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, borrowEffects.accountDeltas).getOrThrow()
        assertEquals(300_000L, afterBorrow[BusinessConstants.ACC_CASH]?.balancePaisa)

        val repayTx = BusinessTransaction(
            id = "tx_repay",
            txType = TxType.LOAN_REPAY.name,
            status = TxStatus.APPROVED.name,
            customerName = "জামাল",
            loanType = LoanType.BORROWED.name,
            paidAccountId = BusinessConstants.ACC_CASH,
            paidAmountPaisa = 100_000L, // principal
            interestPaisa = 5_000L, // optional interest
        )
        val repayEffects = AccountingEngine.computeEffectsForApproval(repayTx, seedPartners()).getOrThrow()
        assertEquals(5_000L, repayEffects.interestExpensePaisa)
        assertEquals(0L, repayEffects.shopExpensePaisa)
        val (afterRepay, _) = AccountingEngine.validateAndApplyAccountDeltas(afterBorrow, repayEffects.accountDeltas).getOrThrow()
        assertEquals(195_000L, afterRepay[BusinessConstants.ACC_CASH]?.balancePaisa)
    }

    @Test
    fun `test loan lend and repay increases account and records separate interest income`() {
        val accounts = seedAccounts(mapOf(BusinessConstants.ACC_CASH to 500_000L))
        val lendTx = BusinessTransaction(
            id = "tx_lend",
            txType = TxType.LOAN_LEND.name,
            status = TxStatus.APPROVED.name,
            customerName = "করিম",
            loanType = LoanType.LENT.name,
            paidAccountId = BusinessConstants.ACC_CASH,
            paidAmountPaisa = 200_000L,
        )
        val lendEffects = AccountingEngine.computeEffectsForApproval(lendTx, seedPartners()).getOrThrow()
        assertEquals(0L, lendEffects.shopExpensePaisa)
        val (afterLend, _) = AccountingEngine.validateAndApplyAccountDeltas(accounts, lendEffects.accountDeltas).getOrThrow()
        assertEquals(300_000L, afterLend[BusinessConstants.ACC_CASH]?.balancePaisa)

        val repayTx = BusinessTransaction(
            id = "tx_lend_repay",
            txType = TxType.LOAN_REPAY.name,
            status = TxStatus.APPROVED.name,
            customerName = "করিম",
            loanType = LoanType.LENT.name,
            receivedAccountId = BusinessConstants.ACC_CASH,
            receivedAmountPaisa = 200_000L,
            interestPaisa = 10_000L,
        )
        val repayEffects = AccountingEngine.computeEffectsForApproval(repayTx, seedPartners()).getOrThrow()
        assertEquals(10_000L, repayEffects.interestIncomePaisa)
        assertEquals(0L, repayEffects.commissionIncomePaisa)
        val (afterRepay, _) = AccountingEngine.validateAndApplyAccountDeltas(afterLend, repayEffects.accountDeltas).getOrThrow()
        assertEquals(510_000L, afterRepay[BusinessConstants.ACC_CASH]?.balancePaisa)
    }

    @Test
    fun `test profit loss period filtering excludes entries older than period start`() {
        val now = 1_750_000_000_000L
        val oldTime = now - 40L * 86_400_000L
        val ledger = listOf(
            LedgerEntry(
                id = "l_today",
                txId = "t1",
                txType = TxType.CUSTOMER_TX.name,
                accountId = BusinessConstants.ACC_CASH,
                commissionIncomePaisa = 10_000L,
                occurredAtMillis = now,
            ),
            LedgerEntry(
                id = "l_old",
                txId = "t2",
                txType = TxType.CUSTOMER_TX.name,
                accountId = BusinessConstants.ACC_CASH,
                commissionIncomePaisa = 50_000L,
                occurredAtMillis = oldTime,
            ),
        )
        val daily = AccountingEngine.calculateProfitLoss(ledger, ReportPeriod.DAILY, now)
        assertEquals(10_000L, daily.commissionIncomePaisa)
        val all = AccountingEngine.calculateProfitLoss(ledger, ReportPeriod.ALL, now)
        assertEquals(60_000L, all.commissionIncomePaisa)
    }

    @Test
    fun `test csv report export writes utf8 bom and bengali headers`() {
        val baos = ByteArrayOutputStream()
        val tx = BusinessTransaction(
            id = "tx_csv_1",
            txType = TxType.CUSTOMER_TX.name,
            status = TxStatus.APPROVED.name,
            customerName = "আবদুল করিম",
            receivedAccountId = BusinessConstants.ACC_BKASH_1,
            receivedAmountPaisa = 100_000L,
            manualCommissionPaisa = 1_000L,
            createdByName = "মালিক",
        )
        ReportExporter.writeTransactionsCsv(
            outputStream = baos,
            title = "দৈনিক রিপোর্ট",
            summaryLines = listOf("মোট কমিশন আয়" to "৳10"),
            transactions = listOf(tx),
        )
        val bytes = baos.toByteArray()
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
        val content = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertTrue(content.contains("লেনদেন আইডি"))
        assertTrue(content.contains("আবদুল করিম"))
        assertTrue(content.contains("bKash-1 — 0198"))
    }

    @Test
    fun `test 20 initial 11 accounts start at 0 and capital vs adjustment prevents double counting`() {
        val initial = seedAccounts()
        assertEquals(11, initial.size)
        assertTrue(initial.values.all { it.balancePaisa == 0L && it.capitalPaisa == 0L })

        // Cash +10,000 capital
        val cashAdd = BusinessTransaction(
            id = "tx_cap_cash",
            txType = TxType.CAPITAL_ADD.name,
            status = TxStatus.APPROVED.name,
            receivedAccountId = BusinessConstants.ACC_CASH,
            receivedAmountPaisa = 1_000_000L,
        )
        val cashEff = AccountingEngine.computeEffectsForApproval(cashAdd, seedPartners()).getOrThrow()
        val (afterCash, _) = AccountingEngine.validateAndApplyAccountDeltas(initial, cashEff.accountDeltas).getOrThrow()

        // bKash-1 +5,000 capital
        val bkashAdd = BusinessTransaction(
            id = "tx_cap_bkash1",
            txType = TxType.CAPITAL_ADD.name,
            status = TxStatus.APPROVED.name,
            receivedAccountId = BusinessConstants.ACC_BKASH_1,
            receivedAmountPaisa = 500_000L,
        )
        val bkashEff = AccountingEngine.computeEffectsForApproval(bkashAdd, seedPartners()).getOrThrow()
        val (afterBkash, _) = AccountingEngine.validateAndApplyAccountDeltas(afterCash, bkashEff.accountDeltas).getOrThrow()

        assertEquals(1_000_000L, afterBkash[BusinessConstants.ACC_CASH]?.balancePaisa)
        assertEquals(500_000L, afterBkash[BusinessConstants.ACC_BKASH_1]?.balancePaisa)
        assertEquals(0L, afterBkash[BusinessConstants.ACC_BKASH_2]?.balancePaisa)

        // External source balance adjustment +2,000 to Cash -> updates balance ONLY, NOT capital
        val extAdj = BusinessTransaction(
            id = "tx_ext_adj",
            txType = TxType.BALANCE_ADJUSTMENT.name,
            status = TxStatus.APPROVED.name,
            receivedAccountId = BusinessConstants.ACC_CASH,
            receivedAmountPaisa = 200_000L,
            adjustmentSign = 1L,
        )
        val extEff = AccountingEngine.computeEffectsForApproval(extAdj, seedPartners()).getOrThrow()
        val (afterExt, _) = AccountingEngine.validateAndApplyAccountDeltas(afterBkash, extEff.accountDeltas).getOrThrow()

        assertEquals(1_200_000L, afterExt[BusinessConstants.ACC_CASH]?.balancePaisa)
        assertEquals(1_000_000L, afterExt[BusinessConstants.ACC_CASH]?.capitalPaisa)
        assertEquals(0L, extEff.commissionIncomePaisa)
        assertEquals(0L, extEff.shopExpensePaisa)
        assertEquals("ashrafmahmudsany2@gmail.com", BusinessConstants.OWNER_EMAIL)
        assertEquals("yRuPuAgCe2V2Y8avgK9U8n4sTgj1", BusinessConstants.SUPER_ADMIN_UID)
    }

    @Test
    fun `test 21 remaining capital subtracts partner disbursements and builds due breakdown list`() {
        val partners = listOf(
            PartnerAccount(partnerId = BusinessConstants.PARTNER_SAMAD, name = "Samad Bhai", sentTotalPaisa = 1_000_000L, currentBalancePaisa = 1_000_000L),
            PartnerAccount(partnerId = BusinessConstants.PARTNER_JAHIR, name = "Jahir Mama", sentTotalPaisa = 500_000L, currentBalancePaisa = 500_000L),
            PartnerAccount(partnerId = BusinessConstants.PARTNER_ARIFUL, name = "Ariful Islam", sentTotalPaisa = 300_000L, currentBalancePaisa = 300_000L),
        )
        val loans = listOf(
            LoanRecord(
                id = "l_lent_1",
                loanType = LoanType.LENT.name,
                personName = "রহিম মিয়া",
                remainingPrincipalPaisa = 200_000L,
                remainingInterestPaisa = 0L,
                status = LoanStatus.OPEN.name,
            ),
            LoanRecord(
                id = "l_borrowed_1",
                loanType = LoanType.BORROWED.name,
                personName = "দোকান মহাজন",
                remainingPrincipalPaisa = 400_000L,
                status = LoanStatus.OPEN.name,
            ),
        )

        val breakdown = computeCapitalAndDueBreakdown(
            totalCapitalPaisa = 5_000_000L, // 50,000 Taka
            partners = partners,
            loans = loans,
        )

        assertEquals(1_800_000L, breakdown.partnerGivenTotalPaisa) // 18,000 Taka
        assertEquals(3_200_000L, breakdown.remainingCapitalPaisa) // 50,000 - 18,000 = 32,000 Taka
        assertEquals(200_000L, breakdown.loanDuePaisa) // 2,000 Taka receivable (excludes BORROWED liability)
        assertEquals(2_000_000L, breakdown.totalDuePaisa) // 18,000 + 2,000 = 20,000 Taka
        assertEquals(4, breakdown.dueItems.size)
        assertEquals("Samad Bhai", breakdown.dueItems[0].personName)
        assertEquals(1_000_000L, breakdown.dueItems[0].amountPaisa)
        assertEquals("রহিম মিয়া", breakdown.dueItems[3].personName)
        assertEquals(200_000L, breakdown.dueItems[3].amountPaisa)
    }
}
