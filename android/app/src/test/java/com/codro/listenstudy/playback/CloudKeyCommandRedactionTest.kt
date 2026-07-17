package com.codro.listenstudy.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real-looking Google Cloud API key. If any of these assertions fail, the key is one stray log
 * line or crash report away from leaking.
 */
private const val REAL_LOOKING_KEY = "AIzaSyD-9tSrke72PouQMnMX-a7eZSW0jkFMBWY"

class CloudKeyCommandRedactionTest {
    @Test
    fun `toString does not contain the key`() {
        val rendered = ServiceCommand.SaveApiKey(REAL_LOOKING_KEY, requestId = 7L).toString()

        assertFalse(rendered.contains(REAL_LOOKING_KEY))
        assertFalse(rendered.contains("AIzaSy"))
    }

    @Test
    fun `toString still identifies the command and its request id`() {
        val rendered = ServiceCommand.SaveApiKey(REAL_LOOKING_KEY, requestId = 7L).toString()

        assertTrue(rendered.contains("SaveApiKey"))
        assertTrue(rendered.contains("requestId=7"))
        assertTrue(rendered.contains("<redacted>"))
    }

    @Test
    fun `string interpolation of the command redacts too`() {
        // The common leak shape: someone logs "handling $command" rather than command.value.
        val command: ServiceCommand = ServiceCommand.SaveApiKey(REAL_LOOKING_KEY, requestId = 1L)

        assertFalse("handling $command".contains(REAL_LOOKING_KEY))
    }

    @Test
    fun `redaction does not weaken equality or hashing`() {
        val a = ServiceCommand.SaveApiKey(REAL_LOOKING_KEY, requestId = 3L)
        val b = ServiceCommand.SaveApiKey(REAL_LOOKING_KEY, requestId = 3L)
        val other = ServiceCommand.SaveApiKey("AIzaSyOTHERKEYVALUE-000000000000000000", requestId = 3L)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == other)
    }

    @Test
    fun `the key is still available to the service that must store it`() {
        assertEquals(REAL_LOOKING_KEY, ServiceCommand.SaveApiKey(REAL_LOOKING_KEY, requestId = 1L).value)
    }
}
