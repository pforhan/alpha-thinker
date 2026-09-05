package alphainterplanetary.thinker.tools

import alphainterplanetary.thinker.database.Storage
import alphainterplanetary.thinker.model.Project
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleProjectGeneratorTest {

  @Test
  fun `generate creates three projects with the expected ids`() = runTest {
    val storage = InMemoryStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    assertEquals(setOf("sample-sparse", "sample-complete", "sample-stress"), storage.projects.keys)
  }

  @Test
  fun `sparse project has a short synopsis with a couple answered and five unanswered questions`() = runTest {
    val storage = InMemoryStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val sparse = storage.projects.getValue("sample-sparse")
    assertTrue(sparse.synopsis.length < 200)
    assertEquals(7, sparse.questions.size)
    assertEquals(2, sparse.questions.count { it.isAnswered })
    assertEquals(5, sparse.questions.count { it.isUnanswered })
  }

  @Test
  fun `complete project mixes answered, ignored, draft and unanswered questions`() = runTest {
    val storage = InMemoryStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val complete = storage.projects.getValue("sample-complete")
    assertTrue(complete.questions.any { it.isAnswered })
    assertTrue(complete.questions.any { it.isIgnored })
    assertTrue(complete.questions.any { it.isUnanswered })
    assertTrue(
      complete.questions.any { q ->
        q.currentAnswer?.isDraft == true && !q.isAnswered
      },
      "complete project should contain a draft answer on an unanswered question",
    )
  }

  @Test
  fun `stress project uses very long text in every field`() = runTest {
    val storage = InMemoryStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()

    val stress = storage.projects.getValue("sample-stress")
    assertTrue(stress.editableTitle.length > 100, "stress title should be very long")
    assertTrue(stress.synopsis.length > 1000, "stress synopsis should be very long")
    assertTrue(stress.questions.all { it.text.length > 100 }, "stress question texts should be long")
    assertTrue(
      stress.questions.any { q -> q.currentAnswer?.isComplete == true && q.currentAnswer!!.text.length > 500 },
      "stress project should contain very long complete answers",
    )
  }

  @Test
  fun `generating twice does not grow the project count`() = runTest {
    val storage = InMemoryStorage()
    val generator = SampleProjectGenerator(storage)

    generator.generate()
    generator.generate()

    assertEquals(3, storage.projects.size)
  }

  private class InMemoryStorage : Storage {
    val projects = mutableMapOf<String, Project>()

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
}