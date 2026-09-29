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
        listOf(task(kind = TaskKind.QuestionGeneration, status = TaskStatus.Succeeded)),
      )
    )
  }

  @Test
  fun `a single active task describes its kind`() {
    assertEquals(
      "Generating questions…",
      activeTaskSummary(listOf(task(kind = TaskKind.QuestionGeneration, status = TaskStatus.Running))),
    )
    assertEquals(
      "Generating questions…",
      activeTaskSummary(listOf(task(kind = TaskKind.QuestionGeneration, status = TaskStatus.Queued))),
    )
    assertEquals(
      "Generating title…",
      activeTaskSummary(listOf(task(kind = TaskKind.TitleRecommendation, status = TaskStatus.Running))),
    )
    assertEquals(
      "Rewriting synopsis…",
      activeTaskSummary(listOf(task(kind = TaskKind.SynopsisRewrite, status = TaskStatus.Queued))),
    )
    assertEquals(
      "Reviewing answers…",
      activeTaskSummary(listOf(task(kind = TaskKind.AutoArchive, status = TaskStatus.Running))),
    )
  }

  @Test
  fun `multiple active tasks count them`() {
    assertEquals(
      "2 tasks running…",
      activeTaskSummary(
        listOf(
          task(kind = TaskKind.QuestionGeneration, status = TaskStatus.Running),
          task(kind = TaskKind.QuestionGeneration, status = TaskStatus.Running),
        )
      ),
    )
  }
}