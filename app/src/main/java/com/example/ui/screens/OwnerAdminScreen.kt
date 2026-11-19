package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.BusinessConstants
import com.example.domain.MoneyFormatter
import com.example.domain.UserRole
import com.example.ui.ActionFeedback
import com.example.ui.BusinessSnapshotData
import com.example.ui.theme.LossCrimson
import com.example.ui.theme.ProfitGreen
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.io.File

@Composable
fun OwnerAdminScreen(
    data: BusinessSnapshotData,
    isOwner: Boolean,
    feedback: ActionFeedback,
    onAddEmployee: (name: String, email: String, directUid: String?, phone: String?) -> Unit,
    onToggleEmployeeActive: (uid: String, active: Boolean) -> Unit,
    onDeleteInvite: (email: String) -> Unit = {},
    onExportBackup: (onJsonReady: (String) -> Unit) -> Unit,
    onRestoreBackup: (json: String, onSafetyJson: (String) -> Unit) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isOwner) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text("সাধারণ মেম্বার ব্যবস্থাপনা") },
                    modifier = Modifier.testTag("tab_employees"),
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text("ব্যাকআপ ও রিস্টোর") },
                    modifier = Modifier.testTag("tab_backup"),
                )
            }
            FilterChip(
                selected = tab == 2 || !isOwner,
                onClick = { tab = 2 },
                label = { Text(if (isOwner) "অডিট লগ ও ফ্রি প্ল্যান" else "সিস্টেম তথ্য") },
                modifier = Modifier.testTag("tab_audit_plan"),
            )
        }
        Spacer(Modifier.height(10.dp))

        if (!isOwner) {
            FreePlanAndSecurityInfo(isOwner = false, data = data)
            return
        }

        when (tab) {
            0 -> EmployeesTab(
                data = data,
                isWorking = feedback.isWorking,
                onAddEmployee = onAddEmployee,
                onToggleActive = onToggleEmployeeActive,
                onDeleteInvite = onDeleteInvite,
            )
            1 -> BackupRestoreTab(
                feedback = feedback,
                onExportBackup = onExportBackup,
                onRestoreBackup = onRestoreBackup,
            )
            else -> FreePlanAndSecurityInfo(isOwner = true, data = data)
        }
    }
}

