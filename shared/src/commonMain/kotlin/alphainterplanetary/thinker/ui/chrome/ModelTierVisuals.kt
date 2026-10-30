package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.ModelPresentationTier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The glyph a [ModelPresentationTier] header leads with.
 *
 * The tiers describe a trade-off rather than an ordered list, so the shapes say
 * which one a model is: a star for the best overall, an hourglass for quality
 * that costs time, a bolt for speed at some quality, a question mark for no
 * data, and a no-entry sign for discouraged. Material icons replace the old
 * Unicode glyphs because a vector is drawn at the header's own size and weight
 * and reads the same on every platform, where `★`/`✔`/`▶▶` fell back to fonts
 * that sized and aligned them differently.
 *
 * The mapping lives here rather than on the engine enum so the engine layer
 * stays free of Compose types; it is the tier counterpart of
 * [CapabilityState.glyph].
 */
internal fun ModelPresentationTier.glyph(): ImageVector =
  when (this) {
    ModelPresentationTier.Recommended -> Icons.Filled.Star
    ModelPresentationTier.HighQualityButSlower -> Icons.Filled.HourglassBottom
    ModelPresentationTier.LowerQualityButFast -> Icons.Filled.Bolt
    ModelPresentationTier.Unknown -> Icons.AutoMirrored.Filled.HelpOutline
    ModelPresentationTier.Discouraged -> Icons.Filled.DoNotDisturb
  }
