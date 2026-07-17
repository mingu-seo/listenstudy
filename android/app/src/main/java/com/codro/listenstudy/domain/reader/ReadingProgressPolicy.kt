package com.codro.listenstudy.domain.reader

import kotlin.math.roundToInt

/** Pure display policy shared by reader and library progress UI. */
object ReadingProgressPolicy {
    fun currentPosition(currentIndex: Int, sentenceCount: Int): Int {
        if (sentenceCount <= 0) return 0
        return currentIndex.coerceIn(0, sentenceCount - 1) + 1
    }

    fun fraction(currentIndex: Int, sentenceCount: Int): Float {
        if (sentenceCount <= 0) return 0f
        return currentPosition(currentIndex, sentenceCount).toFloat() / sentenceCount
    }

    fun percent(currentIndex: Int, sentenceCount: Int): Int =
        (fraction(currentIndex, sentenceCount) * 100).roundToInt()
}
