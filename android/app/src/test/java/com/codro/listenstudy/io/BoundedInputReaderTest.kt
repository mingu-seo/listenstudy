package com.codro.listenstudy.io

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BoundedInputReaderTest {
    @Test
    fun `reads a stream whose size is exactly the limit`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(bytes, BoundedInputReader.read(ByteArrayInputStream(bytes), maxBytes = 4))
    }

    @Test
    fun `rejects a stream as soon as it exceeds the limit`() {
        val bytes = ByteArray(5) { it.toByte() }

        try {
            BoundedInputReader.read(ByteArrayInputStream(bytes), maxBytes = 4)
            fail("Expected InputTooLargeException")
        } catch (error: InputTooLargeException) {
            assertEquals(4, error.maxBytes)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a negative limit`() {
        BoundedInputReader.read(ByteArrayInputStream(byteArrayOf()), maxBytes = -1)
    }
}
