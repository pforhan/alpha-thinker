package com.pforhan.alphathinker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.pforhan.alphathinker.model.ExchangeRound
import com.pforhan.alphathinker.model.Project
import com.pforhan.alphathinker.ui.component.QuestionsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    project: Project?,
    onBack: () -> Unit,
    onUpdateAnswer: (String, String) -> Unit,
    onExportClick: () -> Unit
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    p.synopsis,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                )

                Text(
                    "Exchange Rounds (${p.exchangeRounds.size})",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )

                p.exchangeRounds.forEach { round ->
                    ExchangeRoundCard(
                        round = round,
                        onUpdateAnswer = onUpdateAnswer
                    )
                }
            }
        } ?: let {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun ExchangeRoundCard(
    round: ExchangeRound,
    onUpdateAnswer: (String, String) -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.foundation.layout.padding(8.dp)
    ) {
        Text(
            "Round ${round.round}",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
        )
        round.questions.forEach { question ->
            QuestionsCard(
                question = question,
                onUpdateAnswer = onUpdateAnswer
            )
        }
    }
}
