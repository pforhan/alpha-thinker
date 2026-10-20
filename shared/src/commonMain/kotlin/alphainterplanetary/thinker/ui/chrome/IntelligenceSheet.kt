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
 * The planning-backend picker: one card per [EngineKind], with the Remote
 * connection fields nested inside the Remote card rather than listed below the
 * stack (see [EngineKindOption]'s content slot).
 *
 * This is the only place the engine can change, which is what keeps the header
 * a projection: a switch here moves the single source of truth, and every pill,
 * row, and status line follows from it.
 */
@Composable
internal fun IntelligenceSheetContent(chrome: AppChromeState) {
  val engineKind by chrome.engines.selectedRef.collectAsState()
  val connection by chrome.engines.remoteConnection.collectAsState()
  val remoteModels by chrome.engines.remoteModels.collectAsState()
  val engines = chrome.engines

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    EngineKind.entries.forEach { mode ->
      val selected = when (val ref = engineKind) {
        is EngineRef.Builtin -> ref.kind == mode
        else -> false
      }
      EngineKindOption(
        mode = mode,
        selected = selected,
        selectable = mode.available(),
        onClick = { engines.selectEngine(EngineRef.Builtin(mode)) },
        content = {
          if (mode == EngineKind.Remote) {
            RemoteConnectionFields(
              provider = connection.provider,
              onProviderChange = engines::selectRemoteProvider,
              baseUrl = connection.baseUrl,
              apiKey = connection.apiKey,
              model = connection.model,
              contextTokens = connection.contextLength.toString(),
              models = remoteModels,
              onBaseUrlChange = engines::setRemoteBaseUrl,
              onApiKeyChange = engines::setRemoteApiKey,
              onModelChange = engines::setRemoteModel,
              // Digits only, and only a complete number: a half-typed window
              // would otherwise resolve the budget to something the user did not
              // ask for while they are still in the field.
              onContextTokensChange = { typed ->
                typed.filter { it.isDigit() }
                  .toIntOrNull()
                  ?.takeIf { it > 0 }
                  ?.let(engines::setRemoteContextTokens)
              },
              onBrowseModels = engines::refreshRemoteModels,
              onModelSelected = { info ->
                engines.selectRemoteModel(info.id, info.contextLength)
              },
            )
          }
        },
      )
    }
  }
}
