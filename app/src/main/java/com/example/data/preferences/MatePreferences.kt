package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mateDataStore: DataStore<Preferences> by preferencesDataStore(name = "my_mate_vault_prefs")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class MatePreferences(private val context: Context) {

    private fun secretKeyForUser(uid: String) = stringPreferencesKey("vault_secret_$uid")
    private fun displayNameKeyForUser(uid: String) = stringPreferencesKey("display_name_$uid")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val readReceiptsEnabledKey = booleanPreferencesKey("read_receipts_enabled")

    fun observeVaultSecret(uid: String): Flow<String?> {
        return context.mateDataStore.data.map { prefs ->
            prefs[secretKeyForUser(uid)]
        }
    }

    suspend fun saveVaultSecret(uid: String, secret: String) {
        context.mateDataStore.edit { prefs ->
            prefs[secretKeyForUser(uid)] = secret
        }
    }

    suspend fun clearVaultSecret(uid: String) {
        context.mateDataStore.edit { prefs ->
            prefs.remove(secretKeyForUser(uid))
        }
    }

    fun observeDisplayName(uid: String): Flow<String?> {
        return context.mateDataStore.data.map { prefs ->
            prefs[displayNameKeyForUser(uid)]
        }
    }

    suspend fun saveDisplayName(uid: String, name: String) {
        if (name.isNotBlank()) {
            context.mateDataStore.edit { prefs ->
                prefs[displayNameKeyForUser(uid)] = name.trim()
            }
        }
    }

    val themeModeFlow: Flow<ThemeMode> = context.mateDataStore.data.map { prefs ->
        when (prefs[themeModeKey]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.mateDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    val readReceiptsFlow: Flow<Boolean> = context.mateDataStore.data.map { prefs ->
        prefs[readReceiptsEnabledKey] ?: true
    }

    suspend fun setReadReceipts(enabled: Boolean) {
        context.mateDataStore.edit { prefs ->
            prefs[readReceiptsEnabledKey] = enabled
        }
    }
}
