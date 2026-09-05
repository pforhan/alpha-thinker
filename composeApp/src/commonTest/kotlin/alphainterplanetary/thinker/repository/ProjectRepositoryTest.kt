package alphainterplanetary.thinker.repository

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.llm.QuestionGenerator
import alphainterplanetary.thinker.model.Answer
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.model.Question
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock.System
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class ProjectRepositoryTest {

  private val now: Instant = System.now()

  private fun question(
    id: String,
    text: String = id,
    ignoredAt: Instant? = null,
    answers: List<Answer> = emptyList(),
  ): Question = Question(
    id = id,
    text = text,
    timestamp = now,
    contextId = "ctx",
    ignoredAt = ignoredAt,
    answers = answers,
  )

  private fun answer(
    questionId: String,
    text: String,
    answeredAt: Instant? = now,
    id: Long = 0,
    deletedAt: Instant? = null,
  ): Answer = Answer(
    id = id,
    questionId = questionId,
    text = text,
    answeredAt = answeredAt,
    deletedAt = deletedAt,
  )

  private suspend fun repo(
    storage: InMemoryStorage = InMemoryStorage(),
    generator: FakeGenerator = FakeGenerator(),
  ): ProjectRepository = ProjectRepository(storage, generator)

  private class InMemoryStorage(
    private val projects: MutableMap<String, Project> = mutableMapOf(),
  ) : Storage {
    override suspend fun saveProject(project: Project): Project {
      projects[project.id] = project
      return project
    }

    override suspend fun getProject(id: String): Project? = projects[id]

    override suspend fun getAllProjects(): List<Project> = projects.values.toList()

    override suspend fun deleteProject(id: String) {
      projects.remove(id)
    }

    override suspend fun deleteAllProjects() {
      projects.clear()
    }

    override suspend fun saveQuestionOrder(projectId: String, order: List<String>) {
      val current = projects[projectId] ?: return
      val byId = current.questions.associateBy { it.id }
      projects[projectId] = current.copy(questions = order.mapNotNull { byId[it] })
    }
  }

  private class FakeGenerator : QuestionGenerator {
    var recommendedTitle: String = "Recommended"
    val initialQuestions: MutableList<Question> = mutableListOf()
    val followUpQuestions: MutableList<Question> = mutableListOf()
    var initialCalls: MutableList<InitialCall> = mutableListOf()
    var followUpCalls: MutableList<FollowUpCall> = mutableListOf()

    override suspend fun recommendTitle(synopsis: String): String = recommendedTitle

    override suspend fun generateInitialQuestions(
      editableTitle: String,
      synopsis: String,
      contextId: String,
    ): List<Question> {
      initialCalls += InitialCall(editableTitle, synopsis, contextId)
      return initialQuestions
    }

    override suspend fun generateFollowUpQuestions(
      synopsis: String,
      previousQuestions: List<Question>,
      contextId: String,
    ): List<Question> {
      followUpCalls += FollowUpCall(synopsis, previousQuestions, contextId)
      return followUpQuestions
    }

    data class InitialCall(
      val editableTitle: String,
      val synopsis: String,
      val contextId: String,
    )

    data class FollowUpCall(
      val synopsis: String,
      val previousQuestions: List<Question>,
      val contextId: String,
    )
  }

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
    val storage = InMemoryStorage()
    val repository = repo(storage = storage, generator = generator)

    val project = repository.createProject("My synopsis")

    assertEquals(setOf("q1", "q2"), project.questions.map { it.id }.toSet())
    assertEquals(setOf("q1", "q2"), storage.getProject(project.id)?.questions?.map { it.id }?.toSet())
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
        question("q1", answers = listOf(answer("q1", "Answer"))),
        question("q2", ignoredAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
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
    assertNotNull(updated.questions[1].ignoredAt)
  }

  @Test
  fun `updateProject CLEAR deletes answers and unignores`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "synopsis",
      editableTitle = "title",
      status = "Draft",
      questions = listOf(
        question("q1", answers = listOf(answer("q1", "Answer", id = 7L))),
        question("q2", ignoredAt = now),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateProject(
      id = "p1",
      title = "Title",
      synopsis = "synopsis",
      mode = ProjectUpdateMode.CLEAR,
    )

    assertNotNull(updated)
    assertEquals(null, updated.questions[0].currentAnswer)
    assertNull(updated.questions[1].ignoredAt)
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
        question("answered", answers = listOf(answer("answered", "A"))),
        question("ignored", ignoredAt = now),
        question("open"),
        question("draft", answers = listOf(answer("draft", "d", answeredAt = null))),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val repository = repo()

    val unanswered = repository.getUnansweredQuestions(project)

    assertEquals(listOf("open", "draft"), unanswered.map { it.id })
  }

  // ---------- updateAnswer ----------

  @Test
  fun `updateAnswer adds a completed answer`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "My answer",
      isDraft = false,
    )

    assertNotNull(updated)
    val current = updated.questions.single().currentAnswer
    assertNotNull(current)
    assertEquals("My answer", current.text)
    assertTrue(current.isComplete)
  }

  @Test
  fun `updateAnswer adds a draft answer`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.updateAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Draft text",
      isDraft = true,
    )

    assertNotNull(updated)
    val current = updated.questions.single().currentAnswer
    assertNotNull(current)
    assertTrue(current.isDraft)
    assertFalse(current.isComplete)
  }

  @Test
  fun `updateAnswer throws when adding a draft to a completed question`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", answers = listOf(answer("q1", "Answer")))),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    try {
      repository.updateAnswer(projectId = "p1", questionId = "q1", text = "draft", isDraft = true)
      fail("Expected IllegalStateException")
    } catch (e: IllegalStateException) {
      assertEquals(
        "Cannot add a draft answer to a question that is already answered",
        e.message,
      )
    }
  }

  @Test
  fun `updateAnswer generates follow-ups when all active questions are answered`() = runTest {
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
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.updateAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      isDraft = false,
    )

    assertNotNull(updated)
    assertEquals(1, generator.followUpCalls.size)
    assertEquals(listOf("q1", "f1", "f2"), updated.questions.map { it.id })
    assertEquals("s", generator.followUpCalls.single().synopsis)
    assertEquals(listOf("q1"), generator.followUpCalls.single().previousQuestions.map { it.id })
  }

  @Test
  fun `updateAnswer does not generate follow-ups when not all answered`() = runTest {
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
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.updateAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      isDraft = false,
    )

    assertNotNull(updated)
    assertTrue(generator.followUpCalls.isEmpty())
    assertEquals(listOf("q1", "q2"), updated.questions.map { it.id })
  }

  @Test
  fun `updateAnswer does not generate follow-ups when only ignored questions remain`() = runTest {
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
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage, generator = generator)

    val updated = repository.updateAnswer(
      projectId = "p1",
      questionId = "q1",
      text = "Answer",
      isDraft = false,
    )

    // The final remaining question is ignored; answering it should not trigger follow-ups.
    assertTrue(generator.followUpCalls.isEmpty())
    assertNotNull(updated)
  }

  @Test
  fun `updateAnswer returns null when question not found`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1")),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val result = repository.updateAnswer("p1", "missing", "text")

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
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
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
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.unignoreQuestion("p1", "q1")

    assertNotNull(updated)
    assertNull(updated.questions.single().ignoredAt)
  }

  // ---------- deleteAnswer ----------

  @Test
  fun `deleteAnswer marks the answer as deleted`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(question("q1", answers = listOf(answer("q1", "A", id = 7L)))),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.deleteAnswer("p1", "q1", 7L)

    assertNotNull(updated)
    assertNull(updated.questions.single().currentAnswer)
  }

  @Test
  fun `deleteAnswer leaves other answers intact`() = runTest {
    val original = Project(
      id = "p1",
      synopsis = "s",
      editableTitle = "t",
      status = "Draft",
      questions = listOf(
        question(
          "q1",
          answers = listOf(answer("q1", "First", id = 1L), answer("q1", "Second", id = 2L)),
        ),
      ),
      createdAt = now,
      updatedAt = now,
    )
    val storage = InMemoryStorage(mutableMapOf("p1" to original))
    val repository = repo(storage = storage)

    val updated = repository.deleteAnswer("p1", "q1", 1L)

    assertNotNull(updated)
    assertEquals("Second", updated.questions.single().currentAnswer?.text)
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
