package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.downloads.DownloadState
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.ModelBrowserEntry
import alphainterplanetary.thinker.engine.ModelPresentationTier
import alphainterplanetary.thinker.engine.PlanningContext
import alphainterplanetary.thinker.engine.RemoteModelInfo
import alphainterplanetary.thinker.engine.RemoteModelListState
import alphainterplanetary.thinker.engine.RemoteProvider
import alphainterplanetary.thinker.ui.components.TaskSpinner
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.DialogProperties
import kotlin.math.round

/**
 * The add/edit dialog for a Remote connection (ENGINE-CONFIG-PLAN.md 3b).
 *
 * The dialog shapes a draft held by [alphainterplanetary.thinker.ui.viewmodel.EngineViewModel]
 * — the Name field first, so a connection gets a name of the user's own instead
 * of inheriting the provider's label, then the shared [RemoteConnectionFields].
 * Nothing is written until Save: Cancel discards the draft, and whichever
 * engine is running keeps its stored values while the dialog is open.
 */
@Composable
internal fun ConnectionDialog(
  draft: EngineInstance,
  isNew: Boolean,
  models: RemoteModelListState,
  onNameChange: (String) -> Unit,
  onProviderChange: (RemoteProvider) -> Unit,
  onBaseUrlChange: (String) -> Unit,
  onApiKeyChange: (String) -> Unit,
  onModelChange: (String) -> Unit,
  onContextTokensChange: (Int) -> Unit,
  onBrowseModels: () -> Unit,
  onModelSelected: (RemoteModelInfo) -> Unit,
  onPullModel: (String) -> Unit,
  pulls: List<DownloadState> = emptyList(),
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(dismissOnClickOutside = false),
    title = { Text(if (isNew) "Add connection" else "Edit connection") },
    text = {
      // The full form is taller than a small window, so it scrolls inside the
      // dialog rather than being cut off at its bottom edge.
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
          value = draft.label,
          onValueChange = onNameChange,
          label = { Text("Name") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
        )
        Spacer(modifier = Modifier.height(Dimens.FormGap))
        RemoteConnectionFields(
          provider = draft.provider ?: RemoteProvider.Default,
          onProviderChange = onProviderChange,
          baseUrl = draft.baseUrl,
          apiKey = draft.apiKey,
          model = draft.model,
          contextTokens = draft.contextTokens.toString(),
          models = models,
          onBaseUrlChange = onBaseUrlChange,
          onApiKeyChange = onApiKeyChange,
          onModelChange = onModelChange,
          // Digits only, and only a complete number: a half-typed window
          // would otherwise resolve the budget to something the user did not
          // ask for while they are still in the field.
          onContextTokensChange = { typed ->
            typed.filter { it.isDigit() }
              .toIntOrNull()
              ?.takeIf { it > 0 }
              ?.let(onContextTokensChange)
          },
          onBrowseModels = onBrowseModels,
          onModelSelected = onModelSelected,
          onPullModel = onPullModel,
          pulls = pulls,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onSave,
        enabled = draft.label.isNotBlank(),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

/**
 * The Remote connection settings, in two parts — the form inside
 * [ConnectionDialog]. The provider ([RemoteProviderPicker]) decides which Koog
 * client the backend builds; the fields below it fill that client in — the
 * endpoint where one can be moved, a key where the provider needs one, the
 * model, and the window to budget.
 *
 * Changes take effect when the dialog is saved: the remote backend rebuilds
 * its client on the committed values, with no restart.
 */
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
  onPullModel: (String) -> Unit = {},
  pulls: List<DownloadState> = emptyList(),
) {
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
    provider = provider,
    onPullModel = onPullModel,
    pulls = pulls,
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
      Text(
        "Each generation fills up to ${PlanningContext.TranscriptSharePercent}% of " +
          "it with this project's questions and answers." +
          if (provider == RemoteProvider.OpenAiCompatible) {
            " Picking a model fills in its id, and a window too when the server " +
              "reports one; otherwise tell the app how big the model is."
          } else {
            " Picking a model above fills it in from the model's real window."
          }
      )
    },
  )
}

/**
 * The three [RemoteProvider]s as chips. Wrapping rather than a single row,
 * because the labels are the full user-facing names and a narrow phone has no
 * room for all three abreast.
 */
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
 * chips grouped by Verified/Recommended/Others. Discouraged models stay
 * collapsed until asked for, so the browser leads with models likely to work.
 * Curated Ollama models that are not installed appear as pull chips in those
 * tiers, but only once a list has loaded — there is nothing to compare against
 * before that. Picking one hands its id — and the window the provider actually
 * serves, when it said — back to the caller through [onSelect].
 */
