package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.NewTxRequest
import com.example.domain.AccountingEngine
import com.example.domain.BusinessConstants
import com.example.domain.FinancialAccount
import com.example.domain.LoanRecord
import com.example.domain.LoanStatus
import com.example.domain.LoanType
import com.example.domain.MoneyFormatter
import com.example.domain.PartnerAccount
import com.example.domain.TxType
import com.example.ui.BusinessSnapshotData
import com.example.ui.components.AccountDropdown
import com.example.ui.components.BengaliDatePickerField
import com.example.ui.components.SimpleDropdown
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ProfitGreen

@Composable
fun PartnersAndLoansScreen(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmitTx: (NewTxRequest, onClear: () -> Unit) -> Unit,
    onUpdatePartnerRate: (partnerId: String, ratePerThousandPaisa: Long, bkashOnly: Boolean) -> Unit,
) {
    var mainTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mainTab == 0,
                onClick = { mainTab = 0 },
                label = { Text("পার্টনার ব্যবস্থাপনা") },
                modifier = Modifier.testTag("tab_partners"),
            )
            FilterChip(
                selected = mainTab == 1,
                onClick = { mainTab = 1 },
                label = { Text("ঋণ ও দেনা-পাওনা") },
                modifier = Modifier.testTag("tab_loans"),
            )
        }
        Spacer(Modifier.height(10.dp))

        if (mainTab == 0) {
            PartnersSection(
                data = data,
                isOwner = isOwner,
                isWorking = isWorking,
                onSubmitTx = onSubmitTx,
                onUpdatePartnerRate = onUpdatePartnerRate,
            )
        } else {
            LoansSection(
                data = data,
                isOwner = isOwner,
                isWorking = isWorking,
                onSubmitTx = onSubmitTx,
            )
        }
    }
}

