package com.codro.listenstudy.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableEntryException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [KeystoreCrypto] backed directly by the AndroidKeyStore (minSdk 26). Secrets are sealed with
 * AES/GCM/NoPadding using a non-exportable 256-bit key generated in the `AndroidKeyStore`.
 *
 * The alias is injectable so instrumented tests can use a dedicated, disposable key without
 * touching the production alias.
 */
class AndroidKeystoreCrypto(private val alias: String) : KeystoreCrypto {

    override fun encrypt(aad: ByteArray, plaintext: ByteArray): SealedSecret {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                updateAAD(aad)
            }
            SealedSecret(iv = cipher.iv, ciphertext = cipher.doFinal(plaintext))
        } catch (error: Exception) {
            // Regenerate the alias only for a proven-permanent key failure; transient cipher/provider
            // errors propagate unchanged so the alias and any existing ciphertext are preserved.
            if (isPermanentKeyFailure(error)) throw PermanentlyUnavailableKeyException(error)
            throw error
        }
    }

    private fun isPermanentKeyFailure(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is KeyPermanentlyInvalidatedException ||
                cause is UnrecoverableKeyException ||
                cause is UnrecoverableEntryException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    override fun decrypt(aad: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val key = loadKey() ?: throw IllegalStateException("Missing Keystore key")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    override fun regenerateKey() {
        deleteKey()
        generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey = loadKey() ?: generateKey()

    private fun loadKey(): SecretKey? =
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun deleteKey() {
        keyStore().takeIf { it.containsAlias(alias) }?.deleteEntry(alias)
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}

/**
 * [SecretStore] that seals secrets with a Keystore-resident AES/GCM key and persists only Base64
 * ciphertext and the GCM IV to a dedicated private SharedPreferences file; plaintext never touches
 * these prefs. The secret [name] is bound into the ciphertext as GCM AAD so a payload cannot be
 * replayed under a different name.
 *
 * Read is fully fail-closed: any exception (missing/corrupt payload, wrong-typed prefs entry,
 * missing/invalidated key) yields `null` rather than crashing. Save recovers once from an unusable
 * key via [RecoveringEncryptor]; decrypt never auto-recovers.
 */
class AndroidKeystoreSecretStore internal constructor(
    private val prefs: SharedPreferences,
    private val crypto: KeystoreCrypto,
) : SecretStore {

    constructor(context: Context) : this(context, PREFS, KEY_ALIAS)

    internal constructor(context: Context, prefsName: String, alias: String) : this(
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE),
        AndroidKeystoreCrypto(alias),
    )

    private val encryptor = RecoveringEncryptor(crypto)

    override fun contains(name: String): Boolean =
        prefs.contains(cipherKey(name)) && prefs.contains(ivKey(name))

    override fun read(name: String): String? = runCatching {
        val ciphertext = prefs.getString(cipherKey(name), null)
        val iv = prefs.getString(ivKey(name), null)
        if (ciphertext == null || iv == null) return@runCatching null
        String(crypto.decrypt(aad(name), decode(iv), decode(ciphertext)), Charsets.UTF_8)
    }.getOrNull()

    override fun save(name: String, secret: String) {
        val sealed = encryptor.seal(aad(name), secret.toByteArray(Charsets.UTF_8))
        val stored = prefs.edit()
            .putString(cipherKey(name), encode(sealed.ciphertext))
            .putString(ivKey(name), encode(sealed.iv))
            .commit()
        if (!stored) throw IllegalStateException("Failed to durably persist encrypted secret")
    }

    override fun replace(name: String, secret: String) = save(name, secret)

    // commit(): removal must be durable and synchronous, not the async apply(); its result is
    // reported so callers never claim a deletion the OS has not persisted.
    @Suppress("ApplySharedPref")
    override fun delete(name: String): Boolean =
        prefs.edit().remove(cipherKey(name)).remove(ivKey(name)).commit()

    private fun aad(name: String): ByteArray = name.toByteArray(Charsets.UTF_8)
    private fun cipherKey(name: String): String = "$name$SUFFIX_CIPHER"
    private fun ivKey(name: String): String = "$name$SUFFIX_IV"
    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        private const val KEY_ALIAS = "com.codro.listenstudy.secret.aes.v1"
        private const val PREFS = "com.codro.listenstudy.secure_secrets"
        private const val SUFFIX_CIPHER = ".ct"
        private const val SUFFIX_IV = ".iv"
    }
}

/**
 * [LegacySecretSource] over a plaintext SharedPreferences entry (the pre-migration
 * `cloud_tts_private/google_api_key`). Used only to feed the one-time migration.
 */
class SharedPrefsLegacySecretSource(
    context: Context,
    private val prefsName: String,
    private val key: String,
) : LegacySecretSource {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun read(): String? = runCatching { prefs.getString(key, null) }.getOrNull()

    // commit(): the one-time migration must synchronously and durably drop the legacy plaintext
    // once the secure copy is verified; apply()'s async write is unsafe here. Its result is
    // reported so migration only claims completion when the plaintext is truly gone.
    @Suppress("ApplySharedPref")
    override fun clear(): Boolean = prefs.edit().remove(key).commit()
}
