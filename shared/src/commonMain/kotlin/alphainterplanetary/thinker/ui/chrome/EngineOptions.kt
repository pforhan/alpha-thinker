package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.engine.RemoteModelInfo
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.ui.components.OptionHeader
import alphainterplanetary.thinker.ui.components.SelectionDot
import alphainterplanetary.thinker.ui.components.TaskSpinner
import alphainterplanetary.thinker.ui.theme.Dimens
import alphainterplanetary.thinker.util.formatTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * One selectable planning backend: names and describes the mode, marks the
 * selected one, and gates unavailable backends off (only offline and Remote are
 * selectable today — the on-device backends land later).
 *
 * [content] renders inside the same card, below the header, so a mode's own
 * settings can live with it: the Remote card nests its connection fields rather
 * than floating them in a separate card of their own. A filled endpoint under an
 * engine that was not selected read as a live remote connection when the app
 * would not make one.
 */
@Composable
internal fun EngineKindOption(
  mode: EngineKind,
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
      OptionHeader(label = mode.label, description = mode.description) {
        SelectionDot(selected)
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
 * The Remote backend's connection settings, in two parts. The provider
 * ([RemoteProviderPicker]) decides which Koog client the backend builds; the
 * fields below it fill that client in — the endpoint where one can be moved, a
 * key where the provider needs one, the model, and the window to budget.
 *
 * Changes take effect from the next planning interaction: the remote backend
 * rebuilds its client when these values change, with no restart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemoteConnectionFields(
  provider: RemoteProvider,
  onProviderChange: (RemoteProvider) -> Unit,
  baseUrl: String,
  apiKey: String,
  model: String,
  contextTokens: String,
  models: RemoteModelListState,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onModelChange: (String) -> Unit,
  onContextTokensChange: (String) -> Unit,
  onBrowseModels: () -> Unit,
  onModelSelected: (RemoteModelInfo) -> Unit,
) {
  Spacer(modifier = Modifier.height(Dimens.SectionGap))
  RemoteProviderPicker(provider = provider, onProviderChange = onProviderChange)
  Spacer(modifier = Modifier.height(Dimens.ContentGap))
  Text(
    text = provider.description,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (provider.configurableBaseUrl) {
    Spacer(modifier = Modifier.height(Dimens.ContentGap))
    OutlinedTextField(
      value = baseUrl,
      onValueChange = onBaseUrlChange,
      label = { Text("Base URL") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      isError = baseUrl.isBlank(),
      supportingText = {
        Text("A trailing /v1 endpoint root (as Ollama's docs print) works too.")
      },
    )
  }
  val showsApiKey = provider == RemoteProvider.OpenAiCompatible || provider.requiresApiKey
  if (showsApiKey) {
    Spacer(modifier = Modifier.height(Dimens.ContentGap))
    OutlinedTextField(
      value = apiKey,
      onValueChange = onApiKeyChange,
      label = { Text("API key") },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      supportingText = {
        Text(
          if (provider.requiresApiKey) {
            "A key from the Anthropic console. Open it there:"
          } else {
            "Leave empty for a local server that needs no key."
          },
        )
      },
    )
    provider.apiKeyUrl?.let { url ->
      val uriHandler = LocalUriHandler.current
      TextButton(onClick = { uriHandler.openUri(url) }) {
        Text("Get an API key")
      }
    }
  }
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
  ModelBrowser(
    current = model,
    state = models,
    onBrowse = onBrowseModels,
    onSelect = onModelSelected,
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
      Text("Each generation fills up to ${PlanningContext.TranscriptSharePercent}% of " +
        "it with this project's questions and answers." +
        if (provider == RemoteProvider.OpenAiCompatible) {
          " Picking a model fills in its id, and a window too when the server " +
            "reports one; otherwise tell the app how big the model is."
        } else {
          " Picking a model above fills it in from the model's real window."
        })
    },
  )
}

/**
 * The three [RemoteProvider]s as chips. Wrapping rather than a single row,
 * because the labels are the full user-facing names and a narrow phone has no
 * room for all three abreast.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemoteProviderPicker(
  provider: RemoteProvider,
  onProviderChange: (RemoteProvider) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    RemoteProvider.entries.forEach { candidate ->
      FilterChip(
        selected = provider == candidate,
        onClick = { onProviderChange(candidate) },
        label = { Text(candidate.label) },
        elevation = null,
      )
    }
  }
}

/**
 * The model browser for a provider that can be asked for its list.
 *
 * [state] drives the affordance: idle shows only the button, loading a spinner
 * beside it, a failure the reason in place of the list, and a loaded list (even
 * an empty one — an empty Ollama server is an answer) the models as tappable
 * chips. Picking one hands its id — and the window the provider actually serves,
 * when it said — back to the caller through [onSelect].
 */
@Composable
private fun ModelBrowser(
  current: String,
  state: RemoteModelListState,
  onBrowse: () -> Unit,
  onSelect: (RemoteModelInfo) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedButton(
      onClick = onBrowse,
      enabled = state !is RemoteModelListState.Loading,
    ) {
      Text(if (state is RemoteModelListState.Loaded) "Refresh models" else "Browse models")
    }
    if (state is RemoteModelListState.Loading) {
      Spacer(modifier = Modifier.width(Dimens.ChipGap))
      TaskSpinner()
    }
  }
  when (state) {
    RemoteModelListState.Idle, RemoteModelListState.Loading -> Unit
    is RemoteModelListState.Loaded -> {
      if (state.models.isEmpty()) {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        Text(
          text = "No models found on this server.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
          verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
        ) {
          state.models.forEach { info ->
            FilterChip(
              selected = info.id == current,
              onClick = { onSelect(info) },
              label = {
                // The window the provider reported rides along with the id, so
                // the user can see what picking this model will budget against.
                Text(
                  if (info.contextLength != null) {
                    "${info.id} · ${formatTokens(info.contextLength)}"
                  } else {
                    info.id
                  },
                )
              },
              elevation = null,
            )
          }
        }
      }
    }
    is RemoteModelListState.Failed -> {
      Spacer(modifier = Modifier.height(Dimens.TightGap))
      Text(
        text = "Couldn't load models: ${state.message}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}
