package alphainterplanetary.thinker.ui.viewmodel

import alphainterplanetary.thinker.ProjectUpdateMode
import alphainterplanetary.thinker.data.ThinkerRepository
import alphainterplanetary.thinker.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectListViewModel(private val repository: ThinkerRepository) {
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadProjects() {
        _isLoading.value = true
        repository.getAllProjects { result ->
            result.onSuccess { projects ->
                _projects.value = projects
            }.onFailure {
                _projects.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    private val _createdProject = MutableStateFlow<Project?>(null)
    val createdProject: StateFlow<Project?> = _createdProject.asStateFlow()

    fun createProject(synopsis: String, title: String?) {
        repository.createProject(synopsis, title) { result ->
            result.onSuccess { project ->
                _createdProject.value = project
                loadProjects()
            }
        }
    }

    fun consumeCreatedProject() {
        _createdProject.value = null
    }

    fun deleteProject(id: String) {
        repository.deleteProject(id) { result ->
            result.onSuccess {
                loadProjects()
            }
        }
    }
}

class ProjectDetailViewModel(private val repository: ThinkerRepository) {
    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadProject(id: String) {
        _isLoading.value = true
        repository.getProject(id) { result ->
            result.onSuccess { project ->
                _project.value = project
            }.onFailure {
                _project.value = null
            }
            _isLoading.value = false
        }
    }

    fun updateAnswer(projectId: String, questionId: String, text: String, isDraft: Boolean) {
        repository.updateAnswer(projectId, questionId, text, isDraft) { result ->
            result.onSuccess {
                loadProject(projectId)
            }
        }
    }

    fun ignoreQuestion(projectId: String, questionId: String) {
        repository.ignoreQuestion(projectId, questionId) { result ->
            result.onSuccess {
                loadProject(projectId)
            }
        }
    }

    fun unignoreQuestion(projectId: String, questionId: String) {
        repository.unignoreQuestion(projectId, questionId) { result ->
            result.onSuccess {
                loadProject(projectId)
            }
        }
    }

    fun deleteAnswer(projectId: String, questionId: String, answerId: Long) {
        repository.deleteAnswer(projectId, questionId, answerId) { result ->
            result.onSuccess {
                loadProject(projectId)
            }
        }
    }

    fun updateProject(id: String, title: String, synopsis: String, mode: ProjectUpdateMode) {
        repository.updateProject(id, title, synopsis, mode) { result ->
            result.onSuccess { project ->
                _project.value = project
            }
        }
    }
}
