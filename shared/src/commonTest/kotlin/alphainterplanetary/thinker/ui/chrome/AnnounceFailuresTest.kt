package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.activitylog.ActivityRecord
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogEntry
import alphainterplanetary.thinker.activitylog.LogMarkers
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.util.now
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnnounceFailuresTest {

  private val project = "project-1"
  private val other = "project-2"

  /**
   * One generation activity as the log files it: a lifecycle row then a terminal
   * one, both sharing the activity id, so the read model synthesizes a real
   * failure headline rather than falling back to the raw row.
   */
  private fun activity(
    activityId: String,
    failed: Boolean = true,
    projectId: String? = project,
    source: LogSource? = LogSource.RemoteLLM,
  ): ActivityRecord = ActivityRecord.groupByActivity(
    listOf(
      LogEntry(
        id = 1,
        activityId = activityId,
        projectId = projectId,
        category = LogCategory.QuestionGeneration,
        source = source,
        log = "${LogMarkers.Started} ${TaskKind.InitialQuestions.name}",
        timestamp = now(),
      ),
      LogEntry(
        id = 2,
        activityId = activityId,
        projectId = projectId,
        category = LogCategory.QuestionGeneration,
        source = source,
        log = if (failed) "${LogMarkers.Failed} model exploded" else LogMarkers.Succeeded,
        timestamp = now(),
      ),
    ),
  ).single()

  @Test
  fun `a failure in the project on screen is announced`() {
    assertTrue(
      shouldAnnounceFailure(
        latest = activity("task-1"),
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = project,
      ),
    )
  }

  @Test
  fun `a success is not announced`() {
    assertFalse(
      shouldAnnounceFailure(
        latest = activity("task-1", failed = false),
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = project,
      ),
    )
  }

  @Test
  fun `nothing is announced on an empty log`() {
    assertFalse(
      shouldAnnounceFailure(
        latest = null,
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = project,
      ),
    )
  }

  /**
   * The reported bug: a failure already shown, then the project re-entered (or the
   * engine switched, or the app relaunched) — the same activity must not raise
   * the sheet a second time.
   */
  @Test
  fun `a failure already announced is not announced again`() {
    assertFalse(
      shouldAnnounceFailure(
        latest = activity("task-1"),
        announcedActivityId = "task-1",
        sheetIsOpen = false,
        currentProjectId = project,
      ),
    )
  }

  @Test
  fun `a newer failure is announced after an older one was`() {
    assertTrue(
      shouldAnnounceFailure(
        latest = activity("task-2"),
        announcedActivityId = "task-1",
        sheetIsOpen = false,
        currentProjectId = project,
      ),
    )
  }

  /**
   * A sheet already up is skipped but *not* consumed, so the failure is still
   * pending when the sheet closes.
   */
  @Test
  fun `a failure while a sheet is open is left pending`() {
    assertFalse(
      shouldAnnounceFailure(
        latest = activity("task-1"),
        announcedActivityId = null,
        sheetIsOpen = true,
        currentProjectId = project,
      ),
    )
  }

  @Test
  fun `another project's failure is left for its own project`() {
    assertFalse(
      shouldAnnounceFailure(
        latest = activity("task-1", projectId = other),
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = project,
      ),
    )
  }

  @Test
  fun `another project's failure is announced once its project is open`() {
    assertTrue(
      shouldAnnounceFailure(
        latest = activity("task-1", projectId = other),
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = other,
      ),
    )
  }

  @Test
  fun `a failure off the project list or an app screen is left for the chip and the dot`() {
    assertFalse(
      shouldAnnounceFailure(
        latest = activity("task-1"),
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = null,
      ),
    )
  }

  /**
   * A bare capability failure belongs to no project, so there is no project to
   * match — it interrupts wherever the user is, which is the one case with no
   * screen that could act on it.
   */
  @Test
  fun `a projectless failure is announced anywhere`() {
    assertTrue(
      shouldAnnounceFailure(
        latest = activity("task-1", projectId = null),
        announcedActivityId = null,
        sheetIsOpen = false,
        currentProjectId = null,
      ),
    )
  }

  @Test
  fun `labels the row with the engine that ran it`() {
    assertEquals(
      "Last Lite activity: Initial question generation failed: model exploded",
      lastActivityLabel(activity("task-1", source = LogSource.Lite)),
    )
    assertEquals(
      "Last Remote LLM activity: Initial question generation failed: model exploded",
      lastActivityLabel(activity("task-1")),
    )
  }

  @Test
  fun `labels a row with no source without inventing an engine`() {
    assertEquals(
      "Last activity: Initial question generation failed: model exploded",
      lastActivityLabel(activity("task-1", source = null)),
    )
  }

  @Test
  fun `labels a success with its engine too`() {
    assertEquals(
      "Last Remote LLM activity: Initial question generation succeeded",
      lastActivityLabel(activity("task-1", failed = false)),
    )
  }
}
