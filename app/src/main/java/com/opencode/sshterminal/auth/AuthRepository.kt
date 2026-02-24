package com.opencode.sshterminal.auth

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private val hasPasswordFlow: Flow<Boolean> =
            dataStore.data.map { prefs ->
                val hash = prefs[PASSWORD_HASH_KEY]
                val salt = prefs[PASSWORD_SALT_KEY]
                !hash.isNullOrBlank() && !salt.isNullOrBlank()
            }

        private val appLockEnabledPreference: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[APP_LOCK_ENABLED_KEY] ?: true
            }

        val isAppLockEnabled: Flow<Boolean> =
            combine(hasPasswordFlow, appLockEnabledPreference) { hasPassword, enabled ->
                hasPassword && enabled
            }

        val isBiometricEnabled: Flow<Boolean> =
            dataStore.data.map { prefs ->
                prefs[BIOMETRIC_ENABLED_KEY] ?: false
            }

        val isFirstSetupRequired: Flow<Boolean> =
            combine(hasPasswordFlow, appLockEnabledPreference) { hasPassword, enabled ->
                !hasPassword && enabled
            }

        suspend fun hasPassword(): Boolean {
            val prefs = dataStore.data.first()
            val hash = prefs[PASSWORD_HASH_KEY]
            val salt = prefs[PASSWORD_SALT_KEY]
            return !hash.isNullOrBlank() && !salt.isNullOrBlank()
        }

        suspend fun setMasterPassword(password: String) {
            setMasterPassword(password.toCharArray())
        }

        suspend fun setMasterPassword(password: CharArray) {
            try {
                val salt = ByteArray(SALT_LENGTH_BYTES)
                secureRandom.nextBytes(salt)
                val hash = deriveHash(password = password, salt = salt)
                dataStore.edit { prefs ->
                    prefs[PASSWORD_HASH_KEY] = Base64.encodeToString(hash, Base64.NO_WRAP)
                    prefs[PASSWORD_SALT_KEY] = Base64.encodeToString(salt, Base64.NO_WRAP)
                    prefs[APP_LOCK_ENABLED_KEY] = true
                }
            } finally {
                password.zeroize()
            }
        }

        suspend fun verifyPassword(password: String): Boolean {
            return verifyPassword(password.toCharArray())
        }

        @Suppress("ReturnCount")
        suspend fun verifyPassword(password: CharArray): Boolean {
            return try {
                val prefs = dataStore.data.first()
                val storedHashEncoded = prefs[PASSWORD_HASH_KEY] ?: return false
                val storedSaltEncoded = prefs[PASSWORD_SALT_KEY] ?: return false
                val storedHash = Base64.decode(storedHashEncoded, Base64.DEFAULT)
                val storedSalt = Base64.decode(storedSaltEncoded, Base64.DEFAULT)
                val calculated = deriveHash(password = password, salt = storedSalt)
                MessageDigest.isEqual(storedHash, calculated)
            } finally {
                password.zeroize()
            }
        }

        suspend fun setBiometricEnabled(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[BIOMETRIC_ENABLED_KEY] = enabled
            }
        }

        suspend fun setAppLockEnabled(enabled: Boolean) {
            dataStore.edit { prefs ->
                prefs[APP_LOCK_ENABLED_KEY] = enabled
            }
        }

        private fun deriveHash(
            password: CharArray,
            salt: ByteArray,
        ): ByteArray {
            val passwordCopy = password.copyOf()
            val spec = PBEKeySpec(passwordCopy, salt, PBKDF2_ITERATIONS, DERIVED_KEY_BITS)
            return try {
                keyFactory.generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
                passwordCopy.zeroize()
            }
        }

        private fun CharArray.zeroize() {
            fill('\u0000')
        }

        companion object {
            private val PASSWORD_HASH_KEY = stringPreferencesKey("pref_auth_password_hash")
            private val PASSWORD_SALT_KEY = stringPreferencesKey("pref_auth_password_salt")
            private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("pref_auth_biometric_enabled")
            private val APP_LOCK_ENABLED_KEY = booleanPreferencesKey("pref_auth_lock_enabled")

            private const val PBKDF2_ITERATIONS = 120_000
            private const val DERIVED_KEY_BITS = 256
            private const val SALT_LENGTH_BYTES = 16

            private val secureRandom = SecureRandom()
            private val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        }
    }
