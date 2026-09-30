package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.repository.ContextCheck
import alphainterplanetary.thinker.repository.ContextCompaction
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The near-limit question: this project's transcript has outgrown the share of
 * the model's context window it may fill, and here is what sending it would
 * cost.
 *
 * The number is the model's own window rather than an abstract budget, because
 * "over budget" is a rule the app invented and "bigger than the model's context
 * is what the user actually configured" is the one they can reason about.
 *
 * Three ways to proceed, in the order they give things up — send everything,
 * drop the earliest answers, or have the model summarize whole earlier phases.
 * The middle one is marked as the default because it needs no model call and no
 * waiting; the summarize row only appears when the engine can actually write
 * summaries ([check].canSummarize), so the dialog never offers a choice that
 * would fail. Tapping a row answers immediately: the request is already parked
 * behind this dialog, so there is nothing left to confirm.
 */
@Composable
fun ContextCompactionDialog(
  check: ContextCheck,
  onChoose: (ContextCompaction) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "This project is getting long",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap)) {
        Text(
          text = "This project's questions and answers are about " +
            "${PlanningContext.formatTokens(check.estimatedTokens)} tokens — " +
            "more than the " +
            "${check.budgetTokens?.let(PlanningContext::formatTokens) ?: "0"} " +
            "that fit in this engine's " +
            "${check.windowTokens?.let(PlanningContext::formatTokens) ?: "0"}-token " +
            "context. What should the planner do with the overflow?",
          style = MaterialTheme.typography.bodyMedium,
        )
        ContextChoiceRow(
          title = "Keep everything",
          detail = "Send the whole interview anyway. Nothing is lost, but a " +
            "prompt past the window may be refused outright, and a large one " +
            "makes the model slower to answer.",
          isDefault = false,
          enabled = true,
          onClick = { onChoose(ContextCompaction.KeepEverything) },
        )
        ContextChoiceRow(
          title = "Drop the earliest answers",
          detail = if (check.droppableAnswers == 0) {
            "Nothing to drop yet — every answer here belongs to the current phase."
          } else {
            "Keeps every question; drops ${check.droppableAnswers} of the oldest " +
              "phases' answers. The current phase is untouched."
          },
          isDefault = true,
          enabled = check.droppableAnswers > 0,
          onClick = { onChoose(ContextCompaction.DropEarlierAnswers) },
        )
        if (check.canSummarize) {
          ContextChoiceRow(
            title = "Summarize the earlier phases",
            detail = "Asks the model to condense ${phaseList(check)}, one phase at a " +
              "time, oldest first. Keeps their substance, costs an extra moment per " +
              "phase.",
            isDefault = false,
            enabled = true,
            onClick = { onChoose(ContextCompaction.SummarizeEarlierPhases) },
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

/** The summarizable phases as a readable list, e.g. `Scope & Goals, Design`. */
private fun phaseList(check: ContextCheck): String =
  check.summarizablePhases.joinToString(", ") { it.label }

@Composable
private fun ContextChoiceRow(
  title: String,
  detail: String,
  /** Marks the row that would be chosen by default; nothing is preselected here. */
  isDefault: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.medium,
    color = if (isDefault) {
      MaterialTheme.colorScheme.secondaryContainer
    } else {
      MaterialTheme.colorScheme.surfaceVariant
    },
    modifier = Modifier
      .fillMaxWidth()
      .clickable(enabled = enabled, onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(Dimens.CardPadding),
      horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(selected = isDefault, onClick = onClick, enabled = enabled)
      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = if (enabled) {
            MaterialTheme.colorScheme.onSurface
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
        Text(
          text = detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
