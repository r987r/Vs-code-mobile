package com.vscode.mobile.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores and retrieves OAuth tokens using Android EncryptedSharedPreferences backed
 * by the Android Keystore, so tokens are never stored in plaintext on-device.
 *
 * All reads/writes are performed synchronously on the calling thread; callers should
 * use a background dispatcher for I/O if necessary.
 */
class SecureTokenStorage(context: Context) {

    companion object {
        private const val TAG = "SecureTokenStorage"
        private const val PREFS_FILE = "vscode_mobile_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry_ms"
        private const val KEY_ID_TOKEN = "id_token"
    }

    private val prefs by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs; wiping and retrying", e)
            // If the keystore entry is corrupted (e.g. after a factory reset without full wipe),
            // clear the file and recreate.
            context.deleteSharedPreferences(PREFS_FILE)
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    /** Save OAuth tokens after a successful auth flow. */
    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiryTimeMs: Long,
        idToken: String? = null
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTimeMs)
            .putString(KEY_ID_TOKEN, idToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getTokenExpiryMs(): Long = prefs.getLong(KEY_TOKEN_EXPIRY, 0L)

    fun isAccessTokenValid(): Boolean {
        val token = getAccessToken() ?: return false
        if (token.isBlank()) return false
        val expiry = getTokenExpiryMs()
        // Treat as invalid if within 60 seconds of expiry
        return expiry == 0L || System.currentTimeMillis() < expiry - 60_000L
    }

    /** Remove all stored tokens (sign-out). */
    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .remove(KEY_ID_TOKEN)
            .apply()
    }

    fun hasTokens(): Boolean = getAccessToken()?.isNotBlank() == true
}
