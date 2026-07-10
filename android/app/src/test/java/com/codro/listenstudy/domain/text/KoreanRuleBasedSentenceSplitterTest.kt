package com.codro.listenstudy.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanRuleBasedSentenceSplitterTest {
    private val splitter = KoreanRuleBasedSentenceSplitter()

    @Test
    fun split_keeps_decimal_number_inside_sentence() {
        val sentences = splitter.split("오늘은 3.14에 대해 공부한다. 다음 문장입니다.")
        assertEquals(listOf("오늘은 3.14에 대해 공부한다.", "다음 문장입니다."), sentences.map { it.text })
    }

    @Test
    fun split_keeps_time_inside_sentence() {
        val sentences = splitter.split("오전 10:30에 시작한다. 이후 복습한다.")
        assertEquals(listOf("오전 10:30에 시작한다.", "이후 복습한다."), sentences.map { it.text })
    }

    @Test
    fun split_handles_question_and_exclamation() {
        val sentences = splitter.split("안녕하세요? 반갑습니다! 다시 듣겠습니다.")
        assertEquals(listOf("안녕하세요?", "반갑습니다!", "다시 듣겠습니다."), sentences.map { it.text })
    }

    @Test
    fun split_preserves_offsets_for_highlighting() {
        val text = "  첫 문장입니다. 두 번째 문장입니다."
        val sentences = splitter.split(text)
        assertEquals("첫 문장입니다.", text.substring(sentences[0].startOffset, sentences[0].endOffset))
        assertTrue(sentences[1].startOffset > sentences[0].endOffset)
    }
}
