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
    topic: LogTopic = LogTopic.QuestionGeneration,
    source: LogSource? = LogSource.RemoteLLM,
    timestamp: Instant = now(),
    step: LogStep = inferStep(log),
  ): LogEntry = LogEntry(
    id = id,
    activityId = activityId,
    topic = topic,
    source = source,
    step = step,
    log = log,
    timestamp = timestamp,
  )

  private fun inferStep(log: String): LogStep = when {
    log.startsWith("started:") -> LogStep.Started
    log == "succeeded" || log.startsWith("succeeded:") -> LogStep.Succeeded
    log.startsWith("failed:") -> LogStep.Failed
    log == "cancelled" -> LogStep.Cancelled
    log.startsWith("prompt:") -> LogStep.Prompt
    log.startsWith("response:") -> LogStep.Response
    else -> LogStep.Note
  }

  /** A title-recommendation activity: input, then a produced title. */
  private fun titleActivity(entries: List<LogEntry>): List<ActivityRecord> =
    ActivityRecord.groupByActivity(entries)

  @Test
  fun `groups rows by activity id ordering newest first`() {
    val old = now() - 10.days
    val newer = now() - 2.days
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "input: synopsis=Build a rocketship",
          LogTopic.TitleRecommendation,
          timestamp = old
        ),
        entry(
          2,
          "task-1",
          "response: Rocketship",
          LogTopic.TitleRecommendation,
          timestamp = old
        ),
        entry(
          3,
          "task-2",
          "input: synopsis=Build a rocketship",
          LogTopic.TitleRecommendation,
          timestamp = newer
        ),
        entry(
          4,
          "task-2",
          "response: Rocketship",
          LogTopic.TitleRecommendation,
          timestamp = newer
        ),
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
        entry(1, "task-1", "input: x", LogTopic.TitleRecommendation, timestamp = now()),
        entry(2, null, "capability notice", LogTopic.Info, LogSource.App, timestamp = now()),
      )
    )

    assertEquals(2, activities.size)
    val standalone = activities.first { it.entries.single().activityId == null }
    assertEquals("capability notice", standalone.summary)
    assertNull(standalone.duration)
    assertEquals(LogSource.App, standalone.source)
  }

  @Test
  fun `source prefers the engine over the task-runner wrapper`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(
          2,
          "task-1",
          "input: phase=ScopeGoals, synopsis=S",
          LogTopic.QuestionGeneration,
          LogSource.RemoteLLM
        ),
        entry(
          3,
          "task-1",
          "response: 1 questions, done=false",
          LogTopic.QuestionGeneration,
          LogSource.RemoteLLM
        ),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals(LogSource.RemoteLLM, activity.source)
  }

  @Test
  fun `source falls back to the wrapper when no engine row filed one`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals(LogSource.TaskRunner, activity.source)
  }

  @Test
  fun `summary prefers the last response row else the newest row`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "input: synopsis=S",
          LogTopic.TitleRecommendation,
          timestamp = now() - 1.days
        ),
        entry(
          2,
          "task-1",
          "response: First title",
          LogTopic.TitleRecommendation,
          timestamp = now() - 1.days
        ),
        entry(
          3,
          "task-1",
          "response: Revised title",
          LogTopic.TitleRecommendation,
          timestamp = now()
        ),
      )
    ).single()

    assertEquals("Recommended title: Revised title", activity.summary)
  }

  @Test
  fun `summary falls back to the newest row when no response surfaced`() {
    val activity = titleActivity(
      listOf(
        entry(1, "task-1", "input: synopsis=S", LogTopic.TitleRecommendation, timestamp = now()),
      )
    ).single()

    assertEquals("input: synopsis=S", activity.summary)
  }

  @Test
  fun `duration derives from the clumped timestamps`() {
    val base = Instant.fromEpochMilliseconds(10_000_000_000)
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner,
          timestamp = base
        ),
        entry(
          2,
          "task-1",
          "succeeded",
          LogTopic.TaskRun,
          LogSource.TaskRunner,
          timestamp = base + 5.days
        ),
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
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      ).map { it.copy(projectId = "p1") }
    ).single()

    assertEquals("p1", activity.projectId)
    assertNull(
      titleActivity(
        listOf(
          entry(
            1,
            "task-1",
            "capability notice",
            LogTopic.Info,
            LogSource.App
          )
        )
      ).single().projectId
    )
  }

  @Test
  fun `category aggregates the distinct work categories in order`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "prompt: SYSTEM\nSummarize", LogTopic.PriorSummary),
        entry(3, "task-1", "response: a summary", LogTopic.PriorSummary),
        entry(4, "task-1", "prompt: SYSTEM\nQuestions", LogTopic.QuestionGeneration),
        entry(5, "task-1", "response: 4 questions, done=false", LogTopic.QuestionGeneration),
        entry(6, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Prior-phase summary • Question generation", activity.topic)
  }

  @Test
  fun `category omits the task-run lifecycle wrapper`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "response: 1 questions, done=false", LogTopic.QuestionGeneration),
        entry(3, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Question generation", activity.topic)
  }

  @Test
  fun `category falls back to Task for a pure lifecycle activity`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals(LogTopic.TaskRun.label, activity.topic)
  }

  @Test
  fun `initial batches summarize the produced count`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "input: phase=ScopeGoals, synopsis=S", LogTopic.QuestionGeneration),
        entry(
          3,
          "task-1",
          "response: 3 questions, done=false\n• first\n• second\n• third",
          LogTopic.QuestionGeneration,
        ),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Generated 3 questions", activity.summary)
  }

  /**
   * A round note that names a phase advance merges into the headline, so the
   * activity reads as the move it was rather than an anonymous batch.
   */
  @Test
  fun `a phase advance summarizes the new phase and the produced count`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(
          2,
          "task-1",
          "Action: advance phase\nPhase: Design",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(3, "task-1", "response: 4 questions, done=false", LogTopic.QuestionGeneration),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Advanced to Design — generated 4 questions", activity.summary)
  }

  /** A "more questions" round is not an advance: the headline stays the batch alone. */
  @Test
  fun `a more-questions round is not read as an advance`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(
          2,
          "task-1",
          "Action: more questions\nPhase: Design",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(3, "task-1", "response: 2 questions, done=false", LogTopic.QuestionGeneration),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Generated 2 questions", activity.summary)
  }

  /** An advance that found nothing still names where it moved to. */
  @Test
  fun `an advance that generated nothing still names the phase`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(
          2,
          "task-1",
          "Action: advance phase\nPhase: Research",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(3, "task-1", "response: 0 questions, done=true", LogTopic.QuestionGeneration),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Advanced to Research — no further questions generated", activity.summary)
  }

  @Test
  fun `an exhausted follow-up batch summarizes the no-further signal`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(
          2,
          "task-1",
          "input: phase=ExecutionPlan, previous questions=12",
          LogTopic.QuestionGeneration
        ),
        entry(3, "task-1", "response: 0 questions, done=true", LogTopic.QuestionGeneration),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("No further questions generated", activity.summary)
  }

  /**
   * An interaction that fans out files a pair of rows per request, and the
   * activity is read off the *last* one — so the request the activity is really
   * about has to be filed last (the summarize-then-generate shape).
   */
  @Test
  fun `a fan-out interaction summarizes off its last request`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "prompt: SYSTEM\nSummarize", LogTopic.QuestionGeneration),
        entry(
          3,
          "task-1",
          "response: a summary of earlier answers",
          LogTopic.QuestionGeneration
        ),
        entry(4, "task-1", "prompt: SYSTEM\nQuestions", LogTopic.QuestionGeneration),
        entry(5, "task-1", "response: 4 questions, done=false", LogTopic.QuestionGeneration),
        entry(6, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Generated 4 questions", activity.summary)
    assertEquals(6, activity.entries.size, "both pairs stay on the activity")
  }

  @Test
  fun `a title recommendation summarizes the recommended title`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: TitleRecommendation",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "input: synopsis=Build a rocketship", LogTopic.TitleRecommendation),
        entry(3, "task-1", "response: Rocketship", LogTopic.TitleRecommendation),
        entry(4, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Recommended title: Rocketship", activity.summary)
  }

  @Test
  fun `a failing generation headlines with its topic`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "input: phase=ScopeGoals, synopsis=S", LogTopic.QuestionGeneration),
        entry(3, "task-1", "failed: model exploded", LogTopic.QuestionGeneration),
        entry(4, "task-1", "failed: model exploded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Question generation failed: model exploded", activity.summary)
  }

  @Test
  fun `a task lifecycle with no engine rows wraps the kind`() {
    val activity = titleActivity(
      listOf(
        entry(
          1,
          "task-1",
          "started: QuestionGeneration",
          LogTopic.TaskRun,
          LogSource.TaskRunner
        ),
        entry(2, "task-1", "succeeded", LogTopic.TaskRun, LogSource.TaskRunner),
      )
    ).single()

    assertEquals("Question generation succeeded", activity.summary)
  }
}