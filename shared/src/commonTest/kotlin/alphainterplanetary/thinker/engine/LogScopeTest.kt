package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.activitylog.LogSource
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.testutil.RecordingActivityLogger
import alphainterplanetary.thinker.util.now
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogScopeTest {

  @Test
  fun `a request files its prompt row and then the outcome row for that reply`() = runTest {
    val log = RecordingActivityLogger()
    val scope = LogScope { questionLog(log, "t1") }

    withContext(scope) {
      val request = logRequest("SYSTEM\nYou are a project-planning assistant")
      request?.responded("1 question, done=false", """["What is the MVP?"]""")
    }

    assertEquals(
      listOf(
        "prompt: SYSTEM\nYou are a project-planning assistant",
        "response: 1 question, done=false",
      ),
      log.entries.map { it.log },
    )
    assertEquals("""["What is the MVP?"]""", log.entries[1].raw, "the reply rides on its own row")
    assertTrue(log.entries.all { it.activityId == "t1" })
  }

  @Test
  fun `a failed request keeps the reply that caused the failure`() = runTest {
    val log = RecordingActivityLogger()
    val scope = LogScope { questionLog(log, "t2") }

    withContext(scope) {
      logRequest("SYSTEM\nask")?.failed("couldn't be read", "I am not able to help.")
    }

    assertEquals("failed: couldn't be read", log.entries.last().log)
    assertEquals("I am not able to help.", log.entries.last().raw)
  }

  @Test
  fun `a blank reply is not filed as a raw payload`() = runTest {
    val log = RecordingActivityLogger()
    val scope = LogScope { questionLog(log, "t3") }

    withContext(scope) {
      logRequest("SYSTEM\nask")?.responded("The model returned an empty title", "   ")
    }

    assertNull(log.entries.last().raw, "an empty payload reads as a truncated one")
  }

  /**
   * The fan-out case: two requests in flight at once, each with its own prompt
   * and its own reply. Nothing is shared between them, so a reply can only ever
   * land on the row of the request that produced it — here with the *later*
   * request finishing first, which is the order that would cross a shared
   * capture list.
   */
  @Test
  fun `parallel requests each keep their own reply`() = runTest {
    val log = RecordingActivityLogger()
    val scope = LogScope { questionLog(log, "t4") }

    withContext(scope) {
      coroutineScope {
        val slow = async { ask(delayMillis = 50) }
        val fast = async { ask(delayMillis = 0) }
        slow.await()
        fast.await()
      }
    }

    assertEquals(4, log.entries.size, "two requests, one prompt + one outcome row each")
    val replies = log.entries.filter { it.log.startsWith("response:") }.map { entry ->
      entry.raw.orEmpty()
    }
    assertEquals(1, replies.count { it.contains("first") })
    assertEquals(1, replies.count { it.contains("second") })
  }

  /** The interaction's own row is offered until a request has filed one of its own. */
  @Test
  fun `the fallback row is offered only while no request has reported`() = runTest {
    val log = RecordingActivityLogger()
    val scope = LogScope { questionLog(log, "t5") }

    val fallback = assertNotNull(
      scope.fallback(),
      "an engine that never speaks to a model still records its result",
    )
    fallback.response("5 questions, done=false")

    scope.request("SYSTEM\nask")
    assertNull(scope.fallback(), "the request already filed this interaction's rows")
  }

  @Test
  fun `a request opened with no scope installed files nothing`() = runTest {
    assertNull(logRequest("SYSTEM\nask"), "nothing is observing an undecorated call")
  }

  private suspend fun ask(delayMillis: Long): Question {
    val request = logRequest("SYSTEM\nask")
    delay(delayMillis)
    val reply = """["A question ${if (delayMillis > 0) "first" else "second"}"]"""
    request?.responded("1 question, done=false", reply)
    return Question(id = reply, text = reply, timestamp = now(), roundId = "r1")
  }
}

private fun questionLog(log: RecordingActivityLogger, activityId: String) =
  log.context(activityId, LogCategory.QuestionGeneration, LogSource.RemoteLLM)
