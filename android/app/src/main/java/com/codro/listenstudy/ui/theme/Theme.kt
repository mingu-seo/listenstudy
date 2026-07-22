package com.codro.listenstudy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object QuietReaderPalette {
    val LightPrimary = Color(0xFF3D5A50)
    val LightOnPrimary = Color.White
    val LightPrimaryContainer = Color(0xFFDCE8E1)
    val LightOnPrimaryContainer = Color(0xFF18372E)
    val LightBackground = Color(0xFFF7F5F0)
    val LightSurface = Color(0xFFFFFFFC)
    val LightSurfaceVariant = Color(0xFFF0EEE8)
    val LightTextPrimary = Color(0xFF1C1B18)
    val LightTextSecondary = Color(0xFF5F5D57)
    val LightOutline = Color(0xFF77746D)
    val LightOutlineSubtle = Color(0xFFE1DED6)
    val LightReadingCurrent = Color(0xFFF2E7C9)
    val LightOnReadingCurrent = Color(0xFF5B4515)
    val LightSuccess = Color(0xFF2F6B4F)
    val LightWarning = Color(0xFF7A570B)
    val LightError = Color(0xFFB3261E)
    val LightErrorContainer = Color(0xFFFFF1F0)

    val DarkBackground = Color(0xFF151613)
    val DarkSurface = Color(0xFF1E201C)
    val DarkSurfaceVariant = Color(0xFF292B26)
    val DarkTextPrimary = Color(0xFFE8E6DF)
    val DarkTextSecondary = Color(0xFFC9C6BD)
    val DarkPrimary = Color(0xFFA7C9BC)
    val DarkOnPrimary = Color(0xFF12372D)
    val DarkPrimaryContainer = Color(0xFF294B41)
    val DarkOnPrimaryContainer = Color(0xFFD2E8DF)
    val DarkOutline = Color(0xFF999B92)
    val DarkOutlineSubtle = Color(0xFF41433D)
    val DarkReadingCurrent = Color(0xFF4A3C1F)
    val DarkOnReadingCurrent = Color(0xFFFFE6A6)
    val DarkSuccess = Color(0xFF9AD5B5)
    val DarkWarning = Color(0xFFF0C36A)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)
}

/**
 * Optional warm sepia palette for Supporters. A light-scheme variant of the Quiet Reader palette:
 * warm paper tones, same structure, contrast pinned by SupporterSepiaThemeTest (AAA body text,
 * AA secondary/highlight). Dark mode keeps the standard dark palette.
 */
object SupporterSepiaPalette {
    val Primary = Color(0xFF6D4F23)
    val OnPrimary = Color.White
    val PrimaryContainer = Color(0xFFEBDDBE)
    val OnPrimaryContainer = Color(0xFF3E2E12)
    val Background = Color(0xFFF3E9D8)
    val Surface = Color(0xFFFAF3E5)
    val SurfaceVariant = Color(0xFFEDE0CB)
    val TextPrimary = Color(0xFF3B2F1E)
    val TextSecondary = Color(0xFF5F5138)
    val Outline = Color(0xFF7C6E52)
    val OutlineSubtle = Color(0xFFDDD0B8)
    val ReadingCurrent = Color(0xFFE7D3A9)
    val OnReadingCurrent = Color(0xFF4A3811)
    val Success = Color(0xFF2F6B4F)
    val Warning = Color(0xFF7A570B)
    val Error = Color(0xFFB3261E)
    val ErrorContainer = Color(0xFFFBEFE9)
}

object QuietReaderType {
    const val ReaderFontSizeSp = 15
    const val ReaderLineHeightSp = 22
    val ReaderFontSize: TextUnit = ReaderFontSizeSp.sp
    val ReaderLineHeight: TextUnit = ReaderLineHeightSp.sp
}

object QuietReaderSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val scale = listOf(xxs, xs, sm, md, lg, xl, xxl, xxxl)
}

object QuietReaderSizes {
    val MinTouchTarget = 48.dp
    val PlayButton = 56.dp
    val ReaderMaxWidth = 720.dp
    val ProgressHeight = 4.dp
    val ProgressThumbSize = 12.dp
}

object QuietReaderElevation {
    val none = 0.dp
    val low = 1.dp
    val dock = 3.dp
    val high = 6.dp
    val scale = listOf(none, low, dock, high)
}

object QuietReaderMotion {
    const val fast = 100
    const val standard = 180
    const val emphasized = 280
    val durationScale = listOf(fast, standard, emphasized)
}

object QuietReaderShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(20.dp)
    val dock = RoundedCornerShape(28.dp)
    val radiusScale = listOf(10.dp, 14.dp, 20.dp, 28.dp)
}

@Immutable
data class ExtendedColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val outlineSubtle: Color,
    val readingCurrent: Color,
    val onReadingCurrent: Color,
    val success: Color,
    val warning: Color,
)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

val MaterialTheme.extendedColors: ExtendedColors
    @Composable get() = LocalExtendedColors.current

