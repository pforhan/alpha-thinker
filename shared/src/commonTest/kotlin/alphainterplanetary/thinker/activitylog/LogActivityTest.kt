package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.util.now
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class LogActivityTest {

  private fun entry(
    id: Long,
    activityId: String?,
    log: String,
    category: LogCategory = LogCategory.QuestionGeneration,
    source: LogSource? = LogSource.RemoteLLM,
    timestamp: Instant = now(),
  ): LogEntry = LogEntry(
    id = id,
    activityId = activityId,
    category = category,
    source = source,
    log = log,
    timestamp = timestamp,
  )

  /** A title-recommendation activity: input, then a produced title. */
  private fun titleActivity(entries: List<LogEntry>): List<LogActivity> =
    LogActivity.groupByActivity(entries)

  @Test
  fun `groups rows by activity id ordering newest first`() {
    val old = now() - 10.days
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "input: synopsis=Build a rocketship", LogCategory.TitleRecommendation, timestamp = old),
        entry(2, "task-1", "response: Rocketship", LogCategory.TitleRecommendation, timestamp = old),
        entry(3, "task-2", "started: RemainingInPhase", LogCategory.TaskRun, LogSource.TaskRunner, timestamp = now()),
      )
    )

    assertEquals(listOf("task-2", "task-1"), activity.map { it.activityId })
    val task1 = activity.single { it.activityId == "task-1" }
    assertEquals(listOf(1L, 2L), task1.entries.map { it.id })
    assertEquals("response: Rocketship", task1.summary)
  }

  @Test
  fun `standalone rows become their own single-row activities`() {
    val activities = titleActivity(
      listOf(
        entry(1, "task-1", "input: x", LogCategory.TitleRecommendation, timestamp = now()),
        entry(2, null, "capability notice", LogCategory.Info, LogSource.App, timestamp = now()),
      )
    )

    assertEquals(2, activities.size)
    val standalone = activities.first { it.entries.single().activityId == null }
    assertEquals("capability notice", standalone.summary)
    assertNull(standalone.duration)
    assertEquals(LogSource.App, standalone.source)
  }

  @Test
  fun `summary prefers the last response row else the newest row`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "input: synopsis=S", LogCategory.TitleRecommendation, timestamp = now() - 1.days),
        entry(2, "task-1", "response: First title", LogCategory.TitleRecommendation, timestamp = now() - 1.days),
        entry(3, "task-1", "response: Revised title", LogCategory.TitleRecommendation, timestamp = now()),
      )
    ).single()

    assertEquals("response: Revised title", activity.summary)
  }

  @Test
  fun `summary falls back to the newest row when no response surfaced`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "input: synopsis=S", LogCategory.TitleRecommendation, timestamp = now()),
      )
    ).single()

    assertEquals("input: synopsis=S", activity.summary)
  }

  @Test
  fun `duration derives from the clumped timestamps`() {
    val base = Instant.fromEpochMilliseconds(10_000_000_000)
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: InitialQuestions", LogCategory.TaskRun, LogSource.TaskRunner, timestamp = base),
        entry(2, "task-1", "succeeded", LogCategory.TaskRun, LogSource.TaskRunner, timestamp = base + 5.days),
      )
    ).single()

    assertEquals(5.days, activity.duration)
    val single = titleActivity(listOf(entry(1, "task-1", "started: x"))).single()
    assertNull(single.duration)
  }

  @Test
  fun `hasError flags failed error and cancelled rows`() {
    val failed = titleActivity(listOf(entry(1, "task-1", "failed: model exploded"))).single()
    assertTrue(failed.hasError)

    val errorRow = titleActivity(listOf(entry(1, "task-1", "error: boom"))).single()
    assertTrue(errorRow.hasError)

    val cancelled = titleActivity(listOf(entry(1, "task-1", "cancelled"))).single()
    assertTrue(cancelled.hasError)

    val success = titleActivity(listOf(entry(1, "task-1", "succeeded"))).single()
    assertFalse(success.hasError)
  }

  @Test
  fun `category is the activity's shared category`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "input: x", LogCategory.TitleRecommendation),
        entry(2, "task-1", "response: Title", LogCategory.TitleRecommendation),
      )
    ).single()

    assertEquals(LogCategory.TitleRecommendation, activity.category)
  }
}