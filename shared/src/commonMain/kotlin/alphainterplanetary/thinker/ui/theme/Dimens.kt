package alphainterplanetary.thinker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The app's spacing and icon-size tokens. Names describe the layout role a
 * value plays (e.g. the gap between an icon and its label), not the raw
 * measurement. UI code must reference these names instead of hardcoding dp
 * literals; add a new token here when the UI needs a role the theme doesn't
 * already provide.
 */
object Dimens {
  // Layout insets
  val ScreenPadding = 16.dp
  val SectionPadding = 16.dp
  val CardPadding = 16.dp
  val EmptyStatePadding = 24.dp

  // Vertical rhythm
  val ContentGap = 8.dp
  val SectionGap = 12.dp
  val ListGap = 8.dp
  val FormGap = 16.dp
  val TightGap = 4.dp

  // Gap between an element and its primary action
  val MessageActionGap = 16.dp
  val EmptyStateActionGap = 22.dp

  // Bar/chip layout
  val ChipGap = 8.dp
  val LabelChipGap = 8.dp
  val ModeBarVerticalPadding = 8.dp
  val OutlineStroke = 1.dp

  // Phase badge / pill
  val BadgeSize = 28.dp
  val PillHorizontalPadding = 12.dp
  val PillVerticalPadding = 4.dp

  // Phase row coloring
  val PhaseRowBarWidth = 4.dp

  // Icon / control adjacencies
  val IconLabelGap = 8.dp
  val ToolIconLabelGap = 12.dp
  val ControlLabelGap = 8.dp
  val ActionRowVerticalPadding = 12.dp
  val ButtonHorizontalPadding = 8.dp

  // Swipe actions
  val SwipeActionPadding = 20.dp

  // Icon and control sizes
  val IconSizeSmall = 14.dp
  val IconSizeMedium = 20.dp
  val ScrollControlSize = 16.dp

  // Theme picker preview
  val ThemeSwatchGap = 6.dp
  val ThemePreviewPadding = 8.dp

  // Celebration confetti
  val ConfettiPieceSize = 3.dp
  val ConfettiBurstWidth = 80.dp
  val ConfettiBurstHeight = 56.dp

  // Confetti cell for a phase-timeline row: badge flush to the row's start,
  // burst originating just right of the badge so particles fill the gap
  // toward the row's label instead of padding the badge on both sides.
  val PhaseTimelineBurstWidth = 44.dp
  val PhaseTimelineBurstHeight = 56.dp

  // Next-phase choice callout
  val NextPhaseChoicesMinHeight = 92.dp
  val PhaseChoiceArrowWidth = 10.dp
  val PhaseChoiceCornerRadius = 12.dp

  // Callouts sit beside their target when the dialog text column is at least
  // this wide; below it they expand beneath instead (see PhaseAdvanceDialog).
  val SideBySideCalloutMinWidth = 440.dp
}