@Composable
private fun ModelBrowser(
  current: String,
  state: RemoteModelListState,
  onBrowse: () -> Unit,
  onSelect: (RemoteModelInfo) -> Unit,
  provider: RemoteProvider = RemoteProvider.Default,
  onPullModel: (String) -> Unit = {},
  pulls: List<DownloadState> = emptyList(),
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    val isLoading = state is RemoteModelListState.Loading
    OutlinedButton(
      onClick = onBrowse,
      enabled = !isLoading,
    ) {
      Text(if (state is RemoteModelListState.Loaded) "Refresh models" else "Browse models")
    }
    if (isLoading) {
      Spacer(modifier = Modifier.width(Dimens.ChipGap))
      TaskSpinner()
    }
  }

  when (state) {
    RemoteModelListState.Idle,
    RemoteModelListState.Loading -> Unit

    is RemoteModelListState.Loaded -> {
      // Curated Ollama models that are not installed join the installed ones
      // as pull chips. Filtering is tag-based against the loaded set, so this
      // only runs once there is a list to compare with.
      val entries = state.browserEntriesWithPulls(provider)
      if (entries.isEmpty()) {
        Spacer(modifier = Modifier.height(Dimens.TightGap))
        Text(
          text = "No models found on this server.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        AllTiers(entries, current, onSelect, onPullModel, pulls)
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

@Composable
private fun AllTiers(
  entries: List<ModelBrowserEntry>,
  current: String,
  onSelect: (RemoteModelInfo) -> Unit,
  onPullModel: (String) -> Unit,
  pulls: List<DownloadState>,
) {
  Spacer(modifier = Modifier.height(Dimens.TightGap))
  listOf(
    ModelPresentationTier.Recommended,
    ModelPresentationTier.HighQualityButSlower,
    ModelPresentationTier.LowerQualityButFast,
    ModelPresentationTier.Unknown,
  ).forEach { tier ->
    RenderTier(tier, entries, current, onSelect, onPullModel, pulls)
  }
  DiscouragedTier(entries, current, onSelect, onPullModel, pulls)
}

/**
 * The discouraged tier, collapsed by default so a discouraging list of
 * installed models does not bury the ones worth picking. The header stays
 * visible with a Show/Hide toggle, and the tier stays open while the current
 * model lives in it so an existing selection never looks like it vanished.
 */
@Composable
private fun DiscouragedTier(
  entries: List<ModelBrowserEntry>,
  current: String,
  onSelect: (RemoteModelInfo) -> Unit,
  onPullModel: (String) -> Unit,
  pulls: List<DownloadState>,
) {
  val tier = ModelPresentationTier.Discouraged
  val group = entries.filter { it.rank.presentationTier == tier }
  if (group.isEmpty()) return
  var expandedChoice by rememberSaveable { mutableStateOf(false) }
  var choseManually by rememberSaveable { mutableStateOf(false) }
  val selectionIsDiscouraged = group.any { it.rank.model.id == current }
  val expanded = if (choseManually) expandedChoice else selectionIsDiscouraged
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Dimens.ContentGap),
  ) {
    TierHeader(tier)
    TextButton(
      onClick = {
        expandedChoice = !expanded
        choseManually = true
      },
    ) {
      Text(if (expanded) "Hide" else "Show")
    }
  }
  if (expanded) {
    Spacer(modifier = Modifier.height(Dimens.TightGap))
    TierChips(group, current, onSelect, onPullModel, pulls)
  }
}

@Composable
private fun RenderTier(
  tier: ModelPresentationTier,
  entries: List<ModelBrowserEntry>,
  current: String,
  onSelect: (RemoteModelInfo) -> Unit,
  onPullModel: (String) -> Unit,
  pulls: List<DownloadState>,
) {
  val group = entries.filter { it.rank.presentationTier == tier }
  if (group.isEmpty()) return
  TierHeader(tier)
  Spacer(modifier = Modifier.height(Dimens.TightGap))
  TierChips(group, current, onSelect, onPullModel, pulls)
}

@Composable
private fun TierHeader(tier: ModelPresentationTier) {
  Text(
    text = "${tier.icon} ${tier.description}",
    style = MaterialTheme.typography.headlineSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = Dimens.TightGap),
  )
}

@Composable
private fun TierChips(
  group: List<ModelBrowserEntry>,
  current: String,
  onSelect: (RemoteModelInfo) -> Unit,
  onPullModel: (String) -> Unit,
  pulls: List<DownloadState>,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(Dimens.ChipGap),
    verticalArrangement = Arrangement.spacedBy(Dimens.TightGap),
  ) {
    group.forEach { entry ->
      val rank = entry.rank
      val info = rank.model
      val isPull = entry.isPull
      val note = rank.reason.takeIf {
        it.isNotBlank() &&
          it != "Meets minimum requirements" &&
          it != "Insufficient data" &&
          !it.startsWith("Unmeasured model")
      }
      val activePull =
        pulls.lastOrNull { it.modelId == info.id && !it.status.finished }
      FilterChip(
        selected = info.id == current,
        onClick = {
          if (isPull) {
            onPullModel(info.id)
          } else {
            onSelect(info)
          }
        },
        label = {
          val prefix = when {
            activePull?.progress != null -> activePull.progress.formatAsPercentage()
            isPull -> "↓ "
            else -> ""
          }
          val suffix = if (!note.isNullOrBlank()) " · $note" else ""
          Text("$prefix${info.displayName}$suffix")
        },
        leadingIcon = maybeDownloadSpinner(activePull = activePull),
        colors = if (isPull) {
          FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
          )
        } else {
          FilterChipDefaults.filterChipColors()
        },
        elevation = null,
      )
    }
  }
}

fun Float.formatAsPercentage(): String {
  val rounded = round(this * 1000) / 10f
  val str = rounded.toString()
  // Ensures an explicit .0 is appended if it turns into a whole number string
  return if (!str.contains('.')) "$str.0% " else "$str% "
}

@Composable
private fun maybeDownloadSpinner(
  activePull: DownloadState?,
): @Composable () -> Unit = {
  if (activePull != null) {
    if (activePull.progress != null) {
      CircularProgressIndicator(
        modifier = Modifier.size(Dimens.ProgressIndicatorSize),
        strokeWidth = Dimens.ProgressStroke,
        color = MaterialTheme.colorScheme.primary,
        progress = { activePull.progress }
      )
    } else {
      TaskSpinner()
    }
  }
}
