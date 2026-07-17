package com.codro.listenstudy.domain.text

data class SentenceSpan(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

interface SentenceSplitter {
    fun split(text: String): List<SentenceSpan>
}

class KoreanRuleBasedSentenceSplitter : SentenceSplitter {
    override fun split(text: String): List<SentenceSpan> {
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<SentenceSpan>()
        var start = 0
        var index = 0
        var i = 0

        fun appendSentence(endExclusive: Int) {
            val raw = text.substring(start, endExclusive)
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty()) {
                val leadingSpaces = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val trailingSpaces = raw.length - raw.indexOfLast { !it.isWhitespace() } - 1
                result += SentenceSpan(
                    index = index++,
                    // Keep the exact source segment for the reader UI. Playback trims only
                    // the outer layout whitespace immediately before sending text to TTS.
                    text = raw,
                    startOffset = start + leadingSpaces,
                    endOffset = endExclusive - trailingSpaces,
                )
                start = endExclusive
            } else if (endExclusive == text.length && result.isNotEmpty()) {
                // A source that ends in blank lines has no following sentence to own them.
                // Keep those characters on the final segment instead of dropping them.
                result[result.lastIndex] = result.last().copy(text = result.last().text + raw)
                start = endExclusive
            }
            // For whitespace-only separators between sentences, keep `start` unchanged so
            // the next non-empty source segment includes every original line break.
        }

        while (i < text.length) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            val prev = text.getOrNull(i - 1)

            val shouldSplit = when {
                ch == '\n' -> next == '\n' || looksLikeNextListItem(text, i + 1)
                ch == '.' && prev?.isDigit() == true && next?.isDigit() == true -> false
                ch == ':' && prev?.isDigit() == true && next?.isDigit() == true -> false
                ch == '.' && next == '.' -> false
                ch == '.' || ch == '?' || ch == '!' || ch == '。' || ch == '？' || ch == '！' -> true
                else -> false
            }

            if (shouldSplit) {
                var end = i + 1
                while (text.getOrNull(end) in listOf('.', '?', '!', '。', '？', '！')) end++
                while (text.getOrNull(end)?.isWhitespace() == true && text.getOrNull(end) != '\n') end++
                appendSentence(end)
                i = end
            } else {
                i++
            }
        }

        if (start < text.length) appendSentence(text.length)
        return result
    }

    private fun looksLikeNextListItem(text: String, position: Int): Boolean {
        var cursor = position
        while (text.getOrNull(cursor)?.isWhitespace() == true) cursor++
        return text.getOrNull(cursor)?.isDigit() == true && text.getOrNull(cursor + 1) == '.'
    }
}
