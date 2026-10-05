package dev.stade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Success     = Color(0xFF22C55E)

private val BrandLigth = Color(0xFF000000)

private val BrandDark  = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary                 = Color(0xFF000000),
    onPrimary               = Color(0xFFFFFFFF),
    primaryContainer        = Color(0xFFE2E2E2),
    onPrimaryContainer      = Color(0xFF000000),
    secondary               = Color(0xFF2E2E2E),
    onSecondary             = Color(0xFFFFFFFF),
    secondaryContainer      = Color(0xFFD8D8D8),
    onSecondaryContainer    = Color(0xFF151515),
    tertiary                = Color(0xFF565656),
    onTertiary              = Color(0xFFFFFFFF),
    tertiaryContainer       = Color(0xFFCFCFCF),
    onTertiaryContainer     = Color(0xFF151515),
    error                   = Color(0xFFB42318),
    onError                 = Color.White,
    errorContainer          = Color(0xFFFEE4E2),
    onErrorContainer        = Color(0xFF7A271A),
    background              = Color(0xFFFFFFFF),
    onBackground            = Color(0xFF0A0A0A),
    surface                 = Color(0xFFFFFFFF),
    onSurface               = Color(0xFF0A0A0A),
    surfaceVariant          = Color(0xFFECECEC),
    onSurfaceVariant        = Color(0xFF4A4A4A),
    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF6F6F6),
    surfaceContainer        = Color(0xFFF0F0F0),
    surfaceContainerHigh    = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    outline                 = Color(0xFFB0B0B0),
    outlineVariant          = Color(0xFFDADADA)
)

private val DarkColors = darkColorScheme(
    primary                 = Color(0xFFFFFFFF),
    onPrimary               = Color(0xFF000000),
    primaryContainer        = Color(0xFF2C2C2C),
    onPrimaryContainer      = Color(0xFFF2F2F2),
    secondary               = Color(0xFFCFCFCF),
    onSecondary             = Color(0xFF000000),
    secondaryContainer      = Color(0xFF333333),
    onSecondaryContainer    = Color(0xFFF2F2F2),
    tertiary                = Color(0xFFAEAEAE),
    onTertiary              = Color(0xFF000000),
    tertiaryContainer       = Color(0xFF3A3A3A),
    onTertiaryContainer     = Color(0xFFF2F2F2),
    error                   = Color(0xFFFDA29B),
    onError                 = Color(0xFF7A271A),
    errorContainer          = Color(0xFF55160C),
    onErrorContainer        = Color(0xFFFEE4E2),
    background              = Color(0xFF000000),
    onBackground            = Color(0xFFF2F2F2),
    surface                 = Color(0xFF121212),
    onSurface               = Color(0xFFF2F2F2),
    surfaceVariant          = Color(0xFF2A2A2A),
    onSurfaceVariant        = Color(0xFFC0C0C0),
    surfaceContainerLowest  = Color(0xFF000000),
    surfaceContainerLow     = Color(0xFF121212),
    surfaceContainer        = Color(0xFF1A1A1A),
    surfaceContainerHigh    = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2E2E2E),
    outline                 = Color(0xFF5A5A5A),
    outlineVariant          = Color(0xFF2E2E2E)
)

private val StadeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val StadeTypography = Typography(
    titleLarge   = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium  = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    bodyLarge    = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge   = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium  = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall   = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
)

object StadeColors {
    val online: Color  = Success
    val offline: Color = Color(0xFF98A2B3)
}

@Composable
fun StadeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val dynamicScheme = resolveDynamicColorScheme(dark)
    val target = dynamicScheme ?: if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = rememberBlendedColorScheme(target),
        shapes      = StadeShapes,
        typography  = StadeTypography,
        content     = content
    )
}

val ColorScheme.BrandColor: Color
    @Composable
    get() {
        val dark = isSystemInDarkTheme()
        val isDynamicEnabled = getDynamicColorEnabled().value

        return if (isDynamicEnabled && isDynamicColorSupported) {
            this.primary
        } else {
            if (dark) BrandDark else BrandLigth
        }
    }
