package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.ui.chrome.ChromeSheet

/**
 * The back stack's operation surface: what a screen needs to navigate, while
 * remaining ignorant of how the stack stores its entries.
 *
 * [NavStack] implements it, and `AppChromeState` implements it again *by
 * delegation*, so the chrome's callers say `chrome.navigate(...)` and
 * `chrome.goBack()` rather than reaching through to a stack member. Deliberately
 * not a hand-written set of pass-throughs: delegation keeps the two in lockstep,
 * so the stack stays the single owner of the logic and the chrome of the call
 * shape. `openProjectFromList` on the chrome is the one navigation operation not
 * here — a full [NavStack.resetTo] is too sharp an instrument to hand to every
 * screen, so it stays a named chrome method instead.
 */
internal interface Navigation {
  /**
   * The screen being shown — the last screen on the stack, so a sheet reports
   * the screen it is covering rather than hiding it.
   */
  val route: AppRoute

  /** The open settings subscreen, or null when none is. */
  val sheet: ChromeSheet?

  /**
   * Whether a settings subscreen is covering the current screen. The one
   * spelling of the predicate, so callers cannot drift between `sheet != null`
   * and a second copy of it.
   */
  val isSheetOpen: Boolean

  /**
   * Whether the open sheet replaced another sheet — the difference between the
   * sheet's back glyph (return to the sheet underneath) and its close one
   * (return to the screen).
   */
  val hasSheetBelow: Boolean

  /** Whether there is anywhere to go back to. False only at the root screen. */
  val canGoBack: Boolean

  /**
   * Pushes [route], dropping any open sheet first. Navigating to the screen
   * already on top is a no-op rather than a second copy of it.
   */
  fun navigate(route: AppRoute)

  /**
   * Opens [target] over the current screen, or over the open sheet when one is
   * already up; re-opening the sheet on top does nothing.
   */
  fun openSheet(target: ChromeSheet)

  /** Pops one entry — a sheet if one is up, else the current screen. */
  fun goBack(): Boolean
}