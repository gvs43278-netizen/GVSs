package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.BusinessConstants
import com.example.ui.screens.AccountsAndCapitalScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DueBreakdownScreen
import com.example.ui.screens.HistoryAndReportsScreen
import com.example.ui.screens.OwnerAdminScreen
import com.example.ui.screens.PartnersAndLoansScreen
import com.example.ui.screens.TransactionHubScreen
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.PendingAmber

enum class AppSection(val labelBn: String, val icon: ImageVector) {
    DASHBOARD("ড্যাশবোর্ড", Icons.Filled.Dashboard),
    TRANSACTIONS("লেনদেন", Icons.Filled.SwapHoriz),
    ACCOUNTS("অ্যাকাউন্ট", Icons.Filled.AccountBalance),
    PARTNERS_LOANS("পার্টনার ও ঋণ", Icons.Filled.Handshake),
    HISTORY_REPORTS("ইতিহাস ও রিপোর্ট", Icons.Filled.Assessment),
    ADMIN_SETTINGS("প্রশাসন ও ব্যাকআপ", Icons.Filled.AdminPanelSettings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessAppShell(
    viewModel: MainViewModel,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onSignOutConfirmed: () -> Unit,
) {
    val businessState by viewModel.businessState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    var currentSection by remember { mutableStateOf(AppSection.DASHBOARD) }
    var showDueBreakdown by remember { mutableStateOf(false) }
    var txHubInitialTab by remember { mutableIntStateOf(0) }
    var txHubNavNonce by remember { mutableIntStateOf(0) }
    var historyAccountFilter by remember { mutableStateOf<String?>(null) }
    var historyNavNonce by remember { mutableIntStateOf(0) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback.successMessage, feedback.errorMessage) {
        val msg = feedback.errorMessage ?: feedback.successMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    BackHandler(enabled = currentSection != AppSection.DASHBOARD || showDueBreakdown) {
        if (showDueBreakdown) {
            showDueBreakdown = false
        } else {
            currentSection = AppSection.DASHBOARD
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("সাইন-আউট নিশ্চিতকরণ") },
            text = { Text("আপনি কি অ্যাকাউন্ট থেকে সাইন-আউট করতে চান?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOutConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossCrimson),
                    modifier = Modifier.testTag("confirm_sign_out_btn"),
                ) {
                    Text("সাইন-আউট")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSignOutDialog = false }) { Text("থাকুন") }
            },
        )
    }

    val pendingCount = (businessState as? UiState.Success)?.data?.metrics?.pendingCount ?: 0

    val openTxHub: (Int) -> Unit = { tabIdx ->
        showDueBreakdown = false
        txHubInitialTab = tabIdx
        txHubNavNonce++
        currentSection = AppSection.TRANSACTIONS
    }

    val openAccountHistory: (String) -> Unit = { accId ->
        showDueBreakdown = false
        historyAccountFilter = accId
        historyNavNonce++
        currentSection = AppSection.HISTORY_REPORTS
    }

    val bottomSections = listOf(
        AppSection.DASHBOARD,
        AppSection.TRANSACTIONS,
        AppSection.ACCOUNTS,
        AppSection.PARTNERS_LOANS,
        AppSection.HISTORY_REPORTS,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = BusinessConstants.APP_NAME_BN,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (viewModel.isOwner) {
                                    EmeraldPrimary.copy(alpha = 0.14f)
                                } else {
                                    PendingAmber.copy(alpha = 0.16f)
                                },
                            ) {
                                Text(
                                    text = if (viewModel.isOwner) "সুপার অ্যাডমিন" else "সাধারণ মেম্বার",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (viewModel.isOwner) EmeraldPrimary else PendingAmber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { openTxHub(5) },
                            modifier = Modifier.testTag("top_pending_btn"),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge { Text("$pendingCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = "অপেক্ষমাণ লেনদেন",
                                )
                            }
                        }
                        IconButton(
                            onClick = { onToggleDarkMode(!isDarkMode) },
                            modifier = Modifier.testTag("top_theme_toggle_btn"),
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkMode) "লাইট মোড" else "ডার্ক মোড",
                            )
                        }
                        IconButton(
                            onClick = { currentSection = AppSection.ADMIN_SETTINGS },
                            modifier = Modifier.testTag("top_admin_btn"),
                        ) {
                            Icon(
                                imageVector = if (viewModel.isOwner) Icons.Filled.AdminPanelSettings else Icons.Filled.Info,
                                contentDescription = if (viewModel.isOwner) "প্রশাসন ও ব্যাকআপ" else "সিস্টেম তথ্য",
                            )
                        }
                        IconButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.testTag("top_sign_out_btn"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "সাইন-আউট",
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar {
                        bottomSections.forEach { section ->
                            val selected = currentSection == section
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    showDueBreakdown = false
                                    if (section == AppSection.HISTORY_REPORTS && currentSection != AppSection.HISTORY_REPORTS) {
                                        historyAccountFilter = null
                                        historyNavNonce++
                                    }
                                    currentSection = section
                                },
                                icon = {
                                    if (section == AppSection.TRANSACTIONS && pendingCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                            Icon(section.icon, contentDescription = section.labelBn)
                                        }
                                    } else {
                                        Icon(section.icon, contentDescription = section.labelBn)
                                    }
                                },
                                label = {
                                    Text(
                                        text = when (section) {
                                            AppSection.PARTNERS_LOANS -> "পার্টনার"
                                            AppSection.HISTORY_REPORTS -> "রিপোর্ট"
                                            else -> section.labelBn
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.testTag("nav_${section.name.lowercase()}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        AppSection.entries.forEach { section ->
                            if (section == AppSection.ADMIN_SETTINGS && !viewModel.isOwner) {
                                NavigationRailItem(
                                    selected = currentSection == section,
                                    onClick = { currentSection = section },
                                    icon = { Icon(Icons.Filled.Info, contentDescription = "সিস্টেম তথ্য") },
                                    label = { Text("সিস্টেম তথ্য") },
                                )
                            } else {
                                NavigationRailItem(
                                    selected = currentSection == section,
                                    onClick = {
                                        if (section == AppSection.HISTORY_REPORTS && currentSection != AppSection.HISTORY_REPORTS) {
                                            historyAccountFilter = null
                                            historyNavNonce++
                                        }
                                        currentSection = section
                                    },
                                    icon = { Icon(section.icon, contentDescription = section.labelBn) },
                                    label = { Text(section.labelBn) },
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (feedback.isWorking) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        when (val state = businessState) {
                            is UiState.Loading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        CircularProgressIndicator()
                                        Text(
                                            "ব্যবসায়িক ডেটা ও ১১টি অ্যাকাউন্ট লোড হচ্ছে...",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                            }
                            is UiState.Error -> {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ElevatedCard(modifier = Modifier.widthIn(max = 460.dp)) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                "ডেটা লোড করা যায়নি",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = LossCrimson,
                                            )
                                            Text(state.message, style = MaterialTheme.typography.bodyMedium)
                                            OutlinedButton(onClick = { showSignOutDialog = true }) {
                                                Text("অ্যাকাউন্ট পরিবর্তন করুন")
                                            }
                                        }
                                    }
                                }
                            }
                            is UiState.Success -> {
                                val data = state.data
                                when (currentSection) {
                                    AppSection.DASHBOARD -> if (showDueBreakdown) {
                                        DueBreakdownScreen(
                                            data = data,
                                            onBack = { showDueBreakdown = false },
                                        )
                                    } else {
                                        DashboardScreen(
                                            data = data,
                                            isOwner = viewModel.isOwner,
                                            onNavigateToNewTx = { openTxHub(0) },
                                            onNavigateToPending = { openTxHub(5) },
                                            onViewAccountHistory = openAccountHistory,
                                            onSubmitAdjustment = { req -> viewModel.submitTransaction(req) },
                                            onNavigateToHistory = {
                                                historyAccountFilter = null
                                                historyNavNonce++
                                                currentSection = AppSection.HISTORY_REPORTS
                                            },
                                            onNavigateToDueBreakdown = { showDueBreakdown = true },
                                        )
                                    }
                                    AppSection.TRANSACTIONS -> key(txHubNavNonce) {
                                        TransactionHubScreen(
                                            data = data,
                                            isOwner = viewModel.isOwner,
                                            isWorking = feedback.isWorking,
                                            initialTab = txHubInitialTab,
                                            onSubmitTx = { req, onClear -> viewModel.submitTransaction(req, onClear) },
                                            onApproveTx = { txId -> viewModel.approveTransaction(txId) },
                                            onRemoveTx = { txId -> viewModel.removePendingTransaction(txId) },
                                        )
                                    }
                                    AppSection.ACCOUNTS -> AccountsAndCapitalScreen(
                                        data = data,
                                        isOwner = viewModel.isOwner,
                                        isWorking = feedback.isWorking,
                                        onSubmitTx = { req, onClear -> viewModel.submitTransaction(req, onClear) },
                                        onToggleAllowNegative = { accId, allow -> viewModel.setAccountAllowNegative(accId, allow) },
                                        onViewAccountHistory = openAccountHistory,
                                    )
                                    AppSection.PARTNERS_LOANS -> PartnersAndLoansScreen(
                                        data = data,
                                        isOwner = viewModel.isOwner,
                                        isWorking = feedback.isWorking,
                                        onSubmitTx = { req, onClear -> viewModel.submitTransaction(req, onClear) },
                                        onUpdatePartnerRate = { pId, rate, bkashOnly ->
                                            viewModel.updatePartnerRate(pId, rate, bkashOnly)
                                        },
                                    )
                                    AppSection.HISTORY_REPORTS -> key(historyNavNonce) {
                                        HistoryAndReportsScreen(
                                            data = data,
                                            isOwner = viewModel.isOwner,
                                            initialAccountFilter = historyAccountFilter,
                                            onReverseAndCorrect = { origId, reason, corrected ->
                                                viewModel.reverseAndCorrect(origId, reason, corrected)
                                            },
                                        )
                                    }
                                    AppSection.ADMIN_SETTINGS -> OwnerAdminScreen(
                                        data = data,
                                        isOwner = viewModel.isOwner,
                                        feedback = feedback,
                                        onAddEmployee = { name, email, directUid, phone ->
                                            viewModel.addEmployee(name, email, directUid, phone)
                                        },
                                        onToggleEmployeeActive = { uid, active ->
                                            viewModel.toggleEmployeeActive(uid, active)
                                        },
                                        onDeleteInvite = { email -> viewModel.deleteEmployeeInvite(email) },
                                        onExportBackup = { onReady -> viewModel.generateBackupJson(onReady) },
                                        onRestoreBackup = { json, onSafety -> viewModel.restoreFromBackup(json, onSafety) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
