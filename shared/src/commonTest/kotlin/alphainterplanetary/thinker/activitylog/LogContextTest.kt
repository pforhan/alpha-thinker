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
    context.closeSucceeded()

    assertEquals(2, log.entries.size)
    val started = log.entries[0]
    assertEquals("started: InitialQuestions", started.log)
    assertCommon(started, context)

    val terminal = log.entries[1]
    assertEquals("succeeded", terminal.log)
    assertCommon(terminal, context)
    assertTrue(terminal.timestamp >= started.timestamp)
  }

  @Test
  fun `prompt response and failure rows render their markers`() = runTest {
    val log = RecordingActivityLogger()
    val context = log.context("task-2", LogCategory.QuestionGeneration, LogSource.Lite)

    context.prompt("SYSTEM\nTitle system")
    context.prompt("SYSTEM\nQuestions system")
    context.response("3 questions, done=false")
    context.closeFailed("model exploded")

    assertEquals(
      listOf(
        "prompt: SYSTEM\nTitle system",
        "prompt: SYSTEM\nQuestions system",
        "response: 3 questions, done=false",
        "failed: model exploded",
      ),
      log.entries.map { it.log },
    )
  }

  /**
   * The raw payload rides on the outcome row rather than becoming a row of its
   * own: the row's text stays the summary, and the raw is only ever additive.
   */
  @Test
  fun `outcome rows carry their raw payload without adding a row`() = runTest {
    val log = RecordingActivityLogger()
    val context = log.context("task-4", LogCategory.QuestionGeneration, LogSource.RemoteLLM)

    context.response("0 questions, done=true", raw = """["a", ]""")
    context.closeFailed("model exploded")

    assertEquals(2, log.entries.size)
    assertEquals("response: 0 questions, done=true", log.entries[0].log)
    assertEquals("""["a", ]""", log.entries[0].raw)
    assertEquals(null, log.entries[1].raw, "a row with no payload carries no raw")
  }

  @Test
  fun `a write after a terminal row fails fast`() = runTest {
    val log = RecordingActivityLogger()
    val context = log.context("task-3", LogCategory.TaskRun, LogSource.TaskRunner)

    context.closeSucceeded()

    assertFailsWith<IllegalStateException> {
      context.prompt("too late")
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