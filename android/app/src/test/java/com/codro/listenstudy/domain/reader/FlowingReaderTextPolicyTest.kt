package com.codro.listenstudy.domain.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlowingReaderTextPolicyTest {
    @Test
    fun `sentences flow inline instead of forcing a line break after every sentence`() {
        val layout = FlowingReaderTextPolicy.compose(
            listOf("첫 문장입니다.", "두 번째 문장입니다.", "세 번째 문장입니다."),
        )

        assertEquals("첫 문장입니다. 두 번째 문장입니다. 세 번째 문장입니다.", layout.text)
        assertEquals("첫 문장입니다.", layout.text.substring(layout.ranges[0].start, layout.ranges[0].endExclusive))
        assertEquals("두 번째 문장입니다.", layout.text.substring(layout.ranges[1].start, layout.ranges[1].endExclusive))
    }

    @Test
    fun `line breaks that remain inside source sentence text are preserved`() {
        val layout = FlowingReaderTextPolicy.compose(
            listOf("첫째 줄\n둘째 줄입니다.", "다음 문장입니다."),
        )

        assertEquals("첫째 줄\n둘째 줄입니다. 다음 문장입니다.", layout.text)
    }

    @Test
    fun `tap offset resolves only the exact sentence range and not separator whitespace`() {
        val layout = FlowingReaderTextPolicy.compose(listOf("하나.", "둘."))

        assertEquals(0, layout.sentenceIndexAt(1))
        assertNull(layout.sentenceIndexAt(layout.ranges[0].endExclusive))
        assertEquals(1, layout.sentenceIndexAt(layout.ranges[1].start))
    }
}
