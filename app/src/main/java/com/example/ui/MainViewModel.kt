package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackupRepository
import com.example.data.BusinessRepository
import com.example.data.NewTxRequest
import com.example.data.translateErrorToBengali
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
import com.example.domain.ProfitLossSummary
import com.example.domain.ReportPeriod
import com.example.domain.TxStatus
import com.example.domain.TxType
import kotlin.math.max
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DuePersonItem(
    val id: String,
    val personName: String,
    val categoryLabel: String,
    val amountPaisa: Long,
    val detailText: String,
    val phone: String? = null,
)

data class DashboardMetrics(
    val cashBalancePaisa: Long = 0L,
    val totalBkashBalancePaisa: Long = 0L,
    val nagadBalancePaisa: Long = 0L,
    val rocketBalancePaisa: Long = 0L,
    val upayBalancePaisa: Long = 0L,
    val ucbBankBalancePaisa: Long = 0L,
    val easyRechargeBalancePaisa: Long = 0L,
    val grandTotalBalancePaisa: Long = 0L,
    val totalCapitalPaisa: Long = 0L,
    val partnerGivenTotalPaisa: Long = 0L,
    val remainingCapitalPaisa: Long = 0L,
    val partnerDuePaisa: Long = 0L,
    val loanDuePaisa: Long = 0L,
    val totalDuePaisa: Long = 0L,
    val dueItems: List<DuePersonItem> = emptyList(),
    val profitLossAll: ProfitLossSummary = ProfitLossSummary(),
    val todayTransactionsCount: Int = 0,
    val todayIncomePaisa: Long = 0L,
    val todayExpensesPaisa: Long = 0L,
    val pendingCount: Int = 0,
)

data class BusinessSnapshotData(
    val accounts: List<FinancialAccount> = emptyList(),
    val partners: List<PartnerAccount> = emptyList(),
    val transactions: List<BusinessTransaction> = emptyList(),
    val loans: List<LoanRecord> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val employees: List<Employee> = emptyList(),
    val invites: List<EmployeeInvite> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val metrics: DashboardMetrics = DashboardMetrics(),
)

data class ActionFeedback(
    val isWorking: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val restoreProgress: Float? = null,
    val restoreStatusText: String? = null,
    val exportedBackupJson: String? = null,
)

