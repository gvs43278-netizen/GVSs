package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AuthManager
import com.example.data.BackupRepository
import com.example.data.BusinessRepository
import com.example.data.SessionResolution
import com.example.data.ThemePreferences
import com.example.ui.BusinessAppShell
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.UnauthorizedAccessScreen
import com.example.ui.theme.BusinessManagerTheme
import com.example.ui.theme.LossCrimson
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themePrefs = remember { ThemePreferences(context) }
            val isDarkMode by themePrefs.isDarkMode.collectAsStateWithLifecycle(initialValue = false)

            BusinessManagerTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BusinessManagerAppRoot(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { enabled ->
                            scope.launch { themePrefs.setDarkMode(enabled) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun BusinessManagerAppRoot(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by AuthManager.authStateFlow().collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
        return
    }

    key(user.uid) {
        val repository = remember(user.uid) { BusinessRepository(context) }
        val backupRepository = remember(user.uid) { BackupRepository(repository.db) }
        var sessionState by remember { mutableStateOf<SessionResolution>(SessionResolution.Loading) }
        var retryNonce by remember { mutableIntStateOf(0) }
        var isSigningOut by remember { mutableStateOf(false) }

        LaunchedEffect(user.uid, retryNonce) {
            sessionState = SessionResolution.Loading
            sessionState = repository.resolveAndBootstrapSession()
        }

        val triggerSignOut: () -> Unit = {
            isSigningOut = true
            AuthManager.signOut(credentialManager = credentialManager, scope = scope) {
                isSigningOut = false
            }
        }

        if (isSigningOut) {
            Box(
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("সাইন-আউট করা হচ্ছে...")
                }
            }
        } else {
            when (val session = sessionState) {
            is SessionResolution.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "ব্যবহারকারীর অনুমতি ও অ্যাকাউন্ট যাচাই করা হচ্ছে...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            is SessionResolution.Unauthorized -> {
                UnauthorizedAccessScreen(
                    uid = session.uid,
                    email = session.email,
                    displayName = session.displayName,
                    onRetryCheck = { retryNonce++ },
                    onSignOut = triggerSignOut,
                )
            }
            is SessionResolution.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ElevatedCard(modifier = Modifier.widthIn(max = 460.dp)) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "সেশন যাচাইয়ে ত্রুটি",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = LossCrimson,
                            )
                            Text(
                                session.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            Button(onClick = { retryNonce++ }) {
                                Text("আবার চেষ্টা করুন")
                            }
                            OutlinedButton(onClick = triggerSignOut) {
                                Text("সাইন-আউট")
                            }
                        }
                    }
                }
            }
            is SessionResolution.Authorized -> {
                val vmFactory = remember(user.uid, session.employee, session.isOwner) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MainViewModel(
                                repository = repository,
                                backupRepository = backupRepository,
                                currentUserId = user.uid,
                                currentEmployee = session.employee,
                                isOwner = session.isOwner,
                            ) as T
                        }
                    }
                }
                val mainViewModel: MainViewModel = viewModel(
                    key = "main_vm_${user.uid}",
                    factory = vmFactory,
                )
                BusinessAppShell(
                    viewModel = mainViewModel,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onSignOutConfirmed = triggerSignOut,
                )
            }
            }
        }
    }
}
