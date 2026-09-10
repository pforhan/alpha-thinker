package alphainterplanetary.thinker.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF4D5C92)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFDCE1FF)
private val OnPrimaryContainer = Color(0xFF354479)
private val InversePrimary = Color(0xFFB6C4FF)
private val PrimaryFixed = Color(0xFFDCE1FF)
private val PrimaryFixedDim = Color(0xFFB6C4FF)
private val OnPrimaryFixed = Color(0xFF04164B)
private val OnPrimaryFixedVariant = Color(0xFF354479)

private val Secondary = Color(0xFF595D72)
private val OnSecondary = Color(0xFFFFFFFF)
private val SecondaryContainer = Color(0xFFDEE1F9)
private val OnSecondaryContainer = Color(0xFF424659)
private val SecondaryFixed = Color(0xFFDEE1F9)
private val SecondaryFixedDim = Color(0xFFC2C5DD)
private val OnSecondaryFixed = Color(0xFF161B2C)
private val OnSecondaryFixedVariant = Color(0xFF424659)

private val Tertiary = Color(0xFF75546F)
private val OnTertiary = Color(0xFFFFFFFF)
private val TertiaryContainer = Color(0xFFFFD7F5)
private val OnTertiaryContainer = Color(0xFF5B3D57)
private val TertiaryFixed = Color(0xFFFFD7F5)
private val TertiaryFixedDim = Color(0xFFE3BADA)
private val OnTertiaryFixed = Color(0xFF2C122A)
private val OnTertiaryFixedVariant = Color(0xFF5B3D57)

private val LightBackground = Color(0xFFFAF8FF)
private val LightOnSurface = Color(0xFF1A1B21)
private val LightSurface = Color(0xFFFAF8FF)
private val LightSurfaceVariant = Color(0xFFE2E1EC)
private val LightOnSurfaceVariant = Color(0xFF45464F)
private val LightSurfaceTint = Color(0xFF4D5C92)
private val LightInverseSurface = Color(0xFF2F3036)
private val LightInverseOnSurface = Color(0xFFF1F0F7)
private val LightOutline = Color(0xFF767680)
private val LightOutlineVariant = Color(0xFFC6C5D0)
private val LightSurfaceBright = Color(0xFFFAF8FF)
private val LightSurfaceDim = Color(0xFFDAD9E0)
private val LightSurfaceContainer = Color(0xFFEFEDF4)
private val LightSurfaceContainerHigh = Color(0xFFE9E7EF)
private val LightSurfaceContainerHighest = Color(0xFFE3E1E9)
private val LightSurfaceContainerLow = Color(0xFFF4F3FA)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)

private val DarkBackground = Color(0xFF121318)
private val DarkOnSurface = Color(0xFFE3E1E9)
private val DarkSurface = Color(0xFF121318)
private val DarkSurfaceVariant = Color(0xFF45464F)
private val DarkOnSurfaceVariant = Color(0xFFC6C5D0)
private val DarkSurfaceTint = Color(0xFFB6C4FF)
private val DarkInverseSurface = Color(0xFFE3E1E9)
private val DarkInverseOnSurface = Color(0xFF2F3036)
private val DarkOutline = Color(0xFF90909A)
private val DarkOutlineVariant = Color(0xFF45464F)
private val DarkSurfaceBright = Color(0xFF38393F)
private val DarkSurfaceDim = Color(0xFF121318)
private val DarkSurfaceContainer = Color(0xFF1E1F25)
private val DarkSurfaceContainerHigh = Color(0xFF292A2F)
private val DarkSurfaceContainerHighest = Color(0xFF34343A)
private val DarkSurfaceContainerLow = Color(0xFF1A1B21)
private val DarkSurfaceContainerLowest = Color(0xFF0D0E13)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF93000A)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val Scrim = Color(0xFF000000)

/**
 * Material 3 tonal palette generated from the app seed color (the "Ask
 * later" blue, 0xFF2962FF) via material-color-utilities.
 */
