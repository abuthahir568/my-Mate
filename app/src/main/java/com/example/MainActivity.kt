package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.preferences.MatePreferences
import com.example.data.preferences.ThemeMode
import com.example.data.repository.MateRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.MateWorkspaceScreen
import com.example.ui.theme.MyMateTheme
import com.example.ui.viewmodel.MateViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val preferences = remember(context) { MatePreferences(context.applicationContext) }
            val themeMode by preferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyMateTheme(darkTheme = isDark) {
                MyMateRootApp()
            }
        }
    }
}

@Composable
fun MyMateRootApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val preferences = remember(context) { MatePreferences(context.applicationContext) }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        // Strict Mobile Number OTP Auth Gate: No Firestore ViewModels or queries run until verified
        AuthScreen(onAuthSuccess = {})
    } else {
        val currentUserId = user.uid
        val savedName by preferences.observeDisplayName(currentUserId).collectAsStateWithLifecycle(initialValue = null)
        val displayName = savedName?.takeIf { it.isNotBlank() }
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: user.phoneNumber?.takeIf { it.isNotBlank() }
            ?: "My Mate"
        val photoUrl = user.photoUrl?.toString()

        val mateViewModel: MateViewModel = viewModel(
            key = "$currentUserId::$displayName",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val db = FirebaseFirestore.getInstance(databaseId)
                    MateViewModel(
                        repository = MateRepository(db),
                        preferences = MatePreferences(app),
                        currentUserId = currentUserId,
                        currentUserName = displayName,
                        currentUserPhoto = photoUrl
                    )
                }
            }
        )

        MateWorkspaceScreen(
            viewModel = mateViewModel,
            onSignedOut = {}
        )
    }
}
