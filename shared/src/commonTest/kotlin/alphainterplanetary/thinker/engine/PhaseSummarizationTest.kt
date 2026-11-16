package alphainterplanetary.thinker.engine

import alphainterplanetary.thinker.engine.core.PhaseSummarization
import alphainterplanetary.thinker.engine.core.PhaseTranscript
import alphainterplanetary.thinker.engine.core.PlanningContext
import alphainterplanetary.thinker.engine.core.PlanningEngine
import alphainterplanetary.thinker.model.PhaseQuestions
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.model.Round
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.testutil.FakePlanningEngine
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.defaultTestInstant
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class PhaseSummarizationTest {

  @Test
  fun `compactionTranscripts include past phases with summarizable content in order`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "yes"),
      BuiltInPhase.Research to answered("q2", "Second?", "yes"),
      BuiltInPhase.Design to answered("q3", "Third?", "yes"),
    )

    val transcripts = PhaseSummarization.compactionTranscripts(project)

    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Research),
      transcripts.map { it.phase },
    )
    assertEquals(
      listOf(setOf("q1"), setOf("q2")),
      transcripts.map { it.coveredQuestionIds },
    )
  }

  @Test
  fun `exportTranscripts include the current phase and all phases with summarizable content`() {
    val project = project(
      BuiltInPhase.ScopeGoals to answered("q1", "First?", "yes"),
      BuiltInPhase.Design to answered("q3", "Third?", "yes"),
    )

    val transcripts = PhaseSummarization.exportTranscripts(project)

    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Design),
      transcripts.map { it.phase },
    )
  }

  @Test
  fun `exportTranscripts skip phases without summarizable content`() {
    val project = project(
      BuiltInPhase.ScopeGoals to question("ignored", ignoredAt = defaultTestInstant),
      BuiltInPhase.Research to question("open"),
      BuiltInPhase.Design to answered("q3", "Live?", "yes"),
    )

    val transcripts = PhaseSummarization.exportTranscripts(project)

    assertEquals(listOf(BuiltInPhase.Design), transcripts.map { it.phase })
  }

  @Test
  fun `summarize delegates to the engine and returns its result`() = runTest {
    val engine = FakePlanningEngine(canSummarize = true).apply {
      summaries[BuiltInPhase.ScopeGoals] = "A short summary"
    }
    val candidate = candidate(BuiltInPhase.ScopeGoals, answered("q1", "MVP?", "menu planner"))

    val summary = PhaseSummarization.summarize(
      engine = engine,
      title = "Title",
      synopsis = "Synopsis",
      transcript = candidate,
      activityId = "a1",
    )

    assertEquals("A short summary", summary)
    assertEquals(1, engine.summarizeCalls.size)
    assertEquals(BuiltInPhase.ScopeGoals, engine.summarizeCalls.single().phase)
    assertEquals(candidate.transcript, engine.summarizeCalls.single().transcript)
  }

  @Test
  fun `summarizeOrNull returns null when the engine cannot summarize`() = runTest {
    val engine = FakePlanningEngine(canSummarize = false)
    val candidate = candidate(BuiltInPhase.ScopeGoals, answered("q1", "MVP?", "menu planner"))

    val summary = PhaseSummarization.summarizeOrNull(
      engine = engine,
      title = "Title",
      synopsis = "Synopsis",
      transcript = candidate,
      activityId = "a1",
    )

    assertNull(summary)
    assertEquals(0, engine.summarizeCalls.size)
  }

  @Test
  fun `summarizeOrNull trims summaries and returns null for blank results`() = runTest {
    val engine = FakePlanningEngine(canSummarize = true).apply {
      summaries[BuiltInPhase.ScopeGoals] = "  trimmed  "
    }
    val blankEngine = FakePlanningEngine(canSummarize = true).apply {
      summaries[BuiltInPhase.ScopeGoals] = "   "
    }
    val candidate = candidate(BuiltInPhase.ScopeGoals, answered("q1", "MVP?", "menu planner"))

    assertEquals(
      "trimmed",
      PhaseSummarization.summarizeOrNull(engine, "Title", "Synopsis", candidate, "a1"),
    )
    assertNull(
      PhaseSummarization.summarizeOrNull(blankEngine, "Title", "Synopsis", candidate, "a2"),
    )
  }

  @Test
  fun `summarizeOrNull returns null on non-cancellation failure`() = runTest {
    val engine = throwingEngine(IllegalStateException("boom"))
    val candidate = candidate(BuiltInPhase.ScopeGoals, answered("q1", "MVP?", "menu planner"))

    assertNull(
      PhaseSummarization.summarizeOrNull(engine, "Title", "Synopsis", candidate, "a1"),
    )
  }

  @Test
  fun `summarizeOrNull rethrows cancellation`() = runTest {
    val engine = throwingEngine(CancellationException("cancelled"))
    val candidate = candidate(BuiltInPhase.ScopeGoals, answered("q1", "MVP?", "menu planner"))

    assertFailsWith<CancellationException> {
      PhaseSummarization.summarizeOrNull(engine, "Title", "Synopsis", candidate, "a1")
    }
  }

  private fun candidate(phase: Phase, question: Question): PhaseTranscript =
    checkNotNull(
      PhaseTranscript.from(
        PhaseQuestions(phase, listOf(question), emptyList(), Duration.ZERO),
        PlanningContext::render,
      )
    )

  private fun throwingEngine(failure: Throwable): PlanningEngine =
    object : FakePlanningEngine(canSummarize = true) {
      override suspend fun summarizePriorAnswers(
        title: String,
        synopsis: String,
        phase: Phase,
        transcript: String,
        activityId: String,
      ): String = throw failure
    }

  private fun answered(
    id: String,
    text: String,
    answerText: String,
    at: Long = 0,
  ): Question = question(
    id,
    text = text,
    answers = listOf(answer(id, answerText, id = "a-$id")),
    timestamp = defaultTestInstant + at.seconds,
  )

  /**
   * A project whose questions are filed into one round per phase, in the order
   * given, with the last phase's round the one still in progress — so a
   * question's phase reads back through its round the way it does in storage.
   */
  private fun project(vararg entries: Pair<Phase, Question>): Project {
    val phases = entries.map { it.first }.distinct()
    val rounds: List<Round> = phases.mapIndexed { index, phase ->
      round(id = "r-${phase.key}", phase = phase, roundNumber = index + 1)
    }
    return Project(
      id = "p1",
      synopsis = "A synopsis",
      title = "A title",
      status = "Draft",
      questions = entries.map { (phase, q) -> q.copy(roundId = "r-${phase.key}") },
      rounds = rounds.mapIndexed { index, r ->
        if (index == rounds.lastIndex) r else r.complete(defaultTestInstant)
      },
      createdAt = defaultTestInstant,
      updatedAt = defaultTestInstant,
    )
  }
}
