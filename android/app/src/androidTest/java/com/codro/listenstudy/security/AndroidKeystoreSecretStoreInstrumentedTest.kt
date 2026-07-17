package com.codro.listenstudy.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real AndroidKeyStore round-trip for [AndroidKeystoreSecretStore], run on device/emulator.
 *
 * Uses a dedicated, disposable Keystore alias and a dedicated test SharedPreferences file so it
 * never reads, writes, or deletes any production API key, production alias, or production prefs.
 * Only synthetic fake key strings are used; nothing here is logged.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val secretName = "test_secret_name"
    private val syntheticKey = "SYNTHETIC-FAKE-KEY-abc123-not-a-real-credential"

    private fun newStore(): AndroidKeystoreSecretStore =
        AndroidKeystoreSecretStore(context, TEST_PREFS, TEST_ALIAS)

    private fun testPrefsSnapshot(): Map<String, *> =
        context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE).all

    @Before
    fun setUp() = cleanUpTestState()

    @After
    fun tearDown() = cleanUpTestState()

    @Test
    fun save_then_read_round_trips() {
        val store = newStore()

        store.save(secretName, syntheticKey)

        assertTrue(store.contains(secretName))
        assertEquals(syntheticKey, store.read(secretName))
    }

    @Test
    fun stored_prefs_never_contain_plaintext() {
        newStore().save(secretName, syntheticKey)

        val storedValues = testPrefsSnapshot().values.map { it.toString() }
        assertTrue(storedValues.none { it.contains(syntheticKey) })
    }

    @Test
    fun replace_changes_ciphertext_and_iv() {
        val store = newStore()

        store.save(secretName, syntheticKey)
        val first = testPrefsSnapshot().values.map { it.toString() }.toSet()

        // Re-seal the SAME plaintext: a fresh random GCM IV must yield different stored strings.
        store.replace(secretName, syntheticKey)
        val second = testPrefsSnapshot().values.map { it.toString() }.toSet()

        assertNotEquals("ciphertext/IV must change on replace", first, second)
        assertEquals(syntheticKey, store.read(secretName))
    }

    @Test
    fun delete_removes_payload_and_reports_success() {
        val store = newStore()
        store.save(secretName, syntheticKey)

        assertTrue("delete must confirm durable removal", store.delete(secretName))

        assertFalse(store.contains(secretName))
        assertNull(store.read(secretName))
    }

    @Test
    fun malformed_payload_fails_closed() {
        val store = newStore()
        store.save(secretName, syntheticKey)

        // Corrupt every stored string entry; contains() stays true but read() must fail closed.
        val prefs = context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.forEach { editor.putString(it, "!!!not-valid-base64-or-ciphertext!!!") }
        editor.commit()

        assertTrue(store.contains(secretName))
        assertNull("corrupt payload must not crash or leak", store.read(secretName))
    }

    @Test
    fun migrates_legacy_plaintext_into_secure_store() {
        // Seed a legacy plaintext entry in a dedicated test prefs file.
        context.getSharedPreferences(TEST_LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit().putString(LEGACY_KEY, syntheticKey).commit()

        val store = newStore()
        val legacy = SharedPrefsLegacySecretSource(context, TEST_LEGACY_PREFS, LEGACY_KEY)

        val outcome = SecretMigration(secretName, store, legacy).migrateOnce()

        assertEquals(SecretMigrationOutcome.MIGRATED, outcome)
        assertEquals(syntheticKey, store.read(secretName))
        assertNull("legacy plaintext must be removed after migration", legacy.read())
    }

    private fun cleanUpTestState() {
        // Payload-only cleanup: never touches the production alias or production prefs.
        context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(TEST_LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(TEST_ALIAS)) keyStore.deleteEntry(TEST_ALIAS)
        }
    }

    private companion object {
        const val TEST_PREFS = "test_secure_secrets"
        const val TEST_LEGACY_PREFS = "test_cloud_tts_private"
        const val TEST_ALIAS = "com.codro.listenstudy.test.secret.aes"
        const val LEGACY_KEY = "google_api_key"
    }
}
