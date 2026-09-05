package alphainterplanetary.thinker.data

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.model.Project
import alphainterplanetary.thinker.repository.ProjectRepository
import alphainterplanetary.thinker.tools.SampleProjectGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThinkerRepository(
  private val repository: ProjectRepository,
  private val sampleProjectGenerator: SampleProjectGenerator,
) {
  fun createProject(synopsis: String, title: String?, onResult: (Result<Project>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        val project = repository.createProject(synopsis, title)
        onResult(Result.success(project))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun getAllProjects(onResult: (Result<List<Project>>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        val projects = repository.getAllProjects()
        onResult(Result.success(projects))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun getProject(id: String, onResult: (Result<Project?>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        val project = repository.getProject(id)
        onResult(Result.success(project))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun deleteProject(id: String, onResult: (Result<Unit>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        repository.deleteProject(id)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun updateAnswer(
    projectId: String,
    questionId: String,
    text: String,
    isDraft: Boolean,
    onResult: (Result<Unit>) -> Unit,
  ) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        repository.updateAnswer(projectId, questionId, text, isDraft)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun ignoreQuestion(projectId: String, questionId: String, onResult: (Result<Unit>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        repository.ignoreQuestion(projectId, questionId)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun unignoreQuestion(projectId: String, questionId: String, onResult: (Result<Unit>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        repository.unignoreQuestion(projectId, questionId)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun deleteAnswer(
    projectId: String,
    questionId: String,
    answerId: Long,
    onResult: (Result<Unit>) -> Unit,
  ) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        repository.deleteAnswer(projectId, questionId, answerId)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun updateProject(
    id: String,
    title: String,
    synopsis: String,
    mode: ProjectUpdateMode,
    onResult: (Result<Project?>) -> Unit,
  ) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        val project = repository.updateProject(id, title, synopsis, mode)
        onResult(Result.success(project))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun saveQuestionOrder(projectId: String, order: List<String>, onResult: (Result<Unit>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        repository.saveQuestionOrder(projectId, order)
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }

  fun generateSampleProjects(onResult: (Result<Unit>) -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
      try {
        sampleProjectGenerator.generate()
        onResult(Result.success(Unit))
      } catch (e: Exception) {
        onResult(Result.failure(e))
      }
    }
  }
}
