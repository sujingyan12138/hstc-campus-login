package com.hstc.quicklogin.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CredentialStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences = EncryptedSharedPreferences.create(
        context,
        "hstc_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun load(): SavedCredentials = withContext(Dispatchers.IO) {
        SavedCredentials(
            username = preferences.getString("username", "") ?: "",
            password = preferences.getString("password", "") ?: "",
            autoRetry = preferences.getBoolean("autoRetry", true),
            loggingEnabled = preferences.getBoolean("loggingEnabled", false)
        )
    }

    suspend fun save(credentials: SavedCredentials) = withContext(Dispatchers.IO) {
        preferences.edit()
            .putString("username", credentials.username)
            .putString("password", credentials.password)
            .putBoolean("autoRetry", credentials.autoRetry)
            .putBoolean("loggingEnabled", credentials.loggingEnabled)
            .apply()
    }

    suspend fun clearCredentials() = withContext(Dispatchers.IO) {
        preferences.edit()
            .remove("username")
            .remove("password")
            .apply()
    }
}
