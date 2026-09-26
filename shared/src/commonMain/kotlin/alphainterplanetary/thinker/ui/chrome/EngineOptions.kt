package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineMode
import alphainterplanetary.thinker.ui.theme.BadgeShape
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * One selectable planning backend in the "Intelligence" section: names and
 * describes the mode, marks the selected one, and gates unavailable backends
 * off (only Lite is shipped today — the LLM backends land in Phase 3).
 */
@Composable
internal fun EngineModeOption(
  mode: EngineMode,
  selected: Boolean,
  selectable: Boolean,
  onClick: () -> Unit,
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
    }
  }
}

/**
 * The Remote backend's connection settings: any OpenAI-compatible endpoint,
 * its API key (empty for a local Ollama), and the model name. Shown while
 * [EngineMode.Remote] is the selected backend; changes take effect from the
 * next planning interaction.
 */
@Composable
internal fun RemoteConnectionItem(
  baseUrl: String,
  apiKey: String,
  model: String,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onModelChange: (String) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(Dimens.CardPadding)) {
      Text(
        text = "Remote connection",
        style = MaterialTheme.typography.titleSmall,
      )
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = "Defaults to a local Ollama install; a trailing /v1 endpoint " +
          "root (as Ollama's docs print) works too.",
        style = MaterialTheme.typography.bodyMedium,
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
    }
  }
}
