package com.codro.listenstudy.io

import java.io.ByteArrayOutputStream
import java.io.InputStream

class InputTooLargeException(val maxBytes: Int) : Exception("Input exceeds $maxBytes bytes")

object BoundedInputReader {
    fun read(input: InputStream, maxBytes: Int): ByteArray {
        require(maxBytes >= 0) { "maxBytes must not be negative" }
        val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val remainingWithSentinel = maxBytes - total + 1
            val read = input.read(buffer, 0, minOf(buffer.size, remainingWithSentinel))
            if (read == -1) return output.toByteArray()
            if (total + read > maxBytes) throw InputTooLargeException(maxBytes)
            output.write(buffer, 0, read)
            total += read
        }
    }
}