@Composable
private fun EmployeesTab(
    data: BusinessSnapshotData,
    isWorking: Boolean,
    onAddEmployee: (String, String, String?, String?) -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onDeleteInvite: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var directUid by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("নতুন সাধারণ মেম্বার যুক্ত করুন") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "সাধারণ মেম্বার Google দিয়ে সাইন-ইন করলে স্বয়ংক্রিয়ভাবে General Member হিসেবে যুক্ত হবেন এবং তাদের সব কাজ সুপার অ্যাডমিনের অনুমোদনের জন্য অপেক্ষমাণ থাকবে।",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; err = null },
                        label = { Text("মেম্বারের নাম *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; err = null },
                        label = { Text("Google ইমেইল *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = directUid,
                        onValueChange = { directUid = it },
                        label = { Text("Firebase UID (ঐচ্ছিক)") },
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
                    if (err != null) Text(err!!, color = LossCrimson, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || email.isBlank()) err = "নাম ও ইমেইল আবশ্যক।"
                        else {
                            onAddEmployee(name, email, directUid.takeIf { it.isNotBlank() }, phone.takeIf { it.isNotBlank() })
                            showAddDialog = false
                        }
                    },
                    enabled = !isWorking,
                ) {
                    Text("যুক্ত করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) { Text("বাতিল") }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            val context = LocalContext.current
            val authUser = Firebase.auth.currentUser
            val authEmail = authUser?.email?.trim().orEmpty()
            val authUid = authUser?.uid.orEmpty()
            var showUidGuide by remember { mutableStateOf(false) }

            if (showUidGuide) {
                AlertDialog(
                    onDismissRequest = { showUidGuide = false },
                    title = { Text("নিরাপদ Owner UID যাচাই ও আপডেট") },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("নির্ধারিত Owner Gmail: ${BusinessConstants.OWNER_EMAIL}", fontWeight = FontWeight.SemiBold)
                            Text("সক্রিয় Super Admin UID: ${BusinessConstants.SUPER_ADMIN_UID}", fontWeight = FontWeight.SemiBold)
                            Text("বর্তমান সাইন-ইন Gmail: ${authEmail.ifBlank { "অজানা" }}", style = MaterialTheme.typography.bodySmall)
                            Text("বর্তমান প্রকৃত Firebase UID: ${authUid.ifBlank { "অজানা" }}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "নিরাপত্তা নীতি: শুধুমাত্র Firestore Security Rules ও অ্যাপ কোডের সাথে UID মিললেই Owner ক্ষমতা দেওয়া হয়—ইমেইল দেখে অনুমান করে অ্যাক্সেস দেওয়া হয় না।",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "Owner UID পরিবর্তন করতে চাইলে:\n১) BusinessConstants.SUPER_ADMIN_UID আপডেট করুন।\n২) firestore.rules-এর সুপার অ্যাডমিন UID আপডেট করে Firebase Rules ডিপ্লয় করুন।\n৩) নতুন UID দিয়ে সাইন-ইন করলে স্বয়ংক্রিয় বুটস্ট্র্যাপ সম্পন্ন হবে।",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Owner UID", authUid.ifBlank { BusinessConstants.SUPER_ADMIN_UID }))
                            Toast.makeText(context, "UID কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("UID কপি করুন")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showUidGuide = false }) { Text("বন্ধ করুন") }
                    },
                )
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("স্থায়ী সুপার অ্যাডমিন ও Owner পরিচয় (যাচাইকৃত)", style = MaterialTheme.typography.labelMedium)
                    Text("UID: ${BusinessConstants.SUPER_ADMIN_UID}", fontWeight = FontWeight.Bold)
                    Text("Owner Gmail: ${BusinessConstants.OWNER_EMAIL}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Text(
                        "সাইন-ইনকৃত Firebase Auth: ${authEmail.ifBlank { "অজানা" }} (${if (authUid == BusinessConstants.SUPER_ADMIN_UID) "✓ UID মিলেছে" else "UID অমিল"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (authUid == BusinessConstants.SUPER_ADMIN_UID) ProfitGreen else LossCrimson,
                    )
                    OutlinedButton(
                        onClick = { showUidGuide = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_owner_uid_guide"),
                    ) {
                        Text("নিরাপদ Owner UID যাচাই / আপডেট নির্দেশিকা")
                    }
                }
            }
        }
        item {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("btn_add_employee"),
            ) {
                Text("+ নতুন সাধারণ মেম্বার যুক্ত করুন")
            }
        }
        items(data.employees, key = { it.uid }) { emp ->
            val isSuper = emp.uid == BusinessConstants.SUPER_ADMIN_UID
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(emp.name, fontWeight = FontWeight.Bold)
                        Text(emp.email, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${UserRole.fromRaw(emp.role).labelBn} • UID: ${emp.uid.take(12)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSuper) ProfitGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isSuper) {
                        Switch(
                            checked = emp.active,
                            onCheckedChange = { onToggleActive(emp.uid, it) },
                        )
                    }
                }
            }
        }
        if (data.invites.isNotEmpty()) {
            item {
                Text("আমন্ত্রিত সাধারণ মেম্বার (${data.invites.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(data.invites, key = { "inv_${it.email}" }) { inv ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(inv.name, fontWeight = FontWeight.Bold)
                            Text("${inv.email} (প্রথমবার সাইন-ইন করলে সক্রিয় হবে)", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { onDeleteInvite(inv.email) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "আমন্ত্রণ বাতিল করুন",
                                tint = LossCrimson,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreTab(
    feedback: ActionFeedback,
    onExportBackup: ((String) -> Unit) -> Unit,
    onRestoreBackup: (String, (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var pendingBackupToSave by remember { mutableStateOf<String?>(null) }
    var pendingSafetyBackupToSave by remember { mutableStateOf<String?>(null) }
    var candidateRestoreJson by remember { mutableStateOf<String?>(null) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingBackupToSave ?: pendingSafetyBackupToSave
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "JSON ব্যাকআপ ফাইল সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "ব্যাকআপ সংরক্ষণ ব্যর্থ: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
        pendingBackupToSave = null
        pendingSafetyBackupToSave = null
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            if (content.isNullOrBlank()) {
                Toast.makeText(context, "ফাইল পড়া যায়নি", Toast.LENGTH_LONG).show()
            } else {
                candidateRestoreJson = content
            }
        }
    }

    candidateRestoreJson?.let { jsonToRestore ->
        AlertDialog(
            onDismissRequest = { candidateRestoreJson = null },
            title = { Text("ব্যাকআপ রিস্টোর নিশ্চিতকরণ") },
            text = {
                Text(
                    "সতর্কতা: রিস্টোর করলে বর্তমান লেনদেন, লেজার ও ঋণের ডেটা নির্বাচিত ব্যাকআপ ফাইল দ্বারা প্রতিস্থাপিত হবে। রিস্টোর শুরুর আগে সিস্টেম স্বয়ংক্রিয়ভাবে একটি নিরাপত্তা ব্যাকআপ তৈরি করবে।"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val payload = jsonToRestore
                        candidateRestoreJson = null
                        onRestoreBackup(payload) { safetyJson ->
                            val fileName = "PreRestore_SafetyBackup_${System.currentTimeMillis()}.json"
                            runCatching {
                                File(context.filesDir, fileName).writeText(safetyJson, Charsets.UTF_8)
                            }
                            pendingSafetyBackupToSave = safetyJson
                            createBackupLauncher.launch(fileName)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LossCrimson),
                ) {
                    Text("নিশ্চিত, রিস্টোর করুন")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { candidateRestoreJson = null }) { Text("বাতিল") }
            },
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("সম্পূর্ণ JSON ব্যাকআপ ও রিস্টোর", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "১১টি অ্যাকাউন্ট, পার্টনার, লেনদেন, লেজার, ঋণ ও কর্মচারীর ডেটা নিরাপদ JSON হিসেবে ডাউনলোড ও যাচাইপূর্বক রিস্টোর করুন।",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            onExportBackup { json ->
                                pendingBackupToSave = json
                                createBackupLauncher.launch("BusinessManagerPro_Backup_${System.currentTimeMillis()}.json")
                            }
                        },
                        enabled = !feedback.isWorking,
                        modifier = Modifier.fillMaxWidth().testTag("btn_download_backup"),
                    ) {
                        Text("পূর্ণ JSON ব্যাকআপ ডাউনলোড করুন")
                    }
                    OutlinedButton(
                        onClick = { openBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                        enabled = !feedback.isWorking,
                        modifier = Modifier.fillMaxWidth().testTag("btn_restore_backup"),
                    ) {
                        Text("ব্যাকআপ ফাইল থেকে রিস্টোর করুন")
                    }
                    if (feedback.restoreProgress != null) {
                        LinearProgressIndicator(
                            progress = { feedback.restoreProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(feedback.restoreStatusText ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FreePlanAndSecurityInfo(isOwner: Boolean, data: BusinessSnapshotData) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Firebase Spark (ফ্রি) প্ল্যান তথ্য ও সীমাবদ্ধতা", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• কোনো ক্রেডিট কার্ড, ভিসা/মাস্টারকার্ড বা Blaze পেইড আপগ্রেড সক্রিয় নেই।", style = MaterialTheme.typography.bodySmall)
                    Text("• Spark ফ্রি কোটা: প্রতিদিন ৫০,০০০ Firestore Read, ২০,০০০ Write, ২০,০০০ Delete এবং ১ GiB স্টোরেজ।", style = MaterialTheme.typography.bodySmall)
                    Text("• কোটা পূর্ণ হলে স্বয়ংক্রিয়ভাবে পেইড সার্ভিস চালু হবে না; পরিষ্কার সতর্কবার্তা দেখাবে এবং পরদিন রিসেট হবে।", style = MaterialTheme.typography.bodySmall)
                    Text("• চিরস্থায়ী আনলিমিটেড বা ২৪/৭ গ্যারান্টিযুক্ত হোস্টিং নয় — ফ্রি সীমার মধ্যে পরিচালিত।", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (isOwner) {
            item {
                Text("অডিট লগ (${data.auditLogs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(data.auditLogs.take(50), key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${log.action} • ${log.actorName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(log.details, style = MaterialTheme.typography.bodySmall)
                        val ts = log.createdAt?.toDate()?.time ?: 0L
                        if (ts > 0L) Text(MoneyFormatter.formatDateTime(ts), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
