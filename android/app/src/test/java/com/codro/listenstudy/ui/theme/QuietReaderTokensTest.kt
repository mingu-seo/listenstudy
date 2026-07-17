package com.codro.listenstudy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class QuietReaderTokensTest {
    @Test
    fun `light palette matches approved Quiet Reader colors`() {
        assertEquals(Color(0xFF3D5A50), QuietReaderPalette.LightPrimary)
        assertEquals(Color(0xFFF7F5F0), QuietReaderPalette.LightBackground)
        assertEquals(Color(0xFFFFFFFC), QuietReaderPalette.LightSurface)
        assertEquals(Color(0xFFF2E7C9), QuietReaderPalette.LightReadingCurrent)
        assertEquals(Color(0xFF5B4515), QuietReaderPalette.LightOnReadingCurrent)
    }

    @Test
    fun `reading typography and accessible sizes match specification`() {
        assertEquals(15.sp, QuietReaderType.ReaderFontSize)
        assertEquals(22.sp, QuietReaderType.ReaderLineHeight)
        assertEquals(48.dp, QuietReaderSizes.MinTouchTarget)
        assertEquals(56.dp, QuietReaderSizes.PlayButton)
        assertEquals(720.dp, QuietReaderSizes.ReaderMaxWidth)
        assertEquals(12.dp, QuietReaderSizes.ProgressThumbSize)
    }

    @Test
    fun `shape spacing elevation and motion scales use approved values`() {
        assertEquals(listOf(10.dp, 14.dp, 20.dp, 28.dp), QuietReaderShapes.radiusScale)
        assertEquals(listOf(2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp, 48.dp), QuietReaderSpacing.scale)
        assertEquals(listOf(0.dp, 1.dp, 3.dp, 6.dp), QuietReaderElevation.scale)
        assertEquals(listOf(100, 180, 280), QuietReaderMotion.durationScale)
    }
}
