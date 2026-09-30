package alphainterplanetary.thinker.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskRunnerTest {

  @Test
  fun `enqueue runs the body and transitions queued running succeeded`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    var bodyRan = false

    runner.enqueue("p1", TaskKind.QuestionGeneration) {
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
    assertTrue(bodyRan)
  }

  @Test
  fun `bodies run serially so a later task stays queued behind a slow peer`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val slow = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      delay(1_000)
    }
    val fast = runner.enqueue("p1", TaskKind.QuestionGeneration) {}

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
  fun `existing kinds default to the serial engine group`() {
    assertEquals(
      ConcurrencyGroup.Engine,
      TaskKind.entries.map { it.group }.distinct().single(),
    )
    assertEquals(
      listOf(1, 4),
      listOf(ConcurrencyGroup.Engine.concurrency, ConcurrencyGroup.Remote.concurrency),
    )
  }

  @Test
  fun `remote-group tasks across projects run concurrently`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val a = runner.enqueue("p1", TaskKind.SynopsisRewrite, group = ConcurrencyGroup.Remote) {
      delay(1_000)
    }
    val b = runner.enqueue("p2", TaskKind.QuestionGeneration, group = ConcurrencyGroup.Remote) {
      delay(1_000)
    }

    testScheduler.runCurrent()

    assertEquals(TaskStatus.Running, runner.tasks.value.single { it.id == a.id }.status)
    assertEquals(TaskStatus.Running, runner.tasks.value.single { it.id == b.id }.status)

    testScheduler.advanceUntilIdle()

    assertTrue(runner.tasks.value.all { it.isFinished })
  }

  @Test
  fun `engine group is shared across projects so only one engine task runs at a time`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val slow = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      delay(1_000)
    }
    val other = runner.enqueue("p2", TaskKind.QuestionGeneration) {
      delay(500)
    }

    testScheduler.runCurrent()

    assertEquals(TaskStatus.Running, runner.tasks.value.single { it.id == slow.id }.status)
    assertEquals(TaskStatus.Queued, runner.tasks.value.single { it.id == other.id }.status)

    testScheduler.advanceUntilIdle()

    assertTrue(runner.tasks.value.all { it.isFinished })
  }

  @Test
  fun `same-project tasks never run concurrently even across groups`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val engine = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      delay(1_000)
    }
    val remote = runner.enqueue("p1", TaskKind.QuestionGeneration, group = ConcurrencyGroup.Remote) {
      delay(500)
    }

    testScheduler.runCurrent()

    assertEquals(TaskStatus.Running, runner.tasks.value.single { it.id == engine.id }.status)
    assertEquals(TaskStatus.Queued, runner.tasks.value.single { it.id == remote.id }.status)

    testScheduler.advanceUntilIdle()

    assertTrue(runner.tasks.value.all { it.isFinished })
  }

  @Test
  fun `a throwing body fails the task with its message`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    runner.enqueue("p1", TaskKind.QuestionGeneration) {
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
    val a1 = runner.enqueue("p1", TaskKind.QuestionGeneration) {}
    val a2 = runner.enqueue("p1", TaskKind.QuestionGeneration) {}
    val b1 = runner.enqueue("p2", TaskKind.SynopsisRewrite) {}

    testScheduler.advanceUntilIdle()

    assertEquals(listOf(a1.id, a2.id), runner.tasksFor("p1").first().map { it.id })
    assertEquals(listOf(b1.id), runner.tasksFor("p2").first().map { it.id })
  }

  @Test
  fun `await returns once the task succeeds`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val task = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      delay(1_000)
    }

    var awaited: GenerationTask? = null
    val waiter = launch { awaited = runner.await(task.id) }

    testScheduler.runCurrent()
    assertNull(awaited, "await suspends while the task is still running")

    testScheduler.advanceUntilIdle()
    waiter.join()

    assertEquals(TaskStatus.Succeeded, awaited?.status)
    assertEquals(task.id, awaited?.id)
    assertNotNull(awaited?.finishedAt)
  }

  @Test
  fun `await on an already-finished task returns immediately`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val task = runner.enqueue("p1", TaskKind.QuestionGeneration) {}
    testScheduler.advanceUntilIdle()

    val awaited = runner.await(task.id)

    assertEquals(TaskStatus.Succeeded, awaited.status)
  }

  @Test
  fun `await throws TaskFailed carrying the task's error`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val task = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      error("model exploded")
    }

    val thrown = assertFailsWith<TaskFailed> { runner.await(task.id) }

    assertEquals(task.id, thrown.task.id)
    assertEquals(TaskStatus.Failed, thrown.task.status)
    assertEquals("model exploded", thrown.task.error)
    assertEquals("model exploded", thrown.message)
  }

  @Test
  fun `await on an unknown id fails instead of hanging`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))

    assertFailsWith<IllegalArgumentException> { runner.await("nope") }
  }

  @Test
  fun `await sequences generations in order`() = runTest {
    val runner = TaskRunner(CoroutineScope(coroutineContext))
    val order = mutableListOf<String>()

    val first = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      delay(1_000)
      order += "first"
    }
    val second = runner.enqueue("p1", TaskKind.QuestionGeneration) {
      order += "second"
    }

    val waiter = launch {
      runner.await(first.id)
      order += "after-first"
      runner.await(second.id)
      order += "after-second"
    }

    testScheduler.advanceUntilIdle()
    waiter.join()

    assertEquals(listOf("first", "after-first", "second", "after-second"), order)
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