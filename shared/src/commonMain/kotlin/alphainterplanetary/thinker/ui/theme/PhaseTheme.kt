package alphainterplanetary.thinker.ui.theme

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Container + content colors for one phase in one theme. Values are 0xAARRGGBB
 * longs so the palette can be analyzed (contrast, dilution) without depending
 * on Compose; [PhaseTheme.style] wraps them into [PhaseStyle]s at render time.
 */
@Immutable
data class PhaseColors(
  val containerLight: Long,
  val contents: Long,
  val contentDark: Long,
  val containerDark: Long,
)

/**
 * A selectable phase-color theme: every planning phase gets its own
 * container/content colors, for both light and dark modes.
 *
 * **Colorblind sensitivity.** [Colorblind] uses the Okabe–Ito palette, tuned
 * to stay distinguishable under the common red/green and blue/yellow color-
 * vision deficiencies. Color is always a redundant cue: the badge number and
 * pill label carry the phase identity.
 *
 * **Dilution.** [style] returns full-strength containers for badges and pills.
 * Containers are additionally picked to stay readable when diluted over a
 * surface (future row bars / row tints): every container stays well separated
 * from the surface it sits on, so a phase never washes out into the card it is
 * drawn on.
 *
 * **Text.** Each mode pairs a tuned ink with its container — [PhaseColors.contents]
 * on the light-mode container, [PhaseColors.contentDark] on the dark-mode
 * container — so the containers themselves can be bold mid-tones without
 * losing WCAG AA text contrast in either mode.
 *
 * **Dark mode** pairs [PhaseColors.containerDark] as the container with
 * [PhaseColors.contentDark] as its content; both ends of every pair are always
 * separated by well over an AA contrast ratio.
 */
