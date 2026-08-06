package io.github.brandonscollins.yarn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.brandonscollins.yarn.R

/**
 * Paper and ink. Cream page, near-black ink, one antique gold.
 *
 * The gold is deliberately a single value for both modes: it clears 3:1 against cream paper and
 * 4.5:1 against the dark page, so the same accent works either way. Gold is for fills, indicators
 * and large elements; small text is always ink (light) or cream (dark) — see [PaletteContrastTest].
 */
object Palette {
    const val GOLD = 0xFFB8801CL
    const val GOLD_BRIGHT = 0xFFDFAE4AL

    // Light — warm cream paper.
    const val PAPER = 0xFFFAF5EAL
    const val PAPER_BRIGHT = 0xFFFFFDF6L
    const val PAPER_DIM = 0xFFE4D9C2L
    const val PAPER_LOW = 0xFFF7F0E1L
    const val PAPER_CONTAINER = 0xFFF2EAD8L
    const val PAPER_HIGH = 0xFFECE2CDL
    const val PAPER_HIGHEST = 0xFFE6DBC3L
    const val PAPER_VARIANT = 0xFFEDE2CCL
    const val INK = 0xFF1C1714L
    const val INK_SOFT = 0xFF574E43L
    const val INK_FILL = 0xFF2B2320L
    const val OUTLINE_LIGHT = 0xFF8F8271L
    const val OUTLINE_VARIANT_LIGHT = 0xFFDCCFB6L

    // Dark — warm brown-black page, cream ink.
    const val NIGHT = 0xFF17120EL
    const val NIGHT_LOWEST = 0xFF100C09L
    const val NIGHT_LOW = 0xFF1F1913L
    const val NIGHT_CONTAINER = 0xFF241D16L
    const val NIGHT_HIGH = 0xFF2F271EL
    const val NIGHT_HIGHEST = 0xFF3A3128L
    const val NIGHT_INK_FILL = 0xFF332A1BL
    const val CREAM = 0xFFF2E7D2L
    const val CREAM_SOFT = 0xFFCBBDA6L
    const val OUTLINE_DARK = 0xFF8A7A64L
    const val OUTLINE_VARIANT_DARK = 0xFF3F362BL
}

private val LightScheme =
    lightColorScheme(
        primary = Color(Palette.GOLD),
        onPrimary = Color(Palette.INK),
        primaryContainer = Color(0xFFEFDCAF),
        onPrimaryContainer = Color(0xFF3D2A05),
        inversePrimary = Color(0xFFE7C173),
        secondary = Color(0xFF6B5D45),
        onSecondary = Color(0xFFFFFDF7),
        // The "ink fill with a gold glyph" pair from the reference: circle buttons, selected chips
        // and the selected nav pill all pick this up for free.
        secondaryContainer = Color(Palette.INK_FILL),
        onSecondaryContainer = Color(Palette.GOLD_BRIGHT),
        tertiary = Color(0xFF7A4A38),
        onTertiary = Color(0xFFFFF8F0),
        tertiaryContainer = Color(0xFFF3DFD3),
        onTertiaryContainer = Color(0xFF3B1D12),
        background = Color(Palette.PAPER),
        onBackground = Color(Palette.INK),
        surface = Color(Palette.PAPER),
        onSurface = Color(Palette.INK),
        surfaceVariant = Color(Palette.PAPER_VARIANT),
        onSurfaceVariant = Color(Palette.INK_SOFT),
        surfaceBright = Color(Palette.PAPER_BRIGHT),
        surfaceDim = Color(Palette.PAPER_DIM),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(Palette.PAPER_LOW),
        surfaceContainer = Color(Palette.PAPER_CONTAINER),
        surfaceContainerHigh = Color(Palette.PAPER_HIGH),
        surfaceContainerHighest = Color(Palette.PAPER_HIGHEST),
        // Tonal elevation reads as darker paper rather than gold-tinted paper.
        surfaceTint = Color(Palette.INK),
        inverseSurface = Color(0xFF2E2822),
        inverseOnSurface = Color(0xFFF5EDDD),
        error = Color(0xFF8C2F1E),
        onError = Color(0xFFFFF8F0),
        errorContainer = Color(0xFFF7DDD5),
        onErrorContainer = Color(0xFF3F1108),
        outline = Color(Palette.OUTLINE_LIGHT),
        outlineVariant = Color(Palette.OUTLINE_VARIANT_LIGHT),
        scrim = Color(0xFF000000),
    )

