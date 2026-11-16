package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.engine.core.PhaseTranscript
import alphainterplanetary.thinker.engine.core.PlanningContext
import alphainterplanetary.thinker.util.TokenEstimator
import alphainterplanetary.thinker.model.PhaseQuestions
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PhaseTranscriptTest {

  @Test
  fun `from returns null when there is no summarizable content`() {
    val questions = listOf(
      question("ignored", ignoredAt = defaultTestInstant),
      question("open"),
    )

    assertNull(
      PhaseTranscript.from(
        PhaseQuestions(BuiltInPhase.ScopeGoals, questions, emptyList(), Duration.ZERO),
        PlanningContext::render,
      )
    )
  }

  @Test
  fun `from builds a transcript from answered and draft non-ignored questions`() {
    val questions = listOf(
      answered("a1", "Answered?", "yes", at = 0),
      question(
        "d1",
        "Draft?",
        draftText = "maybe",
        draftUpdatedAt = defaultTestInstant,
        timestamp = defaultTestInstant + 1.seconds,
      ),
      question("ignored", "Ignored?", ignoredAt = defaultTestInstant),
      question("open", "Open?", timestamp = defaultTestInstant + 2.seconds),
    )

    val transcript = PhaseTranscript.from(
      PhaseQuestions(BuiltInPhase.ScopeGoals, questions, emptyList(), Duration.ZERO),
      PlanningContext::render,
    )

    assertNotNull(transcript)
    assertEquals(BuiltInPhase.ScopeGoals, transcript.phase)
    assertEquals(listOf("a1", "d1"), transcript.questions.map { it.id })
    assertEquals(setOf("a1", "d1"), transcript.coveredQuestionIds)
    assertEquals(
      PlanningContext.render(transcript.questions),
      transcript.transcript,
    )
    assertEquals(
      TokenEstimator.estimateTokens(transcript.transcript),
      transcript.estimatedTokens,
    )
  }

  private fun answered(
    id: String,
    text: String,
    answerText: String,
    at: Long = 0,
  ) = question(
    id,
    text = text,
    answers = listOf(answer(id, answerText, id = "a-$id")),
    timestamp = defaultTestInstant + at.seconds,
  )
}
