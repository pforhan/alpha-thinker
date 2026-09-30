package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * One selectable planning backend: names and describes the mode, marks the
 * selected one, and gates unavailable backends off (only Lite and Remote are
 * selectable today — the on-device backends land later).
 *
 * [content] renders inside the same card, below the header, so a mode's own
 * settings can live with it: the Remote card nests its connection fields rather
 * than floating them in a separate card of their own. A filled endpoint under an
 * engine that was not selected read as a live remote connection when the app
 * would not make one.
 */
@Composable
internal fun EngineModeOption(
  mode: EngineMode,
  selected: Boolean,
  selectable: Boolean,
  onClick: () -> Unit,
  content: @Composable ColumnScope.() -> Unit = {},
) {
  Card(
    onClick = onClick,
    enabled = selectable,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = mode.label,
            style = MaterialTheme.typography.titleSmall,
          )
          Spacer(modifier = Modifier.height(Dimens.TightGap))
          Text(
            text = mode.description,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
        Spacer(modifier = Modifier.width(Dimens.ContentGap))
        Box(
          modifier = Modifier
            .size(Dimens.ScrollControlSize)
            .clip(BadgeShape)
            .background(
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant,
            ),
        )
      }
      if (!selectable) {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        Text(
          text = "Not available on this device yet.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      content()
    }
  }
}

/**
 * The Remote backend's connection settings: any OpenAI-compatible endpoint, its
 * API key (empty for a local Ollama), and the model name. Renders as bare fields
 * because its caller already supplies the card — the Remote engine card nests it
 * rather than sitting beside it.
 *
 * Changes take effect from the next planning interaction: the remote backend
 * rebuilds its client when these values change, with no restart.
 */
@Composable
internal fun RemoteConnectionFields(
  baseUrl: String,
  apiKey: String,
  model: String,
  contextTokens: String,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onModelChange: (String) -> Unit,
  onContextTokensChange: (String) -> Unit,
) {
  Spacer(modifier = Modifier.height(Dimens.SectionGap))
  Text(
    text = "Defaults to a local Ollama install; a trailing /v1 endpoint " +
      "root (as Ollama's docs print) works too.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  OutlinedTextField(
    value = baseUrl,
    onValueChange = onBaseUrlChange,
    label = { Text("Base URL") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    isError = baseUrl.isBlank(),
  )
  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  OutlinedTextField(
    value = apiKey,
    onValueChange = onApiKeyChange,
    label = { Text("API key") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    supportingText = {
      Text("Leave empty for a local Ollama that needs no key.")
    },
  )
  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  OutlinedTextField(
    value = model,
    onValueChange = onModelChange,
    label = { Text("Model") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    isError = model.isBlank(),
  )
  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  OutlinedTextField(
    value = contextTokens,
    onValueChange = onContextTokensChange,
    label = { Text("Context window (tokens)") },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    isError = contextTokens.toIntOrNull()?.let { it > 0 } != true,
    supportingText = {
      Text("A model named here has no catalogue to read a window from, so tell " +
        "the app how big it is. Each generation fills up to " +
        "${PlanningContext.TranscriptSharePercent}% of it with this project's " +
        "questions and answers.")
    },
  )
}
