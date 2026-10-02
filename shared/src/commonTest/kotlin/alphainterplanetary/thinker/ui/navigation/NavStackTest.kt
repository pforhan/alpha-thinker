package alphainterplanetary.thinker.ui.navigation

import alphainterplanetary.thinker.ui.chrome.ChromeSheet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavStackTest {

  @Test
  fun `starts at the project list with nowhere to go back to`() {
    val stack = NavStack()

    assertEquals(AppRoute.ProjectList, stack.route)
    assertNull(stack.sheet)
    assertFalse(stack.canGoBack)
    assertFalse(stack.pop())
  }

  @Test
  fun `back from a project returns to the list it was opened from`() {
    val stack = NavStack()

    stack.navigate(AppRoute.ProjectDetail("project-1"))
    assertTrue(stack.canGoBack)

    assertTrue(stack.pop())
    assertEquals(AppRoute.ProjectList, stack.route)
  }

  @Test
  fun `back from the activity log returns to the screen it was opened from`() {
    val stack = NavStack()

    stack.navigate(AppRoute.ProjectDetail("project-1"))
    // Reached from the flyout, which is on every screen — this is the case the
    // old single-slot route variable got wrong by always returning to the list.
    stack.navigate(AppRoute.ActivityLog)
    assertEquals(AppRoute.ActivityLog, stack.route)

    assertTrue(stack.pop())
    assertEquals(AppRoute.ProjectDetail("project-1"), stack.route)
  }

  @Test
  fun `the task manager returns to wherever it was opened from too`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ActivityLog)

    stack.navigate(AppRoute.TaskManager)
    assertTrue(stack.pop())

    assertEquals(AppRoute.ActivityLog, stack.route)
  }

  @Test
  fun `navigating to the screen already on top is a no-op`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ActivityLog)

    // The flyout's Tools rows can be tapped repeatedly; each tap must not leave
    // another copy of the same screen behind it.
    stack.navigate(AppRoute.ActivityLog)

    assertTrue(stack.pop())
    assertEquals(AppRoute.ProjectList, stack.route)
    assertFalse(stack.canGoBack)
  }

  @Test
  fun `a sheet is an entry over the screen that opened it`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ProjectDetail("project-1"))

    stack.openSheet(ChromeSheet.Status)

    assertEquals(ChromeSheet.Status, stack.sheet)
    assertEquals(AppRoute.ProjectDetail("project-1"), stack.route)
    assertFalse(stack.hasSheetBelow)

    assertTrue(stack.pop())
    assertNull(stack.sheet)
    assertEquals(AppRoute.ProjectDetail("project-1"), stack.route)
  }

  @Test
  fun `back returns from a sheet before it leaves the screen`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ActivityLog)
    stack.openSheet(ChromeSheet.Testing)

    // System back with a sheet up closes the sheet; it does not leave the screen
    // the sheet was covering.
    assertTrue(stack.pop())
    assertNull(stack.sheet)
    assertEquals(AppRoute.ActivityLog, stack.route)
    assertTrue(stack.canGoBack)
  }

  @Test
  fun `a sheet that replaced another sheet goes back to it`() {
    val stack = NavStack()
    stack.openSheet(ChromeSheet.Status)

    stack.openSheet(ChromeSheet.Intelligence)
    assertEquals(ChromeSheet.Intelligence, stack.sheet)
    assertTrue(stack.hasSheetBelow)

    assertTrue(stack.pop())
    assertEquals(ChromeSheet.Status, stack.sheet)
    assertFalse(stack.hasSheetBelow)
  }

  @Test
  fun `re-opening the sheet already on top does not stack it twice`() {
    val stack = NavStack()
    stack.openSheet(ChromeSheet.PhaseColors)

    stack.openSheet(ChromeSheet.PhaseColors)

    assertTrue(stack.pop())
    assertNull(stack.sheet)
    assertEquals(AppRoute.ProjectList, stack.route)
  }

  @Test
  fun `navigating to a screen drops the sheets above it`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ProjectDetail("project-1"))
    stack.openSheet(ChromeSheet.Status)

    // The Status sheet's last-activity row opens the log this way. The sheet
    // belongs to the project screen underneath, so it goes with it rather than
    // staying in history to pop back onto.
    stack.navigate(AppRoute.ActivityLog)

    assertNull(stack.sheet)
    assertEquals(AppRoute.ActivityLog, stack.route)
    assertTrue(stack.pop())
    assertEquals(AppRoute.ProjectDetail("project-1"), stack.route)
    // The sheet is gone from the history, not merely hidden: the log sits
    // directly on the project screen, so popping it lands there and not on a
    // sheet floating over nothing.
    assertNull(stack.sheet)
    assertTrue(stack.pop())
    assertEquals(AppRoute.ProjectList, stack.route)
  }

  @Test
  fun `a reset drops the sheets and the screens on the way in`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ProjectDetail("project-1"))
    stack.navigate(AppRoute.ActivityLog)
    stack.openSheet(ChromeSheet.Testing)

    // The simulator's finished run opens its project from wherever the user is —
    // which is not somewhere they asked to be sent from.
    stack.resetTo(AppRoute.ProjectDetail("project-2"))

    assertNull(stack.sheet)
    assertEquals(AppRoute.ProjectDetail("project-2"), stack.route)

    assertTrue(stack.pop())
    assertNull(stack.sheet)
    assertEquals(AppRoute.ProjectList, stack.route)
    assertFalse(stack.canGoBack)
  }

  @Test
  fun `a reset to the list itself is just the list`() {
    val stack = NavStack()
    stack.navigate(AppRoute.ActivityLog)
    stack.openSheet(ChromeSheet.Status)

    stack.resetTo(AppRoute.ProjectList)

    assertNull(stack.sheet)
    assertEquals(AppRoute.ProjectList, stack.route)
    assertFalse(stack.canGoBack)
  }

  @Test
  fun `a saved stack restores with the same history`() {
    // The shape a save actually produces: sheets are only ever on top, because
    // navigating to a screen drops them.
    val restored = NavStack.fromKeys(
      listOf(
        NavEntry.Screen(AppRoute.ProjectList),
        NavEntry.Screen(AppRoute.ProjectDetail("project-1")),
        NavEntry.Sheet(ChromeSheet.Status),
      ).map { it.toKey() },
    )

    assertEquals(ChromeSheet.Status, restored.sheet)
    assertEquals(AppRoute.ProjectDetail("project-1"), restored.route)

    assertTrue(restored.pop())
    assertNull(restored.sheet)
    assertTrue(restored.pop())
    assertEquals(AppRoute.ProjectList, restored.route)
    assertFalse(restored.canGoBack)
  }

  @Test
  fun `every entry kind round-trips through a key`() {
    val entries = listOf(
      NavEntry.Screen(AppRoute.ProjectList),
      NavEntry.Screen(AppRoute.ProjectDetail("project-1")),
      NavEntry.Screen(AppRoute.ActivityLog),
      NavEntry.Screen(AppRoute.TaskManager),
      NavEntry.Screen(AppRoute.Simulator),
      NavEntry.Sheet(ChromeSheet.Status),
      NavEntry.Sheet(ChromeSheet.PhaseColors),
      NavEntry.Sheet(ChromeSheet.Intelligence),
      NavEntry.Sheet(ChromeSheet.Testing),
    )

    entries.forEach { entry ->
      assertEquals(entry, navEntryFromKey(entry.toKey()))
    }
  }

  @Test
  fun `a key this build cannot decode is dropped and the rest survive`() {
    val stack = NavStack.fromKeys(
      listOf("route:project_detail:project-1", "route:retired_screen", "route:task_manager"),
    )

    assertEquals(AppRoute.TaskManager, stack.route)
    assertTrue(stack.pop())
    assertEquals(AppRoute.ProjectDetail("project-1"), stack.route)
  }

  @Test
  fun `a stack of nothing but undecodable keys still has a root`() {
    val stack = NavStack.fromKeys(listOf("route:retired_screen", "sheet:Retired"))

    assertEquals(AppRoute.ProjectList, stack.route)
    assertFalse(stack.canGoBack)
  }
}
