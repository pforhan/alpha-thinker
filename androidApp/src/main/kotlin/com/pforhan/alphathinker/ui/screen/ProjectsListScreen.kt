package com.pforhan.alphathinker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pforhan.alphathinker.ui.theme.md_theme_onSurfaceVariant
import com.pforhan.alphathinker.ui.theme.md_theme_primary
import com.pforhan.alphathinker.ui.MainViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ProjectsListScreen(
    projects: List<MainViewModel.ProjectItem>,
    onNewProjectClick: () -> Unit,
    onProjectClick: (String) -> Unit,
    onGoToNewProject: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alpha Thinker") },
                actions = {
                    Text(
                        "Alpha Thinker",
                        style = MaterialTheme.typography.titleSmall,
                        color = md_theme_primary,
                    )
                },
                scrollBehavior = TopAppBarDefaults.exitAlwaysScrollBehavior()
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProjectClick) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
            }
        }
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 16.dp),
                        tint = md_theme_onSurfaceVariant
                    )
                    Text(
                        "No projects yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    ElevatedButton(onClick = onGoToNewProject, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Create your first project")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(projects, key = { it.id }) { item ->
                    ProjectListItem(
                        projectItem = item,
                        onClick = { onProjectClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectListItem(
    projectItem: MainViewModel.ProjectItem,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                projectItem.synopsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Created: ${formatDate(projectItem.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = md_theme_onSurfaceVariant
            )

            if (projectItem.unansweredCount > 0) {
                Text(
                    "${projectItem.unansweredCount} unresolved question${if (projectItem.unansweredCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = md_theme_primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatDate(instant: Instant): String {
    return DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneOffset.systemDefault())
        .format(instant)
}
