package com.pforhan.alphathinker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pforhan.alphathinker.ui.screen.ProjectDetailScreen

@Composable
@Preview
fun ProjectDetailCardPreview() {
    ProjectDetailScreen(
        project = null,
        onBack = { },
        onUpdateAnswer = { _, _ -> },
        onExportClick = { }
    )
}
