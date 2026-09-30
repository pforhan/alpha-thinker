package alphainterplanetary.thinker.ui.chrome

import alphainterplanetary.thinker.engine.EngineMode
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
 * The planning-backend picker: one card per [EngineMode], with the Remote
 * connection fields nested inside the Remote card rather than listed below the
 * stack (see [EngineModeOption]'s content slot).
 *
 * This is the only place the engine can change, which is what keeps the header
 * a projection: a switch here moves the single source of truth, and every pill,
 * row, and status line follows from it.
 */
@Composable
internal fun IntelligenceSheetContent(chrome: AppChromeState) {
  val engineMode by chrome.settings.engineMode.collectAsState()
  val baseUrl by chrome.settings.remoteLlmBaseUrl.collectAsState()
  val apiKey by chrome.settings.remoteLlmApiKey.collectAsState()
  val model by chrome.settings.remoteLlmModel.collectAsState()
  val contextTokens by chrome.settings.remoteLlmContextTokens.collectAsState()
  val settings = chrome.settings

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(bottom = Dimens.ScreenPadding),
    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
  ) {
    EngineMode.entries.forEach { mode ->
      EngineModeOption(
        mode = mode,
        selected = engineMode == mode,
        selectable = mode.available(),
        onClick = { settings.selectEngineMode(mode) },
        content = {
          if (mode == EngineMode.Remote) {
            RemoteConnectionFields(
              baseUrl = baseUrl,
              apiKey = apiKey,
              model = model,
              contextTokens = contextTokens.toString(),
              onBaseUrlChange = settings::setRemoteLlmBaseUrl,
              onApiKeyChange = settings::setRemoteLlmApiKey,
              onModelChange = settings::setRemoteLlmModel,
              // Digits only, and only a complete number: a half-typed window
              // would otherwise resolve the budget to something the user did not
              // ask for while they are still in the field.
              onContextTokensChange = { typed ->
                typed.filter { it.isDigit() }
                  .toIntOrNull()
                  ?.takeIf { it > 0 }
                  ?.let(settings::setRemoteLlmContextTokens)
              },
            )
          }
        },
      )
    }
  }
}
