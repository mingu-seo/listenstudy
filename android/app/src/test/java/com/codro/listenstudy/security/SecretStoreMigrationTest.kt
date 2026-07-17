package com.codro.listenstudy.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the secret migration/coordinator seams. Production wiring uses
 * [AndroidKeystoreSecretStore], which cannot be instantiated on the JVM, so these tests
 * exercise the migration policy and [com.codro.listenstudy.tts.CloudTtsSettings] behavior
 * against in-memory fakes without weakening production crypto.
 */
class SecretStoreMigrationTest {

    private val secretName = "google_cloud_tts_api_key"
    private val secret = "AIza-super-secret-key-value-123"

    /** In-memory [SecretStore] that stands in for the Keystore-backed implementation. */
    private class FakeSecretStore(
        private val throwOnSave: Boolean = false,
        private val readReturnsNull: Boolean = false,
        private val deleteSucceeds: Boolean = true,
        private val throwOnContains: Boolean = false,
        private val throwOnRead: Boolean = false,
        private val throwOnDelete: Boolean = false,
    ) : SecretStore {
        private val values = mutableMapOf<String, String>()
        var saveCount = 0
            private set
        var deleteCount = 0
            private set

        override fun contains(name: String): Boolean {
            if (throwOnContains) throw IllegalStateException("prefs unavailable")
            return values.containsKey(name)
        }

        override fun read(name: String): String? {
            if (throwOnRead) throw ClassCastException("malformed prefs entry")
            return if (readReturnsNull) null else values[name]
        }

        override fun save(name: String, secret: String) {
            saveCount++
            // Never embed the raw secret in the failure message.
            if (throwOnSave) throw IllegalStateException("keystore encrypt failed")
            values[name] = secret
        }

        override fun replace(name: String, secret: String) = save(name, secret)

        override fun delete(name: String): Boolean {
            deleteCount++
            if (throwOnDelete) throw IllegalStateException("prefs commit failed")
            if (deleteSucceeds) values.remove(name)
            return deleteSucceeds
        }

        // Deliberately never renders the stored secret.
        override fun toString(): String = "FakeSecretStore(entries=${values.size})"
    }

    /** In-memory legacy plaintext source. */
    private class FakeLegacySource(
        private var value: String?,
        private val clearSucceeds: Boolean = true,
        private val throwOnRead: Boolean = false,
        private val throwOnClear: Boolean = false,
    ) : LegacySecretSource {
        var cleared = false
            private set
        var clearAttempts = 0
            private set

        override fun read(): String? {
            if (throwOnRead) throw IllegalStateException("legacy prefs unavailable")
            return value
        }

        override fun clear(): Boolean {
            clearAttempts++
            if (throwOnClear) throw IllegalStateException("legacy commit failed")
            if (clearSucceeds) {
                value = null
                cleared = true
            }
            return clearSucceeds
        }

        fun current(): String? = value
    }

    // ---- SecretMigration policy ----------------------------------------------------------

