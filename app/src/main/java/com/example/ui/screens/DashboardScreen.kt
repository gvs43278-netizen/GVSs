package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.NewTxRequest
import com.example.domain.AccountingEngine
import com.example.domain.BusinessConstants
import com.example.domain.FinancialAccount
import com.example.domain.MoneyFormatter
import com.example.domain.ReportPeriod
import com.example.domain.TxStatus
import com.example.domain.TxType
import com.example.ui.BusinessSnapshotData
import com.example.ui.components.BalanceAdjustmentDialog
import com.example.ui.components.CompactAccountPanelRow
import com.example.ui.components.ProfitLossBarChart
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryKpiCard
import com.example.ui.theme.BankIndigo
import com.example.ui.theme.BkashMagenta
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.NagadOrange
import com.example.ui.theme.PendingAmber
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.RocketPurple
import com.example.ui.theme.UpayBlue

@Composable
fun DashboardScreen(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    onNavigateToNewTx: () -> Unit,
    onNavigateToPending: () -> Unit,
    onViewAccountHistory: (String) -> Unit,
    onSubmitAdjustment: (NewTxRequest) -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToDueBreakdown: () -> Unit = {},
) {
    var adjustingTarget by remember { mutableStateOf<Pair<FinancialAccount, Boolean>?>(null) }

    adjustingTarget?.let { (account, isPlus) ->
        BalanceAdjustmentDialog(
            account = account,
            isPlus = isPlus,
            onDismiss = { adjustingTarget = null },
            onConfirm = { paisa, notes ->
                onSubmitAdjustment(
                    NewTxRequest(
                        txType = TxType.BALANCE_ADJUSTMENT,
                        receivedAccountId = if (isPlus) account.accountId else null,
                        receivedAmountPaisa = if (isPlus) paisa else 0L,
                        paidAccountId = if (!isPlus) account.accountId else null,
                        paidAmountPaisa = if (!isPlus) paisa else 0L,
                        adjustmentSign = if (isPlus) 1L else -1L,
                        notes = notes.ifBlank { if (isPlus) "প্লাস সমন্বয়" else "মাইনাস সমন্বয়" },
                    )
                )
                adjustingTarget = null
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 840.dp
        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1.45f),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        DashboardMainSummaryContent(
                            data = data,
                            isOwner = isOwner,
                            onNavigateToNewTx = onNavigateToNewTx,
                            onNavigateToPending = onNavigateToPending,
                            onNavigateToHistory = onNavigateToHistory,
                            onNavigateToDueBreakdown = onNavigateToDueBreakdown,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        AccountCompactPanelHeader(data)
                    }
                    items(data.accounts, key = { it.accountId }) { acc ->
                        CompactAccountPanelRow(
                            account = acc,
                            isOwner = isOwner,
                            onPlusAdjust = { adjustingTarget = it to true },
                            onMinusAdjust = { adjustingTarget = it to false },
                            onViewHistory = { onViewAccountHistory(it.accountId) },
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    DashboardMainSummaryContent(
                        data = data,
                        isOwner = isOwner,
                        onNavigateToNewTx = onNavigateToNewTx,
                        onNavigateToPending = onNavigateToPending,
                        onNavigateToHistory = onNavigateToHistory,
                        onNavigateToDueBreakdown = onNavigateToDueBreakdown,
                    )
                }
                item {
                    AccountCompactPanelHeader(data)
                }
                items(data.accounts, key = { it.accountId }) { acc ->
                    CompactAccountPanelRow(
                        account = acc,
                        isOwner = isOwner,
                        onPlusAdjust = { adjustingTarget = it to true },
                        onMinusAdjust = { adjustingTarget = it to false },
                        onViewHistory = { onViewAccountHistory(it.accountId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCompactPanelHeader(data: BusinessSnapshotData) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("১১টি পৃথক অ্যাকাউন্ট", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "সর্বমোট ব্যালেন্স: ${MoneyFormatter.formatTaka(data.metrics.grandTotalBalancePaisa)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "সম্মিলিত বিকাশ: ${MoneyFormatter.formatTaka(data.metrics.totalBkashBalancePaisa)}",
                style = MaterialTheme.typography.labelMedium,
                color = BkashMagenta,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DashboardMainSummaryContent(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    onNavigateToNewTx: () -> Unit,
    onNavigateToPending: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDueBreakdown: () -> Unit,
) {
    val m = data.metrics
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.ALL) }
    val pl = remember(data.ledger, selectedPeriod, m.profitLossAll) {
        if (selectedPeriod == ReportPeriod.ALL) m.profitLossAll
        else AccountingEngine.calculateProfitLoss(data.ledger, selectedPeriod)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onNavigateToNewTx,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("dashboard_new_tx_btn"),
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("নতুন লেনদেন")
            }
            FilledTonalButton(
                onClick = onNavigateToPending,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("dashboard_pending_btn"),
            ) {
                Icon(Icons.Filled.PendingActions, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("অনুমোদন (${m.pendingCount})")
            }
        }

        // Capital, Cash in Hand, and Clickable Total Due Breakdown Card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("capital_cash_due_card"),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "মোট মূলধন (অবশিষ্ট মূলধন)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = MoneyFormatter.formatTaka(m.remainingCapitalPaisa),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                )
                Text(
                    text = "মূলধন জমা: ${MoneyFormatter.formatTaka(m.totalCapitalPaisa)} − সামাদ/জহির/আরিফুলকে দেওয়া: ${MoneyFormatter.formatTaka(m.partnerGivenTotalPaisa)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("cash_in_hand_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ক্যাশ আছে (Cash in Hand)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = MoneyFormatter.formatTaka(m.cashBalancePaisa),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ProfitGreen,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onNavigateToDueBreakdown)
                        .testTag("total_due_clickable"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "বাকি আছে (Total Due)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (m.dueItems.isEmpty()) {
                                    "কার কার কাছে বাকি আছে দেখতে ট্যাপ করুন"
                                } else {
                                    "${m.dueItems.size} জনের কাছে বাকি — বিস্তারিত দেখতে ট্যাপ করুন"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = MoneyFormatter.formatTaka(m.totalDuePaisa),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (m.totalDuePaisa > 0L) LossCrimson else ProfitGreen,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "বাকি টাকার তালিকা দেখুন",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // Today's Activity Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("আজকের সারসংক্ষেপ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TodayStat("আজকের লেনদেন", "${m.todayTransactionsCount}টি")
                    TodayStat("আজকের আয়", MoneyFormatter.formatTaka(m.todayIncomePaisa), ProfitGreen)
                    TodayStat("আজকের খরচ", MoneyFormatter.formatTaka(m.todayExpensesPaisa), LossCrimson)
                    TodayStat("অপেক্ষমাণ", "${m.pendingCount}টি", PendingAmber)
                }
            }
        }

        // Balance Summary Grid (2 columns per row)
        Text("অ্যাকাউন্ট ভিত্তিক সারাংশ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        KpiPairRow(
            "মোট ক্যাশ ব্যালেন্স", MoneyFormatter.formatTaka(m.cashBalancePaisa), ProfitGreen,
            "মোট বিকাশ (৫টি মিলিয়ে)", MoneyFormatter.formatTaka(m.totalBkashBalancePaisa), BkashMagenta,
        )
        KpiPairRow(
            "Nagad ব্যালেন্স", MoneyFormatter.formatTaka(m.nagadBalancePaisa), NagadOrange,
            "Rocket ব্যালেন্স", MoneyFormatter.formatTaka(m.rocketBalancePaisa), RocketPurple,
        )
        KpiPairRow(
            "Upay ব্যালেন্স", MoneyFormatter.formatTaka(m.upayBalancePaisa), UpayBlue,
            "UCB Bank ব্যালেন্স", MoneyFormatter.formatTaka(m.ucbBankBalancePaisa), BankIndigo,
        )
        KpiPairRow(
            "Easy Recharge ব্যালেন্স", MoneyFormatter.formatTaka(m.easyRechargeBalancePaisa), EmeraldPrimary,
            "সর্বমোট ব্যালেন্স (১১টি)", MoneyFormatter.formatTaka(m.grandTotalBalancePaisa), EmeraldPrimary,
        )

        // Capital & P&L Grid
        Text("মূলধন ও লাভ-ক্ষতি সারাংশ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (!isOwner) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "সাধারণ মেম্বার হিসেবে আপনার জমা দেওয়া কাজগুলো 'অপেক্ষমাণ (Pending)' থাকে এবং সুপার অ্যাডমিন অনুমোদন করলে মূল হিসাবে যুক্ত হয়। সম্পূর্ণ ব্যবসার লাভ-ক্ষতি ও লেজার হিসাব শুধুমাত্র সুপার অ্যাডমিনের ড্যাশবোর্ডে প্রদর্শিত হয়।",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.labelBn) },
                    )
                }
            }
            KpiPairRow(
                "অবশিষ্ট মূলধন (মোট মূলধন)", MoneyFormatter.formatTaka(m.remainingCapitalPaisa), EmeraldPrimary,
                "কমিশন আয় (${selectedPeriod.labelBn})", MoneyFormatter.formatTaka(pl.commissionIncomePaisa), ProfitGreen,
            )
            KpiPairRow(
                "পার্টনার কমিশন ব্যয়", MoneyFormatter.formatTaka(pl.partnerCommissionExpensesPaisa), PendingAmber,
                "দোকানের মোট খরচ", MoneyFormatter.formatTaka(pl.shopExpensesPaisa), LossCrimson,
            )
            KpiPairRow(
                "মোট লাভ", MoneyFormatter.formatTaka(pl.totalProfitPaisa), ProfitGreen,
                "মোট ক্ষতি", MoneyFormatter.formatTaka(pl.totalLossPaisa), LossCrimson,
            )
            SummaryKpiCard(
                title = "নিট লাভ / ক্ষতি (${selectedPeriod.labelBn} = কমিশন আয় - দোকান খরচ - পার্টনার কমিশন)",
                valueText = MoneyFormatter.formatTaka(pl.netProfitLossPaisa, showPlusSign = true),
                accentColor = if (pl.netProfitLossPaisa >= 0L) ProfitGreen else LossCrimson,
                subtitle = "ঋণের সুদ আয়: ${MoneyFormatter.formatTaka(pl.interestIncomePaisa)} | সুদ ব্যয়: ${MoneyFormatter.formatTaka(pl.interestExpensePaisa)} (পৃথক)",
                modifier = Modifier.fillMaxWidth(),
            )

            // Bar Chart
            ProfitLossBarChart(summary = pl)
        }

        // Recent Transactions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("সাম্প্রতিক লেনদেন", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (data.transactions.isNotEmpty()) {
                Text(
                    text = "সব দেখুন ->",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onNavigateToHistory),
                )
            }
        }
        val recent = data.transactions.take(6)
        if (recent.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("এখনো কোনো লেনদেন যোগ করা হয়নি। 'নতুন লেনদেন' বাটনে চাপ দিয়ে শুরু করুন।")
                }
            }
        } else {
            recent.forEach { tx ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToHistory),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                TxType.fromRaw(tx.txType).labelBn,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            StatusBadge(tx.status)
                        }
                        if (!tx.customerName.isNullOrBlank()) {
                            Text("গ্রাহক/ব্যক্তি: ${tx.customerName}", style = MaterialTheme.typography.bodySmall)
                        }
                        val amtLine = buildString {
                            if (tx.receivedAmountPaisa > 0L) {
                                append("গ্রহণ: ${BusinessConstants.accountDisplayName(tx.receivedAccountId)} ${MoneyFormatter.formatTaka(tx.receivedAmountPaisa)}")
                            }
                            if (tx.paidAmountPaisa > 0L) {
                                if (isNotEmpty()) append(" | ")
                                append("প্রদান: ${BusinessConstants.accountDisplayName(tx.paidAccountId)} ${MoneyFormatter.formatTaka(tx.paidAmountPaisa)}")
                            }
                        }
                        if (amtLine.isNotBlank()) {
                            Text(amtLine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            "${MoneyFormatter.formatDateTime(tx.occurredAtMillis)} • তৈরি: ${tx.createdByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayStat(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun KpiPairRow(
    t1: String, v1: String, c1: androidx.compose.ui.graphics.Color,
    t2: String, v2: String, c2: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SummaryKpiCard(title = t1, valueText = v1, accentColor = c1, modifier = Modifier.weight(1f))
        SummaryKpiCard(title = t2, valueText = v2, accentColor = c2, modifier = Modifier.weight(1f))
    }
}
