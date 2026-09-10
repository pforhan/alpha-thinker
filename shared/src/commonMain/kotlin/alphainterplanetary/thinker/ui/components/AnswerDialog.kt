package alphainterplanetary.thinker.ui.components

import alphainterplanetary.thinker.model.Question
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

enum class AnswerDialogResult {
  Submitted,
  SavedDraft,
  DeletedAnswer,
  Unignored,
}

/**
 * Answer dialog built around a simple "text + completed" model:
 *
 * - The field is prefilled with the committed answer or the draft text.
 * - "Completed" is only ever set by the "Marked as complete" switch; edits
 *   never implicitly demote a committed answer or complete a draft.
 * - Submit and tapping off save with the same rules: blank text clears the
 *   draft or deletes the answer; text stores a draft (switch off) or a
 *   committed answer (switch on). Cancel discards any changes. Ignored
 *   questions are not editable and just dismiss.
 */
@Composable
fun AnswerDialog(
  question: Question,
  onDismiss: () -> Unit,
  onResult: (AnswerDialogResult, String) -> Unit,
) {
  val initialText = question.currentAnswer?.text ?: question.draftText ?: ""
  var answerText by remember { mutableStateOf(initialText) }
  var completed by remember { mutableStateOf(question.isAnswered) }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  fun submit() {
    val trimmed = answerText.trim()
    onResult(
      when {
        trimmed.isBlank() -> AnswerDialogResult.DeletedAnswer
        completed -> AnswerDialogResult.Submitted
        else -> AnswerDialogResult.SavedDraft
      },
      trimmed,
    )
  }

  fun close() {
    if (question.isIgnored) {
      onDismiss()
    } else {
      submit()
    }
  }

  AlertDialog(
    onDismissRequest = { close() },
    title = {
      val density = LocalDensity.current
      val scope = rememberCoroutineScope()
      var scrollableHeight by remember { mutableStateOf(Dp.Unspecified) }
      val scrollState = rememberScrollState()
      val scrollable = scrollableHeight != Dp.Unspecified
      val pagePx = with(density) { scrollableHeight.toPx() }
      val hasContentAbove by remember {
        derivedStateOf { scrollState.value > 0 }
      }
      val hasContentBelow by remember {
        derivedStateOf { scrollState.value < scrollState.maxValue }
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .then(if (scrollable) Modifier.heightIn(max = scrollableHeight) else Modifier),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = question.text,
            maxLines = if (scrollable) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
              if (result.hasVisualOverflow) {
                with(density) { scrollableHeight = result.size.height.toDp() }
              }
            },
            modifier = Modifier
              .weight(1f)
              .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier),
          )
          if (scrollable) {
            Column(
              verticalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxHeight()
                .width(Dimens.ScrollControlSize),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(Dimens.ScrollControlSize)
                  .align(Alignment.CenterHorizontally)
                  .clickable(enabled = hasContentAbove) {
                    scope.launch { scrollState.animateScrollBy(-pagePx) }
                  },
              ) {
                if (hasContentAbove) {
                  ScrollHintIcon(Icons.Default.KeyboardArrowUp)
                }
              }
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .size(Dimens.ScrollControlSize)
                  .align(Alignment.CenterHorizontally)
                  .clickable(enabled = hasContentBelow) {
                    scope.launch { scrollState.animateScrollBy(pagePx) }
                  },
              ) {
                if (hasContentBelow) {
                  ScrollHintIcon(Icons.Default.KeyboardArrowDown)
                }
              }
            }
          }
        }
      }
    },
    text = {
      Column {
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
            TextButton(
              onClick = { onResult(AnswerDialogResult.Unignored, "") },
            ) {
              Text("Unignore")
            }
          }
          Spacer(modifier = Modifier.height(Dimens.SectionGap))
        }

        OutlinedTextField(
          value = answerText,
          onValueChange = { answerText = it },
          enabled = !question.isIgnored,
          label = { Text("Answer") },
          trailingIcon = {
            if (answerText.isNotEmpty()) {
              IconButton(onClick = { answerText = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear answer")
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
          minLines = 3,
          maxLines = 8,
        )

        if (!question.isIgnored) {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Switch(
              checked = completed,
              onCheckedChange = { completed = it },
            )
            Spacer(modifier = Modifier.width(Dimens.ControlLabelGap))
            Text(
              text = "Marked as complete",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        if (question.isAnswered && !question.isIgnored) {
          Spacer(modifier = Modifier.height(Dimens.ContentGap))
          HorizontalDivider()
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          TextButton(
            onClick = { onResult(AnswerDialogResult.DeletedAnswer, "") },
          ) {
            Text(
              "Delete Answer",
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { submit() },
        enabled = !question.isIgnored,
      ) {
        Text("Submit")
      }
    },
    dismissButton = {
      TextButton(onClick = { onDismiss() }) {
        Text("Cancel")
      }
    },
  )
}

@Composable
private fun BoxScope.ScrollHintIcon(icon: ImageVector) {
  Icon(
    imageVector = icon,
    contentDescription = null,
    modifier = Modifier.size(Dimens.IconSizeSmall),
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}