    @Test
    fun `migration success writes securely and removes legacy`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.MIGRATED, outcome)
        assertEquals(secret, secure.read(secretName))
        assertNull("legacy plaintext must be removed after durable secure write", legacy.current())
        assertTrue(legacy.cleared)
    }

    @Test
    fun `migration failure retains legacy plaintext`() {
        val secure = FakeSecretStore(throwOnSave = true)
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.RETAINED_LEGACY, outcome)
        assertFalse("secure store must not hold a value after a failed save", secure.contains(secretName))
        assertEquals("user must not lose the plaintext key", secret, legacy.current())
        assertFalse(legacy.cleared)
    }

    @Test
    fun `failed rollback after failed save returns a distinct non-success outcome`() {
        val secure = FakeSecretStore(throwOnSave = true, deleteSucceeds = false)
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.ROLLBACK_FAILED, outcome)
        assertEquals("legacy must be retained when rollback cannot be confirmed", secret, legacy.current())
        assertFalse(legacy.cleared)
    }

    @Test
    fun `secure write with failed legacy clear does not report completion`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(secret, clearSucceeds = false)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.SECURE_WRITTEN_LEGACY_RETAINED, outcome)
        assertEquals("secure copy must be written", secret, secure.read(secretName))
        assertEquals("plaintext must remain until its removal is durably confirmed", secret, legacy.current())
    }

    @Test
    fun `contains failure stops migration without any writes deletes or clears`() {
        val secure = FakeSecretStore(throwOnContains = true)
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.STORAGE_UNAVAILABLE, outcome)
        assertEquals("no secure save on undetermined state", 0, secure.saveCount)
        assertEquals("no secure delete on undetermined state", 0, secure.deleteCount)
        assertEquals("no legacy clear on undetermined state", 0, legacy.clearAttempts)
        assertEquals("legacy must be retained", secret, legacy.current())
    }

    @Test
    fun `existing readable secure equal to legacy cleans up the stale legacy`() {
        val secure = FakeSecretStore().apply { save(secretName, secret) }
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.ALREADY_SECURE, outcome)
        assertEquals(secret, secure.read(secretName))
        assertNull("legacy matching secure is safely cleared", legacy.current())
    }

    @Test
    fun `existing secure conflicting with nonblank legacy retains legacy and never overwrites`() {
        val secure = FakeSecretStore().apply { save(secretName, "already-secure-value") }
        val staleLegacy = FakeLegacySource("different-stale-plaintext")

        val outcome = SecretMigration(secretName, secure, staleLegacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.SECURE_LEGACY_CONFLICT, outcome)
        assertEquals("secure value must not be overwritten", "already-secure-value", secure.read(secretName))
        assertEquals("conflicting legacy must be retained, never used", "different-stale-plaintext", staleLegacy.current())
        assertEquals("no save on conflict", 1, secure.saveCount) // only the test's own setup save
    }

    @Test
    fun `existing secure equal legacy but failed clear reports a secure-ready non-success`() {
        val secure = FakeSecretStore().apply { save(secretName, secret) }
        val legacy = FakeLegacySource(secret, clearSucceeds = false)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.SECURE_WRITTEN_LEGACY_RETAINED, outcome)
        assertEquals("secure value must be intact", secret, secure.read(secretName))
        assertEquals("legacy retained until removal is durably confirmed", secret, legacy.current())
    }

    @Test
    fun `existing secure equal legacy with clear exception reports a secure-ready non-success`() {
        val secure = FakeSecretStore().apply { save(secretName, secret) }
        val legacy = FakeLegacySource(secret, throwOnClear = true)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.SECURE_WRITTEN_LEGACY_RETAINED, outcome)
        assertEquals(secret, secure.read(secretName))
        assertEquals(secret, legacy.current())
    }

    @Test
    fun `legacy read failure with existing secure stops with storage unavailable and no destructive ops`() {
        val secure = FakeSecretStore().apply { save(secretName, secret) }
        val legacy = FakeLegacySource(secret, throwOnRead = true)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.STORAGE_UNAVAILABLE, outcome)
        assertEquals("no legacy clear on legacy read failure", 0, legacy.clearAttempts)
        assertEquals("no secure delete on legacy read failure", 0, secure.deleteCount)
        assertEquals("no secure save beyond setup on legacy read failure", 1, secure.saveCount)
        assertEquals(secret, legacy.current())
    }

    @Test
    fun `legacy read failure with no secure stops with storage unavailable and no destructive ops`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(secret, throwOnRead = true)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.STORAGE_UNAVAILABLE, outcome)
        assertEquals("no secure save on legacy read failure", 0, secure.saveCount)
        assertEquals("no secure delete on legacy read failure", 0, secure.deleteCount)
        assertEquals("no legacy clear on legacy read failure", 0, legacy.clearAttempts)
        assertEquals("legacy plaintext must be retained", secret, legacy.current())
    }

    @Test
    fun `present blank legacy that cannot be cleared does not report complete cleanup`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource("   ", clearSucceeds = false)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.LEGACY_CLEAR_FAILED, outcome)
        assertEquals("no secure data written for blank legacy", 0, secure.saveCount)
    }

    @Test
    fun `absent legacy requires no clear attempt`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(null)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.NO_LEGACY, outcome)
        assertEquals("truly absent legacy needs no clear", 0, legacy.clearAttempts)
    }

    @Test
    fun `existing secure with blank legacy is already secure`() {
        val secure = FakeSecretStore().apply { save(secretName, secret) }
        val legacy = FakeLegacySource("   ")

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.ALREADY_SECURE, outcome)
        assertEquals(secret, secure.read(secretName))
    }

    @Test
    fun `existing but unreadable secure data retains legacy and fails closed`() {
        val secure = FakeSecretStore(readReturnsNull = true).apply {
            // Simulate a stored-but-corrupt/invalidated payload: contains() true, read() null.
            save(secretName, "will-not-be-decryptable")
        }
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.SECURE_UNREADABLE, outcome)
        assertEquals("legacy is retained but never used when secure payload exists", secret, legacy.current())
        assertFalse(legacy.cleared)
    }

    @Test
    fun `read failure on existing secure retains legacy and fails closed`() {
        val secure = FakeSecretStore(throwOnRead = true).apply { save(secretName, "payload") }
        val legacy = FakeLegacySource(secret)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.SECURE_UNREADABLE, outcome)
        assertEquals(secret, legacy.current())
        assertEquals("no legacy clear when secure is unreadable", 0, legacy.clearAttempts)
    }

    @Test
    fun `blank legacy value creates no secure key`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource("   ")

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.NO_LEGACY, outcome)
        assertFalse(secure.contains(secretName))
        assertEquals(0, secure.saveCount)
    }

    @Test
    fun `absent legacy value creates no secure key`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(null)

        val outcome = SecretMigration(secretName, secure, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.NO_LEGACY, outcome)
        assertFalse(secure.contains(secretName))
    }

    @Test
    fun `migration never leaks the secret in a thrown exception message`() {
        val secure = FakeSecretStore(throwOnSave = true)
        val legacy = FakeLegacySource(secret)

        val outcome = try {
            SecretMigration(secretName, secure, legacy).migrateOnce()
        } catch (error: Throwable) {
            throw AssertionError("migration must not throw: ${error.javaClass.simpleName}", error)
        }

        assertEquals(SecretMigrationOutcome.RETAINED_LEGACY, outcome)
        assertFalse("store toString must not leak the secret", secure.toString().contains(secret))
    }

    @Test
    fun `migration swallows storage exceptions from every seam without leaking secret`() {
        val secure = FakeSecretStore(throwOnContains = true, throwOnRead = true, throwOnDelete = true)
        val legacy = FakeLegacySource(secret, throwOnRead = true, throwOnClear = true)

        val outcome = try {
            SecretMigration(secretName, secure, legacy).migrateOnce()
        } catch (error: Throwable) {
            throw AssertionError("migration must not throw on storage failures: ${error.message}", error)
        }

        // contains() threw -> undetermined state -> stop, retain legacy, no destructive ops.
        assertEquals(SecretMigrationOutcome.STORAGE_UNAVAILABLE, outcome)
    }

    // ---- CloudTtsSettings behavior over the secure store -----------------------------------

    @Test
    fun `settings save trims and persists to the secure store`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(null)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertTrue(settings.saveApiKey("  $secret  "))

        assertTrue(settings.hasApiKey())
        assertEquals(secret, settings.apiKey())
        assertEquals(secret, secure.read(secretName))
    }

    @Test
    fun `settings ignores blank save`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(null)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertFalse(settings.saveApiKey("    "))

        assertFalse(settings.hasApiKey())
        assertFalse(secure.contains(secretName))
    }

    @Test
    fun `settings save reports failure when secure store cannot persist`() {
        val secure = FakeSecretStore(throwOnSave = true, readReturnsNull = true)
        val legacy = FakeLegacySource(null)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertFalse("save must not falsely report success", settings.saveApiKey(secret))
    }

    @Test
    fun `settings save reports failure when legacy clear does not commit`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(secret, clearSucceeds = false)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        // Secure write succeeds, but legacy plaintext could not be removed -> caller sees failure.
        assertFalse(settings.saveApiKey("brand-new-key"))
        // Secure copy is nonetheless present, so the key is usable.
        assertTrue(settings.hasApiKey())
    }

    @Test
    fun `settings save reports failure when legacy clear throws`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(secret, throwOnClear = true)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertFalse(settings.saveApiKey("brand-new-key"))
    }

    @Test
    fun `settings replace overwrites the stored secret`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(null)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        settings.saveApiKey("first-key")
        settings.saveApiKey("second-key")

        assertEquals("second-key", settings.apiKey())
    }

    @Test
    fun `settings delete removes both secure and legacy`() {
        val secure = FakeSecretStore().apply { save(secretName, secret) }
        val legacy = FakeLegacySource(secret)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertTrue(settings.deleteApiKey())

        assertFalse(settings.hasApiKey())
        assertFalse(secure.contains(secretName))
        assertNull(legacy.current())
    }

    @Test
    fun `settings delete reports failure when secure delete does not commit`() {
        val secure = FakeSecretStore(deleteSucceeds = false).apply { save(secretName, secret) }
        val legacy = FakeLegacySource(null)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertFalse("delete must not falsely report success", settings.deleteApiKey())
        assertTrue("key remains until deletion is durable", settings.hasApiKey())
    }

    @Test
    fun `settings migrates legacy plaintext on construction`() {
        val secure = FakeSecretStore()
        val legacy = FakeLegacySource(secret)

        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertEquals(secret, settings.apiKey())
        assertEquals(secret, secure.read(secretName))
        assertNull("legacy plaintext removed after migration", legacy.current())
    }

    @Test
    fun `settings falls back to retained legacy only when no secure payload exists`() {
        val secure = FakeSecretStore(throwOnSave = true, readReturnsNull = true)
        val legacy = FakeLegacySource(secret)

        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        // Migration could not persist securely (no secure payload), so the retained plaintext is usable.
        assertEquals(secret, settings.apiKey())
        assertEquals(secret, legacy.current())
    }

    @Test
    fun `settings fails closed when secure payload exists but is unreadable`() {
        val secure = FakeSecretStore(readReturnsNull = true).apply { save(secretName, "corrupt-payload") }
        val staleLegacy = FakeLegacySource("stale-plaintext-value")
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, staleLegacy)

        // Corrupt/unreadable secure data must NOT resurrect stale legacy plaintext.
        assertFalse(settings.hasApiKey())
        assertEquals("", settings.apiKey())
        assertEquals("stale legacy must not be used or overwritten", "stale-plaintext-value", staleLegacy.current())
    }

    @Test
    fun `settings apiKey fails closed when contains throws`() {
        val secure = FakeSecretStore(throwOnContains = true)
        val legacy = FakeLegacySource(secret)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertEquals("", settings.apiKey())
        assertFalse(settings.hasApiKey())
    }

    @Test
    fun `settings apiKey fails closed when secure read throws`() {
        val secure = FakeSecretStore(throwOnRead = true).apply { save(secretName, "payload") }
        val legacy = FakeLegacySource(secret)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        // Secure payload exists (contains true) but read throws -> blank, never legacy.
        assertEquals("", settings.apiKey())
        assertFalse(settings.hasApiKey())
    }

    @Test
    fun `settings apiKey uses legacy only when contains succeeds and is false`() {
        // Secure save fails so migration retains legacy and no secure payload exists (contains == false).
        val secure = FakeSecretStore(throwOnSave = true)
        val legacy = FakeLegacySource(secret)
        val settings = com.codro.listenstudy.tts.CloudTtsSettings(secretName, secure, legacy)

        assertFalse(secure.contains(secretName))
        assertEquals(secret, settings.apiKey())
    }
}
