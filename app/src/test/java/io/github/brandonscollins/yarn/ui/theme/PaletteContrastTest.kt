package io.github.brandonscollins.yarn.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The palette's one real rule: text pairs clear WCAG AA (4.5:1) and the gold clears the 3:1
 * non-text threshold against both pages. Gold-on-cream small text fails by design — this test is
 * what stops someone "fixing" the palette into unreadability.
 */
class PaletteContrastTest {
    @Test
    fun textPairsClearAa() {
        val pairs =
            listOf(
                "ink on paper" to (Palette.INK to Palette.PAPER),
                "ink-soft on paper" to (Palette.INK_SOFT to Palette.PAPER),
                "ink on paper container" to (Palette.INK to Palette.PAPER_CONTAINER),
                "ink on gold" to (Palette.INK to Palette.GOLD),
                "gold-bright on ink fill" to (Palette.GOLD_BRIGHT to Palette.INK_FILL),
                "cream on night" to (Palette.CREAM to Palette.NIGHT),
                "cream-soft on night" to (Palette.CREAM_SOFT to Palette.NIGHT),
                "cream on night container" to (Palette.CREAM to Palette.NIGHT_CONTAINER),
                "night on gold" to (Palette.NIGHT to Palette.GOLD),
                "gold-bright on night ink fill" to (Palette.GOLD_BRIGHT to Palette.NIGHT_INK_FILL),
            )
        pairs.forEach { (name, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue("$name is $ratio:1, below AA 4.5", ratio >= 4.5)
        }
    }

    @Test
    fun goldAndOutlinesClearNonTextThreshold() {
        val pairs =
            listOf(
                "gold on paper" to (Palette.GOLD to Palette.PAPER),
                "gold on night" to (Palette.GOLD to Palette.NIGHT),
                "outline on paper" to (Palette.OUTLINE_LIGHT to Palette.PAPER),
                "outline on night" to (Palette.OUTLINE_DARK to Palette.NIGHT),
            )
        pairs.forEach { (name, colors) ->
            val ratio = contrast(colors.first, colors.second)
            assertTrue("$name is $ratio:1, below the 3:1 non-text threshold", ratio >= 3.0)
        }
    }
}

/** WCAG 2.1 relative luminance / contrast ratio on 0xAARRGGBB values. */
private fun contrast(
    a: Long,
    b: Long,
): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun luminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
