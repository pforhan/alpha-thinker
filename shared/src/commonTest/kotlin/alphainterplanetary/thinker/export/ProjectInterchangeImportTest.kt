package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.RoundOrigin
import alphainterplanetary.thinker.model.RoundOutcome
import alphainterplanetary.thinker.phases.BuiltInPhase
import alphainterplanetary.thinker.phases.NoPhase
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import alphainterplanetary.thinker.testutil.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProjectInterchangeImportTest {
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

  private fun sampleProject(id: String = "p1"): Project {
    val r1 = round(
      id = "r1",
      projectId = id,
      phase = BuiltInPhase.ScopeGoals,
      roundNumber = 1,
      origin = RoundOrigin.Initial,
      startedAt = t1,
      completedAt = t2,
      outcome = RoundOutcome.MoreAvailable,
    )
    val a1 = answer("q1", "Old", id = "a1", createdAt = t1)
    val a2 = answer("q1", "Current", id = "a2", createdAt = t2)
    val q1 = question(id = "q1", text = "Question?", roundId = "r1", timestamp = t1, answers = listOf(a2, a1))
    val qDraft = question(
      id = "qDraft",
      text = "Draft?",
      roundId = "r1",
      timestamp = t2,
      draftText = "wip",
      draftUpdatedAt = t3,
    )
    return project(id = id, questions = listOf(q1, qDraft), rounds = listOf(r1), updatedAt = t3)
  }

  private fun compressedEnvelopeOf(vararg projects: Project): ByteArray {
    val snapshots = projects.map { ExportSnapshot.from(it) }
    val json = ProjectInterchange.encode(ProjectInterchange.envelopeOf(snapshots.toList(), exportedAtEpochMs = 99L))
    return gzipCompress(json.encodeToByteArray())
  }

  @Test
  fun analyzeBuildsPlanForValidSingleProjectWithConflictInfo() {
    val existing = sampleProject(id = "p1").copy(title = "Existing")
    val incoming = sampleProject(id = "p1").copy(title = "Incoming")

    val plan = ProjectInterchangeImport.analyze(compressedEnvelopeOf(incoming), existingProjects = listOf(existing))

    assertTrue(plan.isReadable)
    assertFalse(plan.hasErrors)
    assertEquals(1, plan.projects.size)
    val candidate = plan.projects.single()
    assertTrue(candidate.isImportable)
    assertEquals("p1", candidate.existingProjectId)
    assertEquals("Existing", candidate.existingProjectTitle)
    assertEquals(ImportProjectAction.ImportAsNew, candidate.defaultAction)
    assertEquals(ImportProjectAction.ImportAsNew, candidate.action)
    assertEquals(1, candidate.stats.rounds)
    assertEquals(2, candidate.stats.questions)
    assertEquals(3, candidate.stats.answers)
  }

  @Test
  fun analyzeReportsErrorsForMalformedOrUnsupportedPayloads() {
    // Not gzip and not valid interchange JSON.
    val notInterchange = ProjectInterchangeImport.analyze("not json".encodeToByteArray())
    assertFalse(notInterchange.isReadable)
    assertTrue(notInterchange.hasErrors)

    val badGzip = ProjectInterchangeImport.analyze(byteArrayOf(0x1f, 0x8b.toByte(), 0x00, 0x01))
    assertFalse(badGzip.isReadable)
    assertTrue(badGzip.hasErrors)
    assertTrue(badGzip.envelopeIssues.any { "decompressed" in it.message })

    val badJson = ProjectInterchangeImport.analyze(gzipCompress("{not json".encodeToByteArray()))
    assertFalse(badJson.isReadable)
    assertTrue(badJson.hasErrors)

    val futureEnvelope = ProjectInterchangeEnvelope(
      schemaVersion = PROJECT_INTERCHANGE_SCHEMA_VERSION + 1,
      exportedAtEpochMs = 1,
      projects = listOf(ProjectInterchangeProject(ExportSnapshot.from(sampleProject()))),
    )
    val future = ProjectInterchangeImport.analyze(
      gzipCompress(ProjectInterchange.encode(futureEnvelope).encodeToByteArray()),
    )
    assertTrue(future.hasErrors)
    assertTrue(future.envelopeIssues.any { it.severity == ImportIssueSeverity.Error })
  }

  @Test
  fun analyzeAcceptsUncompressedInterchangeJson() {
    val snapshot = ExportSnapshot.from(sampleProject())
    val json = ProjectInterchange.encode(ProjectInterchange.envelopeOf(snapshot, exportedAtEpochMs = 7L))

    val plan = ProjectInterchangeImport.analyze(json.encodeToByteArray())

    assertTrue(plan.isReadable)
    assertFalse(plan.hasErrors)
    assertEquals("p1", plan.projects.single().snapshot.projectId)
  }

  @Test
  fun analyzeFlagsUnknownPhaseKeysForUnassignedImport() {
    val r1 = round(id = "r1", projectId = "p1", phase = BuiltInPhase.ScopeGoals, roundNumber = 1)
    val snapshot = ExportSnapshot.from(project(id = "p1", rounds = listOf(r1))).copy(
      phases = listOf(
        ExportPhaseSnapshot(
          phaseKey = "custom-phase",
          phaseLabel = "Custom Phase",
          phaseOrder = 99,
          questions = listOf(
            ExportQuestionSnapshot(
              id = "q1",
              text = "Q",
              timestampEpochMs = t1.toEpochMilliseconds(),
              roundId = "r1",
            ),
          ),
        ),
      ),
      rounds = listOf(
        ExportRoundSnapshot(
          id = "r1",
          projectId = "p1",
          phaseKey = "custom-phase",
          roundNumber = 1,
          origin = RoundOrigin.Initial.name,
          startedAtEpochMs = t1.toEpochMilliseconds(),
          outcome = RoundOutcome.Pending.name,
        ),
      ),
    )
    val bytes = gzipCompress(ProjectInterchange.encode(ProjectInterchange.envelopeOf(snapshot)).encodeToByteArray())

    val plan = ProjectInterchangeImport.analyze(bytes)

    val candidate = plan.projects.single()
    assertTrue(candidate.isImportable)
    assertTrue(candidate.unknownPhaseKeys.contains("custom-phase"))
    assertTrue(candidate.issues.any { it.severity == ImportIssueSeverity.Warning && "unassigned" in it.message })
  }

  @Test
  fun toProjectRemapsIdsForImportAsNewAndPreservesStructure() {
    val snapshot = ExportSnapshot.from(sampleProject(id = "p1"))

    val imported = ProjectInterchangeImport.toProject(snapshot, newProjectId = "new-p")

    assertEquals("new-p", imported.id)
    assertEquals("My Project", imported.title)
    assertEquals("Synopsis", imported.synopsis)
    assertEquals(1, imported.rounds.size)
    assertEquals("new-p", imported.rounds.single().projectId)
    assertNotEquals("r1", imported.rounds.single().id)
    assertEquals(2, imported.questions.size)
    val q1 = imported.questions.first { it.text == "Question?" }
    assertNotEquals("q1", q1.id)
    assertEquals(imported.rounds.single().id, q1.roundId)
    assertEquals(2, q1.answers.size)
    assertTrue(q1.answers.all { it.questionId == q1.id })
    assertNotNull(q1.currentAnswer)
    assertEquals("Current", q1.currentAnswer?.text)

    val draft = imported.questions.first { it.text == "Draft?" }
    assertTrue(draft.isDraft)
    assertEquals("wip", draft.draftText)
  }

  @Test
  fun toProjectPreservesIdsForOverwriteAndMapsUnknownPhasesToUnassigned() {
    val snapshot = ExportSnapshot(
      schemaVersion = EXPORT_SNAPSHOT_SCHEMA_VERSION,
      projectId = "p1",
      title = "T",
      synopsis = "S",
      status = "Draft",
      createdAtEpochMs = t0.toEpochMilliseconds(),
      revisionUpdatedAtEpochMs = t3.toEpochMilliseconds(),
      phases = listOf(
        ExportPhaseSnapshot(
          phaseKey = "mystery-phase",
          phaseLabel = "Mystery",
          phaseOrder = 7,
          questions = listOf(
            ExportQuestionSnapshot(
              id = "q1",
              text = "Q",
              timestampEpochMs = t1.toEpochMilliseconds(),
              roundId = "r1",
            ),
          ),
        ),
      ),
      rounds = listOf(
        ExportRoundSnapshot(
          id = "r1",
          projectId = "p1",
          phaseKey = "mystery-phase",
          roundNumber = 1,
          origin = RoundOrigin.Initial.name,
          startedAtEpochMs = t1.toEpochMilliseconds(),
          outcome = RoundOutcome.Pending.name,
        ),
      ),
    )

    val imported = ProjectInterchangeImport.toProject(snapshot, newProjectId = null)

    assertEquals("p1", imported.id)
    assertEquals("r1", imported.rounds.single().id)
    assertEquals(NoPhase, imported.rounds.single().phase)
    assertEquals("q1", imported.questions.single().id)
  }

  @Test
  fun importDerivesAnswerStateFromV2StyleSnapshotFields() {
    // v2-style payload: no answerState/draftAnswerId/isDraft, draft represented by draftText.
    val v2Json = """
      {
        "schemaVersion": 1,
        "exportedAtEpochMs": 99,
        "appId": "alpha-thinker",
        "projects": [
          {
            "snapshot": {
              "schemaVersion": 2,
              "projectId": "p1",
              "title": "Old",
              "synopsis": "S",
              "status": "Draft",
              "createdAtEpochMs": 0,
              "revisionUpdatedAtEpochMs": 3000,
              "rounds": [
                {
                  "id": "r1",
                  "projectId": "p1",
                  "phaseKey": "scope-goals",
                  "roundNumber": 1,
                  "origin": "Initial",
                  "startedAtEpochMs": 1000,
                  "outcome": "Pending"
                }
              ],
              "phases": [
                {
                  "phaseKey": "scope-goals",
                  "phaseLabel": "Scope & Goals",
                  "phaseOrder": 1,
                  "questions": [
                    {
                      "id": "qAnswered",
                      "text": "Answered?",
                      "timestampEpochMs": 1000,
                      "roundId": "r1",
                      "answerId": "a2",
                      "answers": [
                        { "id": "a1", "questionId": "qAnswered", "text": "Old", "createdAtEpochMs": 1000 },
                        { "id": "a2", "questionId": "qAnswered", "text": "Current", "createdAtEpochMs": 2000 }
                      ]
                    },
                    {
                      "id": "qDraft",
                      "text": "Draft?",
                      "timestampEpochMs": 2000,
                      "roundId": "r1",
                      "draftText": "wip",
                      "draftUpdatedAtEpochMs": 3000,
                      "answers": []
                    },
                    {
                      "id": "qOpen",
                      "text": "Open?",
                      "timestampEpochMs": 2000,
                      "roundId": "r1",
                      "answers": []
                    }
                  ]
                }
              ]
            }
          }
        ]
      }
    """.trimIndent()

    val plan = ProjectInterchangeImport.analyze(gzipCompress(v2Json.encodeToByteArray()))

    assertTrue(plan.isReadable)
    assertFalse(plan.hasErrors)
    val imported = ProjectInterchangeImport.toProject(plan.projects.single().snapshot, newProjectId = "new")

    val answered = imported.questions.first { it.id != "qDraft" && it.id != "qOpen" }
    assertTrue(answered.isAnswered)
    assertEquals("Current", answered.currentAnswer?.text)
    assertEquals(2, answered.answers.size)

    val draft = imported.questions.first { it.id == "qDraft" || it.text == "Draft?" }
    assertTrue(draft.isDraft)
    assertEquals("wip", draft.draftText)
    assertTrue(draft.answers.any { it.text == "wip" })

    val open = imported.questions.first { it.text == "Open?" }
    assertTrue(open.isUnanswered)
    assertNull(open.currentAnswer)
    assertNull(open.currentDraft)
  }
}
