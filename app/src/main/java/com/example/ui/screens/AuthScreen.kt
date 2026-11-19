package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.AuthManager
import com.example.domain.BusinessConstants
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.PendingAmber

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(EmeraldPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = BusinessConstants.APP_NAME_BN,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(38.dp),
                    )
                }

                Text(
                    text = BusinessConstants.APP_NAME_BN,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "১১টি আলাদা অ্যাকাউন্ট, গ্রাহক লেনদেন, মোবাইল রিচার্জ, পার্টনার কমিশন, ঋণ ও লাভ-ক্ষতি ব্যবস্থাপনা",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldPrimary)
                        Text(
                            text = "নিরাপদ দুই-স্তর রোল অ্যাক্সেস: শুধুমাত্র সুপার অ্যাডমিন UID ও অনুমোদিত সাধারণ মেম্বাররাই প্রবেশ করতে পারবেন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = LossCrimson.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = LossCrimson,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        AuthManager.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            },
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Google দিয়ে সাইন-ইন করুন", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Text(
                    text = "Firebase Spark (ফ্রি) প্ল্যান সামঞ্জস্যপূর্ণ • কোনো ক্রেডিট কার্ড বা পেইড সাবস্ক্রিপশন প্রয়োজন নেই",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun UnauthorizedAccessScreen(
    uid: String,
    email: String,
    displayName: String,
    onRetryCheck: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val isOwnerEmailMatch = email.trim().equals(BusinessConstants.OWNER_EMAIL, ignoreCase = true)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "অনুমতি অপেক্ষমাণ",
                    tint = if (isOwnerEmailMatch) LossCrimson else PendingAmber,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = if (isOwnerEmailMatch) "Owner UID যাচাই অমিল (নিরাপদ লক)" else "অ্যাকাউন্ট অনুমোদন প্রয়োজন",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (isOwnerEmailMatch) {
                        "Firebase Authentication-এ আপনার Owner Gmail (${BusinessConstants.OWNER_EMAIL})-এর প্রকৃত UID পাওয়া গেছে, কিন্তু সেটি নির্ধারিত সুপার অ্যাডমিন UID (${BusinessConstants.SUPER_ADMIN_UID})-এর সাথে মিলেনি। নিরাপত্তার জন্য অনুমান করে Owner অ্যাক্সেস দেওয়া হয়নি। নিচ থেকে প্রকৃত UID কপি করে নিরাপদভাবে Owner UID আপডেট করুন।"
                    } else {
                        "হ্যালো $displayName, আপনার অ্যাকাউন্টটি এখনো এই ব্যবসার সাধারণ মেম্বার তালিকায় যুক্ত হয়নি। নিচের UID বা ইমেইলটি সুপার অ্যাডমিনকে দিন।"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Firebase ইমেইল: ${email.ifBlank { "অজানা" }}", style = MaterialTheme.typography.bodySmall)
                        Text("প্রকৃত Firebase UID: $uid", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (isOwnerEmailMatch) {
                            Text(
                                "নিরাপদ আপডেট: BusinessConstants.SUPER_ADMIN_UID ও firestore.rules-এ নতুন UID বসিয়ে Rules ডিপ্লয় করুন।",
                                style = MaterialTheme.typography.labelSmall,
                                color = LossCrimson,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Firebase UID", uid))
                        Toast.makeText(context, "UID কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (isOwnerEmailMatch) "নতুন Owner UID কপি করুন" else "UID কপি করুন")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onRetryCheck,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("পুনরায় যাচাই")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("সাইন-আউট")
                    }
                }
            }
        }
    }
}
