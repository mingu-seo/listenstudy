package com.codro.listenstudy.security

import java.security.GeneralSecurityException

/**
 * Authenticated persistence for a single secret string, keyed by a stable [name].
 *
 * Implementations must never log the raw secret, embed it in exception messages, or expose
 * it through [toString]. A missing, corrupt, or otherwise undecryptable payload must fail
 * closed by returning `null` from [read] rather than throwing with any recoverable data.
 */
interface SecretStore {
    /** True when an encrypted payload is stored for [name], regardless of decryptability. */
    fun contains(name: String): Boolean

    /** Decrypts and returns the secret, or `null` if absent/corrupt/undecryptable. */
    fun read(name: String): String?

    /** Encrypts and durably stores [secret] under [name], replacing any prior value. */
    fun save(name: String, secret: String)

    /** Alias for [save]; overwrites any existing value for [name]. */
    fun replace(name: String, secret: String)

    /** Removes any stored payload for [name]. Returns true only if the removal is durable. */
    fun delete(name: String): Boolean
}

/**
 * Legacy plaintext source that a one-time migration reads from and then clears. Kept as a
 * seam so the migration policy is testable without Android SharedPreferences.
 */
interface LegacySecretSource {
    /** Returns the raw legacy plaintext value, or `null` if none is stored. */
    fun read(): String?

    /** Removes the legacy plaintext value. Returns true only if the removal is durable. */
    fun clear(): Boolean
}

/** IV/nonce and ciphertext produced by an authenticated [KeystoreCrypto.encrypt]. */
class SealedSecret(val iv: ByteArray, val ciphertext: ByteArray)

/**
 * Signals that the Keystore key is *permanently* unusable for encryption (e.g. permanently
 * invalidated, or an unrecoverable key/entry). Only this condition justifies destroying and
 * regenerating the alias during a SAVE. The message is static and carries no secret; the cause
 * chain holds only crypto exceptions.
 */
class PermanentlyUnavailableKeyException(cause: Throwable?) :
    GeneralSecurityException("Keystore key is permanently unusable for encryption", cause)

/**
 * Authenticated encryption seam over a single Keystore-resident key. Kept as an interface so the
 * SAVE-path key-recovery policy ([RecoveringEncryptor]) is testable without a real Keystore.
 */
interface KeystoreCrypto {
    /**
     * Encrypts [plaintext] under the current key, creating one if absent. Throws
     * [PermanentlyUnavailableKeyException] only when the existing key is permanently unusable;
     * any other (transient) failure propagates unchanged so the alias/ciphertext are preserved.
     */
    fun encrypt(aad: ByteArray, plaintext: ByteArray): SealedSecret

    /** Decrypts [ciphertext]. Throws on any failure; callers must fail closed, never recover here. */
    fun decrypt(aad: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray

    /** Deletes the current key alias and generates a fresh non-exportable key. */
    fun regenerateKey()
}

/**
 * Wraps [KeystoreCrypto.encrypt] with a single, non-looping recovery that fires *only* for a
 * [PermanentlyUnavailableKeyException]: delete the alias, generate a fresh key, retry once.
 *
 * Rationale: if the current key is permanently unusable for encryption, then any old ciphertext it
 * produced is already permanently undecryptable — regenerating cannot make recoverability worse,
 * and it restores the ability to store a fresh secret. A transient cipher/provider error is NOT
 * caught here, so the alias and existing ciphertext are preserved and the (static, non-secret)
 * exception propagates. Recovery is never applied on decrypt.
 */
class RecoveringEncryptor(private val crypto: KeystoreCrypto) {
    fun seal(aad: ByteArray, plaintext: ByteArray): SealedSecret =
        try {
            crypto.encrypt(aad, plaintext)
        } catch (permanent: PermanentlyUnavailableKeyException) {
            crypto.regenerateKey()
            crypto.encrypt(aad, plaintext)
        }
}

/** Observable result of [SecretMigration.migrateOnce], used for testing and diagnostics. */
enum class SecretMigrationOutcome {
    /** No usable legacy plaintext existed; nothing was migrated. */
    NO_LEGACY,

    /** Secure data already existed and legacy (if any) is consistent with it. */
    ALREADY_SECURE,

    /** Legacy plaintext was securely stored, verified, and then durably removed. */
    MIGRATED,

    /** A secure copy is present and verified, but the legacy plaintext could not be cleared yet. */
    SECURE_WRITTEN_LEGACY_RETAINED,

    /** A present (blank) legacy entry could not be durably cleared; no secret or secure data involved. */
    LEGACY_CLEAR_FAILED,

    /** Secure write/verify failed but rollback was confirmed; legacy plaintext retained. */
    RETAINED_LEGACY,

    /** Secure write/verify failed and the rollback delete could not be confirmed. */
    ROLLBACK_FAILED,

    /** A readable secure value conflicts with a nonblank legacy value; legacy retained, never used. */
    SECURE_LEGACY_CONFLICT,

