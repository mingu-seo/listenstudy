package com.codro.listenstudy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optional warm sepia Supporter theme must stay readable: WCAG contrast is pinned here so a
 * palette tweak can never quietly ship an unreadable reader. The theme is a light-scheme variant;
 * dark mode keeps the standard Quiet Reader dark palette.
 */
class SupporterSepiaThemeTest {

    private fun contrast(foreground: Color, background: Color): Double {
        val lighter = maxOf(foreground.luminance(), background.luminance()).toDouble()
        val darker = minOf(foreground.luminance(), background.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `sepia palette matches the approved warm tokens`() {
        assertEquals(Color(0xFFF3E9D8), SupporterSepiaPalette.Background)
        assertEquals(Color(0xFFFAF3E5), SupporterSepiaPalette.Surface)
        assertEquals(Color(0xFF3B2F1E), SupporterSepiaPalette.TextPrimary)
        assertEquals(Color(0xFFE7D3A9), SupporterSepiaPalette.ReadingCurrent)
    }

    @Test
    fun `sepia body text keeps AAA contrast on background and surface`() {
        assertTrue(contrast(SupporterSepiaPalette.TextPrimary, SupporterSepiaPalette.Background) >= 7.0)
        assertTrue(contrast(SupporterSepiaPalette.TextPrimary, SupporterSepiaPalette.Surface) >= 7.0)
    }

    @Test
    fun `sepia secondary text and current-sentence highlight keep AA contrast`() {
        assertTrue(contrast(SupporterSepiaPalette.TextSecondary, SupporterSepiaPalette.Background) >= 4.5)
        assertTrue(contrast(SupporterSepiaPalette.TextSecondary, SupporterSepiaPalette.Surface) >= 4.5)
        assertTrue(contrast(SupporterSepiaPalette.OnReadingCurrent, SupporterSepiaPalette.ReadingCurrent) >= 4.5)
    }

    @Test
    fun `sepia primary action color keeps AA contrast against its content color`() {
        assertTrue(contrast(SupporterSepiaPalette.OnPrimary, SupporterSepiaPalette.Primary) >= 4.5)
    }
}
