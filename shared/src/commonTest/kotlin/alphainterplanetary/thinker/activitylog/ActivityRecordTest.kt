package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.util.now
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class ActivityRecordTest {

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
  private fun titleActivity(entries: List<LogEntry>): List<ActivityRecord> =
    ActivityRecord.groupByActivity(entries)

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
    assertEquals("Recommended title: Rocketship", task1.summary)
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

    assertEquals("Recommended title: Revised title", activity.summary)
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
  fun `hasError flags failed and cancelled rows`() {
    val failed = titleActivity(listOf(entry(1, "task-1", "failed: model exploded"))).single()
    assertTrue(failed.hasError)

    val cancelled = titleActivity(listOf(entry(1, "task-1", "cancelled"))).single()
    assertTrue(cancelled.hasError)

    val success = titleActivity(listOf(entry(1, "task-1", "succeeded"))).single()
    assertFalse(success.hasError)
  }

  @Test
  fun `projectId is the first populated project id across the rows`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: InitialQuestions", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "succeeded", LogCategory.TaskRun, LogSource.TaskRunner),
      ).map { it.copy(projectId = "p1") }
    ).single()

    assertEquals("p1", activity.projectId)
    assertNull(titleActivity(listOf(entry(1, "task-1", "capability notice", LogCategory.Info, LogSource.App))).single().projectId)
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

  @Test
  fun `remaining-in-phase answers summarize the phase capacity`() {
    val noMore = titleActivity(
      listOf(
        entry(1, "task-1", "started: RemainingInPhase", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "input: phase=ScopeGoals, previous questions=10", LogCategory.CapabilityCheck),
        entry(3, "task-1", "response: canProduceMore=false, phase=Scope & Goals", LogCategory.CapabilityCheck),
        entry(4, "task-1", "succeeded: result=false", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()
    assertEquals("No more questions available in phase (Scope & Goals)", noMore.summary)

    val stillOpen = titleActivity(
      listOf(
        entry(1, "task-2", "started: RemainingInPhase", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-2", "succeeded: result=true", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()
    assertEquals("More questions available in phase", stillOpen.summary)
  }

  @Test
  fun `initial batches summarize the produced count`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: InitialQuestions", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "input: phase=ScopeGoals, synopsis=S", LogCategory.QuestionGeneration),
        entry(
          3,
          "task-1",
          "response: 3 questions, done=false\n• first\n• second\n• third",
          LogCategory.QuestionGeneration,
        ),
        entry(4, "task-1", "succeeded", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Generated 3 initial questions", activity.summary)
  }

  @Test
  fun `an exhausted follow-up batch summarizes the no-further signal`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: FollowUpQuestions", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "input: phase=ExecutionPlan, previous questions=12", LogCategory.QuestionGeneration),
        entry(3, "task-1", "response: 0 questions, done=true", LogCategory.QuestionGeneration),
        entry(4, "task-1", "succeeded", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("No further follow-up questions generated", activity.summary)
  }

  @Test
  fun `a title recommendation summarizes the recommended title`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: TitleRecommendation", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "input: synopsis=Build a rocketship", LogCategory.TitleRecommendation),
        entry(3, "task-1", "response: Rocketship", LogCategory.TitleRecommendation),
        entry(4, "task-1", "succeeded", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Recommended title: Rocketship", activity.summary)
  }

  @Test
  fun `a failing generation headlines with its topic`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: InitialQuestions", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "input: phase=ScopeGoals, synopsis=S", LogCategory.QuestionGeneration),
        entry(3, "task-1", "failed: model exploded", LogCategory.QuestionGeneration),
        entry(4, "task-1", "failed: model exploded", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Initial question generation failed: model exploded", activity.summary)
  }

  @Test
  fun `a task lifecycle with no engine rows wraps the kind`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "started: InitialQuestions", LogCategory.TaskRun, LogSource.TaskRunner),
        entry(2, "task-1", "succeeded", LogCategory.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Initial question generation succeeded", activity.summary)
  }
}