    /** A secure payload exists but cannot be read; fail closed and retain legacy. */
    SECURE_UNREADABLE,

    /** The secure store could not report its state (contains threw); nothing was touched. */
    STORAGE_UNAVAILABLE,
}

/**
 * One-time, idempotent, restart-safe migration from a legacy plaintext [LegacySecretSource] to a
 * Keystore-backed [SecretStore].
 *
 * Invariants:
 * - If the secure store cannot report whether a payload exists, nothing is written/deleted/cleared.
 * - Existing secure data is never overwritten from a legacy value; legacy plaintext is dropped only
 *   when a readable secure value equals the trimmed legacy value.
 * - A conflicting or unreadable secure payload fails closed and retains (but never uses) legacy.
 * - Legacy plaintext is removed only after a durable secure write is verified readable AND its own
 *   removal is durably confirmed; [MIGRATED] is reported only then.
 * - Every storage/crypto call is guarded: no exception (secret-bearing or otherwise) escapes.
 */
class SecretMigration(
    private val secretName: String,
    private val secure: SecretStore,
    private val legacy: LegacySecretSource,
) {
    fun migrateOnce(): SecretMigrationOutcome {
        val contains = runCatching { secure.contains(secretName) }
        if (contains.isFailure) {
            // Undetermined secure state: touch nothing, keep legacy for a later retry.
            return SecretMigrationOutcome.STORAGE_UNAVAILABLE
        }

        if (contains.getOrDefault(false)) {
            return reconcileExistingSecure()
        }

        return migrateFromLegacy()
    }

    private fun reconcileExistingSecure(): SecretMigrationOutcome {
        val secureValue = runCatching { secure.read(secretName) }.getOrNull()
            ?: return SecretMigrationOutcome.SECURE_UNREADABLE // fail closed, retain legacy

        // A legacy READ failure is not "absent": we cannot know the value, so touch nothing.
        val legacyRead = runCatching { legacy.read() }
        if (legacyRead.isFailure) return SecretMigrationOutcome.STORAGE_UNAVAILABLE
        val legacyValue = legacyRead.getOrNull()?.trim().orEmpty()
        if (legacyValue.isBlank()) {
            return SecretMigrationOutcome.ALREADY_SECURE
        }

        // Nonblank legacy alongside readable secure: only drop legacy when it matches secure, and
        // only claim ALREADY_SECURE when that removal is durably confirmed.
        return if (legacyValue == secureValue) {
            if (runCatching { legacy.clear() }.getOrDefault(false)) {
                SecretMigrationOutcome.ALREADY_SECURE
            } else {
                SecretMigrationOutcome.SECURE_WRITTEN_LEGACY_RETAINED
            }
        } else {
            // Conflict: never overwrite secure, never use legacy, keep legacy for inspection/retry.
            SecretMigrationOutcome.SECURE_LEGACY_CONFLICT
        }
    }

    private fun migrateFromLegacy(): SecretMigrationOutcome {
        // A legacy READ failure is not "absent": cannot determine the value, so touch nothing.
        val legacyRead = runCatching { legacy.read() }
        if (legacyRead.isFailure) return SecretMigrationOutcome.STORAGE_UNAVAILABLE
        val rawLegacy = legacyRead.getOrNull()
        val plaintext = rawLegacy?.trim().orEmpty()
        if (plaintext.isBlank()) {
            // Truly absent (null): nothing to clean. Present-but-blank: tidy up, but never claim a
            // complete result if the removal cannot be confirmed.
            if (rawLegacy == null) return SecretMigrationOutcome.NO_LEGACY
            return if (runCatching { legacy.clear() }.getOrDefault(false)) {
                SecretMigrationOutcome.NO_LEGACY
            } else {
                SecretMigrationOutcome.LEGACY_CLEAR_FAILED
            }
        }

        val verified = runCatching {
            secure.save(secretName, plaintext)
            secure.read(secretName) == plaintext
        }.getOrDefault(false)

        if (!verified) {
            // Roll back any partial secure write and keep the plaintext. Never rethrow: an
            // exception could carry the secret. If rollback cannot be confirmed, report it so a
            // later run reconciles via the conflict/unreadable paths rather than clearing legacy.
            val rolledBack = runCatching { secure.delete(secretName) }.getOrDefault(false)
            return if (rolledBack) {
                SecretMigrationOutcome.RETAINED_LEGACY
            } else {
                SecretMigrationOutcome.ROLLBACK_FAILED
            }
        }

        // Secure copy is durable and verified. Only claim completion once the plaintext is durably
        // gone; otherwise a later run (ALREADY_SECURE equal-value path) retries the clear.
        val cleared = runCatching { legacy.clear() }.getOrDefault(false)
        return if (cleared) {
            SecretMigrationOutcome.MIGRATED
        } else {
            SecretMigrationOutcome.SECURE_WRITTEN_LEGACY_RETAINED
        }
    }
}
