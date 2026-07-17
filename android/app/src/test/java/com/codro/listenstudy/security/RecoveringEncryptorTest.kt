package com.codro.listenstudy.security

import java.security.GeneralSecurityException
import java.security.ProviderException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Pure JVM tests for the SAVE-path key recovery seam. The real [AndroidKeystoreCrypto] cannot
 * run on the JVM, so recovery semantics are verified against an in-memory [KeystoreCrypto] fake.
 *
 * Recovery is deliberately narrow: only a [PermanentlyUnavailableKeyException] (a key proven
 * permanently unusable for encryption) triggers a one-time key regeneration. Regenerating cannot
 * make an already-permanently-unreadable old ciphertext any worse, whereas a transient
 * cipher/provider error must preserve the existing alias/ciphertext and simply propagate.
 */
class RecoveringEncryptorTest {

    /**
     * Fake crypto that fails encryption a configurable number of times before succeeding, and
     * counts key regenerations so we can assert a single, non-looping recovery.
     */
    private class FakeCrypto(
        private var failuresBeforeSuccess: Int,
        private val failureFactory: () -> Throwable,
    ) : KeystoreCrypto {
        var encryptAttempts = 0
            private set
        var regenerations = 0
            private set

        override fun encrypt(aad: ByteArray, plaintext: ByteArray): SealedSecret {
            encryptAttempts++
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                throw failureFactory()
            }
            return SealedSecret(iv = byteArrayOf(1, 2, 3), ciphertext = plaintext.reversedArray())
        }

        override fun decrypt(aad: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray =
            ciphertext.reversedArray()

        override fun regenerateKey() {
            regenerations++
        }
    }

    private val aad = "google_cloud_tts_api_key".toByteArray()
    private val plaintext = "synthetic-key".toByteArray()

    private fun permanent(): Throwable = PermanentlyUnavailableKeyException(GeneralSecurityException("invalidated"))

    @Test
    fun `encrypts directly when the key is usable`() {
        val crypto = FakeCrypto(failuresBeforeSuccess = 0, failureFactory = { permanent() })

        val sealed = RecoveringEncryptor(crypto).seal(aad, plaintext)

        assertArrayEquals(plaintext.reversedArray(), sealed.ciphertext)
        assertEquals(0, crypto.regenerations)
        assertEquals(1, crypto.encryptAttempts)
    }

    @Test
    fun `recovers exactly once from a permanently unusable key`() {
        val crypto = FakeCrypto(failuresBeforeSuccess = 1, failureFactory = { permanent() })

        val sealed = RecoveringEncryptor(crypto).seal(aad, plaintext)

        assertArrayEquals(plaintext.reversedArray(), sealed.ciphertext)
        assertEquals("must regenerate the key exactly once", 1, crypto.regenerations)
        assertEquals("one failed attempt plus one retry", 2, crypto.encryptAttempts)
    }

    @Test
    fun `does not regenerate on a generic GeneralSecurityException`() {
        val crypto = FakeCrypto(failuresBeforeSuccess = 1, failureFactory = { GeneralSecurityException("transient") })

        assertThrows(GeneralSecurityException::class.java) {
            RecoveringEncryptor(crypto).seal(aad, plaintext)
        }

        assertEquals("transient crypto errors must preserve the alias/ciphertext", 0, crypto.regenerations)
        assertEquals(1, crypto.encryptAttempts)
    }

    @Test
    fun `does not regenerate on a generic ProviderException`() {
        val crypto = FakeCrypto(failuresBeforeSuccess = 1, failureFactory = { ProviderException("transient provider") })

        assertThrows(ProviderException::class.java) {
            RecoveringEncryptor(crypto).seal(aad, plaintext)
        }

        assertEquals(0, crypto.regenerations)
        assertEquals(1, crypto.encryptAttempts)
    }

    @Test
    fun `does not loop when recovery also fails`() {
        val crypto = FakeCrypto(failuresBeforeSuccess = 99, failureFactory = { permanent() })

        assertThrows(PermanentlyUnavailableKeyException::class.java) {
            RecoveringEncryptor(crypto).seal(aad, plaintext)
        }

        assertEquals("recovery must be attempted once, not in a loop", 1, crypto.regenerations)
        assertEquals("exactly one initial attempt plus one retry", 2, crypto.encryptAttempts)
    }
}
