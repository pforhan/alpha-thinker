package alphainterplanetary.thinker.model

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.answeredQuestion
import alphainterplanetary.thinker.testutil.draftQuestion
import alphainterplanetary.thinker.testutil.ignoredQuestion
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class PhaseStatsTest {

  private val base: Instant = Instant.fromEpochMilliseconds(1_000_000)
  private val now: Instant = base + 99.minutes

  private fun projectWith(
    rounds: List<Round>,
    questions: List<Question> = emptyList(),
  ): Project = Project(
    id = "p",
    synopsis = "s",
    editableTitle = "t",
    status = "Draft",
    questions = questions,
    rounds = rounds,
    createdAt = base,
    updatedAt = base,
  )

  @Test
  fun `phaseStats is empty when the project has no rounds`() {
    assertEquals(emptyList(), projectWith(rounds = emptyList()).phaseStats(now))
  }

  @Test
  fun `phaseStats lists visited phases in library order`() {
    val p = projectWith(
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p",
          phase = BuiltInPhase.Design,
          roundNumber = 1,
          startedAt = base,
          completedAt = base + 10.minutes,
        ),
        round(
          id = "r2",
          projectId = "p",
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 2,
          startedAt = base,
          completedAt = base + 20.minutes,
        ),
      ),
    )

    assertEquals(
      listOf(BuiltInPhase.ScopeGoals, BuiltInPhase.Design),
      p.phaseStats(now).map { it.phase },
    )
  }

  @Test
  fun `phaseStats resolves questions by the phase of their round and counts resolved`() {
    val p = projectWith(
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p",
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 1,
          startedAt = base,
        ),
        round(
          id = "r2",
          projectId = "p",
          phase = BuiltInPhase.Research,
          roundNumber = 2,
          startedAt = base,
        ),
      ),
      questions = listOf(
        answeredQuestion("scopeAnswered").copy(roundId = "r1"),
        ignoredQuestion("scopeIgnored").copy(roundId = "r1"),
        draftQuestion("scopeDraft").copy(roundId = "r1"),
        question("scopeBlank", roundId = "r1"),
        answeredQuestion("researchAnswered").copy(roundId = "r2"),
        question("researchBlank", roundId = "r2"),
      ),
    )

    val stats = p.phaseStats(now)
    val scope = stats.first { it.phase == BuiltInPhase.ScopeGoals }
    assertEquals(2, scope.resolved)
    assertEquals(4, scope.total)
    val research = stats.first { it.phase == BuiltInPhase.Research }
    assertEquals(1, research.resolved)
    assertEquals(2, research.total)
  }

  @Test
  fun `spent sums round durations using completedAt for finished rounds`() {
    val p = projectWith(
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p",
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 1,
          startedAt = base,
          completedAt = base + 120.minutes,
        ),
        round(
          id = "r2",
          projectId = "p",
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 2,
          startedAt = base + 130.minutes,
          completedAt = base + 150.minutes,
        ),
      ),
    )

    assertEquals(140.minutes, p.phaseStats(now).single().spent)
  }

  @Test
  fun `spent uses now as the end of an in-progress round`() {
    val p = projectWith(
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p",
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 1,
          startedAt = base,
        ),
      ),
    )

    assertEquals(99.minutes, p.phaseStats(now).single().spent)
  }

  @Test
  fun `spent never goes below zero for rounds started after the reference instant`() {
    val p = projectWith(
      rounds = listOf(
        round(
          id = "r1",
          projectId = "p",
          phase = BuiltInPhase.ScopeGoals,
          roundNumber = 1,
          startedAt = base + 10.minutes,
        ),
      ),
    )

    assertEquals(0.minutes, p.phaseStats(base).single().spent)
  }
}