private val LightColors = lightColorScheme(
    primary = QuietReaderPalette.LightPrimary,
    onPrimary = QuietReaderPalette.LightOnPrimary,
    primaryContainer = QuietReaderPalette.LightPrimaryContainer,
    onPrimaryContainer = QuietReaderPalette.LightOnPrimaryContainer,
    background = QuietReaderPalette.LightBackground,
    onBackground = QuietReaderPalette.LightTextPrimary,
    surface = QuietReaderPalette.LightSurface,
    onSurface = QuietReaderPalette.LightTextPrimary,
    surfaceVariant = QuietReaderPalette.LightSurfaceVariant,
    onSurfaceVariant = QuietReaderPalette.LightTextSecondary,
    outline = QuietReaderPalette.LightOutline,
    outlineVariant = QuietReaderPalette.LightOutlineSubtle,
    error = QuietReaderPalette.LightError,
    errorContainer = QuietReaderPalette.LightErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = QuietReaderPalette.DarkPrimary,
    onPrimary = QuietReaderPalette.DarkOnPrimary,
    primaryContainer = QuietReaderPalette.DarkPrimaryContainer,
    onPrimaryContainer = QuietReaderPalette.DarkOnPrimaryContainer,
    background = QuietReaderPalette.DarkBackground,
    onBackground = QuietReaderPalette.DarkTextPrimary,
    surface = QuietReaderPalette.DarkSurface,
    onSurface = QuietReaderPalette.DarkTextPrimary,
    surfaceVariant = QuietReaderPalette.DarkSurfaceVariant,
    onSurfaceVariant = QuietReaderPalette.DarkTextSecondary,
    outline = QuietReaderPalette.DarkOutline,
    outlineVariant = QuietReaderPalette.DarkOutlineSubtle,
    error = QuietReaderPalette.DarkError,
    onError = QuietReaderPalette.DarkOnError,
    errorContainer = QuietReaderPalette.DarkErrorContainer,
    onErrorContainer = QuietReaderPalette.DarkOnErrorContainer,
)

private val SepiaColors = lightColorScheme(
    primary = SupporterSepiaPalette.Primary,
    onPrimary = SupporterSepiaPalette.OnPrimary,
    primaryContainer = SupporterSepiaPalette.PrimaryContainer,
    onPrimaryContainer = SupporterSepiaPalette.OnPrimaryContainer,
    background = SupporterSepiaPalette.Background,
    onBackground = SupporterSepiaPalette.TextPrimary,
    surface = SupporterSepiaPalette.Surface,
    onSurface = SupporterSepiaPalette.TextPrimary,
    surfaceVariant = SupporterSepiaPalette.SurfaceVariant,
    onSurfaceVariant = SupporterSepiaPalette.TextSecondary,
    outline = SupporterSepiaPalette.Outline,
    outlineVariant = SupporterSepiaPalette.OutlineSubtle,
    error = SupporterSepiaPalette.Error,
    errorContainer = SupporterSepiaPalette.ErrorContainer,
)

private val ListenStudyTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val ListenStudyShapes = Shapes(
    small = QuietReaderShapes.small,
    medium = QuietReaderShapes.medium,
    large = QuietReaderShapes.large,
)

@Composable
fun ListenStudyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Supporter-only warm sepia option. Callers pass SupporterEntitlementStore.isSepiaThemeActive()
    // so the theme is applied only while the selection is backed by the entitlement. It varies the
    // light scheme; dark mode keeps the standard dark palette for night readability.
    sepiaTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val extended = when {
        darkTheme -> ExtendedColors(
            textPrimary = QuietReaderPalette.DarkTextPrimary,
            textSecondary = QuietReaderPalette.DarkTextSecondary,
            outlineSubtle = QuietReaderPalette.DarkOutlineSubtle,
            readingCurrent = QuietReaderPalette.DarkReadingCurrent,
            onReadingCurrent = QuietReaderPalette.DarkOnReadingCurrent,
            success = QuietReaderPalette.DarkSuccess,
            warning = QuietReaderPalette.DarkWarning,
        )
        sepiaTheme -> ExtendedColors(
            textPrimary = SupporterSepiaPalette.TextPrimary,
            textSecondary = SupporterSepiaPalette.TextSecondary,
            outlineSubtle = SupporterSepiaPalette.OutlineSubtle,
            readingCurrent = SupporterSepiaPalette.ReadingCurrent,
            onReadingCurrent = SupporterSepiaPalette.OnReadingCurrent,
            success = SupporterSepiaPalette.Success,
            warning = SupporterSepiaPalette.Warning,
        )
        else -> ExtendedColors(
            textPrimary = QuietReaderPalette.LightTextPrimary,
            textSecondary = QuietReaderPalette.LightTextSecondary,
            outlineSubtle = QuietReaderPalette.LightOutlineSubtle,
            readingCurrent = QuietReaderPalette.LightReadingCurrent,
            onReadingCurrent = QuietReaderPalette.LightOnReadingCurrent,
            success = QuietReaderPalette.LightSuccess,
            warning = QuietReaderPalette.LightWarning,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = when {
                darkTheme -> DarkColors
                sepiaTheme -> SepiaColors
                else -> LightColors
            },
            typography = ListenStudyTypography,
            shapes = ListenStudyShapes,
            content = content,
        )
    }
}
