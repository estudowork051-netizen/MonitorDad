package com.dohguard.shared.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Armazenamento cifrado de credenciais sensíveis (ex.: sharedSecret HMAC)
 * utilizando Android Keystore (AES-256-GCM) sem dependências externas.
 * Nunca grava segredos em texto puro após a migração inicial.
 */
class SecureCredentialStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun saveSharedSecret(secret: String) {
        if (secret.isBlank()) {
            prefs.edit().remove(KEY_ENCRYPTED_SECRET).remove(KEY_SECRET_IV).apply()
            return
        }
        val cipherResult = encryptWithKeystore(secret)
        if (cipherResult != null) {
            val (ivBase64, cipherBase64) = cipherResult
            prefs.edit()
                .putString(KEY_SECRET_IV, ivBase64)
                .putString(KEY_ENCRYPTED_SECRET, cipherBase64)
                .apply()
        }
    }

    fun loadSharedSecret(): String {
        val ivBase64 = prefs.getString(KEY_SECRET_IV, null) ?: return ""
        val cipherBase64 = prefs.getString(KEY_ENCRYPTED_SECRET, null) ?: return ""
        return decryptWithKeystore(ivBase64, cipherBase64) ?: ""
    }

    private fun getOrCreateAesKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptWithKeystore(plaintext: String): Pair<String, String>? {
        return try {
            val key = getOrCreateAesKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv, Base64.NO_WRAP) to
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptWithKeystore(ivBase64: String, cipherBase64: String): String? {
        return try {
            val key = getOrCreateAesKey() ?: return null
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val encrypted = Base64.decode(cipherBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "monitordad_hmac_secret_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PREFS_NAME = "monitordad_secure_credentials"
        private const val KEY_ENCRYPTED_SECRET = "enc_shared_secret"
        private const val KEY_SECRET_IV = "enc_shared_secret_iv"
    }
}
