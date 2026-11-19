package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.domain.BusinessConstants
import com.example.domain.FinancialAccount
import com.example.ui.BusinessSnapshotData
import com.example.ui.DashboardMetrics
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.UnauthorizedAccessScreen
import com.example.ui.theme.BusinessManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import com.example.domain.BusinessTransaction
import com.example.domain.TxStatus
import com.example.domain.TxType
import com.example.ui.DuePersonItem
import com.example.ui.screens.DueBreakdownScreen
import com.example.ui.screens.TransactionHubScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Business Manager Pro", appName)
    }

    @Test
    fun `dashboard renders all 11 accounts and Bengali KPIs`() {
        val accounts = BusinessConstants.ALL_ACCOUNTS.map { seed ->
            FinancialAccount(
                id = seed.accountId,
                accountId = seed.accountId,
                name = seed.name,
                group = seed.group.name,
                sortOrder = seed.sortOrder,
                balancePaisa = 100_000L, // 1,000 Taka each
            )
        }
        val snapshot = BusinessSnapshotData(
            accounts = accounts,
            metrics = DashboardMetrics(
                cashBalancePaisa = 100_000L,
                totalBkashBalancePaisa = 500_000L,
                grandTotalBalancePaisa = 1_100_000L,
                totalCapitalPaisa = 1_500_000L,
                partnerGivenTotalPaisa = 400_000L,
                remainingCapitalPaisa = 1_100_000L,
                totalDuePaisa = 400_000L,
            ),
        )

        composeRule.setContent {
            BusinessManagerTheme {
                DashboardScreen(
                    data = snapshot,
                    isOwner = true,
                    onNavigateToNewTx = {},
                    onNavigateToPending = {},
                    onViewAccountHistory = {},
                    onSubmitAdjustment = {},
                )
            }
        }

        composeRule.onNodeWithTag("dashboard_new_tx_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_pending_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("capital_cash_due_card").assertIsDisplayed()
        composeRule.onNodeWithText("ক্যাশ আছে (Cash in Hand)").assertIsDisplayed()
        composeRule.onNodeWithText("বাকি আছে (Total Due)").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("আজকের সারসংক্ষেপ"))
        composeRule.onNodeWithText("আজকের সারসংক্ষেপ").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("১১টি পৃথক অ্যাকাউন্ট"))
        composeRule.onNodeWithText("১১টি পৃথক অ্যাকাউন্ট").assertIsDisplayed()
    }

    @Test
    fun `clicking total due opens due breakdown screen with individual amounts`() {
        val accounts = BusinessConstants.ALL_ACCOUNTS.map { seed ->
            FinancialAccount(
                id = seed.accountId,
                accountId = seed.accountId,
                name = seed.name,
                group = seed.group.name,
                sortOrder = seed.sortOrder,
                balancePaisa = if (seed.accountId == BusinessConstants.ACC_CASH) 3_000_000L else 0L,
            )
        }
        val snapshot = BusinessSnapshotData(
            accounts = accounts,
            metrics = DashboardMetrics(
                cashBalancePaisa = 3_000_000L,
                totalCapitalPaisa = 5_000_000L,
                partnerGivenTotalPaisa = 2_000_000L,
                remainingCapitalPaisa = 3_000_000L,
                totalDuePaisa = 2_000_000L,
                dueItems = listOf(
                    DuePersonItem("partner_samad_bhai", "Samad Bhai", "পার্টনার", 1_200_000L, "পাঠানো: ৳12,000"),
                    DuePersonItem("partner_jahir_mama", "Jahir Mama", "পার্টনার", 800_000L, "পাঠানো: ৳8,000"),
                ),
            ),
        )

        composeRule.setContent {
            BusinessManagerTheme {
                var showDue by remember { mutableStateOf(false) }
                if (showDue) {
                    DueBreakdownScreen(data = snapshot, onBack = { showDue = false })
                } else {
                    DashboardScreen(
                        data = snapshot,
                        isOwner = true,
                        onNavigateToNewTx = {},
                        onNavigateToPending = {},
                        onViewAccountHistory = {},
                        onSubmitAdjustment = {},
                        onNavigateToDueBreakdown = { showDue = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("total_due_clickable").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("কার কার কাছে কত টাকা বাকি আছে").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Samad Bhai"))
        composeRule.onNodeWithText("Samad Bhai").assertIsDisplayed()
        composeRule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Jahir Mama"))
        composeRule.onNodeWithText("Jahir Mama").assertIsDisplayed()
    }

    @Test
    fun `super admin sees Approve and Remove buttons in pending approvals`() {
        val accounts = BusinessConstants.ALL_ACCOUNTS.map { seed ->
            FinancialAccount(id = seed.accountId, accountId = seed.accountId, name = seed.name)
        }
        val pendingTx = BusinessTransaction(
            id = "tx_pending_1",
            txType = TxType.CUSTOMER_TX.name,
            status = TxStatus.PENDING.name,
            customerName = "করিম স্টোর",
            receivedAccountId = BusinessConstants.ACC_CASH,
            receivedAmountPaisa = 50_000L,
            createdByName = "মেম্বার করিম",
            occurredAtMillis = 1_750_000_000_000L,
        )
        var approvedId = ""
        var removedId = ""

        composeRule.setContent {
            BusinessManagerTheme {
                TransactionHubScreen(
                    data = BusinessSnapshotData(
                        accounts = accounts,
                        transactions = listOf(pendingTx),
                        metrics = DashboardMetrics(pendingCount = 1),
                    ),
                    isOwner = true,
                    isWorking = false,
                    initialTab = 5,
                    onSubmitTx = { _, _ -> },
                    onApproveTx = { approvedId = it },
                    onRemoveTx = { removedId = it },
                )
            }
        }

        composeRule.onNodeWithText("অপেক্ষমাণ অনুমোদন (Pending Approvals)").assertIsDisplayed()
        composeRule.onNodeWithTag("approve_tx_tx_pending_1").assertIsDisplayed().performClick()
        assertEquals("tx_pending_1", approvedId)
        composeRule.onNodeWithTag("remove_tx_tx_pending_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_remove_tx_btn").performClick()
        assertEquals("tx_pending_1", removedId)
    }

    @Test
    fun `unauthorized screen shows Bengali notice and user UID`() {
        composeRule.setContent {
            BusinessManagerTheme {
                UnauthorizedAccessScreen(
                    uid = "test_emp_uid_123",
                    email = "emp@example.com",
                    displayName = "রহিম",
                    onRetryCheck = {},
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithText("অ্যাকাউন্ট অনুমোদন প্রয়োজন").assertIsDisplayed()
        composeRule.onNodeWithText("প্রকৃত Firebase UID: test_emp_uid_123").assertIsDisplayed()
        composeRule.onNodeWithText("UID কপি করুন").assertIsDisplayed()
    }

    @Test
    fun `unauthorized screen locks matching owner email when UID mismatches`() {
        composeRule.setContent {
            BusinessManagerTheme {
                UnauthorizedAccessScreen(
                    uid = "different_uid_999",
                    email = BusinessConstants.OWNER_EMAIL,
                    displayName = "আশরাফ",
                    onRetryCheck = {},
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithText("Owner UID যাচাই অমিল (নিরাপদ লক)").assertIsDisplayed()
        composeRule.onNodeWithText("প্রকৃত Firebase UID: different_uid_999").assertIsDisplayed()
        composeRule.onNodeWithText("নতুন Owner UID কপি করুন").assertIsDisplayed()
    }

    @Test
    fun `general member dashboard hides full business P&L and displays role notice`() {
        val accounts = BusinessConstants.ALL_ACCOUNTS.map { seed ->
            FinancialAccount(
                id = seed.accountId,
                accountId = seed.accountId,
                name = seed.name,
                group = seed.group.name,
                sortOrder = seed.sortOrder,
                balancePaisa = 50_000L,
            )
        }
        val snapshot = BusinessSnapshotData(accounts = accounts)
        composeRule.setContent {
            BusinessManagerTheme {
                DashboardScreen(
                    data = snapshot,
                    isOwner = false,
                    onNavigateToNewTx = {},
                    onNavigateToPending = {},
                    onViewAccountHistory = {},
                    onSubmitAdjustment = {},
                )
            }
        }

        composeRule.onAllNodes(hasScrollAction()).onFirst()
            .performScrollToNode(hasText("সম্পূর্ণ ব্যবসার লাভ-ক্ষতি", substring = true))
        composeRule.onNodeWithText(
            "সাধারণ মেম্বার হিসেবে আপনার জমা দেওয়া কাজগুলো 'অপেক্ষমাণ (Pending)' থাকে এবং সুপার অ্যাডমিন অনুমোদন করলে মূল হিসাবে যুক্ত হয়। সম্পূর্ণ ব্যবসার লাভ-ক্ষতি ও লেজার হিসাব শুধুমাত্র সুপার অ্যাডমিনের ড্যাশবোর্ডে প্রদর্শিত হয়।"
        ).assertIsDisplayed()
    }
}
