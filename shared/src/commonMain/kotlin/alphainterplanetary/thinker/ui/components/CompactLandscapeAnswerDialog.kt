package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.phases.Phase
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun CompactLandscapeAnswerDialog(
  question: Question,
  phase: Phase,
  answerText: String,
  onAnswerTextChange: (String) -> Unit,
  hasAnswer: Boolean,
  focusRequester: FocusRequester,
  maxDialogHeight: Dp,
  onDismiss: () -> Unit,
  onUnignore: () -> Unit,
  onDelete: () -> Unit,
  onResearch: () -> Unit,
  onSaveDraft: () -> Unit,
  onSaveAnswer: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(Dimens.ContentGap),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = maxDialogHeight)
          .padding(Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
      ) {
        ScrollableOverflowText(
          text = question.text,
          collapsedMaxLines = Int.MAX_VALUE,
          fillViewportHeight = true,
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier
            .weight(0.38f)
            .fillMaxHeight(),
        )

        Column(
          modifier = Modifier
            .weight(0.62f)
            .fillMaxHeight(),
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
          ) {
            if (question.isIgnored) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "This question is ignored.",
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onUnignore) {
                  Text("Unignore")
                }
              }
            }

            Box {
              OutlinedTextField(
                value = answerText,
                onValueChange = onAnswerTextChange,
                enabled = !question.isIgnored,
                label = { Text("Answer") },
                modifier = Modifier
                  .fillMaxWidth()
                  .focusRequester(focusRequester),
                minLines = 3,
                maxLines = 8,
              )

              if (answerText.isNotEmpty() && !question.isIgnored) {
                IconButton(
                  onClick = { onAnswerTextChange("") },
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                      top = Dimens.FieldOverlayInsetTop,
                      end = Dimens.FieldOverlayInsetEnd,
                    ),
                ) {
                  Icon(Icons.Default.Clear, contentDescription = "Clear answer")
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(Dimens.TightGap),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              PhasePill(phase = phase)
              Spacer(modifier = Modifier.weight(1f))
              TextButton(onClick = onResearch) {
                Text("Research")
              }
            }

            if (hasAnswer) {
              HorizontalDivider()
              TextButton(onClick = onDelete) {
                Text(
                  "Delete Answer",
                  color = MaterialTheme.colorScheme.error,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
              Dimens.TightGap,
              Alignment.End,
            ),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TextButton(onClick = onDismiss) {
              Text("Cancel")
            }
            TextButton(
              onClick = onSaveDraft,
              enabled = !question.isIgnored,
            ) {
              Text("Save Draft")
            }
            Button(
              onClick = onSaveAnswer,
              enabled = !question.isIgnored,
            ) {
              Text("Save Answer")
            }
          }
        }
      }
    }
  }
}
