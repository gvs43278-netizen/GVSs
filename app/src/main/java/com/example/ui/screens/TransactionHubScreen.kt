package com.example.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.domain.BillType
import com.example.domain.BusinessConstants
import com.example.domain.BusinessTransaction
import com.example.domain.ExpenseCategory
import com.example.domain.FinancialAccount
import com.example.domain.MoneyFormatter
import com.example.domain.TxStatus
import com.example.domain.TxType
import com.example.ui.BusinessSnapshotData
import com.example.ui.components.AccountDropdown
import com.example.ui.components.BengaliDatePickerField
import com.example.ui.components.SimpleDropdown
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.ProfitGreen

@Composable
fun TransactionHubScreen(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    isWorking: Boolean,
    initialTab: Int = 0,
    onSubmitTx: (NewTxRequest, onClear: () -> Unit) -> Unit,
    onApproveTx: (String) -> Unit,
    onRemoveTx: (String) -> Unit,
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf(
        "গ্রাহক লেনদেন",
        "অভ্যন্তরীণ ট্রান্সফার",
        "মোবাইল রিচার্জ",
        "বিদ্যুৎ/ওয়াইফাই বিল",
        "দোকানের খরচ",
        "অপেক্ষমাণ অনুমোদন (${data.metrics.pendingCount})",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    label = { Text(label) },
                    modifier = Modifier.testTag("tx_tab_$idx"),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (data.accounts.isEmpty()) {
            Text("অ্যাকাউন্ট লোড হচ্ছে...", style = MaterialTheme.typography.bodyMedium)
            return
        }

        when (selectedTab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    CustomerTransactionForm(
                        accounts = data.accounts,
                        isOwner = isOwner,
                        isWorking = isWorking,
                        onSubmit = onSubmitTx,
                    )
                }
            }
            1 -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    InternalTransferForm(
                        accounts = data.accounts,
                        isOwner = isOwner,
                        isWorking = isWorking,
                        onSubmit = onSubmitTx,
                    )
                }
            }
            2 -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    MobileRechargeForm(
                        accounts = data.accounts,
                        isOwner = isOwner,
                        isWorking = isWorking,
                        onSubmit = onSubmitTx,
                    )
                }
            }
            3 -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    BillPaymentForm(
                        accounts = data.accounts,
                        isOwner = isOwner,
                        isWorking = isWorking,
                        onSubmit = onSubmitTx,
                    )
                }
            }
            4 -> LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    ShopExpenseForm(
                        accounts = data.accounts,
                        isOwner = isOwner,
                        isWorking = isWorking,
                        onSubmit = onSubmitTx,
                    )
                }
            }
            5 -> PendingQueueList(
                transactions = data.transactions.filter { it.status == TxStatus.PENDING.name },
                isOwner = isOwner,
                isWorking = isWorking,
                onApprove = onApproveTx,
                onRemove = onRemoveTx,
            )
        }
    }
}

