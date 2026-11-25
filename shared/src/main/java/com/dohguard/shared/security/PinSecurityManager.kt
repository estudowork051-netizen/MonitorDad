package com.dohguard.shared.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utilitário puro para hash SHA-256 com salt do PIN do responsável.
 * Nunca armazena nem registra o PIN em texto puro.
 */
object PinHashUtils {

    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String, storedHash: String, salt: String): Boolean {
        if (inputPin.isBlank() || storedHash.isBlank() || salt.isBlank()) return false
        val candidateHash = hashPin(inputPin, salt)
        return MessageDigest.isEqual(
            candidateHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
    }
}

/**
 * Gerenciador seguro do PIN parental armazenado localmente apenas como hash com salt.
 */
class PinSecurityManager(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureInitialized()
    }

    private fun ensureInitialized() {
        val existingHash = prefs.getString(KEY_PIN_HASH, null)
        val existingSalt = prefs.getString(KEY_PIN_SALT, null)
        if (existingHash.isNullOrBlank() || existingSalt.isNullOrBlank()) {
            val salt = PinHashUtils.generateSalt()
            val hash = PinHashUtils.hashPin(DEFAULT_DEMO_PIN, salt)
            prefs.edit()
                .putString(KEY_PIN_SALT, salt)
                .putString(KEY_PIN_HASH, hash)
                .apply()
        }
    }

    fun verifyPin(inputPin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, "") ?: ""
        val hash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        return PinHashUtils.verifyPin(inputPin, hash, salt)
    }

    fun updatePin(currentPin: String, newPin: String): Boolean {
        if (newPin.length != 4 || !newPin.all { it.isDigit() }) return false
        if (!verifyPin(currentPin)) return false
        val newSalt = PinHashUtils.generateSalt()
        val newHash = PinHashUtils.hashPin(newPin, newSalt)
        prefs.edit()
            .putString(KEY_PIN_SALT, newSalt)
            .putString(KEY_PIN_HASH, newHash)
            .apply()
        return true
    }

    companion object {
        private const val PREFS_NAME = "monitordad_security_prefs"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val DEFAULT_DEMO_PIN = "1234"
    }
}