@Immutable
data class PhaseTheme(
  val key: String,
  val label: String,
  val description: String,
  /** Per-phase colors, keyed by the phase's durable key string ([Phase.key]). */
  val colors: Map<String, PhaseColors>,
) {

  /** True when this theme defines a style for [phase]. */
  fun supports(phase: Phase): Boolean = phase.key in colors

  /** The phase's container/content [PhaseStyle] for the given light/dark mode. */
  fun style(phase: Phase, dark: Boolean): PhaseStyle {
    val palette = colors[phase.key] ?: FallbackColors
    return if (dark) {
      PhaseStyle(container = Color(palette.containerDark), content = Color(palette.contentDark))
    } else {
      PhaseStyle(container = Color(palette.containerLight), content = Color(palette.contents))
    }
  }

  companion object {
    private val FallbackColors = PhaseColors(
      containerLight = 0xFFB8E0D2,
      contents = 0xFF152E27,
      contentDark = 0xFFA8D9C7,
      containerDark = 0xFF1E4A3C,
    )

    /** Bold pure-hue primaries: red, orange, yellow, green, blue, purple. */
    val Vibrant = PhaseTheme(
      key = "vibrant",
      label = "Vibrant",
      description = "Bold pure-hue primaries for maximum pop and maximum " +
        "distinction.",
      colors = palette(
        BuiltInPhase.ScopeGoals to PhaseColors(0xFFCD001A, 0xFFFAE0DB, 0xFFF6E1DD, 0xFF6E1713),
        BuiltInPhase.Research to PhaseColors(0xFFEF6A00, 0xFF2F231D, 0xFFF4E2D9, 0xFF7D3C12),
        BuiltInPhase.Design to PhaseColors(0xFFF2CD00, 0xFF2A251A, 0xFFEBE5D6, 0xFF6E5D1B),
        BuiltInPhase.ExecutionPlan to PhaseColors(0xFF79C300, 0xFF23271C, 0xFFE2E7D8, 0xFF46661B),
        BuiltInPhase.ValidationPlan to PhaseColors(0xFF1961AE, 0xFFDFE5F8, 0xFFE0E5F4, 0xFF19365F),
        BuiltInPhase.DefinitionOfDone to PhaseColors(0xFF61007D, 0xFFEFE1F2, 0xFFEDE2EF, 0xFF4F1362),
      ),
    )

    /** A deep-sea blue ramp from midnight navy to bright cyan surf. */
    val Ocean = PhaseTheme(
      key = "ocean",
      label = "Open Ocean",
      description = "Deep offshore blues and bright shallows, from midnight " +
        "navy to cyan surf.",
      colors = palette(
        BuiltInPhase.ScopeGoals to PhaseColors(0xFF03045E, 0xFFF2F5FC, 0xFF02022F, 0xFFF2F5FC),
        BuiltInPhase.Research to PhaseColors(0xFF023E8A, 0xFFF2F5FC, 0xFF011F45, 0xFFF2F5FC),
        BuiltInPhase.Design to PhaseColors(0xFF0077B6, 0xFFFCFDFF, 0xFF003C5B, 0xFFFCFDFF),
        BuiltInPhase.ExecutionPlan to PhaseColors(0xFF0096C7, 0xFF001B26, 0xFF004B64, 0xFFFCFDFF),
        BuiltInPhase.ValidationPlan to PhaseColors(0xFF00B4D8, 0xFF001B26, 0xFF005A6C, 0xFFFCFDFF),
        BuiltInPhase.DefinitionOfDone to PhaseColors(0xFF48CAE4, 0xFF001B26, 0xFF246572, 0xFFFCFDFF),
      ),
    )

    /** Sun-baked oranges, ambers and olives. */
    val Earth = PhaseTheme(
      key = "earth",
      label = "Warm Autumn",
      description = "Sun-baked oranges, ambers and olives.",
      colors = palette(
        BuiltInPhase.ScopeGoals to PhaseColors(0xFFEB5E28, 0xFF1A1412, 0xFFF5E1DA, 0xFF5E220B),
        BuiltInPhase.Research to PhaseColors(0xFFF9A03F, 0xFF191511, 0xFFF1E3D7, 0xFF774813),
        BuiltInPhase.Design to PhaseColors(0xFFF3C053, 0xFF181511, 0xFFEEE4D6, 0xFF55400E),
        BuiltInPhase.ExecutionPlan to PhaseColors(0xFFA1C349, 0xFF151611, 0xFFE4E7D8, 0xFF273209),
        BuiltInPhase.ValidationPlan to PhaseColors(0xFF87A330, 0xFF151611, 0xFFE4E7D8, 0xFF3B490E),
        BuiltInPhase.DefinitionOfDone to PhaseColors(0xFF6A8532, 0xFF000000, 0xFFE3E7D8, 0xFF4C6519),
      ),
    )

    /** Okabe–Ito palette: distinguishable under common color-vision deficiency. */
    val Colorblind = PhaseTheme(
      key = "colorblind",
      label = "Colorblind",
      description = "Okabe–Ito palette, designed to stay distinguishable for " +
        "common color-vision deficiencies.",
      colors = palette(
        BuiltInPhase.ScopeGoals to PhaseColors(0xFF56B4E9, 0xFF121619, 0xFFD8E7F3, 0xFF0D3144),
        BuiltInPhase.Research to PhaseColors(0xFF009E73, 0xFF111714, 0xFFD7EAE1, 0xFF1D644B),
        BuiltInPhase.Design to PhaseColors(0xFFF0E442, 0xFF171611, 0xFFE9E5D6, 0xFF4E4A0D),
        BuiltInPhase.ExecutionPlan to PhaseColors(0xFF0072B2, 0xFFF2F4F8, 0xFFDCE6F4, 0xFF276090),
        BuiltInPhase.ValidationPlan to PhaseColors(0xFFD55E00, 0xFF191512, 0xFFF4E2D9, 0xFF672F0A),
        BuiltInPhase.DefinitionOfDone to PhaseColors(0xFFCC79A7, 0xFF191417, 0xFFF3E1E9, 0xFFA52777),
      ),
    )

    /** Every selectable theme, in display order. */
    val All: List<PhaseTheme> = listOf(Vibrant, Ocean, Earth, Colorblind)

    /** The theme a fresh install starts on. */
    val Default: PhaseTheme = Vibrant

    /** Resolves a persisted theme key back to a theme, or null for an unknown key. */
    fun fromKey(key: String): PhaseTheme? = All.find { it.key == key }

    private fun palette(vararg entries: Pair<Phase, PhaseColors>): Map<String, PhaseColors> =
      entries.map { it.first.key to it.second }.toMap()
  }
}