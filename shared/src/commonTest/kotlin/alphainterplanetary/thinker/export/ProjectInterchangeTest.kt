package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.model.RoundOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectInterchangeTest {
  private val t0 = Instant.fromEpochMilliseconds(0)
  private val t1 = Instant.fromEpochMilliseconds(1_000)
  private val t2 = Instant.fromEpochMilliseconds(2_000)
  private val t3 = Instant.fromEpochMilliseconds(3_000)

  private fun project(
    id: String = "p1",
    title: String = "My Project",
    synopsis: String = "Synopsis",
    status: String = "Draft",
    createdAt: Instant = t0,
    updatedAt: Instant = t3,
    questions: List<alphainterplanetary.thinker.model.Question> = emptyList(),
    rounds: List<alphainterplanetary.thinker.model.Round> = emptyList(),
  ) = Project(
    id = id,
    synopsis = synopsis,
    title = title,
    status = status,
    questions = questions,
    rounds = rounds,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

  @Test
  fun singleProjectUsesStandardMultiProjectEnvelope() {
    val snapshot = ExportSnapshot.from(project(id = "p1"))

    val envelope = ProjectInterchange.envelopeOf(snapshot, exportedAtEpochMs = 42L, appVersion = "1.2.3")

    assertEquals(PROJECT_INTERCHANGE_SCHEMA_VERSION, envelope.schemaVersion)
    assertEquals(42L, envelope.exportedAtEpochMs)
    assertEquals("alpha-thinker", envelope.appId)
    assertEquals("1.2.3", envelope.appVersion)
    assertEquals(1, envelope.projects.size)
    assertEquals("p1", envelope.projects.single().snapshot.projectId)
  }

  @Test
  fun globalExportWrapsMultipleProjectsInOneEnvelope() {
    val a = ExportSnapshot.from(project(id = "p1", title = "One"))
    val b = ExportSnapshot.from(project(id = "p2", title = "Two"))

    val envelope = ProjectInterchange.envelopeOf(listOf(a, b), exportedAtEpochMs = 99L)

    assertEquals(PROJECT_INTERCHANGE_SCHEMA_VERSION, envelope.schemaVersion)
    assertEquals(listOf("p1", "p2"), envelope.projects.map { it.snapshot.projectId })
    assertEquals(listOf("One", "Two"), envelope.projects.map { it.snapshot.title })
  }

  @Test
  fun envelopeRequiresAtLeastOneProject() {
    assertFailsWith<IllegalArgumentException> {
      ProjectInterchangeEnvelope(exportedAtEpochMs = 0L, projects = emptyList())
    }
  }

  @Test
  fun jsonRoundTripPreservesSnapshotContentAndRounds() {
    val r1 = round(
      id = "r1",
      projectId = "p1",
      phase = BuiltInPhase.ScopeGoals,
      roundNumber = 1,
      origin = RoundOrigin.Initial,
      startedAt = t1,
      completedAt = t2,
      outcome = RoundOutcome.MoreAvailable,
    )
    val a1 = answer("q1", "Old", id = "a1", createdAt = t1)
    val a2 = answer("q1", "Current", id = "a2", createdAt = t2)
    val q1 = question(
      id = "q1",
      text = "Question?",
      roundId = "r1",
      timestamp = t1,
      answers = listOf(a2, a1),
      draftText = null,
    )
    val snapshot = ExportSnapshot.from(
      project(
        id = "p1",
        title = "Title",
        synopsis = "Syn",
        status = "Draft",
        createdAt = t0,
        updatedAt = t3,
        questions = listOf(q1),
        rounds = listOf(r1),
      )
    )

    val encoded = ProjectInterchange.encode(ProjectInterchange.envelopeOf(snapshot, exportedAtEpochMs = 7L))
    val decoded = ProjectInterchange.decode(encoded)

    assertEquals(PROJECT_INTERCHANGE_SCHEMA_VERSION, decoded.schemaVersion)
    assertEquals(7L, decoded.exportedAtEpochMs)
    val project = decoded.projects.single().snapshot
    assertEquals(EXPORT_SNAPSHOT_SCHEMA_VERSION, project.schemaVersion)
    assertEquals("p1", project.projectId)
    assertEquals("Title", project.title)
    assertEquals("Syn", project.synopsis)
    assertEquals("Draft", project.status)
    assertEquals(t0.toEpochMilliseconds(), project.createdAtEpochMs)
    assertEquals(t3.toEpochMilliseconds(), project.revisionUpdatedAtEpochMs)

    val round = project.rounds.single()
    assertEquals("r1", round.id)
    assertEquals(BuiltInPhase.ScopeGoals.key, round.phaseKey)
    assertEquals(1, round.roundNumber)
    assertEquals(RoundOrigin.Initial.name, round.origin)
    assertEquals(RoundOutcome.MoreAvailable.name, round.outcome)
    assertEquals(t1.toEpochMilliseconds(), round.startedAtEpochMs)
    assertEquals(t2.toEpochMilliseconds(), round.completedAtEpochMs)
    assertNull(round.outcomeDetail)

    val question = project.phases.single().questions.single()
    assertEquals("q1", question.id)
    assertEquals("a2", question.answerId)
    assertEquals(listOf("a2", "a1"), question.answers.map { it.id })
  }

  @Test
  fun decodeIgnoresUnknownFieldsForForwardCompatibility() {
    val text = """
      {
        "schemaVersion": 1,
        "exportedAtEpochMs": 5,
        "appId": "alpha-thinker",
        "appVersion": null,
        "futureEnvelopeField": {"x": 1},
        "projects": [
          {
            "snapshot": {
              "schemaVersion": 2,
              "projectId": "p1",
              "title": "T",
              "synopsis": "S",
              "status": "Draft",
              "createdAtEpochMs": 0,
              "revisionUpdatedAtEpochMs": 1,
              "phases": [],
              "rounds": [],
              "futureSnapshotField": true
            },
            "futureProjectField": "ignored"
          }
        ]
      }
    """.trimIndent()

    val decoded = ProjectInterchange.decode(text)

    assertEquals(1, decoded.schemaVersion)
    assertEquals(5L, decoded.exportedAtEpochMs)
    assertEquals("p1", decoded.projects.single().snapshot.projectId)
    assertTrue(decoded.projects.single().snapshot.phases.isEmpty())
  }

  @Test
  fun fileExtensionIsThinker() {
    assertEquals("thinker", PROJECT_INTERCHANGE_EXTENSION)
  }
}
