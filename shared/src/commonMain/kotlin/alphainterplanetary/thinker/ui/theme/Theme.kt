package alphainterplanetary.thinker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun AlphaThinkerTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  phaseTheme: PhaseTheme = PhaseTheme.Default,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalExtendedColors provides (if (darkTheme) DarkExtendedColors else LightExtendedColors),
    LocalPhaseTheme provides phaseTheme,
    LocalDarkTheme provides darkTheme,
  ) {
    MaterialTheme(
      colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
      typography = Typography,
      shapes = Shapes,
      content = content,
    )
  }
}