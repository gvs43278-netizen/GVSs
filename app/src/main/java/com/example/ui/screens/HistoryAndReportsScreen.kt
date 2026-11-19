package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.NewTxRequest
import com.example.domain.AccountingEngine
import com.example.domain.BusinessConstants
import com.example.domain.BusinessTransaction
import com.example.domain.LoanStatus
import com.example.domain.MoneyFormatter
import com.example.domain.ReportPeriod
import com.example.domain.TxStatus
import com.example.domain.TxType
import com.example.export.ReportExporter
import com.example.ui.BusinessSnapshotData
import com.example.ui.components.AccountDropdown
import com.example.ui.components.ProfitLossBarChart
import com.example.ui.components.SimpleDropdown
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ProfitGreen

enum class ReportKind(val labelBn: String) {
    DAILY("দৈনিক রিপোর্ট"),
    WEEKLY("সাপ্তাহিক রিপোর্ট"),
    MONTHLY("মাসিক রিপোর্ট"),
    YEARLY("বাৎসরিক রিপোর্ট"),
    ACCOUNT_WISE("অ্যাকাউন্ট-ভিত্তিক"),
    EMPLOYEE_WISE("কর্মচারী-ভিত্তিক"),
    COMMISSION("কমিশন রিপোর্ট"),
    PARTNER("পার্টনার রিপোর্ট"),
    EXPENSE("খরচের রিপোর্ট"),
    LOAN("ঋণ রিপোর্ট"),
    PROFIT_LOSS("লাভ-ক্ষতি রিপোর্ট"),
    PENDING("অপেক্ষমাণ রিপোর্ট"),
}

@Composable
fun HistoryAndReportsScreen(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    initialAccountFilter: String? = null,
    onReverseAndCorrect: (originalTxId: String, reason: String, corrected: NewTxRequest?) -> Unit,
) {
    var mainTab by remember { mutableIntStateOf(0) } // 0 = History, 1 = Reports

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mainTab == 0,
                onClick = { mainTab = 0 },
                label = { Text("লেনদেন ইতিহাস") },
                modifier = Modifier.testTag("tab_tx_history"),
            )
            FilterChip(
                selected = mainTab == 1,
                onClick = { mainTab = 1 },
                label = { Text("১২টি রিপোর্ট ও এক্সপোর্ট") },
                modifier = Modifier.testTag("tab_reports"),
            )
        }
        Spacer(Modifier.height(10.dp))

        if (mainTab == 0) {
            TransactionHistoryView(
                data = data,
                isOwner = isOwner,
                initialAccountFilter = initialAccountFilter,
                onReverseAndCorrect = onReverseAndCorrect,
            )
        } else {
            ReportsView(data = data, isOwner = isOwner)
        }
    }
}

