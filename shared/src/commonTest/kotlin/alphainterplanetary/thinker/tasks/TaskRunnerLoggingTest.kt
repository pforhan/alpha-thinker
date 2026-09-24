package alphainterplanetary.thinker.tasks

import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.testutil.RecordingActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRunnerLoggingTest {

  @Test
  fun `a successful task appends started then succeeded rows`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    val task = runner.enqueue("p1", TaskKind.InitialQuestions) {}

    testScheduler.advanceUntilIdle()

    assertEquals(
      listOf("started: InitialQuestions", "succeeded"),
      log.entries.map { it.log },
      "lifecycle rows append in order, nothing else",
    )
    assertTrue(
      log.entries.all { it.activityId == task.id },
      "every lifecycle row joins the task's activity id",
    )
    assertTrue(
      log.entries.all { it.projectId == "p1" },
      "lifecycle rows carry the task's project",
    )
    assertTrue(
      log.entries.all { it.category == LogCategory.TaskRun && it.source == LogSource.TaskRunner },
      "lifecycle rows report the TaskRun category and TaskRunner source",
    )
  }

  @Test
  fun `a failing task appends a failed row with its error`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    val task = runner.enqueue("p1", TaskKind.FollowUpQuestions) {
      error("model exploded")
    }

    testScheduler.advanceUntilIdle()

    val terminal = log.entries.last()
    assertEquals("failed: model exploded", terminal.log)
    assertEquals(task.id, terminal.activityId)
  }

  @Test
  fun `setProgress updates the live task without adding a log row`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    runner.enqueue("p1", TaskKind.SynopsisRewrite) { taskId ->
      runner.setProgress(taskId, 0.5f)
      assertEquals(0.5f, runner.tasks.value.single { it.id == taskId }.progress)
    }

    testScheduler.advanceUntilIdle()

    assertEquals(
      listOf("started: SynopsisRewrite", "succeeded"),
      log.entries.map { it.log },
      "progress ticks are UI-only; the durable log keeps just start + terminal",
    )
  }

  @Test
  fun `enqueueResult rides the boolean answer on the succeeded row`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    runner.enqueueResult("p1", TaskKind.RemainingInPhase) { true }

    testScheduler.advanceUntilIdle()

    assertEquals(listOf("started: RemainingInPhase", "succeeded: result=true"), log.entries.map { it.log })
  }

  @Test
  fun `a cancelled task terminates the activity instead of leaving it live`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    runner.enqueue("p1", TaskKind.InitialQuestions) {
      throw kotlin.coroutines.cancellation.CancellationException("stop")
    }

    testScheduler.advanceUntilIdle()

    assertEquals(listOf("started: InitialQuestions", "cancelled"), log.entries.map { it.log })
  }

  @Test
  fun `no log injected means no log rows and tasks still complete`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    runner.enqueue("p1", TaskKind.InitialQuestions) {}
    testScheduler.advanceUntilIdle()

    assertTrue(runner.tasks.value.single().isFinished)
  }
}