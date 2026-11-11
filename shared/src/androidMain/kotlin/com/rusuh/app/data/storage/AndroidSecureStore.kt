package com.rusuh.app.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM key in the AndroidKeyStore (never exportable); each value is stored as base64(iv + ciphertext)
 * in a private SharedPreferences file. The entry name is bound as AAD so values cannot be swapped between keys.
 * A corrupt entry (bad tag, invalidated key, malformed data) reads as null and is dropped; any other failure
 * is treated as transient: null, entry kept.
 */
class AndroidSecureStore(
    context: Context,
) : SecureStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) {
            val stored = prefs.getString(key, null) ?: return@withContext null
            try {
                decrypt(key, Base64.decode(stored, Base64.NO_WRAP))
            } catch (
                @Suppress("SwallowedException") e: AEADBadTagException,
            ) {
                dropCorrupt(key)
            } catch (
                @Suppress("SwallowedException") e: KeyPermanentlyInvalidatedException,
            ) {
                dropCorrupt(key)
            } catch (
                @Suppress("SwallowedException") e: GeneralSecurityException,
            ) {
                // Possibly transient (KeyStore busy or unavailable): report a miss, keep the entry.
                null
            } catch (
                @Suppress("SwallowedException") e: IllegalArgumentException,
            ) {
                // Bad base64, entry too short or malformed GCM parameters: corrupt, never recoverable.
                dropCorrupt(key)
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: RuntimeException,
            ) {
                // Provider or KeyStore runtime failures (ProviderException, IllegalStateException, ...) can be
                // transient: report a miss but keep the entry.
                null
            }
        }

    override suspend fun putString(
        key: String,
        value: String,
    ) {
        withContext(Dispatchers.IO) {
            val encoded = Base64.encodeToString(encrypt(key, value), Base64.NO_WRAP)
            check(prefs.edit().putString(key, encoded).commit()) { WRITE_FAILED }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) { check(prefs.edit().remove(key).commit()) { WRITE_FAILED } }
    }

    override suspend fun removeWhere(prefix: String) {
        withContext(Dispatchers.IO) {
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
            check(editor.commit()) { WRITE_FAILED }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { check(prefs.edit().clear().commit()) { WRITE_FAILED } }
    }

    private fun dropCorrupt(key: String): String? {
        prefs.edit().remove(key).commit()
        return null
    }

    private fun encrypt(
        key: String,
        value: String,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        return cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun decrypt(
        key: String,
        data: ByteArray,
    ): String {
        require(data.size > IV_BYTES + TAG_BITS / BITS_PER_BYTE) { "Entry too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
    }

    @Synchronized
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val WRITE_FAILED = "Secure store write failed"
        const val FILE_NAME = "rusuh_secure_store"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rusuh_secure_store_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        const val BITS_PER_BYTE = 8
    }
}
