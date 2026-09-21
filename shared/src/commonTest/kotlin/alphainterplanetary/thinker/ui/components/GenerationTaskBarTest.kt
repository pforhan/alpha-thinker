package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenerationTaskBarTest {

  @Test
  fun `no active tasks shows nothing`() {
    assertNull(activeTaskSummary(emptyList()))
    assertNull(
      activeTaskSummary(
        listOf(task(kind = TaskKind.InitialQuestions, status = TaskStatus.Succeeded)),
      )
    )
  }

  @Test
  fun `a single active task describes its kind`() {
    assertEquals(
      "Creating questions…",
      activeTaskSummary(listOf(task(kind = TaskKind.InitialQuestions, status = TaskStatus.Running))),
    )
    assertEquals(
      "Generating questions…",
      activeTaskSummary(listOf(task(kind = TaskKind.FollowUpQuestions, status = TaskStatus.Queued))),
    )
    assertEquals(
      "Generating title…",
      activeTaskSummary(listOf(task(kind = TaskKind.TitleRecommendation, status = TaskStatus.Running))),
    )
    assertEquals(
      "Checking for more questions…",
      activeTaskSummary(listOf(task(kind = TaskKind.RemainingInPhase, status = TaskStatus.Queued))),
    )
  }

  @Test
  fun `multiple active tasks count them`() {
    assertEquals(
      "2 tasks running…",
      activeTaskSummary(
        listOf(
          task(kind = TaskKind.InitialQuestions, status = TaskStatus.Running),
          task(kind = TaskKind.FollowUpQuestions, status = TaskStatus.Running),
        )
      ),
    )
  }
}