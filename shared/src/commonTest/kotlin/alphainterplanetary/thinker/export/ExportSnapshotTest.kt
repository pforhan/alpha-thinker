package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ExportSnapshotTest {
  private val t0 = Instant.fromEpochMilliseconds(0)
  private val t1 = Instant.fromEpochMilliseconds(1_000)
  private val t2 = Instant.fromEpochMilliseconds(2_000)
  private val t3 = Instant.fromEpochMilliseconds(3_000)

  private fun project(
    id: String = "p1",
    title: String = "My Project",
    synopsis: String = "Synopsis",
    updatedAt: Instant = t3,
    questions: List<Question> = emptyList(),
    rounds: List<alphainterplanetary.thinker.model.Round> = emptyList(),
  ) = Project(
    id = id,
    synopsis = synopsis,
    title = title,
    status = "Draft",
    questions = questions,
    rounds = rounds,
    createdAt = t0,
    updatedAt = updatedAt,
  )

  @Test
  fun capturesHeaderRevisionAndSchemaVersion() {
    val snapshot = ExportSnapshot.from(
      project(id = "proj", title = "Title", synopsis = "Syn", updatedAt = t2)
        .copy(status = "Draft", createdAt = t0),
    )

    assertEquals(EXPORT_SNAPSHOT_SCHEMA_VERSION, snapshot.schemaVersion)
    assertEquals("proj", snapshot.projectId)
    assertEquals("Title", snapshot.title)
    assertEquals("Syn", snapshot.synopsis)
    assertEquals("Draft", snapshot.status)
    assertEquals(t0.toEpochMilliseconds(), snapshot.createdAtEpochMs)
    assertEquals(t2.toEpochMilliseconds(), snapshot.revisionUpdatedAtEpochMs)
    assertTrue(snapshot.phases.isEmpty())
    assertTrue(snapshot.rounds.isEmpty())
  }

  @Test
  fun ordersTraversedPhasesAndQuestionsDeterministically() {
    val scopeRound = round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals, roundNumber = 1)
    val researchRound = round(id = "r2", projectId = "p1", phase = BuiltInPhase.Research, roundNumber = 2)
    val qLate = question(id = "q2", text = "Second", roundId = "r1", timestamp = t2)
    val qEarly = question(id = "q1", text = "First", roundId = "r1", timestamp = t1)
    val qResearch = question(id = "q3", text = "Research Q", roundId = "r2", timestamp = t1)

    val snapshot = ExportSnapshot.from(
      project(
        questions = listOf(qLate, qEarly, qResearch),
        rounds = listOf(scopeRound, researchRound),
      )
    )

    assertEquals(listOf(BuiltInPhase.ScopeGoals.key, BuiltInPhase.Research.key), snapshot.phases.map { it.phaseKey })
    assertEquals(listOf(BuiltInPhase.ScopeGoals.order, BuiltInPhase.Research.order), snapshot.phases.map { it.phaseOrder })
    assertEquals(listOf("q1", "q2"), snapshot.phases[0].questions.map { it.id })
    assertEquals(listOf("q3"), snapshot.phases[1].questions.map { it.id })
  }

  @Test
  fun capturesCanonicalQuestionStateIncludingHistoryDraftAndFlags() {
    val r1 = round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals)
    val a1 = answer("qAnswered", "Old", id = "a1", createdAt = t1)
    val a2 = answer("qAnswered", "Current", id = "a2", createdAt = t2)
    val answered = question(
      id = "qAnswered",
      text = "Answered?",
      roundId = "r1",
      timestamp = t1,
      answers = listOf(a1, a2),
    )
    val draft = question(
      id = "qDraft",
      text = "Draft?",
      roundId = "r1",
      timestamp = t2,
      draftText = "in progress",
      draftUpdatedAt = t3,
    )
    val ignored = question(
      id = "qIgnored",
      text = "Ignored?",
      roundId = "r1",
      timestamp = t3,
      ignoredAt = t2,
    )
    val compacted = Question(
      id = "qCompacted",
      text = "Compacted?",
      timestamp = Instant.fromEpochMilliseconds(4_000),
      roundId = "r1",
      compacted = true,
    )

    val snapshot = ExportSnapshot.from(
      project(questions = listOf(answered, draft, ignored, compacted), rounds = listOf(r1))
    )
    val records = snapshot.phases.single().questions.associateBy { it.id }

    val answeredSnap = records.getValue("qAnswered")
    assertEquals("a2", answeredSnap.answerId)
    assertNull(answeredSnap.draftText)
    assertEquals(
      listOf(
        ExportAnswerSnapshot("a1", "qAnswered", "Old", t1.toEpochMilliseconds()),
        ExportAnswerSnapshot("a2", "qAnswered", "Current", t2.toEpochMilliseconds()),
      ),
      answeredSnap.answers,
    )

    val draftSnap = records.getValue("qDraft")
    assertNull(draftSnap.answerId)
    assertEquals("Draft", draftSnap.answerState)
    assertNull(draftSnap.draftText)
    assertNull(draftSnap.draftUpdatedAtEpochMs)
    assertEquals(
      listOf(ExportAnswerSnapshot("qDraft-draft", "qDraft", "in progress", t3.toEpochMilliseconds())),
      draftSnap.answers,
    )

    val ignoredSnap = records.getValue("qIgnored")
    assertEquals(t2.toEpochMilliseconds(), ignoredSnap.ignoredAtEpochMs)

    val compactedSnap = records.getValue("qCompacted")
    assertTrue(compactedSnap.compacted)
    assertNull(compactedSnap.answerId)
    assertTrue(compactedSnap.answers.isEmpty())
  }

  @Test
  fun includesPhaseTraversedByRoundEvenWithoutQuestions() {
    val r1 = round(id = "r1", projectId = "p1", phase = BuiltInPhase.Design, roundNumber = 1)
    val snapshot = ExportSnapshot.from(project(rounds = listOf(r1)))

    assertEquals(listOf(BuiltInPhase.Design.key), snapshot.phases.map { it.phaseKey })
    assertTrue(snapshot.phases.single().questions.isEmpty())
  }

  @Test
  fun capturesCanonicalRoundsForInterchangeFidelity() {
    val r1 = round(
      id = "r1",
      projectId = "p1",
      phase = BuiltInPhase.ScopeGoals,
      roundNumber = 1,
      origin = alphainterplanetary.thinker.model.RoundOrigin.UserRequested,
      startedAt = t1,
      completedAt = t2,
      outcome = alphainterplanetary.thinker.model.RoundOutcome.Exhausted,
    )
    val snapshot = ExportSnapshot.from(project(rounds = listOf(r1)))

    val rs = snapshot.rounds.single()
    assertEquals("r1", rs.id)
    assertEquals("p1", rs.projectId)
    assertEquals(BuiltInPhase.ScopeGoals.key, rs.phaseKey)
    assertEquals(1, rs.roundNumber)
    assertEquals(alphainterplanetary.thinker.model.RoundOrigin.UserRequested.name, rs.origin)
    assertEquals(t1.toEpochMilliseconds(), rs.startedAtEpochMs)
    assertEquals(t2.toEpochMilliseconds(), rs.completedAtEpochMs)
    assertEquals(alphainterplanetary.thinker.model.RoundOutcome.Exhausted.name, rs.outcome)
    assertNull(rs.outcomeDetail)
  }
}