@Composable
private fun TransactionHistoryView(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    initialAccountFilter: String?,
    onReverseAndCorrect: (String, String, NewTxRequest?) -> Unit,
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var accountFilter by remember(initialAccountFilter) { mutableStateOf(initialAccountFilter ?: "ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var typeFilter by remember { mutableStateOf("ALL") }
    var periodFilter by remember { mutableStateOf(ReportPeriod.ALL) }
    var employeeFilter by remember { mutableStateOf("ALL") }
    var reversingTx by remember { mutableStateOf<BusinessTransaction?>(null) }

    val minMillis = remember(periodFilter) { AccountingEngine.periodStartMillis(periodFilter) }
    val filtered = remember(
        data.transactions,
        search,
        accountFilter,
        statusFilter,
        typeFilter,
        periodFilter,
        employeeFilter,
    ) {
        val q = search.trim().lowercase()
        data.transactions.filter { tx ->
            val matchPeriod = tx.occurredAtMillis >= minMillis
            val matchAccount = accountFilter == "ALL" ||
                tx.receivedAccountId == accountFilter ||
                tx.paidAccountId == accountFilter
            val matchStatus = statusFilter == "ALL" || tx.status == statusFilter
            val matchType = typeFilter == "ALL" || tx.txType == typeFilter
            val matchEmp = employeeFilter == "ALL" || tx.createdBy == employeeFilter
            val matchQuery = q.isEmpty() ||
                tx.id.lowercase().contains(q) ||
                (tx.customerName?.lowercase()?.contains(q) == true) ||
                (tx.customerPhone?.lowercase()?.contains(q) == true) ||
                (tx.referenceTxId?.lowercase()?.contains(q) == true) ||
                (tx.notes?.lowercase()?.contains(q) == true) ||
                tx.createdByName.lowercase().contains(q)
            matchPeriod && matchAccount && matchStatus && matchType && matchEmp && matchQuery
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeReportPdf(
                        out,
                        "লেনদেন ইতিহাস (${filtered.size}টি)",
                        listOf("মোট এন্ট্রি" to "${filtered.size}টি"),
                        filtered,
                        context = context,
                    )
                }
            }.onSuccess {
                Toast.makeText(context, "PDF সফলভাবে সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "PDF সংরক্ষণ ব্যর্থ: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeTransactionsCsv(
                        out,
                        "লেনদেন ইতিহাস",
                        listOf("মোট এন্ট্রি" to "${filtered.size}টি"),
                        filtered,
                    )
                }
            }.onSuccess {
                Toast.makeText(context, "Excel (CSV) সফলভাবে সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "CSV সংরক্ষণ ব্যর্থ: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    reversingTx?.let { target ->
        ReverseAndCorrectDialog(
            original = target,
            accounts = data.accounts,
            onDismiss = { reversingTx = null },
            onConfirm = { reason, corrected ->
                onReverseAndCorrect(target.id, reason, corrected)
                reversingTx = null
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("খুঁজুন (নাম, আইডি, ফোন, নোট)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("history_search"),
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportPeriod.entries.forEach { p ->
                    FilterChip(
                        selected = periodFilter == p,
                        onClick = { periodFilter = p },
                        label = { Text(p.labelBn) },
                    )
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleDropdown(
                    label = "অ্যাকাউন্ট",
                    selectedLabel = if (accountFilter == "ALL") "সকল অ্যাকাউন্ট" else BusinessConstants.accountDisplayName(accountFilter),
                    items = listOf("ALL" to "সকল অ্যাকাউন্ট") + data.accounts.map { it.accountId to it.name },
                    onSelected = { accountFilter = it },
                    modifier = Modifier.weight(1f),
                )
                SimpleDropdown(
                    label = "অবস্থা",
                    selectedLabel = if (statusFilter == "ALL") "সকল অবস্থা" else TxStatus.fromRaw(statusFilter).labelBn,
                    items = listOf("ALL" to "সকল অবস্থা") + TxStatus.entries.map { it.name to it.labelBn },
                    onSelected = { statusFilter = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleDropdown(
                    label = "ধরন",
                    selectedLabel = if (typeFilter == "ALL") "সকল ধরন" else TxType.fromRaw(typeFilter).labelBn,
                    items = listOf("ALL" to "সকল ধরন") + TxType.entries.map { it.name to it.labelBn },
                    onSelected = { typeFilter = it },
                    modifier = Modifier.weight(1f),
                )
                if (isOwner && data.employees.isNotEmpty()) {
                    SimpleDropdown(
                        label = "সাধারণ মেম্বার",
                        selectedLabel = if (employeeFilter == "ALL") "সকল" else (data.employees.firstOrNull { it.uid == employeeFilter }?.name ?: "অজানা"),
                        items = listOf("ALL" to "সকল") + data.employees.map { it.uid to it.name },
                        onSelected = { employeeFilter = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { pdfLauncher.launch("BusinessManager_History_${System.currentTimeMillis()}.pdf") },
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Text(" PDF")
                }
                OutlinedButton(
                    onClick = { csvLauncher.launch("BusinessManager_History_${System.currentTimeMillis()}.csv") },
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null)
                    Text(" Excel (CSV)")
                }
                OutlinedButton(
                    onClick = {
                        ReportExporter.printReport(
                            context,
                            "লেনদেন ইতিহাস (${filtered.size}টি)",
                            listOf("মোট এন্ট্রি" to "${filtered.size}টি"),
                            filtered,
                        )
                    },
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Text(" প্রিন্ট")
                }
            }
        }

        items(filtered, key = { it.id }) { tx ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(TxType.fromRaw(tx.txType).labelBn, fontWeight = FontWeight.Bold)
                        StatusBadge(tx.status)
                    }
                    Text("ID: ${tx.id} | ${MoneyFormatter.formatDateTime(tx.occurredAtMillis)}", style = MaterialTheme.typography.labelSmall)
                    if (!tx.customerName.isNullOrBlank()) {
                        Text("নাম/বিবরণ: ${tx.customerName}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (tx.receivedAmountPaisa > 0L) {
                        Text(
                            "গ্রহণ: ${BusinessConstants.accountDisplayName(tx.receivedAccountId)} ${MoneyFormatter.formatTaka(tx.receivedAmountPaisa)}",
                            color = ProfitGreen,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (tx.paidAmountPaisa > 0L) {
                        Text(
                            "প্রদান: ${BusinessConstants.accountDisplayName(tx.paidAccountId)} ${MoneyFormatter.formatTaka(tx.paidAmountPaisa)}",
                            color = LossCrimson,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (tx.manualCommissionPaisa > 0L) {
                        Text("কমিশন আয়: ${MoneyFormatter.formatTaka(tx.manualCommissionPaisa)}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (tx.partnerCommissionPaisa > 0L) {
                        Text("পার্টনার কমিশন ব্যয়: ${MoneyFormatter.formatTaka(tx.partnerCommissionPaisa)}", color = PendingAmber, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!tx.accountChangesSummary.isNullOrBlank()) {
                        Text("পরিবর্তন: ${tx.accountChangesSummary}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!tx.reversedByTxId.isNullOrBlank()) {
                        Text(
                            "রিভার্স করা হয়েছে -> ${tx.reversedByTxId} (${tx.reversalReason ?: ""})",
                            color = LossCrimson,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!tx.replacesTxId.isNullOrBlank()) {
                        Text(
                            "সংশোধিত এন্ট্রি (পূর্বের ID: ${tx.replacesTxId})",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "তৈরি: ${tx.createdByName}${tx.approvedBy?.let { " | অনুমোদন: $it" } ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isOwner && tx.status == TxStatus.APPROVED.name && tx.reversedByTxId.isNullOrBlank() && tx.txType != TxType.REVERSAL.name) {
                        OutlinedButton(
                            onClick = { reversingTx = tx },
                            modifier = Modifier.fillMaxWidth().testTag("reverse_btn_${tx.id}"),
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null)
                            Text(" সংশোধন / রিভার্স করুন (ইতিহাস অক্ষুণ্ণ থাকবে)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReverseAndCorrectDialog(
    original: BusinessTransaction,
    accounts: List<com.example.domain.FinancialAccount>,
    onDismiss: () -> Unit,
    onConfirm: (reason: String, corrected: NewTxRequest?) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var alsoCreateCorrected by remember { mutableStateOf(true) }
    var customerName by remember { mutableStateOf(original.customerName ?: "") }
    var recvAcc by remember { mutableStateOf(original.receivedAccountId ?: BusinessConstants.ACC_CASH) }
    var recvAmt by remember { mutableStateOf(MoneyFormatter.formatTakaPlain(original.receivedAmountPaisa)) }
    var paidAcc by remember { mutableStateOf(original.paidAccountId ?: BusinessConstants.ACC_CASH) }
    var paidAmt by remember { mutableStateOf(MoneyFormatter.formatTakaPlain(original.paidAmountPaisa)) }
    var commAmt by remember { mutableStateOf(MoneyFormatter.formatTakaPlain(original.manualCommissionPaisa)) }
    var refTxId by remember { mutableStateOf(original.referenceTxId ?: "") }
    var notesText by remember { mutableStateOf(original.notes ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("রিভার্সাল ও সংশোধন") },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "মূল লেনদেন (${original.id}) মুছে ফেলা হবে না। প্রথমে একটি রিভার্সাল এন্ট্রি তৈরি হবে, এবং চাইলে সংশোধিত নতুন এন্ট্রি যুক্ত হবে।",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it; err = null },
                    label = { Text("সংশোধনের কারণ *") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alsoCreateCorrected, onCheckedChange = { alsoCreateCorrected = it })
                    Text("রিভার্সালের সাথে সংশোধিত নতুন এন্ট্রি তৈরি করুন", style = MaterialTheme.typography.bodySmall)
                }
                if (alsoCreateCorrected) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("সংশোধিত গ্রাহক / বিবরণ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (original.receivedAmountPaisa > 0L || original.receivedAccountId != null) {
                        AccountDropdown("সংশোধিত গ্রহণ অ্যাকাউন্ট", recvAcc, accounts, { recvAcc = it })
                        OutlinedTextField(
                            value = recvAmt,
                            onValueChange = { recvAmt = it },
                            label = { Text("সংশোধিত প্রাপ্ত টাকা (৳)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (original.paidAmountPaisa > 0L || original.paidAccountId != null) {
                        AccountDropdown("সংশোধিত প্রদান অ্যাকাউন্ট", paidAcc, accounts, { paidAcc = it })
                        OutlinedTextField(
                            value = paidAmt,
                            onValueChange = { paidAmt = it },
                            label = { Text("সংশোধিত প্রদত্ত টাকা (৳)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = commAmt,
                        onValueChange = { commAmt = it },
                        label = { Text("সংশোধিত কমিশন (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = refTxId,
                        onValueChange = { refTxId = it },
                        label = { Text("সংশোধিত রেফারেন্স / ট্রানজেকশন আইডি") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("সংশোধিত নোট") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isBlank()) {
                        err = "সংশোধনের কারণ লিখুন।"
                        return@Button
                    }
                    if (!alsoCreateCorrected) {
                        onConfirm(reason, null)
                    } else {
                        val rP = MoneyFormatter.parseTakaToPaisa(recvAmt) ?: 0L
                        val pP = MoneyFormatter.parseTakaToPaisa(paidAmt) ?: 0L
                        val cP = MoneyFormatter.parseTakaToPaisa(commAmt) ?: 0L
                        onConfirm(
                            reason,
                            NewTxRequest(
                                txType = TxType.fromRaw(original.txType),
                                customerName = customerName.takeIf { it.isNotBlank() },
                                customerPhone = original.customerPhone,
                                operator = original.operator,
                                billType = original.billType,
                                expenseCategory = original.expenseCategory,
                                partnerId = original.partnerId,
                                loanId = original.loanId,
                                loanType = original.loanType,
                                receivedAccountId = if (rP > 0L) recvAcc else null,
                                receivedAmountPaisa = rP,
                                paidAccountId = if (pP > 0L) paidAcc else null,
                                paidAmountPaisa = pP,
                                manualCommissionPaisa = cP,
                                explicitFeePaisa = original.explicitFeePaisa,
                                interestPaisa = original.interestPaisa,
                                adjustmentSign = original.adjustmentSign,
                                referenceTxId = refTxId.takeIf { it.isNotBlank() },
                                notes = notesText.takeIf { it.isNotBlank() },
                                occurredAtMillis = original.occurredAtMillis,
                            )
                        )
                    }
                }
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}

@Composable
private fun ReportsView(
    data: BusinessSnapshotData,
    isOwner: Boolean,
) {
    val context = LocalContext.current
    var selectedReport by remember { mutableStateOf(ReportKind.DAILY) }

    val (reportTxs, summaryLines, plSummary) = remember(data, selectedReport) {
        buildReportData(data, selectedReport)
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeReportPdf(out, selectedReport.labelBn, summaryLines, reportTxs, context = context)
                }
            }.onSuccess {
                Toast.makeText(context, "রিপোর্ট PDF সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "PDF ত্রুটি: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ReportExporter.writeTransactionsCsv(out, selectedReport.labelBn, summaryLines, reportTxs)
                }
            }.onSuccess {
                Toast.makeText(context, "রিপোর্ট Excel (CSV) সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "CSV ত্রুটি: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportKind.entries.forEach { kind ->
                    FilterChip(
                        selected = selectedReport == kind,
                        onClick = { selectedReport = kind },
                        label = { Text(kind.labelBn) },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { pdfLauncher.launch("${selectedReport.name}_${System.currentTimeMillis()}.pdf") }) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                    Text(" PDF ডাউনলোড")
                }
                OutlinedButton(onClick = { csvLauncher.launch("${selectedReport.name}_${System.currentTimeMillis()}.csv") }) {
                    Icon(Icons.Filled.TableChart, contentDescription = null)
                    Text(" Excel (CSV)")
                }
                OutlinedButton(onClick = { ReportExporter.printReport(context, selectedReport.labelBn, summaryLines, reportTxs) }) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Text(" প্রিন্ট")
                }
            }
        }
        if (!isOwner) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "সাধারণ মেম্বার হিসেবে আপনি শুধুমাত্র আপনার তৈরি করা লেনদেনের রিপোর্ট দেখতে ও এক্সপোর্ট করতে পারবেন। সম্পূর্ণ ব্যবসার লাভ-ক্ষতি রিপোর্ট শুধুমাত্র সুপার অ্যাডমিনের জন্য সীমাবদ্ধ।",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(selectedReport.labelBn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    summaryLines.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (plSummary != null) {
            item { ProfitLossBarChart(summary = plSummary) }
        }
        item {
            Text("রিপোর্টভুক্ত লেনদেন (${reportTxs.size}টি)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(reportTxs, key = { "rep_${it.id}" }) { tx ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(TxType.fromRaw(tx.txType).labelBn, fontWeight = FontWeight.Bold)
                        StatusBadge(tx.status)
                    }
                    Text("${tx.customerName ?: tx.notes ?: "—"} • ${MoneyFormatter.formatDateTime(tx.occurredAtMillis)}", style = MaterialTheme.typography.bodySmall)
                    if (!tx.accountChangesSummary.isNullOrBlank()) {
                        Text(tx.accountChangesSummary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun buildReportData(
    data: BusinessSnapshotData,
    kind: ReportKind,
): Triple<List<BusinessTransaction>, List<Pair<String, String>>, com.example.domain.ProfitLossSummary?> {
    val approvedTxs = data.transactions.filter { it.status == TxStatus.APPROVED.name }
    return when (kind) {
        ReportKind.DAILY, ReportKind.WEEKLY, ReportKind.MONTHLY, ReportKind.YEARLY -> {
            val period = when (kind) {
                ReportKind.DAILY -> ReportPeriod.DAILY
                ReportKind.WEEKLY -> ReportPeriod.WEEKLY
                ReportKind.MONTHLY -> ReportPeriod.MONTHLY
                else -> ReportPeriod.YEARLY
            }
            val start = AccountingEngine.periodStartMillis(period)
            val txs = approvedTxs.filter { it.occurredAtMillis >= start }
            val pl = AccountingEngine.calculateProfitLoss(data.ledger, period)
            val summary = listOf(
                "অনুমোদিত লেনদেন" to "${txs.size}টি",
                "কমিশন আয়" to MoneyFormatter.formatTaka(pl.commissionIncomePaisa),
                "দোকানের খরচ" to MoneyFormatter.formatTaka(pl.shopExpensesPaisa),
                "পার্টনার কমিশন ব্যয়" to MoneyFormatter.formatTaka(pl.partnerCommissionExpensesPaisa),
                "নিট লাভ/ক্ষতি" to MoneyFormatter.formatTaka(pl.netProfitLossPaisa, showPlusSign = true),
                "ঋণের সুদ আয় (পৃথক)" to MoneyFormatter.formatTaka(pl.interestIncomePaisa),
                "ঋণের সুদ ব্যয় (পৃথক)" to MoneyFormatter.formatTaka(pl.interestExpensePaisa),
            )
            Triple(txs, summary, pl)
        }
        ReportKind.ACCOUNT_WISE -> {
            val lines = data.accounts.map { it.name to "${MoneyFormatter.formatTaka(it.balancePaisa)} (মূলধন: ${MoneyFormatter.formatTaka(it.capitalPaisa)})" } +
                listOf("সর্বমোট ব্যালেন্স" to MoneyFormatter.formatTaka(data.metrics.grandTotalBalancePaisa))
            Triple(approvedTxs, lines, null)
        }
        ReportKind.EMPLOYEE_WISE -> {
            val grouped = data.transactions.groupBy { it.createdByName }
            val lines = grouped.map { (empName, list) ->
                empName to "${list.size}টি (অনুমোদিত: ${list.count { it.status == TxStatus.APPROVED.name }}, পেন্ডিং: ${list.count { it.status == TxStatus.PENDING.name }})"
            }
            Triple(data.transactions, lines.ifEmpty { listOf("তথ্য" to "কোনো সাধারণ মেম্বারের লেনদেন নেই") }, null)
        }
        ReportKind.COMMISSION -> {
            val commTxs = approvedTxs.filter { it.manualCommissionPaisa > 0L || it.partnerCommissionPaisa > 0L }
            val lines = listOf(
                "মোট গ্রাহক/রিচার্জ/বিল কমিশন আয়" to MoneyFormatter.formatTaka(data.metrics.profitLossAll.commissionIncomePaisa),
                "মোট পার্টনার কমিশন ব্যয়" to MoneyFormatter.formatTaka(data.metrics.profitLossAll.partnerCommissionExpensesPaisa),
            )
            Triple(commTxs, lines, null)
        }
        ReportKind.PARTNER -> {
            val pTxs = approvedTxs.filter {
                it.txType == TxType.PARTNER_SEND.name || it.txType == TxType.PARTNER_RECEIVE.name || it.txType == TxType.PARTNER_COMMISSION_PAY.name
            }
            val lines = data.partners.map {
                it.name to "পাঠানো: ${MoneyFormatter.formatTaka(it.sentTotalPaisa)} | বকেয়া: ${MoneyFormatter.formatTaka(it.currentBalancePaisa)} | প্রদেয় কমিশন: ${MoneyFormatter.formatTaka(it.payableCommissionPaisa)}"
            }
            Triple(pTxs, lines, null)
        }
        ReportKind.EXPENSE -> {
            val expTxs = approvedTxs.filter { it.txType == TxType.SHOP_EXPENSE.name }
            val lines = listOf(
                "মোট দোকানের খরচ" to MoneyFormatter.formatTaka(data.metrics.profitLossAll.shopExpensesPaisa),
                "খরচ এন্ট্রি সংখ্যা" to "${expTxs.size}টি",
            )
            Triple(expTxs, lines, null)
        }
        ReportKind.LOAN -> {
            val lTxs = approvedTxs.filter { it.txType == TxType.LOAN_BORROW.name || it.txType == TxType.LOAN_LEND.name || it.txType == TxType.LOAN_REPAY.name }
            val payable = data.loans.filter { it.loanType == "BORROWED" && it.status != LoanStatus.PAID.name }.sumOf { it.remainingPrincipalPaisa }
            val receivable = data.loans.filter { it.loanType == "LENT" && it.status != LoanStatus.PAID.name }.sumOf { it.remainingPrincipalPaisa }
            val lines = listOf(
                "মোট বকেয়া দায় (ধার নিয়েছি)" to MoneyFormatter.formatTaka(payable),
                "মোট পাওনা (ধার দিয়েছি)" to MoneyFormatter.formatTaka(receivable),
                "সক্রিয় ঋণ সংখ্যা" to "${data.loans.count { it.status != LoanStatus.PAID.name }}টি",
            )
            Triple(lTxs, lines, null)
        }
        ReportKind.PROFIT_LOSS -> {
            val pl = data.metrics.profitLossAll
            val lines = listOf(
                "মোট কমিশন আয়" to MoneyFormatter.formatTaka(pl.commissionIncomePaisa),
                "মোট দোকানের খরচ" to MoneyFormatter.formatTaka(pl.shopExpensesPaisa),
                "মোট পার্টনার কমিশন ব্যয়" to MoneyFormatter.formatTaka(pl.partnerCommissionExpensesPaisa),
                "নিট লাভ/ক্ষতি" to MoneyFormatter.formatTaka(pl.netProfitLossPaisa, showPlusSign = true),
                "সুদ আয় (পৃথক)" to MoneyFormatter.formatTaka(pl.interestIncomePaisa),
                "সুদ ব্যয় (পৃথক)" to MoneyFormatter.formatTaka(pl.interestExpensePaisa),
            )
            Triple(approvedTxs, lines, pl)
        }
        ReportKind.PENDING -> {
            val pending = data.transactions.filter { it.status == TxStatus.PENDING.name }
            Triple(pending, listOf("মোট অপেক্ষমাণ লেনদেন" to "${pending.size}টি"), null)
        }
    }
}
