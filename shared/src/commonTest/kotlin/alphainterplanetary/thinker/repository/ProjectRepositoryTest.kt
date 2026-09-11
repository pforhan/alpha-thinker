package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.testutil.FakeStorage
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.testutil.FakeGenerator
import alphainterplanetary.thinker.testutil.answer
import alphainterplanetary.thinker.testutil.question
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ProjectRepositoryTest {

  private val now: Instant = Clock.System.now()

  private suspend fun repo(
    storage: FakeStorage = FakeStorage(),
    generator: FakeGenerator = FakeGenerator(),
  ): ProjectRepository = ProjectRepository(storage, generator)

  // ---------- createProject ----------

  @Test
  fun `createProject with explicit title truncates to 30 chars`() = runTest {
    val generator = FakeGenerator()
    val repository = repo(generator = generator)
    val longTitle = "x".repeat(50)

    val project = repository.createProject("My synopsis", title = longTitle)

    assertEquals(longTitle.take(30), project.editableTitle)
    assertEquals("My synopsis", project.synopsis)
    assertEquals("Draft", project.status)
  }

  @Test
  fun `createProject without title uses recommended title`() = runTest {
    val generator = FakeGenerator().apply { recommendedTitle = "From Generator" }
    val repository = repo(generator = generator)

    val project = repository.createProject("My synopsis")

    assertEquals("From Generator", project.editableTitle)
  }

  @Test
  fun `createProject trims synopsis and title`() = runTest {
    val repository = repo(generator = FakeGenerator().apply { recommendedTitle = "Fallback" })

    val project = repository.createProject("  leading and trailing  ", title = "  My Title  ")

    assertEquals("leading and trailing", project.synopsis)
    assertEquals("My Title", project.editableTitle)
  }

  @Test
  fun `createProject passes editable title and synopsis to initial generation`() = runTest {
    val generator = FakeGenerator().apply {
      recommendedTitle = "Recommended Title"
      initialQuestions += question("q1", "First?")
      initialQuestions += question("q2", "Second?")
    }
    val repository = repo(generator = generator)

    repository.createProject("My synopsis")

    assertEquals(1, generator.initialCalls.size)
    val call = generator.initialCalls.single()
    assertEquals("Recommended Title", call.editableTitle)
    assertEquals("My synopsis", call.synopsis)
  }

  @Test
  fun `createProject saves generated questions onto the project`() = runTest {
    val generator = FakeGenerator().apply {
      initialQuestions += question("q1")
      initialQuestions += question("q2")
    }
    val storage = FakeStorage()
    val repository = repo(storage = storage, generator = generator)

    val project = repository.createProject("My synopsis")

    assertEquals(setOf("q1", "q2"), project.questions.map { it.id }.toSet())
    assertEquals(
      setOf("q1", "q2"),
      storage.getProject(project.id)?.questions?.map { it.id }?.toSet()
    )
  }

  // ---------- updateProject ----------

  @Test
  fun `updateProject KEEP preserves answers and ignore state`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "old synopsis",
      editableTitle = "old title",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "1"))),
        question("q2", ignoredAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateProject(
      id = "p1",
      title = "New Title",
      synopsis = "New Synopsis",
      mode = ProjectUpdateMode.KEEP,
    )

    assertNotNull(updated)
    assertEquals("New Title", updated.editableTitle)
    assertEquals("New Synopsis", updated.synopsis)
    assertEquals(1, updated.questions[0].answers.size)
    assertTrue(updated.questions[0].isAnswered)
    assertNotNull(updated.questions[1].ignoredAt)
  }

  @Test
  fun `updateProject CLEAR resets answers drafts and ignore state`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "synopsis",
      editableTitle = "title",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "7"))),
        question("q2", ignoredAt = now),
        question("q3", draftText = "draft", draftUpdatedAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateProject(
      id = "p1",
      title = "Title",
      synopsis = "synopsis",
      mode = ProjectUpdateMode.CLEAR,
    )

    assertNotNull(updated)
    assertNull(updated.questions[0].currentAnswer)
    assertFalse(updated.questions[0].isAnswered)
    assertNull(updated.questions[1].ignoredAt)
    assertFalse(updated.questions[2].isDraft)
    assertNull(updated.questions[2].draftText)
  }

  @Test
  fun `updateProject returns null for missing project`() = runTest {
    val repository = repo()

    val result = repository.updateProject(
      id = "missing",
      title = "T",
      synopsis = "S",
      mode = ProjectUpdateMode.KEEP,
    )

    assertNull(result)
  }

  // ---------- getUnansweredQuestions ----------

  @Test
  fun `getUnansweredQuestions excludes answered and ignored`() = runTest {
    val project = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("answered", answers = listOf(answer("answered", "A", id = "1"))),
        question("ignored", ignoredAt = now),
        question("open"),
        question("draft", draftText = "d", draftUpdatedAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo()

    val unanswered = project.unansweredQuestions

    assertEquals(listOf("open", "draft"), unanswered.map { it.id })
  }

  // ---------- saveAnswer ----------

  @Test
  fun `saveAnswer commits a completed answer`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "My answer",
      completed = true,
    )

    assertNotNull(updated)
    val current = updated.questions.single().currentAnswer
    assertNotNull(current)
    assertEquals("My answer", current.text)
    assertTrue(updated.questions.single().isAnswered)
  }

  @Test
  fun `saveAnswer stores a draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Draft text",
      completed = false,
    )

    assertNotNull(updated)
    assertEquals("Draft text", updated.questions.single().draftText)
    assertTrue(updated.questions.single().isDraft)
    assertFalse(updated.questions.single().isAnswered)
  }

  @Test
  fun `saveAnswer blank text clears the draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", draftText = "in progress", draftUpdatedAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "",
      completed = false,
    )

    assertNotNull(updated)
    assertNull(updated.questions.single().draftText)
    assertNull(updated.questions.single().draftUpdatedAt)
    assertFalse(updated.questions.single().isDraft)
  }

  @Test
  fun `saveAnswer editing a committed answer demotes it to a draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "1"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Edited",
      completed = false,
    )

    assertNotNull(updated)
    val q = updated.questions.single()
    assertNull(q.currentAnswer)
    assertFalse(q.isAnswered)
    assertTrue(q.isDraft)
    assertEquals("Edited", q.draftText)
    assertEquals(1, q.answers.size, "the committed version stays in immutable history")
  }

  @Test
  fun `saveAnswer committing unchanged text is a no-op`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = "1"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val unchanged = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    assertNotNull(unchanged)
    val q = unchanged.questions.single()
    assertEquals(1, q.answers.size, "no new version for identical committed text")
    assertTrue(q.isAnswered)
    assertEquals("Answer", q.currentAnswer?.text)
  }

  @Test
  fun `saveAnswer commits a new version when text changes`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "First", id = "1"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Second",
      completed = true,
    )

    assertNotNull(updated)
    val q = updated.questions.single()
    assertEquals(2, q.answers.size, "an edit appends an immutable version")
    assertEquals("Second", q.currentAnswer?.text)
    assertEquals(listOf("First", "Second"), q.answers.map { it.text })
  }

  @Test
  fun `saveAnswer generating a draft for a question with no text leaves it unanswered`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "   ",
      completed = false,
    )

    assertNotNull(updated)
    val q = updated.questions.single()
    assertFalse(q.isDraft)
    assertFalse(q.isAnswered)
  }

  @Test
  fun `saveAnswer generates follow-ups when all active questions are answered`() = runTest {
    val generator = FakeGenerator().apply {
      followUpQuestions += question("f1")
      followUpQuestions += question("f2")
    }
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    assertNotNull(updated)
    assertEquals(1, generator.followUpCalls.size)
    assertEquals(listOf("q1", "f1", "f2"), updated.questions.map { it.id })
    assertEquals("s", generator.followUpCalls.single().synopsis)
    assertEquals(listOf("q1"), generator.followUpCalls.single().previousQuestions.map { it.id })
  }

  @Test
  fun `saveAnswer does not generate follow-ups when not all answered`() = runTest {
    val generator = FakeGenerator()
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1"), question("q2")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    assertNotNull(updated)
    assertTrue(generator.followUpCalls.isEmpty())
    assertEquals(listOf("q1", "q2"), updated.questions.map { it.id })
  }

  @Test
  fun `saveAnswer does not generate follow-ups when only ignored questions remain`() = runTest {
    val generator = FakeGenerator()
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", ignoredAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.saveAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      completed = true,
    )

    // The question being answered is ignored; with no active questions left the
    // all-answered check should not trigger follow-ups.
    assertTrue(generator.followUpCalls.isEmpty())
    assertNotNull(updated)
  }

  @Test
  fun `saveAnswer returns null when question not found`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val result = repository.saveAnswer("p1", "missing", "text", completed = true)

    assertNull(result)
  }

  // ---------- ignore / unignore ----------

  @Test
  fun `ignoreQuestion sets ignoredAt`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.ignoreQuestion("p1", "q1")

    assertNotNull(updated)
    assertTrue(updated.questions.single().isIgnored)
  }

  @Test
  fun `unignoreQuestion clears ignoredAt`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", ignoredAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.unignoreQuestion("p1", "q1")

    assertNotNull(updated)
    assertNull(updated.questions.single().ignoredAt)
  }

  // ---------- deleting an answer (a blank draft) ----------

  @Test
  fun `saving a blank draft removes the current answer but keeps history`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "A", id = "7"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer("p1", "q1", "", completed = false)

    assertNotNull(updated)
    val q = updated.questions.single()
    assertNull(q.currentAnswer)
    assertFalse(q.isAnswered)
    assertTrue(q.isUnanswered)
    assertEquals(1, q.answers.size, "the version row stays in immutable history")
  }

  @Test
  fun `saving a blank draft clears a pending draft`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", draftText = "in progress", draftUpdatedAt = now)),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.saveAnswer("p1", "q1", "", completed = false)

    assertNotNull(updated)
    val q = updated.questions.single()
    assertNull(q.draftText)
    assertNull(q.draftUpdatedAt)
    assertFalse(q.isDraft)
  }

  // ---------- restoreProject (undo) ----------

  @Test
  fun `restoreProject re-persists a pre-mutation snapshot`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    repository.ignoreQuestion("p1", "q1")
    assertTrue(storage.getProject("p1")!!.questions.single().isIgnored)

    repository.restoreProject(original)

    val restored = storage.getProject("p1")
    assertNotNull(restored)
    assertNull(restored.questions.single().ignoredAt)
    assertEquals(listOf("q1"), restored.questions.map { it.id })
  }

  @Test
  fun `restoreProject re-links a deleted answer`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "A", id = "7"))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = FakeStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    repository.saveAnswer("p1", "q1", "", completed = false)
    assertNull(storage.getProject("p1")!!.questions.single().currentAnswer)

    repository.restoreProject(original)

    val restored = storage.getProject("p1")
    assertNotNull(restored)
    assertEquals("A", restored.questions.single().currentAnswer?.text)
  }

  @Test
  fun `restoreProject inserts a project that was missing`() = runTest {
    val project = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo()

    repository.restoreProject(project)

    assertEquals(project, repository.getProject(project.id))
  }

  // ---------- exportProject ----------

  @Test
  fun `exportProject renders answered and unanswered questions`() = runTest {
    val project = Project(
      id = "p1",
      synopsis = "Build a thing",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question("q1", "What is it?", answers = listOf(answer("q1", "A thing"))),
        question("q2", "When done?"),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo()

    val markdown = repository.exportProject(project)

    assertTrue(markdown.contains("# Build a thing"))
    assertTrue(markdown.contains("### Q: What is it?"))
    assertTrue(markdown.contains("| **Answer:** | A thing |"))
    assertTrue(markdown.contains("|**Status:** | unanswered |"))
  }
}