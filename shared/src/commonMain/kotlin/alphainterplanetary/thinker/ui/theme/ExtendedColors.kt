package alphainterplanetary.thinker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * App-specific colors that don't map onto a standard Material 3 role.
 * Access via [LocalExtendedColors] inside composition, e.g. the swipe-action
 * backgrounds and their foreground content.
 */
@Immutable
data class ExtendedColors(
  val swipeActionContent: Color,
  val swipeAskLater: Color,
  val swipeIgnore: Color,
  val swipeDelete: Color,
  val swipeUnignore: Color,
)

internal val LightExtendedColors = ExtendedColors(
  swipeActionContent = Color(0xFFFFFFFF),
  swipeAskLater = Color(0xFF2962FF),
  swipeIgnore = Color(0xFF757575),
  swipeDelete = Color(0xFFD32F2F),
  swipeUnignore = Color(0xFF2E7D32),
)

internal val DarkExtendedColors = ExtendedColors(
  swipeActionContent = Color(0xFFFFFFFF),
  swipeAskLater = Color(0xFF448AFF),
  swipeIgnore = Color(0xFF9E9E9E),
  swipeDelete = Color(0xFFEF5350),
  swipeUnignore = Color(0xFF66BB6A),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }