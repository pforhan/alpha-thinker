package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.tasks.TaskKind
import alphainterplanetary.thinker.tasks.TaskStatus
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import alphainterplanetary.thinker.testutil.task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenerationProblemTest {

  @Test
  fun `a project with questions and no failed task has no problem`() {
    val project = project(rounds = listOf(round("r1")))

    assertNull(generationProblemKind(project, emptyList()))
  }

  @Test
  fun `a task still in flight is not a problem`() {
    val project = project(rounds = listOf(round("r1", outcome = RoundOutcome.Failed, outcomeDetail = "boom")))

    val running = task(kind = TaskKind.FollowUpQuestions, status = TaskStatus.Running)

    assertNull(generationProblemKind(project, listOf(running)))
  }

  @Test
  fun `a failed title task on a project with no title is a title problem`() {
    val project = project(editableTitle = "")

    val failed = task(kind = TaskKind.TitleRecommendation, status = TaskStatus.Failed, error = "boom")

    assertEquals(GenerationProblemKind.Title, generationProblemKind(project, listOf(failed)))
  }

  @Test
  fun `a failed title task is not a problem once the project has a title`() {
    val project = project(editableTitle = "User typed this")

    val failed = task(kind = TaskKind.TitleRecommendation, status = TaskStatus.Failed, error = "boom")

    assertNull(generationProblemKind(project, listOf(failed)))
  }

  @Test
  fun `a failed round is a question problem`() {
    val project = project(
      rounds = listOf(
        round("r1", outcome = RoundOutcome.Failed, outcomeDetail = "The planner came back with no new questions."),
      ),
    )

    assertEquals(GenerationProblemKind.Questions, generationProblemKind(project, emptyList()))
  }

  @Test
  fun `a failed question task is a problem even when the round write did not land`() {
    val project = project(rounds = listOf(round("r1")))

    val failed = task(kind = TaskKind.FollowUpQuestions, status = TaskStatus.Failed, error = "HTTP 500")

    assertEquals(GenerationProblemKind.Questions, generationProblemKind(project, listOf(failed)))
  }

  @Test
  fun `a stale failed question task is not a problem once the round has questions`() {
    val project = project(
      questions = listOf(question("q1", roundId = "r1")),
      rounds = listOf(round("r1")),
    )

    val failed = task(kind = TaskKind.FollowUpQuestions, status = TaskStatus.Failed, error = "HTTP 500")

    assertNull(generationProblemKind(project, listOf(failed)))
  }

  @Test
  fun `a failed non-question task with no round failure is not a problem`() {
    val project = project(rounds = listOf(round("r1")))

    val failed = task(kind = TaskKind.SynopsisRewrite, status = TaskStatus.Failed, error = "boom")

    assertNull(generationProblemKind(project, listOf(failed)))
  }

  @Test
  fun `tasks belonging to another project are ignored`() {
    val project = project(editableTitle = "")

    val otherProjectFailed = task(
      projectId = "p2",
      kind = TaskKind.TitleRecommendation,
      status = TaskStatus.Failed,
      error = "Someone else's error",
    )

    assertNull(generationProblemKind(project, listOf(otherProjectFailed)))
  }

  @Test
  fun `a missing title problem wins over a question problem`() {
    val project = project(
      editableTitle = "",
      rounds = listOf(round("r1", outcome = RoundOutcome.Failed, outcomeDetail = "boom")),
    )

    val failed = task(kind = TaskKind.TitleRecommendation, status = TaskStatus.Failed, error = "boom")

    assertEquals(GenerationProblemKind.Title, generationProblemKind(project, listOf(failed)))
  }

  @Test
  fun `each kind has its own headline so the list chip can name it`() {
    assertEquals("Couldn't suggest a title", GenerationProblemKind.Title.headline)
    assertEquals("Couldn't create questions", GenerationProblemKind.Questions.headline)
  }

  private fun project(
    editableTitle: String = "A project",
    questions: List<Question> = emptyList(),
    rounds: List<Round> = emptyList(),
  ): Project = Project(
    id = "p1",
    synopsis = "Synopsis",
    editableTitle = editableTitle,
    status = "Draft",
    questions = questions,
    rounds = rounds,
    createdAt = defaultTestInstant,
    updatedAt = defaultTestInstant,
  )
}
