package alphainterplanetary.thinker.ui.theme

import alphainterplanetary.thinker.phases.BuiltInPhase
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Closest any container may sit to the surface it is drawn on. Below this a
 * badge washes into the row it sits on. Matches the app's M3 card surface
 * (`surfaceContainerLow` in `Color.kt`).
 */
private const val MIN_SURFACE_DISTANCE = 12.0

private const val LIGHT_SURFACE = 0xFFF4F3FA

private const val DARK_SURFACE = 0xFF1A1B21

class PhaseThemeTest {

  @Test
  fun `every theme styles every built-in phase`() {
    PhaseTheme.All.forEach { theme ->
      BuiltInPhase.entries.forEach { phase ->
        assertTrue(theme.supports(phase), "${theme.key} is missing ${phase.key}")
      }
    }
  }

  @Test
  fun `theme keys are unique`() {
    val keys = PhaseTheme.All.map { it.key }
    assertEquals(keys.size, keys.toSet().size)
  }

  @Test
  fun `theme keys round-trip through fromKey`() {
    PhaseTheme.All.forEach { theme ->
      assertEquals(theme, PhaseTheme.fromKey(theme.key))
    }
  }

  @Test
  fun `fromKey returns null for an unknown key`() {
    assertNull(PhaseTheme.fromKey("nope"))
  }

  @Test
  fun `at least three themes are selectable`() {
    assertTrue(PhaseTheme.All.size >= 3)
  }

  @Test
  fun `light and dark styles differ per phase`() {
    PhaseTheme.All.forEach { theme ->
      BuiltInPhase.entries.forEach { phase ->
        assertFalse(
          theme.style(phase, dark = false) == theme.style(phase, dark = true),
          "${theme.key}/${phase.key} should differ between modes",
        )
      }
    }
  }

  @Test
  fun `every phase style meets WCAG AA contrast`() {
    PhaseTheme.All.forEach { theme ->
      BuiltInPhase.entries.forEach { phase ->
        val palette = theme.colors.getValue(phase.key)
        val light = contrastRatio(palette.contents, palette.containerLight)
        val dark = contrastRatio(palette.contentDark, palette.containerDark)
        assertTrue(
          light >= 4.5,
          "${theme.key}/${phase.key} light mode contrast $light is below WCAG AA",
        )
        assertTrue(
          dark >= 4.5,
          "${theme.key}/${phase.key} dark mode contrast $dark is below WCAG AA",
        )
      }
    }
  }

  @Test
  fun `every phase badge stands out from the row background it sits on`() {
    PhaseTheme.All.forEach { theme ->
      BuiltInPhase.entries.forEach { phase ->
        val palette = theme.colors.getValue(phase.key)
        val light = deltaE(palette.containerLight, LIGHT_SURFACE)
        val dark = deltaE(palette.containerDark, DARK_SURFACE)
        assertTrue(
          light >= MIN_SURFACE_DISTANCE,
          "${theme.key}/${phase.key} light-mode container is only $light " +
            "distant from the card surface (washes out)",
        )
        assertTrue(
          dark >= MIN_SURFACE_DISTANCE,
          "${theme.key}/${phase.key} dark-mode container is only $dark " +
            "distant from the card surface (washes out)",
        )
      }
    }
  }

  private fun deltaE(first: Long, second: Long): Double {
    val a = cieLab(first)
    val b = cieLab(second)
    val dl = b.first - a.first
    val da = b.second - a.second
    val db = b.third - a.third
    return sqrt(dl * dl + da * da + db * db)
  }

  private fun cieLab(argb: Long): Triple<Double, Double, Double> {
    val r = linear(((argb shr 16) and 0xFF).toDouble() / 255.0)
    val g = linear(((argb shr 8) and 0xFF).toDouble() / 255.0)
    val b = linear((argb and 0xFF).toDouble() / 255.0)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883
    fun f(t: Double): Double =
      if (t > 0.008856) t.pow(1.0 / 3.0) else 7.787 * t + 16.0 / 116.0

    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return Triple(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
  }

  private fun linear(channel: Double): Double =
    if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

  private fun contrastRatio(foreground: Long, background: Long): Double {
    val hi = maxOf(relativeLuminance(foreground), relativeLuminance(background))
    val lo = minOf(relativeLuminance(foreground), relativeLuminance(background))
    return (hi + 0.05) / (lo + 0.05)
  }

  private fun relativeLuminance(argb: Long): Double {
    val r = ((argb shr 16) and 0xFF).toDouble() / 255.0
    val g = ((argb shr 8) and 0xFF).toDouble() / 255.0
    val b = (argb and 0xFF).toDouble() / 255.0
    return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
  }
}