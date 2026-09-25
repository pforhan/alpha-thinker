package alphainterplanetary.thinker.activitylog

import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LogContextTest {

  @Test
  fun `rows carry the context's siblings and marker formatting`() = runTest {
    val log = RecordingActivityLogger()
    val context = log.context(
      activityId = "task-1",
      category = LogCategory.TaskRun,
      source = LogSource.TaskRunner,
      projectId = "p1",
    )

    context.started("InitialQuestions")
    context.closeSucceeded("result=true")

    assertEquals(2, log.entries.size)
    val started = log.entries[0]
    assertEquals("started: InitialQuestions", started.log)
    assertCommon(started, context)

    val terminal = log.entries[1]
    assertEquals("succeeded: result=true", terminal.log)
    assertCommon(terminal, context)
    assertTrue(terminal.timestamp >= started.timestamp)
  }

  @Test
  fun `input prompt response and failure rows render their markers`() = runTest {
    val log = RecordingActivityLogger()
    val context = log.context("task-2", LogCategory.QuestionGeneration, LogSource.Lite)

    context.input("phase=ScopeGoals, synopsis=S")
    context.prompt("SYSTEM\nTitle system")
    context.response("3 questions, done=false")
    context.closeFailed("model exploded")

    assertEquals(
      listOf(
        "input: phase=ScopeGoals, synopsis=S",
        "prompt: SYSTEM\nTitle system",
        "response: 3 questions, done=false",
        "failed: model exploded",
      ),
      log.entries.map { it.log },
    )
  }

  @Test
  fun `a write after a terminal row fails fast`() = runTest {
    val log = RecordingActivityLogger()
    val context = log.context("task-3", LogCategory.TaskRun, LogSource.TaskRunner)

    context.closeSucceeded()

    assertFailsWith<IllegalStateException> {
      context.input("too late")
    }
    assertFailsWith<IllegalStateException> {
      context.closeFailed("double outcome")
    }
  }

  private fun assertCommon(entry: LogEntry, context: LogContext) {
    assertEquals(context.activityId, entry.activityId)
    assertEquals(context.projectId, entry.projectId)
    assertEquals(context.category, entry.category)
    assertEquals(context.source, entry.source)
  }
}