package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.activitylog.EngineActivityEvent
import alphainterplanetary.thinker.activitylog.EngineActivityEventType
import alphainterplanetary.thinker.activitylog.LogCategory
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.util.now
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityLogItemTest {

  private fun event(
    id: Int,
    kind: TaskKind? = null,
    category: LogCategory? = null,
    type: EngineActivityEventType,
    questions: List<String> = emptyList(),
    payload: String? = null,
    result: Boolean? = null,
  ): EngineActivityEvent = EngineActivityEvent(
    eventId = id.toLong(),
    activityId = "task-1",
    kind = kind,
    logCategory = category,
    eventType = type,
    result = result,
    generationPayload = payload,
    suggestedQuestions = if (questions.isEmpty()) null else plainTexts(questions),
    timestamp = now(),
  )

  /** A generation task: lifecycle rows (no category) plus a detail Succeeded row (questions). */
  private fun questionActivity(
    questions: List<String>,
    done: Boolean,
  ): ActivityLogItem = ActivityLogItem(
    activityId = "task-1",
    history = listOf(
      event(1, kind = TaskKind.FollowUpQuestions, type = EngineActivityEventType.Created),
      event(
        2,
        kind = TaskKind.FollowUpQuestions,
        category = LogCategory.RemoteInference,
        type = EngineActivityEventType.Succeeded,
        questions = questions,
        payload = "done=$done",
      ),
      event(3, kind = TaskKind.FollowUpQuestions, type = EngineActivityEventType.Succeeded, result = true),
    ),
    children = emptyList(),
  )

  @Test
  fun `aggregates question totals and done flag across the whole activity`() {
    val item = questionActivity(questions = listOf("one", "two"), done = true)

    assertEquals(2, item.totalQuestions)
    assertEquals("done=true", item.detailPayload)
    assertTrue(item.questionPayloads.single().contains("two"))
  }

  @Test
  fun `reports zero questions when only lifecycle rows exist`() {
    val item = questionActivity(questions = emptyList(), done = false)

    assertEquals(0, item.totalQuestions)
    assertEquals("done=false", item.detailPayload)
  }

  @Test
  fun `reads kind and log category from whichever row recorded them`() {
    val item = questionActivity(questions = listOf("one"), done = true)

    assertEquals(TaskKind.FollowUpQuestions, item.kind)
    assertEquals(LogCategory.RemoteInference, item.logCategory)
  }

  @Test
  fun `reads the capability answer from the task result with payload fallback`() {
    val item = ActivityLogItem(
      activityId = "task-1",
      history = listOf(
        event(1, kind = TaskKind.RemainingInPhase, type = EngineActivityEventType.Created),
        event(
          2,
          kind = TaskKind.RemainingInPhase,
          category = LogCategory.Hardcoded,
          type = EngineActivityEventType.Succeeded,
          payload = "false",
        ),
        event(3, kind = TaskKind.RemainingInPhase, type = EngineActivityEventType.Succeeded, result = false),
      ),
      children = emptyList(),
    )

    assertEquals(false, item.terminal?.result)
    assertEquals("false", item.detailPayload)
  }

  @Test
  fun `decodeQuestionCount tolerates a malformed payload`() {
    assertEquals(0, "not json".decodeQuestionCount())
    assertEquals(3, plainTexts(listOf("a", "b", "c")).decodeQuestionCount())
  }

  private fun plainTexts(texts: List<String>): String = Json.encodeToString(texts)
}