internal val LightColorScheme = lightColorScheme(
  primary = Primary,
  onPrimary = OnPrimary,
  primaryContainer = PrimaryContainer,
  onPrimaryContainer = OnPrimaryContainer,
  inversePrimary = InversePrimary,
  primaryFixed = PrimaryFixed,
  primaryFixedDim = PrimaryFixedDim,
  onPrimaryFixed = OnPrimaryFixed,
  onPrimaryFixedVariant = OnPrimaryFixedVariant,
  secondary = Secondary,
  onSecondary = OnSecondary,
  secondaryContainer = SecondaryContainer,
  onSecondaryContainer = OnSecondaryContainer,
  secondaryFixed = SecondaryFixed,
  secondaryFixedDim = SecondaryFixedDim,
  onSecondaryFixed = OnSecondaryFixed,
  onSecondaryFixedVariant = OnSecondaryFixedVariant,
  tertiary = Tertiary,
  onTertiary = OnTertiary,
  tertiaryContainer = TertiaryContainer,
  onTertiaryContainer = OnTertiaryContainer,
  tertiaryFixed = TertiaryFixed,
  tertiaryFixedDim = TertiaryFixedDim,
  onTertiaryFixed = OnTertiaryFixed,
  onTertiaryFixedVariant = OnTertiaryFixedVariant,
  background = LightBackground,
  onBackground = LightOnSurface,
  surface = LightSurface,
  onSurface = LightOnSurface,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightOnSurfaceVariant,
  surfaceTint = LightSurfaceTint,
  inverseSurface = LightInverseSurface,
  inverseOnSurface = LightInverseOnSurface,
  outline = LightOutline,
  outlineVariant = LightOutlineVariant,
  scrim = Scrim,
  error = LightError,
  onError = LightOnError,
  errorContainer = LightErrorContainer,
  onErrorContainer = LightOnErrorContainer,
  surfaceBright = LightSurfaceBright,
  surfaceDim = LightSurfaceDim,
  surfaceContainer = LightSurfaceContainer,
  surfaceContainerHigh = LightSurfaceContainerHigh,
  surfaceContainerHighest = LightSurfaceContainerHighest,
  surfaceContainerLow = LightSurfaceContainerLow,
  surfaceContainerLowest = LightSurfaceContainerLowest,
)

internal val DarkColorScheme = darkColorScheme(
  primary = InversePrimary,
  onPrimary = Color(0xFF1E2D61),
  primaryContainer = OnPrimaryContainer,
  onPrimaryContainer = PrimaryContainer,
  inversePrimary = Primary,
  primaryFixed = PrimaryFixed,
  primaryFixedDim = PrimaryFixedDim,
  onPrimaryFixed = OnPrimaryFixed,
  onPrimaryFixedVariant = OnPrimaryFixedVariant,
  secondary = SecondaryFixedDim,
  onSecondary = Color(0xFF2B3042),
  secondaryContainer = OnSecondaryContainer,
  onSecondaryContainer = SecondaryContainer,
  secondaryFixed = SecondaryFixed,
  secondaryFixedDim = SecondaryFixedDim,
  onSecondaryFixed = OnSecondaryFixed,
  onSecondaryFixedVariant = OnSecondaryFixedVariant,
  tertiary = TertiaryFixedDim,
  onTertiary = Color(0xFF43273F),
  tertiaryContainer = OnTertiaryContainer,
  onTertiaryContainer = TertiaryContainer,
  tertiaryFixed = TertiaryFixed,
  tertiaryFixedDim = TertiaryFixedDim,
  onTertiaryFixed = OnTertiaryFixed,
  onTertiaryFixedVariant = OnTertiaryFixedVariant,
  background = DarkBackground,
  onBackground = DarkOnSurface,
  surface = DarkSurface,
  onSurface = DarkOnSurface,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkOnSurfaceVariant,
  surfaceTint = DarkSurfaceTint,
  inverseSurface = DarkInverseSurface,
  inverseOnSurface = DarkInverseOnSurface,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
  scrim = Scrim,
  error = DarkError,
  onError = DarkOnError,
  errorContainer = DarkErrorContainer,
  onErrorContainer = DarkOnErrorContainer,
  surfaceBright = DarkSurfaceBright,
  surfaceDim = DarkSurfaceDim,
  surfaceContainer = DarkSurfaceContainer,
  surfaceContainerHigh = DarkSurfaceContainerHigh,
  surfaceContainerHighest = DarkSurfaceContainerHighest,
  surfaceContainerLow = DarkSurfaceContainerLow,
  surfaceContainerLowest = DarkSurfaceContainerLowest,
)