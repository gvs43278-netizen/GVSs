package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.AccountGroup
import com.example.domain.BusinessConstants
import com.example.domain.FinancialAccount
import com.example.domain.MoneyFormatter
import com.example.domain.ProfitLossSummary
import com.example.domain.TxStatus
import com.example.ui.theme.BankIndigo
import com.example.ui.theme.BkashMagenta
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.NagadOrange
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.RocketPurple
import com.example.ui.theme.UpayBlue

fun accountAccentColor(accountId: String): Color = when {
    accountId == BusinessConstants.ACC_CASH -> ProfitGreen
    accountId.startsWith("bkash_") -> BkashMagenta
    accountId == BusinessConstants.ACC_NAGAD -> NagadOrange
    accountId == BusinessConstants.ACC_ROCKET -> RocketPurple
    accountId == BusinessConstants.ACC_UPAY -> UpayBlue
    accountId == BusinessConstants.ACC_UCB_BANK -> BankIndigo
    else -> EmeraldPrimary
}

fun accountIcon(group: String): ImageVector = when (AccountGroup.fromRaw(group)) {
    AccountGroup.CASH -> Icons.Filled.Payments
    AccountGroup.BKASH, AccountGroup.MFS -> Icons.Filled.AccountBalanceWallet
    AccountGroup.BANK -> Icons.Filled.AccountBalance
    AccountGroup.RECHARGE -> Icons.Filled.PhoneAndroid
}

@Composable
fun SummaryKpiCard(
    title: String,
    valueText: String,
    accentColor: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CompactAccountPanelRow(
    account: FinancialAccount,
    isOwner: Boolean,
    onPlusAdjust: (FinancialAccount) -> Unit,
    onMinusAdjust: (FinancialAccount) -> Unit,
    onViewHistory: (FinancialAccount) -> Unit,
) {
    val accent = accountAccentColor(account.accountId)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = accountIcon(account.group),
                        contentDescription = account.name,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = MoneyFormatter.formatTaka(account.balancePaisa),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (account.balancePaisa < 0L) LossCrimson else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isOwner) {
                    IconButton(
                        onClick = { onPlusAdjust(account) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("acc_plus_${account.accountId}"),
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "${account.name} প্লাস সমন্বয়",
                            tint = ProfitGreen,
                        )
                    }
                    IconButton(
                        onClick = { onMinusAdjust(account) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("acc_minus_${account.accountId}"),
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = "${account.name} মাইনাস সমন্বয়",
                            tint = LossCrimson,
                        )
                    }
                }
                IconButton(
                    onClick = { onViewHistory(account) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("acc_history_${account.accountId}"),
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "${account.name} ইতিহাস দেখুন",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun ProfitLossBarChart(
    summary: ProfitLossSummary,
    modifier: Modifier = Modifier,
) {
    val income = summary.commissionIncomePaisa.coerceAtLeast(0L)
    val shopExp = summary.shopExpensesPaisa.coerceAtLeast(0L)
    val partnerExp = summary.partnerCommissionExpensesPaisa.coerceAtLeast(0L)
    val maxVal = maxOf(income, shopExp, partnerExp, 100L).toFloat()

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "আয় বনাম খরচ বিশ্লেষণ (চার্ট)",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val barHeight = 22.dp.toPx()
                val gap = 14.dp.toPx()
                val items = listOf(
                    income to ProfitGreen,
                    shopExp to LossCrimson,
                    partnerExp to PendingAmber,
                )
                items.forEachIndexed { idx, (value, color) ->
                    val y = idx * (barHeight + gap)
                    drawRoundRect(
                        color = color.copy(alpha = 0.14f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, barHeight),
                        cornerRadius = CornerRadius(10f, 10f),
                    )
                    val w = if (value > 0) ((value / maxVal) * size.width).coerceAtLeast(16f) else 0f
                    if (w > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.8f), color)),
                            topLeft = Offset(0f, y),
                            size = Size(w, barHeight),
                            cornerRadius = CornerRadius(10f, 10f),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChartLegendChip("কমিশন আয়", MoneyFormatter.formatTaka(income), ProfitGreen)
                ChartLegendChip("দোকান খরচ", MoneyFormatter.formatTaka(shopExp), LossCrimson)
                ChartLegendChip("পার্টনার খরচ", MoneyFormatter.formatTaka(partnerExp), PendingAmber)
            }
        }
    }
}

@Composable
private fun ChartLegendChip(label: String, amount: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(amount, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusBadge(status: String) {
    val st = TxStatus.fromRaw(status)
    val (bg, fg) = when (st) {
        TxStatus.PENDING -> PendingAmber.copy(alpha = 0.15f) to PendingAmber
        TxStatus.APPROVED -> ProfitGreen.copy(alpha = 0.15f) to ProfitGreen
        TxStatus.REJECTED -> LossCrimson.copy(alpha = 0.15f) to LossCrimson
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Text(
            text = st.labelBn,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDropdown(
    label: String,
    selectedAccountId: String,
    accounts: List<FinancialAccount>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "account_dropdown",
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = accounts.firstOrNull { it.accountId == selectedAccountId }
    val displayText = if (selected != null) {
        "${selected.name} (${MoneyFormatter.formatTaka(selected.balancePaisa)})"
    } else {
        BusinessConstants.accountDisplayName(selectedAccountId)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .testTag(testTag),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            accounts.forEach { acc ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(acc.name)
                            Text(
                                MoneyFormatter.formatTaka(acc.balancePaisa),
                                fontWeight = FontWeight.SemiBold,
                                color = if (acc.balancePaisa < 0) LossCrimson else MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        onSelected(acc.accountId)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SimpleDropdown(
    label: String,
    selectedLabel: String,
    items: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            items.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun BalanceAdjustmentDialog(
    account: FinancialAccount,
    isPlus: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amountPaisa: Long, notes: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${account.name} — ${if (isPlus) "প্লাস (+) সমন্বয়" else "মাইনাস (-) সমন্বয়"}",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "বর্তমান ব্যালেন্স: ${MoneyFormatter.formatTaka(account.balancePaisa)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMsg = null
                    },
                    label = { Text("টাকার পরিমাণ (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("সমন্বয়ের কারণ / নোট") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paisa = MoneyFormatter.parseTakaToPaisa(amountText)
                    if (paisa == null || paisa <= 0L) {
                        errorMsg = "সঠিক টাকার পরিমাণ লিখুন।"
                    } else {
                        onConfirm(paisa, notesText)
                    }
                }
            ) {
                Text("নিশ্চিত করুন")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BengaliDatePickerField(
    label: String,
    dateMillis: Long,
    onDateSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "date_picker_field",
) {
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedUtcMidnight = pickerState.selectedDateMillis
                        if (selectedUtcMidnight != null) {
                            val srcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = selectedUtcMidnight
                            }
                            val origCal = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
                            val mergedCal = java.util.Calendar.getInstance().apply {
                                set(
                                    srcCal.get(java.util.Calendar.YEAR),
                                    srcCal.get(java.util.Calendar.MONTH),
                                    srcCal.get(java.util.Calendar.DAY_OF_MONTH),
                                    origCal.get(java.util.Calendar.HOUR_OF_DAY),
                                    origCal.get(java.util.Calendar.MINUTE),
                                    0,
                                )
                            }
                            onDateSelected(mergedCal.timeInMillis)
                        }
                        showPicker = false
                    }
                ) {
                    Text("নির্বাচন করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("বাতিল")
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Text("$label: ${MoneyFormatter.formatDate(dateMillis)}")
            }
            Text("পরিবর্তন", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
