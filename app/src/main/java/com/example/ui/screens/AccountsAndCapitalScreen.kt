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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.domain.BusinessConstants
import com.example.domain.FinancialAccount
import com.example.domain.MoneyFormatter
import com.example.domain.TxType
import com.example.ui.BusinessSnapshotData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import com.example.ui.components.AccountDropdown
import com.example.ui.components.BalanceAdjustmentDialog
import com.example.ui.components.BengaliDatePickerField
import com.example.ui.components.CompactAccountPanelRow
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BkashMagenta
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.ProfitGreen

@Composable
fun AccountsAndCapitalScreen(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    isWorking: Boolean,
    onSubmitTx: (NewTxRequest, onClear: () -> Unit) -> Unit,
    onToggleAllowNegative: (String, Boolean) -> Unit,
    onViewAccountHistory: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var adjustingTarget by remember { mutableStateOf<Pair<FinancialAccount, Boolean>?>(null) }
    var showCapitalDialog by remember { mutableStateOf<Boolean?>(null) } // true=add, false=withdraw

    adjustingTarget?.let { (account, isPlus) ->
        BalanceAdjustmentDialog(
            account = account,
            isPlus = isPlus,
            onDismiss = { adjustingTarget = null },
            onConfirm = { paisa, notes ->
                onSubmitTx(
                    NewTxRequest(
                        txType = TxType.BALANCE_ADJUSTMENT,
                        receivedAccountId = if (isPlus) account.accountId else null,
                        receivedAmountPaisa = if (isPlus) paisa else 0L,
                        paidAccountId = if (!isPlus) account.accountId else null,
                        paidAmountPaisa = if (!isPlus) paisa else 0L,
                        adjustmentSign = if (isPlus) 1L else -1L,
                        notes = notes.ifBlank { if (isPlus) "প্লাস সমন্বয়" else "মাইনাস সমন্বয়" },
                    )
                ) {}
                adjustingTarget = null
            },
        )
    }

    showCapitalDialog?.let { isAdd ->
        BalanceOperationDialog(
            isAdd = isAdd,
            accounts = data.accounts,
            isWorking = isWorking,
            onDismiss = { showCapitalDialog = null },
            onConfirm = { accId, txType, adjustmentSign, amountPaisa, occurredAtMillis, sourceOrReason, notes ->
                val fullNotes = buildString {
                    if (sourceOrReason.isNotBlank()) append(sourceOrReason.trim())
                    if (notes.isNotBlank()) {
                        if (isNotEmpty()) append(" — ")
                        append(notes.trim())
                    }
                    if (isEmpty()) {
                        append(
                            when (txType) {
                                TxType.CAPITAL_ADD -> "মূলধন যোগ"
                                TxType.CAPITAL_WITHDRAW -> "মূলধন উত্তোলন"
                                else -> if (isAdd) "ব্যালেন্স যোগ" else "ব্যালেন্স উত্তোলন"
                            }
                        )
                    }
                }
                onSubmitTx(
                    NewTxRequest(
                        txType = txType,
                        receivedAccountId = if (isAdd) accId else null,
                        receivedAmountPaisa = if (isAdd) amountPaisa else 0L,
                        paidAccountId = if (!isAdd) accId else null,
                        paidAmountPaisa = if (!isAdd) amountPaisa else 0L,
                        adjustmentSign = adjustmentSign,
                        occurredAtMillis = occurredAtMillis,
                        notes = fullNotes,
                    )
                ) {
                    showCapitalDialog = null
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tab == 0,
                onClick = { tab = 0 },
                label = { Text("১১টি অ্যাকাউন্ট") },
                modifier = Modifier.testTag("tab_11_accounts"),
            )
            FilterChip(
                selected = tab == 1,
                onClick = { tab = 1 },
                label = { Text("মূলধন ব্যবস্থাপনা") },
                modifier = Modifier.testTag("tab_capital"),
            )
        }
        Spacer(Modifier.height(10.dp))

        if (tab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "সর্বমোট অ্যাকাউন্ট ব্যালেন্স: ${MoneyFormatter.formatTaka(data.metrics.grandTotalBalancePaisa)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "সম্মিলিত বিকাশ (bKash-1 থেকে bKash-5): ${MoneyFormatter.formatTaka(data.metrics.totalBkashBalancePaisa)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = BkashMagenta,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "প্রতিটি অ্যাকাউন্ট সম্পূর্ণ পৃথকভাবে সংরক্ষিত। গোপন পূর্ণ নম্বর কখনোই দেখানো হয় না।",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (isOwner) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { showCapitalDialog = true },
                                        modifier = Modifier.weight(1f).testTag("btn_balance_add"),
                                    ) {
                                        Text("+ Balance Add")
                                    }
                                    OutlinedButton(
                                        onClick = { showCapitalDialog = false },
                                        modifier = Modifier.weight(1f).testTag("btn_balance_withdraw"),
                                    ) {
                                        Text("- Balance Withdraw")
                                    }
                                }
                            }
                        }
                    }
                }
                items(data.accounts, key = { it.accountId }) { acc ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactAccountPanelRow(
                                account = acc,
                                isOwner = isOwner,
                                onPlusAdjust = { adjustingTarget = it to true },
                                onMinusAdjust = { adjustingTarget = it to false },
                                onViewHistory = { onViewAccountHistory(it.accountId) },
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "মূলধন: ${MoneyFormatter.formatTaka(acc.capitalPaisa)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (isOwner) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("ঋণাত্মক ব্যালেন্স", style = MaterialTheme.typography.labelSmall)
                                        Switch(
                                            checked = acc.allowNegativeBalance,
                                            onCheckedChange = { onToggleAllowNegative(acc.accountId, it) },
                                            modifier = Modifier.testTag("neg_switch_${acc.accountId}"),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val capitalTxs = data.transactions.filter {
                it.txType == TxType.CAPITAL_ADD.name || it.txType == TxType.CAPITAL_WITHDRAW.name
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "মোট মূলধন: ${MoneyFormatter.formatTaka(data.metrics.totalCapitalPaisa)}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "মূলধন যোগ বা উত্তোলন কখনোই ব্যবসার আয়, খরচ বা লাভ হিসেবে গণ্য হয় না।",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (isOwner) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { showCapitalDialog = true },
                                        modifier = Modifier.weight(1f).testTag("btn_add_capital"),
                                    ) {
                                        Text("মূলধন যোগ")
                                    }
                                    OutlinedButton(
                                        onClick = { showCapitalDialog = false },
                                        modifier = Modifier.weight(1f).testTag("btn_withdraw_capital"),
                                    ) {
                                        Text("মূলধন উত্তোলন")
                                    }
                                }
                            } else {
                                Text(
                                    "শুধুমাত্র সুপার অ্যাডমিন মূলধন যোগ বা উত্তোলন করতে পারবেন।",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LossCrimson,
                                )
                            }
                        }
                    }
                }

                item {
                    Text("অ্যাকাউন্ট-ভিত্তিক মূলধন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(data.accounts, key = { "cap_${it.accountId}" }) { acc ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(acc.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                MoneyFormatter.formatTaka(acc.capitalPaisa),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                item {
                    Text("মূলধন ইতিহাস (${capitalTxs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(capitalTxs, key = { "captx_${it.id}" }) { tx ->
                    val isAdd = tx.txType == TxType.CAPITAL_ADD.name
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    if (isAdd) "মূলধন যোগ" else "মূলধন উত্তোলন",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdd) ProfitGreen else LossCrimson,
                                )
                                StatusBadge(tx.status)
                            }
                            val accName = BusinessConstants.accountDisplayName(
                                if (isAdd) tx.receivedAccountId else tx.paidAccountId
                            )
                            val amt = if (isAdd) tx.receivedAmountPaisa else tx.paidAmountPaisa
                            Text("$accName: ${MoneyFormatter.formatTaka(amt)}", fontWeight = FontWeight.SemiBold)
                            if (!tx.notes.isNullOrBlank()) Text(tx.notes, style = MaterialTheme.typography.bodySmall)
                            Text(MoneyFormatter.formatDateTime(tx.occurredAtMillis), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

private enum class BalanceAddCategory(val labelBn: String) {
    CAPITAL("মূলধন যোগ (Capital)"),
    EXTERNAL_TRANSFER("অন্য উৎস থেকে স্থানান্তর"),
    OTHER_ADJUSTMENT("অন্যান্য সমন্বয় (+)"),
}

private enum class BalanceWithdrawCategory(val labelBn: String) {
    CAPITAL_WITHDRAW("মূলধন উত্তোলন"),
    OTHER_WITHDRAW("অন্যান্য উত্তোলন / সমন্বয় (-)"),
}

@Composable
private fun BalanceOperationDialog(
    isAdd: Boolean,
    accounts: List<FinancialAccount>,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        accountId: String,
        txType: TxType,
        adjustmentSign: Long,
        amountPaisa: Long,
        occurredAtMillis: Long,
        sourceOrReason: String,
        notes: String,
    ) -> Unit,
) {
    var accId by remember { mutableStateOf(BusinessConstants.ACC_CASH) }
    var addCategory by remember { mutableStateOf(BalanceAddCategory.CAPITAL) }
    var withdrawCategory by remember { mutableStateOf(BalanceWithdrawCategory.CAPITAL_WITHDRAW) }
    var amount by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var sourceOrReason by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAdd) "Balance Add (ব্যালেন্স যোগ)" else "Balance Withdraw (ব্যালেন্স উত্তোলন)") },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AccountDropdown("অ্যাকাউন্ট নির্বাচন করুন", accId, accounts, { accId = it })

                Text(
                    text = if (isAdd) "টাকার ধরন নির্বাচন করুন *" else "উত্তোলনের ধরন নির্বাচন করুন *",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isAdd) {
                        BalanceAddCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = addCategory == cat,
                                onClick = { addCategory = cat },
                                label = { Text(cat.labelBn) },
                            )
                        }
                    } else {
                        BalanceWithdrawCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = withdrawCategory == cat,
                                onClick = { withdrawCategory = cat },
                                label = { Text(cat.labelBn) },
                            )
                        }
                    }
                }

                Text(
                    text = if (isAdd) {
                        when (addCategory) {
                            BalanceAddCategory.CAPITAL -> "এটি মূলধন হিসেবে যোগ হবে (লাভ-ক্ষতিতে গণনা হবে না, এবং পুনরায় মূলধনে যোগ হবে না)।"
                            BalanceAddCategory.EXTERNAL_TRANSFER -> "অন্য উৎস থেকে স্থানান্তর হিসেবে শুধু অ্যাকাউন্ট ব্যালেন্স বাড়বে (দুইবার মূলধন বা লাভ হিসেবে গণনা হবে না)।"
                            BalanceAddCategory.OTHER_ADJUSTMENT -> "সমন্বয় হিসেবে শুধু অ্যাকাউন্ট ব্যালেন্স বাড়বে (মূলধন বা লাভ হিসেবে গণনা হবে না)।"
                        }
                    } else {
                        when (withdrawCategory) {
                            BalanceWithdrawCategory.CAPITAL_WITHDRAW -> "মূলধন উত্তোলন হিসেবে ব্যালেন্স ও মূলধন কমবে (ব্যবসার খরচ বা ক্ষতি হিসেবে গণনা হবে না)।"
                            BalanceWithdrawCategory.OTHER_WITHDRAW -> "ব্যালেন্স সমন্বয় (-) হিসেবে শুধু অ্যাকাউন্ট ব্যালেন্স কমবে (ব্যবসার খরচ বা ক্ষতি হিসেবে গণনা হবে না)।"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; err = null },
                    label = { Text("টাকার পরিমাণ (৳) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                BengaliDatePickerField(
                    label = "তারিখ",
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it },
                )
                OutlinedTextField(
                    value = sourceOrReason,
                    onValueChange = { sourceOrReason = it; err = null },
                    label = {
                        Text(
                            if (isAdd) "টাকার উৎস (যেমন: নিজস্ব মূলধন / ব্যাংক) *"
                            else "উত্তোলনের কারণ *"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("অতিরিক্ত বিবরণ / নোট") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = MoneyFormatter.parseTakaToPaisa(amount)
                    if (p == null || p <= 0L) {
                        err = "সঠিক টাকার পরিমাণ লিখুন।"
                        return@Button
                    }
                    if (sourceOrReason.isBlank()) {
                        err = if (isAdd) "টাকার উৎস লিখুন।" else "উত্তোলনের কারণ লিখুন।"
                        return@Button
                    }
                    val (txType, adjSign) = if (isAdd) {
                        when (addCategory) {
                            BalanceAddCategory.CAPITAL -> TxType.CAPITAL_ADD to 0L
                            BalanceAddCategory.EXTERNAL_TRANSFER,
                            BalanceAddCategory.OTHER_ADJUSTMENT -> TxType.BALANCE_ADJUSTMENT to 1L
                        }
                    } else {
                        when (withdrawCategory) {
                            BalanceWithdrawCategory.CAPITAL_WITHDRAW -> TxType.CAPITAL_WITHDRAW to 0L
                            BalanceWithdrawCategory.OTHER_WITHDRAW -> TxType.BALANCE_ADJUSTMENT to -1L
                        }
                    }
                    onConfirm(accId, txType, adjSign, p, dateMillis, sourceOrReason, notes)
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
