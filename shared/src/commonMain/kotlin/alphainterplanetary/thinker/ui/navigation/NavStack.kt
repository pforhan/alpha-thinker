package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.ui.chrome.ChromeSheet
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * A screen the app can be on.
 *
 * This is the whole destination list: the app is a shallow drill-down (list ->
 * project -> log) with settings sheets over the top, so a flat route type is the
 * model rather than a string route plus an argument map.
 */
internal sealed interface AppRoute {
  /** The project list, and the root of the stack. */
  data object ProjectList : AppRoute

  /** A single project's question workspace. */
  data class ProjectDetail(val projectId: String) : AppRoute

  /** The app-wide activity log. */
  data object ActivityLog : AppRoute

  /** The generation task manager. */
  data object TaskManager : AppRoute

  /**
   * The project simulator, full-screen.
   *
   * A destination rather than a settings sheet because a run is minutes of real
   * latency on a form worth watching: a sheet is a half-height peek with a
   * dismiss gesture, and the thing being simulated has its own progress, its own
   * Cancel, and a result the user has to be able to read without first finding
   * the right sheet. See `SimulatorScreen`.
   */
  data object Simulator : AppRoute
}

/**
 * One entry in the back stack: a screen, or a settings sheet stacked over one.
 *
 * Sheets are entries rather than separate state so that back means the same thing
 * everywhere — it closes the sheet if a sheet is up, and otherwise returns to
 * wherever the current screen was opened from. A sheet that replaced another
 * sheet (Status handing off to Intelligence) is a second entry, so back from it
 * returns to the sheet it came from instead of to the screen underneath.
 */
internal sealed interface NavEntry {
  data class Screen(val route: AppRoute) : NavEntry
  data class Sheet(val target: ChromeSheet) : NavEntry
}

/**
 * The app's back stack, shared by every platform.
 *
 * It replaces both a hand-rolled single-slot route variable and Jetpack
 * Navigation: those two implementations had to be kept in step by hand, and only
 * the Android one was actually a stack. This one is the same type on Android,
 * iOS, desktop and web, so "leaving the activity log returns to the screen you
 * opened it from" holds everywhere and there is no second nav root to update.
 *
 * The stack is a plain list of [NavEntry]s with two rules that keep it honest:
 * a sheet can only sit on top of a screen (or another sheet), and navigating to
 * a screen drops the sheets above the current one, because a sheet belongs to
 * the screen that opened it — leaving it in history would pop back onto a sheet
 * floating over a screen that is no longer there.
 *
 * The root is never popped, so [pop] returning false is the signal that there is
 * nothing left to go back to and the platform should handle the event itself
 * (on Android, leaving the app).
 */
