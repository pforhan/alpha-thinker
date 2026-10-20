package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineKind
import alphainterplanetary.thinker.engine.EngineRef
import alphainterplanetary.thinker.ui.theme.Dimens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

/**
 * The planning-backend picker: one card per [EngineKind], except Remote — that
 * slot holds [ConnectionsList], because the Remote backend is per-instance:
 * the list is where a connection is selected, edited, or deleted, and its
 * add/edit [ConnectionDialog] holds the connection fields as a draft.
 *
 * This is the only place the engine can change, which is what keeps the header
 * a projection: a switch here moves the single source of truth, and every pill,
 * row, and status line follows from it.
 */
@Composable
internal fun IntelligenceSheetContent(chrome: AppChromeState) {
  val selectedRef by chrome.engines.selectedRef.collectAsState()
  val instances by chrome.engines.instances.collectAsState()
  val draft by chrome.engines.draft.collectAsState()
  val draftModels by chrome.engines.draftModels.collectAsState()
  val engines = chrome.engines

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    EngineKind.entries.forEach { mode ->
      if (mode == EngineKind.Remote) {
        ConnectionsList(
          instances = instances,
          selectedRef = selectedRef,
          onSelect = { ref -> engines.selectEngine(ref) },
          onEdit = { ref -> engines.startEditConnection(ref.id) },
          onDelete = { ref -> engines.deleteInstance(ref.id) },
          onAdd = { engines.startAddConnection() },
        )
      } else {
        EngineKindOption(
          mode = mode,
          selected = selectedRef == EngineRef.Builtin(mode),
          selectable = mode.available(),
          onClick = { engines.selectEngine(EngineRef.Builtin(mode)) },
        )
      }
    }
  }

  draft?.let { current ->
    ConnectionDialog(
      draft = current,
      isNew = instances.none { it.id == current.id },
      models = draftModels,
      onNameChange = engines::setDraftLabel,
      onProviderChange = engines::setDraftProvider,
      onBaseUrlChange = engines::setDraftBaseUrl,
      onApiKeyChange = engines::setDraftApiKey,
      onModelChange = engines::setDraftModel,
      onContextTokensChange = engines::setDraftContextTokens,
      onBrowseModels = engines::refreshDraftModels,
      onModelSelected = { info ->
        engines.pickDraftModel(info.id, info.contextLength)
      },
      onSave = engines::saveDraft,
      onDismiss = engines::cancelDraft,
    )
  }
}
