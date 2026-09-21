package alphainterplanetary.thinker.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRunnerTest {

  @Test
  fun `enqueue runs the body and transitions queued running succeeded`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    var bodyRan = false

    runner.enqueue("p1", TaskKind.InitialQuestions) {
      bodyRan = true
      assertEquals(
        TaskStatus.Running,
        runner.tasks.value.single { it.projectId == "p1" }.status,
      )
    }
    assertEquals(TaskStatus.Queued, runner.tasks.value.single().status)

    testScheduler.advanceUntilIdle()

    val done = runner.tasks.value.single()
    assertEquals(TaskStatus.Succeeded, done.status)
    assertNotNull(done.startedAt)
    assertNotNull(done.finishedAt)
    assertTrue(done.isFinished)
    assertNull(done.result, "plain enqueue carries no boolean answer")
    assertTrue(bodyRan)
  }

  @Test
  fun `bodies run serially so a later task stays queued behind a slow peer`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val slow = runner.enqueue("p1", TaskKind.InitialQuestions) {
      delay(1_000)
    }
    val fast = runner.enqueue("p1", TaskKind.FollowUpQuestions) {}

    testScheduler.runCurrent()

    assertEquals(TaskStatus.Running, runner.tasks.value.single { it.id == slow.id }.status)
    assertEquals(TaskStatus.Queued, runner.tasks.value.single { it.id == fast.id }.status)

    testScheduler.advanceUntilIdle()

    val done = runner.tasks.value
    assertEquals(
      listOf(slow.id, fast.id),
      done.map { it.id },
      "insertion order is preserved",
    )
    assertTrue(done.all { it.isFinished })
  }

  @Test
  fun `enqueueResult folds the boolean answer into the terminal task`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    runner.enqueueResult("p1", TaskKind.RemainingInPhase) { true }

    testScheduler.advanceUntilIdle()

    val done = runner.tasks.value.single()
    assertEquals(TaskStatus.Succeeded, done.status)
    assertNotNull(done.startedAt)
    assertNotNull(done.finishedAt)
    assertEquals(true, done.result)
  }

  @Test
  fun `a throwing body fails the task with its message`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    runner.enqueue("p1", TaskKind.FollowUpQuestions) {
      error("model exploded")
    }

    testScheduler.advanceUntilIdle()

    val failed = runner.tasks.value.single()
    assertEquals(TaskStatus.Failed, failed.status)
    assertEquals("model exploded", failed.error)
    assertNotNull(failed.finishedAt)
    assertTrue(failed.isFinished)
  }

  @Test
  fun `tasksFor filters by project and keeps insertion order`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val a1 = runner.enqueue("p1", TaskKind.InitialQuestions) {}
    val a2 = runner.enqueue("p1", TaskKind.FollowUpQuestions) {}
    val b1 = runner.enqueue("p2", TaskKind.SynopsisRewrite) {}

    testScheduler.advanceUntilIdle()

    assertEquals(listOf(a1.id, a2.id), runner.tasksFor("p1").first().map { it.id })
    assertEquals(listOf(b1.id), runner.tasksFor("p2").first().map { it.id })
  }

  @Test
  fun `setProgress is observable on the task`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    runner.enqueue("p1", TaskKind.SynopsisRewrite) {
      val id = runner.tasks.value.single { it.kind == TaskKind.SynopsisRewrite }.id
      runner.setProgress(id, 0.5f)
      assertEquals(0.5f, runner.tasks.value.single { it.id == id }.progress)
    }

    testScheduler.advanceUntilIdle()

    val done = runner.tasks.value.single()
    assertEquals(TaskStatus.Succeeded, done.status)
    assertEquals(0.5f, done.progress)
  }
}