private val DarkScheme =
    darkColorScheme(
        primary = Color(Palette.GOLD),
        onPrimary = Color(Palette.NIGHT),
        primaryContainer = Color(0xFF4A3405),
        onPrimaryContainer = Color(0xFFEFC975),
        inversePrimary = Color(0xFF7A5405),
        secondary = Color(0xFFC9B893),
        onSecondary = Color(0xFF302818),
        secondaryContainer = Color(Palette.NIGHT_INK_FILL),
        onSecondaryContainer = Color(Palette.GOLD_BRIGHT),
        tertiary = Color(0xFFDDB0A0),
        onTertiary = Color(0xFF442014),
        tertiaryContainer = Color(0xFF5D3325),
        onTertiaryContainer = Color(0xFFFFDCD0),
        background = Color(Palette.NIGHT),
        onBackground = Color(Palette.CREAM),
        surface = Color(Palette.NIGHT),
        onSurface = Color(Palette.CREAM),
        surfaceVariant = Color(Palette.NIGHT_HIGHEST),
        onSurfaceVariant = Color(Palette.CREAM_SOFT),
        surfaceBright = Color(0xFF3D342A),
        surfaceDim = Color(Palette.NIGHT_LOWEST),
        surfaceContainerLowest = Color(Palette.NIGHT_LOWEST),
        surfaceContainerLow = Color(Palette.NIGHT_LOW),
        surfaceContainer = Color(Palette.NIGHT_CONTAINER),
        surfaceContainerHigh = Color(Palette.NIGHT_HIGH),
        surfaceContainerHighest = Color(Palette.NIGHT_HIGHEST),
        surfaceTint = Color(Palette.CREAM),
        inverseSurface = Color(Palette.CREAM),
        inverseOnSurface = Color(0xFF2E2822),
        error = Color(0xFFF2B8A8),
        onError = Color(0xFF4E1508),
        errorContainer = Color(0xFF6E2213),
        onErrorContainer = Color(0xFFFFDAD1),
        outline = Color(Palette.OUTLINE_DARK),
        outlineVariant = Color(Palette.OUTLINE_VARIANT_DARK),
        scrim = Color(0xFF000000),
    )

/** Lora, one variable file; the weight axis carries Regular → Bold. */
@OptIn(ExperimentalTextApi::class)
private val Serif =
    FontFamily(
        Font(R.font.lora, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.lora, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.lora, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.lora, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

/**
 * Serif for the bookish half — book titles, screen titles, big numerals; the platform sans stays on
 * body and label styles, where it's more legible at small sizes and in the dark.
 */
private val YarnTypography =
    Typography().run {
        copy(
            displayLarge =
                displayLarge.copy(
                    fontFamily = Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 56.sp,
                    lineHeight = 64.sp,
                    letterSpacing = (-0.5).sp,
                ),
            displayMedium =
                displayMedium.copy(
                    fontFamily = Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 44.sp,
                    lineHeight = 52.sp,
                    letterSpacing = (-0.25).sp,
                ),
            displaySmall =
                displaySmall.copy(
                    fontFamily = Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                ),
            headlineLarge = headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp),
            headlineMedium = headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),
            headlineSmall = headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
            titleLarge = titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
            titleMedium = titleMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
            titleSmall = titleSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
        )
    }

/** Generously rounded: pills and soft-cornered cards, nothing sharp. */
private val YarnShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/**
 * Dark by default (PRD: "dark theme default") — deliberately not `isSystemInDarkTheme()`, and
 * deliberately not dynamic color: Yarn's identity is the paper-and-ink palette, not the wallpaper's.
 */
@Composable
fun YarnTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = YarnTypography,
        shapes = YarnShapes,
        content = content,
    )
}
