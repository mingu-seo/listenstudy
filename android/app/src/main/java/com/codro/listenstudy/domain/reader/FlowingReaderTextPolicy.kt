package com.codro.listenstudy.domain.reader

data class SentenceTextRange(
    val sentenceIndex: Int,
    val start: Int,
    val endExclusive: Int,
)

data class FlowingReaderText(
    val text: String,
    val ranges: List<SentenceTextRange>,
) {
    fun sentenceIndexAt(characterOffset: Int): Int? = ranges
        .firstOrNull { characterOffset >= it.start && characterOffset < it.endExclusive }
        ?.sentenceIndex
}

/** Builds one naturally wrapping text flow while retaining exact sentence ranges for highlighting and taps. */
object FlowingReaderTextPolicy {
    fun compose(sentences: List<String>): FlowingReaderText {
        val text = StringBuilder()
        val ranges = ArrayList<SentenceTextRange>(sentences.size)

        sentences.forEachIndexed { index, sentence ->
            if (sentence.isEmpty()) return@forEachIndexed
            if (text.isNotEmpty() && !text.last().isWhitespace() && !sentence.first().isWhitespace()) {
                text.append(' ')
            }
            val start = text.length
            text.append(sentence)
            ranges += SentenceTextRange(index, start, text.length)
        }

        return FlowingReaderText(text.toString(), ranges)
    }
}
