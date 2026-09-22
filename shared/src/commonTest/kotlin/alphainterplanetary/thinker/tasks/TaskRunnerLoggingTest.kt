package alphainterplanetary.thinker.tasks

import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.testutil.RecordingActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRunnerLoggingTest {

  @Test
  fun `a successful task appends created then succeeded lifecycle events`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    val task = runner.enqueue("p1", TaskKind.InitialQuestions) {}

    testScheduler.advanceUntilIdle()

    assertEquals(
      listOf(EngineActivityEventType.Created, EngineActivityEventType.Succeeded),
      log.events.map { it.eventType },
      "transitions append in order, nothing else",
    )
    assertTrue(
      log.events.all { it.activityId == task.id },
      "every lifecycle event joins the task's activity id",
    )
    assertTrue(
      log.events.all { it.projectId == "p1" && it.kind == TaskKind.InitialQuestions },
      "lifecycle rows carry the task's project and kind",
    )
    assertTrue(log.events.first().isTerminal.not())
    assertTrue(log.events.last().isTerminal)
  }

  @Test
  fun `a failing task appends a failed lifecycle event with its error`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    val task = runner.enqueue("p1", TaskKind.FollowUpQuestions) {
      error("model exploded")
    }

    testScheduler.advanceUntilIdle()

    val terminal = log.events.last()
    assertEquals(EngineActivityEventType.Failed, terminal.eventType)
    assertEquals(task.id, terminal.activityId)
    assertEquals("model exploded", terminal.error)
  }

  @Test
  fun `setProgress appends a progress event between created and terminal`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    runner.enqueue("p1", TaskKind.SynopsisRewrite) { taskId ->
      runner.setProgress(taskId, 0.5f)
      assertEquals(0.5f, runner.tasks.value.single { it.id == taskId }.progress)
    }

    testScheduler.advanceUntilIdle()

    assertEquals(
      listOf(EngineActivityEventType.Created, EngineActivityEventType.Progress, EngineActivityEventType.Succeeded),
      log.events.map { it.eventType },
    )
    assertEquals(0.5f, log.events[1].progress)
  }

  @Test
  fun `enqueueResult rides the boolean answer on the succeeded event`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    runner.enqueueResult("p1", TaskKind.RemainingInPhase) { true }

    testScheduler.advanceUntilIdle()

    assertEquals(EngineActivityEventType.Succeeded, log.events.last().eventType)
    assertEquals(true, log.events.last().result)
  }

  @Test
  fun `a cancelled task terminates the activity instead of leaving it live`() = runTest {
    val log = RecordingActivityLog()
    val runner = TaskRunner(CoroutineScope(coroutineContext), activityLog = log)

    runner.enqueue("p1", TaskKind.InitialQuestions) {
      throw kotlin.coroutines.cancellation.CancellationException("stop")
    }

    testScheduler.advanceUntilIdle()

    assertEquals(EngineActivityEventType.Cancelled, log.events.last().eventType)
    assertEquals("Task cancelled", log.events.last().error)
    assertNull(log.events.last().result)
  }

  @Test
  fun `no log injected means no log events and tasks still complete`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    runner.enqueue("p1", TaskKind.InitialQuestions) {}
    testScheduler.advanceUntilIdle()

    assertTrue(runner.tasks.value.single().isFinished)
  }
}