@Stable
internal class NavStack(
  entries: List<NavEntry> = listOf(NavEntry.Screen(AppRoute.ProjectList)),
) : Navigation {
  private val entries = mutableStateListOf<NavEntry>().apply {
    // A restore that decoded nothing usable (an old saved key, an empty list)
    // must still leave a root behind: every getter below reads the last entry.
    addAll(entries.ifEmpty { listOf(NavEntry.Screen(AppRoute.ProjectList)) })
  }

  /** The entry on top: a sheet if one is open, otherwise the current screen. */
  val current: NavEntry
    get() = entries.last()

  /**
   * The screen being shown — the last screen on the stack, so a sheet reports the
   * screen it is covering rather than hiding it.
   */
  override val route: AppRoute
    get() = (entries.last { it is NavEntry.Screen } as NavEntry.Screen).route

  /** The open settings subscreen, or null when the top entry is a screen. */
  override val sheet: ChromeSheet?
    get() = (entries.last() as? NavEntry.Sheet)?.target

  /**
   * Whether a settings subscreen is covering the current screen. The one
   * spelling of the predicate, so callers cannot drift between `sheet != null`
   * and a second copy of it.
   */
  override val isSheetOpen: Boolean
    get() = sheet != null

  /**
   * Whether the open sheet replaced another sheet, which is what tells the sheet
   * to offer a back glyph (return to the sheet underneath) rather than a close
   * one (return to the screen).
   */
  override val hasSheetBelow: Boolean
    get() = entries.getOrNull(entries.size - 2) is NavEntry.Sheet

  /** Whether there is anything to go back to. False only at the root screen. */
  override val canGoBack: Boolean
    get() = entries.size > 1

  /**
   * Pushes [route], dropping any open sheet first.
   *
   * Navigating to the screen already on top is a no-op rather than a second
   * copy of it, so the flyout's Tools rows can be tapped repeatedly without
   * building a stack of identical screens (the old Jetpack graph's
   * `launchSingleTop`).
   */
  override fun navigate(route: AppRoute) {
    while (entries.last() is NavEntry.Sheet) {
      entries.removeLast()
    }
    if (entries.last() == NavEntry.Screen(route)) return
    entries.add(NavEntry.Screen(route))
  }

  /**
   * Replaces the whole stack with the root screen and [route] on top of it,
   * discarding every sheet and every screen in between.
   *
   * For a caller that has a destination to put the user in front of wherever
   * they happen to be — the simulator opening the project it just created, say.
   * It is deliberately not [navigate]: pushing onto the current stack would
   * leave the way in on the history, so back from the new screen would return
   * the user to wherever the jump happened from. The place they jumped *from* is
   * not a place worth returning to, so back lands on the project list instead.
   */
  fun resetTo(route: AppRoute) {
    entries.clear()
    entries.add(NavEntry.Screen(AppRoute.ProjectList))
    if (route != AppRoute.ProjectList) entries.add(NavEntry.Screen(route))
  }

  /**
   * Opens [target] over the current screen, or over the open sheet when one is
   * already up. Re-opening the sheet already on top does nothing, so a double
   * tap cannot stack two copies of it.
   */
  override fun openSheet(target: ChromeSheet) {
    if (sheet == target) return
    entries.add(NavEntry.Sheet(target))
  }

  /**
   * Pops one entry — a sheet if one is up, otherwise the current screen —
   * returning false when the root is all that is left.
   */
  override fun goBack(): Boolean {
    if (!canGoBack) return false
    entries.removeLast()
    return true
  }

  companion object {
    /**
     * The stack [keys] name, dropping any that no longer decode. An empty or
     * fully undecodable list still yields the root screen, so a saved stack from
     * an older build cannot leave the app with nowhere to go back to.
     */
    fun fromKeys(keys: List<String>): NavStack = NavStack(keys.mapNotNull(::navEntryFromKey))

    /**
     * Saves the stack as entry keys, so it survives process death on the
     * platforms that have a saved-instance-state to write into. Keys rather than
     * a Bundle-native encoding of each entry: the alternative is a
     * `Parcelable`/`@Serializable` per entry type, and an entry whose key no
     * longer decodes is dropped on restore rather than crashing the launch.
     */
    val Saver: Saver<NavStack, Any> = listSaver(
      save = { stack -> stack.entries.map { it.toKey() } },
      restore = ::fromKeys,
    )
  }
}

/** This entry as a saveable key. Prefixed per kind so a route key and a sheet key can never collide. */
internal fun NavEntry.toKey(): String = when (this) {
  is NavEntry.Screen -> when (route) {
    AppRoute.ProjectList -> "route:project_list"
    is AppRoute.ProjectDetail -> "route:project_detail:${route.projectId}"
    AppRoute.ActivityLog -> "route:activity_log"
    AppRoute.TaskManager -> "route:task_manager"
    AppRoute.Simulator -> "route:simulator"
  }

  is NavEntry.Sheet -> "sheet:${target.name}"
}

/**
 * The entry [key] names, or null when it names something this build no longer
 * has — an entry that fails to decode is dropped, and an empty restore falls
 * back to the root screen.
 */
internal fun navEntryFromKey(key: String): NavEntry? = when {
  key == "route:project_list" -> NavEntry.Screen(AppRoute.ProjectList)
  key == "route:activity_log" -> NavEntry.Screen(AppRoute.ActivityLog)
  key == "route:task_manager" -> NavEntry.Screen(AppRoute.TaskManager)
  key == "route:simulator" -> NavEntry.Screen(AppRoute.Simulator)
  key.startsWith("route:project_detail:") -> key
    .removePrefix("route:project_detail:")
    .takeIf { it.isNotEmpty() }
    ?.let { NavEntry.Screen(AppRoute.ProjectDetail(it)) }

  key.startsWith("sheet:") -> key
    .removePrefix("sheet:")
    .let { name -> ChromeSheet.entries.firstOrNull { it.name == name } }
    ?.let { NavEntry.Sheet(it) }

  else -> null
}