class MainViewModel(
    private val repository: BusinessRepository,
    private val backupRepository: BackupRepository,
    val currentUserId: String,
    val currentEmployee: Employee,
    val isOwner: Boolean,
) : ViewModel() {

    private val _feedback = MutableStateFlow(ActionFeedback())
    val feedback: StateFlow<ActionFeedback> = _feedback.asStateFlow()

    private fun <T> Flow<T>.unwrapFirestoreErrors(fallback: T? = null): Flow<Result<T>> =
        this.map<T, Result<T>> { Result.success(it) }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                Log.w(TAG, "Stream error", root)
                if (fallback != null && fsError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    emit(Result.success(fallback))
                } else {
                    emit(Result.failure(Exception(translateErrorToBengali(root), root)))
                }
            }

    private val accountsFlow = repository.observeAccounts(currentUserId, isOwner).unwrapFirestoreErrors()
    private val partnersFlow = repository.observePartners(currentUserId, isOwner).unwrapFirestoreErrors()
    private val transactionsFlow = repository.observeTransactions(currentUserId, isOwner).unwrapFirestoreErrors()
    private val loansFlow = repository.observeLoans(currentUserId, isOwner).unwrapFirestoreErrors()
    private val ledgerFlow = if (isOwner) {
        repository.observeLedger(currentUserId).unwrapFirestoreErrors(emptyList())
    } else {
        flowOf(Result.success(emptyList()))
    }
    private val employeesFlow = if (isOwner) {
        repository.observeEmployees(currentUserId).unwrapFirestoreErrors(emptyList())
    } else {
        flowOf(Result.success(listOf(currentEmployee)))
    }
    private val invitesFlow = if (isOwner) {
        repository.observeInvites(currentUserId).unwrapFirestoreErrors(emptyList())
    } else {
        flowOf(Result.success(emptyList()))
    }
    private val auditLogsFlow = if (isOwner) {
        repository.observeAuditLogs(currentUserId).unwrapFirestoreErrors(emptyList())
    } else {
        flowOf(Result.success(emptyList()))
    }

    val businessState: StateFlow<UiState<BusinessSnapshotData>> = combine(
        accountsFlow,
        partnersFlow,
        transactionsFlow,
        loansFlow,
        ledgerFlow,
        employeesFlow,
        invitesFlow,
        auditLogsFlow,
    ) { results ->
        val firstFailure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (firstFailure != null) {
            return@combine UiState.Error(firstFailure.message ?: "ডেটা লোড ব্যর্থ হয়েছে")
        }

        @Suppress("UNCHECKED_CAST")
        val accounts = (results[0].getOrNull() as? List<FinancialAccount>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val partners = (results[1].getOrNull() as? List<PartnerAccount>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val transactions = (results[2].getOrNull() as? List<BusinessTransaction>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val loans = (results[3].getOrNull() as? List<LoanRecord>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val ledger = (results[4].getOrNull() as? List<LedgerEntry>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val employees = (results[5].getOrNull() as? List<Employee>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val invites = (results[6].getOrNull() as? List<EmployeeInvite>).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val auditLogs = (results[7].getOrNull() as? List<AuditLog>).orEmpty()

        val metrics = computeDashboardMetrics(accounts, partners, transactions, loans, ledger)
        UiState.Success(
            BusinessSnapshotData(
                accounts = accounts,
                partners = partners,
                transactions = transactions,
                loans = loans,
                ledger = ledger,
                employees = employees,
                invites = invites,
                auditLogs = auditLogs,
                metrics = metrics,
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading,
    )

    private fun computeDashboardMetrics(
        accounts: List<FinancialAccount>,
        partners: List<PartnerAccount>,
        transactions: List<BusinessTransaction>,
        loans: List<LoanRecord>,
        ledger: List<LedgerEntry>,
    ): DashboardMetrics {
        val byId = accounts.associateBy { it.accountId }
        val cash = byId[BusinessConstants.ACC_CASH]?.balancePaisa ?: 0L
        val bkashTotal = BusinessConstants.BKASH_ACCOUNT_IDS.sumOf { byId[it]?.balancePaisa ?: 0L }
        val nagad = byId[BusinessConstants.ACC_NAGAD]?.balancePaisa ?: 0L
        val rocket = byId[BusinessConstants.ACC_ROCKET]?.balancePaisa ?: 0L
        val upay = byId[BusinessConstants.ACC_UPAY]?.balancePaisa ?: 0L
        val ucb = byId[BusinessConstants.ACC_UCB_BANK]?.balancePaisa ?: 0L
        val easy = byId[BusinessConstants.ACC_EASY_RECHARGE]?.balancePaisa ?: 0L
        val grandTotal = accounts.sumOf { it.balancePaisa }
        val totalCapital = accounts.sumOf { it.capitalPaisa }

        val breakdown = computeCapitalAndDueBreakdown(totalCapital, partners, loans)

        // If Owner has full ledger, compute exact P&L from ledger; otherwise compute from visible approved txs
        val plAll = if (ledger.isNotEmpty() || isOwner) {
            AccountingEngine.calculateProfitLoss(ledger, ReportPeriod.ALL)
        } else {
            ProfitLossSummary()
        }

        val todayStart = startOfTodayMillis()
        val todayTxs = transactions.filter { it.occurredAtMillis >= todayStart }
        val todayApproved = todayTxs.filter { it.status == TxStatus.APPROVED.name && it.reversedByTxId.isNullOrBlank() }
        val todayLedger = ledger.filter { it.occurredAtMillis >= todayStart }

        val todayIncome = if (isOwner) {
            todayLedger.sumOf { it.commissionIncomePaisa }
        } else {
            todayApproved.sumOf { it.manualCommissionPaisa }
        }
        val todayExpenses = if (isOwner) {
            todayLedger.sumOf { it.shopExpensePaisa + it.partnerCommissionExpensePaisa }
        } else {
            todayApproved.sumOf {
                (if (it.txType == TxType.SHOP_EXPENSE.name) it.paidAmountPaisa else 0L) + it.partnerCommissionPaisa + it.explicitFeePaisa
            }
        }
        val pendingCount = transactions.count { it.status == TxStatus.PENDING.name }

        return DashboardMetrics(
            cashBalancePaisa = cash,
            totalBkashBalancePaisa = bkashTotal,
            nagadBalancePaisa = nagad,
            rocketBalancePaisa = rocket,
            upayBalancePaisa = upay,
            ucbBankBalancePaisa = ucb,
            easyRechargeBalancePaisa = easy,
            grandTotalBalancePaisa = grandTotal,
            totalCapitalPaisa = totalCapital,
            partnerGivenTotalPaisa = breakdown.partnerGivenTotalPaisa,
            remainingCapitalPaisa = breakdown.remainingCapitalPaisa,
            partnerDuePaisa = breakdown.partnerDuePaisa,
            loanDuePaisa = breakdown.loanDuePaisa,
            totalDuePaisa = breakdown.totalDuePaisa,
            dueItems = breakdown.dueItems,
            profitLossAll = plAll,
            todayTransactionsCount = todayTxs.size,
            todayIncomePaisa = todayIncome,
            todayExpensesPaisa = todayExpenses,
            pendingCount = pendingCount,
        )
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun clearFeedback() {
        _feedback.value = _feedback.value.copy(successMessage = null, errorMessage = null)
    }

    fun clearExportedBackup() {
        _feedback.value = _feedback.value.copy(exportedBackupJson = null)
    }

    fun submitTransaction(request: NewTxRequest, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null, successMessage = null)
            val result = repository.submitTransaction(request, currentEmployee.name)
            result.fold(
                onSuccess = {
                    val msg = if (isOwner) {
                        "লেনদেন সফলভাবে অনুমোদিত ও সংরক্ষিত হয়েছে।"
                    } else {
                        "লেনদেন জমা হয়েছে (অপেক্ষমাণ / Pending — সুপার অ্যাডমিনের অনুমোদনের পর মূল ব্যালেন্সে যোগ হবে)।"
                    }
                    _feedback.value = _feedback.value.copy(isWorking = false, successMessage = msg)
                    onSuccess()
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(
                        isWorking = false,
                        errorMessage = err.message ?: "লেনদেন সংরক্ষণ ব্যর্থ হয়েছে।",
                    )
                }
            )
        }
    }

    fun approveTransaction(txId: String) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null)
            val res = repository.approvePendingTransaction(txId, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(isWorking = false, successMessage = "অনুমোদিত হয়েছে — মূল অ্যাকাউন্ট ও লেজারে যুক্ত হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(isWorking = false, errorMessage = err.message ?: "অনুমোদন ব্যর্থ হয়েছে।")
                }
            )
        }
    }

    fun removePendingTransaction(txId: String) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null)
            val res = repository.removePendingTransaction(txId, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(isWorking = false, successMessage = "অপেক্ষমাণ এন্ট্রি সফলভাবে রিমুভ করা হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(isWorking = false, errorMessage = err.message ?: "রিমুভ করা যায়নি।")
                }
            )
        }
    }

    fun rejectTransaction(txId: String, reason: String) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null)
            val res = repository.rejectPendingTransaction(txId, reason, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(isWorking = false, successMessage = "লেনদেন বাতিল (Rejected) করা হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(isWorking = false, errorMessage = err.message ?: "বাতিল ব্যর্থ হয়েছে।")
                }
            )
        }
    }

    fun reverseAndCorrect(originalTxId: String, reason: String, corrected: NewTxRequest?) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null)
            val res = repository.reverseAndCorrectTransaction(originalTxId, reason, corrected, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(
                        isWorking = false,
                        successMessage = "মূল ইতিহাস অক্ষুণ্ণ রেখে রিভার্সাল ও সংশোধন সম্পন্ন হয়েছে।",
                    )
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(isWorking = false, errorMessage = err.message ?: "রিভার্সাল ব্যর্থ হয়েছে।")
                }
            )
        }
    }

    fun setAccountAllowNegative(accountId: String, allow: Boolean) {
        viewModelScope.launch {
            val res = repository.updateAccountAllowNegative(accountId, allow, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(successMessage = "অ্যাকাউন্ট সেটিংস হালনাগাদ হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(errorMessage = err.message)
                }
            )
        }
    }

    fun updatePartnerRate(partnerId: String, ratePerThousandPaisa: Long, bkashOnly: Boolean) {
        viewModelScope.launch {
            val res = repository.updatePartnerCommissionSettings(partnerId, ratePerThousandPaisa, bkashOnly, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(successMessage = "পার্টনার কমিশন হার হালনাগাদ হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(errorMessage = err.message)
                }
            )
        }
    }

    fun addEmployee(name: String, email: String, directUid: String?, phone: String?) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null)
            val res = repository.inviteOrAddEmployee(name, email, directUid, phone, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(isWorking = false, successMessage = "সাধারণ মেম্বার সফলভাবে যুক্ত হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(isWorking = false, errorMessage = err.message)
                }
            )
        }
    }

    fun toggleEmployeeActive(uid: String, active: Boolean) {
        viewModelScope.launch {
            val res = repository.toggleEmployeeActive(uid, active, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(successMessage = "সাধারণ মেম্বারের অবস্থা পরিবর্তন করা হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(errorMessage = err.message)
                }
            )
        }
    }

    fun deleteEmployeeInvite(email: String) {
        viewModelScope.launch {
            val res = repository.deleteEmployeeInvite(email, currentEmployee.name)
            res.fold(
                onSuccess = {
                    _feedback.value = _feedback.value.copy(successMessage = "সাধারণ মেম্বারের আমন্ত্রণ বাতিল করা হয়েছে।")
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(errorMessage = err.message)
                }
            )
        }
    }

    fun generateBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(isWorking = true, errorMessage = null)
            val res = backupRepository.createFullBackupJson()
            res.fold(
                onSuccess = { json ->
                    _feedback.value = _feedback.value.copy(isWorking = false, exportedBackupJson = json)
                    onReady(json)
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(isWorking = false, errorMessage = err.message)
                }
            )
        }
    }

    fun restoreFromBackup(jsonString: String, onSafetyBackupReady: (String) -> Unit) {
        viewModelScope.launch {
            _feedback.value = _feedback.value.copy(
                isWorking = true,
                restoreProgress = 0.05f,
                restoreStatusText = "শুরু হচ্ছে...",
                errorMessage = null,
            )
            val res = backupRepository.restoreFromBackupJson(jsonString, currentEmployee.name) { p, txt ->
                _feedback.value = _feedback.value.copy(restoreProgress = p, restoreStatusText = txt)
            }
            res.fold(
                onSuccess = { safetyBackupJson ->
                    _feedback.value = _feedback.value.copy(
                        isWorking = false,
                        restoreProgress = null,
                        restoreStatusText = null,
                        successMessage = "ব্যাকআপ সফলভাবে রিস্টোর সম্পন্ন হয়েছে! পূর্বের নিরাপত্তা ব্যাকআপ সংরক্ষণ করুন।",
                    )
                    onSafetyBackupReady(safetyBackupJson)
                },
                onFailure = { err ->
                    _feedback.value = _feedback.value.copy(
                        isWorking = false,
                        restoreProgress = null,
                        restoreStatusText = null,
                        errorMessage = err.message ?: "রিস্টোর ব্যর্থ হয়েছে।",
                    )
                }
            )
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}

data class CapitalDueBreakdownResult(
    val partnerGivenTotalPaisa: Long,
    val remainingCapitalPaisa: Long,
    val partnerDuePaisa: Long,
    val loanDuePaisa: Long,
    val totalDuePaisa: Long,
    val dueItems: List<DuePersonItem>,
)

fun computeCapitalAndDueBreakdown(
    totalCapitalPaisa: Long,
    partners: List<PartnerAccount>,
    loans: List<LoanRecord>,
): CapitalDueBreakdownResult {
    val dueItems = mutableListOf<DuePersonItem>()

    val partnerGiven = partners.sumOf { max(0L, it.currentBalancePaisa) }
    for (p in partners) {
        if (p.currentBalancePaisa > 0L) {
            dueItems += DuePersonItem(
                id = "partner_${p.partnerId}",
                personName = p.name,
                categoryLabel = "পার্টনার (টাকা দেওয়া হয়েছে)",
                amountPaisa = p.currentBalancePaisa,
                detailText = "পাঠানো: ${MoneyFormatter.formatTaka(p.sentTotalPaisa)} • ফেরত: ${MoneyFormatter.formatTaka(p.receivedTotalPaisa)}",
            )
        }
    }

    var loanDue = 0L
    for (loan in loans) {
        if (loan.loanType == LoanType.LENT.name && loan.status != LoanStatus.PAID.name) {
            val rem = loan.remainingPrincipalPaisa + loan.remainingInterestPaisa
            if (rem > 0L) {
                loanDue += rem
                dueItems += DuePersonItem(
                    id = "loan_${loan.id}",
                    personName = loan.personName,
                    categoryLabel = "পাওনা ঋণ (ধার)",
                    amountPaisa = rem,
                    detailText = "বাকি আসল: ${MoneyFormatter.formatTaka(loan.remainingPrincipalPaisa)} • বাকি সুদ: ${MoneyFormatter.formatTaka(loan.remainingInterestPaisa)}",
                    phone = loan.personPhone,
                )
            }
        }
    }

    val sortedDue = dueItems.sortedByDescending { it.amountPaisa }
    val totalDue = sortedDue.sumOf { it.amountPaisa }
    val remainingCapital = totalCapitalPaisa - partnerGiven

    return CapitalDueBreakdownResult(
        partnerGivenTotalPaisa = partnerGiven,
        remainingCapitalPaisa = remainingCapital,
        partnerDuePaisa = partnerGiven,
        loanDuePaisa = loanDue,
        totalDuePaisa = totalDue,
        dueItems = sortedDue,
    )
}