@Composable
private fun CustomerTransactionForm(
    accounts: List<FinancialAccount>,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmit: (NewTxRequest, onClear: () -> Unit) -> Unit,
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var receivedAccountId by remember { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var receivedAmount by remember { mutableStateOf("") }
    var paidAccountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var paidAmount by remember { mutableStateOf("") }
    var manualCommission by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var refTxId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("গ্রাহক লেনদেন এন্ট্রি", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "গুরুত্বপূর্ণ: কমিশন সম্পূর্ণ ঐচ্ছিক এবং স্বয়ংক্রিয়ভাবে হিসাব করা হয় না। গ্রহণ ও প্রদান আলাদাভাবে লিখুন।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it; localError = null },
                label = { Text("গ্রাহকের নাম *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cust_name_input"),
            )
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("মোবাইল নম্বর (ঐচ্ছিক)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            AccountDropdown(
                label = "গ্রহণ অ্যাকাউন্ট (টাকা এসেছে)",
                selectedAccountId = receivedAccountId,
                accounts = accounts,
                onSelected = { receivedAccountId = it },
                testTag = "cust_recv_acc",
            )
            OutlinedTextField(
                value = receivedAmount,
                onValueChange = { receivedAmount = it; localError = null },
                label = { Text("প্রাপ্ত টাকা (৳) — ০ হলে ফাঁকা রাখুন") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cust_recv_amt"),
            )

            AccountDropdown(
                label = "প্রদান অ্যাকাউন্ট (টাকা দিয়েছেন)",
                selectedAccountId = paidAccountId,
                accounts = accounts,
                onSelected = { paidAccountId = it },
                testTag = "cust_paid_acc",
            )
            OutlinedTextField(
                value = paidAmount,
                onValueChange = { paidAmount = it; localError = null },
                label = { Text("প্রদত্ত টাকা (৳) — ০ হলে ফাঁকা রাখুন") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cust_paid_amt"),
            )

            OutlinedTextField(
                value = manualCommission,
                onValueChange = { manualCommission = it; localError = null },
                label = { Text("কমিশন আয় (৳) — ঐচ্ছিক, ম্যানুয়ালি লিখুন") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("cust_comm_input"),
            )

            OutlinedTextField(
                value = refTxId,
                onValueChange = { refTxId = it },
                label = { Text("ট্রানজেকশন আইডি / রেফারেন্স (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BengaliDatePickerField(
                label = "লেনদেনের তারিখ",
                dateMillis = occurredAtMillis,
                onDateSelected = { occurredAtMillis = it },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("নোট / বিবরণ") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (localError != null) {
                Text(localError!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    val recvPaisa = if (receivedAmount.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(receivedAmount)
                    val paidPaisa = if (paidAmount.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(paidAmount)
                    val commPaisa = if (manualCommission.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(manualCommission)
                    when {
                        customerName.isBlank() -> localError = "গ্রাহকের নাম লিখুন।"
                        recvPaisa == null || paidPaisa == null || commPaisa == null -> localError = "সঠিক টাকার পরিমাণ লিখুন।"
                        recvPaisa == 0L && paidPaisa == 0L -> localError = "প্রাপ্ত বা প্রদত্ত টাকার পরিমাণ ০-এর বেশি হতে হবে।"
                        else -> {
                            onSubmit(
                                NewTxRequest(
                                    txType = TxType.CUSTOMER_TX,
                                    customerName = customerName,
                                    customerPhone = customerPhone,
                                    receivedAccountId = if (recvPaisa > 0L) receivedAccountId else null,
                                    receivedAmountPaisa = recvPaisa,
                                    paidAccountId = if (paidPaisa > 0L) paidAccountId else null,
                                    paidAmountPaisa = paidPaisa,
                                    manualCommissionPaisa = commPaisa,
                                    referenceTxId = refTxId,
                                    notes = notes,
                                    occurredAtMillis = occurredAtMillis,
                                )
                            ) {
                                customerName = ""
                                customerPhone = ""
                                receivedAmount = ""
                                paidAmount = ""
                                manualCommission = ""
                                refTxId = ""
                                notes = ""
                                occurredAtMillis = System.currentTimeMillis()
                            }
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("cust_submit_btn"),
            ) {
                Text(if (isOwner) "লেনদেন সংরক্ষণ ও অনুমোদন করুন" else "লেনদেন জমা দিন (Pending)")
            }
        }
    }
}

@Composable
private fun InternalTransferForm(
    accounts: List<FinancialAccount>,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmit: (NewTxRequest, onClear: () -> Unit) -> Unit,
) {
    var fromId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var toId by remember { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var amount by remember { mutableStateOf("") }
    var explicitFee by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var refId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("অভ্যন্তরীণ অ্যাকাউন্ট ট্রান্সফার", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "এক অ্যাকাউন্ট থেকে অন্য অ্যাকাউন্টে টাকা স্থানান্তর — এটি আয়, খরচ বা লাভ হিসেবে গণ্য হয় না।",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AccountDropdown("উৎস অ্যাকাউন্ট (বিয়োগ হবে)", fromId, accounts, { fromId = it }, testTag = "xfer_from")
            AccountDropdown("গন্তব্য অ্যাকাউন্ট (যোগ হবে)", toId, accounts, { toId = it }, testTag = "xfer_to")

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; localError = null },
                label = { Text("ট্রান্সফার পরিমাণ (৳) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("xfer_amt"),
            )
            OutlinedTextField(
                value = explicitFee,
                onValueChange = { explicitFee = it; localError = null },
                label = { Text("আলাদা ফি থাকলে লিখুন (৳, ঐচ্ছিক — ডিফল্ট ০)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = refId,
                onValueChange = { refId = it },
                label = { Text("রেফারেন্স / ট্রানজেকশন আইডি") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BengaliDatePickerField(
                label = "তারিখ",
                dateMillis = occurredAtMillis,
                onDateSelected = { occurredAtMillis = it },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("নোট") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (localError != null) Text(localError!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    val amt = MoneyFormatter.parseTakaToPaisa(amount)
                    val fee = if (explicitFee.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(explicitFee)
                    when {
                        fromId == toId -> localError = "উৎস ও গন্তব্য অ্যাকাউন্ট আলাদা হতে হবে।"
                        amt == null || amt <= 0L || fee == null -> localError = "সঠিক টাকার পরিমাণ লিখুন।"
                        else -> onSubmit(
                            NewTxRequest(
                                txType = TxType.INTERNAL_TRANSFER,
                                paidAccountId = fromId,
                                paidAmountPaisa = amt,
                                receivedAccountId = toId,
                                receivedAmountPaisa = amt,
                                explicitFeePaisa = fee,
                                referenceTxId = refId,
                                notes = notes.ifBlank { "অভ্যন্তরীণ ট্রান্সফার" },
                                occurredAtMillis = occurredAtMillis,
                            )
                        ) {
                            amount = ""; explicitFee = ""; refId = ""; notes = ""; occurredAtMillis = System.currentTimeMillis()
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("xfer_submit_btn"),
            ) {
                Text(if (isOwner) "ট্রান্সফার সম্পন্ন করুন" else "ট্রান্সফার জমা দিন (Pending)")
            }
        }
    }
}

@Composable
private fun MobileRechargeForm(
    accounts: List<FinancialAccount>,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmit: (NewTxRequest, onClear: () -> Unit) -> Unit,
) {
    var customerName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf(BusinessConstants.MOBILE_OPERATORS.first()) }
    var rechargeAmount by remember { mutableStateOf("") }
    var receivedAmount by remember { mutableStateOf("") }
    var receivedAccountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var rechargeAccountId by remember { mutableStateOf(BusinessConstants.ACC_EASY_RECHARGE) }
    var manualComm by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var refId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("মোবাইল রিচার্জ লেজার", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "এটি শুধুমাত্র রিচার্জ হিসাব সংরক্ষণের লেজার। এখান থেকে সরাসরি সিম রিচার্জ পাঠানো হয় না।",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("গ্রাহকের নাম *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = mobileNumber,
                onValueChange = { mobileNumber = it },
                label = { Text("মোবাইল নম্বর *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SimpleDropdown(
                label = "অপারেটর",
                selectedLabel = operator,
                items = BusinessConstants.MOBILE_OPERATORS.map { it to it },
                onSelected = { operator = it },
            )
            AccountDropdown("রিচার্জ অ্যাকাউন্ট (ডিফল্ট: Easy Recharge)", rechargeAccountId, accounts, { rechargeAccountId = it })
            OutlinedTextField(
                value = rechargeAmount,
                onValueChange = { rechargeAmount = it; localError = null },
                label = { Text("রিচার্জের পরিমাণ (৳) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rech_amt"),
            )
            AccountDropdown("টাকা গ্রহণের অ্যাকাউন্ট", receivedAccountId, accounts, { receivedAccountId = it })
            OutlinedTextField(
                value = receivedAmount,
                onValueChange = { receivedAmount = it; localError = null },
                label = { Text("গ্রাহকের কাছ থেকে প্রাপ্ত টাকা (৳) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rech_recv"),
            )
            OutlinedTextField(
                value = manualComm,
                onValueChange = { manualComm = it; localError = null },
                label = { Text("কমিশন আয় (৳) — ঐচ্ছিক, ম্যানুয়ালি লিখুন") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("rech_comm"),
            )
            OutlinedTextField(
                value = refId,
                onValueChange = { refId = it },
                label = { Text("ট্রানজেকশন আইডি (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BengaliDatePickerField(
                label = "তারিখ",
                dateMillis = occurredAtMillis,
                onDateSelected = { occurredAtMillis = it },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("নোট") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (localError != null) Text(localError!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    val rechP = MoneyFormatter.parseTakaToPaisa(rechargeAmount)
                    val recvP = MoneyFormatter.parseTakaToPaisa(receivedAmount)
                    val commP = if (manualComm.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(manualComm)
                    when {
                        customerName.isBlank() || mobileNumber.isBlank() -> localError = "গ্রাহকের নাম ও মোবাইল নম্বর দিন।"
                        rechP == null || rechP <= 0L || recvP == null || recvP <= 0L || commP == null ->
                            localError = "সঠিক টাকার পরিমাণ লিখুন।"
                        else -> onSubmit(
                            NewTxRequest(
                                txType = TxType.MOBILE_RECHARGE,
                                customerName = customerName,
                                customerPhone = mobileNumber,
                                operator = operator,
                                receivedAccountId = receivedAccountId,
                                receivedAmountPaisa = recvP,
                                paidAccountId = rechargeAccountId,
                                paidAmountPaisa = rechP,
                                manualCommissionPaisa = commP,
                                referenceTxId = refId,
                                notes = notes.ifBlank { "$operator রিচার্জ ($mobileNumber)" },
                                occurredAtMillis = occurredAtMillis,
                            )
                        ) {
                            customerName = ""; mobileNumber = ""; rechargeAmount = ""; receivedAmount = ""; manualComm = ""; refId = ""; notes = ""; occurredAtMillis = System.currentTimeMillis()
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("rech_submit_btn"),
            ) {
                Text(if (isOwner) "রিচার্জ এন্ট্রি সংরক্ষণ করুন" else "রিচার্জ জমা দিন (Pending)")
            }
        }
    }
}

@Composable
private fun BillPaymentForm(
    accounts: List<FinancialAccount>,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmit: (NewTxRequest, onClear: () -> Unit) -> Unit,
) {
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var billType by remember { mutableStateOf(BillType.ELECTRICITY) }
    var recvAccountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var recvAmount by remember { mutableStateOf("") }
    var paidAccountId by remember { mutableStateOf(BusinessConstants.ACC_BKASH_1) }
    var paidAmount by remember { mutableStateOf("") }
    var manualComm by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var refId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("বিদ্যুৎ ও ওয়াইফাই বিল ব্যবস্থাপনা", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SimpleDropdown(
                label = "বিলের ধরন",
                selectedLabel = billType.labelBn,
                items = BillType.entries.map { it to it.labelBn },
                onSelected = { billType = it },
            )
            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it; localError = null },
                label = { Text("গ্রাহকের নাম *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("মোবাইল / মিটার / বিল নম্বর (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AccountDropdown("টাকা গ্রহণের অ্যাকাউন্ট", recvAccountId, accounts, { recvAccountId = it })
            OutlinedTextField(
                value = recvAmount,
                onValueChange = { recvAmount = it; localError = null },
                label = { Text("গ্রাহকের কাছ থেকে প্রাপ্ত টাকা (৳) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AccountDropdown("বিল পরিশোধের অ্যাকাউন্ট", paidAccountId, accounts, { paidAccountId = it })
            OutlinedTextField(
                value = paidAmount,
                onValueChange = { paidAmount = it; localError = null },
                label = { Text("বিল পরিশোধকৃত টাকা (৳) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = manualComm,
                onValueChange = { manualComm = it; localError = null },
                label = { Text("কমিশন আয় (৳) — ঐচ্ছিক, ম্যানুয়ালি লিখুন") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = refId,
                onValueChange = { refId = it },
                label = { Text("ট্রানজেকশন / বিল আইডি (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BengaliDatePickerField(
                label = "তারিখ",
                dateMillis = occurredAtMillis,
                onDateSelected = { occurredAtMillis = it },
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("নোট") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (localError != null) Text(localError!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    val rP = MoneyFormatter.parseTakaToPaisa(recvAmount)
                    val pP = MoneyFormatter.parseTakaToPaisa(paidAmount)
                    val cP = if (manualComm.isBlank()) 0L else MoneyFormatter.parseTakaToPaisa(manualComm)
                    when {
                        customerName.isBlank() -> localError = "গ্রাহকের নাম লিখুন।"
                        rP == null || rP <= 0L || pP == null || pP <= 0L || cP == null ->
                            localError = "সঠিক টাকার পরিমাণ লিখুন।"
                        else -> onSubmit(
                            NewTxRequest(
                                txType = TxType.BILL_PAYMENT,
                                customerName = customerName,
                                customerPhone = customerPhone,
                                billType = billType.name,
                                receivedAccountId = recvAccountId,
                                receivedAmountPaisa = rP,
                                paidAccountId = paidAccountId,
                                paidAmountPaisa = pP,
                                manualCommissionPaisa = cP,
                                referenceTxId = refId,
                                notes = notes.ifBlank { billType.labelBn },
                                occurredAtMillis = occurredAtMillis,
                            )
                        ) {
                            customerName = ""; customerPhone = ""; recvAmount = ""; paidAmount = ""; manualComm = ""; refId = ""; notes = ""; occurredAtMillis = System.currentTimeMillis()
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (isOwner) "বিল এন্ট্রি সংরক্ষণ করুন" else "বিল এন্ট্রি জমা দিন (Pending)")
            }
        }
    }
}

@Composable
private fun ShopExpenseForm(
    accounts: List<FinancialAccount>,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmit: (NewTxRequest, onClear: () -> Unit) -> Unit,
) {
    var category by remember { mutableStateOf(ExpenseCategory.SHOP_RENT) }
    var paidAccountId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var amount by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var receiptRef by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    ElevatedCard(modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("দোকানের খরচ ব্যবস্থাপনা", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SimpleDropdown(
                label = "খরচের খাত",
                selectedLabel = category.labelBn,
                items = ExpenseCategory.entries.map { it to it.labelBn },
                onSelected = { category = it },
            )
            AccountDropdown("পরিশোধের অ্যাকাউন্ট", paidAccountId, accounts, { paidAccountId = it })
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; localError = null },
                label = { Text("খরচের পরিমাণ (৳) *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = receiptRef,
                onValueChange = { receiptRef = it },
                label = { Text("রসিদ / রেফারেন্স নম্বর (ঐচ্ছিক)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            BengaliDatePickerField(
                label = "তারিখ",
                dateMillis = occurredAtMillis,
                onDateSelected = { occurredAtMillis = it },
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; localError = null },
                label = { Text("খরচের বিবরণ *") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (localError != null) Text(localError!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)

            Button(
                onClick = {
                    val amtP = MoneyFormatter.parseTakaToPaisa(amount)
                    when {
                        amtP == null || amtP <= 0L -> localError = "সঠিক খরচের পরিমাণ লিখুন।"
                        description.isBlank() -> localError = "খরচের বিবরণ লিখুন।"
                        else -> onSubmit(
                            NewTxRequest(
                                txType = TxType.SHOP_EXPENSE,
                                expenseCategory = category.name,
                                customerName = category.labelBn,
                                paidAccountId = paidAccountId,
                                paidAmountPaisa = amtP,
                                referenceTxId = receiptRef,
                                notes = description,
                                occurredAtMillis = occurredAtMillis,
                            )
                        ) {
                            amount = ""; receiptRef = ""; description = ""; occurredAtMillis = System.currentTimeMillis()
                        }
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(if (isOwner) "খরচ সংরক্ষণ করুন" else "খরচ জমা দিন (Pending)")
            }
        }
    }
}

@Composable
private fun PendingQueueList(
    transactions: List<BusinessTransaction>,
    isOwner: Boolean,
    isWorking: Boolean,
    onApprove: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var removingTxId by remember { mutableStateOf<String?>(null) }

    removingTxId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { removingTxId = null },
            title = { Text("অপেক্ষমাণ এন্ট্রি রিমুভ করুন") },
            text = {
                Text("এই এন্ট্রিটি ভুল হলে তালিকা থেকে সম্পূর্ণ রিমুভ (মুছে ফেলা) হবে এবং মূল ব্যালেন্সে যোগ হবে না।")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemove(targetId)
                        removingTxId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossCrimson),
                    modifier = Modifier.testTag("confirm_remove_tx_btn"),
                ) {
                    Text("নিশ্চিত, রিমুভ করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { removingTxId = null }) { Text("থাকুন") }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "অপেক্ষমাণ অনুমোদন (Pending Approvals)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isOwner) {
                            "সাধারণ মেম্বারদের জমা দেওয়া কাজ ও এন্ট্রি অনুমোদন করলে মূল ব্যালেন্সে যোগ হবে, অথবা ভুল হলে রিমুভ করুন।"
                        } else {
                            "আপনার জমা দেওয়া কাজগুলো সুপার অ্যাডমিনের অনুমোদনের আগ পর্যন্ত অপেক্ষমাণ থাকে এবং মূল ব্যালেন্সে যোগ হয় না।"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (transactions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "এই মুহূর্তে কোনো অপেক্ষমাণ (Pending) এন্ট্রি নেই।",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            items(transactions, key = { it.id }) { tx ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(TxType.fromRaw(tx.txType).labelBn, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            StatusBadge(tx.status)
                        }
                        Text("জমা দিয়েছেন: ${tx.createdByName} • ${MoneyFormatter.formatDateTime(tx.occurredAtMillis)}", style = MaterialTheme.typography.bodySmall)
                        if (!tx.customerName.isNullOrBlank()) {
                            Text("বিবরণ/নাম: ${tx.customerName}", style = MaterialTheme.typography.bodyMedium)
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
                            Text("কমিশন: ${MoneyFormatter.formatTaka(tx.manualCommissionPaisa)}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!tx.notes.isNullOrBlank()) {
                            Text("নোট: ${tx.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (isOwner) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = { onApprove(tx.id) },
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f).testTag("approve_tx_${tx.id}"),
                                ) {
                                    Text("অনুমোদন (Approve)")
                                }
                                OutlinedButton(
                                    onClick = { removingTxId = tx.id },
                                    enabled = !isWorking,
                                    modifier = Modifier.weight(1f).testTag("remove_tx_${tx.id}"),
                                ) {
                                    Text("রিমুভ (Remove)", color = LossCrimson)
                                }
                            }
                        } else {
                            Text(
                                "সুপার অ্যাডমিনের অনুমোদনের অপেক্ষায় রয়েছে (মূল ব্যালেন্সে এখনো যোগ হয়নি)।",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
