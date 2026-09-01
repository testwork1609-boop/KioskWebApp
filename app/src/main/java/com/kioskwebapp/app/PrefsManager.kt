package com.kioskwebapp.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Handles secure, persistent storage of the kiosk configuration:
 * target URL, admin password (hashed, never stored in plain text),
 * and the auto-refresh interval.
 *
 * Backed by EncryptedSharedPreferences, which uses an Android Keystore
 * generated key to encrypt both keys and values on disk.
 */
class PrefsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "kiosk_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun isConfigured(): Boolean = prefs.getBoolean(KEY_CONFIGURED, false)

    fun getUrlPriorytety(): String = prefs.getString(KEY_URL_PRIORYTETY, "") ?: ""

    fun getUrlPrzeglady(): String = prefs.getString(KEY_URL_PRZEGLADY, "") ?: ""

    fun getRefreshIntervalMinutes(): Int = prefs.getInt(KEY_REFRESH_MINUTES, 5)

    fun saveConfiguration(
        urlPriorytety: String,
        urlPrzeglady: String,
        password: String,
        refreshMinutes: Int = 5
    ) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        prefs.edit()
            .putString(KEY_URL_PRIORYTETY, urlPriorytety)
            .putString(KEY_URL_PRZEGLADY, urlPrzeglady)
            .putString(KEY_PASSWORD_SALT, salt)
            .putString(KEY_PASSWORD_HASH, hash)
            .putInt(KEY_REFRESH_MINUTES, refreshMinutes)
            .putBoolean(KEY_CONFIGURED, true)
            .apply()
    }

    fun updateUrlPriorytety(url: String) {
        prefs.edit().putString(KEY_URL_PRIORYTETY, url).apply()
    }

    fun updateUrlPrzeglady(url: String) {
        prefs.edit().putString(KEY_URL_PRZEGLADY, url).apply()
    }

    fun updatePassword(newPassword: String) {
        val salt = generateSalt()
        val hash = hashPassword(newPassword, salt)
        prefs.edit()
            .putString(KEY_PASSWORD_SALT, salt)
            .putString(KEY_PASSWORD_HASH, hash)
            .apply()
    }

    fun updateRefreshInterval(minutes: Int) {
        prefs.edit().putInt(KEY_REFRESH_MINUTES, minutes).apply()
    }

    fun checkPassword(password: String): Boolean {
        val salt = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        return hashPassword(password, salt) == storedHash
    }

    fun resetConfiguration() {
        prefs.edit().clear().apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray())
        val bytes = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_CONFIGURED = "configured"
        private const val KEY_URL_PRIORYTETY = "url_priorytety"
        private const val KEY_URL_PRZEGLADY = "url_przeglady"
        private const val KEY_PASSWORD_HASH = "password_hash"
        private const val KEY_PASSWORD_SALT = "password_salt"
        private const val KEY_REFRESH_MINUTES = "refresh_minutes"
    }
}
