package com.pforhan.alphathinker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.ui.component.QuestionsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    project: Project?,
    onViewAllClick: () -> Unit,
    onViewArchivedClick: () -> Unit,
    onToggleAutoArchive: () -> Unit,
    onArchiveCurrentClick: () -> Unit,
    onBack: () -> Unit,
    onUpdateAnswer: (String, String) -> Unit,
    onExportClick: () -> Unit,
    autoArchive: Boolean
) {
    Scaffold(
        modifier = Modifier.nestedScroll(1f),
        topBar = {
            val scrollBehavior = TopAppBarDefaults.exitAlwaysScrollBehavior()
            TopAppBar(
                title = { Text("Project Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        project?.let { p ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Synopsis",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            p.synopsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                val activeRound = p.exchangeRounds.filter { it.isActive }.lastOrNull()
                val unanswered = activeRound?.questions?.filter { q ->
                    q.text.isNotBlank() && !q.isArchived
                } ?: p.questions.filter { q ->
                    q.text.isNotBlank()
                }

                if (unanswered.isNotEmpty()) {
                    Column {
                        Text(
                            "Unanswered Questions",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        unanswered.forEach { question ->
                            QuestionsCard(
                                question = question,
                                onUpdateAnswer = onUpdateAnswer
                            )
                        }
                    }
                }

                val archived = p.exchangeRounds
                    .flatMap { it.questions }
                    .filter { it.isArchived }

                if (archived.isNotEmpty()) {
                    Column {
                        Text(
                            "Archived Questions (${archived.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        archived.forEach { question ->
                            QuestionsCard(
                                question = question,
                                onUpdateAnswer = onUpdateAnswer
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Auto-archive on update",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = onToggleAutoArchive) {
                                Icon(
                                    if (autoArchive) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = "Toggle auto-archive"
                                )
                            }
                        }
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Button(
                            onClick = onArchiveCurrentClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Archive, contentDescription = null)
                            Text("Archive Current Questions")
                        }
                        Button(
                            onClick = onViewAllClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AllInclusive, contentDescription = null)
                            Text("View All Questions")
                        }
                    }
                }
            }
        } ?: let {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
