package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.answeredQuestion
import alphainterplanetary.thinker.testutil.draftQuestion
import alphainterplanetary.thinker.testutil.ignoredQuestion
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectTest {

  private fun project(vararg qs: Question) = Project(
    id = "p",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = qs.toList(),
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0)
  )

  private fun firstThree(p: Project): List<String> =
    p.unansweredQuestions.map { it.id }.take(3)

  // ---------- invariant rails ----------

  @Test
  fun `question cannot be committed and a draft at the same time`() {
    assertFailsWith<IllegalArgumentException> {
      Question(
        id = "q",
        text = "Q",
        timestamp = Instant.fromEpochMilliseconds(0),
        roundId = "ctx",
        answerId = "1",
        draftText = "draft",
        draftUpdatedAt = Instant.fromEpochMilliseconds(0),
        answers = listOf(
          alphainterplanetary.thinker.model.Answer(
            id = "1",
            questionId = "q",
            text = "a",
            createdAt = Instant.fromEpochMilliseconds(0),
          )
        ),
      )
    }
  }

  @Test
  fun `question with draft text requires a draft timestamp`() {
    assertFailsWith<IllegalArgumentException> {
      Question(
        id = "q",
        text = "Q",
        timestamp = Instant.fromEpochMilliseconds(0),
        roundId = "ctx",
        draftText = "draft",
        draftUpdatedAt = null,
      )
    }
  }

  @Test
  fun `question answerId must reference a stored answer`() {
    assertFailsWith<IllegalArgumentException> {
      Question(
        id = "q",
        text = "Q",
        timestamp = Instant.fromEpochMilliseconds(0),
        roundId = "ctx",
        answerId = "99",
        answers = emptyList(),
      )
    }
  }

  // ---------- deck mechanics ----------

  @Test
  fun `moveToEnd pushes the question to the back of the list`() {
    val p = project(question("a"), question("b"), question("c"), question("d"))
    assertEquals(listOf("a", "b", "c", "d"), p.questionOrderIds)
    assertEquals(listOf("a", "c", "d", "b"), p.moveToEnd("b").questionOrderIds)
  }

  @Test
  fun `askLater on the first visible question rotates the deck - pulling in the next one`() {
    val p = project(question("a"), question("b"), question("c"), question("d"))
    assertEquals(listOf("a", "b", "c"), firstThree(p))

    val after = p.moveToEnd("a")
    // a moved to the back, so b/c/d now lead the order
    assertEquals(listOf("b", "c", "d", "a"), after.questionOrderIds)
    // and the visible three now pull in d
    assertEquals(listOf("b", "c", "d"), firstThree(after))
  }

  @Test
  fun `shuffle rotates the current visible three to the back`() {
    val p = project(
      question("a"), question("b"), question("c"),
      question("d"), question("e")
    )
    assertEquals(listOf("a", "b", "c"), firstThree(p))

    val after = p.rotateToEnd(listOf("a", "b", "c"))
    assertEquals(listOf("d", "e", "a", "b", "c"), after.questionOrderIds)
    assertEquals(listOf("d", "e", "a"), firstThree(after))
  }

  @Test
  fun `askLater on last question is a no-op`() {
    val p = project(question("a"), question("b"), question("c"))
    val moved = p.moveToEnd("c")
    assertEquals(listOf("a", "b", "c"), moved.questionOrderIds)
  }

  @Test
  fun `unanswered order is preserved across reorders and ignores settled questions`() {
    val p = project(
      answeredQuestion("x"),
      question("a"), question("b"), question("c"), question("d")
    )
    // answered question x is not part of the unanswered deck
    assertEquals(listOf("a", "b", "c"), firstThree(p))

    // a is moved to the end of the full list, so b/c/d now lead the deck
    val after = p.moveToEnd("a")
    assertEquals(listOf("b", "c", "d"), firstThree(after))
  }

  @Test
  fun `moveToEnd preserves the full question set and their payloads`() {
    val p = project(
      question("a"), answeredQuestion("b"), question("c")
    )
    val after = p.moveToEnd("a")
    assertEquals(setOf("a", "b", "c"), after.questions.map { it.id }.toSet())
    assertTrue(after.questions.first { it.id == "b" }.isAnswered)
  }

  @Test
  fun `unansweredQuestions excludes answered and ignored questions`() {
    val p = project(answeredQuestion("a"), ignoredQuestion("b"), question("c"), draftQuestion("d"))

    assertEquals(listOf("c", "d"), p.unansweredQuestions.map { it.id })
  }

  @Test
  fun `activeQuestions excludes only ignored questions`() {
    val p = project(answeredQuestion("a"), ignoredQuestion("b"), question("c"))

    assertEquals(listOf("a", "c"), p.activeQuestions.map { it.id })
  }

  @Test
  fun `allActiveQuestionsAnswered is true when every active question is answered`() {
    val p = project(answeredQuestion("a"), answeredQuestion("b"), ignoredQuestion("c"))

    assertTrue(p.allActiveQuestionsAnswered)
  }

  @Test
  fun `allActiveQuestionsAnswered is false when an active question is unanswered`() {
    val p = project(answeredQuestion("a"), question("b"))

    assertFalse(p.allActiveQuestionsAnswered)
  }

  @Test
  fun `allActiveQuestionsAnswered is false when an active question only has a draft`() {
    val p = project(answeredQuestion("a"), draftQuestion("b"))

    assertFalse(p.allActiveQuestionsAnswered)
  }

  @Test
  fun `allActiveQuestionsAnswered is false when there are no active questions`() {
    val p = project(ignoredQuestion("a"), ignoredQuestion("b"))

    assertFalse(p.allActiveQuestionsAnswered)
  }

  // ---------- rounds ----------

  @Test
  fun `currentRound is the newest in-progress round`() {
    val p = Project(
      id = "p",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = emptyList(),
      rounds = listOf(
        round(id = "r1", projectId = "p", roundNumber = 1),
        round(id = "r2", projectId = "p", roundNumber = 2),
        round(id = "r3", projectId = "p", roundNumber = 3),
      ),
      createdAt = Instant.fromEpochMilliseconds(0),
      updatedAt = Instant.fromEpochMilliseconds(0),
    )

    assertEquals("r3", p.currentRound?.id)
  }

  @Test
  fun `currentRound skips completed rounds`() {
    val p = Project(
      id = "p",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = emptyList(),
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p",
          roundNumber = 1,
          completedAt = Instant.fromEpochMilliseconds(50)
        ),
        round(id = "r2", projectId = "p", roundNumber = 2),
      ),
      createdAt = Instant.fromEpochMilliseconds(0),
      updatedAt = Instant.fromEpochMilliseconds(0),
    )

    assertEquals("r2", p.currentRound?.id)
  }

  @Test
  fun `currentRound is null when there are no rounds`() {
    val p = project(question("a"))

    assertNull(p.currentRound)
  }

  // ---------- phase per question ----------

  @Test
  fun `phaseForQuestion resolves the question's phase through its round`() {
    val p = Project(
      id = "p",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", roundId = "r1")),
      rounds = listOf(
        round(id = "r1", projectId = "p", phase = BuiltInPhase.Research, roundNumber = 1),
      ),
      createdAt = Instant.fromEpochMilliseconds(0),
      updatedAt = Instant.fromEpochMilliseconds(0),
    )

    assertEquals(BuiltInPhase.Research, p.phaseForQuestion(p.questions.first()))
  }

  @Test
  fun `phaseForQuestion falls back to the current phase for an unknown round`() {
    val p = Project(
      id = "p",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", roundId = "r-unknown")),
      rounds = listOf(
        round(id = "r1", projectId = "p", phase = BuiltInPhase.Design, roundNumber = 1),
      ),
      createdAt = Instant.fromEpochMilliseconds(0),
      updatedAt = Instant.fromEpochMilliseconds(0),
    )

    assertEquals(BuiltInPhase.Design, p.phaseForQuestion(p.questions.first()))
  }

  @Test
  fun `phaseForQuestion falls back to the first phase when there are no rounds`() {
    val p = project(question("q1", roundId = "r-unknown"))

    assertEquals(BuiltInPhase.ScopeGoals, p.phaseForQuestion(p.questions.first()))
  }

  // ---------- phase completion ----------

  private fun projectWithRounds(
    phase: BuiltInPhase = BuiltInPhase.ScopeGoals,
    roundPhases: List<BuiltInPhase> = emptyList(),
    roundCount: Int = 1,
    questions: List<Question> = emptyList(),
  ): Project = Project(
    id = "p",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = questions,
    rounds = List(roundCount) { i ->
      round(
        id = "r${i + 1}",
        projectId = "p",
        roundNumber = i + 1,
        phase = roundPhases.getOrElse(i) { phase },
      )
    },
    createdAt = Instant.fromEpochMilliseconds(0),
    updatedAt = Instant.fromEpochMilliseconds(0),
  )

  @Test
  fun `current phase completion counts resolved questions across all phase rounds`() {
    val p = projectWithRounds(
      roundCount = 2,
      questions = listOf(
        question("q1", roundId = "r1"),
        answeredQuestion("q2").copy(roundId = "r1"),
        ignoredQuestion("q3").copy(roundId = "r2"),
        draftQuestion("q4").copy(roundId = "r2"),
      ),
    )

    assertEquals(BuiltInPhase.ScopeGoals, p.currentPhase)
    assertEquals(4, p.currentPhaseQuestionCount)
    // answered + ignored are resolved; drafts and blanks are not
    assertEquals(2, p.currentPhaseResolvedCount)
  }

  @Test
  fun `phase completion counts only rounds of the current phase`() {
    val p = projectWithRounds(
      roundCount = 2,
      roundPhases = listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research),
      questions = listOf(
        question("q1", roundId = "r1"),
        answeredQuestion("q2").copy(roundId = "r2"),
        ignoredQuestion("q3").copy(roundId = "r2"),
      ),
    )

    assertEquals(BuiltInPhase.Research, p.currentPhase)
    assertEquals(2, p.currentPhaseQuestionCount)
    assertEquals(2, p.currentPhaseResolvedCount)
  }

  @Test
  fun `currentPhaseCompletionPercent rounds resolved to the nearest percent`() {
    fun projectWith(resolved: Int, total: Int): Project = projectWithRounds(
      questions = List(total) { i ->
        if (i < resolved) answeredQuestion("q$i").copy(roundId = "r1")
        else question("q$i", roundId = "r1")
      },
    )

    assertEquals(33, projectWith(resolved = 1, total = 3).currentPhaseCompletionPercent)
    assertEquals(67, projectWith(resolved = 2, total = 3).currentPhaseCompletionPercent)
    assertEquals(17, projectWith(resolved = 1, total = 6).currentPhaseCompletionPercent)
    assertEquals(100, projectWith(resolved = 4, total = 4).currentPhaseCompletionPercent)
  }

  @Test
  fun `currentPhaseCompletionPercent is zero when the phase has no questions`() {
    assertEquals(0, projectWithRounds().currentPhaseCompletionPercent)
  }
}