@Composable
private fun PartnersSection(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmitTx: (NewTxRequest, onClear: () -> Unit) -> Unit,
    onUpdatePartnerRate: (String, Long, Boolean) -> Unit,
) {
    var actionPartner by remember { mutableStateOf<Pair<PartnerAccount, TxType>?>(null) }
    var editingRatePartner by remember { mutableStateOf<PartnerAccount?>(null) }

    actionPartner?.let { (partner, txType) ->
        PartnerTransactionDialog(
            partner = partner,
            txType = txType,
            accounts = data.accounts,
            isWorking = isWorking,
            onDismiss = { actionPartner = null },
            onConfirm = { accId, amtPaisa, notes ->
                onSubmitTx(
                    NewTxRequest(
                        txType = txType,
                        partnerId = partner.partnerId,
                        customerName = partner.name,
                        receivedAccountId = if (txType == TxType.PARTNER_RECEIVE) accId else null,
                        receivedAmountPaisa = if (txType == TxType.PARTNER_RECEIVE) amtPaisa else 0L,
                        paidAccountId = if (txType != TxType.PARTNER_RECEIVE) accId else null,
                        paidAmountPaisa = if (txType != TxType.PARTNER_RECEIVE) amtPaisa else 0L,
                        notes = notes.ifBlank { "${partner.name} — ${txType.labelBn}" },
                    )
                ) {
                    actionPartner = null
                }
            },
        )
    }

    editingRatePartner?.let { partner ->
        PartnerRateDialog(
            partner = partner,
            onDismiss = { editingRatePartner = null },
            onSave = { ratePaisa, bkashOnly ->
                onUpdatePartnerRate(partner.partnerId, ratePaisa, bkashOnly)
                editingRatePartner = null
            },
        )
    }

    val partnerTxs = data.transactions.filter {
        it.txType == TxType.PARTNER_SEND.name ||
            it.txType == TxType.PARTNER_RECEIVE.name ||
            it.txType == TxType.PARTNER_COMMISSION_PAY.name
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(data.partners, key = { it.partnerId }) { partner ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(partner.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isOwner) {
                            OutlinedButton(onClick = { editingRatePartner = partner }) {
                                Text("কমিশন সেটিংস")
                            }
                        }
                    }

                    val ruleDesc = if (partner.ratePerThousandPaisa > 0L) {
                        val scopeText = if (partner.bkashOnlyCommission) "শুধুমাত্র নিজস্ব bKash থেকে পাঠালে" else "যেকোনো অ্যাকাউন্ট থেকে পাঠালে"
                        "$scopeText হাজারে ${MoneyFormatter.formatTaka(partner.ratePerThousandPaisa)} পার্টনার কমিশন ব্যয় (নগদ/ব্যাংক বা টাকা গ্রহণে ৳0)"
                    } else {
                        "কমিশন চার্জ: ৳0 — কোনো কমিশন কাটা হবে না, প্রদত্ত/প্রাপ্ত সম্পূর্ণ টাকা সরাসরি রেকর্ড হবে"
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(ruleDesc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCol("পাঠানো হয়েছে", MoneyFormatter.formatTaka(partner.sentTotalPaisa))
                        StatCol("পাওয়া গেছে", MoneyFormatter.formatTaka(partner.receivedTotalPaisa))
                        StatCol("বর্তমান বকেয়া", MoneyFormatter.formatTaka(partner.currentBalancePaisa))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCol("প্রদেয় কমিশন (দায়)", MoneyFormatter.formatTaka(partner.payableCommissionPaisa), PendingAmber)
                        StatCol("পরিশোধিত কমিশন", MoneyFormatter.formatTaka(partner.paidCommissionPaisa), ProfitGreen)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { actionPartner = partner to TxType.PARTNER_SEND },
                            modifier = Modifier.weight(1f).testTag("partner_send_${partner.partnerId}"),
                        ) {
                            Text("টাকা পাঠান")
                        }
                        OutlinedButton(
                            onClick = { actionPartner = partner to TxType.PARTNER_RECEIVE },
                            modifier = Modifier.weight(1f).testTag("partner_recv_${partner.partnerId}"),
                        ) {
                            Text("টাকা গ্রহণ")
                        }
                    }
                    if (isOwner && partner.payableCommissionPaisa > 0L) {
                        OutlinedButton(
                            onClick = { actionPartner = partner to TxType.PARTNER_COMMISSION_PAY },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("কমিশন পরিশোধ করুন (${MoneyFormatter.formatTaka(partner.payableCommissionPaisa)})")
                        }
                    }
                }
            }
        }

        item {
            Text("পার্টনার লেনদেন ইতিহাস (${partnerTxs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(partnerTxs, key = { "ptx_${it.id}" }) { tx ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${tx.customerName ?: tx.partnerId} • ${TxType.fromRaw(tx.txType).labelBn}", fontWeight = FontWeight.Bold)
                        StatusBadge(tx.status)
                    }
                    val amt = if (tx.receivedAmountPaisa > 0) tx.receivedAmountPaisa else tx.paidAmountPaisa
                    val accName = BusinessConstants.accountDisplayName(tx.receivedAccountId ?: tx.paidAccountId)
                    Text("$accName: ${MoneyFormatter.formatTaka(amt)}", fontWeight = FontWeight.SemiBold)
                    if (!tx.calculationExplanation.isNullOrBlank()) {
                        Text(tx.calculationExplanation, style = MaterialTheme.typography.bodySmall, color = PendingAmber)
                    }
                    Text(MoneyFormatter.formatDateTime(tx.occurredAtMillis), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StatCol(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun PartnerTransactionDialog(
    partner: PartnerAccount,
    txType: TxType,
    accounts: List<FinancialAccount>,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (accountId: String, amountPaisa: Long, notes: String) -> Unit,
) {
    var accId by remember { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    val parsedPaisa = MoneyFormatter.parseTakaToPaisa(amount) ?: 0L
    val (_, liveExplanation) = remember(partner, accId, parsedPaisa, txType) {
        if (txType == TxType.PARTNER_SEND) {
            AccountingEngine.calculatePartnerCommission(partner, accId, parsedPaisa, true)
        } else if (txType == TxType.PARTNER_RECEIVE) {
            AccountingEngine.calculatePartnerCommission(partner, accId, parsedPaisa, false)
        } else {
            0L to "প্রদেয় পার্টনার কমিশন পরিশোধ করা হচ্ছে"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${partner.name} — ${txType.labelBn}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountDropdown(
                    label = if (txType == TxType.PARTNER_RECEIVE) "গ্রহণ অ্যাকাউন্ট" else "প্রদান অ্যাকাউন্ট",
                    selectedAccountId = accId,
                    accounts = accounts,
                    onSelected = { accId = it },
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; err = null },
                    label = { Text("টাকার পরিমাণ (৳) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(liveExplanation, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = MoneyFormatter.parseTakaToPaisa(amount)
                    if (p == null || p <= 0L) err = "সঠিক টাকার পরিমাণ লিখুন।"
                    else onConfirm(accId, p, notes)
                },
                enabled = !isWorking,
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
private fun PartnerRateDialog(
    partner: PartnerAccount,
    onDismiss: () -> Unit,
    onSave: (ratePerThousandPaisa: Long, bkashOnly: Boolean) -> Unit,
) {
    var rateText by remember { mutableStateOf(MoneyFormatter.formatTakaPlain(partner.ratePerThousandPaisa)) }
    var bkashOnly by remember { mutableStateOf(partner.bkashOnlyCommission) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${partner.name} — কমিশন হার") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it; err = null },
                    label = { Text("প্রতি ৳১,০০০-এ কমিশন (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("শুধুমাত্র bKash পাঠালে প্রযোজ্য", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = bkashOnly, onCheckedChange = { bkashOnly = it })
                }
                if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = MoneyFormatter.parseTakaToPaisa(rateText)
                    if (p == null || p < 0L) err = "সঠিক হার লিখুন।"
                    else onSave(p, bkashOnly)
                }
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}

@Composable
private fun LoansSection(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmitTx: (NewTxRequest, onClear: () -> Unit) -> Unit,
) {
    var subTab by remember { mutableIntStateOf(0) } // 0=BORROWED, 1=LENT
    var showNewLoanDialog by remember { mutableStateOf(false) }
    var repayTarget by remember { mutableStateOf<LoanRecord?>(null) }

    if (showNewLoanDialog) {
        NewLoanDialog(
            loanType = if (subTab == 0) LoanType.BORROWED else LoanType.LENT,
            accounts = data.accounts,
            isWorking = isWorking,
            onDismiss = { showNewLoanDialog = false },
            onConfirm = { req ->
                onSubmitTx(req) { showNewLoanDialog = false }
            },
        )
    }

    repayTarget?.let { loan ->
        LoanRepayDialog(
            loan = loan,
            accounts = data.accounts,
            isWorking = isWorking,
            onDismiss = { repayTarget = null },
            onConfirm = { req ->
                onSubmitTx(req) { repayTarget = null }
            },
        )
    }

    val targetType = if (subTab == 0) LoanType.BORROWED.name else LoanType.LENT.name
    val filteredLoans = data.loans.filter { it.loanType == targetType }
    val now = System.currentTimeMillis()
    val totalPayable = data.loans
        .filter { it.loanType == LoanType.BORROWED.name && it.status != LoanStatus.PAID.name }
        .sumOf { it.remainingPrincipalPaisa + it.remainingInterestPaisa }
    val totalReceivable = data.loans
        .filter { it.loanType == LoanType.LENT.name && it.status != LoanStatus.PAID.name }
        .sumOf { it.remainingPrincipalPaisa + it.remainingInterestPaisa }
    val overdueCount = data.loans.count {
        it.status != LoanStatus.PAID.name && it.dueDateMillis > 0L && it.dueDateMillis < now
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatCol("মোট বকেয়া দায়", MoneyFormatter.formatTaka(totalPayable), LossCrimson)
                    StatCol("মোট পাওনা", MoneyFormatter.formatTaka(totalReceivable), ProfitGreen)
                    StatCol("মেয়াদোত্তীর্ণ", "${overdueCount}টি", if (overdueCount > 0) LossCrimson else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    label = { Text("ধার নিয়েছি (দায়)") },
                )
                FilterChip(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    label = { Text("ধার দিয়েছি (পাওনা)") },
                )
            }
        }
        item {
            Button(
                onClick = { showNewLoanDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (subTab == 0) "+ নতুন ঋণ গ্রহণ (দায়)" else "+ নতুন ঋণ প্রদান (পাওনা)")
            }
        }
        item {
            Text(
                "ঋণের আসল টাকা আয় বা খরচ হিসেবে গণ্য হয় না। সুদ আলাদাভাবে হিসাব করা হয়।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(filteredLoans, key = { it.id }) { loan ->
            val overdue = loan.status != LoanStatus.PAID.name && loan.dueDateMillis > 0L && loan.dueDateMillis < now
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(loan.personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (overdue) "মেয়াদোত্তীর্ণ (Overdue)" else LoanStatus.fromRaw(loan.status).labelBn,
                            color = if (overdue) LossCrimson else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCol("মূল টাকা", MoneyFormatter.formatTaka(loan.principalPaisa))
                        StatCol("বাকি আসল", MoneyFormatter.formatTaka(loan.remainingPrincipalPaisa), LossCrimson)
                        StatCol("বাকি সুদ", MoneyFormatter.formatTaka(loan.remainingInterestPaisa), PendingAmber)
                    }
                    Text(
                        "তারিখ: ${MoneyFormatter.formatDate(loan.loanDateMillis)} | পরিশোধের শেষ তারিখ: ${MoneyFormatter.formatDate(loan.dueDateMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!loan.notes.isNullOrBlank()) {
                        Text("নোট: ${loan.notes}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (loan.status != LoanStatus.PAID.name) {
                        OutlinedButton(
                            onClick = { repayTarget = loan },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isOwner) "আংশিক / সম্পূর্ণ পরিশোধ রেকর্ড করুন" else "পরিশোধ জমা দিন (Pending)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewLoanDialog(
    loanType: LoanType,
    accounts: List<FinancialAccount>,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (NewTxRequest) -> Unit,
) {
    val initialNow = remember { System.currentTimeMillis() }
    var personName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var accId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var principal by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("") }
    var loanDateMillis by remember { mutableLongStateOf(initialNow) }
    var dueDateMillis by remember { mutableLongStateOf(initialNow + 30L * 86_400_000L) }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loanType.labelBn) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; err = null },
                    label = { Text("ব্যক্তির নাম *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AccountDropdown(
                    label = if (loanType == LoanType.BORROWED) "টাকা গ্রহণের অ্যাকাউন্ট" else "টাকা প্রদানের অ্যাকাউন্ট",
                    selectedAccountId = accId,
                    accounts = accounts,
                    onSelected = { accId = it },
                )
                OutlinedTextField(
                    value = principal,
                    onValueChange = { principal = it; err = null },
                    label = { Text("আসল টাকা (৳) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = interest,
                    onValueChange = { interest = it },
                    label = { Text("সুদ (৳, ঐচ্ছিক — ডিফল্ট ০)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BengaliDatePickerField(
                    label = "ঋণের তারিখ",
                    dateMillis = loanDateMillis,
                    onDateSelected = {
                        loanDateMillis = it
                        err = null
                    },
                )
                BengaliDatePickerField(
                    label = "পরিশোধের শেষ তারিখ",
                    dateMillis = dueDateMillis,
                    onDateSelected = {
                        dueDateMillis = it
                        err = null
                    },
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pPaisa = MoneyFormatter.parseTakaToPaisa(principal)
                    val iPaisa = if (interest.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(interest)
                    when {
                        personName.isBlank() -> err = "ব্যক্তির নাম লিখুন।"
                        pPaisa == null || pPaisa <= 0L || iPaisa == null -> err = "সঠিক টাকার পরিমাণ লিখুন।"
                        dueDateMillis < loanDateMillis -> err = "পরিশোধের তারিখ ঋণের তারিখের আগে হতে পারবে না।"
                        else -> {
                            onConfirm(
                                NewTxRequest(
                                    txType = if (loanType == LoanType.BORROWED) TxType.LOAN_BORROW else TxType.LOAN_LEND,
                                    customerName = personName,
                                    customerPhone = phone,
                                    loanType = loanType.name,
                                    receivedAccountId = if (loanType == LoanType.BORROWED) accId else null,
                                    receivedAmountPaisa = if (loanType == LoanType.BORROWED) pPaisa else 0L,
                                    paidAccountId = if (loanType == LoanType.LENT) accId else null,
                                    paidAmountPaisa = if (loanType == LoanType.LENT) pPaisa else 0L,
                                    interestPaisa = iPaisa,
                                    occurredAtMillis = loanDateMillis,
                                    dueDateMillis = dueDateMillis,
                                    notes = notes,
                                )
                            )
                        }
                    }
                },
                enabled = !isWorking,
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}

@Composable
private fun LoanRepayDialog(
    loan: LoanRecord,
    accounts: List<FinancialAccount>,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (NewTxRequest) -> Unit,
) {
    val isBorrowed = loan.loanType == LoanType.BORROWED.name
    var accId by remember { mutableStateOf(loan.accountId) }
    var principalPaid by remember { mutableStateOf(MoneyFormatter.formatTakaPlain(loan.remainingPrincipalPaisa)) }
    var interestPaid by remember { mutableStateOf(MoneyFormatter.formatTakaPlain(loan.remainingInterestPaisa)) }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${loan.personName} — ঋণ পরিশোধ") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccountDropdown(
                    label = if (isBorrowed) "কোন অ্যাকাউন্ট থেকে দিচ্ছেন" else "কোন অ্যাকাউন্টে পাচ্ছেন",
                    selectedAccountId = accId,
                    accounts = accounts,
                    onSelected = { accId = it },
                )
                OutlinedTextField(
                    value = principalPaid,
                    onValueChange = { principalPaid = it; err = null },
                    label = { Text("আসল পরিশোধ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = interestPaid,
                    onValueChange = { interestPaid = it; err = null },
                    label = { Text("সুদ পরিশোধ (৳, ঐচ্ছিক)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("নোট") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pPaisa = if (principalPaid.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(principalPaid)
                    val iPaisa = if (interestPaid.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(interestPaid)
                    if (pPaisa == null || iPaisa == null || (pPaisa + iPaisa) <= 0L) {
                        err = "সঠিক টাকার পরিমাণ লিখুন।"
                    } else {
                        onConfirm(
                            NewTxRequest(
                                txType = TxType.LOAN_REPAY,
                                loanId = loan.id,
                                loanType = loan.loanType,
                                customerName = loan.personName,
                                receivedAccountId = if (!isBorrowed) accId else null,
                                receivedAmountPaisa = if (!isBorrowed) pPaisa else 0L,
                                paidAccountId = if (isBorrowed) accId else null,
                                paidAmountPaisa = if (isBorrowed) pPaisa else 0L,
                                interestPaisa = iPaisa,
                                notes = notes.ifBlank { "ঋণ পরিশোধ (${loan.personName})" },
                            )
                        )
                    }
                },
                enabled = !isWorking,
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("বাতিল") }
        },
    )
}
