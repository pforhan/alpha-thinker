package com.pforhan.alphathinker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pforhan.alphathinker.repository.ProjectRepository
import kotlinx.coroutines.launch
import java.time.Instant

class MainViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    var uiState by mutableStateOf<UiState>(UiState.Loading)
        private set

    sealed interface UiState {
        object Loading : UiState
        data class ProjectList(
            val projects: List<ProjectItem>
        ) : UiState

        data class ProjectDetail(
            val project: Project
        ) : UiState

        data class NewProject(val synopsis: String, val error: String?) : UiState

        data class Error(val message: String) : UiState
    }

    data class ProjectItem(
        val id: String,
        val synopsis: String,
        val createdAt: Instant,
        val unansweredCount: Int
    )

    data class ArchiveSettings(
        val autoArchiveAfterUpdate: Boolean = false
    )

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch {
            uiState = UiState.Loading
            try {
                val projects = repository.getAllProjects()
                val items = projects.map { p ->
                    val activeRound = p.exchangeRounds.filter { it.isActive }.lastOrNull()
                    val unansweredCount = if (activeRound != null) {
                        activeRound.questions.count { q ->
                            q.text.isNotBlank() && !q.isArchived
                        }
                    } else {
                        p.questions.count { it.text.isNotBlank() }
                    }
                    ProjectItem(
                        id = p.id,
                        synopsis = p.synopsis.take(60),
                        createdAt = p.createdAt,
                        unansweredCount = unansweredCount
                    )
                }
                uiState = UiState.ProjectList(items)
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Failed to load projects")
            }
        }
    }

    fun createProject(synopsis: String) {
        val trimmed = synopsis.trim()
        if (trimmed.isBlank()) {
            uiState = UiState.NewProject(trimmed, "Synopsis cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                val project = repository.createProject(trimmed)
                uiState = UiState.ProjectDetail(project)
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Failed to create project")
            }
        }
    }

    fun showProjectDetail(projectId: String) {
        viewModelScope.launch {
            uiState = UiState.Loading
            val project = repository.getProject(projectId)
            if (project != null) {
                uiState = UiState.ProjectDetail(project)
            } else {
                uiState = UiState.Error("Project not found")
            }
        }
    }

    fun updateAnswer(projectId: String, questionId: String, text: String) {
        viewModelScope.launch {
            try {
                val updated = repository.updateAnswer(
                    projectId,
                    questionId,
                    text,
                    archiveSettings.autoArchiveAfterUpdate
                )
                if (updated != null) {
                    uiState = UiState.ProjectDetail(updated)
                }
            } catch (e: Exception) {
                uiState = UiState.Error(e.message ?: "Failed to save answer")
            }
        }
    }

    fun toggleAutoArchive() {
        archiveSettings = archiveSettings.copy(
            autoArchiveAfterUpdate = !archiveSettings.autoArchiveAfterUpdate
        )
    }

    fun goToProjectList() {
        loadProjects()
    }

    fun goToNewProject() {
        uiState = UiState.NewProject("", null)
    }

    var archiveSettings by mutableStateOf(ArchiveSettings())
        private set
}
